package com.flykpicks.android.store

import com.flykpicks.android.api.userFacingMessage
import com.flykpicks.android.model.MediaItemImpl
import com.flykpicks.android.service.SearchService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The last search (blueprint §9.3). */
data class SearchResults(
    /** What was searched for; null when there is no search. */
    val query: String? = null,
    val items: List<MediaItemImpl> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Changes with every search, so the screen can start new results scrolled to the top. */
    val generation: Int = 0,
)

/**
 * The last search's results (iOS `SearchStore`). The Discover screen drives it and clears it
 * when the user leaves the tab; detail screens look up the title they were opened for here.
 */
@Singleton
class SearchStore @Inject constructor(
    private val service: SearchService,
    private val genreStore: GenreStore,
) : PerUserState {

    private val _state = MutableStateFlow(SearchResults())
    val state: StateFlow<SearchResults> = _state.asStateFlow()

    private val _completions = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Emits after every search the server answered (iOS `onDidCompleteSearch`). The server has
     * added it to the search history the recommendations are built from.
     */
    val completions: SharedFlow<Unit> = _completions.asSharedFlow()

    // Guarded by `lock`.
    private val lock = Any()
    private var generation = 0

    fun item(flyksId: String): MediaItemImpl? = _state.value.items.firstOrNull { it.flyksId == flyksId }

    /**
     * Searches for [query], replacing the last results. Runs in the caller's scope, so it stops
     * when the caller does. A newer search, or [clear], drops this one's answer.
     */
    suspend fun search(query: String) {
        val ticket = synchronized(lock) {
            generation++
            _state.value = SearchResults(query, isLoading = true, generation = generation)
            generation
        }
        val answered = try {
            // Titles get their genre names as they're mapped, so the table has to be there first.
            genreStore.loadIfNeeded()
            val items = service.fetchTitles(query)
            publish(ticket) { SearchResults(query, items, generation = ticket) }
        } catch (e: CancellationException) {
            publish(ticket) { it.copy(isLoading = false) }
            throw e
        } catch (e: Exception) {
            // iOS keeps the raw error text and then shows "No results found" anyway (ios-gaps.md G5).
            publish(ticket) { SearchResults(query, errorMessage = e.userFacingMessage, generation = ticket) }
            false
        }
        if (answered) _completions.tryEmit(Unit)
    }

    /** Forgets the results, and drops the answer to any search still in flight. */
    fun clear() {
        synchronized(lock) {
            generation++
            _state.value = SearchResults(generation = generation)
        }
    }

    override fun reset() = clear()

    /** Applies [change] if the search holding [ticket] is still the latest. */
    private inline fun publish(ticket: Int, change: (SearchResults) -> SearchResults): Boolean =
        synchronized(lock) {
            if (ticket != generation) return false
            _state.value = change(_state.value)
            true
        }
}
