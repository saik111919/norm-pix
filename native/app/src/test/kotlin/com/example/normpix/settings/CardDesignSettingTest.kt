package com.example.normpix.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.settings.ui.AppearanceSectionPage
import com.example.normpix.settings.ui.SettingsCatalog
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
 * Settings > Appearance > Layouts > Card design: Classic by default, offers Banner, Ledger, and Tinted.
 * Persists across restarts, exports in backups, and is searchable in SettingsCatalog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CardDesignSettingTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("card_design_setting")
    }

    @After
    fun tearDown() = env.close()

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun choice(description: String) = compose.onNode(hasText(description) and radio)

    private fun show() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.Layouts) } }
        compose.waitForIdle()
    }

    @Test
    fun defaultIsClassic() {
        assertEquals(CardDesign.Classic, env.preferences.state.value.cardDesign)
        assertEquals(CardDesign.Classic, AppPreferences(env.prefsStore).state.value.cardDesign)
    }

    @Test
    fun allDesignsAreOfferedOnLayoutsPage() {
        show()
        compose.onNodeWithText("Card design").assertExists()
        for (design in CardDesign.entries) {
            choice(design.description).performScrollTo().assertIsDisplayed()
        }
        choice(CardDesign.Classic.description).assertIsSelected()
    }

    @Test
    fun changingDesignPersistsAcrossReRead() {
        show()
        for (design in listOf(CardDesign.Banner, CardDesign.Ledger, CardDesign.Tinted, CardDesign.Classic)) {
            choice(design.description).performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(design, env.preferences.state.value.cardDesign)
            assertEquals(design, AppPreferences(env.prefsStore).state.value.cardDesign)
            choice(design.description).assertIsSelected()
        }
    }

    @Test
    fun exportAllIncludesCardDesign() {
        env.preferences.setCardDesign(CardDesign.Banner)
        val exported = env.preferences.exportAll()
        assertEquals(PrefValue.StringPref("Banner"), exported["card_design"])
    }

    @Test
    fun searchCatalogFindsCardDesign() {
        val results = SettingsCatalog.search("card design")
        assertTrue(results.any { it.title == "Card design" && it.appearance == AppearanceSection.Layouts })
    }
}