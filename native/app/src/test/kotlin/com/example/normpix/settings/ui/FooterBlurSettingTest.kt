package com.example.normpix.settings.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Settings > Appearance > Effects: the footer's blur (the page blurred and faded under the dock) has its own switch, on by
 * default, that follows the background blur; and card tilt is there whether or not saved cards are kept, since the accounts
 * lean too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class FooterBlurSettingTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("footer_blur_setting")
    }

    @After
    fun tearDown() = env.close()

    private fun open() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.Effects) } }
        compose.waitForIdle()
    }

    @Test
    fun itIsOnByDefault() {
        assertTrue(env.preferences.state.value.footerBlurEnabled)
    }

    @Test
    fun theEffectsPageTogglesItOnAndOff() {
        open()
        val row = compose.onNodeWithText("Footer blur")
        row.assertIsOn()
        row.performClick()
        compose.waitForIdle()
        assertFalse(env.preferences.state.value.footerBlurEnabled)
        row.assertIsOff()
        row.performClick()
        compose.waitForIdle()
        assertTrue(env.preferences.state.value.footerBlurEnabled)
    }

    @Test
    fun itFollowsTheBackgroundBlurSwitch() {
        open()
        compose.onNodeWithText("Footer blur").assertIsEnabled()
        env.preferences.setSheetBlurEnabled(false)
        compose.waitForIdle()
        compose.onNodeWithText("Footer blur").assertIsNotEnabled()
    }

    @Test
    fun cardTiltIsOfferedEvenWhereSavedCardsAreOff() {
        env.preferences.setWalletCardsEnabled(false)
        open()
        compose.onNodeWithText("Card tilt").assertExists()
        compose.onNodeWithText("Accounts and saved cards lean a few degrees as you move your phone").assertExists()
    }

    @Test
    fun theChoiceIsKept_andTravelsWithAnAppBackup() {
        env.preferences.setFooterBlurEnabled(false)
        val reopened = AppPreferences(env.prefsStore)
        assertFalse(reopened.state.value.footerBlurEnabled)
        assertNotNull(reopened.exportAll()["footer_blur_enabled"])
    }

    @Test
    fun settingsCatalogFindsItUnderEffects() {
        val entry = SettingsCatalog.search("footer blur").firstOrNull()
        assertNotNull(entry)
        assertEquals(AppearanceSection.Effects, entry.appearance)
    }
}
