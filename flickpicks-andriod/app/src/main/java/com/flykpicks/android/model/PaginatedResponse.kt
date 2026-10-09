package com.flykpicks.android.model

import com.squareup.moshi.JsonClass

/** Standard paginated envelope. Pages are 1-based, matching iOS (`hasMore = page < totalPages`). */
@JsonClass(generateAdapter = true)
data class PaginatedResponse<T>(
    val items: List<T>,
    val page: Int,
    val totalPages: Int,
    val totalItems: Int,
) {
    val hasMore: Boolean get() = page < totalPages
}
