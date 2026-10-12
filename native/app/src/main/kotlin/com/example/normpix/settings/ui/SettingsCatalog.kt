package com.example.normpix.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import kotlinx.coroutines.launch

/**
 * A searchable setting item in the settings catalog.
 */
@Immutable
data class SettingsEntry(
    val title: String,
    val page: SettingsPage?,
    val keywords: List<String>,
    val special: SettingsSpecial? = null,
) {
    val group: SettingsPage? get() = page
    val location: String get() = page?.name ?: "Settings"
    val icon: ImageVector get() = if (special != null) SettingsCatalog.iconFor(special) else SettingsCatalog.iconFor(page)
}

enum class SettingsSpecial { Profile, Categories, People, Reset, About, DemoData, DummyData }

object SettingsCatalog {
    val entries: List<SettingsEntry> = listOf(
        // Profile
        SettingsEntry("Profile", null, listOf("profile", "name", "picture", "photo", "image", "gallery", "avatar", "emoji", "initial", "about me"), special = SettingsSpecial.Profile),
        SettingsEntry("Account button", null, listOf("account button", "account", "circle", "header", "profile"), special = SettingsSpecial.Profile),

        // Appearance
        SettingsEntry("Theme", SettingsPage.Appearance, listOf("dark", "light", "system", "theme", "mode")),
        SettingsEntry("Color source & palette", SettingsPage.Appearance, listOf("color", "dynamic", "material", "wallpaper", "monochrome")),
        SettingsEntry("Accent color", SettingsPage.Appearance, listOf("accent", "emerald", "ocean", "amber", "rose", "violet", "tint")),
        SettingsEntry("Progress indicators", SettingsPage.Appearance, listOf("progress", "loading", "wavy", "flat", "bar", "spinner", "loader", "indicator", "expressive")),
        SettingsEntry("Background blur", SettingsPage.Appearance, listOf("sheet", "dialog", "blur", "frosted", "glass", "background", "overlay", "dock", "footer")),
        SettingsEntry("Category icons", SettingsPage.Appearance, listOf("category", "icons", "emoji", "outline", "outlined", "glyph", "avatar", "style")),
        SettingsEntry("Back button style", SettingsPage.Appearance, listOf("back", "button", "floating", "circle", "header", "dock", "top bar")),
        SettingsEntry("Page transitions", SettingsPage.Appearance, listOf("animation", "motion", "shared axis", "slide", "fade")),

        // Finance
        SettingsEntry("Categories", null, listOf("categories", "labels", "tags"), special = SettingsSpecial.Categories),
        SettingsEntry("People", null, listOf("people", "person", "split", "spending by person", "family", "friends", "who"), special = SettingsSpecial.People),
        SettingsEntry("Default account", SettingsPage.Finance, listOf("default account", "preferred account")),
        SettingsEntry("Swipe actions", SettingsPage.Finance, listOf("swipe", "edit", "delete", "gesture", "list")),
        SettingsEntry("Financial year start", SettingsPage.Finance, listOf("financial year", "fiscal", "april", "january")),

        // Data & Testing
        SettingsEntry("Sample data", null, listOf("sample data", "dummy data", "test", "mock", "demo"), special = SettingsSpecial.DummyData),
        SettingsEntry("Export backup", SettingsPage.Data, listOf("backup", "export", "json", "save")),
        SettingsEntry("Import backup", SettingsPage.Data, listOf("restore", "import", "load", "recover")),

        // Wallet
        SettingsEntry("Card actions style", SettingsPage.Wallet, listOf("buttons", "menu", "context menu", "actions")),
        SettingsEntry("Accounts layout", SettingsPage.Wallet, listOf("accounts", "stack", "stacked", "cards", "list", "rows", "compact", "layout", "wallet")),
        SettingsEntry("Bank logo style", SettingsPage.Wallet, listOf("logo", "brand", "icon", "compact", "full")),
        SettingsEntry("NFC tag reveal gate", SettingsPage.Wallet, listOf("nfc", "tap to reveal", "card number", "security", "registered tag", "only my tag", "my tag", "register tag")),
        SettingsEntry("Card tilt", SettingsPage.Wallet, listOf("tilt", "motion", "gyroscope", "sensor", "parallax", "animation")),
        SettingsEntry("Clear copied card details after", SettingsPage.Wallet, listOf("clipboard", "copy", "clear", "timeout", "seconds", "security", "cvv")),

        // Availability
        SettingsEntry("NFC card exchange schedule", SettingsPage.Availability, listOf("nfc", "schedule", "hours", "time window", "availability", "when")),

        // Security
        SettingsEntry("Biometric lock", SettingsPage.Security, listOf("fingerprint", "face", "biometric", "pin", "gate")),
        SettingsEntry("Lock scope", SettingsPage.Security, listOf("wallet only", "entire app", "scope")),
        SettingsEntry("Auto-lock timeout", SettingsPage.Security, listOf("immediate", "timeout", "delay", "grace period")),

        // System
        SettingsEntry("Reset all settings", null, listOf("defaults", "reset", "clear preferences"), special = SettingsSpecial.Reset),
        SettingsEntry("About NormPix", null, listOf("licenses", "version", "privacy"), special = SettingsSpecial.About),
    )

