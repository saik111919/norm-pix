package com.normpix.cashflow

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CurrencyPound
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

// ── Data ──

data class Currency(
    val code: String,
    val symbol: String,
    val label: String,
    val locale: Locale,
    val icon: ImageVector
)

val currencies = listOf(
    Currency("INR", "₹", "Indian Rupee", Locale("en", "IN"), Icons.Rounded.CurrencyRupee),
    Currency("USD", "$", "US Dollar", Locale.US, Icons.Rounded.AttachMoney),
    Currency("EUR", "€", "Euro", Locale.FRANCE, Icons.Rounded.Euro),
    Currency("GBP", "£", "British Pound", Locale.UK, Icons.Rounded.CurrencyPound),
    Currency("AED", "د.إ", "UAE Dirham", Locale("ar", "AE"), Icons.Rounded.Payments),
)

data class Txn(
    val id: String,
    val merchant: String,
    val amount: Double, // negative = expense
    val cat: String,
    val account: String,
    val whenLabel: String,
    val day: String, // Today / Yesterday / Earlier
    val icon: ImageVector,
    val auto: Boolean,
    val sub: String? = null,
)

data class InboxMsg(
    val id: String,
    val sender: String,
    val conf: String, // high / low
    val dup: Boolean,
    val sms: String,
    val merchant: String,
    val amount: Double,
    val cat: String,
    val account: String,
    val whenLabel: String,
    val icon: ImageVector,
)

data class Sub(
    val name: String,
    val icon: ImageVector,
    val amt: Double,
    val due: String,
    val cycle: String,
)

/** [senders] holds SMS sender IDs (e.g. "VM-HDFCBK") for SMS-based banks, or a notification
 * package name (e.g. "com.phonepe.app") for apps captured via [BankNotificationListener] —
 * [parseSms] treats both the same way: a substring to match against the message's origin. */
data class Bank(
    val name: String,
    val senders: String,
    val parsed: String,
    val on: Boolean,
    val formats: List<String>,
)

data class Account(
    val name: String,
    val mask: String?,
    val type: String, // Savings | Credit card | Cash
    val balance: Double, // may be negative (e.g. credit card)
    val icon: ImageVector,
    val on: Boolean,
)

data class SmartRule(val id: String, val match: String, val cat: String)

data class Webhook(val id: String, val url: String, val event: String, val on: Boolean)

data class NotifPrefs(
    val budget: Boolean = true,
    val weekly: Boolean = true,
    val bills: Boolean = true,
    val sms: Boolean = true,
)

/** A scheduled or instant notification NormPix can fire (design's "Reminders" screen). */
data class Reminder(
    val id: String,
    val type: String, // daily_summary | weekly_summary | budget_alert | bill_due | custom
    val icon: ImageVector,
    val title: String,
    val on: Boolean,
    val time: String?, // "HH:mm" 24h; null when freq == "instant"
    val freq: String, // daily | weekly | custom | instant | once
    val days: List<String> = emptyList(), // "Mon".."Sun", for freq == weekly/custom
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val priority: String = "normal", // silent | normal | urgent
    val date: String? = null, // "yyyy-MM-dd", for a one-off custom reminder
    val linkedTxnId: String? = null, // for a "review a spend" custom reminder
)

/**
 * Derives a [Reminder]'s icon from its [type] (and, for a custom reminder, whether it links a
 * transaction) rather than storing the [ImageVector] itself — reminders are persisted to
 * SharedPreferences as JSON so [android.app.AlarmManager] alarms survive process death and
 * reboot, and [ImageVector] isn't serializable.
 */
fun reminderIcon(type: String, hasLinkedTxn: Boolean): ImageVector = when (type) {
    "daily_summary" -> Icons.Rounded.Today
    "weekly_summary" -> Icons.Rounded.Summarize
    "budget_alert" -> Icons.Rounded.NotificationsActive
    "bill_due" -> Icons.Rounded.EventRepeat
    else -> if (hasLinkedTxn) Icons.AutoMirrored.Rounded.ReceiptLong else Icons.Rounded.Event
}

