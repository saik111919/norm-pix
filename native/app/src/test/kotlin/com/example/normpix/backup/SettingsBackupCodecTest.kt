package com.example.normpix.backup

import com.example.normpix.settings.PrefValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The settings file of an app backup: every preference with its type, read back exactly, and read carefully. */
class SettingsBackupCodecTest {
    private val everyKind: Map<String, PrefValue> = mapOf(
        "a_string" to PrefValue.StringPref("Dark"),
        "an_int" to PrefValue.IntPref(110),
        "a_long" to PrefValue.LongPref(5_000_000_000L),
        "a_float" to PrefValue.FloatPref(1.5f),
        "a_boolean" to PrefValue.BooleanPref(false),
        "a_set" to PrefValue.StringSetPref(setOf("b", "a")),
    )

    @Test
    fun `each preference is written as its type and its value`() {
        val root = Json.parseToJsonElement(SettingsBackupCodec.encode(everyKind)).jsonObject
        assertEquals(everyKind.keys, root.keys)
        fun entry(key: String): JsonObject = root.getValue(key).jsonObject
        assertEquals("string", entry("a_string").getValue("type").jsonPrimitive.content)
        assertEquals("Dark", entry("a_string").getValue("value").jsonPrimitive.content)
        assertEquals("int", entry("an_int").getValue("type").jsonPrimitive.content)
        assertEquals(110, entry("an_int").getValue("value").jsonPrimitive.intOrNull)
        assertEquals("long", entry("a_long").getValue("type").jsonPrimitive.content)
        assertEquals("float", entry("a_float").getValue("type").jsonPrimitive.content)
        assertEquals("boolean", entry("a_boolean").getValue("type").jsonPrimitive.content)
        assertEquals(false, entry("a_boolean").getValue("value").jsonPrimitive.booleanOrNull)
        assertEquals("stringSet", entry("a_set").getValue("type").jsonPrimitive.content)
        assertEquals(listOf("a", "b"), (entry("a_set").getValue("value") as JsonArray).map { it.jsonPrimitive.content })
    }

    @Test
    fun `what is written is read back with every type kept`() {
        val read = assertNotNull(SettingsBackupCodec.decode(SettingsBackupCodec.encode(everyKind)))
        assertEquals(everyKind, read.values)
        assertEquals(0, read.skipped)
    }

    @Test
    fun `an entry that is not a preference of a known type is left out and counted`() {
        val text = """
            {
              "ok": {"type": "int", "value": 5},
              "quoted number": {"type": "int", "value": "5"},
              "wrong kind": {"type": "boolean", "value": 1},
              "unknown type": {"type": "double", "value": 1.5},
              "no type": {"value": 1},
              "no value": {"type": "int"},
              "not an object": 7,
              "set with a number": {"type": "stringSet", "value": ["a", 1]},
              "": {"type": "int", "value": 1}
            }
        """.trimIndent()
        val read = assertNotNull(SettingsBackupCodec.decode(text))
        assertEquals(mapOf("ok" to PrefValue.IntPref(5)), read.values)
        assertEquals(8, read.skipped)
    }

    @Test
    fun `a file that is not a settings object is not read`() {
        assertNull(SettingsBackupCodec.decode("not json"))
        assertNull(SettingsBackupCodec.decode("[]"))
        assertNull(SettingsBackupCodec.decode("7"))
        assertTrue(assertNotNull(SettingsBackupCodec.decode("{}")).values.isEmpty())
    }

    @Test
    fun `a float that is not a number cannot be written, and one in a file is not taken`() {
        assertNull(PrefValue.of(Float.NaN))
        assertNull(PrefValue.of(Float.POSITIVE_INFINITY))
        assertNull(PrefValue.of(object {}))
        assertNull(PrefValue.fromJson(JsonObject(mapOf("type" to JsonPrimitive("float"), "value" to JsonPrimitive("NaN")))))
    }
}
