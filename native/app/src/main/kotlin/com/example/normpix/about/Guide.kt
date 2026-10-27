package com.example.normpix.about

/**
 * Where a guide topic's button leads: the screen the topic is about. Which screen each one opens is decided where the guide is
 * drawn (`GuidePage`); the label is what the button says.
 */
enum class GuideLink(val label: String) {
    Home("Open Home"),
    Activity("Open Activity"),
    Plans("Open Plans"),
    Insights("Open Insights"),
    Wallet("Open Wallet"),
    Colors("Open Colors"),
    DataBackup("Open Data & Backup"),
    Security("Open Security & Lock"),
    Availability("Open Availability & Schedules"),
    Widgets("Open Widgets"),
}

/**
 * One thing NormPix does, explained on the About page: what it is for ([summary]), how to do it ([steps], in order) and what is
 * worth knowing ([tips]). The picture above them is drawn from the topic (`GuideArt`), in the colors the app is wearing.
 *
 * [hue] is an index into the category palette, the way each settings group has a color on the Settings list, so a topic keeps
 * its color from the About page into its own.
 *
 * The name is what a route and a saved state carry, so a name is never changed (see [named]).
 */
enum class GuideTopic(
    val title: String,
    val summary: String,
    val hue: Int,
    val steps: List<String>,
    val tips: List<String>,
    val link: GuideLink? = null,
) {
    AddTransaction(
        title = "Add a transaction",
        summary = "Record an expense, income or a transfer in a few taps.",
        hue = 1,
        steps = listOf(
            "Tap the add button in the bar at the bottom of Home, Activity or Insights. On Home and Activity its menu also has Add income.",
            "Type the amount, then choose a category and the account the money moved through.",
            "Add a payee, a note, the person it was for, or a picture of the bill if you like.",
            "Switch the type to Transfer to move money between two of your accounts, or to pay a credit card's bill.",
            "Save. Home, Insights and your budgets update straight away.",
        ),
        tips = listOf(
            "Deleted one by mistake? Undo stays on screen for a few seconds.",
            "A picture you attach can be read again to fill the form in.",
        ),
        link = GuideLink.Home,
    ),
    ShareReceipt(
        title = "Add from a receipt",
        summary = "Share a payment screenshot or receipt with NormPix and it fills the form in for you.",
        hue = 5,
        steps = listOf(
            "In Google Pay, PhonePe or your bank app, open the receipt or take a screenshot of it.",
            "Tap Share, then choose NormPix.",
            "NormPix reads the picture on this phone and fills in the amount, the payee, the account and the category.",
            "Check what it found, change anything that is off, and save.",
        ),
        tips = listOf(
            "The picture is read on the phone. Nothing is uploaded, and NormPix never reads your SMS or notifications.",
            "Shared text works the same way as a shared picture.",
        ),
    ),
    Home(
        title = "Home",
        summary = "Your month at a glance, and what needs you today.",
        hue = 3,
        steps = listOf(
            "The top card shows what came in, what went out and your total balance.",
            "Needs attention gathers overdue reminders, budgets that are nearly used or over, cards about to expire and an update waiting to install.",
            "Top spending shows where most of the month went.",
            "Next 7 days lists what is coming up. Recent shows your latest transactions; See all opens the whole history.",
        ),
        tips = listOf(
            "Press and hold a stack of cards or banners to spread it out.",
            "Choose how Top spending is laid out in Settings > Appearance > Layouts.",
        ),
        link = GuideLink.Home,
    ),
    Activity(
        title = "Activity",
        summary = "Every transaction, searchable and easy to narrow down.",
        hue = 2,
        steps = listOf(
            "Open Plans & Activity and stay on Activity.",
            "Move between months, or choose All time or a range of days.",
            "Filter by type, account, category, person, amount or dates, or search.",
            "Tap a row for its details. Swipe it to edit or delete.",
        ),
        tips = listOf(
            "Choose which swipe actions you want in Settings > Finance.",
            "A repeating payment is recorded by itself on its day, and shows up here like any other.",
        ),
        link = GuideLink.Activity,
    ),
    Plans(
        title = "Budgets, recurring and reminders",
        summary = "Plan the month ahead: limits to stay under, payments that repeat and things not to forget.",
        hue = 4,
        steps = listOf(
            "Open Plans & Activity, then switch to Plans.",
            "Budgets: set an overall monthly limit and one for each category. A change applies from that month on; earlier months keep theirs.",
            "Recurring: add a payment that repeats, and NormPix records it on its day.",
            "Reminders: one-off or repeating, with snooze, and you can complete one from its notification.",
        ),
        tips = listOf(
            "A reminder can be tied to a transaction, a repeating payment, an account or a card's bill.",
            "Overdue reminders put a badge on the Plans & Activity tab.",
        ),
        link = GuideLink.Plans,
    ),
    Insights(
        title = "Insights",
        summary = "Where the money went, and what changed since last month.",
        hue = 6,
        steps = listOf(
            "Pick a month, or choose your own range of days.",
            "The snapshot shows income, spending and how the month is trending.",
            "Where your money went breaks spending down by category; What changed compares it with the month before.",
            "See what you spend on each person, and the month day by day.",
        ),
        tips = listOf(
            "Add a person to an expense when you record it, and Insights totals them for you.",
        ),
        link = GuideLink.Insights,
    ),
    Wallet(
        title = "Accounts and cards",
        summary = "Cash, banks, wallets and saved cards, kept private on this phone.",
        hue = 7,
        steps = listOf(
            "Open Wallet to see your accounts and your saved cards.",
            "Add account for cash, a bank, a card, a wallet or savings. Add card to save a card. Settings > Banks lists every bank NormPix knows, with its logo.",
            "A card's number and CVV stay hidden until you unlock with your fingerprint, face or screen lock, or tap your own NFC tag.",
            "Tap a detail to copy it. The clipboard is cleared after the time you set.",
        ),
        tips = listOf(
            "Scan the card number with the camera. It is read on the phone.",
            "Accounts and saved cards lean a little as you move your phone (Settings > Appearance > Effects > Card tilt).",
            "Don't want cards in NormPix? Turn off Store cards in Settings > Wallet & Cards and the Wallet shows accounts only.",
        ),
        link = GuideLink.Wallet,
    ),
    CardExchange(
        title = "Send a card over NFC",
        summary = "Hand a saved card to another phone running NormPix by holding them together.",
        hue = 0,
        steps = listOf(
            "NFC card exchange is off until you turn it on: Settings > Availability & Schedules. Choose Always on, or On a schedule to allow it only at set hours.",
            "Make sure NFC is on in your phone's own settings too. With either one off, NormPix hides every NFC button.",
            "Open the card and tap Share via NFC, then hold the two phones back to back.",
            "On the other phone, open the Wallet and choose NFC Card Exchange.",
        ),
        tips = listOf(
            "Both phones need NormPix.",
            "On a schedule, the buttons appear and disappear by themselves as the hours start and end.",
        ),
        link = GuideLink.Availability,
    ),
    Widgets(
        title = "Home-screen widgets",
        summary = "Seven widgets for your home screen: five clocks, your recent transactions and a quick add.",
        hue = 2,
        steps = listOf(
            "Open Settings > Widgets, tap a pill to see that widget, then Add to home screen. Or press and hold an empty spot on your home screen, choose Widgets and find NormPix.",
            "Cookie clock: a clock in a cookie shape, in four styles - analog, analog with numbers, plain (only the hands), or digital with the date.",
            "Watch dial: the time on a face of two rings, the hour in the middle. The outer ring turns with the seconds and the inner one steps with the minute.",
            "Recent transactions: your latest entries, with a button to add one.",
            "Add transaction: one tap to start an expense, an income or a transfer.",
        ),
        tips = listOf(
            "The clocks keep time on their own and open your phone's clock app when tapped.",
            "On Android 12 and later the widgets wear your wallpaper's colors, and the analog clocks have a second hand.",
            "Press and hold a clock on your home screen and choose Widget settings: make it transparent, or have the plain digital clock - for good, or when you make the widget small.",
            "While NormPix is locked, the widgets show nothing of your ledger.",
        ),
        link = GuideLink.Widgets,
    ),
    LookAndFeel(
        title = "Colors and themes",
        summary = "Make NormPix look like yours: light or dark, your wallpaper's colors, or a palette you pick.",
        hue = 3,
        steps = listOf(
            "Open Settings > Appearance and choose Light, Dark or the system's theme.",
            "In Colors, choose Wallpaper colors (Android 12 and later) or Other colors. Each palette shows the colors it gives the app: primary above, container and accent below.",
            "Choose Pure for black and white with greys between: black surfaces in dark, white in light.",
            "Fonts, icon shapes, transitions and the app icon each have a screen of their own in Appearance.",
        ),
        tips = listOf(
            "Every theme works in light and dark, and the pictures in this guide follow whichever you pick.",
        ),
        link = GuideLink.Colors,
    ),
    Backup(
        title = "Back up and move phones",
        summary = "Keep a copy of everything, and set NormPix up again on another phone.",
        hue = 5,
        steps = listOf(
            "Open Settings > Data & Backup.",
            "Choose Everything for a .zip with your data, pictures, settings and reminders, or Full backup, with pictures for your finance data and the bills and receipts attached to it.",
            "Keep the file somewhere safe. It is saved only where you choose.",
            "On the new phone, install NormPix and choose Restore from backup. It checks the file first and asks before replacing anything.",
        ),
        tips = listOf(
            "Saved cards are left out unless you choose to include them, and then they are locked with a password.",
            "Check and recalculate works every balance out again from your entries.",
            "Sample data lets you explore NormPix without entering your own.",
        ),
        link = GuideLink.DataBackup,
    ),
    Privacy(
        title = "Lock and privacy",
        summary = "Everything stays on this phone, behind the lock you choose.",
        hue = 0,
        steps = listOf(
            "Open Settings > Security & Lock and turn on Require unlock.",
            "Choose the lock's scope: the Wallet only, or the entire app. Auto-lock decides how soon it locks again.",
            "To keep card numbers hidden even after unlocking, turn on Tap an NFC tag to reveal and register your tag.",
            "NormPix connects only to GitHub, to check for and download its own updates.",
        ),
        tips = listOf(
            "Card details are encrypted, and the app's screen is protected from screenshots.",
            "There is no account and no analytics.",
        ),
        link = GuideLink.Security,
    ),
    ;

    companion object {
        /** The topic a route's or a saved state's [name] stands for, or null for a name that is none (a stale link). */
        fun named(name: String?): GuideTopic? = entries.firstOrNull { it.name == name }
    }
}
