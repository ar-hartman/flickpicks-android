package com.flykpicks.android.store

import com.flykpicks.android.api.endpoints.FlykSource
import com.flykpicks.android.api.userFacingMessage
import com.flykpicks.android.di.ApplicationScope
import com.flykpicks.android.model.MediaItemImpl
import com.flykpicks.android.service.RecommendationsService
import com.flykpicks.android.store.RecommendationRefreshTrigger.LIBRARY_CHANGED
import com.flykpicks.android.store.RecommendationRefreshTrigger.SEARCH_PERFORMED
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What makes a Discover row out of date (iOS `RecommendationRefreshTrigger`). */
enum class RecommendationRefreshTrigger { LIBRARY_CHANGED, SEARCH_PERFORMED }

/**
 * The eight Discover rows, in the order iOS shows them (iOS `RecommendationSection`). [source] is
 * the row's `X-Flyk-Source`, sent with its fetch and by the detail screens opened from it.
 * [staleAfter] is what makes the row reload; rows without one are fetched once per session.
 */
enum class RecommendationSection(
    val displayTitle: String,
    val source: String,
    val staleAfter: RecommendationRefreshTrigger?,
) {
    INVENTORY_MOVIES("Because You Own It · Movies", FlykSource.INVENTORY_MOVIES, LIBRARY_CHANGED),
    SEARCH_HISTORY_MOVIES("Based on Searches · Movies", FlykSource.SEARCH_HISTORY_MOVIES, SEARCH_PERFORMED),
    INVENTORY_TV("Because You Own It · TV", FlykSource.INVENTORY_TV, LIBRARY_CHANGED),
    SEARCH_HISTORY_TV("Based on Searches · TV", FlykSource.SEARCH_HISTORY_TV, SEARCH_PERFORMED),
    POPULAR_MOVIES("Popular Movies", FlykSource.POPULAR_MOVIES, null),
    TOP_RATED_MOVIES("Top Rated Movies", FlykSource.TOP_RATED_MOVIES, null),
    POPULAR_TV("Popular TV", FlykSource.POPULAR_TV, null),
    TOP_RATED_TV("Top Rated TV", FlykSource.TOP_RATED_TV, null),
}

data class RecommendationSectionState(
    val items: List<MediaItemImpl> = emptyList(),
    val isLoading: Boolean = false,
    /** Set when the row's last fetch failed; [items] keeps what was there before. */
    val errorMessage: String? = null,
)

/** Blueprint §9.4. */
data class RecommendationsState(
    val sections: Map<RecommendationSection, RecommendationSectionState> = emptySections(),
    /** True once every row has been fetched at least once, whether or not it succeeded. */
    val hasLoadedOnce: Boolean = false,
) {
    fun section(section: RecommendationSection): RecommendationSectionState =
        sections[section] ?: RecommendationSectionState()

    /** A title shown in any row. */
    fun item(flyksId: String): MediaItemImpl? =
        sections.values.firstNotNullOfOrNull { row -> row.items.firstOrNull { it.flyksId == flyksId } }
}

private fun emptySections() = RecommendationSection.entries.associateWith { RecommendationSectionState() }

/**
 * The Discover rows (blueprint §9.4, iOS `RecommendationsStore`), app-wide: the Library tab
 * starts them loading and library changes mark them stale while Discover isn't showing.
 *
 * Popular and top-rated rows are fetched once per session. The "Because You Own It" rows reload
 * whenever the library changes, and the "Based on Searches" rows after every search; like iOS,
 * they reload right away rather than the next time Discover appears. Search-history rows leave
 * out what the matching "Because You Own It" row shows.
 *
 * Each row keeps only the answer to the latest fetch of it, so a reload overtaken by another
 * (or by a reset) can't bring back older rows. Fetches run in the application scope.
 */
