package com.example.normpix.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.backup.SettingsBackupCodec
import com.example.normpix.settings.ui.CARD_LIMIT_WARNING_TITLE
import com.example.normpix.settings.ui.SettingsCatalog
import com.example.normpix.settings.ui.WalletSettingsPage
import com.example.normpix.navigation.SettingsPage
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
 * Settings > Wallet & Cards > Card limit warning (owner, 2026-11-14: warn at 30% by default): Off, 30%, 50%, 75% or 90% of a credit
 * card's limit, chosen in an option sheet; 30% until the user picks another. It is a setting like the others - kept across a
 * restart, put back by Reset, carried by an app backup, found by Settings search - and its row says why 30% is where it starts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CardLimitWarningSettingTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("card_limit_warning_setting")
    }

    @After
    fun tearDown() = env.close()

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun choice(label: String) = compose.onNode(hasText(label) and radio)

    private fun show() {
        compose.setContent { env.Content { WalletSettingsPage() } }
        compose.waitForIdle()
    }

    private fun openSheet() {
        compose.onNodeWithText(CARD_LIMIT_WARNING_TITLE).performScrollTo().performClick()
        compose.waitForIdle()
    }

    // -------------------------------------------------------------------------------------------------- the setting

    @Test
    fun itStartsAt30Percent_andThereAreFiveLevels() {
        assertEquals(CardLimitWarning.At30, env.preferences.state.value.cardLimitWarning)
        assertEquals(CardLimitWarning.At30, AppPreferences(env.prefsStore).state.value.cardLimitWarning)
        assertEquals(listOf("Off", "30%", "50%", "75%", "90%"), CardLimitWarning.entries.map { it.label })
        assertEquals(listOf(0, 30, 50, 75, 90), CardLimitWarning.entries.map { it.percent })
        assertFalse(CardLimitWarning.Off.on)
        assertTrue(CardLimitWarning.entries.drop(1).all { it.on })
        assertFalse(env.prefsStore.contains("card_limit_warning"), "a default is not stored: only a pick is")
    }

    @Test
    fun aChangeIsKeptAcrossARestart_underAKeyThatNeverChanges() {
        for (level in CardLimitWarning.entries) {
            env.preferences.setCardLimitWarning(level)
            assertEquals(level, env.preferences.state.value.cardLimitWarning)
            assertEquals(level, AppPreferences(env.prefsStore).state.value.cardLimitWarning, "read back by the next start")
            assertEquals(level.name, env.prefsStore.getString("card_limit_warning", null))
        }
        // The names are what is stored: they are the contract with every phone that already has one.
        assertEquals(listOf("Off", "At30", "At50", "At75", "At90"), CardLimitWarning.entries.map { it.name })
    }

    @Test
    fun aValueNoVersionKnowsFallsBackTo30Percent() {
        env.prefsStore.edit().putString("card_limit_warning", "At42").commit()
        assertEquals(CardLimitWarning.At30, AppPreferences(env.prefsStore).state.value.cardLimitWarning)
    }

    @Test
    fun resetSettingsPutsItBackTo30Percent() {
        env.preferences.setCardLimitWarning(CardLimitWarning.Off)
        env.preferences.resetToDefaults()
        assertEquals(CardLimitWarning.At30, env.preferences.state.value.cardLimitWarning)
        assertFalse(env.prefsStore.contains("card_limit_warning"))
    }

    @Test
    fun anAppBackupCarriesIt_andARestoreBringsItBack() {
        env.preferences.setCardLimitWarning(CardLimitWarning.At75)
        val exported = env.preferences.exportAll()
        assertEquals(PrefValue.StringPref("At75"), exported["card_limit_warning"], "it is about the user's money, not this phone: not excluded")

        // Through the file a backup is, into a phone that has never heard of it.
        val read = assertNotNull(SettingsBackupCodec.decode(SettingsBackupCodec.encode(exported)))
        assertEquals(0, read.skipped)
        val other = TestEnvironment("card_limit_warning_other")
        try {
            assertEquals(CardLimitWarning.At30, other.preferences.state.value.cardLimitWarning)
            assertEquals(0, other.preferences.importAll(read.values))
            assertEquals(CardLimitWarning.At75, other.preferences.state.value.cardLimitWarning)
        } finally {
            other.close()
        }
    }

    @Test
    fun settingsSearchFindsIt_whateverItIsCalled() {
        for (query in listOf("card limit", "credit limit", "limit warning", "utilisation", "utilization", "30%", "credit score", "over limit")) {
            val found = SettingsCatalog.search(query).firstOrNull { it.title == CARD_LIMIT_WARNING_TITLE }
            assertNotNull(found, "'$query' finds it")
            assertEquals(SettingsPage.Wallet, found.page)
        }
        // About the account's own limit, not the saved cards: it is there with Store cards off too.
        assertTrue(SettingsCatalog.search("credit limit", cardsEnabled = false).any { it.title == CARD_LIMIT_WARNING_TITLE })
    }

    // ------------------------------------------------------------------------------------------------------- the row

    @Test
    fun theRowSaysWhere30PercentComesFrom_andTheSheetOffersTheFiveLevels() {
        show()
        compose.onNodeWithText(CARD_LIMIT_WARNING_TITLE).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Warns at 30% of a card's limit used. Lenders look at how much of a card's limit is used, and keeping it under 30% is the usual advice.",
        ).assertIsDisplayed()

        openSheet()
        compose.onNodeWithText("Tell me when a credit card has used this much of its limit").assertIsDisplayed()
        for (level in CardLimitWarning.entries) choice(level.label).assertIsDisplayed()
        choice("30%").assertIsSelected()
        compose.onNodeWithText("The usual advice: keep a card under 30% of its limit").assertIsDisplayed()
    }

    @Test
    fun pickingALevelKeepsItAndSaysItOnTheRow_andOffSaysNoWarnings() {
        show()
        openSheet()
        choice("75%").performClick()
        compose.waitForIdle()
        assertEquals(CardLimitWarning.At75, env.preferences.state.value.cardLimitWarning)
        assertEquals(CardLimitWarning.At75, AppPreferences(env.prefsStore).state.value.cardLimitWarning)
        compose.onNodeWithText("Warns at 75% of a card's limit used.", substring = true).assertIsDisplayed()

        openSheet()
        choice("75%").assertIsSelected()
        choice("Off").performClick()
        compose.waitForIdle()
        assertEquals(CardLimitWarning.Off, env.preferences.state.value.cardLimitWarning)
        compose.onNodeWithText("No warnings. Lenders look at how much of a card's limit is used", substring = true).assertIsDisplayed()
    }

    @Test
    fun theRowIsThereWithStoreCardsOff_itIsAboutTheAccountNotTheVault() {
        env.preferences.setWalletCardsEnabled(false)
        show()
        compose.onNodeWithText(CARD_LIMIT_WARNING_TITLE).performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Save CVV").fetchSemanticsNodes().size, "the page really is the cards-off one")
    }
}
