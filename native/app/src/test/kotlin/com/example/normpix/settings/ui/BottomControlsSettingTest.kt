package com.example.normpix.settings.ui

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isToggleable
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.settings.AppPreferences
import com.example.normpix.testing.TestEnvironment
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings > Appearance > Navigation & controls > Bottom controls: one switch, beside the back button and the month control
 * rather than a page of its own, off until the user turns it on, and found by the words a user would look for it by.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class BottomControlsSettingTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("bottom_controls_setting")
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun itIsOffUntilTheUserTurnsItOn() {
        assertFalse(env.preferences.state.value.bottomControls)
    }

    @Test
    fun itSitsWithTheOtherControls_whoseRowSaysWhenItIsOn_andOpensTheirPage() {
        compose.setContent { env.Content { AppearanceSettingsPage() } }
        compose.waitForIdle()
        // Not a row of its own: with the back button and the month control, on one page.
        compose.onAllNodesWithText("Bottom controls").assertCountEquals(0)
        compose.onNodeWithText("Navigation & controls").performScrollTo().assertExists()

        env.preferences.setBottomControls(true)
        compose.waitForIdle()
        compose.onNodeWithText("Bottom controls ·", substring = true).assertExists()

        compose.onNodeWithText("Navigation & controls").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(AppearanceSection.Controls), env.navigator.appearanceSections)
    }

    @Test
    fun theControlsPageHasOneSwitch_thatTurnsItOnAndOff() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.Controls) } }
        compose.waitForIdle()
        val switch = compose.onNode(isToggleable())
        switch.assertIsOff()

        switch.performClick()
        compose.waitForIdle()
        assertTrue(env.preferences.state.value.bottomControls)
        switch.assertIsOn()

        switch.performClick()
        compose.waitForIdle()
        assertFalse(env.preferences.state.value.bottomControls)
        switch.assertIsOff()
    }

    @Test
    fun theChoiceIsKept_andTravelsWithAnAppBackup() {
        env.preferences.setBottomControls(true)

        val reopened = AppPreferences(env.prefsStore)
        assertTrue(reopened.state.value.bottomControls, "a new run of the app reads it back")
        assertNotNull(reopened.exportAll()["bottom_controls"], "and a backup carries it")

        // A throwaway store is untouched by this one.
        val other = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("bottom_controls_other", Context.MODE_PRIVATE)
        other.edit().clear().commit()
        assertFalse(AppPreferences(other).state.value.bottomControls)
    }

    @Test
    fun settingsSearchFindsIt_byTheWordsAUserWouldTry() {
        for (word in listOf("bottom", "thumb", "one hand", "search bar")) {
            val found = SettingsCatalog.search(word, cardsEnabled = true).map { it.title }
            assertTrue("Bottom controls" in found, "\"$word\" finds it: $found")
        }
        val entry = SettingsCatalog.search("bottom controls", cardsEnabled = true).first { it.title == "Bottom controls" }
        assertEquals(AppearanceSection.Controls, entry.appearance)
    }
}
