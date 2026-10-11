package com.example.normpix.settings

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.example.normpix.availability.AvailabilityRule
import com.example.normpix.availability.ScheduledFeature
import com.example.normpix.availability.WeeklySchedule
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Light, dark, or follow the system. */
enum class ThemeMode(val label: String) {
    System("Follow system"),
    Light("Light"),
    Dark("Dark"),
}

/** Source of the color palette: the device's wallpaper-derived colors, or a curated [AccentColor]. */
enum class ColorSource { System, Custom }

/** Route transition style for pushed pages. */
enum class PageAnimationType(val label: String, val description: String) {
    System("System default", "Native Android transition with predictive back preview"),
    SlideHorizontal("Slide", "New pages slide in from the side"),
    FadeThrough("Fade through", "Pages fade and scale gently into place"),
    SharedAxis("Shared axis", "Pages glide a short distance while crossfading"),
    ScaleAndFade("Scale and fade", "Pages grow into place as they fade in"),
    LinuxWindow("Linux window", "Window naturally expands into focus on open and contracts away on close"),
}

/** Style and placement of a page's back button - and, in the floating style, of its whole header. */
enum class BackButtonStyle(val label: String, val description: String) {
    BottomDock("Bottom dock", "Floating pill at the bottom of sub-pages"),
    TopFloatingCircle("Top floating circle", "Glass circles and pills float over the top of every page, in place of the app bar"),
    StandardTopBar("Standard top bar", "Inline navigation arrow in the top app bar"),
}

/** How progress is drawn: bars that fill and a loader for waits (Material 3 Expressive, or the classic straight look). */
enum class ProgressStyle(val label: String, val description: String) {
    Wavy("Wavy", "Bars that fill with a moving wave, and a loader that morphs between shapes"),
    Flat("Flat", "Straight bars, and a round spinner for waits"),
}

/** How wallet cards expose their reveal/edit/delete controls. */
enum class WalletCardActionStyle(val label: String, val description: String) {
    Bar("Buttons under each card", "Reveal, edit, and delete sit in a row under every card"),
    Swipe("Swipe to reveal", "Swipe a card from right to left to show its actions"),
}

/** Which actions a swipe on a transaction row reveals. */
enum class TransactionSwipeActions(val label: String, val edit: Boolean, val delete: Boolean) {
    EditAndDelete("Edit and delete", edit = true, delete = true),
    EditOnly("Edit only", edit = true, delete = false),
    DeleteOnly("Delete only", edit = false, delete = true),
}

/** Which artwork represents a bank on the wallet card face: the full wordmark, or a compact square symbol. */
enum class BankIconStyle(val label: String, val description: String) {
    Full("Full logo", "The bank's full wordmark on each card"),
    Compact("Compact symbol", "A smaller, icon-only bank symbol"),
}

/** Where biometric locking is enforced. */
enum class AppLockScope(val label: String, val description: String) {
    WalletOnly("Wallet only", "Require biometric unlock only when viewing Wallet"),
    EntireApp("Entire app", "Require biometric unlock to open NormPix"),
}

/** Inactivity or tab-switch timeout after which the app/wallet re-locks. */
enum class AppLockTimeout(val label: String, val description: String, val millis: Long?) {
    Immediate("Immediately on tab change", "Re-locks immediately whenever you switch tabs or leave", 0),
    Seconds30("After 30 seconds", "Stays unlocked for 30 seconds after switching away", 30_000),
    Minute1("After 1 minute", "Stays unlocked for 1 minute after switching away", 60_000),
    Minute5("After 5 minutes", "Stays unlocked for 5 minutes after switching away", 300_000),
    Session("When app is closed", "Stays unlocked until the app is closed", null),
}

/**
 * How long a copied card detail stays on the clipboard before NormPix clears it. [seconds] is 0 for [Never];
 * [spoken] finishes the sentence "Clears from the clipboard in ...".
 */
enum class ClipboardTimeout(val seconds: Int, val label: String, val description: String, val spoken: String) {
    Sec10(10, "10 seconds", "The default", "10 seconds"),
    Sec30(30, "30 seconds", "Clears after half a minute", "30 seconds"),
    Min1(60, "1 minute", "Clears after a minute", "1 minute"),
    Min5(300, "5 minutes", "Clears after five minutes", "5 minutes"),
    Never(0, "Never", "Copied details stay until you copy something else", ""),
    ;

    val clears: Boolean get() = seconds > 0
}

