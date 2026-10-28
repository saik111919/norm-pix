package com.example.normpix.settings.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.navigation.ActionMenuLayout
import com.example.normpix.navigation.ActionTileSize
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
 * The "+" menu's arrangement (set in the menu's Edit mode) and its way back in Settings > Appearance > Navigation & controls >
 * Reset action menu: kept on the phone, carried by an app backup, and gone without a trace once reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class ActionMenuLayoutSettingTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("action_menu_layout_setting")
    }

    @After
    fun tearDown() = env.close()

    private val arranged = ActionMenuLayout.Default.moved("people", 1).resized("search", ActionTileSize.Wide)

    @Test
    fun itIsTheDesignByDefaultAndLeavesNothingBehind() {
        assertEquals("", env.preferences.state.value.actionMenuLayout)
        assertFalse("action_menu_layout" in env.preferences.exportAll().keys)
    }

    @Test
    fun anArrangementIsKept_readBackTheSame_andTravelsWithAnAppBackup() {
        env.preferences.setActionMenuLayout(ActionMenuLayout.keptAs(arranged))

        val reopened = AppPreferences(env.prefsStore)
        assertEquals(arranged, ActionMenuLayout.from(reopened.state.value.actionMenuLayout))
        assertNotNull(reopened.exportAll()["action_menu_layout"], "backup carries action_menu_layout")
    }

    @Test
    fun resetIsOffOnTheDesign_andOnceAnArrangementIsMadeItPutsTheDesignBack() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.Controls) } }
        compose.waitForIdle()

        compose.onNodeWithText("Reset action menu").assertIsNotEnabled()

        env.preferences.setActionMenuLayout(ActionMenuLayout.keptAs(arranged))
        compose.waitForIdle()
        compose.onNodeWithText("Reset action menu").assertIsEnabled()

        compose.onNodeWithText("Reset action menu").performClick()
        compose.waitForIdle()
        assertEquals("", env.preferences.state.value.actionMenuLayout)
        assertFalse("action_menu_layout" in env.preferences.exportAll().keys, "a reset leaves no key")
        compose.onNodeWithText("Reset action menu").assertIsNotEnabled()
    }

    @Test
    fun settingsSearchFindsItUnderControls_byTheWordsPeopleUseForIt() {
        for (query in listOf("action menu", "reorder", "resize", "arrange", "plus menu")) {
            val entry = SettingsCatalog.search(query).firstOrNull { it.title == "Reset action menu" }
            assertNotNull(entry, query)
            assertEquals(AppearanceSection.Controls, entry.appearance, query)
        }
        assertTrue(SettingsCatalog.search("action menu").size >= 1)
    }
}
