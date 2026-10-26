package com.example.normpix.settings.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.settings.AppPreferences
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
 * Settings > Appearance > Navigation & controls > Footer labels: switch to toggle destination labels
 * on the bottom navigation dock / footer, on by default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class FooterLabelsSettingTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("footer_labels_setting")
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun itIsOnByDefault() {
        assertTrue(env.preferences.state.value.showFooterLabels)
    }

    @Test
    fun theControlsPageTogglesFooterLabelsOnAndOff() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.Controls) } }
        compose.waitForIdle()

        val switch = compose.onNodeWithText("Footer labels")
        switch.assertIsOn()

        switch.performClick()
        compose.waitForIdle()
        assertFalse(env.preferences.state.value.showFooterLabels)
        switch.assertIsOff()

        switch.performClick()
        compose.waitForIdle()
        assertTrue(env.preferences.state.value.showFooterLabels)
        switch.assertIsOn()
    }

    @Test
    fun theChoiceIsKept_andTravelsWithAnAppBackup() {
        env.preferences.setShowFooterLabels(false)

        val reopened = AppPreferences(env.prefsStore)
        assertFalse(reopened.state.value.showFooterLabels, "reopening prefs preserves false")
        assertNotNull(reopened.exportAll()["show_footer_labels"], "backup carries show_footer_labels")
    }

    @Test
    fun settingsCatalogFindsFooterLabelsUnderControls() {
        val entry = SettingsCatalog.search("footer labels").firstOrNull()
        assertNotNull(entry)
        assertEquals(AppearanceSection.Controls, entry.appearance)
    }
}