fun sampleReminders() = mutableListOf(
    Reminder("r_daily", "daily_summary", reminderIcon("daily_summary", false), "Daily spend summary", true, "21:00", "daily", emptyList(), true, true, "normal"),
    Reminder("r_weekly", "weekly_summary", reminderIcon("weekly_summary", false), "Weekly summary", true, "09:00", "weekly", listOf("Mon"), true, false, "normal"),
    Reminder("r_budget", "budget_alert", reminderIcon("budget_alert", false), "Budget cap warnings", true, null, "instant", emptyList(), true, true, "urgent"),
    Reminder("r_bills", "bill_due", reminderIcon("bill_due", false), "Bill due reminders", true, "10:00", "custom", listOf("Mon", "Thu"), false, true, "normal"),
)

/** An app NormPix can show full-screen reminder alarms over, or keep to a plain notification. */
data class AppInfo(val name: String, val icon: ImageVector, val category: String) // category: call | other

val appsList = listOf(
    AppInfo("Phone", Icons.Rounded.Call, "call"),
    AppInfo("WhatsApp Calls", Icons.Rounded.PhoneInTalk, "call"),
    AppInfo("Google Meet", Icons.Rounded.VideoCall, "call"),
    AppInfo("Zoom", Icons.Rounded.VideoCall, "call"),
    AppInfo("Instagram", Icons.Rounded.Image, "other"),
    AppInfo("Chrome", Icons.Rounded.Language, "other"),
    AppInfo("Gmail", Icons.Rounded.Mail, "other"),
    AppInfo("Spotify", Icons.Rounded.MusicNote, "other"),
    AppInfo("Maps", Icons.Rounded.Map, "other"),
    AppInfo("Camera", Icons.Rounded.PhotoCamera, "other"),
)

/** Default per-app behavior: calling apps stay notification-only so an alarm never interrupts a call. */
fun defaultAppExceptions(): Map<String, Boolean> = appsList.associate { it.name to (it.category != "call") }

/** A synced SMS shown in the "Synced messages" history (already accepted into Transactions, or ignored). */
data class SyncHistoryEntry(
    val id: String,
    val sender: String,
    val sms: String,
    val merchant: String,
    val amount: Double?,
    val cat: String?,
    val whenLabel: String,
    val icon: ImageVector,
    val status: String, // added | ignored
)

fun sampleSyncHistory() = mutableListOf(
    SyncHistoryEntry(
        "h1", "VM-SBIUPI", "Dear UPI user A/C X8890 debited by Rs520.0 on date 30Jun26 trf to ZOMATO Refno 887744221.",
        "Zomato", 520.0, "Food", "Mon, 8:15 PM", Icons.Rounded.Restaurant, "added",
    ),
    SyncHistoryEntry(
        "h2", "VM-HDFCBK", "Rs.1,240.00 debited from a/c **4521 on 29-06-26 to VPA bigbasket@icici (UPI Ref 552013341).",
        "BigBasket", 1240.0, "Food", "Sun, 11:04 AM", Icons.Rounded.ShoppingCart, "added",
    ),
    SyncHistoryEntry(
        "h3", "VM-ICICIB", "INR 649.00 spent using ICICI Bank Card XX2210 on 28-Jun-26 on NETFLIX.",
        "Netflix", 649.0, "Bills", "Sat, 8:00 AM", Icons.Rounded.Subscriptions, "added",
    ),
    SyncHistoryEntry(
        "h4", "AD-PROMOB", "Get 20% cashback on your next HDFC card transaction. T&C apply.",
        "Promotional SMS", null, null, "Fri, 2:30 PM", Icons.Rounded.Block, "ignored",
    ),
)

// ── Icon + formatting helpers ──

