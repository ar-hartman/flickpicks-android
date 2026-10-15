package com.flykpicks.android.api.endpoints

import com.flykpicks.android.model.InventoryTitleDTO
import com.flykpicks.android.model.PaginatedResponse
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

private const val LIBRARY_SOURCE = FlykSource.HEADER + ": " + FlykSource.LIBRARY_DETAIL

/** Inventory endpoints. Mutations return the user's full library as a bare list. */
interface LibraryApi {
    /** One page of titles; pages are 1-based. The server defaults to 20 per page and caps [limit] at 100. */
    @Headers(LIBRARY_SOURCE)
    @GET("v1/users/inventory/media")
    suspend fun fetchLibrary(@Query("page") page: Int, @Query("limit") limit: Int): PaginatedResponse<InventoryTitleDTO>

    @Headers(LIBRARY_SOURCE)
    @POST("v1/users/inventory/media/add")
    suspend fun addItem(@Body body: AddMediaItemToLibraryPayload): List<InventoryTitleDTO>

    @Headers(LIBRARY_SOURCE)
    @POST("v1/users/inventory/media/remove")
    suspend fun removeItem(@Body body: RemoveMediaItemPayload): List<InventoryTitleDTO>

    @Headers(LIBRARY_SOURCE)
    @PUT("v1/users/inventory/media/update")
    suspend fun updateItem(@Body body: UpdateInventoryItemPayload): List<InventoryTitleDTO>

    /** An empty [UpdateOwnedSeasonsPayload.ownedSeasons] is a 400; see the payload docs. */
    @Headers(LIBRARY_SOURCE)
    @PUT("v1/users/inventory/media/seasons")
    suspend fun updateSeasons(@Body body: UpdateOwnedSeasonsPayload): List<InventoryTitleDTO>
}

/** For TV, pass [ownedSeasons] + [selectAllSeasons]; the backend ignores both for movies. */
@JsonClass(generateAdapter = true)
data class AddMediaItemToLibraryPayload(
    val mediaItemFlyksID: String,
    val format: String,
    val condition: String,
    val ownedSeasons: List<Int>? = null,
    val selectAllSeasons: Boolean = true,
)

/** [mediaInventoryItemID] is the per-unit id, not the catalog title id. */
@JsonClass(generateAdapter = true)
data class RemoveMediaItemPayload(val mediaInventoryItemID: String)

@JsonClass(generateAdapter = true)
data class UpdateInventoryItemPayload(
    val mediaInventoryItemID: String,
    val format: String,
    val condition: String,
)

/**
 * Never send an empty [ownedSeasons] (400). To drop the last season, remove the unit; to
 * restore whole-series ownership, send [selectAllSeasons] = true.
 */
@JsonClass(generateAdapter = true)
data class UpdateOwnedSeasonsPayload(
    val mediaInventoryItemID: String,
    val ownedSeasons: List<Int>?,
    val selectAllSeasons: Boolean,
)
