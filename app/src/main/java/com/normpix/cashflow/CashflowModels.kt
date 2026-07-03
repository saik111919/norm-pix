package com.normpix.cashflow

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CurrencyPound
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.CurrencyYen
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Today
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
    Currency("JPY", "¥", "Japanese Yen", Locale.JAPAN, Icons.Rounded.CurrencyYen),
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

data class Bank(
    val name: String,
    val senders: String,
    val parsed: String,
    val on: Boolean,
    val formats: List<String>,
)

// ── Icon + formatting helpers ──

val catIcons: Map<String, ImageVector> = mapOf(
    "Food" to Icons.Rounded.LunchDining,
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
