package com.example.normpix.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.ui.SettingsCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What plays at launch and how the dock moves its pill are choices (Settings > Appearance > Transitions), each with the way it
 * used to be kept beside the new one: a default, kept, travelling in a backup, and found by Settings search.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchAndDockPreferencesTest {
    private fun store() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("launch_and_dock_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun theNewLooksAreTheDefaults_andTheOldOnesAreStillChoices() {
        val prefs = AppPreferences(store())
        assertEquals(LaunchAnimation.Cookie, prefs.state.value.launchAnimation)
        assertEquals(DockSelectionStyle.Glide, prefs.state.value.dockSelection)
        assertEquals(listOf("Cookie", "Name"), LaunchAnimation.entries.map { it.label })
        assertEquals(listOf("Glide", "Fade in place"), DockSelectionStyle.entries.map { it.label })
    }

    @Test
    fun theChoicesAreKeptTravelInABackupAndComeBack() {
        val context = store()
        val prefs = AppPreferences(context)
        prefs.setLaunchAnimation(LaunchAnimation.Name)
        prefs.setDockSelection(DockSelectionStyle.Fade)
        // Each travels as a stored string of its own name (a backup writes the name, and reads it back by name).
        assertTrue(prefs.exportAll()["launch_animation"].toString().contains("Name"), "launch_animation travels in a backup")
        assertTrue(prefs.exportAll()["dock_selection"].toString().contains("Fade"), "dock_selection travels in a backup")

        val again = AppPreferences(context)
        assertEquals(LaunchAnimation.Name, again.state.value.launchAnimation)
        assertEquals(DockSelectionStyle.Fade, again.state.value.dockSelection)
    }

    @Test
    fun aValueNoVersionOfTheAppKnowsFallsBackToTheDefault() {
        val context = store()
        context.edit().putString("launch_animation", "Fireworks").putString("dock_selection", "Teleport").commit()
        val prefs = AppPreferences(context)
        assertEquals(LaunchAnimation.Cookie, prefs.state.value.launchAnimation)
        assertEquals(DockSelectionStyle.Glide, prefs.state.value.dockSelection)
    }

    @Test
    fun settingsSearchFindsThemOnTheTransitionsPage() {
        for (query in listOf("launch animation", "splash", "cookie")) {
            val hit = SettingsCatalog.search(query).firstOrNull { it.title == "Launch animation" }
            assertTrue(hit != null, "'$query' finds the launch animation")
            assertEquals(SettingsPage.Appearance, hit.page)
            assertEquals(AppearanceSection.Transitions, hit.appearance)
        }
        for (query in listOf("dock animation", "footer", "pill")) {
            val hit = SettingsCatalog.search(query).firstOrNull { it.title == "Dock animation" }
            assertTrue(hit != null, "'$query' finds the dock animation")
            assertEquals(AppearanceSection.Transitions, hit.appearance)
        }
    }
}
