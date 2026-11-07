package com.example.normpix.settings.ui

import android.content.Context
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.settings.AppPreferences
import com.example.normpix.testing.TestEnvironment
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings > Appearance > Colors > Artwork colors: on until the user turns it off, remembered, carried by an app backup like any
 * setting, back on when settings are reset, and found by its own words in Settings search.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class ArtworkColorsSettingTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("artwork_colors_setting")
    }

    @After
    fun tearDown() = env.close()

    private fun store() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("artwork_colors_prefs", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun itIsOnUntilTurnedOff_andRememberedEitherWay() {
        val store = store()
        val preferences = AppPreferences(store)
        assertTrue(preferences.state.value.artworkColors)
        preferences.setArtworkColors(false)
        assertEquals(false, AppPreferences(store).state.value.artworkColors)
        preferences.setArtworkColors(true)
        assertTrue(AppPreferences(store).state.value.artworkColors)
    }

    @Test
    fun anAppBackupCarriesIt_andResettingSettingsBringsItBackOn() {
        val store = store()
        val preferences = AppPreferences(store)
        preferences.setArtworkColors(false)
        assertTrue("artwork_colors" in preferences.exportAll(), "a normal setting: in a backup, not excluded from it")

        preferences.resetToDefaults()
        assertTrue(preferences.state.value.artworkColors)
        assertTrue(AppPreferences(store).state.value.artworkColors)
    }

    @Test
    fun theColorsPageHasTheSwitch_andItWorksTheSetting() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.Colors) } }
        compose.waitForIdle()

        val row = compose.onNode(isToggleable() and hasText("Artwork colors"))
        row.performScrollTo().assertIsOn()
        compose.onNodeWithText("Add and edit screens take their colors from the picture or card they show").assertExists()

        row.performClick()
        compose.waitForIdle()
        assertEquals(false, env.preferences.state.value.artworkColors)
        row.assertIsOff()

        row.performClick()
        compose.waitForIdle()
        assertTrue(env.preferences.state.value.artworkColors)
    }

    @Test
    fun settingsSearchFindsItByItsWords() {
        val entry = SettingsCatalog.entries.single { it.title == "Artwork colors" }
        assertEquals(AppearanceSection.Colors, entry.appearance)
        for (word in listOf("artwork", "cover", "picture", "card", "dynamic")) {
            assertTrue(SettingsCatalog.search(word).any { it.title == "Artwork colors" }, "found by \"$word\"")
        }
    }
}
