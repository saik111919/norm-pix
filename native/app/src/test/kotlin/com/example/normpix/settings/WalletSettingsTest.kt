package com.example.normpix.settings

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.settings.ui.AppearanceSectionPage
import com.example.normpix.settings.ui.SettingsCatalog
import com.example.normpix.settings.ui.WalletSettingsPage
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

/** The two settings from the card brief: when copied details clear, and whether cards tilt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class WalletSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("wallet_settings")
    }

    @After
    fun tearDown() = env.close()

    private fun show() {
        compose.setContent { env.Content { WalletSettingsPage() } }
        compose.waitForIdle()
    }

    @Test
    fun theClipboardTimeoutOffersTheFiveChoicesWithTenSecondsSelected() {
        show()
        for (label in listOf("10 seconds", "30 seconds", "1 minute", "5 minutes", "Never")) compose.onNodeWithText(label).performScrollTo()
        compose.onNodeWithText("10 seconds").assertIsSelected()
        assertEquals(ClipboardTimeout.Sec10, env.preferences.state.value.clipboardTimeout)
    }

    @Test
    fun theWalletsLookIsNotRepeatedHere_oneRowLeadsToIt() {
        show()
        for (gone in listOf("Accounts layout", "Cards layout", "Bank logos", "Card tilt")) compose.onAllNodesWithText(gone).assertCountEquals(0)
        compose.onNodeWithText("Accounts and cards layout").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(AppearanceSection.Layouts), env.navigator.appearanceSections)
    }

    @Test
    fun pickingATimeoutIsRememberedAndShown() {
        show()
        compose.onNodeWithText("1 minute").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("1 minute").assertIsSelected()
        assertEquals(ClipboardTimeout.Min1, env.preferences.state.value.clipboardTimeout)

        // A fresh AppPreferences over the same file (a restart) reads it back.
        assertEquals(ClipboardTimeout.Min1, AppPreferences(env.prefsStore).state.value.clipboardTimeout)
    }

    @Test
    fun cardTiltIsOnByDefault_andItsSwitchIsWithTheOtherEffects() {
        // How the cards look is with the rest of how NormPix looks: Appearance > Effects, beside the background blur.
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.Effects) } }
        compose.waitForIdle()
        compose.onNodeWithText("Background blur").assertExists()
        compose.onNodeWithText("Card tilt").performScrollTo().assertIsOn()
        compose.onNodeWithText("Card tilt").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Card tilt").assertIsOff()
        assertEquals(false, env.preferences.state.value.cardTiltEnabled)
        assertEquals(false, AppPreferences(env.prefsStore).state.value.cardTiltEnabled, "remembered across restarts")
    }
}

class WalletPreferencesTest {
    @Test
    fun everyTimeoutHasItsSecondsAndNeverMeansNoTimer() {
        assertEquals(listOf(10, 30, 60, 300, 0), ClipboardTimeout.entries.map { it.seconds })
        assertTrue(ClipboardTimeout.entries.filter { it != ClipboardTimeout.Never }.all { it.clears })
        assertEquals(false, ClipboardTimeout.Never.clears)
    }

    @Test
    fun theCatalogFindsBothSettings() {
        val tilt = SettingsCatalog.search("gyroscope").map { it.title }
        assertTrue("Card tilt" in tilt, "$tilt")
        val clipboard = SettingsCatalog.search("clipboard").map { it.title }
        assertTrue("Clear copied card details after" in clipboard, "$clipboard")
        assertEquals(AppearanceSection.Effects, SettingsCatalog.entries.single { it.title == "Card tilt" }.appearance)
    }
}