/**
 * Curated seed colors used when [ColorSource.Custom] is selected. The final
 * palette is generated from the seed (see `AppTheme`), so these are starting
 * hues, not literal UI colors.
 */
enum class AccentColor(val seed: Color, val label: String) {
    Green(Color(0xFF16A34A), "Emerald"),
    Blue(Color(0xFF2563EB), "Ocean"),
    Purple(Color(0xFF7C3AED), "Royal"),
    Red(Color(0xFFDC2626), "Coral"),
    Orange(Color(0xFFD97706), "Amber"),

    /** Lime on near-black green. Its palette is drawn by hand (see `AppTheme.voltScheme`), not generated from the seed. */
    Volt(Color(0xFFAFE22D), "Volt"),
}

/** How the Wallet lays out the accounts. */
enum class AccountsLayout(val label: String, val description: String) {
    Stack("Stacked cards", "One account in front as a card, the others tucked behind it. Swipe to bring the next one forward"),
    Cards("Card list", "Every account as a card, one under another"),
    Rows("Compact list", "One line for each account: bank, kind and balance"),
}

/** How the Wallet lays out the saved cards. */
enum class CardsLayout(val label: String, val description: String) {
    Stack("Stacked cards", "One card in front, the others tucked behind it. Swipe to bring the next one forward, hold to spread them out"),
    Cards("Card list", "Every card in full, one under another, with its own actions"),
    Rows("Compact list", "One line for each card: bank, kind and expiry. Tap one to open it"),
}

/**
 * How the app's built-in category icons are drawn: as colourful emoji, or as the outlined Material icons. A category the
 * user gave an emoji of their own shows that emoji either way.
 */
enum class CategoryIconStyle(val label: String, val description: String) {
    Emoji("Emoji", "Colourful, specific pictures: a pizza for Pizza, a bus for Bus & metro"),
    Outline("Outline", "Simple single-colour icons that take the category's colour"),
}

/** Where the round account button at the top of every tab leads. */
enum class AccountButtonTarget(val label: String, val description: String) {
    Profile("Profile", "Your name and picture, with the way on to Settings"),
    Settings("Settings", "Straight to Settings"),
}

/** The longest name the profile keeps: enough for a full name, short enough for a header. */
const val MAX_PROFILE_NAME = 40

/** Everything the user can tune about how the app looks and how the wallet is protected. */
data class AppPrefsState(
    val mode: ThemeMode = ThemeMode.System,
    val colorSource: ColorSource = ColorSource.System,
    val accent: AccentColor = AccentColor.Green,
    val pageAnimation: PageAnimationType = PageAnimationType.System,
    val walletCardActionStyle: WalletCardActionStyle = WalletCardActionStyle.Bar,
    val bankIconStyle: BankIconStyle = BankIconStyle.Full,
    val accountsLayout: AccountsLayout = AccountsLayout.Stack,
    val cardsLayout: CardsLayout = CardsLayout.Cards,
    val nfcTagRevealEnabled: Boolean = false,
    /** Revealing a card asks for one of the tags the user registered, not just any tag or phone. */
    val nfcRevealRegisteredOnly: Boolean = false,
    val walletLockEnabled: Boolean = false,
    val appLockScope: AppLockScope = AppLockScope.WalletOnly,
    val appLockTimeout: AppLockTimeout = AppLockTimeout.Immediate,
    val dummyDataEnabled: Boolean = false,
    /** Swipe a transaction row to reveal its actions. Every action stays reachable from the row's details either way. */
    val swipeActionsEnabled: Boolean = true,
    val transactionSwipeActions: TransactionSwipeActions = TransactionSwipeActions.EditAndDelete,
    val clipboardTimeout: ClipboardTimeout = ClipboardTimeout.Sec10,
    /** Cards tilt a few degrees with the phone's motion. Off, or with animations removed system-wide, they stay flat. */
    val cardTiltEnabled: Boolean = true,
    /** Blurs the background behind bottom sheets on supported devices. */
    val sheetBlurEnabled: Boolean = true,
    /** Back button style and placement on sub-pages. */
    val backButtonStyle: BackButtonStyle = BackButtonStyle.BottomDock,
    /** How progress bars and loaders look, wherever the app shows progress. */
    val progressStyle: ProgressStyle = ProgressStyle.Wavy,
    /** The window each [ScheduledFeature] may be used in; a feature missing here is always available. */
    val availability: Map<ScheduledFeature, AvailabilityRule> = emptyMap(),
    /** How the built-in category icons are drawn, wherever a category shows. */
    val categoryIconStyle: CategoryIconStyle = CategoryIconStyle.Emoji,
    /** What the user likes to be called. Stays on this device; empty until they say. */
    val profileName: String = "",
    /** One emoji for the profile picture, or empty for the name's initials. */
    val profileEmoji: String = "",
    /** Where the account button at the top of each tab leads. Settings, as it always has; Profile is the user's to choose. */
    val accountButtonTarget: AccountButtonTarget = AccountButtonTarget.Settings,
) {
    fun availabilityOf(feature: ScheduledFeature): AvailabilityRule = availability[feature] ?: AvailabilityRule()
}

