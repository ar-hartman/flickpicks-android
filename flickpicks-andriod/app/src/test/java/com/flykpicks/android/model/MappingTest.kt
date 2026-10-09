package com.flykpicks.android.model

import com.flykpicks.android.api.ApiClient
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.model.profile.OrderError
import com.flykpicks.android.testing.movieJson
import com.flykpicks.android.util.GenreTranslator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

const val INVENTORY_TV_JSON = """
{
  "id": 7, "mediaItemId": "t1", "media_type": "tv", "overview": null,
  "poster_path": null, "backdrop_path": null, "posterUrl": "https://img.example/got.jpg",
  "adult": false, "original_language": "en", "genre_ids": [10765, 18, 99],
  "popularity": 300.1, "vote_average": 8.5, "vote_count": 21000,
  "date_created": null, "date_refreshed": null,
  "copies": [
    {
      "format": "BLURAY", "condition": "LIKE_NEW", "appraisalValue": 40.0, "appraisalSource": "DEFAULT",
      "mediaReleaseId": null, "releaseEdition": null, "quantity": 2,
      "units": [
        {"mediaInventoryItemId": "u1", "ownedSeasons": [1, 3], "dateAdded": "2026-02-01T00:00:00Z", "editionOverride": null},
        {"mediaInventoryItemId": "u2", "ownedSeasons": null, "dateAdded": null, "editionOverride": null}
      ]
    },
    {
      "format": "BETAMAX", "condition": "MINT", "appraisalValue": 5.0, "appraisalSource": "EBAY",
      "mediaReleaseId": null, "releaseEdition": null, "quantity": 1,
      "units": [{"mediaInventoryItemId": "u3", "ownedSeasons": [], "dateAdded": null, "editionOverride": null}]
    }
  ],
  "title": null, "original_title": null, "release_date": null, "video": null,
  "name": "Game of Thrones", "original_name": "Game of Thrones", "first_air_date": "2011-04-17",
  "origin_country": ["US"]
}
"""

class MappingTest {

    private val moshi = ApiClient.moshi()
    private val genres = GenreTranslator().apply {
        configure(mapOf(10765 to "Sci-Fi & Fantasy", 18 to "Drama", 99 to "Documentary", 28 to "Action"))
    }

    @Test
    fun inventoryTitle_flattensToOneItemPerUnit() {
        val title = moshi.adapter(InventoryTitleDTO::class.java).fromJson(INVENTORY_TV_JSON)!!

        val items = title.toLibraryItems(genres)

        assertEquals(listOf("u1", "u2", "u3"), items.map { it.id })
        items.forEach {
            assertEquals("t1", it.titleId)
            assertEquals("Game of Thrones", it.title)
            assertEquals(MediaType.TV, it.mediaType)
            assertEquals(2011, it.year)
            assertEquals("Sci-Fi & Fantasy • Drama", it.genre)
            assertEquals("", it.overview)
        }
        assertEquals("1, 3", items[0].season)
        assertEquals(listOf(1, 3), items[0].ownedSeasons)
        assertNull(items[1].season)
        assertNull("empty seasons mean the whole series", items[2].season)
        assertEquals(MediaFormat.BLURAY, items[0].format)
        assertEquals(ItemCondition.LIKE_NEW, items[0].condition)
        // Unknown values fall back like iOS.
        assertEquals(MediaFormat.DVD, items[2].format)
        assertEquals(ItemCondition.NEW, items[2].condition)
    }

    @Test
    fun movieUnits_neverCarrySeasons() {
        val movie = moshi.adapter(InventoryTitleDTO::class.java).fromJson(
            INVENTORY_TV_JSON
                .replace("\"media_type\": \"tv\"", "\"media_type\": \"movie\"")
                .replace("\"title\": null", "\"title\": \"Arrival\"")
                .replace("\"release_date\": null", "\"release_date\": \"2016-11-11\""),
        )!!

        val first = movie.toLibraryItems(genres).first()

        assertEquals(MediaType.FILM, first.mediaType)
        assertEquals("Arrival", first.title)
        assertEquals(2016, first.year)
        assertNull(first.ownedSeasons)
        assertNull(first.season)
    }

    @Test
    fun mediaItemImpl_usesFlyksIdAndJoinedGenres() {
        val dto = moshi.adapter(MediaMovieItemDTO::class.java).fromJson(movieJson(genreIds = "[28, 18, 99]"))!!

        val item = dto.toMediaItemImpl(genres)

        assertEquals("m1", item.id)
        assertEquals(MediaType.FILM, item.mediaType)
        assertEquals("Action • Drama", item.genre)
        assertEquals("1999-10-15", item.releaseDate)
    }

    @Test
    fun unknownGenres_renderAsDash() {
        assertEquals("—", GenreTranslator().joinedNames(listOf(1, 2)))
        assertEquals("—", genres.primaryName(listOf(424242)))
    }

    @Test
    fun order409_mapsMessageSubstringsLikeIos() {
        assertEquals(OrderError.BuyingOwnListing, OrderError.map409("You cannot rent your own listing"))
        assertEquals(OrderError.RentalCapReached, OrderError.map409("Active rental limit reached"))
        assertEquals(OrderError.PlanDisallowsRentals, OrderError.map409("Plan does not include rentals"))
        assertEquals(OrderError.AvailabilityRace, OrderError.map409("Concurrent claim"))
        assertEquals(OrderError.ListingUnavailable, OrderError.map409(null))
    }
}
