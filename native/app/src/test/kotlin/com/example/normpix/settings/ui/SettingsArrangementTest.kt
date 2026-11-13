package com.example.normpix.settings.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.BackGestureSpeed
import com.example.normpix.settings.BackGestureStyle
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings as it is arranged since 2026-11-11 (owner: "a little bit confusing, can you rearrange them ... and where we need the
 * previews of what that setting is doing"): the main list in named groups, each page in exactly one and still the same page; the
 * row that undoes things last; and the motion settings showing what they do. Pictures in `app/build/qa_settings/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1500dp-xhdpi")
class SettingsArrangementTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "settings_arrangement")
    private val outDir = File("build/qa_settings").also { it.mkdirs() }

    @AfterTest
    fun tearDown() = env.close()

    private fun dump(name: String) {
        File(outDir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun everyPageIsInExactlyOneGroup_andTheListStillStartsWithAppearance() {
        val listed = SettingsListGroup.entries.flatMap { it.pages }
        assertEquals(listed.size, listed.toSet().size, "no page is listed twice")
        assertEquals(SettingsPage.entries.toSet() - SettingsPage.Updates, listed.toSet(), "every page but Automatic updates, which is behind Updates")
        assertEquals(listed, SettingsGroups)
        assertEquals(SettingsPage.Appearance, SettingsGroups.first(), "what a tablet opens beside the list")
        for (group in SettingsListGroup.entries) assertTrue(group.title.isNotBlank() && group.pages.isNotEmpty())
    }

    @Test
    fun theMainListIsGroupedUnderHeadings_andResetIsTheLastRow() {
        env.preferences.setMode(ThemeMode.Light)
        compose.setContent { env.Content { Surface(color = MaterialTheme.colorScheme.background) { SettingsScreen() } } }
        compose.waitForIdle()
        dump("settings_list")

        // The headings, top to bottom, each above the pages it holds.
        val headings = listOf("Look & feel", "Money", "Cards", "Alerts & safety", "System")
        for (heading in headings) compose.onNodeWithText(heading).assertIsDisplayed()
        assertEquals(headings, headings.sortedBy { top(it) })
        assertTrue(top("Look & feel") < top("Appearance") && top("Widgets") < top("Money"))
        assertTrue(top("Money") < top("Finance") && top("Finance") < top("Cards"))
        assertTrue(top("Cards") < top("Wallet & Cards") && top("Availability & Schedules") < top("Alerts & safety"))
        assertTrue(top("Alerts & safety") < top("Notifications") && top("Data & Backup") < top("System"))
        // The row that undoes things is the last of the page, under the ones that only show something.
        assertTrue(top("Reset settings") > top("About NormPix") && top("Reset settings") > top("Welcome tour"))
    }

    @Test
    fun aRowStillOpensItsOwnPage() {
        compose.setContent { env.Content { SettingsScreen() } }
        compose.waitForIdle()
        compose.onNodeWithText("Wallet & Cards").performClick()
        assertEquals(SettingsPage.Wallet, env.navigator.settingsPages.last())
        compose.onNodeWithText("Data & Backup").performClick()
        assertEquals(SettingsPage.Data, env.navigator.settingsPages.last())
    }

    @Test
    fun theSwipeDeleteQuestionIsAGroupOfItsOwn_notStuckToTheChoicesAboveIt() {
        env.preferences.setMode(ThemeMode.Light)
        compose.setContent { env.Content { Surface(color = MaterialTheme.colorScheme.background) { FinanceSettingsPage() } } }
        compose.waitForIdle()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Ask before a swipe deletes"))
        compose.waitForIdle()
        dump("finance_swipe")

        val pitch = top("Delete only") - top("Edit only") // one row to the next in the same group: rows 2 dp apart
        val gap = top("Ask before a swipe deletes") - top("Delete only")
        // The group under it starts a heading's room lower than the next row of its own group would: not flush against it (seen on the
        // phone, 2026-11-13).
        assertTrue(gap > pitch + 12f * compose.density.density, "the question is set apart: row pitch $pitch px, gap $gap px")
    }

    @Test
    fun theTransitionsPageShowsWhatPagesAndTheBackGestureDo_andHasTheBackGesturesChoices() {
        env.preferences.setMode(ThemeMode.Light)
        compose.setContent {
            env.Content { Surface(color = MaterialTheme.colorScheme.background) { AppearanceSectionPage(AppearanceSection.Transitions) } }
        }
        compose.waitForIdle()
        dump("transitions_page")

        compose.onNodeWithText("Back gesture").assertIsDisplayed()
        // By what each says it does: the names alone ("Slide", "Fade") are also names of page and sheet styles on this page.
        for (style in BackGestureStyle.entries) {
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(style.description))
            compose.onNodeWithText(style.description).assertIsDisplayed()
        }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(BackGestureStyle.Slide.description))
        compose.onNodeWithText(BackGestureStyle.Slide.description).performClick()
        assertEquals(BackGestureStyle.Slide, env.preferences.state.value.backGestureStyle)

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Back gesture speed"))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(BackGestureSpeed.Relaxed.label))
        compose.onNodeWithText(BackGestureSpeed.Relaxed.label).performClick()
        assertEquals(BackGestureSpeed.Relaxed, env.preferences.state.value.backGestureSpeed)
        compose.waitForIdle()
        dump("transitions_page_slide")
    }
}
