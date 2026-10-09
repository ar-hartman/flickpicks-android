package com.flykpicks.android.api.json

import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Types

/**
 * Decode a JSON array element by element, skipping elements that fail to decode so one malformed
 * record can't blank a whole response (iOS `LenientArray`). Apply to a `List<T>` property or to a
 * Retrofit method returning `List<T>`.
 */
@JsonQualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class Lenient

class LenientListJsonAdapter<T>(private val elementAdapter: JsonAdapter<T>) : JsonAdapter<List<T>>() {

    override fun fromJson(reader: JsonReader): List<T>? {
        if (reader.peek() == JsonReader.Token.NULL) return reader.nextNull()
        val items = ArrayList<T>()
        reader.beginArray()
        var index = 0
        while (reader.hasNext()) {
            val raw = reader.readJsonValue()
            try {
                elementAdapter.fromJsonValue(raw)?.let(items::add)
            } catch (e: JsonDataException) {
                Log.w(TAG, "Skipped element [$index]: ${e.message}")
            }
            index++
        }
        reader.endArray()
        return items
    }

    override fun toJson(writer: JsonWriter, value: List<T>?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginArray()
        value.forEach { elementAdapter.toJson(writer, it) }
        writer.endArray()
    }

    companion object {
        private const val TAG = "LenientList"

        val FACTORY = Factory { type, annotations, moshi ->
            Types.nextAnnotations(annotations, Lenient::class.java) ?: return@Factory null
            if (Types.getRawType(type) != List::class.java) return@Factory null
            val elementType = Types.collectionElementType(type, List::class.java)
            LenientListJsonAdapter(moshi.adapter<Any>(elementType))
        }
    }
}
