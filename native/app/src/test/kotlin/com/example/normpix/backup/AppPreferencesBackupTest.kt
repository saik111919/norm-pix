package com.example.normpix.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.PrefValue
import com.example.normpix.settings.ThemeMode
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The preferences as a backup carries them: all of them, typed, minus the ones that guard or describe this phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppPreferencesBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var raw: SharedPreferences
    private lateinit var prefs: AppPreferences

    /** The preferences a backup neither carries nor changes (see AppPreferences.BACKUP_EXCLUDED). */
    private val excluded = listOf(
        "exact_alarm_prompted",
        "dummy_data_enabled",
        "wallet_lock_enabled",
        "app_lock_scope",
        "app_lock_timeout",
        "nfc_tag_reveal_enabled",
        "nfc_reveal_registered_only",
        "card_cvv_enabled",
        "wallet_cards_enabled",
    )

    private fun store(name: String): SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    @BeforeTest
    fun setUp() {
        raw = store("backup_prefs_a")
        prefs = AppPreferences(raw)
    }

    @Test
    fun `every kind of preference is exported with its type`() {
        raw.edit()
            .putString("a_string", "x")
            .putInt("an_int", 5)
            .putLong("a_long", 5_000_000_000L)
            .putFloat("a_float", 1.5f)
            .putBoolean("a_boolean", true)
            .putStringSet("a_set", setOf("a", "b"))
            .putFloat("not_a_number", Float.NaN)
            .commit()
        val exported = prefs.exportAll()
        assertEquals(PrefValue.StringPref("x"), exported["a_string"])
        assertEquals(PrefValue.IntPref(5), exported["an_int"])
        assertEquals(PrefValue.LongPref(5_000_000_000L), exported["a_long"])
        assertEquals(PrefValue.FloatPref(1.5f), exported["a_float"])
        assertEquals(PrefValue.BooleanPref(true), exported["a_boolean"])
        assertEquals(PrefValue.StringSetPref(setOf("a", "b")), exported["a_set"])
        assertFalse("not_a_number" in exported, "JSON can't write it")
    }

    @Test
    fun `preferences made with the settings survive the file and come back as they were`() {
        prefs.setMode(ThemeMode.Dark)
        prefs.setCardTiltEnabled(false)
        prefs.setFontShape(weightShift = 50, width = 110, roundness = 20)
        prefs.setProfile("Asha", "🙂")
        raw.edit().putStringSet("a_set", setOf("x", "y")).putLong("a_long", 7L).commit()

        val file = SettingsBackupCodec.encode(prefs.exportAll())
        val read = assertNotNull(SettingsBackupCodec.decode(file))
        assertEquals(0, read.skipped)

        val other = AppPreferences(store("backup_prefs_b"))
        assertEquals(0, other.importAll(read.values))
        assertEquals(prefs.exportAll(), other.exportAll())
        assertEquals(ThemeMode.Dark, other.state.value.mode)
        assertFalse(other.state.value.cardTiltEnabled)
        assertEquals(110, other.state.value.fontWidth)
        assertEquals("Asha", other.state.value.profileName)
    }

    @Test
    fun `the preferences that guard or describe this phone are neither exported nor changed`() {
        val editor = raw.edit()
        for (key in excluded) editor.putBoolean(key, true)
        editor.putString("app_lock_scope", "EntireApp").putString("app_lock_timeout", "Immediately")
        editor.putString("theme_mode", "Dark").commit()
        prefs = AppPreferences(raw)

        val exported = prefs.exportAll()
        for (key in excluded) assertFalse(key in exported, key)
        assertTrue("theme_mode" in exported)

        // A file that carries them (hand-made, or from another version) can't set them, nor take them away.
        val hostile: Map<String, PrefValue> = excluded.associateWith { key ->
            if (key.endsWith("scope") || key.endsWith("timeout")) PrefValue.StringPref("x") else PrefValue.BooleanPref(false)
        }
        prefs.importAll(hostile + ("theme_mode" to PrefValue.StringPref("Light")))
        for (key in excluded) assertTrue(raw.contains(key), "$key stays")
        for (key in excluded.filter { !it.endsWith("scope") && !it.endsWith("timeout") }) assertTrue(raw.getBoolean(key, false), key)
        assertEquals("Light", raw.getString("theme_mode", null))

        // And a backup without them doesn't clear them.
        prefs.importAll(emptyMap())
        for (key in excluded) assertTrue(raw.contains(key), "$key stays")
    }

    @Test
    fun `importing puts the backup in place of what is there`() {
        prefs.setMode(ThemeMode.Dark)
        prefs.setCardTiltEnabled(false)
        raw.edit().putInt("only_here", 1).commit()

        val skipped = prefs.importAll(mapOf("theme_mode" to PrefValue.StringPref("Light"), "from_backup" to PrefValue.IntPref(2)))

        assertEquals(0, skipped)
        assertEquals(ThemeMode.Light, prefs.state.value.mode)
        assertTrue(prefs.state.value.cardTiltEnabled, "not in the backup: back to its default")
        assertFalse(raw.contains("card_tilt_enabled"))
        assertFalse(raw.contains("only_here"))
        assertEquals(2, raw.getInt("from_backup", 0), "a key this version doesn't know is kept")
    }

    @Test
    fun `only the keys in scope are touched`() {
        prefs.setMode(ThemeMode.Dark)
        prefs.setCardTiltEnabled(false)
        prefs.setProfile("Asha", "A")

        prefs.importAll(mapOf("theme_mode" to PrefValue.StringPref("Light"), "profile_name" to PrefValue.StringPref("Other"))) { key ->
            key == "theme_mode"
        }

        assertEquals(ThemeMode.Light, prefs.state.value.mode)
        assertFalse(prefs.state.value.cardTiltEnabled, "out of scope: as it was")
        assertEquals("Asha", prefs.state.value.profileName, "out of scope: not taken from the file")
    }

    @Test
    fun `a value of another kind than the one stored is skipped and counted`() {
        prefs.setMode(ThemeMode.Dark)
        val skipped = prefs.importAll(mapOf("theme_mode" to PrefValue.IntPref(3)))
        assertEquals(1, skipped)
        assertEquals("Dark", raw.getString("theme_mode", null))
        assertEquals(ThemeMode.Dark, prefs.state.value.mode)
    }

    @Test
    fun `a value the app cannot read its own preference from is skipped, and the app still starts`() {
        // card_tilt_enabled is read as a boolean: a string there would throw at every start if it were kept.
        val skipped = prefs.importAll(mapOf("card_tilt_enabled" to PrefValue.StringPref("yes"), "font_width" to PrefValue.StringPref("wide")))
        assertEquals(2, skipped)
        assertFalse(raw.contains("card_tilt_enabled"))
        assertFalse(raw.contains("font_width"))
        AppPreferences(raw)
    }

    @Test
    fun `the state is read again, so what draws from it changes at once`() {
        val revision = prefs.state.value.customFontRevision
        prefs.importAll(mapOf("theme_mode" to PrefValue.StringPref("Dark"), "font_width" to PrefValue.IntPref(120)))
        assertEquals(ThemeMode.Dark, prefs.state.value.mode)
        assertEquals(120, prefs.state.value.fontWidth)
        assertEquals(revision + 1, prefs.state.value.customFontRevision, "the font is loaded again")
    }

    @Test
    fun `importing what was exported before takes an import back`() {
        prefs.setMode(ThemeMode.Dark)
        prefs.setFontShape(weightShift = 10, width = 90, roundness = 5)
        raw.edit().putInt("only_here", 1).commit()
        val before = prefs.exportAll()

        prefs.importAll(mapOf("theme_mode" to PrefValue.StringPref("Light"), "from_backup" to PrefValue.IntPref(2)))
        assertEquals(ThemeMode.Light, prefs.state.value.mode)

        prefs.importAll(before)
        assertEquals(before, prefs.exportAll())
        assertEquals(ThemeMode.Dark, prefs.state.value.mode)
        assertEquals(90, prefs.state.value.fontWidth)
    }
}