val catIcons: Map<String, ImageVector> = mapOf(
    "Food" to Icons.Rounded.LunchDining,
    "Groceries" to Icons.Rounded.ShoppingCart,
    "Shopping" to Icons.Rounded.ShoppingBag,
    "Transport" to Icons.Rounded.LocalTaxi,
    "Bills" to Icons.Rounded.Bolt,
    "Income" to Icons.Rounded.Savings,
    "Transfer" to Icons.Rounded.SwapHoriz,
)

fun iconForCat(cat: String): ImageVector = catIcons[cat] ?: Icons.Rounded.Receipt

/** Formats a number with currency symbol and locale grouping. */
fun fmt(n: Double, currency: Currency = currencies[0]): String {
    val formatter = NumberFormat.getNumberInstance(currency.locale).apply {
        maximumFractionDigits = 2
    }
    return currency.symbol + formatter.format(abs(n))
}

/** Formats a number without symbol for input fields. */
fun groupINR(n: Double, locale: Locale = Locale("en", "IN")): String {
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 2
    }
    return formatter.format(n)
}

// ── Calculator expression evaluation (ported from the design) ──

private val OPS = charArrayOf('+', '-', '×', '÷')

fun evalAmount(str: String): Double {
    val toks = Regex("([0-9.]+)|[+\\-×÷]").findAll(str).map { it.value }.toList()
    if (toks.isEmpty()) return 0.0
    val vals = mutableListOf<Double>()
    val ops = mutableListOf<Char>()
    var expectNum = true
    for (tk in toks) {
        val isOp = tk.length == 1 && tk[0] in OPS
        if (isOp && !expectNum) { ops.add(tk[0]); expectNum = true }
        else if (expectNum && !isOp) { vals.add(tk.toDoubleOrNull() ?: 0.0); expectNum = false }
    }
    var i = 0
    while (i < ops.size) {
        if (ops[i] == '×' || ops[i] == '÷') {
            val b = vals.getOrElse(i + 1) { 1.0 }
            vals[i] = if (ops[i] == '×') vals[i] * b else if (b != 0.0) vals[i] / b else 0.0
            if (i + 1 < vals.size) vals.removeAt(i + 1)
            ops.removeAt(i)
        } else i++
    }
    var res = vals.getOrElse(0) { 0.0 }
    for (j in ops.indices) {
        val b = vals.getOrElse(j + 1) { 0.0 }
        res = if (ops[j] == '+') res + b else res - b
    }
    return (res * 100).roundToLong() / 100.0
}

/** Applies a keypad press to the current amount string (ported from the design). */
fun keypad(current: String, k: String): String {
    var a = current
    when {
        k == "back" -> a = a.dropLast(1)
        k.length == 1 && k[0] in OPS -> {
            if (a.isEmpty() || a.last() in OPS || a.last() == '.') return a
            a += k
        }
        k == "." -> {
            val seg = a.split('+', '-', '×', '÷').last()
            if (!seg.contains('.')) a += if (seg.isEmpty()) "0." else "."
        }
        else -> {
            val seg = a.split('+', '-', '×', '÷').last()
            if (seg.contains('.') && seg.substringAfter('.').length >= 2) return a
            if (a.length >= 18) return a
            a += k
        }
    }
    return a
}

// ── Sample data (from the design) ──

fun sampleSubs() = mutableListOf(
    Sub("Netflix", Icons.Rounded.Subscriptions, 649.0, "due Friday", "Monthly"),
    Sub("Spotify", Icons.Rounded.MusicNote, 119.0, "due 12 Jul", "Monthly"),
    Sub("iCloud+", Icons.Rounded.Cloud, 75.0, "due 18 Jul", "Monthly"),
    Sub("Gym membership", Icons.Rounded.FitnessCenter, 1301.0, "due 25 Jul", "Monthly"),
)

