package com.flykpicks.android.model

import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.util.GenreTranslator

/**
 * One physical copy in the user's library.
 * [id] is the `mediaInventoryItemId` — the per-unit key every mutation endpoint takes.
 * [titleId] is the `mediaItemId` — the catalog key used for grouping and search linkage.
 */
data class LibraryItemImpl(
    val id: String,
    val titleId: String,
    val title: String,
    val mediaType: MediaType,
    /** 0 when unknown. */
    val year: Int,
    val genre: String,
    val posterUrl: String?,
    /** Owned season numbers joined with ", "; null for movies and whole-series TV. */
    val season: String?,
    /** TV only; null means the unit owns the whole series. */
    val ownedSeasons: List<Int>?,
    val format: MediaFormat,
    val condition: ItemCondition,
    val appraisalValue: Double,
    val appraisalSource: String,
    val dateAdded: String?,
    val overview: String,
)

/** Flattens title → copies → units into one item per physical unit (blueprint Appendix A). */
fun InventoryTitleDTO.toLibraryItems(genres: GenreTranslator): List<LibraryItemImpl> =
    copies.flatMap { copy ->
        copy.units.map { unit ->
            val seasons = if (isMovie) null else unit.ownedSeasons
            LibraryItemImpl(
                id = unit.mediaInventoryItemId,
                titleId = mediaItemId,
                title = displayTitle,
                mediaType = if (isMovie) MediaType.FILM else MediaType.TV,
                year = releaseYear,
                genre = genres.joinedNames(genreIds),
                posterUrl = posterUrl,
                season = seasons?.takeIf { it.isNotEmpty() }?.joinToString(", "),
                ownedSeasons = seasons,
                format = MediaFormat.fromRaw(copy.format) ?: MediaFormat.DVD,
                // Unknown conditions read as NEW, matching iOS (ios-gaps.md G11).
                condition = ItemCondition.fromRaw(copy.condition) ?: ItemCondition.NEW,
                appraisalValue = copy.appraisalValue,
                appraisalSource = copy.appraisalSource,
                dateAdded = unit.dateAdded,
                overview = overview.orEmpty(),
            )
        }
    }
