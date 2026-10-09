package com.flykpicks.android.api.json

import com.flykpicks.android.model.MediaItem
import com.flykpicks.android.model.MediaMovieItemDTO
import com.flykpicks.android.model.MediaTVItemDTO
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Decodes a search/media result as a movie or a TV item. As on iOS the shape decides — movies
 * carry `title`/`release_date`, TV items `name`/`first_air_date` — but `media_type` picks which
 * shape to try first so the common case decodes once.
 */
class MediaItemJsonAdapter(moshi: Moshi) : JsonAdapter<MediaItem>() {

    private val movieAdapter = moshi.adapter(MediaMovieItemDTO::class.java)
    private val tvAdapter = moshi.adapter(MediaTVItemDTO::class.java)

    override fun fromJson(reader: JsonReader): MediaItem? {
        if (reader.peek() == JsonReader.Token.NULL) return reader.nextNull()
        val path = reader.path
        val raw = reader.readJsonValue()
        val mediaType = (raw as? Map<*, *>)?.get("media_type")

        val attempts: List<(Any?) -> MediaItem?> = if (mediaType == "tv") {
            listOf(::decodeTv, ::decodeMovie)
        } else {
            listOf(::decodeMovie, ::decodeTv)
        }
        val errors = mutableListOf<String>()
        for (attempt in attempts) {
            try {
                attempt(raw)?.let { return it }
            } catch (e: JsonDataException) {
                errors += e.message.orEmpty()
            }
        }
        throw JsonDataException("Item at $path did not match movie or tv shape: $errors")
    }

    private fun decodeMovie(raw: Any?): MediaItem? = movieAdapter.fromJsonValue(raw)?.let(MediaItem::Movie)
    private fun decodeTv(raw: Any?): MediaItem? = tvAdapter.fromJsonValue(raw)?.let(MediaItem::Tv)

    override fun toJson(writer: JsonWriter, value: MediaItem?) {
        when (value) {
            null -> writer.nullValue()
            is MediaItem.Movie -> movieAdapter.toJson(writer, value.dto)
            is MediaItem.Tv -> tvAdapter.toJson(writer, value.dto)
        }
    }

    companion object {
        val FACTORY = Factory { type, annotations, moshi ->
            if (annotations.isEmpty() && Types.getRawType(type) == MediaItem::class.java) {
                MediaItemJsonAdapter(moshi)
            } else {
                null
            }
        }
    }
}
