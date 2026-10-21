package com.example.normpix.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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

        // The accounts' layouts share three names with the cards' (twice on the page each); the carousel is the cards' alone.
        for (layout in CardsLayout.entries) {
            compose.onAllNodesWithText(layout.label).assertCountEquals(if (layout == CardsLayout.Carousel) 1 else 2)
        }
        compose.onNodeWithText("Carousel").performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertEquals(CardsLayout.Carousel, env.preferences.state.value.cardsLayout)
        assertEquals(CardsLayout.Carousel, AppPreferences(env.prefsStore).state.value.cardsLayout, "kept across a restart")
        compose.onNodeWithText("Carousel").assertIsSelected()
    }

    @Test
    fun searchFindsTheCardsLayoutByCarousel_onlyWhileCardsAreStored() {
        assertTrue(SettingsCatalog.search("carousel", cardsEnabled = true).any { it.title == "Cards layout" })
        assertTrue(SettingsCatalog.search("swipe", cardsEnabled = true).any { it.title == "Cards layout" })
        assertFalse(SettingsCatalog.search("carousel", cardsEnabled = false).any { it.title == "Cards layout" })
    }
}
