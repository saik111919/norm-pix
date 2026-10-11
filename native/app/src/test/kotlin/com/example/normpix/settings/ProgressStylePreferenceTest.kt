package com.example.normpix.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.ui.SettingsCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The progress style the user picks: wavy until they say otherwise, remembered, and found by Settings search. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressStylePreferenceTest {
    private fun store() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("progress_style_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun isWavyUntilTheUserPicksAnother_andIsRemembered() {
        val store = store()
        assertEquals(ProgressStyle.Wavy, AppPreferences(store).state.value.progressStyle)

        AppPreferences(store).setProgressStyle(ProgressStyle.Flat)
        assertEquals(ProgressStyle.Flat, AppPreferences(store).state.value.progressStyle, "read back by the next launch")
    }

    @Test
    fun anUnknownStoredValueFallsBackToTheDefault() {
        val store = store()
        store.edit().putString("progress_style", "Squiggly").commit()
        assertEquals(ProgressStyle.Wavy, AppPreferences(store).state.value.progressStyle)
    }

    @Test
    fun resettingSettingsBringsTheDefaultBack() {
        val preferences = AppPreferences(store())
        preferences.setProgressStyle(ProgressStyle.Flat)
        preferences.resetToDefaults()
        assertEquals(ProgressStyle.Wavy, preferences.state.value.progressStyle)
    }

    @Test
    fun settingsSearchFindsItInAppearance() {
        for (query in listOf("progress", "loading", "spinner", "wavy")) {
            val hit = SettingsCatalog.search(query).single { it.title == "Progress indicators" }
            assertEquals(SettingsPage.Appearance, hit.group, query)
        }
        assertTrue(SettingsCatalog.search("dark").none { it.title == "Progress indicators" })
    }

    @Test
    fun everyStyleIsDescribed() {
        for (style in ProgressStyle.entries) assertTrue(style.label.isNotBlank() && style.description.isNotBlank(), style.name)
    }
}
