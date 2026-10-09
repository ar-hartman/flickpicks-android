package com.flykpicks.android.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Genre id → name lookup, loaded once from `/v1/genres`. Movie and TV id spaces don't overlap,
 * so both tables share one map. Unknown ids render as "—".
 */
@Singleton
class GenreTranslator @Inject constructor() {

    @Volatile
    private var table: Map<Int, String> = emptyMap()

    val isConfigured: Boolean get() = table.isNotEmpty()

    fun configure(genres: Map<Int, String>) {
        table = genres.toMap()
    }

    fun primaryName(ids: List<Int>): String = ids.firstNotNullOfOrNull { table[it] } ?: UNKNOWN

    fun joinedNames(ids: List<Int>, limit: Int = 2): String {
        val names = ids.mapNotNull { table[it] }
        return if (names.isEmpty()) UNKNOWN else names.take(limit).joinToString(" • ")
    }

    private companion object {
        const val UNKNOWN = "—"
    }
}
