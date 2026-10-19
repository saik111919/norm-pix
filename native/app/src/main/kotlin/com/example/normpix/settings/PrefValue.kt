package com.example.normpix.settings

import android.content.SharedPreferences
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * One stored preference with its type, as an app backup carries it (see [AppPreferences.exportAll]). SharedPreferences keeps
 * six kinds of value and throws when one is read as another kind, so a backup has to say which kind each value is.
 *
 * In a backup's JSON a preference is `{"type": "int", "value": 5}`; the types are `string`, `int`, `long`, `float`,
 * `boolean` and `stringSet` (an array of strings).
 */
sealed interface PrefValue {
    /** The kind as a backup names it. */
    val type: String

    data class StringPref(val value: String) : PrefValue {
        override val type: String get() = "string"
    }

    data class IntPref(val value: Int) : PrefValue {
        override val type: String get() = "int"
    }

    data class LongPref(val value: Long) : PrefValue {
        override val type: String get() = "long"
    }

    data class FloatPref(val value: Float) : PrefValue {
        override val type: String get() = "float"
    }

    data class BooleanPref(val value: Boolean) : PrefValue {
        override val type: String get() = "boolean"
    }

    data class StringSetPref(val value: Set<String>) : PrefValue {
        override val type: String get() = "stringSet"
    }

    /** Writes this value under [key] (the caller applies the [editor]). */
    fun putInto(editor: SharedPreferences.Editor, key: String) {
        when (this) {
            is StringPref -> editor.putString(key, value)
            is IntPref -> editor.putInt(key, value)
            is LongPref -> editor.putLong(key, value)
            is FloatPref -> editor.putFloat(key, value)
            is BooleanPref -> editor.putBoolean(key, value)
            is StringSetPref -> editor.putStringSet(key, value.toSet())
        }
    }

    fun toJson(): JsonObject {
        val element: JsonElement = when (this) {
            is StringPref -> JsonPrimitive(value)
            is IntPref -> JsonPrimitive(value)
            is LongPref -> JsonPrimitive(value)
            is FloatPref -> JsonPrimitive(value)
            is BooleanPref -> JsonPrimitive(value)
            is StringSetPref -> JsonArray(value.sorted().map { JsonPrimitive(it) })
        }
        return JsonObject(mapOf("type" to JsonPrimitive(type), "value" to element))
    }

    companion object {
        /**
         * The value [raw] stands for, as `SharedPreferences.getAll` hands it out. Null for what no preference can hold, and for a
         * float with no number in it (NaN, infinity), which JSON can't write.
         */
        fun of(raw: Any?): PrefValue? = when (raw) {
            is String -> StringPref(raw)
            is Int -> IntPref(raw)
            is Long -> LongPref(raw)
            is Float -> if (raw.isFinite()) FloatPref(raw) else null
            is Boolean -> BooleanPref(raw)
            is Set<*> -> if (raw.all { it is String }) StringSetPref(raw.filterIsInstance<String>().toSet()) else null
            else -> null
        }

        /**
         * The value a backup's JSON entry says, or null when it isn't one: no type, a type NormPix doesn't know, or a value that
         * isn't of the type it names (a quoted "5" for an int is not an int).
         */
        fun fromJson(element: JsonElement): PrefValue? {
            val entry = element as? JsonObject ?: return null
            val type = (entry["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val value = entry["value"] ?: return null
            // A number or a flag, written without quotes.
            val bare = (value as? JsonPrimitive)?.takeIf { !it.isString }
            return when (type) {
                "string" -> (value as? JsonPrimitive)?.takeIf { it.isString }?.let { StringPref(it.content) }
                "int" -> bare?.intOrNull?.let { IntPref(it) }
                "long" -> bare?.longOrNull?.let { LongPref(it) }
                "float" -> bare?.floatOrNull?.takeIf { it.isFinite() }?.let { FloatPref(it) }
                "boolean" -> bare?.booleanOrNull?.let { BooleanPref(it) }
                "stringSet" -> {
                    val items = (value as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                    if (items == null || items.any { it == null }) null else StringSetPref(items.filterNotNull().toSet())
                }
                else -> null
            }
        }
    }
}