/**
 * App-wide preferences, persisted in [SharedPreferences]. Reads are
 * synchronous (the file is loaded at process start), writes are applied
 * asynchronously, and [state] always reflects the latest values.
 */
class AppPreferences(private val prefs: SharedPreferences) {
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppPrefsState> = _state.asStateFlow()

    private fun read(): AppPrefsState {
        val defaults = AppPrefsState()
        return AppPrefsState(
            mode = prefs.enum(K_MODE, defaults.mode),
            colorSource = prefs.enum(K_COLOR_SOURCE, defaults.colorSource),
            accent = prefs.enum(K_ACCENT, defaults.accent),
            pageAnimation = prefs.enum(K_PAGE_ANIMATION, defaults.pageAnimation),
            walletCardActionStyle = prefs.enum(K_WALLET_ACTION_STYLE, defaults.walletCardActionStyle),
            bankIconStyle = prefs.enum(K_BANK_ICON_STYLE, defaults.bankIconStyle),
            accountsLayout = prefs.enum(K_ACCOUNTS_LAYOUT, defaults.accountsLayout),
            cardsLayout = prefs.enum(K_CARDS_LAYOUT, defaults.cardsLayout),
            nfcTagRevealEnabled = prefs.getBoolean(K_NFC_REVEAL, defaults.nfcTagRevealEnabled),
            nfcRevealRegisteredOnly = prefs.getBoolean(K_NFC_REVEAL_REGISTERED, defaults.nfcRevealRegisteredOnly),
            walletLockEnabled = prefs.getBoolean(K_WALLET_LOCK, defaults.walletLockEnabled),
            appLockScope = prefs.enum(K_LOCK_SCOPE, defaults.appLockScope),
            appLockTimeout = prefs.enum(K_LOCK_TIMEOUT, defaults.appLockTimeout),
            dummyDataEnabled = prefs.getBoolean(K_DUMMY_DATA, defaults.dummyDataEnabled),
            swipeActionsEnabled = prefs.getBoolean(K_SWIPE_ENABLED, defaults.swipeActionsEnabled),
            transactionSwipeActions = prefs.enum(K_TRANSACTION_SWIPE, defaults.transactionSwipeActions),
            clipboardTimeout = prefs.enum(K_CLIPBOARD_TIMEOUT, defaults.clipboardTimeout),
            cardTiltEnabled = prefs.getBoolean(K_CARD_TILT, defaults.cardTiltEnabled),
            sheetBlurEnabled = prefs.getBoolean(K_SHEET_BLUR, defaults.sheetBlurEnabled),
            backButtonStyle = prefs.enum(K_BACK_STYLE, defaults.backButtonStyle),
            progressStyle = prefs.enum(K_PROGRESS_STYLE, defaults.progressStyle),
            categoryIconStyle = prefs.enum(K_CATEGORY_ICON_STYLE, defaults.categoryIconStyle),
            profileName = prefs.getString(K_PROFILE_NAME, defaults.profileName).orEmpty(),
            profileEmoji = prefs.getString(K_PROFILE_EMOJI, defaults.profileEmoji).orEmpty(),
            accountButtonTarget = prefs.enum(K_ACCOUNT_BUTTON, defaults.accountButtonTarget),
            // Only features the user has configured; the rest read as the default (always on) through availabilityOf.
            availability = ScheduledFeature.entries.filter { prefs.contains(it.key("mode")) }.associateWith { prefs.readRule(it) },
        )
    }

    private fun SharedPreferences.readRule(feature: ScheduledFeature): AvailabilityRule {
        val defaults = AvailabilityRule()
        val days = getString(feature.key("days"), null)
            ?.split(',')?.mapNotNull { it.toIntOrNull()?.takeIf { d -> d in 1..7 }?.let(DayOfWeek::of) }?.toSet()
        return AvailabilityRule(
            mode = enum(feature.key("mode"), defaults.mode),
            schedule = WeeklySchedule(
                days = days ?: defaults.schedule.days,
                start = getInt(feature.key("start"), defaults.schedule.start.minuteOfDay()).toLocalTime(),
                end = getInt(feature.key("end"), defaults.schedule.end.minuteOfDay()).toLocalTime(),
            ),
        )
    }

