package com.example.normpix.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.isListDetail
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings on a tablet: the list on the left, always there, and the page a row opens on the right, as a tablet's own Settings
 * is; on a phone, a page alone over the list, as it was.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp")
class SettingsTwoPaneTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var env: TestEnvironment? = null

    @After
    fun tearDown() {
        env?.close()
    }

    /** The app, with Settings opened from the rail or the dock (it loads in the background, so this waits for it). */
    private fun openSettings(configure: (TestEnvironment) -> Unit = {}) {
        val environment = TestEnvironment(prefsName = "settings_two_pane").also { env = it }
        configure(environment)
        compose.setContent { NormPixRoot(container = environment.container) }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            compose.onAllNodes(hasText("Recent")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasText("Settings")).onFirst().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            compose.onAllNodes(hasText("Search settings")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun first(text: String): DpRect = compose.onAllNodes(hasText(text)).onFirst().getUnclippedBoundsInRoot()

    private fun last(text: String): DpRect = compose.onAllNodes(hasText(text)).onLast().getUnclippedBoundsInRoot()

    private fun count(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    private fun tapRow(text: String) {
        // A short window (a phone on its side) shows only the top of the list: the row is scrolled to first.
        compose.onAllNodes(hasText(text)).onFirst().performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** Whether the row of the list that says [text] is the filled, selected one. */
    private fun isSelectedRow(text: String): Boolean = compose.onAllNodes(
        hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
    ).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theListIsOnTheLeft_andAPageIsOpenBesideIt() {
        openSettings()
        // The list holds its place in the first 360dp ...
        val list = first("Search settings")
        assertTrue(list.right <= AppLayout.listPaneWidth, "the search field is in the list: ${list.right}")
        assertTrue(first("Finance").right <= AppLayout.listPaneWidth, "Finance is a row of the list")
        // ... and the first group's page is open on the right, with its own title there.
        val page = last("Appearance")
        assertTrue(page.left >= AppLayout.listPaneWidth, "Appearance's page starts after the list: ${page.left}")
        assertTrue(isSelectedRow("Appearance"), "its row is the filled one")
        assertFalse(isSelectedRow("Finance"))
    }

    @Test
    fun choosingARow_swapsThePageBesideTheList() {
        openSettings()
        tapRow("Finance")
        val page = last("Finance")
        assertTrue(page.left >= AppLayout.listPaneWidth, "Finance's page is on the right: ${page.left}")
        assertTrue(isSelectedRow("Finance"))
        assertFalse(isSelectedRow("Appearance"), "only the open page's row is filled")
        // The list is still there, whole: nothing was pushed over it.
        assertTrue(first("Search settings").right <= AppLayout.listPaneWidth)
        // A page that the list opens has no way back of its own: the one Back there is leaves Settings.
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(1)
    }

    @Test
    fun aScreenInsideAPage_hasItsOwnBack_thatGoesUpToThePage() {
        openSettings()
        tapRow("Appearance")
        // Appearance's screens (Colors, Interface font...) are steps inside it: a row there opens one beside the list.
        compose.onAllNodes(hasText(AppearanceSection.Colors.title)).onFirst().performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(2)
        assertTrue(isSelectedRow("Appearance"), "the list still stands on Appearance")
        // The page's Back (the last, on the right) goes up to Appearance; the list stays where it is.
        compose.onAllNodesWithContentDescription("Back").onLast().performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(1)
        assertTrue(first("Search settings").right <= AppLayout.listPaneWidth)
    }

    @Test
    fun theListOpensWithTheSearchField_andTheProfilePictureAtItsEnd() {
        openSettings()
        // As a tablet's own Settings: the search field across the top, the user's picture after it, no title and no profile row.
        val search = first("Search settings")
        val profile = compose.onNodeWithContentDescription("Profile").getUnclippedBoundsInRoot()
        assertTrue(search.top < 80.dp && profile.top < 80.dp, "both are at the top: ${search.top} ${profile.top}")
        assertTrue(profile.left >= search.right && profile.right <= AppLayout.listPaneWidth, "the picture is after the field, in the list: $profile")
        assertEquals(0, count("Set up your profile"), "the profile is the picture, not a row")
        assertEquals(0, count("Settings"), "the list has no title of its own")
    }

    @Test
    fun theProfile_opensBesideTheList_withoutALinkBackToSettings() {
        openSettings()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.waitForIdle()
        assertTrue(last("Add your name").left >= AppLayout.listPaneWidth, "the Profile page is on the right")
        compose.onNodeWithContentDescription("Profile").assertIsSelected()
        // Its row of links to Settings is for a page that has no list beside it.
        assertEquals(0, count("Appearance, finance, data, security and more"))
        assertFalse(isSelectedRow("Appearance"), "the group that was open no longer is")
    }

    @Test
    fun inTheBottomDockStyle_theDockIsAtTheFootOfTheWindow_notOfTheList() {
        openSettings { it.container.preferences.setBackButtonStyle(BackButtonStyle.BottomDock) }
        // One dock, with Settings' name on it, and no back circle or bar button of the pages': Back is the dock's.
        compose.onAllNodesWithContentDescription("Back, Settings").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        val back = compose.onNodeWithContentDescription("Back, Settings").getUnclippedBoundsInRoot()
        val search = compose.onAllNodesWithContentDescription("Search").onLast().getUnclippedBoundsInRoot()
        // The pill and its search button are centred on the window (1280dp), not on the list's 360dp, and near its foot (800dp).
        val centre = (back.left + search.right) / 2
        assertEquals(640f, centre.value, 24f, "the dock is centred on the window: $back $search")
        assertTrue(back.left > AppLayout.listPaneWidth, "the dock starts after the list: ${back.left}")
        assertTrue(search.bottom > 700.dp, "the dock is at the foot of the window: ${search.bottom}")
        // The pages beside it kept their room for it, so their last rows can be scrolled clear of it.
        assertTrue(first("Search settings").right <= AppLayout.listPaneWidth, "the list is still the list")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp")
    fun aTabletHeldUpright_keepsTheListAlone() {
        openSettings()
        tapRow("Finance")
        // One page at a time, as on a phone: the list has gone under the page.
        assertEquals(0, count("Search settings"))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w915dp-h412dp")
    fun aPhoneOnItsSide_keepsTheListAlone() {
        openSettings()
        tapRow("Finance")
        assertEquals(0, count("Search settings"))
    }

    @Test
    fun theRuleForTheListBesideThePage() {
        // Expanded and wider, and tall enough not to be a phone on its side.
        assertFalse(isListDetail(839, 800))
        assertTrue(isListDetail(840, 480))
        assertTrue(isListDetail(1280, 800))
        assertFalse(isListDetail(915, 412))
        assertFalse(isListDetail(411, 891))
    }

    @Test
    fun aDetailKeyComesBackAsTheSameDetail() {
        val all = listOf(SettingsDetail.Profile, SettingsDetail.Update) +
            SettingsPage.entries.map { SettingsDetail.Page(it) } +
            AppearanceSection.entries.map { SettingsDetail.Appearance(it) }
        for (detail in all) assertEquals(detail, SettingsDetail.fromKey(detail.key))
        assertNull(SettingsDetail.fromKey("page:NoSuchPage"))
        assertNull(SettingsDetail.fromKey("nonsense"))
    }

    @Test
    fun stepsKnowWhatTheyAreInside() {
        assertEquals(SettingsDetail.Page(SettingsPage.Appearance), SettingsDetail.Appearance(AppearanceSection.Colors).parent)
        assertEquals(SettingsDetail.Update, SettingsDetail.Page(SettingsPage.Updates).parent)
        assertNull(SettingsDetail.Page(SettingsPage.Finance).parent)
        assertNull(SettingsDetail.Profile.parent)
        assertNull(SettingsDetail.Update.parent)
        // An Appearance screen is the Appearance row's; Automatic updates and the update page are the Updates row's.
        assertEquals(SettingsPage.Appearance, SettingsDetail.Appearance(AppearanceSection.Colors).group)
        assertTrue(SettingsDetail.Update.isUpdates)
        assertTrue(SettingsDetail.Page(SettingsPage.Updates).isUpdates)
        assertFalse(SettingsDetail.Page(SettingsPage.Finance).isUpdates)
        assertEquals(360.dp, AppLayout.listPaneWidth)
    }
}
