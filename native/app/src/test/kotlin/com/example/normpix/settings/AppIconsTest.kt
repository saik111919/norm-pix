package com.example.normpix.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.MainActivity
import com.example.normpix.R
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.ui.SettingsCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The launcher icons the user can pick: each is a launcher entry that opens the main screen with an icon of its own, one
 * of them is in the launcher at a time, and Android keeps the choice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppIconsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packages: PackageManager get() = context.packageManager

    /** What a launcher lists for the app. */
    private fun launcherEntries(): List<String> = packages
        .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
        .map { it.activityInfo.name }

    @Test
    fun everyIconIsAnEntryThatOpensTheMainScreen_withAnIconOfItsOwn() {
        val icons = AppIcon.entries.map { icon ->
            val info = packages.getActivityInfo(AppIcons.component(context, icon), PackageManager.MATCH_DISABLED_COMPONENTS)
            assertEquals(MainActivity::class.java.name, info.targetActivity, icon.name)
            assertNotNull(ContextCompat.getDrawable(context, info.icon), icon.name)
            info.icon
        }
        assertEquals(AppIcon.entries.size, icons.toSet().size, "no two share an icon")
    }

    @Test
    fun toStartWithTheDefaultIsTheOnlyLauncherEntry() {
        // MainActivity itself isn't one: shares and notifications open it whichever icon is on.
        assertEquals(listOf(AppIcon.Default.alias), launcherEntries())
        assertEquals(AppIcon.Default, AppIcons.current(context))
    }

    @Test
    fun choosingAnIconPutsItInTheLauncherInPlaceOfTheOthers() {
        AppIcons.use(context, AppIcon.Comic)
        assertEquals(AppIcon.Comic, AppIcons.current(context))
        assertEquals(listOf(AppIcon.Comic.alias), launcherEntries())

        AppIcons.use(context, AppIcon.Neon)
        assertEquals(AppIcon.Neon, AppIcons.current(context))
        assertEquals(listOf(AppIcon.Neon.alias), launcherEntries())
    }

    @Test
    fun goingBackToTheDefaultLeavesNothingOverridden() {
        AppIcons.use(context, AppIcon.Pixel)
        AppIcons.use(context, AppIcon.Default)

        assertEquals(AppIcon.Default, AppIcons.current(context))
        assertEquals(listOf(AppIcon.Default.alias), launcherEntries())
        for (icon in AppIcon.entries) {
            val state = packages.getComponentEnabledSetting(AppIcons.component(context, icon))
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, state, "${icon.name} is back to what the manifest says")
        }
    }

    @Test
    fun everyIconsLayersLoad_forShowingItInTheApp() {
        for (icon in AppIcon.entries) {
            for (layer in listOf(icon.background, icon.foreground)) {
                val drawable = assertNotNull(ContextCompat.getDrawable(context, layer), icon.name)
                assertTrue(drawable.intrinsicWidth > 0 && drawable.intrinsicWidth == drawable.intrinsicHeight, icon.name)
            }
        }
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome), "the themed-icon layer they share")
    }

    @Test
    fun settingsSearchFindsItInAppearance() {
        for (query in listOf("app icon", "launcher", "home screen", "logo", "comic", "neon")) {
            val hit = SettingsCatalog.search(query).single { it.title == "App icon" }
            assertEquals(SettingsPage.Appearance, hit.group, query)
        }
    }
}
