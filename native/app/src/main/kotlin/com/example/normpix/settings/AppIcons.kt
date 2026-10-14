package com.example.normpix.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.example.normpix.MainActivity
import com.example.normpix.R

/**
 * The launcher icons the user can pick, all the same stack of cards drawn in different styles. Each is an
 * `<activity-alias>` of [MainActivity] in the manifest named `.Icon<name>`, with its own adaptive icon
 * (`mipmap-anydpi/ic_launcher_<name>`); [background] and [foreground] are its two layers, for showing it in the app.
 */
enum class AppIcon(val label: String, @DrawableRes val background: Int, @DrawableRes val foreground: Int) {
    Default("Default", R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground),
    Glossy("Glossy", R.drawable.ic_launcher_glossy_background, R.drawable.ic_launcher_glossy_foreground),
    Comic("Comic", R.drawable.ic_launcher_comic_background, R.drawable.ic_launcher_comic_foreground),
    Minimal("Minimal", R.drawable.ic_launcher_minimal_background, R.drawable.ic_launcher_minimal_foreground),
    Flat("Flat", R.drawable.ic_launcher_flat_background, R.drawable.ic_launcher_flat_foreground),
    Glass("Glass", R.drawable.ic_launcher_glass_background, R.drawable.ic_launcher_glass_foreground),
    Clay("Clay", R.drawable.ic_launcher_clay_background, R.drawable.ic_launcher_clay_foreground),
    Pixel("Pixel", R.drawable.ic_launcher_pixel_background, R.drawable.ic_launcher_pixel_foreground),
    Paper("Paper", R.drawable.ic_launcher_paper_background, R.drawable.ic_launcher_paper_foreground),
    Neon("Neon", R.drawable.ic_launcher_neon_background, R.drawable.ic_launcher_neon_foreground),
    ;

    /** The alias's class name: the manifest's `.Icon<name>` in the app's namespace (not the qa build's package). */
    val alias: String get() = MainActivity::class.java.name.substringBeforeLast('.') + ".Icon" + name
}

/**
 * Which launcher icon is on, and switching it. Android keeps the choice (the aliases' enabled states, which survive
 * app updates), so nothing is stored by the app.
 */
object AppIcons {
    fun current(context: Context): AppIcon {
        val packages = context.packageManager
        return AppIcon.entries.firstOrNull { icon ->
            when (packages.getComponentEnabledSetting(component(context, icon))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.Default
                else -> false
            }
        } ?: AppIcon.Default
    }

    /**
     * Puts [icon] in the launcher in place of the others. The new entry is turned on before the old one is turned off,
     * so the app is never without one. Android closes a task that was opened from the old entry, and launchers drop
     * home-screen shortcuts to it.
     */
    fun use(context: Context, icon: AppIcon) {
        val packages = context.packageManager
        for (each in listOf(icon) + AppIcon.entries.filter { it != icon }) {
            val state = stateFor(each, on = each == icon)
            val component = component(context, each)
            if (packages.getComponentEnabledSetting(component) != state) {
                packages.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }
        }
    }

    fun component(context: Context, icon: AppIcon) = ComponentName(context.packageName, icon.alias)

    // The manifest's own state wherever it already says the right thing, so going back to Default leaves no overrides.
    private fun stateFor(icon: AppIcon, on: Boolean): Int = when {
        on == (icon == AppIcon.Default) -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        on -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
}
