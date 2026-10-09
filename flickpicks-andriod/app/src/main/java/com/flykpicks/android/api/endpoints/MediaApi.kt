package com.flykpicks.android.api.endpoints

import com.flykpicks.android.api.json.Lenient
import com.flykpicks.android.model.MediaItem
import com.flykpicks.android.model.MediaMovieItemDTO
import com.flykpicks.android.model.MediaTVItemDTO
import com.flykpicks.android.model.SeasonSummaryDTO
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SearchApi {
    /** Lenient: a malformed result is skipped instead of failing the search (ios-gaps.md G15). */
    @Lenient
    @Headers(FlykSource.HEADER + ": " + FlykSource.SEARCH_RESULTS)
    @GET("v1/search")
    suspend fun search(@Query("query") query: String): List<MediaItem>
}

/** Recommendation rows. Lenient like iOS `LenientArray`: bad items are skipped. */
interface RecommendationsApi {
    @Lenient
    @Headers(FlykSource.HEADER + ": " + FlykSource.POPULAR_MOVIES)
    @GET("v1/users/recommendations/movies/popular")
    suspend fun popularMovies(): List<MediaMovieItemDTO>

    @Lenient
    @Headers(FlykSource.HEADER + ": " + FlykSource.TOP_RATED_MOVIES)
    @GET("v1/users/recommendations/movies/topRated")
    suspend fun topRatedMovies(): List<MediaMovieItemDTO>

    @Lenient
    @Headers(FlykSource.HEADER + ": " + FlykSource.INVENTORY_MOVIES)
    @GET("v1/users/recommendations/movies/inventory")
    suspend fun inventoryMovies(): List<MediaMovieItemDTO>

    @Lenient
    @Headers(FlykSource.HEADER + ": " + FlykSource.SEARCH_HISTORY_MOVIES)
    @GET("v1/users/recommendations/movies/searchHistory")
    suspend fun searchHistoryMovies(): List<MediaMovieItemDTO>

    @Lenient
    @Headers(FlykSource.HEADER + ": " + FlykSource.POPULAR_TV)
    @GET("v1/users/recommendations/tv/popular")
    suspend fun popularTV(): List<MediaTVItemDTO>

    @Lenient
    @Headers(FlykSource.HEADER + ": " + FlykSource.TOP_RATED_TV)
    @GET("v1/users/recommendations/tv/topRated")
    suspend fun topRatedTV(): List<MediaTVItemDTO>

    @Lenient
    @Headers(FlykSource.HEADER + ": " + FlykSource.INVENTORY_TV)
    @GET("v1/users/recommendations/tv/inventory")
    suspend fun inventoryTV(): List<MediaTVItemDTO>

    @Lenient
    @Headers(FlykSource.HEADER + ": " + FlykSource.SEARCH_HISTORY_TV)
    @GET("v1/users/recommendations/tv/searchHistory")
    suspend fun searchHistoryTV(): List<MediaTVItemDTO>
}

/** Catalog lookups not tied to the user's library. */
interface MediaApi {
    /** EP-032: every season of a series, including ones the user doesn't own. */
    @GET("v1/media/tv/{flyksId}/seasons")
    suspend fun seasons(
        @Path("flyksId") flyksId: String,
        @Header(FlykSource.HEADER) source: String,
    ): List<SeasonSummaryDTO>

    /** Attribution beacon + convenience read. Fire-and-forget from detail screens. */
    @GET("v1/media/{flyksId}")
    suspend fun mediaItem(
        @Path("flyksId") flyksId: String,
        @Header(FlykSource.HEADER) source: String,
    ): MediaItem

    /** 404 = UPC not in the catalog. Backend pending (iOS DEP-12). */
    @Headers(FlykSource.HEADER + ": " + FlykSource.SEARCH_BARCODE)
    @GET("v1/search/barcode")
    suspend fun barcode(@Query("upc") upc: String): MediaItem

    /** 422 `no_match` / 502 `upstream_error`. Backend pending (iOS DEP-12). */
    @POST("v1/media/submissions")
    suspend fun submitTitle(@Body body: BarcodeSubmission): BarcodeSubmissionResponse
}

@JsonClass(generateAdapter = true)
data class BarcodeSubmission(
    val upc: String,
    val title: String,
    /** `movie` or `tv`. */
    val mediaType: String,
    val year: Int? = null,
    val season: Int? = null,
    /** `MediaFormat.raw`. */
    val format: String? = null,
    val edition: String? = null,
    val distributor: String? = null,
)

@JsonClass(generateAdapter = true)
data class BarcodeSubmissionResponse(val item: MediaItem)

interface GenreApi {
    /** `{ "movie": { "28": "Action" }, "tv": { "10759": "Action & Adventure" } }` */
    @GET("v1/genres")
    suspend fun fetchGenres(): Map<String, Map<String, String>>
}
