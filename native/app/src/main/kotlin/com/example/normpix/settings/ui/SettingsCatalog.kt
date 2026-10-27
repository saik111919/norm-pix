package com.example.normpix.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.settings.CustomFont
import com.example.normpix.ui.theme.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A searchable setting item in the settings catalog.
 */
@Immutable
data class SettingsEntry(
    val title: String,
    val page: SettingsPage?,
    val keywords: List<String>,
    val special: SettingsSpecial? = null,
    /** For an Appearance entry: the screen of Appearance it is on (null: Appearance itself, where the theme is). */
    val appearance: AppearanceSection? = null,
    /** About saved cards alone: not offered while NormPix isn't storing cards (Settings > Wallet & Cards > Store cards). */
    val cardsOnly: Boolean = false,
) {
    val group: SettingsPage? get() = page
    val location: String
        get() = if (appearance != null) "${page?.name} > ${appearance.title}" else page?.name ?: "Settings"
    val icon: ImageVector get() = if (special != null) SettingsCatalog.iconFor(special) else SettingsCatalog.iconFor(page)
}

enum class SettingsSpecial { Profile, Categories, People, Reset, Updates, About }

object SettingsCatalog {
    val entries: List<SettingsEntry> = listOf(
        // Profile
        SettingsEntry("Profile", null, listOf("profile", "name", "picture", "photo", "image", "gallery", "avatar", "emoji", "initial", "about me"), special = SettingsSpecial.Profile),
        SettingsEntry("Account button", null, listOf("account button", "account", "circle", "header", "profile"), special = SettingsSpecial.Profile),

        // Appearance
        SettingsEntry("Theme", SettingsPage.Appearance, listOf("dark", "light", "system", "theme", "mode")),
        SettingsEntry("Color source & palette", SettingsPage.Appearance, listOf("color", "dynamic", "material", "wallpaper", "monochrome"), appearance = AppearanceSection.Colors),
        SettingsEntry("Accent color", SettingsPage.Appearance, listOf("accent", "emerald", "ocean", "amber", "rose", "violet", "tint", "volt", "other colors", "preview", "palette"), appearance = AppearanceSection.Colors),
        SettingsEntry("Pure black and white", SettingsPage.Appearance, listOf("pure", "black", "white", "amoled", "oled", "true black", "monochrome", "greyscale", "grayscale", "high contrast"), appearance = AppearanceSection.Colors),
        SettingsEntry("App icon", SettingsPage.Appearance, listOf("app icon", "launcher", "home screen", "logo", "glossy", "comic", "minimal", "flat", "glass", "clay", "pixel", "paper", "neon"), appearance = AppearanceSection.AppIcon),
        SettingsEntry("Progress indicators", SettingsPage.Appearance, listOf("progress", "loading", "wavy", "flat", "bar", "spinner", "loader", "indicator", "expressive"), appearance = AppearanceSection.ProgressIndicators),
        SettingsEntry("Background blur", SettingsPage.Appearance, listOf("sheet", "dialog", "blur", "frosted", "glass", "background", "overlay", "dock", "footer"), appearance = AppearanceSection.Effects),
        SettingsEntry("Category icons", SettingsPage.Appearance, listOf("category", "icons", "emoji", "outline", "outlined", "glyph", "avatar", "style"), appearance = AppearanceSection.CategoryIcons),
        SettingsEntry("Icon background", SettingsPage.Appearance, listOf("icon background", "shape", "radius", "circle", "rounded", "square", "avatar", "corners", "disc", "tile", "category", "logo", "round", "bank", "account"), appearance = AppearanceSection.CategoryIcons),
        SettingsEntry("Interface font", SettingsPage.Appearance, listOf("font", "typeface", "text", "gs flex", "google sans", "weight", "width", "roundness", "zenith", "neo", "impact", "airy"), appearance = AppearanceSection.InterfaceFont),
        SettingsEntry("Your font", SettingsPage.Appearance, listOf("custom font", "upload font", "ttf", "otf", "font file", "import font", "own font", "my font", "typeface", "text"), appearance = AppearanceSection.InterfaceFont),
        SettingsEntry("Back button style", SettingsPage.Appearance, listOf("back", "button", "floating", "circle", "header", "dock", "top bar"), appearance = AppearanceSection.Controls),
        SettingsEntry("Bottom controls", SettingsPage.Appearance, listOf("bottom", "thumb", "reach", "one hand", "search bar", "filter", "filters", "back", "actions", "footer", "controls", "layout"), appearance = AppearanceSection.Controls),
        SettingsEntry("Footer labels", SettingsPage.Appearance, listOf("footer", "labels", "dock", "bottom bar", "navigation", "tabs", "icons", "show labels", "hide labels"), appearance = AppearanceSection.Controls),
        SettingsEntry("Page transitions", SettingsPage.Appearance, listOf("animation", "motion", "shared axis", "slide", "fade"), appearance = AppearanceSection.Transitions),
        SettingsEntry("Sheet & dialog transitions", SettingsPage.Appearance, listOf("sheet", "dialog", "bottom sheet", "decision", "popup", "overlay", "animation", "motion", "transition", "window", "linux", "desktop", "gnome", "slide", "fade", "scale"), appearance = AppearanceSection.Transitions),
        SettingsEntry("Top spending", SettingsPage.Appearance, listOf("top spending", "categories", "carousel", "stack", "home", "biggest", "spending cards", "layout"), appearance = AppearanceSection.Layouts),
        SettingsEntry("Month control", SettingsPage.Appearance, listOf("month", "switcher", "picker", "steps", "track", "split", "buttons", "classic", "earlier", "previous", "old", "date", "dates", "all time", "by month", "style"), appearance = AppearanceSection.Controls),
        SettingsEntry("Accounts layout", SettingsPage.Appearance, listOf("accounts", "stack", "stacked", "cards", "list", "rows", "compact", "carousel", "swipe", "layout", "wallet"), appearance = AppearanceSection.Layouts),
        SettingsEntry("Cards layout", SettingsPage.Appearance, listOf("cards", "stack", "stacked", "list", "rows", "compact", "carousel", "swipe", "layout", "wallet"), appearance = AppearanceSection.Layouts, cardsOnly = true),
        SettingsEntry("Bank logo style", SettingsPage.Appearance, listOf("logo", "brand", "icon", "compact", "full", "bank", "wallet"), appearance = AppearanceSection.CategoryIcons),
        SettingsEntry("Card tilt", SettingsPage.Appearance, listOf("tilt", "motion", "gyroscope", "sensor", "parallax", "animation", "accounts", "cards"), appearance = AppearanceSection.Effects),
        SettingsEntry("Footer blur", SettingsPage.Appearance, listOf("footer", "blur", "dock", "bottom bar", "fade", "frosted", "glass", "navigation", "progressive"), appearance = AppearanceSection.Effects),

        // Widgets
        SettingsEntry("Home-screen widgets", SettingsPage.Widgets, listOf("widget", "widgets", "home screen", "launcher", "preview", "pill", "pills", "clock", "recent", "quick add")),
        SettingsEntry("Add a widget to the home screen", SettingsPage.Widgets, listOf("add widget", "add to home screen", "put on home screen", "install widget")),
        SettingsEntry("Cookie clock widgets", SettingsPage.Widgets, listOf("cookie", "clock", "analog", "analogue", "digital", "numerals", "watch dial")),

        // Banks
        SettingsEntry("Banks and their logos", SettingsPage.Banks, listOf("bank", "banks", "logo", "logos", "icons", "directory", "list of banks", "find a bank", "sbi", "hdfc", "icici", "axis", "kotak", "gramin", "co-operative", "cooperative", "small finance", "payments bank", "foreign bank", "ifsc")),
        SettingsEntry("Add a bank", SettingsPage.Banks, listOf("add bank", "new bank", "missing bank", "my bank", "custom bank")),

        // Finance
        SettingsEntry("Categories", null, listOf("categories", "labels", "tags"), special = SettingsSpecial.Categories),
        SettingsEntry("People", null, listOf("people", "person", "split", "spending by person", "family", "friends", "who"), special = SettingsSpecial.People),
        SettingsEntry("Currency", SettingsPage.Finance, listOf("currency", "rupee", "inr", "indian", "money", "symbol", "dollar", "euro")),
        SettingsEntry("Default account", SettingsPage.Finance, listOf("default account", "preferred account")),
        SettingsEntry("Swipe actions", SettingsPage.Finance, listOf("swipe", "edit", "delete", "gesture", "list")),
        SettingsEntry("Financial year start", SettingsPage.Finance, listOf("financial year", "fiscal", "april", "january")),

        // Data & backup
        SettingsEntry("Sample data", SettingsPage.Data, listOf("sample data", "dummy data", "test", "mock", "demo")),
        SettingsEntry("Export backup", SettingsPage.Data, listOf("backup", "export", "json", "save")),
        SettingsEntry("Import backup", SettingsPage.Data, listOf("restore", "import", "load", "recover")),
        SettingsEntry("Backup of everything", SettingsPage.Data, listOf("app backup", "everything", "full app backup", "zip", "new phone", "move to a new phone", "backup settings", "export settings", "backup profile", "backup font")),
        SettingsEntry("Restore settings", SettingsPage.Data, listOf("restore settings", "import settings", "restore app backup", "restore everything", "restore profile", "restore font")),
        SettingsEntry("Reminders backup", SettingsPage.Data, listOf("reminders backup", "backup reminders", "export reminders", "restore reminders", "import reminders")),

        // Wallet
        SettingsEntry("Store cards", SettingsPage.Wallet, listOf("store cards", "don't store cards", "dont store cards", "do not store cards", "delete cards", "delete all cards", "remove cards", "erase cards", "no cards", "save cards", "card vault", "wallet cards", "accounts only")),
        SettingsEntry("Save CVV", SettingsPage.Wallet, listOf("cvv", "save cvv", "cvc", "security code", "don't save cvv", "dont save cvv", "do not store cvv", "delete cvv", "remove cvv", "card security code", "optional cvv"), cardsOnly = true),
        SettingsEntry("Card number on account", SettingsPage.Wallet, listOf("card number on account", "account card number", "card number", "first 6 digits", "last 4 digits", "bin", "paid from", "receipts", "match receipts", "recognise card", "recognize card")),
        SettingsEntry("Export and import cards", SettingsPage.Wallet, listOf("export cards", "import cards", "card backup", "backup cards", "move cards", "transfer cards", "password"), cardsOnly = true),
        SettingsEntry("Card actions style", SettingsPage.Wallet, listOf("buttons", "menu", "context menu", "actions"), cardsOnly = true),
        SettingsEntry("NFC tag reveal gate", SettingsPage.Security, listOf("nfc", "tap to reveal", "card number", "security", "registered tag", "only my tag", "my tag", "register tag"), cardsOnly = true),
        SettingsEntry("Clear copied card details after", SettingsPage.Wallet, listOf("clipboard", "copy", "clear", "timeout", "seconds", "security", "cvv"), cardsOnly = true),

        // Notifications
        SettingsEntry("Notifications", SettingsPage.Notifications, listOf("notifications", "notify", "alerts", "alert", "mute", "silence", "reminder alerts", "card expiry")),

        // Availability
        SettingsEntry("NFC card exchange schedule", SettingsPage.Availability, listOf("nfc", "schedule", "hours", "time window", "availability", "when")),

        // Security
        SettingsEntry("Biometric lock", SettingsPage.Security, listOf("fingerprint", "face", "biometric", "pin", "gate")),
        SettingsEntry("Lock scope", SettingsPage.Security, listOf("wallet only", "entire app", "scope")),
        SettingsEntry("Auto-lock timeout", SettingsPage.Security, listOf("immediate", "timeout", "delay", "grace period")),

        // System
        SettingsEntry("Reset all settings", null, listOf("defaults", "reset", "clear preferences"), special = SettingsSpecial.Reset),
        SettingsEntry("Check for updates", null, listOf("update", "upgrade", "new version", "latest", "download", "install", "apk", "release", "github", "automatic"), special = SettingsSpecial.Updates),
        SettingsEntry("What's new & changelog", null, listOf("what's new", "whats new", "changelog", "change log", "release notes", "version history", "latest changes", "new features", "version"), special = SettingsSpecial.Updates),
        SettingsEntry("Automatic updates", SettingsPage.Updates, listOf("automatic updates", "auto update", "background", "install automatically", "download automatically", "wi-fi", "wifi", "mobile data", "cellular", "metered", "hotspot", "network")),
        SettingsEntry("How often to check for updates", SettingsPage.Updates, listOf("how often", "frequency", "interval", "update schedule", "daily", "weekly", "twice a day", "once a day", "every 3 days", "once a week")),
        SettingsEntry("Update check time", SettingsPage.Updates, listOf("check time", "set time", "at a set time", "time of day", "when to check", "any time", "update time")),
        SettingsEntry("Check when NormPix opens", SettingsPage.Updates, listOf("check when opened", "check on open", "check at launch", "when i open", "open the app", "launch", "startup")),
        SettingsEntry(
            "About NormPix",
            null,
            listOf("about", "guide", "help", "how to", "how it works", "tips", "tutorial", "features", "licenses", "licences", "credits", "version", "privacy"),
            special = SettingsSpecial.About,
        ),
    )

