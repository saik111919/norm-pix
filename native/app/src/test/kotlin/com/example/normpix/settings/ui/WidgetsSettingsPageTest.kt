package com.example.normpix.settings.ui

import android.appwidget.AppWidgetManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.widgets.HomeWidget
import com.example.normpix.widgets.HomeWidgetGroup
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Settings > Widgets: a pill for every widget, the chosen one previewed above them, and a button that asks the launcher to add
 * it to the home screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class WidgetsSettingsPageTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("widgets_settings_page")
    }

    @After
    fun tearDown() = env.close()

    private fun open(launcherCanPin: Boolean = true) {
        shadowOf(AppWidgetManager.getInstance(env.context)).setRequestPinAppWidgetSupported(launcherCanPin)
        compose.setContent { env.Content { WidgetsSettingsPage() } }
        compose.waitForIdle()
    }

    private fun previewOf(widget: HomeWidget) = compose.onNodeWithContentDescription("Preview of the ${widget.title} widget")

    @Test
    fun everyWidgetHasAPill_underItsGroup() {
        open()
        for (group in HomeWidgetGroup.entries) compose.onNodeWithText(group.title).assertExists()
        for (widget in HomeWidget.entries) compose.onNodeWithText(widget.pill).assertExists()
    }

    @Test
    fun theCookieAnalogClockIsUpFirst_andItsPillIsTheSelectedOne() {
        open()
        compose.onNodeWithText(HomeWidget.CookieAnalog.pill).assertIsSelected()
        for (widget in HomeWidget.entries - HomeWidget.CookieAnalog) compose.onNodeWithText(widget.pill).assertIsNotSelected()
        previewOf(HomeWidget.CookieAnalog).assertExists()
        compose.onNodeWithText(HomeWidget.CookieAnalog.title).assertExists()
        compose.onNodeWithText(HomeWidget.CookieAnalog.description).assertExists()
    }

    @Test
    fun aPillIsAnnouncedAsOneChoiceOfSeveral() {
        open()
        for (widget in HomeWidget.entries) {
            compose.onNodeWithText(widget.pill).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
    }

    @Test
    fun aPillPreviewsItsWidget_andSaysWhatItIs() {
        open()
        for (widget in HomeWidget.entries) {
            compose.onNodeWithText(widget.pill).performClick()
            compose.waitForIdle()
            compose.onNodeWithText(widget.pill).assertIsSelected()
            previewOf(widget).assertExists()
            compose.onNodeWithText(widget.title).assertExists()
            compose.onNodeWithText(widget.description).assertExists()
            // Only the chosen widget is up, never two at once.
            for (other in HomeWidget.entries - widget) compose.onAllNodesWithText(other.title).assertCountEquals(0)
        }
    }

    @Test
    fun theButtonAsksTheLauncher_whichSaysSoOnlyWhenItCannot() {
        open(launcherCanPin = true)
        compose.onNodeWithText("Add to home screen").performScrollTo().performClick()
        compose.waitForIdle()
        // Taken: the launcher shows its own dialog, so there is nothing to say here.
        compose.onAllNodesWithText("Your launcher didn't take the request", substring = true).assertCountEquals(0)
    }

    @Test
    fun aLauncherThatCannotBeAsked_getsTheWayToTheWidgetPickerInWords() {
        open(launcherCanPin = false)
        compose.onAllNodesWithText("Add to home screen").assertCountEquals(0)
        compose.onNodeWithText("Your launcher can't add it from here").assertExists()
        compose.onNodeWithText("Press and hold the home screen, choose Widgets and find NormPix.").assertExists()
    }

    @Test
    fun theWatchDialAsksForExactAlarms_onlyWhileItIsWithoutThem() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        open()
        compose.onAllNodesWithText("Let the rings step on the minute").assertCountEquals(0)
        compose.onNodeWithText(HomeWidget.WatchDial.pill).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Let the rings step on the minute").assertExists()
        // The other widgets don't need it.
        compose.onNodeWithText(HomeWidget.CookieDigital.pill).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Let the rings step on the minute").assertCountEquals(0)
    }

    @Test
    fun theWatchDialSaysNothingOfExactAlarms_whenItHasThem() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        open()
        compose.onNodeWithText(HomeWidget.WatchDial.pill).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Let the rings step on the minute").assertCountEquals(0)
    }

    @Test
    fun settingsListsWidgets_andTheRowOpensThePage() {
        compose.setContent { env.Content { SettingsScreen() } }
        compose.waitForIdle()
        compose.onNodeWithText("Widgets").performScrollTo().performClick()
        assertEquals(listOf<SettingsPage?>(SettingsPage.Widgets), env.navigator.settingsPages)
    }

    @Test
    fun settingsSearchFindsTheWidgets() {
        for (query in listOf("widget", "cookie", "clock", "add to home screen", "analog", "digital")) {
            val found = SettingsCatalog.search(query).filter { it.page == SettingsPage.Widgets }
            assertTrue(found.isNotEmpty(), "\"$query\" finds the Widgets page")
            assertEquals("Widgets", found.first().location)
        }
        // The Widgets page doesn't take over the search for a setting that was already found.
        assertTrue(SettingsCatalog.search("dark").none { it.page == SettingsPage.Widgets })
    }
}
