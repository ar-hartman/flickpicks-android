package com.flykpicks.android.store

import com.flykpicks.android.api.ApiError
import com.flykpicks.android.api.UserFacingError
import com.flykpicks.android.di.ApplicationScope
import com.flykpicks.android.model.LibraryItemImpl
import com.flykpicks.android.model.MediaItemImpl
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.service.LibraryService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Blueprint §9.2. One entry in [items] per physical copy. */
data class LibraryState(
    val items: List<LibraryItemImpl> = emptyList(),
    val isLoading: Boolean = false,
    /** Set when the last load failed; [items] keeps what was there before. */
    val errorMessage: String? = null,
    /** True once the server's list has arrived. Tells "not loaded yet" from an empty library. */
    val hasLoaded: Boolean = false,
) {
    fun item(id: String): LibraryItemImpl? = items.firstOrNull { it.id == id }

    /** Every copy of one catalog title: a DVD and a Blu-Ray of the same film are two items. */
    fun items(forTitleId: String): List<LibraryItemImpl> = items.filter { it.titleId == forTitleId }

    /** Whether any copy of the title owns the season. */
    fun isSeasonOwned(seasonNumber: Int, titleId: String): Boolean =
        items.any { it.titleId == titleId && it.ownsSeason(seasonNumber) }
}

/** Why a removal failed (iOS `LibraryRemoveError`). */
sealed class LibraryRemoveError(detail: String) : Exception(detail), UserFacingError {
    data object CantRemove : LibraryRemoveError("cant_remove") {
        override val userMessage get() = "Couldn't remove this item."
    }

    data object Retryable : LibraryRemoveError("retryable") {
        override val userMessage get() = "Something went wrong. Try again."
    }
}

/**
 * The signed-in user's library (blueprint §9.2, iOS `LibraryStore`), shared by the Library tab
 * and, from Phase 6, the Discover tab.
 *
 * Removing or editing a copy shows at once and is undone if the server refuses it. The change is
 * kept as a pending edit on top of the server's last list rather than as a saved copy of the
 * list to restore, so a load or a second change landing in between can't bring back stale data.
 * Every mutation's response is the whole library and replaces the list.
 *
 * Requests run in the application scope: a screen that goes away mid-request can't leave a
 * change half applied.
 */