fun sampleAccounts() = mutableListOf(
    Account("HDFC Bank", "••4521", "Savings", 128430.0, Icons.Rounded.AccountBalance, true),
    Account("SBI", "••8890", "Savings", 42110.0, Icons.Rounded.AccountBalance, true),
    Account("ICICI Bank", "••2210", "Credit card", -8420.0, Icons.Rounded.CreditCard, true),
    Account("Cash wallet", null, "Cash", 1200.0, Icons.Rounded.Payments, true),
)

fun sampleSmartRules() = mutableListOf(
    SmartRule("r1", "swiggy, zomato", "Food"),
    SmartRule("r2", "uber, ola, rapido", "Transport"),
    SmartRule("r3", "amazon, myntra, flipkart", "Shopping"),
)

fun sampleWebhooks() = mutableListOf(
    Webhook("w1", "https://hooks.example.com/normpix", "transaction.created", true),
)

val langList = listOf("English", "हिन्दी (Hindi)", "বাংলা (Bengali)", "தமிழ் (Tamil)", "मराठी (Marathi)", "ગુજરાતી (Gujarati)")

fun sampleBanks() = mutableListOf(
    Bank(
        "HDFC Bank", "VM-HDFCBK · AD-HDFCBK", "128 parsed", true,
        listOf(
            "Rs.{amount} debited from a/c **{last4} on {date} to VPA {vpa} (UPI Ref {ref})",
            "INR {amount} spent using HDFC Card XX{last4} at {merchant} on {date}",
        ),
    ),
    Bank(
        "SBI", "AD-SBIUPI · VM-SBIINB", "86 parsed", true,
        listOf("Dear UPI user A/C X{last4} debited by Rs{amount} on {date} trf to {merchant} Refno {ref}"),
    ),
    Bank(
        "ICICI Bank", "VM-ICICIB", "54 parsed", true,
        listOf("INR {amount} spent using ICICI Bank Card XX{last4} on {date} on {merchant}. Avl Lmt: INR {limit}"),
    ),
    Bank(
        "Axis Bank", "AD-AXISBK", "12 parsed", true,
        listOf("INR {amount} debited A/c no. XX{last4} {date} UPI/P2M/{ref}/{merchant}"),
    ),
    Bank(
        "Paytm Payments Bank", "VM-PYTMPB", "8 parsed", false,
        listOf("Rs.{amount} paid to {merchant} from Paytm Wallet. Order {ref}"),
    ),
    // Captured via BankNotificationListener, not SMS — these apps often push a notification
    // with no SMS at all for a UPI payment. `senders` holds the app's package name instead of
    // an SMS sender ID (parseSms treats both the same way). The formats below are a best-effort
    // starting point, not verified against live notifications — edit them here (Settings > Banks)
    // to match what you actually see once notification access is granted.
    Bank(
        "Google Pay", "com.google.android.apps.nbu.paisa.user", "0 parsed", true,
        listOf(
            "₹{amount} paid to {merchant}",
            "You paid {merchant} ₹{amount}",
        ),
    ),
    Bank(
        "PhonePe", "com.phonepe.app", "0 parsed", true,
        listOf(
            "Payment of ₹{amount} to {merchant} was successful",
            "You paid ₹{amount} to {merchant}",
        ),
    ),
    Bank(
        "Paytm", "net.one97.paytm", "0 parsed", true,
        listOf(
            "Paid ₹{amount} to {merchant}",
            "Payment of ₹{amount} to {merchant} is successful",
        ),
    ),
)