@Singleton
class RecommendationsStore @Inject constructor(
    private val service: RecommendationsService,
    private val genreStore: GenreStore,
    libraryStore: LibraryStore,
    searchStore: SearchStore,
    @ApplicationScope private val scope: CoroutineScope,
) : PerUserState {

    private val _state = MutableStateFlow(RecommendationsState())
    val state: StateFlow<RecommendationsState> = _state.asStateFlow()

    // Guarded by `lock`.
    private val lock = Any()

    /** What each row's last fetch returned, before search history is deduplicated. */
    private var rows = emptySections()
    private var hasLoadedOnce = false

    /** The latest fetch of each row. An answer carrying another ticket is dropped. */
    private val tickets = HashMap<RecommendationSection, Int>()
    private var lastTicket = 0

    /** Counts [reset]s. A load that belongs to an earlier session doesn't mark this one loaded. */
    private var session = 0
    private var loadJob: Job? = null
    private val rowJobs = HashMap<RecommendationSection, Job>()

    init {
        scope.launch { libraryStore.changes.collect { markStale(LIBRARY_CHANGED) } }
        scope.launch { searchStore.completions.collect { markStale(SEARCH_PERFORMED) } }
    }

    /** Fetches every row the first time it is called. Callers arriving mid-load wait for it. */
    suspend fun loadIfNeeded() {
        val job = synchronized(lock) {
            if (hasLoadedOnce) return
            loadJob?.takeIf { it.isActive } ?: startLoad()
        }
        job.join()
    }

    /** Fetches every row again (pull to refresh). Joins a load already in flight. */
    suspend fun refresh() {
        val job = synchronized(lock) { loadJob?.takeIf { it.isActive } ?: startLoad() }
        job.join()
    }

    /** Fetches one row again (its "Retry"). */
    fun refreshSection(section: RecommendationSection): Job = synchronized(lock) { startRow(section) }

    /**
     * Reloads the rows [trigger] makes stale. Rows that have never been fetched are left alone:
     * the first load gets them fresh. iOS also ignores a change that lands while that first load
     * is in flight, which can leave the load's answer out of date (ios-gaps.md G35).
     */
    fun markStale(trigger: RecommendationRefreshTrigger) {
        synchronized(lock) {
            if (!hasLoadedOnce && loadJob?.isActive != true) return
            RecommendationSection.entries.filter { it.staleAfter == trigger }.forEach(::startRow)
        }
    }

    override fun reset() {
        synchronized(lock) {
            session++
            loadJob?.cancel()
            loadJob = null
            rowJobs.values.forEach { it.cancel() }
            rowJobs.clear()
            tickets.clear()
            rows = emptySections()
            hasLoadedOnce = false
            _state.value = RecommendationsState()
        }
    }

    /** Call with [lock] held. */
    private fun startLoad(): Job {
        val startedIn = session
        val fetches = RecommendationSection.entries.associateWith { section ->
            rowJobs.remove(section)?.cancel()
            beginFetch(section)
        }
        publish()
        return scope.launch {
            // Titles get their genre names as they're mapped, so the table has to be there first.
            genreStore.loadIfNeeded()
            coroutineScope {
                for ((section, ticket) in fetches) launch { fetch(section, ticket) }
            }
            synchronized(lock) {
                if (startedIn != session) return@launch
                hasLoadedOnce = true
                publish()
            }
        }.also { loadJob = it }
    }

    /** Call with [lock] held. */
    private fun startRow(section: RecommendationSection): Job {
        rowJobs.remove(section)?.cancel()
        val ticket = beginFetch(section)
        publish()
        return scope.launch {
            genreStore.loadIfNeeded()
            fetch(section, ticket)
        }.also { rowJobs[section] = it }
    }

    /** Call with [lock] held. Shows the row as loading; returns the ticket its answer must carry. */
    private fun beginFetch(section: RecommendationSection): Int {
        val ticket = ++lastTicket
        tickets[section] = ticket
        rows = rows + (section to rows.getValue(section).copy(isLoading = true, errorMessage = null))
        return ticket
    }

    private suspend fun fetch(section: RecommendationSection, ticket: Int) {
        val result = try {
            Result.success(request(section))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        synchronized(lock) {
            if (tickets[section] != ticket) return
            rows = rows + (
                section to result.fold(
                    onSuccess = { RecommendationSectionState(items = it) },
                    onFailure = { rows.getValue(section).copy(isLoading = false, errorMessage = it.userFacingMessage) },
                )
            )
            publish()
        }
    }

    private suspend fun request(section: RecommendationSection): List<MediaItemImpl> = when (section) {
        RecommendationSection.INVENTORY_MOVIES -> service.inventoryMovies()
        RecommendationSection.SEARCH_HISTORY_MOVIES -> service.searchHistoryMovies()
        RecommendationSection.INVENTORY_TV -> service.inventoryTV()
        RecommendationSection.SEARCH_HISTORY_TV -> service.searchHistoryTV()
        RecommendationSection.POPULAR_MOVIES -> service.popularMovies()
        RecommendationSection.TOP_RATED_MOVIES -> service.topRatedMovies()
        RecommendationSection.POPULAR_TV -> service.popularTV()
        RecommendationSection.TOP_RATED_TV -> service.topRatedTV()
    }

    /** Call with [lock] held. */
    private fun publish() {
        _state.value = RecommendationsState(
            sections = rows.mapValues { (section, row) -> row.copy(items = shownItems(section, row.items)) },
            hasLoadedOnce = hasLoadedOnce,
        )
    }

    /**
     * A search-history row leaves out the titles its "Because You Own It" row shows, as on iOS.
     * Worked out each time from the rows' own answers, so a title comes back once the other
     * row stops showing it. (iOS filters its copy of the row and doesn't.)
     */
    private fun shownItems(section: RecommendationSection, items: List<MediaItemImpl>): List<MediaItemImpl> {
        val owned = when (section) {
            RecommendationSection.SEARCH_HISTORY_MOVIES -> RecommendationSection.INVENTORY_MOVIES
            RecommendationSection.SEARCH_HISTORY_TV -> RecommendationSection.INVENTORY_TV
            else -> return items
        }
        val shownAbove = rows.getValue(owned).items.mapTo(HashSet()) { it.flyksId }
        return items.filterNot { it.flyksId in shownAbove }
    }
}