@Singleton
class LibraryStore @Inject constructor(
    private val libraryService: LibraryService,
    private val genreStore: GenreStore,
    @ApplicationScope private val scope: CoroutineScope,
) : PerUserState {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits after every change the server accepted (iOS `onDidChangeLibrary`). */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    // Guarded by `lock`.
    private val lock = Any()
    private var serverItems: List<LibraryItemImpl> = emptyList()
    private var pendingEdits: List<PendingEdit> = emptyList()

    /** Counts mutation responses. A load that started before the latest one holds an older list. */
    private var serverRevision = 0

    /** Counts [reset]s. A result that belongs to an earlier session is dropped. */
    private var session = 0
    private var loadJob: Job? = null

    /**
     * Fetches the library. On failure the current items stay and [LibraryState.errorMessage] is
     * set. Callers arriving while a fetch is in flight wait for it instead of starting another.
     */
    suspend fun load() {
        val job = synchronized(lock) {
            loadJob?.takeIf { it.isActive } ?: run {
                val startedIn = session
                scope.launch { performLoad(startedIn) }.also { loadJob = it }
            }
        }
        job.join()
    }

    private suspend fun performLoad(session: Int) {
        val revision = synchronized(lock) {
            if (session != this.session) return
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            serverRevision
        }
        val result = try {
            // Genre names are filled in as titles are mapped, so the table has to be there first.
            genreStore.loadIfNeeded()
            Result.success(libraryService.fetchLibrary())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        synchronized(lock) {
            if (session != this.session) return
            val error = result.exceptionOrNull()
            if (error == null) {
                // A mutation that answered while this was in flight already brought a newer list.
                if (revision == serverRevision) serverItems = result.getOrThrow()
                publish { it.copy(isLoading = false, hasLoaded = true) }
            } else {
                val message = if (error is ApiError.NetworkUnavailable) error.userMessage else LOAD_FAILED
                publish { it.copy(isLoading = false, errorMessage = message) }
            }
        }
    }

    /** Shows the new format and condition at once; a failure puts the old ones back. */
    suspend fun updateItem(itemId: String, format: MediaFormat, condition: ItemCondition) {
        mutate(PendingEdit.Update(itemId, format, condition)) { libraryService.updateItem(itemId, format, condition) }
    }

    /**
     * Hides the copy at once; a failure brings it back and throws a [LibraryRemoveError].
     * [itemId] is [LibraryItemImpl.id] (one copy), not the title id.
     */
    suspend fun removeItem(itemId: String) {
        try {
            mutate(PendingEdit.Remove(itemId)) { libraryService.removeItem(itemId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError.Http) {
            throw if (e.status == 400) LibraryRemoveError.CantRemove else LibraryRemoveError.Retryable
        } catch (e: Exception) {
            throw LibraryRemoveError.Retryable
        }
    }

    /**
     * Never pass an empty [ownedSeasons]: to drop a copy's last season, [removeItem] the copy.
     * [selectAllSeasons] gives the copy every season the catalog lists.
     */
    suspend fun updateOwnedSeasons(itemId: String, ownedSeasons: List<Int>?, selectAllSeasons: Boolean) {
        mutate(edit = null) { libraryService.updateOwnedSeasons(itemId, ownedSeasons, selectAllSeasons) }
    }

    suspend fun addFromSearchItem(
        item: MediaItemImpl,
        format: MediaFormat,
        condition: ItemCondition,
        ownedSeasons: List<Int>? = null,
        selectAllSeasons: Boolean = true,
    ) {
        mutate(edit = null) { libraryService.addMediaItem(item.flyksId, format, condition, ownedSeasons, selectAllSeasons) }
    }

    override fun reset() {
        synchronized(lock) {
            session++
            loadJob?.cancel()
            loadJob = null
            serverItems = emptyList()
            pendingEdits = emptyList()
            _state.value = LibraryState()
        }
    }

    /**
     * Sends [request], whose result is the whole library. [edit], if any, is shown while the
     * request is in flight; dropping it on failure is the rollback.
     */
    private suspend fun mutate(edit: PendingEdit?, request: suspend () -> List<LibraryItemImpl>) {
        scope.async {
            val session = synchronized(lock) {
                if (edit != null) {
                    pendingEdits = pendingEdits + edit
                    publish()
                }
                session
            }
            val updated = try {
                request()
            } catch (e: Throwable) {
                synchronized(lock) {
                    if (edit != null && session == this@LibraryStore.session) {
                        pendingEdits = pendingEdits - edit
                        publish()
                    }
                }
                throw e
            }
            val applied = synchronized(lock) {
                if (session != this@LibraryStore.session) return@synchronized false
                serverItems = updated
                serverRevision++
                if (edit != null) pendingEdits = pendingEdits - edit
                publish { it.copy(hasLoaded = true, errorMessage = null) }
                true
            }
            if (applied) _changes.tryEmit(Unit)
        }.await()
    }

    /** Call with [lock] held. */
    private inline fun publish(change: (LibraryState) -> LibraryState = { it }) {
        val items = pendingEdits.fold(serverItems) { items, edit -> edit.applyTo(items) }
        _state.update { change(it).copy(items = items) }
    }

    private sealed interface PendingEdit {
        fun applyTo(items: List<LibraryItemImpl>): List<LibraryItemImpl>

        data class Remove(val itemId: String) : PendingEdit {
            override fun applyTo(items: List<LibraryItemImpl>) = items.filterNot { it.id == itemId }
        }

        data class Update(val itemId: String, val format: MediaFormat, val condition: ItemCondition) : PendingEdit {
            override fun applyTo(items: List<LibraryItemImpl>) =
                items.map { if (it.id == itemId) it.copy(format = format, condition = condition) else it }
        }
    }

    private companion object {
        const val LOAD_FAILED = "Couldn't load your library. Please try again."
    }
}
