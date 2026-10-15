package com.flykpicks.android.ui.screens.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flykpicks.android.api.userFacingMessage
import com.flykpicks.android.model.LibraryItemImpl
import com.flykpicks.android.model.SeasonSummaryDTO
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.service.AssetService
import com.flykpicks.android.store.LibraryState
import com.flykpicks.android.store.LibraryStore
import com.flykpicks.android.ui.components.EditLibraryItemState
import com.flykpicks.android.ui.components.ToastStore
import com.flykpicks.android.ui.navigation.NavRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The copy open in the edit sheet, and what the sheet shows. */
data class LibraryItemEdit(val itemId: String, val sheet: EditLibraryItemState)

/**
 * One owned title: its copies, and for a series its seasons (iOS `LibraryItemDetailView` and
 * `TVSeasonDetailView`). The season screen works on what the title screen fetched, so the two
 * share this ViewModel, scoped to the title screen.
 *
 * Actions return their [Job] so tests can wait for them; the UI ignores it.
 */
@HiltViewModel
class LibraryItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val store: LibraryStore,
    private val assetService: AssetService,
    private val toastStore: ToastStore,
) : ViewModel() {

    /** The catalog title id. Every copy of the title shares it. */
    val titleId: String = checkNotNull(savedStateHandle[NavRoutes.ARG_TITLE_ID]) { "Missing title id" }

    val library: StateFlow<LibraryState> = store.state

    /** Every season of the series, owned or not. Empty for films, and when the fetch failed. */
    var seasons by mutableStateOf<List<SeasonSummaryDTO>>(emptyList())
        private set

    var seasonsLoading by mutableStateOf(false)
        private set

    var edit by mutableStateOf<LibraryItemEdit?>(null)
        private set

    /** A season change or a copy's removal is in flight; the buttons that start one are disabled. */
    var isUpdating by mutableStateOf(false)
        private set

    var updateError by mutableStateOf<String?>(null)
        private set

    /** The copy that would lose its last season, awaiting "Remove this copy?". */
    var pendingCopyRemoval by mutableStateOf<String?>(null)
        private set

    /** Set once a copy is removed from the season screen, which then closes, as on iOS. */
    var seasonScreenShouldClose by mutableStateOf(false)
        private set

    /**
     * While true, an empty title isn't final: a removal shown ahead of the server's answer may
     * yet be undone. The screens wait for it before closing themselves.
     */
    val isBusy: Boolean get() = isUpdating || edit?.sheet?.isBusy == true

    init {
        load()
    }

    /** Also the retry when the title couldn't be shown. */
    fun load(): Job = viewModelScope.launch {
        // Restored after process death, the store starts empty and the title can't be found.
        if (!store.state.value.hasLoaded) store.load()
        val title = store.state.value.items(titleId).firstOrNull() ?: return@launch
        if (title.mediaType != MediaType.TV || seasonsLoading || seasons.isNotEmpty()) return@launch
        seasonsLoading = true
        try {
            seasons = assetService.fetchSeasons(titleId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The screen says "Season information unavailable."
            seasons = emptyList()
        } finally {
            seasonsLoading = false
        }
    }

    // MARK: Edit sheet

    /** [title] and [posterUrl] are what the sheet's header shows; the season screen passes its own. */
    fun startEditing(
        copy: LibraryItemImpl,
        title: String = copy.title,
        posterUrl: String? = copy.posterUrl,
        canRemove: Boolean = true,
    ) {
        edit = LibraryItemEdit(
            itemId = copy.id,
            sheet = EditLibraryItemState(title, posterUrl, copy.format, copy.condition, canRemove = canRemove),
        )
    }

    fun selectFormat(format: MediaFormat) = updateSheet { it.copy(format = format) }

    fun selectCondition(condition: ItemCondition) = updateSheet { it.copy(condition = condition) }

    fun cancelEdit() {
        edit = null
    }

    fun saveEdit(): Job = viewModelScope.launch {
        val editing = edit ?: return@launch
        if (editing.sheet.isBusy || !editing.sheet.hasChanges) return@launch
        updateSheet(editing.itemId) { it.copy(isSaving = true, errorMessage = null) }
        try {
            store.updateItem(editing.itemId, editing.sheet.format, editing.sheet.condition)
            closeSheet(editing.itemId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateSheet(editing.itemId) { it.copy(isSaving = false, errorMessage = e.userFacingMessage) }
        }
    }

    /** Removes the copy being edited. When it was the title's last, the screen closes itself. */
    fun removeEdited(): Job = viewModelScope.launch {
        val editing = edit ?: return@launch
        if (editing.sheet.isBusy) return@launch
        updateSheet(editing.itemId) { it.copy(isRemoving = true, errorMessage = null) }
        try {
            store.removeItem(editing.itemId)
            toastStore.show("Removed from library")
            closeSheet(editing.itemId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateSheet(editing.itemId) { it.copy(isRemoving = false, errorMessage = e.userFacingMessage) }
        }
    }

    // A request can outlive the sheet it started from, so its result only touches that sheet.
    private fun updateSheet(itemId: String? = null, change: (EditLibraryItemState) -> EditLibraryItemState) {
        val current = edit ?: return
        if (itemId == null || current.itemId == itemId) edit = current.copy(sheet = change(current.sheet))
    }

    private fun closeSheet(itemId: String) {
        if (edit?.itemId == itemId) edit = null
    }

    // MARK: Seasons

    /** Gives [copy] every season the catalog lists. */
    fun addAllSeasons(copy: LibraryItemImpl): Job = updateSeasons {
        store.updateOwnedSeasons(copy.id, ownedSeasons = null, selectAllSeasons = true)
    }

    fun addSeason(copy: LibraryItemImpl, seasonNumber: Int): Job = updateSeasons {
        val seasons = (copy.ownedSeasons.orEmpty() + seasonNumber).distinct().sorted()
        store.updateOwnedSeasons(copy.id, seasons, selectAllSeasons = false)
    }

    /**
     * Takes one season off a copy that lists its seasons. The server won't store an empty list,
     * so when it is the copy's last season this asks whether to remove the copy instead (and
     * returns null: nothing was sent).
     */
    fun removeSeason(copy: LibraryItemImpl, seasonNumber: Int): Job? {
        val remaining = copy.ownedSeasons?.filter { it != seasonNumber } ?: return null
        if (remaining.isEmpty()) {
            pendingCopyRemoval = copy.id
            return null
        }
        return updateSeasons { store.updateOwnedSeasons(copy.id, remaining.sorted(), selectAllSeasons = false) }
    }

    fun cancelCopyRemoval() {
        pendingCopyRemoval = null
    }

    fun confirmCopyRemoval(): Job {
        val itemId = pendingCopyRemoval
        pendingCopyRemoval = null
        return updateSeasons {
            if (itemId == null) return@updateSeasons
            store.removeItem(itemId)
            seasonScreenShouldClose = true
        }
    }

    fun onSeasonScreenClosed() {
        seasonScreenShouldClose = false
    }

    fun dismissUpdateError() {
        updateError = null
    }

    private fun updateSeasons(change: suspend () -> Unit): Job = viewModelScope.launch {
        if (isUpdating) return@launch
        isUpdating = true
        try {
            change()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateError = e.userFacingMessage
        } finally {
            isUpdating = false
        }
    }
}
