package com.flykpicks.android.service

import com.flykpicks.android.api.endpoints.AddMediaItemToLibraryPayload
import com.flykpicks.android.api.endpoints.LibraryApi
import com.flykpicks.android.api.endpoints.RemoveMediaItemPayload
import com.flykpicks.android.api.endpoints.UpdateInventoryItemPayload
import com.flykpicks.android.api.endpoints.UpdateOwnedSeasonsPayload
import com.flykpicks.android.model.InventoryTitleDTO
import com.flykpicks.android.model.LibraryItemImpl
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.model.toLibraryItems
import com.flykpicks.android.util.GenreTranslator
import javax.inject.Inject
import javax.inject.Singleton

/** The user's library. Every call returns the whole library, flattened to one item per unit. */
@Singleton
class LibraryService @Inject constructor(
    private val api: LibraryApi,
    private val genres: GenreTranslator,
) {

    suspend fun fetchLibrary(): List<LibraryItemImpl> = api.fetchLibrary().items.flatten()

    /** For TV, [ownedSeasons] + [selectAllSeasons] choose the seasons; ignored for movies. */
    suspend fun addMediaItem(
        flyksId: String,
        format: MediaFormat,
        condition: ItemCondition,
        ownedSeasons: List<Int>? = null,
        selectAllSeasons: Boolean = true,
    ): List<LibraryItemImpl> = api.addItem(
        AddMediaItemToLibraryPayload(flyksId, format.raw, condition.raw, ownedSeasons, selectAllSeasons),
    ).flatten()

    /** [inventoryItemId] is [LibraryItemImpl.id] (per unit), not the title id. */
    suspend fun removeItem(inventoryItemId: String): List<LibraryItemImpl> =
        api.removeItem(RemoveMediaItemPayload(inventoryItemId)).flatten()

    suspend fun updateItem(
        inventoryItemId: String,
        format: MediaFormat,
        condition: ItemCondition,
    ): List<LibraryItemImpl> =
        api.updateItem(UpdateInventoryItemPayload(inventoryItemId, format.raw, condition.raw)).flatten()

    /**
     * Never pass an empty [ownedSeasons] (400): remove the unit instead, or pass
     * [selectAllSeasons] = true to restore whole-series ownership.
     */
    suspend fun updateOwnedSeasons(
        inventoryItemId: String,
        ownedSeasons: List<Int>?,
        selectAllSeasons: Boolean,
    ): List<LibraryItemImpl> {
        require(ownedSeasons == null || ownedSeasons.isNotEmpty()) {
            "Empty ownedSeasons is rejected by the server; remove the item or select all seasons"
        }
        return api.updateSeasons(UpdateOwnedSeasonsPayload(inventoryItemId, ownedSeasons, selectAllSeasons))
            .flatten()
    }

    private fun List<InventoryTitleDTO>.flatten(): List<LibraryItemImpl> = flatMap { it.toLibraryItems(genres) }
}
