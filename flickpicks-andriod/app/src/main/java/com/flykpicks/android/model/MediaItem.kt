package com.flykpicks.android.model

import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.util.GenreTranslator

/** A search/media result: a movie or a TV title. Decoded by `MediaItemJsonAdapter`. */
sealed interface MediaItem {
    val flyksId: String

    data class Movie(val dto: MediaMovieItemDTO) : MediaItem {
        override val flyksId: String get() = dto.flyksId
    }

    data class Tv(val dto: MediaTVItemDTO) : MediaItem {
        override val flyksId: String get() = dto.flyksId
    }
}

/** Presentation model for search and recommendation results. [id] is the `flyksId`. */
data class MediaItemImpl(
    val id: String,
    val flyksId: String,
    val title: String,
    val mediaType: MediaType,
    val originalLanguage: String,
    /** Up to two genre names joined with " • ", or "—" when unknown. */
    val genre: String,
    val popularity: Double,
    val releaseDate: String,
    val voteAverage: Double,
    val voteCount: Int,
    val posterUrl: String?,
    val season: String? = null,
    val overview: String,
)

/** Pairs an item with the row it was tapped from, so the detail screen can attribute the view. */
data class MediaDetailNavigation(val item: MediaItemImpl, val source: String)

fun MediaMovieItemDTO.toMediaItemImpl(genres: GenreTranslator) = MediaItemImpl(
    id = flyksId,
    flyksId = flyksId,
    title = title,
    mediaType = MediaType.FILM,
    originalLanguage = originalLanguage,
    genre = genres.joinedNames(genreIds),
    popularity = popularity,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    voteCount = voteCount,
    posterUrl = posterUrl,
    overview = overview,
)

fun MediaTVItemDTO.toMediaItemImpl(genres: GenreTranslator) = MediaItemImpl(
    id = flyksId,
    flyksId = flyksId,
    title = name,
    mediaType = MediaType.TV,
    originalLanguage = originalLanguage,
    genre = genres.joinedNames(genreIds),
    popularity = popularity,
    releaseDate = firstAirDate,
    voteAverage = voteAverage,
    voteCount = voteCount,
    posterUrl = posterUrl,
    overview = overview,
)

fun MediaItem.toMediaItemImpl(genres: GenreTranslator): MediaItemImpl = when (this) {
    is MediaItem.Movie -> dto.toMediaItemImpl(genres)
    is MediaItem.Tv -> dto.toMediaItemImpl(genres)
}