    fun setMode(mode: ThemeMode) = change({ copy(mode = mode) }) { putString(K_MODE, mode.name) }

    fun setColorSource(source: ColorSource) = change({ copy(colorSource = source) }) { putString(K_COLOR_SOURCE, source.name) }

    /**
     * Picking an accent implies the user wants curated colors, so this also
     * switches the color source to [ColorSource.Custom].
     */
    fun setAccent(accent: AccentColor) = change({ copy(accent = accent, colorSource = ColorSource.Custom) }) {
        putString(K_ACCENT, accent.name)
        putString(K_COLOR_SOURCE, ColorSource.Custom.name)
    }

    fun setPageAnimation(animation: PageAnimationType) =
        change({ copy(pageAnimation = animation) }) { putString(K_PAGE_ANIMATION, animation.name) }

    fun setWalletCardActionStyle(style: WalletCardActionStyle) =
        change({ copy(walletCardActionStyle = style) }) { putString(K_WALLET_ACTION_STYLE, style.name) }

    fun setBankIconStyle(style: BankIconStyle) = change({ copy(bankIconStyle = style) }) { putString(K_BANK_ICON_STYLE, style.name) }

    fun setAccountsLayout(layout: AccountsLayout) = change({ copy(accountsLayout = layout) }) { putString(K_ACCOUNTS_LAYOUT, layout.name) }

    fun setCardsLayout(layout: CardsLayout) = change({ copy(cardsLayout = layout) }) { putString(K_CARDS_LAYOUT, layout.name) }

    fun setAppLockScope(scope: AppLockScope) = change({ copy(appLockScope = scope) }) { putString(K_LOCK_SCOPE, scope.name) }

    fun setAppLockTimeout(timeout: AppLockTimeout) =
        change({ copy(appLockTimeout = timeout) }) { putString(K_LOCK_TIMEOUT, timeout.name) }

    fun setNfcTagRevealEnabled(enabled: Boolean) =
        change({ copy(nfcTagRevealEnabled = enabled) }) { putBoolean(K_NFC_REVEAL, enabled) }

    fun setNfcRevealRegisteredOnly(enabled: Boolean) =
        change({ copy(nfcRevealRegisteredOnly = enabled) }) { putBoolean(K_NFC_REVEAL_REGISTERED, enabled) }

    fun setWalletLockEnabled(enabled: Boolean) =
        change({ copy(walletLockEnabled = enabled) }) { putBoolean(K_WALLET_LOCK, enabled) }

    fun setDummyDataEnabled(enabled: Boolean) =
        change({ copy(dummyDataEnabled = enabled) }) { putBoolean(K_DUMMY_DATA, enabled) }

    fun setSwipeActionsEnabled(enabled: Boolean) =
        change({ copy(swipeActionsEnabled = enabled) }) { putBoolean(K_SWIPE_ENABLED, enabled) }

    fun setTransactionSwipeActions(actions: TransactionSwipeActions) =
        change({ copy(transactionSwipeActions = actions) }) { putString(K_TRANSACTION_SWIPE, actions.name) }

    fun setClipboardTimeout(timeout: ClipboardTimeout) =
        change({ copy(clipboardTimeout = timeout) }) { putString(K_CLIPBOARD_TIMEOUT, timeout.name) }

    fun setCardTiltEnabled(enabled: Boolean) =
        change({ copy(cardTiltEnabled = enabled) }) { putBoolean(K_CARD_TILT, enabled) }

    fun setSheetBlurEnabled(enabled: Boolean) =
        change({ copy(sheetBlurEnabled = enabled) }) { putBoolean(K_SHEET_BLUR, enabled) }

    fun setBackButtonStyle(style: BackButtonStyle) =
        change({ copy(backButtonStyle = style) }) { putString(K_BACK_STYLE, style.name) }

    fun setProgressStyle(style: ProgressStyle) =
        change({ copy(progressStyle = style) }) { putString(K_PROGRESS_STYLE, style.name) }

    fun setCategoryIconStyle(style: CategoryIconStyle) =
        change({ copy(categoryIconStyle = style) }) { putString(K_CATEGORY_ICON_STYLE, style.name) }

