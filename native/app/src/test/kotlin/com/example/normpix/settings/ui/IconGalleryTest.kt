package com.example.normpix.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import kotlin.test.assertTrue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.label
import com.example.normpix.reminders.ReminderCategory
import com.example.normpix.settings.AppIcon
import com.example.normpix.testing.TestEnvironment
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The icon gallery shows the icons the screens themselves draw from, in groups, under three tabs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class IconGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment("icon_gallery")
        compose.setContent { env.Content { IconGallerySheet() } }
        compose.waitForIdle()
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun theInterfaceIconsAreGroupedByWhatTheyStandFor_andNameEveryEntryOfTheEnumTheScreensUse() {
        for (group in listOf("Transactions", "Repeats", "Reminders", "Reminder status", "Budget status")) {
            compose.onNodeWithText(group).assertIsDisplayed()
        }
        for (type in TransactionType.entries) assertTrue(compose.onAllNodesWithContentDescription(type.label).fetchSemanticsNodes().isNotEmpty(), type.label)
        for (category in ReminderCategory.entries) assertTrue(compose.onAllNodesWithContentDescription(category.label).fetchSemanticsNodes().isNotEmpty(), category.label)
    }

    @Test
    fun theCategoryAndBrandIconsAreOneTabAway() {
        compose.onNodeWithText("Categories").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tap an icon to see its name").assertIsDisplayed()
        // The same picker a category's form has: the app's own icons, brands and emoji.
        compose.onNodeWithText("Brands").assertIsDisplayed()
        compose.onNodeWithText("Emoji").assertIsDisplayed()
    }

    @Test
    fun theHomeScreenIconsAreAllThere() {
        compose.onNodeWithText("App icons").performClick()
        compose.waitForIdle()
        for (icon in AppIcon.entries) compose.onNodeWithContentDescription("${icon.label} app icon").assertExists()
    }
}
