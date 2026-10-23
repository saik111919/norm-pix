package com.example.normpix.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.settings.ui.SettingsCatalog
import com.example.normpix.settings.ui.WalletSettingsPage
import com.example.normpix.testing.TestEnvironment
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings > Wallet & Cards > Cards layout lists the carousel with the other layouts; the choice is kept, and search finds it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CardsLayoutSettingTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("cards_layout_setting")
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun theCarouselIsOneOfTheCardsLayouts_andIsKept() {
        compose.setContent { env.Content { WalletSettingsPage() } }
        compose.waitForIdle()

        // The accounts' layouts and the cards' have the same names: each is on the page twice, the accounts' first.
        for (layout in CardsLayout.entries) compose.onAllNodesWithText(layout.label).assertCountEquals(2)
        compose.onAllNodesWithText("Carousel")[1].performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertEquals(CardsLayout.Carousel, env.preferences.state.value.cardsLayout)
        assertEquals(AccountsLayout.Stack, env.preferences.state.value.accountsLayout, "the accounts' choice is its own")
        assertEquals(CardsLayout.Carousel, AppPreferences(env.prefsStore).state.value.cardsLayout, "kept across a restart")
        compose.onAllNodesWithText("Carousel")[1].assertIsSelected()
    }

    @Test
    fun theCarouselIsOneOfTheAccountsLayoutsToo_andIsKept() {
        compose.setContent { env.Content { WalletSettingsPage() } }
        compose.waitForIdle()

        compose.onAllNodesWithText("Carousel")[0].performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertEquals(AccountsLayout.Carousel, env.preferences.state.value.accountsLayout)
        assertEquals(CardsLayout.Cards, env.preferences.state.value.cardsLayout, "the cards' choice is its own")
        assertEquals(AccountsLayout.Carousel, AppPreferences(env.prefsStore).state.value.accountsLayout, "kept across a restart")
        compose.onAllNodesWithText("Carousel")[0].assertIsSelected()
    }

    @Test
    fun searchFindsTheLayoutsByCarousel() {
        assertTrue(SettingsCatalog.search("carousel", cardsEnabled = true).any { it.title == "Cards layout" })
        assertTrue(SettingsCatalog.search("carousel", cardsEnabled = true).any { it.title == "Accounts layout" })
        assertTrue(SettingsCatalog.search("swipe", cardsEnabled = true).any { it.title == "Cards layout" })
        // With Store cards off there are no cards to lay out: only the accounts' layout is found.
        assertFalse(SettingsCatalog.search("carousel", cardsEnabled = false).any { it.title == "Cards layout" })
        assertTrue(SettingsCatalog.search("carousel", cardsEnabled = false).any { it.title == "Accounts layout" })
    }
}