    /** The settings matching [query]; with [cardsEnabled] false (Store cards off) the ones about saved cards are left out. */
    fun search(query: String, cardsEnabled: Boolean = true): List<SettingsEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return entries.filter { entry ->
            (cardsEnabled || !entry.cardsOnly) && (
                entry.title.lowercase().contains(q) ||
                    entry.keywords.any { it.contains(q) } ||
                    (entry.page?.name?.lowercase()?.contains(q) == true) ||
                    (entry.appearance?.title?.lowercase()?.contains(q) == true)
                )
        }
    }

    fun iconFor(page: SettingsPage?): ImageVector = when (page) {
        SettingsPage.Appearance -> Icons.Outlined.Palette
        SettingsPage.Widgets -> Icons.Outlined.Widgets
        SettingsPage.Finance -> Icons.Outlined.Payments
        SettingsPage.Banks -> Icons.Outlined.AccountBalance
        SettingsPage.Data -> Icons.Outlined.Storage
        SettingsPage.Wallet -> AppIcons.Wallet
        SettingsPage.Availability -> Icons.Outlined.Schedule
        SettingsPage.Notifications -> Icons.Outlined.Notifications
        SettingsPage.Updates -> Icons.Outlined.Update
        SettingsPage.Security -> Icons.Outlined.Security
        null -> Icons.Outlined.Tune
    }

    fun iconFor(special: SettingsSpecial?): ImageVector = when (special) {
        SettingsSpecial.Profile -> Icons.Outlined.AccountCircle
        SettingsSpecial.Categories -> Icons.Outlined.Payments
        SettingsSpecial.People -> Icons.Outlined.Groups
        SettingsSpecial.Reset -> Icons.Outlined.DeleteOutline
        SettingsSpecial.Updates -> Icons.Outlined.SystemUpdate
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
                entry.appearance != null -> navigator.openAppearance(entry.appearance)
                entry.page != null -> navigator.openSettings(entry.page)
                entry.special == SettingsSpecial.Profile -> navigator.openProfile()
                entry.special == SettingsSpecial.Categories -> navigator.openCategories()
                entry.special == SettingsSpecial.People -> navigator.openPeople()
                entry.special == SettingsSpecial.Reset -> system.resetSettings()
                entry.special == SettingsSpecial.Updates -> system.checkForUpdates()
                entry.special == SettingsSpecial.About -> navigator.openAbout()
                else -> navigator.openSettings()
            }
        }
    }
}

