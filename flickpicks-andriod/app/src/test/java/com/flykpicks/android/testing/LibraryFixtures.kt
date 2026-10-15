package com.flykpicks.android.testing

/** One physical copy of a title, as the inventory endpoints list it. */
data class TestCopy(
    val id: String,
    val format: String = "DVD",
    val condition: String = "GOOD",
    /** Null means the copy owns the whole series. */
    val seasons: List<Int>? = null,
    val appraisalSource: String = "DEFAULT",
)

/** An `InventoryTitleDTO`. Genre ids resolve through [GENRES_JSON]. */
fun inventoryTitleJson(
    titleId: String,
    title: String,
    vararg copies: TestCopy,
    tv: Boolean = false,
    genreIds: String = "[18]",
): String {
    val copiesJson = copies.joinToString(",") { copy ->
        """
        {
          "format": "${copy.format}", "condition": "${copy.condition}", "appraisalValue": 12.5,
          "appraisalSource": "${copy.appraisalSource}", "mediaReleaseId": null, "releaseEdition": null, "quantity": 1,
          "units": [{"mediaInventoryItemId": "${copy.id}", "ownedSeasons": ${copy.seasons ?: "null"},
                     "dateAdded": "2026-02-01T00:00:00Z", "editionOverride": null}]
        }
        """
    }
    return """
    {
      "id": 1, "mediaItemId": "$titleId", "media_type": "${if (tv) "tv" else "movie"}", "overview": "About $title.",
      "poster_path": null, "backdrop_path": null, "posterUrl": "/v1/media/poster?flyksId=$titleId",
      "adult": false, "original_language": "en", "genre_ids": $genreIds,
      "popularity": 1.0, "vote_average": 7.5, "vote_count": 10, "date_created": null, "date_refreshed": null,
      "copies": [$copiesJson],
      "title": ${if (tv) "null" else "\"$title\""}, "original_title": null,
      "release_date": ${if (tv) "null" else "\"1999-10-15\""}, "video": null,
      "name": ${if (tv) "\"$title\"" else "null"}, "original_name": null,
      "first_air_date": ${if (tv) "\"1990-04-08\"" else "null"}, "origin_country": null
    }
    """
}

/** One page of `GET v1/users/inventory/media`. */
fun libraryPageJson(vararg titles: String, page: Int = 1, totalPages: Int = 1): String =
    """{"items":[${titles.joinToString(",")}],"page":$page,"totalPages":$totalPages,"totalItems":${titles.size}}"""

/** What every inventory mutation answers with: the whole library, unpaged. */
fun libraryListJson(vararg titles: String): String = "[${titles.joinToString(",")}]"

const val LIBRARY_PATH = "v1/users/inventory/media"
const val LIBRARY_REMOVE_PATH = "v1/users/inventory/media/remove"
const val LIBRARY_UPDATE_PATH = "v1/users/inventory/media/update"
const val LIBRARY_SEASONS_PATH = "v1/users/inventory/media/seasons"
const val LIBRARY_ADD_PATH = "v1/users/inventory/media/add"

// A film with two copies and a series with one, used across the library tests.
val HEAT_DVD = TestCopy("heat-dvd", format = "DVD", condition = "GOOD")
val HEAT_BLURAY = TestCopy("heat-bluray", format = "BLURAY", condition = "LIKE_NEW", appraisalSource = "EBAY")
val PEAKS_BLURAY = TestCopy("peaks-bluray", format = "BLURAY", condition = "NEW", seasons = listOf(1, 2))

fun heatJson(vararg copies: TestCopy = arrayOf(HEAT_DVD, HEAT_BLURAY)) =
    inventoryTitleJson("heat", "Heat", *copies, genreIds = "[80, 18]")

fun twinPeaksJson(vararg copies: TestCopy = arrayOf(PEAKS_BLURAY)) =
    inventoryTitleJson("peaks", "Twin Peaks", *copies, tv = true, genreIds = "[9648, 18]")

const val SEASONS_JSON = """
[
  {"seasonNumber": 1, "episodeCount": 8, "airDate": "1990-04-08", "overview": "A body is found.", "voteAverage": 8.3},
  {"seasonNumber": 2, "episodeCount": 22, "airDate": "1990-09-30", "overview": null, "voteAverage": 7.9},
  {"seasonNumber": 3, "episodeCount": 18, "airDate": null, "overview": "", "voteAverage": 0.0}
]
"""
