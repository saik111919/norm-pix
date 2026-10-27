package com.example.normpix.settings

import androidx.core.content.edit
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.example.normpix.availability.AvailabilityRule
import com.example.normpix.availability.ScheduledFeature
import com.example.normpix.availability.WeeklySchedule
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The typeface the interface is set in: the phone's own, the bundled variable GS Flex (Google Sans Flex, SIL OFL), or a font
 * file the user picked ([Custom]; its copy and name are kept by `CustomFont` and [AppPrefsState.customFontName]). Not to be
 * mixed up with [AppPrefsState.customFont], which is GS Flex set by hand.
 */
enum class InterfaceFont(val label: String, val description: String) {
    GsFlex("GS Flex", "A variable font with adjustable weight, width and roundness"),
    System("System", "Your phone's own font"),
    Custom("Your font", "A .ttf or .otf file from your phone"),
}

/**
 * GS Flex styles: how heavy, how wide and how round the letters are. [weightShift] moves every text style's weight by that
 * much (so headings stay heavier than body text), [width] is the font's width axis (100 is as designed) and [roundness] its
 * ROND axis (0 sharp, 100 fully rounded). [Standard] is the font as designed.
 */
enum class FontPreset(val label: String, val weightShift: Int, val width: Int, val roundness: Int) {
    Standard("Standard", weightShift = 0, width = 100, roundness = 0),
    Zenith("Zenith", weightShift = -50, width = 112, roundness = 0),
    Neo("Neo", weightShift = 50, width = 88, roundness = 0),
    Impact("Impact", weightShift = 250, width = 104, roundness = 0),
    Airy("Airy", weightShift = -100, width = 106, roundness = 100),
}

/** The ranges the custom GS Flex controls allow (see [AppPrefsState.fontWeightShift] and the rest). */
object FontAxes {
    val WEIGHT_SHIFT = -300..300
    val WIDTH = 75..125
    val ROUNDNESS = 0..100
}

/** Light, dark, or follow the system. */
enum class ThemeMode(val label: String) {
    System("Follow system"),
    Light("Light"),
    Dark("Dark"),
}

/** Source of the color palette: the device's wallpaper-derived colors, or a curated [AccentColor]. */
enum class ColorSource { System, Custom }

/**
 * Dynamic Monet palette style derived from the device's wallpaper (Android 12+).
 */
enum class WallpaperPalette(val label: String, val style: PaletteStyle) {
    TonalSpot("Wallpaper", PaletteStyle.TonalSpot),
    Neutral("Neutral", PaletteStyle.Neutral),
    Vibrant("Vibrant", PaletteStyle.Vibrant),
    Expressive("Expressive", PaletteStyle.Expressive),
    FruitSalad("Fruit salad", PaletteStyle.FruitSalad),
}

/**
 * How the app moves between places: pushed pages, tabs, a screen's sections, and the back gesture's preview all follow
 * the one chosen. [LinuxWindow] is the default.
 */
enum class PageAnimationType(val label: String, val description: String) {
    LinuxWindow("Linux window", "Pages open like a desktop window: a thin strip at the bottom that expands upward, then sinks back into it, quicker"),
    System("System default", "Native Android transition with predictive back preview"),
    SlideHorizontal("Slide", "New pages slide in from the side"),
    FadeThrough("Fade through", "Pages fade and scale gently into place"),
    SharedAxis("Shared axis", "Pages glide a short distance while crossfading"),
    ScaleAndFade("Scale and fade", "Pages grow into place as they fade in"),
}

/**
 * How bottom sheets and decisions (the confirm sheets) open and close - chosen apart from the [PageAnimationType], which
 * moves pages. They are the same surface, so every sheet and every decision follows the one chosen. With animations turned
 * off in system settings none of them adds any motion of its own.
 */
enum class SheetAnimationType(val label: String, val description: String) {
    Window("Window", "Grows out of the bottom edge as it rises, and shrinks back into it as it goes"),
    LinuxWindow(
        "Linux window",
        "Opens like a desktop window: a thin strip at the bottom that expands upward, then sinks back into it as it closes",
    ),
    Slide("Slide", "The standard bottom sheet: it slides up from the bottom edge"),
    Fade("Fade", "Stays where it rests and fades in, then fades out as it goes"),
    Scale("Scale", "Stays where it rests and grows from its bottom edge as it fades in, then shrinks back as it goes"),
    ;

    companion object {
        /**
         * The style sheets have when none was ever picked: what they showed before they had a setting of their own - the window
         * style with the Linux window pages, the plain sheet with any other.
         */
        fun defaultFor(pages: PageAnimationType): SheetAnimationType =
            if (pages == PageAnimationType.LinuxWindow) Window else Slide
    }
}

/** Style and placement of a page's back button - and, in the floating style, of its whole header. [TopFloatingCircle] is the default. */
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

/**
 * How the month a page shows is switched (Home's history, Plans, Insights, People): [Track] is the one tonal track the tabs above
 * share, [Split] the earlier look of separate tonal buttons in the split-button family. [Track] is the default; the choice is
 * the user's, and both stay.
 */
enum class MonthControlStyle(val label: String, val description: String) {
    Track("Track", "One tonal track like the tabs above it: a step back, the month on a filled pill, a step forward"),
    Split("Split buttons", "The earlier look: the steps and the month as separate tonal buttons, their inner corners opening under a finger"),
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
 * hues, not literal UI colors. [Volt] is the default: what the app wears with
 * wallpaper colors off (or unavailable) until the user picks another. [Volt] and [Pure] are drawn by hand.
 */
enum class AccentColor(val seed: Color, val label: String) {
    Green(Color(0xFF16A34A), "Emerald"),
    Blue(Color(0xFF2563EB), "Ocean"),
    Purple(Color(0xFF7C3AED), "Royal"),
    Red(Color(0xFFDC2626), "Coral"),
    Orange(Color(0xFFD97706), "Amber"),