    fun search(query: String): List<SettingsEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return entries.filter { entry ->
            entry.title.lowercase().contains(q) ||
                entry.keywords.any { it.contains(q) } ||
                (entry.page?.name?.lowercase()?.contains(q) == true)
        }
    }

    fun iconFor(page: SettingsPage?): ImageVector = when (page) {
        SettingsPage.Appearance -> Icons.Outlined.Palette
        SettingsPage.Finance -> Icons.Outlined.Payments
        SettingsPage.Data -> Icons.Outlined.Storage
        SettingsPage.Wallet -> Icons.Outlined.CreditCard
        SettingsPage.Availability -> Icons.Outlined.Schedule
        SettingsPage.Security -> Icons.Outlined.Security
        null -> Icons.Outlined.Tune
    }

    fun iconFor(special: SettingsSpecial?): ImageVector = when (special) {
        SettingsSpecial.Profile -> Icons.Outlined.AccountCircle
        SettingsSpecial.Categories -> Icons.Outlined.Payments
        SettingsSpecial.People -> Icons.Outlined.Groups
        SettingsSpecial.DummyData, SettingsSpecial.DemoData -> Icons.Outlined.Storage
        SettingsSpecial.Reset -> Icons.Outlined.DeleteOutline
        SettingsSpecial.About -> Icons.Outlined.Info
        null -> Icons.Outlined.Tune
    }
}

class SettingsActivator(val activate: (SettingsEntry) -> Unit)

/**
 * Activates a selected settings catalog entry.
 */
@Composable
fun rememberSettingsActivator(): SettingsActivator {
    val navigator = LocalNavigator.current
    val system = rememberSettingsSystemActions()

    return remember(navigator, system) {
        SettingsActivator { entry ->
            when {
                entry.page != null -> navigator.openSettings(entry.page)
                entry.special == SettingsSpecial.Profile -> navigator.openProfile()
                entry.special == SettingsSpecial.Categories -> navigator.openCategories()
                entry.special == SettingsSpecial.People -> navigator.openPeople()
                entry.special == SettingsSpecial.DummyData -> navigator.openSettings()
                entry.special == SettingsSpecial.Reset -> system.resetSettings()
                entry.special == SettingsSpecial.About -> system.showAbout()
                else -> navigator.openSettings()
            }
        }
    }
}

/** \"Reset settings\" and \"About\", shared by the Settings page and search. */
class SettingsSystemActions(
    val resetSettings: () -> Unit,
    val showAbout: () -> Unit,
)

@Composable
fun rememberSettingsSystemActions(): SettingsSystemActions {
    val container = LocalAppContainer.current
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val scope = LocalAppScope.current

    fun resetSettings() {
        scope.launch {
            val confirmed = overlays.confirm(
                title = "Reset all settings?",
                message = "Your preferences will return to defaults. Your finance data, cards and reminders are not touched.",
                confirmLabel = "Reset",
            )
            if (!confirmed) return@launch
            container.preferences.resetToDefaults()
            snackbars.show("Settings reset to defaults")
        }
    }

    fun showAbout() {
        scope.launch {
            overlays.alert(
                title = "About NormPix",
                message = "NormPix v1.0.0\nModern finance, cards & reminders on Android.\nOffline-first, encrypted, on-device.",
                okLabel = "OK",
            )
        }
    }

    return remember(container, overlays, snackbars, scope) {
        SettingsSystemActions(::resetSettings, ::showAbout)
    }
}