fun sampleInbox() = mutableListOf(
    InboxMsg(
        "i1", "VM-HDFCBK", "high", false,
        "Rs.385.00 debited from a/c **4521 on 02-07-26 to VPA swiggy@icici (UPI Ref 519234117). Not you? Call 18002586161.",
        "Swiggy", 385.0, "Food", "HDFC ••4521", "Today, 7:42 PM", Icons.Rounded.LunchDining,
    ),
    InboxMsg(
        "i2", "AD-SBIUPI", "high", true,
        "Dear UPI user A/C X8890 debited by Rs212.0 on date 02Jul26 trf to UBER INDIA Refno 654301998877.",
        "Uber", 212.0, "Transport", "SBI ••8890", "Today, 6:10 PM", Icons.Rounded.LocalTaxi,
    ),
    InboxMsg(
        "i3", "VM-ICICIB", "low", false,
        "INR 2,499.00 spent using ICICI Bank Card XX2210 on 02-Jul-26 on AMZN. Avl Lmt: INR 1,42,501.",
        "AMZN (Amazon?)", 2499.0, "Shopping", "ICICI ••2210", "Today, 3:05 PM", Icons.Rounded.ShoppingBag,
    ),
)

fun sampleTxns() = mutableListOf(
    Txn("t1", "Swiggy", -385.0, "Food", "HDFC ••4521 · UPI", "7:42 PM", "Today", Icons.Rounded.LunchDining, true),
    Txn("t2", "Uber", -212.0, "Transport", "HDFC ••4521 · UPI", "6:10 PM", "Today", Icons.Rounded.LocalTaxi, true),
    Txn("t3", "Salary — Acme Corp", 86000.0, "Income", "SBI ••8890 · NEFT", "9:00 AM", "Today", Icons.Rounded.Savings, true),
    Txn("t4", "Amazon", -1499.0, "Shopping", "ICICI ••2210 · Card", "8:21 PM", "Yesterday", Icons.Rounded.ShoppingBag, true),
    Txn("t5", "BigBasket", -1240.0, "Food", "HDFC ••4521 · UPI", "11:04 AM", "Yesterday", Icons.Rounded.ShoppingCart, true),
    Txn("t6", "Jio Recharge", -299.0, "Bills", "HDFC ••4521 · UPI", "9:45 AM", "Yesterday", Icons.Rounded.Bolt, true),
    Txn("t7", "Netflix", -649.0, "Bills", "ICICI ••2210 · Card", "Mon", "Earlier", Icons.Rounded.Subscriptions, true),
    Txn("t8", "Chai (cash)", -40.0, "Food", "Cash wallet", "Mon", "Earlier", Icons.Rounded.LocalCafe, false),
    Txn("t9", "Zomato", -520.0, "Food", "HDFC ••4521 · UPI", "Sun", "Earlier", Icons.Rounded.Restaurant, true),
)

/** Static metadata for the category detail drill-down — one entry per `Txn.cat` key (design section 9). */
data class CatMeta(val title: String, val icon: ImageVector)

val catMeta: Map<String, CatMeta> = mapOf(
    "Food" to CatMeta("Food & dining", Icons.Rounded.LunchDining),
    "Shopping" to CatMeta("Shopping", Icons.Rounded.ShoppingBag),
    "Transport" to CatMeta("Transport", Icons.Rounded.LocalTaxi),
    "Bills" to CatMeta("Bills & utilities", Icons.Rounded.Bolt),
    "Income" to CatMeta("Income", Icons.Rounded.Savings),
)

/** Display name shown for a `Txn.cat` key in budget lists (design's category budgets section). */
val catDisplayName: Map<String, String> = catMeta.mapValues { it.value.title }

/** Sum of this month's spend in [cat] (expenses only, i.e. negative amounts). */
fun categorySpent(txns: List<Txn>, cat: String): Double =
    txns.filter { it.cat == cat && it.amount < 0 }.sumOf { -it.amount }

/** Which relative-day bucket a picked date falls into, for grouping in the transaction list. */
fun dayBucketFor(millis: Long): String {
    fun sameDay(a: java.util.Calendar, b: java.util.Calendar) =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    val picked = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val today = java.util.Calendar.getInstance()
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DATE, -1) }
    return when {
        sameDay(picked, today) -> "Today"
        sameDay(picked, yesterday) -> "Yesterday"
        else -> "Earlier"
    }
}

/** Short "5 Jul" label for a picked date. */
fun formatShortDate(millis: Long): String =
    java.text.SimpleDateFormat("d MMM", Locale.getDefault()).format(java.util.Date(millis))

