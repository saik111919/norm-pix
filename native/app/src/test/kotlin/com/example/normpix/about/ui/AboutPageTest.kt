package com.example.normpix.about.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.BuildConfig
import com.example.normpix.about.GuideLink
import com.example.normpix.about.GuideTopic
import com.example.normpix.navigation.AppDestination
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ui.SettingsCatalog
import com.example.normpix.settings.ui.SettingsScreen
import com.example.normpix.settings.ui.SettingsSpecial
import com.example.normpix.testing.TestEnvironment
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings > About NormPix: the app's name and version, a card for each topic of the guide, and a page for each topic with its
 * steps and a way to the screen it is about. Run on a tall window so the whole page is on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class AboutPageTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    /** The topic [GuidePage] shows; a test changes it to see another without starting over. */
    private var shown by mutableStateOf(GuideTopic.AddTransaction)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("about_page")
    }

    @After
    fun tearDown() = env.close()

    private fun showAbout() {
        compose.setContent { env.Content { AboutPage() } }
        compose.waitForIdle()
    }

    private fun showGuide(topic: GuideTopic) {
        shown = topic
        compose.setContent { env.Content { GuidePage(shown) } }
        compose.waitForIdle()
    }

    private fun show(topic: GuideTopic) {
        compose.runOnIdle { shown = topic }
        compose.waitForIdle()
    }

    // ------------------------------------------------------------------------------------------------ About

    @Test
    fun theAboutPage_namesTheAppAndItsVersion() {
        showAbout()
        compose.onNodeWithText("NormPix").assertExists()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertExists()
        compose.onNodeWithText(GUIDE_HEADING).assertExists()
    }

    @Test
    fun everyTopicHasACard() {
        showAbout()
        for (topic in GuideTopic.entries) compose.onAllNodesWithText(topic.title).assertCountEquals(1)
    }

    @Test
    fun aCardOpensItsTopic() {
        showAbout()
        val topic = GuideTopic.Insights
        compose.onNodeWithText(topic.title).performScrollTo().performClick()
        assertEquals(listOf(topic), env.navigator.guides)
    }

    @Test
    fun everyCardOpensItsOwnTopic() {
        showAbout()
        for (topic in GuideTopic.entries) compose.onNodeWithText(topic.title).performScrollTo().performClick()
        assertEquals(GuideTopic.entries.toList(), env.navigator.guides)
    }

    @Test
    fun thePicturesArePicturesOnly_notReadOutAsText() {
        showAbout()
        // The drawings have words in them (a card's last digits, the names of a form's fields); a screen reader meets none of them.
        compose.onAllNodesWithText("•••• 4242").assertCountEquals(0)
        compose.onAllNodesWithText("Transfer").assertCountEquals(0)
        compose.onAllNodesWithText("Amount").assertCountEquals(0)
    }

    @Test
    fun theSmallPrintIsThere() {
        showAbout()
        compose.onNodeWithText("Everything stays on this device").assertExists()
        compose.onNodeWithText("3D category pictures").assertExists()
        compose.onNodeWithText("GS Flex font").assertExists()
        compose.onNodeWithText("Microsoft Fluent Emoji, under the MIT licence.").assertExists()
    }

    @Test
    fun theUpdatesRowIsOnlyThereWhereTheAppCanUpdate() {
        // The test app has no updater (only release builds update themselves), so there is nothing to open.
        showAbout()
        compose.onAllNodesWithText("Updates & changelog").assertCountEquals(0)
    }

    // ------------------------------------------------------------------------------------------------ a topic

    @Test
    fun aTopicShowsItsSummaryStepsAndTips() {
        showGuide(GuideTopic.AddTransaction)
        for (topic in GuideTopic.entries) {
            show(topic)
            compose.onNodeWithText(topic.summary).assertExists()
            compose.onAllNodesWithText(GUIDE_STEPS_HEADING).assertCountEquals(1)
            for (step in topic.steps) compose.onNodeWithText(step).assertExists()
            for (tip in topic.tips) compose.onNodeWithText(tip).assertExists()
        }
    }

    @Test
    fun theSteps_areNumberedInOrder() {
        showGuide(GuideTopic.AddTransaction)
        for (number in 1..GuideTopic.AddTransaction.steps.size) compose.onAllNodesWithText("$number").assertCountEquals(1)
        compose.onAllNodesWithText("${GuideTopic.AddTransaction.steps.size + 1}").assertCountEquals(0)
    }

    @Test
    fun aTopicWithAScreen_hasAButtonThatGoesThere() {
        showGuide(GuideTopic.CardExchange)
        compose.onNodeWithText(GuideLink.Availability.label).performScrollTo().performClick()
        assertEquals(listOf<SettingsPage?>(SettingsPage.Availability), env.navigator.settingsPages)

        show(GuideTopic.LookAndFeel)
        compose.onNodeWithText(GuideLink.Colors.label).performScrollTo().performClick()
        assertEquals(listOf(AppearanceSection.Colors), env.navigator.appearanceSections)

        show(GuideTopic.Backup)
        compose.onNodeWithText(GuideLink.DataBackup.label).performScrollTo().performClick()
        show(GuideTopic.Privacy)
        compose.onNodeWithText(GuideLink.Security.label).performScrollTo().performClick()
        assertEquals(
            listOf<SettingsPage?>(SettingsPage.Availability, SettingsPage.Data, SettingsPage.Security),
            env.navigator.settingsPages,
        )
    }

    @Test
    fun theTabTopicsGoToTheirTabs() {
        showGuide(GuideTopic.Home)
        compose.onNodeWithText(GuideLink.Home.label).performScrollTo().performClick()
        show(GuideTopic.Insights)
        compose.onNodeWithText(GuideLink.Insights.label).performScrollTo().performClick()
        show(GuideTopic.Wallet)
        compose.onNodeWithText(GuideLink.Wallet.label).performScrollTo().performClick()
        assertEquals(listOf(AppDestination.Home, AppDestination.Insights, AppDestination.Wallet), env.navigator.destinations)

        show(GuideTopic.Activity)
        compose.onNodeWithText(GuideLink.Activity.label).performScrollTo().performClick()
        assertEquals(1, env.navigator.transactionsOpened)

        show(GuideTopic.Plans)
        compose.onNodeWithText(GuideLink.Plans.label).performScrollTo().performClick()
        assertEquals(listOf(null), env.navigator.plansSections)
    }

    @Test
    fun aTopicWithoutAScreen_hasNoButton() {
        showGuide(GuideTopic.ShareReceipt)
        for (link in GuideLink.entries) compose.onAllNodesWithText(link.label).assertCountEquals(0)
        show(GuideTopic.Widgets)
        for (link in GuideLink.entries) compose.onAllNodesWithText(link.label).assertCountEquals(0)
    }

    @Test
    fun aTopicPageGoesBack() {
        showGuide(GuideTopic.Wallet)
        compose.onNode(hasContentDescription("Back") and hasClickAction()).performClick()
        assertEquals(1, env.navigator.backs)
    }

    @Test
    fun aTopicIsDrawnInEveryTheme() {
        // The drawings take the theme's colors; none may fail to draw in a theme that has colors of its own (Volt, Pure).
        showGuide(GuideTopic.LookAndFeel)
        for (accent in AccentColor.entries) {
            env.preferences.setAccent(accent)
            compose.waitForIdle()
            compose.onNodeWithText(GuideTopic.LookAndFeel.summary).assertExists()
        }
    }

    // ------------------------------------------------------------------------------------------------ from Settings

    @Test
    fun theSettingsListOpensTheAboutPage_notADialog() {
        compose.setContent { env.Content { SettingsScreen() } }
        compose.waitForIdle()
        compose.onNodeWithText("About NormPix").performScrollTo().performClick()
        assertEquals(1, env.navigator.aboutOpened)
    }

    @Test
    fun settingsSearchFindsTheGuide() {
        for (query in listOf("about", "guide", "help", "how it works", "tips", "licenses", "credits", "privacy", "version")) {
            val found = SettingsCatalog.search(query).filter { it.special == SettingsSpecial.About }
            assertEquals(1, found.size, "\"$query\" finds About")
        }
    }
}
