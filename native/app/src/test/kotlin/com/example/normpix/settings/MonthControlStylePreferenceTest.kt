package com.example.normpix.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.ui.SettingsCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How the month is switched is a choice of two looks - the track, and the earlier split buttons: the track unless picked,
 * kept once picked, reset with the rest, and found by Settings search (also by what the earlier one was called).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonthControlStylePreferenceTest {
    private fun store() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("month_control_style_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun hasTheTrackAndTheEarlierSplitButtons_andTheTrackIsTheDefault() {
        assertEquals(listOf(MonthControlStyle.Track, MonthControlStyle.Split), MonthControlStyle.entries.toList())
        assertEquals(listOf("Track", "Split buttons"), MonthControlStyle.entries.map { it.label })
        assertEquals(MonthControlStyle.Track, AppPrefsState().monthControlStyle)
        assertEquals(MonthControlStyle.Track, AppPreferences(store()).state.value.monthControlStyle)
    }

    @Test
    fun aPickedStyleIsKept() {
        val store = store()
        val preferences = AppPreferences(store)
        preferences.setMonthControlStyle(MonthControlStyle.Split)

        assertEquals(MonthControlStyle.Split, preferences.state.value.monthControlStyle)
        assertEquals(MonthControlStyle.Split, AppPreferences(store).state.value.monthControlStyle, "read back by the next launch")
        assertTrue(store.contains("month_control_style"))

        preferences.setMonthControlStyle(MonthControlStyle.Track)
        assertEquals(MonthControlStyle.Track, AppPreferences(store).state.value.monthControlStyle, "and back again")
    }

    @Test
    fun anUnknownStoredValueFallsBackToTheTrack() {
        val store = store()
        store.edit().putString("month_control_style", "Pill").commit()
        assertEquals(MonthControlStyle.Track, AppPreferences(store).state.value.monthControlStyle)
    }

    @Test
    fun resettingSettingsBringsTheTrackBack() {
        val store = store()
        val preferences = AppPreferences(store)
        preferences.setMonthControlStyle(MonthControlStyle.Split)
        preferences.resetToDefaults()

        assertEquals(MonthControlStyle.Track, preferences.state.value.monthControlStyle)
        assertFalse(store.contains("month_control_style"))
    }

    @Test
    fun theChoiceIsInAnAppBackup() {
        val preferences = AppPreferences(store())
        preferences.setMonthControlStyle(MonthControlStyle.Split)
        assertTrue("month_control_style" in preferences.exportAll(), "a backup carries it without anyone listing it")
    }

    @Test
    fun settingsSearchFindsItByWhatItDoes_andByWhatTheEarlierOneWasCalled() {
        for (query in listOf("month", "month control", "split", "classic", "earlier", "steps")) {
            val hit = SettingsCatalog.search(query).single { it.title == "Month control" }
            assertEquals(SettingsPage.Appearance, hit.group, query)
            assertEquals(AppearanceSection.MonthControl, hit.appearance, query)
            assertEquals("Appearance > Month control", hit.location, query)
        }
    }
}
