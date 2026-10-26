package com.example.normpix.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.settings.ui.AppearanceSectionPage
import com.example.normpix.settings.ui.SettingsCatalog
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

/**
 * Settings > Appearance > Layouts: Home's top spending, the Wallet's accounts and its cards, each with the carousel among its
 * layouts, on one page. Each choice is its own and is kept, and search finds them.
 */
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

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    // The three lists share names ("Stacked cards", "Carousel"): a choice is known by what it says it does.
    private fun choice(description: String) = compose.onNode(hasText(description) and radio)

    private fun show() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.Layouts) } }
        compose.waitForIdle()
    }

    @Test
    fun theLayoutsOfHomeAndTheWalletAreOnOnePage() {
        show()
        compose.onNodeWithText("Top spending on Home").assertExists()
        compose.onNodeWithText("Accounts in the Wallet").assertExists()
        compose.onNodeWithText("Cards in the Wallet").assertExists()
        for (layout in TopSpendingLayout.entries) choice(layout.description).performScrollTo().assertIsDisplayed()
        for (layout in AccountsLayout.entries) choice(layout.description).performScrollTo().assertIsDisplayed()
        for (layout in CardsLayout.entries) choice(layout.description).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theCarouselIsOneOfTheCardsLayouts_andIsKept() {
        show()
        choice(CardsLayout.Carousel.description).performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(CardsLayout.Carousel, env.preferences.state.value.cardsLayout)
        assertEquals(AccountsLayout.Stack, env.preferences.state.value.accountsLayout, "the accounts' choice is its own")
        assertEquals(TopSpendingLayout.Stack, env.preferences.state.value.topSpendingLayout, "and Home's")
        assertEquals(CardsLayout.Carousel, AppPreferences(env.prefsStore).state.value.cardsLayout, "kept across a restart")
        choice(CardsLayout.Carousel.description).assertIsSelected()
    }

    @Test
    fun theCarouselIsOneOfTheAccountsLayoutsToo_andIsKept() {
        show()
        choice(AccountsLayout.Carousel.description).performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(AccountsLayout.Carousel, env.preferences.state.value.accountsLayout)
        assertEquals(CardsLayout.Cards, env.preferences.state.value.cardsLayout, "the cards' choice is its own")
        assertEquals(AccountsLayout.Carousel, AppPreferences(env.prefsStore).state.value.accountsLayout, "kept across a restart")
        choice(AccountsLayout.Carousel.description).assertIsSelected()
    }

    @Test
    fun withStoreCardsOffThereAreNoCardsToLayOut() {
        env.preferences.setWalletCardsEnabled(false)
        show()
        compose.onAllNodesWithText("Cards in the Wallet").assertCountEquals(0)
        compose.onNodeWithText("Accounts in the Wallet").assertExists()
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
