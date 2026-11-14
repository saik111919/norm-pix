package com.example.normpix.settings.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.availability.ScheduledFeature
import com.example.normpix.availability.ui.emoji
import com.example.normpix.finance.ui.Fluent3d
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.CategoryIconStyle
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
 * Settings' own icons follow the 3D icon style (owner, 2026-11-01: "if icons are set as 3D, here we need to follow that; if the
 * user doesn't want it he can turn it off for this only"): on by default, only with the 3D style, and a switch of its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class SettingsIcons3dTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("settings_icons_3d")
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun `every row of the Settings list has a 3D picture`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val emoji = SettingsPage.entries.map { it.emoji } +
            listOf(SettingsRowEmoji.RESET, SettingsRowEmoji.UPDATES, SettingsRowEmoji.WELCOME_TOUR, SettingsRowEmoji.ABOUT)
        for (e in emoji) assertNotNull(Fluent3d.path(context, e), "a bundled 3D picture of $e")
    }

    /** Every string constant of [SettingsRowEmoji], the pictures of the rows of Settings and of the pages under it. */
    private fun registryEmoji(): List<String> = SettingsRowEmoji::class.java.declaredFields
        .filter { it.type == String::class.java && java.lang.reflect.Modifier.isStatic(it.modifiers) }
        .map { field -> field.isAccessible = true; field.get(null) as String }

    @Test
    fun `every emoji on the pages under Settings has a 3D picture`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val registry = registryEmoji()
        assertTrue(registry.size >= 40, "the registry was read (${registry.size} emoji)")
        val others = AppearanceSection.entries.map { it.emoji } + ScheduledFeature.entries.map { it.emoji } +
            SettingsCatalog.entries.mapNotNull { it.emoji }
        assertTrue(others.isNotEmpty())
        for (e in registry + others) assertNotNull(Fluent3d.path(context, e), "a bundled 3D picture of $e (${Fluent3d.fileName(e)}.webp)")
    }

    @Test
    fun `the rows of the pages under Settings take their emoji from the registry, not a literal`() {
        // A literal at a call site would escape the test above, and a missing picture would quietly leave the row flat.
        val root = java.io.File("src/main/kotlin/com/example/normpix")
        val literal = Regex("""Settings(?:LeadingIcon|LeadingBadge)\([^)\n]*"|\bemoji\s*=\s*"""")
        val offenders = listOf("settings/ui", "about/ui", "updates/ui", "availability/ui")
            .flatMap { dir -> java.io.File(root, dir).walkTopDown().filter { it.extension == "kt" }.toList() }
            .filter { it.name != "SettingsRows.kt" }
            .filter { literal.containsMatchIn(it.readText()) }
            .map { it.name }
        assertTrue(offenders.isEmpty(), "use a SettingsRowEmoji constant in: $offenders")
    }

    @Test
    fun `a bare leading icon is a larger picture in 3D and exactly the flat icon otherwise`() {
        compose.setContent {
            env.Content {
                Column {
                    SettingsLeadingIcon(Icons.Rounded.Fingerprint, SettingsRowEmoji.LOCK_AND_KEY, Modifier.testTag("bare"))
                    SettingsLeadingBadge(Icons.Rounded.Fingerprint, SettingsRowEmoji.LOCK_AND_KEY, Modifier.testTag("badge"))
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("bare").assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
        compose.onNodeWithTag("badge").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)

        env.preferences.setSettingsIcons3d(false)
        compose.waitForIdle()
        compose.onNodeWithTag("bare").assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
        compose.onNodeWithTag("badge").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)

        env.preferences.setSettingsIcons3d(true)
        env.preferences.setCategoryIconStyle(CategoryIconStyle.entries.first { it != CategoryIconStyle.ThreeD })
        compose.waitForIdle()
        compose.onNodeWithTag("bare").assertWidthIsEqualTo(24.dp)
    }

    @Test
    fun `a search result is pictured by what it leads to`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // A search result is pictured by what it leads to: its own emoji, its Appearance screen's, else its page's.
        val result = SettingsCatalog.entries.first { it.title == "Reset all settings" }
        assertEquals(SettingsRowEmoji.RESET, result.emoji)
        val colors = SettingsCatalog.entries.first { it.appearance == AppearanceSection.Colors }
        assertEquals(AppearanceSection.Colors.emoji, colors.emoji)
        assertEquals(SettingsPage.Security.emoji, SettingsCatalog.entries.first { it.page == SettingsPage.Security && it.appearance == null }.emoji)
        assertNotNull(Fluent3d.path(context, result.emoji!!))
    }

    @Test
    fun `it is on by default and kept`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = context.getSharedPreferences("settings_icons_3d_store", Context.MODE_PRIVATE)
        assertTrue(AppPreferences(store).state.value.settingsIcons3d)
        AppPreferences(store).setSettingsIcons3d(false)
        assertFalse(AppPreferences(store).state.value.settingsIcons3d, "read back by the next launch")
        assertFalse(store.getBoolean("settings_icons_3d", true), "under its own key")
    }

    @Test
    fun `Settings is 3D only with the 3D style and the switch on`() {
        var threeD: Boolean? = null
        compose.setContent { env.Content { threeD = LocalSettingsIcons3d.current } }
        compose.waitForIdle()
        assertEquals(true, threeD, "3D style (the default) and the switch on")
        env.preferences.setSettingsIcons3d(false)
        compose.waitForIdle()
        assertEquals(false, threeD, "turned off for Settings alone")
        env.preferences.setSettingsIcons3d(true)
        env.preferences.setCategoryIconStyle(CategoryIconStyle.entries.first { it != CategoryIconStyle.ThreeD })
        compose.waitForIdle()
        assertEquals(false, threeD, "another style is flat in Settings too")
    }

    @Test
    fun `the switch is on the Icons page and only offered with the 3D style`() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.CategoryIcons) } }
        compose.waitForIdle()
        val switch = compose.onNodeWithText("3D icons in Settings").performScrollTo()
        switch.assertIsOn().assertIsEnabled()
        switch.performClick()
        compose.waitForIdle()
        assertFalse(env.preferences.state.value.settingsIcons3d)
        compose.onNodeWithText("3D icons in Settings").assertIsOff()

        env.preferences.setSettingsIcons3d(true)
        env.preferences.setCategoryIconStyle(CategoryIconStyle.entries.first { it != CategoryIconStyle.ThreeD })
        compose.waitForIdle()
        compose.onNodeWithText("3D icons in Settings").performScrollTo().assertIsNotEnabled().assertIsOff()
        compose.onNodeWithText("Only with the 3D style").assertExists()
    }
}
