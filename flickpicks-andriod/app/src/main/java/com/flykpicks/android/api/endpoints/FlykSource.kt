package com.flykpicks.android.api.endpoints

/**
 * `X-Flyk-Source` attribution values, as sent by iOS. Tells the backend which surface drove a
 * request. (The blueprint's §13.2 `discover.*` names are not what the server receives.)
 */
object FlykSource {
    const val HEADER = "X-Flyk-Source"

    const val SEARCH_RESULTS = "search.results"
    const val SEARCH_BARCODE = "search.barcode"
    const val LIBRARY_DETAIL = "library.detail"

    const val POPULAR_MOVIES = "recommendation.popularMovies"
    const val TOP_RATED_MOVIES = "recommendation.topRatedMovies"
    const val POPULAR_TV = "recommendation.popularTV"
    const val TOP_RATED_TV = "recommendation.topRatedTV"
    const val INVENTORY_MOVIES = "recommendation.inventoryMovies"
    const val INVENTORY_TV = "recommendation.inventoryTV"
    const val SEARCH_HISTORY_MOVIES = "recommendation.searchHistoryMovies"
    const val SEARCH_HISTORY_TV = "recommendation.searchHistoryTV"
}