    /** Saves the profile; [name] is trimmed and capped, [emoji] is kept only when it is exactly one emoji (see `isSingleEmoji`). */
    fun setProfile(name: String, emoji: String) {
        val cleanName = name.trim().take(MAX_PROFILE_NAME)
        val cleanEmoji = emoji.takeIf { com.example.normpix.finance.domain.isSingleEmoji(it) }.orEmpty()
        change({ copy(profileName = cleanName, profileEmoji = cleanEmoji) }) {
            putString(K_PROFILE_NAME, cleanName)
            putString(K_PROFILE_EMOJI, cleanEmoji)
        }
    }

    fun setAccountButtonTarget(target: AccountButtonTarget) =
        change({ copy(accountButtonTarget = target) }) { putString(K_ACCOUNT_BUTTON, target.name) }

    fun setAvailability(feature: ScheduledFeature, rule: AvailabilityRule) = change({ copy(availability = availability + (feature to rule)) }) {
        putString(feature.key("mode"), rule.mode.name)
        putString(feature.key("days"), rule.schedule.days.joinToString(",") { it.value.toString() })
        putInt(feature.key("start"), rule.schedule.start.minuteOfDay())
        putInt(feature.key("end"), rule.schedule.end.minuteOfDay())
    }

    /** Whether the user was already sent to the system page for exact-alarm access (asked once, then never nagged). */
    var exactAlarmPrompted: Boolean
        get() = prefs.getBoolean(K_EXACT_ALARM_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(K_EXACT_ALARM_PROMPTED, value).apply()

    /**
     * Restores every preference to its default. Wallet cards and reminders are separate stores and are never affected,
     * and neither is the profile: a name and a picture are who the user is, not a setting.
     */
    fun resetToDefaults() {
        val keep = setOf(K_EXACT_ALARM_PROMPTED, K_PROFILE_NAME, K_PROFILE_EMOJI)
        _state.update { AppPrefsState(profileName = it.profileName, profileEmoji = it.profileEmoji) }
        prefs.edit().apply {
            // Everything except the one-time system-prompt marker and the profile.
            prefs.all.keys.filter { it !in keep }.forEach { remove(it) }
        }.apply()
    }

    private inline fun change(crossinline update: AppPrefsState.() -> AppPrefsState, write: SharedPreferences.Editor.() -> Unit) {
        _state.update { it.update() }
        prefs.edit().apply(write).apply()
    }

    private companion object {
        const val K_MODE = "theme_mode"
        const val K_COLOR_SOURCE = "color_source"
        const val K_ACCENT = "accent_color"
        const val K_PAGE_ANIMATION = "page_animation"
        const val K_WALLET_ACTION_STYLE = "wallet_card_action_style"
        const val K_BANK_ICON_STYLE = "bank_icon_style"
        const val K_ACCOUNTS_LAYOUT = "accounts_layout"
        const val K_CARDS_LAYOUT = "cards_layout"
        const val K_NFC_REVEAL = "nfc_tag_reveal_enabled"
        const val K_NFC_REVEAL_REGISTERED = "nfc_reveal_registered_only"
        const val K_WALLET_LOCK = "wallet_lock_enabled"
        const val K_LOCK_SCOPE = "app_lock_scope"
        const val K_LOCK_TIMEOUT = "app_lock_timeout"
        const val K_EXACT_ALARM_PROMPTED = "exact_alarm_prompted"
        const val K_DUMMY_DATA = "dummy_data_enabled"
        const val K_SWIPE_ENABLED = "swipe_actions_enabled"
        const val K_TRANSACTION_SWIPE = "transaction_swipe_actions"
        const val K_CLIPBOARD_TIMEOUT = "clipboard_timeout"
        const val K_CARD_TILT = "card_tilt_enabled"
        const val K_SHEET_BLUR = "sheet_blur_enabled"
        const val K_BACK_STYLE = "back_button_style"
        const val K_PROGRESS_STYLE = "progress_style"
        const val K_CATEGORY_ICON_STYLE = "category_icon_style"
        const val K_PROFILE_NAME = "profile_name"
        const val K_PROFILE_EMOJI = "profile_emoji"
        const val K_ACCOUNT_BUTTON = "account_button_target"

        fun ScheduledFeature.key(field: String) = "availability_${name.lowercase()}_$field"
        fun LocalTime.minuteOfDay() = hour * 60 + minute
        fun Int.toLocalTime(): LocalTime = coerceIn(0, 24 * 60 - 1).let { LocalTime.of(it / 60, it % 60) }

        inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String, default: T): T {
            val name = getString(key, null) ?: return default
            return enumValues<T>().firstOrNull { it.name == name } ?: default
        }
    }
}