    /** Lime on near-black green. Its palette is drawn by hand (see `AppTheme.voltScheme`), not generated from the seed. */
    Volt(Color(0xFFAFE22D), "Volt"),

    /**
     * Black and white and nothing between but greys: pure white pages with black ink in light, pure black pages with white ink in
     * dark. Drawn by hand like [Volt] (see `AppTheme.pureScheme`); it follows the Theme choice (light, dark, or the system's) like
     * every other color does.
     */
    Pure(Color(0xFF000000), "Pure"),
}

/** How Home shows the month's biggest spending categories. */
enum class TopSpendingLayout(val label: String, val description: String) {
    Stack("Stacked cards", "The biggest in front, the others tucked behind it. Swipe to bring the next one forward, hold to spread them out"),
    Carousel("Carousel", "The biggest in the middle, the next ones turned away either side. Swipe across to change category; what it came to is underneath"),
}

/** How the Wallet lays out the accounts. */
enum class AccountsLayout(val label: String, val description: String) {
    Stack("Stacked cards", "One account in front as a card, the others tucked behind it. Swipe to bring the next one forward, hold to spread them out"),
    Cards("Card list", "Every account as a card, one under another"),
    Rows("Compact list", "One line for each account: bank, kind and balance"),
    Carousel("Carousel", "One account in the middle, the next ones turned away either side. Swipe across to change account; what it holds is underneath"),
}

/** How the Wallet lays out the saved cards. */
enum class CardsLayout(val label: String, val description: String) {
    Stack("Stacked cards", "One card in front, the others tucked behind it. Swipe to bring the next one forward, hold to spread them out"),
    Cards("Card list", "Every card in full, one under another, with its own actions"),
    Rows("Compact list", "One line for each card: bank, kind and expiry. Tap one to open it"),
    Carousel("Carousel", "One card in the middle, the next ones peeking in from the sides. Swipe across to change card; its details and buttons are underneath, whichever Card actions style is chosen"),
}

/**
 * How the app's built-in category icons are drawn: as 3D pictures, as the phone's own emoji, or as the outlined Material
 * icons. A brand is shown by its logo in the first two. A category the user gave an emoji of their own shows that emoji in
 * every style (in 3D where the app has a picture of it). Stored by name: "Emoji" and "Outline" are what earlier versions saved.
 */
enum class CategoryIconStyle(val label: String, val description: String) {
    ThreeD("3D", "Rich 3D pictures, and each brand's own logo: a pizza for Pizza, the Swiggy logo for Swiggy"),
    Emoji("Emoji", "Your phone's own colourful emoji, with brand logos"),
    Outline("Outline", "Simple single-colour icons that take the category's colour"),
}

/**
 * The shape behind a category's (or a person's) icon wherever an avatar shows. [cornerPercent] is how much of the avatar's size
 * each corner takes: 50 is a circle. [None] has no shape behind the icon, which is drawn larger instead; a brand's logo, which
 * is a full-bleed square, is still clipped (to rounded corners) so it never shows its white edges.
 */
enum class CategoryIconShape(val label: String, val description: String, val cornerPercent: Int) {
    Circle("Circle", "A round disc behind each icon", 50),
    Rounded("Rounded", "A soft rounded square, like an app icon", 30),
    Square("Square", "A square tile with just-softened corners", 12),
    None("No background", "Just the icon, drawn larger, with nothing behind it", 22),
}

/** Where the round account button at the top of every tab leads. */
enum class AccountButtonTarget(val label: String, val description: String) {
    Profile("Profile", "Your name and picture, with the way on to Settings"),
    Settings("Settings", "Straight to Settings"),
}

/**
 * What NormPix does with an update its automatic check finds on its own (Settings > Automatic updates). Every update is
 * still checked against its release and has to be signed with the same key as the app on the phone.
 */
enum class BackgroundUpdates(val label: String, val description: String, val short: String) {
    Notify("Tell me", "A notification says a new version is out. Downloading and installing wait for you.", "Tell me"),
    Download(
        "Download it, then ask",
        "It downloads in the background, and a notification says it's ready. You install it when it suits you.",
        "Download, then ask",
    ),
    Install(
        "Install it automatically",
        "It downloads and installs while NormPix is closed, and a notification says what was installed. Android 12 and " +
            "later; before that, Android asks you first.",
        "Install automatically",
    ),
    ;

