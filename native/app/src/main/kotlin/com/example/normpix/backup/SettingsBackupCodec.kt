package com.example.normpix.backup

import com.example.normpix.settings.PrefValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The app's preferences as a backup carries them (`app/settings.json`): one JSON object with a key for each preference, whose
 * value says the type and the value - `{"theme_mode": {"type": "string", "value": "Dark"}, "font_width": {"type": "int",
 * "value": 100}}`. It holds whatever the preferences file held (see `AppPreferences.exportAll`), so it needs no list of keys.
 */
object SettingsBackupCodec {
    private val pretty = Json { prettyPrint = true }

    /** The preferences read from a backup, and how many entries in it were unusable (no type, an unknown type, a wrong kind of value). */
    class Decoded(val values: Map<String, PrefValue>, val skipped: Int)

    fun encode(values: Map<String, PrefValue>): String {
        val root = JsonObject(values.toSortedMap().mapValues { (_, value) -> value.toJson() })
        return pretty.encodeToString(JsonElement.serializer(), root)
    }

    /** The preferences in [text], or null when it isn't a settings file at all. */
    fun decode(text: String): Decoded? {
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val values = LinkedHashMap<String, PrefValue>()
        var skipped = 0
        for ((key, element) in root) {
            val value = PrefValue.fromJson(element)
            if (key.isEmpty() || value == null) skipped++ else values[key] = value
        }
        return Decoded(values, skipped)
    }
}