/** \"Reset settings\" and \"Check for updates\", shared by the Settings page and search. */
class SettingsSystemActions(
    val resetSettings: () -> Unit,
    val checkForUpdates: () -> Unit,
)

@Composable
fun rememberSettingsSystemActions(): SettingsSystemActions {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val scope = LocalAppScope.current
    val appContext = LocalContext.current.applicationContext

    fun resetSettings() {
        scope.launch {
            val confirmed = overlays.confirm(
                title = "Reset all settings?",
                message = "Your preferences will return to defaults. Your finance data, cards and reminders are not touched.",
                confirmLabel = "Reset",
            )
            if (!confirmed) return@launch
            container.preferences.resetToDefaults()
            // The font file the user picked goes with the setting that named it.
            withContext(Dispatchers.IO) { CustomFont.delete(appContext) }
            snackbars.show("Settings reset to defaults")
        }
    }

    /**
     * The update page: it asks GitHub (unless an update is already found, downloading or waiting) and shows where updating
     * stands - an update to download, its progress, ready to install, or up to date - with the changelog under it.
     */
    fun checkForUpdates() {
        if (container.updates == null) return
        navigator.openUpdates()
    }

    return remember(container, navigator, overlays, snackbars, scope, appContext) {
        SettingsSystemActions(::resetSettings, ::checkForUpdates)
    }
}
