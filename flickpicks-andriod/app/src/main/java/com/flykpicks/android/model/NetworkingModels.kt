package com.flykpicks.android.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Inventory and media wire DTOs. Field naming on the server is mixed camelCase/snake_case (no
// global strategy), so every key is mapped exactly as the backend emits it.

// MARK: Inventory (EP-039–043)

@JsonClass(generateAdapter = true)
data class EditionDTO(
    val editionType: String?,
    val studioLabel: String?,
    val regionCode: String?,
    val discCount: Int?,
    val outOfPrint: Boolean?,
)

@JsonClass(generateAdapter = true)
data class InventoryUnitDTO(
    /** Per-unit mutation key for the remove/update/seasons endpoints. */
    val mediaInventoryItemId: String,
    /** TV only; null or empty means the whole series. */
    val ownedSeasons: List<Int>?,
    val dateAdded: String?,
    val editionOverride: EditionDTO?,
)

@JsonClass(generateAdapter = true)
data class InventoryCopyDTO(
    /** [com.flykpicks.android.model.enums.MediaFormat] raw value. */
    val format: String,
    /** [com.flykpicks.android.model.enums.ItemCondition] raw value. */
    val condition: String,
    val appraisalValue: Double,
    /** `DEFAULT` means not a real valuation. */
    val appraisalSource: String,
    val mediaReleaseId: String?,
    val releaseEdition: EditionDTO?,
    val units: List<InventoryUnitDTO>,
    val quantity: Int,
)

/**
 * One object per owned title. Movie vs. TV is distinguished by [mediaType]; movie-only and
 * TV-only fields are optional.
 */
@JsonClass(generateAdapter = true)
data class InventoryTitleDTO(
    /** Numeric internal id — distinct from [mediaItemId]. */
    val id: Int,
    /** Catalog title id, equivalent to `flyksId` in search results. */
    val mediaItemId: String,
    @Json(name = "media_type") val mediaType: String,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    val posterUrl: String?,
    val adult: Boolean,
    @Json(name = "original_language") val originalLanguage: String?,
    @Json(name = "genre_ids") val genreIds: List<Int>,
    val popularity: Double,
    @Json(name = "vote_average") val voteAverage: Double,
    @Json(name = "vote_count") val voteCount: Int,
    @Json(name = "date_created") val dateCreated: String?,
    @Json(name = "date_refreshed") val dateRefreshed: String?,
    val copies: List<InventoryCopyDTO>,
    // Movie-only
    val title: String?,
    @Json(name = "original_title") val originalTitle: String?,
    @Json(name = "release_date") val releaseDate: String?,
    val video: Boolean?,
    // TV-only
    val name: String?,
    @Json(name = "original_name") val originalName: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "origin_country") val originCountry: List<String>?,
) {
    val isMovie: Boolean get() = mediaType == "movie"
    val displayTitle: String get() = (if (isMovie) title else name).orEmpty()

    /** 0 when the date is missing or malformed. */
    val releaseYear: Int
        get() = (if (isMovie) releaseDate else firstAirDate)?.take(4)?.toIntOrNull() ?: 0
}

// MARK: TV seasons (EP-032) — GET /v1/media/tv/{flyksId}/seasons, bare array

@JsonClass(generateAdapter = true)
data class SeasonSummaryDTO(
    val seasonNumber: Int,
    val episodeCount: Int,
    val airDate: String?,
    val overview: String?,
    val voteAverage: Double,
)

// MARK: Search / recommendations (EP-029, EP-033–038)

@JsonClass(generateAdapter = true)
data class MediaMovieItemDTO(
    val id: String,
    val flyksId: String,
    val title: String,
    val overview: String,
    @Json(name = "media_type") val mediaType: String,
    @Json(name = "original_language") val originalLanguage: String,
    @Json(name = "genre_ids") val genreIds: List<Int>,
    val popularity: Double,
    @Json(name = "release_date") val releaseDate: String,
    @Json(name = "vote_average") val voteAverage: Double,
    @Json(name = "vote_count") val voteCount: Int,
    val posterUrl: String?,
)

@JsonClass(generateAdapter = true)
data class MediaTVItemDTO(
    val id: String,
    val flyksId: String,
    val name: String,
    val overview: String,
    @Json(name = "media_type") val mediaType: String,
    @Json(name = "original_language") val originalLanguage: String,
    @Json(name = "genre_ids") val genreIds: List<Int>,
    val popularity: Double,
    @Json(name = "first_air_date") val firstAirDate: String,
    @Json(name = "vote_average") val voteAverage: Double,
    @Json(name = "vote_count") val voteCount: Int,
    val posterUrl: String?,
)
