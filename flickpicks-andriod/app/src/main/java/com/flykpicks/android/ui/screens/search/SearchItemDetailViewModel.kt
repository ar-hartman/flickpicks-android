package com.flykpicks.android.ui.screens.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flykpicks.android.api.userFacingMessage
import com.flykpicks.android.di.ApplicationScope
import com.flykpicks.android.model.MediaItemImpl
import com.flykpicks.android.model.SeasonSummaryDTO
import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.service.AssetService
import com.flykpicks.android.store.LibraryState
import com.flykpicks.android.store.LibraryStore
import com.flykpicks.android.store.RecommendationsStore
import com.flykpicks.android.store.SearchStore
import com.flykpicks.android.ui.components.AddToLibraryState
import com.flykpicks.android.ui.navigation.NavRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A title opened from Discover (blueprint §10.6, iOS `SearchItemDetailView`) and its "Add to
 * Inventory" sheet.
 *
 * The route carries the title's id and the `X-Flyk-Source` of the row it was opened from. The
 * title itself is the one that row shows, found in [SearchStore] or [RecommendationsStore]; after
 * process death those are empty, and it comes from the server instead.
 *
 * Actions return their [Job] so tests can wait for them; the UI ignores it.
 */
@HiltViewModel
class SearchItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    searchStore: SearchStore,
    recommendationsStore: RecommendationsStore,
    private val libraryStore: LibraryStore,
    private val assetService: AssetService,
    @ApplicationScope appScope: CoroutineScope,
) : ViewModel() {

    val flyksId: String = checkNotNull(savedStateHandle[NavRoutes.ARG_FLYKS_ID]) { "Missing flyksId" }

    /** Where the title was opened from; sent with every request this screen makes. */
    val source: String = checkNotNull(savedStateHandle[NavRoutes.ARG_SOURCE]) { "Missing source" }

    /** Null only while a title restored after process death is being fetched, or if that failed. */
    var item by mutableStateOf(searchStore.item(flyksId) ?: recommendationsStore.state.value.item(flyksId))
        private set

    /** Why the title couldn't be fetched, when there was nothing else to show. */
    var loadError by mutableStateOf<String?>(null)
        private set

    /** Every season of a series, for the add sheet. Empty for films, and when the fetch failed. */
    var seasons by mutableStateOf<List<SeasonSummaryDTO>>(emptyList())
        private set

    var seasonsLoading by mutableStateOf(false)
        private set

    var addSheet by mutableStateOf<AddToLibraryState?>(null)
        private set

    /** For "In Your Library". */
    val library: StateFlow<LibraryState> = libraryStore.state

    /**
     * The backend counts this request as the view of the title, attributed to [source]. It runs
     * in the application scope so that backing out at once still lets it through, as iOS's
     * unstructured task does. The screen waits for it only when it has nothing else to show.
     */
    private val attribution: Deferred<Result<MediaItemImpl>> = appScope.async { fetchItem() }

    init {
        if (item != null) loadSeasons() else viewModelScope.launch { show(attribution.await()) }
    }

    /** After a failed fetch of a restored title. */
    fun retry(): Job = viewModelScope.launch {
        loadError = null
        show(fetchItem())
    }

    // MARK: Add sheet

    fun openAddSheet() {
        val item = item ?: return
        addSheet = AddToLibraryState(isTV = item.mediaType == MediaType.TV)
    }

    /** Not while saving: the sheet stays until the requests have answered. */
    fun cancelAddSheet() {
        if (addSheet?.isSaving != true) addSheet = null
    }

    fun editAddSheet(change: (AddToLibraryState) -> AddToLibraryState) {
        val sheet = addSheet ?: return
        if (!sheet.isSaving) addSheet = change(sheet)
    }

    /**
     * Sends the sheet's requests one after another (several when seasons were picked in different
     * formats or conditions) and closes the sheet once all are in. If one fails, the seasons
     * already added drop out of the selection, so trying again doesn't add them twice (iOS does,
     * ios-gaps.md G34).
     */
    fun saveToLibrary(): Job = viewModelScope.launch {
        val item = item ?: return@launch
        val sheet = addSheet ?: return@launch
        if (sheet.isSaving || !sheet.canSave) return@launch
        addSheet = sheet.copy(isSaving = true, errorMessage = null)
        val added = mutableListOf<Int>()
        for (request in sheet.requests()) {
            try {
                libraryStore.addFromSearchItem(
                    item,
                    request.format,
                    request.condition,
                    ownedSeasons = request.seasons,
                    selectAllSeasons = request.seasons == null,
                )
                request.seasons?.let(added::addAll)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = if (added.isEmpty()) e.userFacingMessage else "Added ${seasonList(added)}. ${e.userFacingMessage}"
                addSheet = addSheet?.copy(isSaving = false, selectedSeasons = sheet.selectedSeasons - added.toSet(), errorMessage = message)
                return@launch
            }
        }
        addSheet = null
    }

    private suspend fun fetchItem(): Result<MediaItemImpl> =
        try {
            Result.success(assetService.fetchMediaItem(flyksId, source))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun show(fetched: Result<MediaItemImpl>) {
        if (item != null) return
        fetched
            .onSuccess {
                item = it
                loadSeasons()
            }
            .onFailure { loadError = it.userFacingMessage }
    }

    // A failure leaves the list empty, and the sheet says "Season list unavailable", as on iOS.
    private fun loadSeasons() {
        if (item?.mediaType != MediaType.TV) return
        viewModelScope.launch {
            seasonsLoading = true
            seasons = try {
                assetService.fetchSeasons(flyksId, source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            seasonsLoading = false
        }
    }
}

/** "Season 2", "Seasons 1, 3". */
internal fun seasonList(seasons: List<Int>): String {
    val sorted = seasons.sorted()
    return if (sorted.size == 1) "Season ${sorted.single()}" else "Seasons ${sorted.joinToString(", ")}"
}
