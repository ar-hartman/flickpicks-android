package com.flykpicks.android.testing

import com.flykpicks.android.service.AssetService
import com.flykpicks.android.service.GenreService
import com.flykpicks.android.service.LibraryService
import com.flykpicks.android.service.RecommendationsService
import com.flykpicks.android.service.SearchService
import com.flykpicks.android.store.GenreStore
import com.flykpicks.android.store.LibraryStore
import com.flykpicks.android.store.RecommendationSection
import com.flykpicks.android.store.RecommendationsStore
import com.flykpicks.android.store.SearchStore
import kotlinx.coroutines.CoroutineScope

/** The app-wide stores over [net], wired to each other as Hilt wires them. */
class TestStores(net: TestNetwork, scope: CoroutineScope) {
    val genres = GenreStore(GenreService(net.api(), net.genres))
    val library = LibraryStore(LibraryService(net.api(), net.genres), genres, scope)
    val search = SearchStore(SearchService(net.api(), net.genres), genres)
    val recommendations = RecommendationsStore(RecommendationsService(net.api(), net.genres), genres, library, search, scope)
    val asset = AssetService(net.api(), net.genres)
}

/** A film as search, the recommendation rows and `GET v1/media/{id}` return it. Genre ids resolve through [GENRES_JSON]. */
fun movieItemJson(flyksId: String, title: String, genreIds: String = "[18]") = """
{
  "id": "tmdb-$flyksId", "flyksId": "$flyksId", "title": "$title", "overview": "About $title.",
  "media_type": "movie", "original_language": "en", "genre_ids": $genreIds, "popularity": 1.0,
  "release_date": "1995-12-15", "vote_average": 7.0, "vote_count": 10, "posterUrl": null
}
"""

/** A series, as [movieItemJson]. */
fun tvItemJson(flyksId: String, title: String, genreIds: String = "[9648]") = """
{
  "id": "tmdb-$flyksId", "flyksId": "$flyksId", "name": "$title", "overview": "About $title.",
  "media_type": "tv", "original_language": "en", "genre_ids": $genreIds, "popularity": 1.0,
  "first_air_date": "1990-04-08", "vote_average": 8.0, "vote_count": 10, "posterUrl": null
}
"""

fun mediaListJson(vararg items: String) = "[${items.joinToString(",")}]"

const val SEARCH_PATH = "v1/search"

val RecommendationSection.path: String
    get() = when (this) {
        RecommendationSection.INVENTORY_MOVIES -> "v1/users/recommendations/movies/inventory"
        RecommendationSection.SEARCH_HISTORY_MOVIES -> "v1/users/recommendations/movies/searchHistory"
        RecommendationSection.INVENTORY_TV -> "v1/users/recommendations/tv/inventory"
        RecommendationSection.SEARCH_HISTORY_TV -> "v1/users/recommendations/tv/searchHistory"
        RecommendationSection.POPULAR_MOVIES -> "v1/users/recommendations/movies/popular"
        RecommendationSection.TOP_RATED_MOVIES -> "v1/users/recommendations/movies/topRated"
        RecommendationSection.POPULAR_TV -> "v1/users/recommendations/tv/popular"
        RecommendationSection.TOP_RATED_TV -> "v1/users/recommendations/tv/topRated"
    }

/** Blocks until [condition] holds, for state that settles on another thread. */
fun eventually(condition: () -> Boolean) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (!condition()) {
        check(System.nanoTime() < deadline) { "Condition not met within 10 s" }
        Thread.sleep(5)
    }
}
