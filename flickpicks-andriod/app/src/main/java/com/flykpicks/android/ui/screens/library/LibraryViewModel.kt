package com.flykpicks.android.ui.screens.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flykpicks.android.api.userFacingMessage
import com.flykpicks.android.model.LibraryItemImpl
import com.flykpicks.android.store.LibraryState
import com.flykpicks.android.store.LibraryStore
import com.flykpicks.android.store.RecommendationsStore
import com.flykpicks.android.util.GenreSection
import com.flykpicks.android.util.groupByPrimaryGenre
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row of the Library screen: the copies whose first genre is [GenreSection.genre]. */
typealias LibrarySection = GenreSection<LibraryItemImpl>

/**
 * Groups copies into rows by their first genre (iOS `LibraryView.itemsByGenre`). Only titles
 * containing [query] are kept. iOS computes that filter but never applies it, so its search
 * field does nothing (ios-gaps.md G22).
 */
fun librarySections(items: List<LibraryItemImpl>, query: String): List<LibrarySection> {
    val needle = query.trim()
    val matching = items.filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }
    return groupByPrimaryGenre(matching, genre = { it.genre }, title = { it.title })
}

/**
 * The Library tab's first screen (blueprint §10.7, iOS `LibraryView`). The library itself lives
 * in [LibraryStore]; this holds what belongs to the screen.
 *
 * Actions return their [Job] so tests can wait for them; the UI ignores it.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val store: LibraryStore,
    private val recommendations: RecommendationsStore,
) : ViewModel() {

    val library: StateFlow<LibraryState> = store.state

    var query by mutableStateOf("")

    /** The copy the user asked to remove and hasn't confirmed yet. */
    var pendingRemoval by mutableStateOf<LibraryItemImpl?>(null)
        private set

    var removalError by mutableStateOf<String?>(null)
        private set

    /**
     * Loads the library, then starts Discover's rows loading in the background, as iOS does, so
     * they are usually ready by the time the user gets there. The returned job doesn't wait for them.
     */
    fun load(): Job = viewModelScope.launch {
        store.load()
        viewModelScope.launch { recommendations.loadIfNeeded() }
    }

    fun requestRemoval(item: LibraryItemImpl) {
        pendingRemoval = item
    }

    fun cancelRemoval() {
        pendingRemoval = null
    }

    fun confirmRemoval(): Job = viewModelScope.launch {
        val item = pendingRemoval ?: return@launch
        pendingRemoval = null
        try {
            store.removeItem(item.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            removalError = e.userFacingMessage
        }
    }

    fun dismissRemovalError() {
        removalError = null
    }
}