    /** Whether this downloads the update, so the network it may use matters. */
    val downloads: Boolean get() = this != Notify
}

/**
 * How often NormPix looks for an update by itself (Settings > Automatic updates > How often): in the background, and when it
 * is opened. [intervalMs] is the wait between two checks, [short] the word the Settings summary uses.
 */
enum class UpdateCheckEvery(val label: String, val intervalMs: Long, val short: String) {
    TwiceADay("Twice a day", 12L * 60 * 60 * 1000, "Twice daily"),
    OnceADay("Once a day", 24L * 60 * 60 * 1000, "Daily"),
    EveryThreeDays("Every 3 days", 3L * 24 * 60 * 60 * 1000, "Every 3 days"),
    OnceAWeek("Once a week", 7L * 24 * 60 * 60 * 1000, "Weekly"),
}

/** When the background check runs: whenever Android finds fit, as it always has, or around a time of day the user sets. */
enum class UpdateCheckWhen(val label: String, val description: String) {
    AnyTime("Any time", "Android picks a quiet moment"),
    AtSetTime("At a set time", "Around a time you choose"),
}

/** The time of day a check at a set time is made around until the user picks another: 9:00 PM, as minutes since midnight. */
const val DEFAULT_UPDATE_CHECK_MINUTE = 21 * 60

/** The longest name the profile keeps: enough for a full name, short enough for a header. */
const val MAX_PROFILE_NAME = 40

/** The longest file name kept for the user's own font: more than any real file has, so only an odd one is cut. */
const val MAX_CUSTOM_FONT_NAME = 80

/** Everything the user can tune about how the app looks and how the wallet is protected. */
data class AppPrefsState(
    val mode: ThemeMode = ThemeMode.System,
    val colorSource: ColorSource = ColorSource.System,
    val accent: AccentColor = AccentColor.Volt,
    val wallpaperPalette: WallpaperPalette = WallpaperPalette.TonalSpot,
    val pageAnimation: PageAnimationType = PageAnimationType.LinuxWindow,
    /** How sheets and decisions open and close. Never picked, it is what the page style made it before (see [SheetAnimationType.defaultFor]). */
    val sheetAnimation: SheetAnimationType = SheetAnimationType.defaultFor(PageAnimationType.LinuxWindow),
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
    /**
     * Settings > Wallet & Cards > Save CVV. Off, no card keeps a CVV: the field is gone from the card form, and a card from a
     * scan, an NFC tap or a cards file is kept without one. Turning it off deletes the CVVs already saved, after asking.
     */
    val cardCvvEnabled: Boolean = true,
    /**
     * Settings > Wallet & Cards > Store cards. Off, NormPix keeps no cards at all: the vault is emptied (after asking), the Wallet
     * shows accounts only, and everything about cards leaves the settings and the search. Turning it back on starts empty.
     */
    val walletCardsEnabled: Boolean = true,
    /** Blurs the background behind bottom sheets on supported devices. */
    val sheetBlurEnabled: Boolean = true,
    /** Blurs and fades the page under the floating dock and back row (the footer), where background blur is available. */
    val footerBlurEnabled: Boolean = true,
    /**
     * The wide navigation rail of a Large window (a tablet in landscape) folded down by hand to the narrow one, with its button at
     * the top; false, the rail is as wide as the window allows. Only a window that has the wide rail one to fold.
     */
    val railCollapsed: Boolean = false,
    /** Back button style and placement on sub-pages. */
    val backButtonStyle: BackButtonStyle = BackButtonStyle.TopFloatingCircle,
    /**
     * Settings > Appearance > Navigation & controls. On a phone the controls a page has at its top - the way back, its actions, its
     * search and filters - sit at the bottom instead, within a thumb's reach, beside the dock. The pages follow the bottom dock
     * style while it is on, whichever [backButtonStyle] is picked; a wider window keeps its rail and its layout.
     */
    val bottomControls: Boolean = false,
    /**
     * Settings > Appearance > Navigation & controls. Show text labels next to the active destination in the bottom navigation bar.
     * When off, destinations are represented by icons only.
     */
    val showFooterLabels: Boolean = true,
    /** How progress bars and loaders look, wherever the app shows progress. */
    val progressStyle: ProgressStyle = ProgressStyle.Wavy,
    /** Settings > Appearance > Layouts: Home's biggest categories as a stack or a carousel. */
    val topSpendingLayout: TopSpendingLayout = TopSpendingLayout.Stack,
    /** How the month a page shows is switched: one tonal track, or the earlier separate buttons. */
    val monthControlStyle: MonthControlStyle = MonthControlStyle.Track,
    /** The window each [ScheduledFeature] may be used in; a feature missing here has its default rule ([ScheduledFeature.defaultRule]). */
    val availability: Map<ScheduledFeature, AvailabilityRule> = emptyMap(),
    /** How the built-in category icons are drawn, wherever a category shows. */
    val categoryIconStyle: CategoryIconStyle = CategoryIconStyle.ThreeD,
    /** The shape behind those icons (and the people's avatars): a circle, as they have always been, unless the user picks another. */
    /** The shape behind icons: none until the user picks one - the icon on its own, drawn larger. */
    val categoryIconShape: CategoryIconShape = CategoryIconShape.None,
    /** What the user likes to be called. Stays on this device; empty until they say. */
    val profileName: String = "",
    /** One emoji for the profile picture, or empty for the name's initials. */
    val profileEmoji: String = "",
    /** The file name of the user's own photo for the profile picture (see [ProfilePhotos]), or empty when they have none. It wins over the emoji and the initial. */
    val profilePhoto: String = "",
    /** Where the account button at the top of each tab leads. Settings, as it always has; Profile is the user's to choose. */
    val accountButtonTarget: AccountButtonTarget = AccountButtonTarget.Settings,
    /**
     * In the background and at launch, as often as [updateCheckEvery] says, ask GitHub whether a newer release is out
     * (Settings > Automatic updates). Off, NormPix only checks when asked.
     */
    val updateChecksEnabled: Boolean = true,
    /** What happens to an update found in the background: downloaded, and then the user installs it, unless they choose otherwise. */
    val backgroundUpdates: BackgroundUpdates = BackgroundUpdates.Download,
    /**
     * Background downloads may use mobile data; off, they wait for Wi-Fi (or another network that isn't metered, so a metered
     * Wi-Fi such as a hotspot counts as mobile data). A download the user starts in the app uses any network.
     */
    val updateOnMobileData: Boolean = false,
    /** How often the automatic check is made, in the background and at launch: once a day, as it always was, unless the user chooses. */
    val updateCheckEvery: UpdateCheckEvery = UpdateCheckEvery.OnceADay,
    /** Whether the background check runs whenever Android finds fit, or around [updateCheckMinuteOfDay]. */
    val updateCheckWhen: UpdateCheckWhen = UpdateCheckWhen.AnyTime,
    /** The time of day a check at a set time is made around, as minutes since midnight (see [updateCheckTime]). */
    val updateCheckMinuteOfDay: Int = DEFAULT_UPDATE_CHECK_MINUTE,
    /** NormPix also checks, quietly, when it is opened - at most once per [updateCheckEvery]. Off, only the background check looks. */
    val updateCheckOnOpen: Boolean = true,
    /** NormPix may post notifications: reminders that come due and card-expiry reminders. Android's own switch still applies. */
    val notificationsEnabled: Boolean = true,
    /** Every amount is shown as dots (the eye button at the top of Home and Wallet) until shown again. */
    val amountsHidden: Boolean = false,
    /** The interface's typeface: GS Flex, in its Zenith style, until the user picks another. */
    val interfaceFont: InterfaceFont = InterfaceFont.GsFlex,
    /** The GS Flex style used while [customFont] is off. */
    val fontPreset: FontPreset = FontPreset.Zenith,
    /** GS Flex is set by hand with the three values below instead of a [fontPreset]. */
    val customFont: Boolean = false,
    /** How much heavier (+) or lighter (-) than designed every text style is, in font-weight units (see [FontAxes]). */
    val fontWeightShift: Int = 0,
    /** GS Flex's width axis: 100 as designed, less is narrower. */
    val fontWidth: Int = 100,
    /** GS Flex's roundness axis: 0 sharp corners, 100 fully rounded. */
    val fontRoundness: Int = 0,
    /** The name of the font file the user picked for [InterfaceFont.Custom], as the file manager showed it; empty when there is none. */
    val customFontName: String = "",
    /** Counts the times the font file was replaced or removed, so what draws with it loads it again. Not saved. */
    val customFontRevision: Int = 0,
) {
    /** The GS Flex values in force: the custom ones while [customFont] is on, else the [fontPreset]'s. */
    val fontShape: FontShape
        get() = if (customFont) {
            FontShape(fontWeightShift, fontWidth, fontRoundness)
        } else {
            FontShape(fontPreset.weightShift, fontPreset.width, fontPreset.roundness)
        }

    fun availabilityOf(feature: ScheduledFeature): AvailabilityRule = availability[feature] ?: feature.defaultRule()

    /** The time of day a check at a set time is made around (see [updateCheckWhen]). */
    val updateCheckTime: LocalTime
        get() = updateCheckMinuteOfDay.coerceIn(0, 24 * 60 - 1).let { LocalTime.of(it / 60, it % 60) }
}

/** How GS Flex is drawn: the weight shift, width and roundness (see [FontPreset]). */
data class FontShape(val weightShift: Int = 0, val width: Int = 100, val roundness: Int = 0)

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
        val pages = prefs.enum(K_PAGE_ANIMATION, defaults.pageAnimation)
        return AppPrefsState(
            mode = prefs.enum(K_MODE, defaults.mode),
            colorSource = prefs.enum(K_COLOR_SOURCE, defaults.colorSource),
            accent = prefs.enum(K_ACCENT, defaults.accent),
            wallpaperPalette = prefs.enum(K_WALLPAPER_PALETTE, defaults.wallpaperPalette),
            pageAnimation = pages,
            // Sheets used to follow the page style: until one is picked they keep doing what they did.
            sheetAnimation = prefs.enum(K_SHEET_ANIMATION, SheetAnimationType.defaultFor(pages)),
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
            cardCvvEnabled = prefs.getBoolean(K_CARD_CVV, defaults.cardCvvEnabled),
            walletCardsEnabled = prefs.getBoolean(K_WALLET_CARDS, defaults.walletCardsEnabled),
            sheetBlurEnabled = prefs.getBoolean(K_SHEET_BLUR, defaults.sheetBlurEnabled),
            footerBlurEnabled = prefs.getBoolean(K_FOOTER_BLUR, defaults.footerBlurEnabled),
            railCollapsed = prefs.getBoolean(K_RAIL_COLLAPSED, defaults.railCollapsed),
            backButtonStyle = prefs.enum(K_BACK_STYLE, defaults.backButtonStyle),
            bottomControls = prefs.getBoolean(K_BOTTOM_CONTROLS, defaults.bottomControls),
            showFooterLabels = prefs.getBoolean(K_FOOTER_LABELS, defaults.showFooterLabels),
            progressStyle = prefs.enum(K_PROGRESS_STYLE, defaults.progressStyle),
            topSpendingLayout = prefs.enum(K_TOP_SPENDING_LAYOUT, defaults.topSpendingLayout),
            monthControlStyle = prefs.enum(K_MONTH_CONTROL, defaults.monthControlStyle),
            categoryIconStyle = prefs.enum(K_CATEGORY_ICON_STYLE, defaults.categoryIconStyle),
            categoryIconShape = prefs.enum(K_CATEGORY_ICON_SHAPE, defaults.categoryIconShape),
            profileName = prefs.getString(K_PROFILE_NAME, defaults.profileName).orEmpty(),
            profileEmoji = prefs.getString(K_PROFILE_EMOJI, defaults.profileEmoji).orEmpty(),
            profilePhoto = prefs.getString(K_PROFILE_PHOTO, defaults.profilePhoto).orEmpty().takeIf(::isProfilePhotoName).orEmpty(),
            accountButtonTarget = prefs.enum(K_ACCOUNT_BUTTON, defaults.accountButtonTarget),
            updateChecksEnabled = prefs.getBoolean(K_UPDATE_CHECKS, defaults.updateChecksEnabled),
            backgroundUpdates = prefs.enum(K_BACKGROUND_UPDATES, defaults.backgroundUpdates),
            updateOnMobileData = prefs.getBoolean(K_UPDATE_MOBILE_DATA, defaults.updateOnMobileData),
            updateCheckEvery = prefs.enum(K_UPDATE_CHECK_EVERY, defaults.updateCheckEvery),
            updateCheckWhen = prefs.enum(K_UPDATE_CHECK_WHEN, defaults.updateCheckWhen),
            updateCheckMinuteOfDay = prefs.getInt(K_UPDATE_CHECK_MINUTE, defaults.updateCheckMinuteOfDay).coerceIn(0, 24 * 60 - 1),
            updateCheckOnOpen = prefs.getBoolean(K_UPDATE_CHECK_ON_OPEN, defaults.updateCheckOnOpen),
            notificationsEnabled = prefs.getBoolean(K_NOTIFICATIONS, defaults.notificationsEnabled),
            amountsHidden = prefs.getBoolean(K_AMOUNTS_HIDDEN, defaults.amountsHidden),
            interfaceFont = prefs.enum(K_FONT, defaults.interfaceFont),
            fontPreset = prefs.enum(K_FONT_PRESET, defaults.fontPreset),
            customFont = prefs.getBoolean(K_FONT_CUSTOM, defaults.customFont),
            fontWeightShift = prefs.getInt(K_FONT_WEIGHT, defaults.fontWeightShift).coerceIn(FontAxes.WEIGHT_SHIFT),
            fontWidth = prefs.getInt(K_FONT_WIDTH, defaults.fontWidth).coerceIn(FontAxes.WIDTH),
            fontRoundness = prefs.getInt(K_FONT_ROUNDNESS, defaults.fontRoundness).coerceIn(FontAxes.ROUNDNESS),
            customFontName = prefs.getString(K_CUSTOM_FONT_NAME, defaults.customFontName).orEmpty(),
            // Only features the user has configured; the rest read as the feature's own default (NFC card exchange: off) through availabilityOf.
            availability = ScheduledFeature.entries.filter { prefs.contains(it.key("mode")) }.associateWith { prefs.readRule(it) },
        )
    }

    private fun SharedPreferences.readRule(feature: ScheduledFeature): AvailabilityRule {
        val defaults = feature.defaultRule()
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

    fun setWallpaperPalette(palette: WallpaperPalette) =
        change({ copy(wallpaperPalette = palette) }) { putString(K_WALLPAPER_PALETTE, palette.name) }

    /**
     * Picking an accent implies the user wants curated colors, so this also
     * switches the color source to [ColorSource.Custom].
     */
    fun setAccent(accent: AccentColor) = change({ copy(accent = accent, colorSource = ColorSource.Custom) }) {
        putString(K_ACCENT, accent.name)
        putString(K_COLOR_SOURCE, ColorSource.Custom.name)
    }

    fun setPageAnimation(animation: PageAnimationType) {
        // Sheets used to follow the page style. One whose style was never picked is fixed to what it shows now, so changing
        // the pages leaves the sheets as they are - now, and after a restart, when a missing choice is worked out afresh.
        val sheets = state.value.sheetAnimation
        val pinSheets = !prefs.contains(K_SHEET_ANIMATION)
        change({ copy(pageAnimation = animation) }) {
            putString(K_PAGE_ANIMATION, animation.name)
            if (pinSheets) putString(K_SHEET_ANIMATION, sheets.name)
        }
    }

    fun setSheetAnimation(animation: SheetAnimationType) =
        change({ copy(sheetAnimation = animation) }) { putString(K_SHEET_ANIMATION, animation.name) }

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

    /** Only the settings flow that has dealt with the CVVs already saved should call this (see `CardsSettingsFlows`). */
    fun setCardCvvEnabled(enabled: Boolean) =
        change({ copy(cardCvvEnabled = enabled) }) { putBoolean(K_CARD_CVV, enabled) }

    /** Only the settings flow that has erased the cards already saved should call this with false (see `CardsSettingsFlows`). */
    fun setWalletCardsEnabled(enabled: Boolean) =
        change({ copy(walletCardsEnabled = enabled) }) { putBoolean(K_WALLET_CARDS, enabled) }

    fun setSheetBlurEnabled(enabled: Boolean) =
        change({ copy(sheetBlurEnabled = enabled) }) { putBoolean(K_SHEET_BLUR, enabled) }

    fun setFooterBlurEnabled(enabled: Boolean) =
        change({ copy(footerBlurEnabled = enabled) }) { putBoolean(K_FOOTER_BLUR, enabled) }

    fun setRailCollapsed(collapsed: Boolean) =
        change({ copy(railCollapsed = collapsed) }) { putBoolean(K_RAIL_COLLAPSED, collapsed) }

    fun setBackButtonStyle(style: BackButtonStyle) =
        change({ copy(backButtonStyle = style) }) { putString(K_BACK_STYLE, style.name) }

    fun setBottomControls(enabled: Boolean) =
        change({ copy(bottomControls = enabled) }) { putBoolean(K_BOTTOM_CONTROLS, enabled) }

    fun setShowFooterLabels(enabled: Boolean) =
        change({ copy(showFooterLabels = enabled) }) { putBoolean(K_FOOTER_LABELS, enabled) }

    fun setProgressStyle(style: ProgressStyle) =
        change({ copy(progressStyle = style) }) { putString(K_PROGRESS_STYLE, style.name) }

    fun setTopSpendingLayout(layout: TopSpendingLayout) =
        change({ copy(topSpendingLayout = layout) }) { putString(K_TOP_SPENDING_LAYOUT, layout.name) }

    fun setMonthControlStyle(style: MonthControlStyle) =
        change({ copy(monthControlStyle = style) }) { putString(K_MONTH_CONTROL, style.name) }

    fun setCategoryIconStyle(style: CategoryIconStyle) =
        change({ copy(categoryIconStyle = style) }) { putString(K_CATEGORY_ICON_STYLE, style.name) }

    fun setCategoryIconShape(shape: CategoryIconShape) =
        change({ copy(categoryIconShape = shape) }) { putString(K_CATEGORY_ICON_SHAPE, shape.name) }

    /** Saves the profile; [name] is trimmed and capped, [emoji] is kept only when it is exactly one emoji (see `isSingleEmoji`). */
    fun setProfile(name: String, emoji: String) {
        val cleanName = name.trim().take(MAX_PROFILE_NAME)
        val cleanEmoji = emoji.takeIf { com.example.normpix.finance.domain.isSingleEmoji(it) }.orEmpty()
        change({ copy(profileName = cleanName, profileEmoji = cleanEmoji) }) {
            putString(K_PROFILE_NAME, cleanName)
            putString(K_PROFILE_EMOJI, cleanEmoji)
        }
    }

    /** Saves the file name of the user's profile photo ([name] from [ProfilePhotos.save]); anything else - "" included - means no photo. */
    fun setProfilePhoto(name: String) {
        val clean = name.takeIf(::isProfilePhotoName).orEmpty()
        change({ copy(profilePhoto = clean) }) { putString(K_PROFILE_PHOTO, clean) }
    }

    fun setAccountButtonTarget(target: AccountButtonTarget) =
        change({ copy(accountButtonTarget = target) }) { putString(K_ACCOUNT_BUTTON, target.name) }

    fun setAvailability(feature: ScheduledFeature, rule: AvailabilityRule) = change({ copy(availability = availability + (feature to rule)) }) {
        putString(feature.key("mode"), rule.mode.name)
        putString(feature.key("days"), rule.schedule.days.joinToString(",") { it.value.toString() })
        putInt(feature.key("start"), rule.schedule.start.minuteOfDay())
        putInt(feature.key("end"), rule.schedule.end.minuteOfDay())
    }

    fun setUpdateChecksEnabled(enabled: Boolean) =
        change({ copy(updateChecksEnabled = enabled) }) { putBoolean(K_UPDATE_CHECKS, enabled) }

    fun setBackgroundUpdates(mode: BackgroundUpdates) =
        change({ copy(backgroundUpdates = mode) }) { putString(K_BACKGROUND_UPDATES, mode.name) }

    fun setUpdateOnMobileData(allowed: Boolean) =
        change({ copy(updateOnMobileData = allowed) }) { putBoolean(K_UPDATE_MOBILE_DATA, allowed) }

    fun setUpdateCheckEvery(every: UpdateCheckEvery) =
        change({ copy(updateCheckEvery = every) }) { putString(K_UPDATE_CHECK_EVERY, every.name) }

    fun setUpdateCheckWhen(whenToCheck: UpdateCheckWhen) =
        change({ copy(updateCheckWhen = whenToCheck) }) { putString(K_UPDATE_CHECK_WHEN, whenToCheck.name) }

    /** Sets the time of day a check at a set time is made around. */
    fun setUpdateCheckTime(hour: Int, minute: Int) {
        val minuteOfDay = (hour * 60 + minute).coerceIn(0, 24 * 60 - 1)
        change({ copy(updateCheckMinuteOfDay = minuteOfDay) }) { putInt(K_UPDATE_CHECK_MINUTE, minuteOfDay) }
    }

    fun setUpdateCheckOnOpen(enabled: Boolean) =
        change({ copy(updateCheckOnOpen = enabled) }) { putBoolean(K_UPDATE_CHECK_ON_OPEN, enabled) }

    fun setNotificationsEnabled(enabled: Boolean) =
        change({ copy(notificationsEnabled = enabled) }) { putBoolean(K_NOTIFICATIONS, enabled) }

    fun setAmountsHidden(hidden: Boolean) = change({ copy(amountsHidden = hidden) }) { putBoolean(K_AMOUNTS_HIDDEN, hidden) }

    fun setInterfaceFont(font: InterfaceFont) = change({ copy(interfaceFont = font) }) { putString(K_FONT, font.name) }

    /**
     * The font file [CustomFont] just saved is now the interface's font. [name] is what the file was called (as the file
     * manager showed it), kept to show the user which font is in use. Also what a restore of the file calls.
     */
    fun useCustomFont(name: String) {
        val clean = name.trim().take(MAX_CUSTOM_FONT_NAME)
        change({ copy(interfaceFont = InterfaceFont.Custom, customFontName = clean, customFontRevision = customFontRevision + 1) }) {
            putString(K_FONT, InterfaceFont.Custom.name)
            putString(K_CUSTOM_FONT_NAME, clean)
        }
    }

    /** The font file is gone (removed by the user, or lost): its name is forgotten, and GS Flex takes over if the file was in use. */
    fun clearCustomFont() {
        val wasInUse = state.value.interfaceFont == InterfaceFont.Custom
        change({
            copy(
                interfaceFont = if (wasInUse) InterfaceFont.GsFlex else interfaceFont,
                customFontName = "",
                customFontRevision = customFontRevision + 1,
            )
        }) {
            remove(K_CUSTOM_FONT_NAME)
            if (wasInUse) putString(K_FONT, InterfaceFont.GsFlex.name)
        }
    }

    /** Picking a style also leaves the custom values: the style is what is then used. */
    fun setFontPreset(preset: FontPreset) = change({ copy(fontPreset = preset, customFont = false) }) {
        putString(K_FONT_PRESET, preset.name)
        putBoolean(K_FONT_CUSTOM, false)
    }

    /**
     * Turning the custom font on starts it from the style in use, so nothing jumps; the values are the user's to change from
     * there (see [setFontShape]).
     */
    fun setCustomFont(enabled: Boolean) {
        val from = state.value.fontPreset
        change({
            if (enabled) {
                copy(customFont = true, fontWeightShift = from.weightShift, fontWidth = from.width, fontRoundness = from.roundness)
            } else {
                copy(customFont = false)
            }
        }) {
            putBoolean(K_FONT_CUSTOM, enabled)
            if (enabled) {
                putInt(K_FONT_WEIGHT, from.weightShift)
                putInt(K_FONT_WIDTH, from.width)
                putInt(K_FONT_ROUNDNESS, from.roundness)
            }
        }
    }

    /** The custom GS Flex values, each kept inside [FontAxes]. */
    fun setFontShape(weightShift: Int, width: Int, roundness: Int) {
        val w = weightShift.coerceIn(FontAxes.WEIGHT_SHIFT)
        val wd = width.coerceIn(FontAxes.WIDTH)
        val r = roundness.coerceIn(FontAxes.ROUNDNESS)
        change({ copy(fontWeightShift = w, fontWidth = wd, fontRoundness = r) }) {
            putInt(K_FONT_WEIGHT, w)
            putInt(K_FONT_WIDTH, wd)
            putInt(K_FONT_ROUNDNESS, r)
        }
    }

    /** Whether the user was already sent to the system page for exact-alarm access (asked once, then never nagged). */
    var exactAlarmPrompted: Boolean
        get() = prefs.getBoolean(K_EXACT_ALARM_PROMPTED, false)
        set(value) = prefs.edit { putBoolean(K_EXACT_ALARM_PROMPTED, value) }

    /**
     * Restores every preference to its default. Wallet cards and reminders are separate stores and are never affected,
     * and neither is the profile: a name and a picture are who the user is, not a setting.
     */
    fun resetToDefaults() {
        val keep = setOf(K_EXACT_ALARM_PROMPTED, K_PROFILE_NAME, K_PROFILE_EMOJI, K_PROFILE_PHOTO)
        _state.update { AppPrefsState(profileName = it.profileName, profileEmoji = it.profileEmoji, profilePhoto = it.profilePhoto) }
        prefs.edit {
            // Everything except the one-time system-prompt marker and the profile.
            prefs.all.keys.filter { it !in keep }.forEach { remove(it) }
        }
    }

    /**
     * Every preference there is, with its type, for an app backup (see [PrefValue]). It is whatever the file holds, not a list
     * of the ones known today, so a preference added later is carried without anyone listing it - minus [BACKUP_EXCLUDED].
     */
    fun exportAll(): Map<String, PrefValue> {
        val out = LinkedHashMap<String, PrefValue>()
        for ((key, raw) in prefs.all) {
            if (key in BACKUP_EXCLUDED) continue
            PrefValue.of(raw)?.let { out[key] = it }
        }
        return out
    }

    /**
     * Puts a backup's preferences ([exportAll]'s) in place of the ones stored, and has [state] read them again, so the theme,
     * the font and the rest change at once. [inScope] says which keys the backup speaks for (all, unless told): of those, the
     * ones [values] has are written, and the ones it doesn't have go back to their default. Keys out of scope, and
     * [BACKUP_EXCLUDED] ones, are never touched, whatever [values] says.
     *
     * A key the backup doesn't know is written all the same - a newer NormPix may have made it, and keeping it does no harm.
     * A value of another kind than the one stored for that key, or that the app can't read its own preference from, is
     * skipped and the phone keeps what it had (reading a preference as the wrong kind would otherwise throw at every start).
     * Returns how many were skipped.
     *
     * To take an import back, import what [exportAll] gave before it, with the same [inScope].
     */
    fun importAll(values: Map<String, PrefValue>, inScope: (String) -> Boolean = { true }): Int {
        val before = prefs.all
        prefs.edit().apply {
            for (key in before.keys) if (key !in BACKUP_EXCLUDED && inScope(key) && key !in values) remove(key)
        }.apply()
        var skipped = 0
        for ((key, value) in values) {
            if (key in BACKUP_EXCLUDED || !inScope(key)) continue
            val had = PrefValue.of(before[key])
            if (had != null && had.type != value.type) {
                skipped++
                continue
            }
            prefs.edit().also { value.putInto(it, key) }.apply()
            if (!readsCleanly()) {
                prefs.edit().also { editor -> if (had == null) editor.remove(key) else had.putInto(editor, key) }.apply()
                skipped++
            }
        }
        // The font may have been replaced along with the preferences: what draws with it loads it again.
        _state.value = read().copy(customFontRevision = _state.value.customFontRevision + 1)
        return skipped
    }

    /** Whether the app can still read all its preferences (a value of the wrong kind makes [read] throw). */
    private fun readsCleanly(): Boolean = try {
        read()
        true
    } catch (_: RuntimeException) {
        false
    }

    private inline fun change(crossinline update: AppPrefsState.() -> AppPrefsState, write: SharedPreferences.Editor.() -> Unit) {
        _state.update { it.update() }
        prefs.edit().apply(write).apply()
    }

    private companion object {
        const val K_MODE = "theme_mode"
        const val K_COLOR_SOURCE = "color_source"
        const val K_ACCENT = "accent_color"
        const val K_WALLPAPER_PALETTE = "wallpaper_palette"
        const val K_PAGE_ANIMATION = "page_animation"
        const val K_SHEET_ANIMATION = "sheet_animation"
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
        const val K_CARD_CVV = "card_cvv_enabled"
        const val K_WALLET_CARDS = "wallet_cards_enabled"
        const val K_SHEET_BLUR = "sheet_blur_enabled"
        const val K_FOOTER_BLUR = "footer_blur_enabled"
        const val K_RAIL_COLLAPSED = "rail_collapsed"
        const val K_BACK_STYLE = "back_button_style"
        const val K_BOTTOM_CONTROLS = "bottom_controls"
        const val K_FOOTER_LABELS = "show_footer_labels"
        const val K_PROGRESS_STYLE = "progress_style"
        const val K_TOP_SPENDING_LAYOUT = "top_spending_layout"
        const val K_MONTH_CONTROL = "month_control_style"
        const val K_CATEGORY_ICON_STYLE = "category_icon_style"
        const val K_CATEGORY_ICON_SHAPE = "category_icon_shape"
        const val K_PROFILE_NAME = "profile_name"
        const val K_PROFILE_EMOJI = "profile_emoji"
        const val K_PROFILE_PHOTO = "profile_photo"
        const val K_ACCOUNT_BUTTON = "account_button_target"
        const val K_UPDATE_CHECKS = "update_checks_enabled"
        const val K_BACKGROUND_UPDATES = "background_updates"
        const val K_UPDATE_MOBILE_DATA = "update_on_mobile_data"
        const val K_UPDATE_CHECK_EVERY = "update_check_every"
        const val K_UPDATE_CHECK_WHEN = "update_check_when"
        const val K_UPDATE_CHECK_MINUTE = "update_check_minute_of_day"
        const val K_UPDATE_CHECK_ON_OPEN = "update_check_on_open"
        const val K_NOTIFICATIONS = "notifications_enabled"
        const val K_AMOUNTS_HIDDEN = "amounts_hidden"
        const val K_FONT = "interface_font"
        const val K_FONT_PRESET = "font_preset"
        const val K_FONT_CUSTOM = "font_custom"
        const val K_FONT_WEIGHT = "font_weight_shift"
        const val K_FONT_WIDTH = "font_width"
        const val K_FONT_ROUNDNESS = "font_roundness"

        /** The file name of the font the user picked (the file itself is kept by [CustomFont]); backed up with it. */
        const val K_CUSTOM_FONT_NAME = "custom_font_name"

        /**
         * The preferences an app backup neither carries nor changes (see [exportAll] and [importAll]): they describe this phone,
         * or guard it, and a file must not be able to set them.
         * - [K_EXACT_ALARM_PROMPTED]: whether this phone's user was sent to Android's exact-alarm page.
         * - [K_DUMMY_DATA]: sample-data mode is a way of looking at the app right now, not a setting to bring back.
         * - [K_WALLET_LOCK], [K_LOCK_SCOPE], [K_LOCK_TIMEOUT]: the biometric lock. Turning it on has to prove this phone can
         *   unlock, and turning it off or loosening it has to prove it is the owner (see Security & privacy), which a restored
         *   file would get around.
         * - [K_NFC_REVEAL], [K_NFC_REVEAL_REGISTERED]: the NFC gate for revealing a card. It needs this phone's NFC and the tags
         *   registered here (kept apart, under a key that never leaves this phone), and it is a lock like the one above.
         * - [K_CARD_CVV], [K_WALLET_CARDS]: whether this phone keeps CVVs and cards at all. Each is tied to what is stored here
         *   (turning one off deletes it, after asking), so a file must neither switch it off over saved cards nor back on.
         * Update records and the card vault are in files of their own, not in these preferences.
         */
        val BACKUP_EXCLUDED: Set<String> = setOf(
            K_EXACT_ALARM_PROMPTED,
            K_DUMMY_DATA,
            K_WALLET_LOCK,
            K_LOCK_SCOPE,
            K_LOCK_TIMEOUT,
            K_NFC_REVEAL,
            K_NFC_REVEAL_REGISTERED,
            K_CARD_CVV,
            K_WALLET_CARDS,
        )

        fun ScheduledFeature.key(field: String) = "availability_${name.lowercase()}_$field"
        fun LocalTime.minuteOfDay() = hour * 60 + minute
        fun Int.toLocalTime(): LocalTime = coerceIn(0, 24 * 60 - 1).let { LocalTime.of(it / 60, it % 60) }

        inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String, default: T): T {
            val name = getString(key, null) ?: return default
            return enumValues<T>().firstOrNull { it.name == name } ?: default
        }
    }
}
