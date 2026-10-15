package com.flykpicks.android.util

import java.text.Collator

/** One row of a genre-grouped screen: the titles whose first genre is [genre]. */
data class GenreSection<T>(val genre: String, val items: List<T>)

/** The first of a title's genre names ("Crime • Drama" → "Crime"); "Unknown" when there is none. */
fun primaryGenre(raw: String): String =
    raw.split('•', ',', '/', '|', ';').first().trim().ifEmpty { "Unknown" }

/**
 * Groups titles into rows by their first genre, as the iOS Library and Search screens do: rows
 * in alphabetical order ignoring case, titles by name within a row.
 */
fun <T> groupByPrimaryGenre(items: List<T>, genre: (T) -> String, title: (T) -> String): List<GenreSection<T>> {
    val byName = Collator.getInstance().apply { strength = Collator.SECONDARY }
    return items
        .groupBy { primaryGenre(genre(it)) }
        .map { (name, members) -> GenreSection(name, members.sortedBy(title)) }
        .sortedWith(compareBy(byName) { it.genre })
}
