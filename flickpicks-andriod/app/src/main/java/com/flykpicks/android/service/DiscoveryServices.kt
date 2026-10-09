package com.flykpicks.android.service

import com.flykpicks.android.api.ApiError
import com.flykpicks.android.api.endpoints.BarcodeSubmission
import com.flykpicks.android.api.endpoints.FlykSource
import com.flykpicks.android.api.endpoints.GenreApi
import com.flykpicks.android.api.endpoints.MediaApi
import com.flykpicks.android.api.endpoints.RecommendationsApi
import com.flykpicks.android.api.endpoints.SearchApi
import com.flykpicks.android.api.UserFacingError
import com.flykpicks.android.model.MediaItemImpl
import com.flykpicks.android.model.SeasonSummaryDTO
import com.flykpicks.android.model.toMediaItemImpl
import com.flykpicks.android.util.GenreTranslator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchService @Inject constructor(
    private val api: SearchApi,
    private val genres: GenreTranslator,
) {
    suspend fun fetchTitles(query: String): List<MediaItemImpl> =
        api.search(query).map { it.toMediaItemImpl(genres) }
}

/** The eight discovery rows. Malformed items are skipped rather than failing a row. */
@Singleton
class RecommendationsService @Inject constructor(
    private val api: RecommendationsApi,
    private val genres: GenreTranslator,
) {
    suspend fun popularMovies() = api.popularMovies().map { it.toMediaItemImpl(genres) }
    suspend fun topRatedMovies() = api.topRatedMovies().map { it.toMediaItemImpl(genres) }
    suspend fun inventoryMovies() = api.inventoryMovies().map { it.toMediaItemImpl(genres) }
    suspend fun searchHistoryMovies() = api.searchHistoryMovies().map { it.toMediaItemImpl(genres) }
    suspend fun popularTV() = api.popularTV().map { it.toMediaItemImpl(genres) }
    suspend fun topRatedTV() = api.topRatedTV().map { it.toMediaItemImpl(genres) }
    suspend fun inventoryTV() = api.inventoryTV().map { it.toMediaItemImpl(genres) }
    suspend fun searchHistoryTV() = api.searchHistoryTV().map { it.toMediaItemImpl(genres) }
}

/** Catalog reads for detail screens (iOS `AssetService`). */
@Singleton
class AssetService @Inject constructor(
    private val api: MediaApi,
    private val genres: GenreTranslator,
) {
    /** Every season of a series, including ones the user doesn't own. */
    suspend fun fetchSeasons(flyksId: String, source: String = FlykSource.LIBRARY_DETAIL): List<SeasonSummaryDTO> =
        api.seasons(flyksId, source)

    /** Attribution beacon for a detail view. Fire without blocking the screen on it. */
    suspend fun fetchMediaItem(flyksId: String, source: String): MediaItemImpl =
        api.mediaItem(flyksId, source).toMediaItemImpl(genres)
}

/** Loads `/v1/genres` into [GenreTranslator]. */
@Singleton
class GenreService @Inject constructor(
    private val api: GenreApi,
    private val genres: GenreTranslator,
) {
    /** Movie and TV tables are merged; their id spaces don't overlap. */
    suspend fun load() {
        val combined = buildMap {
            for (table in api.fetchGenres().values) {
                for ((id, name) in table) id.toIntOrNull()?.let { put(it, name) }
            }
        }
        genres.configure(combined)
    }
}

sealed class BarcodeServiceError(detail: String) : Exception(detail), UserFacingError {
    data object NoTMDBMatch : BarcodeServiceError("no_match") {
        override val userMessage
            get() = "No TMDB match found. Try adjusting the title or year and searching again."
    }

    data object UpstreamUnavailable : BarcodeServiceError("upstream_unavailable") {
        override val userMessage get() = "TMDB is temporarily unavailable. Please try again in a moment."
    }
}

/**
 * UPC lookup and unknown-title submission. The endpoints are pending on the backend (iOS DEP-12,
 * where they are stubbed out — ios-gaps.md G13); until they ship, lookup 404s and returns null.
 */
@Singleton
class BarcodeService @Inject constructor(
    private val api: MediaApi,
    private val genres: GenreTranslator,
) {
    /** Null when the UPC isn't in the catalog; the UI then offers the submission form. */
    suspend fun lookupBarcode(upc: String): MediaItemImpl? =
        try {
            api.barcode(upc).toMediaItemImpl(genres)
        } catch (e: ApiError.Http) {
            if (e.status == 404) null else throw e
        }

    /** The backend matches the submission against TMDB and returns the enriched title. */
    suspend fun submitUnknownTitle(submission: BarcodeSubmission): MediaItemImpl =
        mapHttpErrors({
            when {
                it.status == 422 && it.serverCode == "no_match" -> BarcodeServiceError.NoTMDBMatch
                it.status == 502 -> BarcodeServiceError.UpstreamUnavailable
                else -> null
            }
        }) { api.submitTitle(submission).item.toMediaItemImpl(genres) }
}