/** Formats a number compactly with a "k"/"L" suffix, e.g. 60000 -> "₹60k" (design's budget summary text). */
fun fmtCompact(n: Double, currency: Currency = currencies[0]): String {
    val v = n / 1000.0
    val s = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
    return currency.symbol + s + "k"
}

/** Icon choices offered when creating a new category (design's 28-icon picker). */
val addCategoryIcons = listOf(
    Icons.Rounded.LunchDining, Icons.Rounded.LocalCafe, Icons.Rounded.ShoppingBag, Icons.Rounded.ShoppingCart,
    Icons.Rounded.LocalTaxi, Icons.Rounded.Flight, Icons.Rounded.DirectionsCar, Icons.Rounded.Bolt,
    Icons.Rounded.Wifi, Icons.Rounded.HomeWork, Icons.Rounded.Medication, Icons.Rounded.FitnessCenter,
    Icons.Rounded.Spa, Icons.Rounded.School, Icons.Rounded.Movie, Icons.Rounded.SportsEsports,
    Icons.Rounded.Subscriptions, Icons.Rounded.Pets, Icons.Rounded.ChildCare, Icons.Rounded.CardGiftcard,
    Icons.Rounded.VolunteerActivism, Icons.Rounded.Devices, Icons.Rounded.HealthAndSafety, Icons.Rounded.Savings,
    Icons.Rounded.Category, Icons.Rounded.Label, Icons.Rounded.Star, Icons.Rounded.Extension,
)

data class CatInfo(val name: String, val icon: ImageVector, val subtitle: String)

val catList = listOf(
    CatInfo("Food & dining", Icons.Rounded.LunchDining, "₹13,540 this month"),
    CatInfo("Shopping", Icons.Rounded.ShoppingBag, "₹10,220 this month"),
    CatInfo("Transport", Icons.Rounded.LocalTaxi, "₹7,610 this month"),
    CatInfo("Bills & utilities", Icons.Rounded.Bolt, "₹5,890 this month"),
    CatInfo("Groceries", Icons.Rounded.ShoppingCart, "₹3,120 this month"),
    CatInfo("Entertainment", Icons.Rounded.Movie, "₹1,240 this month"),
    CatInfo("Health", Icons.Rounded.Medication, "₹680 this month"),
    CatInfo("Travel", Icons.Rounded.Flight, "—"),
    CatInfo("Education", Icons.Rounded.School, "—"),
    CatInfo("Rent", Icons.Rounded.HomeWork, "—"),
    CatInfo("Chai & snacks", Icons.Rounded.LocalCafe, "₹410 · custom"),
    CatInfo("Gifts", Icons.Rounded.CardGiftcard, "— · custom"),
)

val catSubMap = mapOf(
    "Food & dining" to listOf("Restaurants", "Delivery", "Snacks"),
    "Shopping" to listOf("Clothing", "Electronics", "Home"),
    "Transport" to listOf("Cab", "Fuel", "Metro"),
    "Bills & utilities" to listOf("Electricity", "Mobile", "Internet"),
    "Groceries" to listOf("Vegetables", "Staples"),
    "Entertainment" to listOf("Streaming", "Movies"),
)

val addSubCats = mapOf(
    "Food" to listOf("Restaurants", "Delivery", "Snacks"),
    "Shopping" to listOf("Clothing", "Electronics", "Home"),
    "Transport" to listOf("Cab", "Fuel", "Metro"),
    "Bills" to listOf("Electricity", "Mobile", "Internet"),
)

val accounts = listOf("HDFC ••4521", "SBI ••8890", "ICICI ••2210", "Cash")

fun accountIcon(a: String): ImageVector =
    if (a == "Cash") Icons.Rounded.Payments else Icons.Rounded.AccountBalance

val todayIcon = Icons.Rounded.Today
val subscriptionsIcon = Icons.Rounded.Subscriptions
