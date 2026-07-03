package com.normpix.cashflow

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import java.io.File

/** What the edit bottom sheet is targeting. */
data class SheetTarget(
    val type: String, // "txn" | "inbox" | "currency" | "add_cat" | "add_subcat"
    val txn: Txn? = null,
    val inbox: InboxMsg? = null,
    val parentCat: String? = null
)

/**
 * Single state holder for the whole Cashflow app — mirrors the design prototype's
 * component state. Appearance + nav settings persist via SharedPreferences.
 */
@Stable
class CashflowState(private val context: Context, private val prefs: SharedPreferences) {

    // Navigation
    var tab by mutableIntStateOf(0)
    var subScreen by mutableStateOf<String?>(null) // null | "banks" | "categories" | "privacy"

    // Appearance (persisted)
    var seed by mutableStateOf(runCatching { Seed.valueOf(prefs.getString("seed", "GREEN")!!) }.getOrDefault(Seed.GREEN))
        private set
    var mode by mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("mode", "LIGHT")!!) }.getOrDefault(ThemeMode.LIGHT))
        private set
    var navBlur by mutableStateOf(prefs.getFloat("navBlur", 22f))
        private set
    var navOpacity by mutableStateOf(prefs.getFloat("navOpacity", 0.55f))
        private set
    var showFab by mutableStateOf(prefs.getBoolean("showFab", true))
        private set

    // Currency (persisted)
    var currency by mutableStateOf(runCatching { 
        currencies.find { it.code == prefs.getString("currency", "INR") } ?: currencies[0] 
    }.getOrDefault(currencies[0]))
        private set

    fun setSeedPref(v: Seed) { seed = v; prefs.edit().putString("seed", v.name).apply() }
    fun setModePref(v: ThemeMode) { mode = v; prefs.edit().putString("mode", v.name).apply() }
    fun setNavBlurPref(v: Float) { navBlur = v; prefs.edit().putFloat("navBlur", v).apply() }
    fun setNavOpacityPref(v: Float) { navOpacity = v; prefs.edit().putFloat("navOpacity", v).apply() }
    fun setShowFabPref(v: Boolean) { showFab = v; prefs.edit().putBoolean("showFab", v).apply() }
    fun setCurrencyPref(v: Currency) { currency = v; prefs.edit().putString("currency", v.code).apply() }

    // Formatting helper
    fun fmt(n: Double): String = com.normpix.cashflow.fmt(n, currency)

    // Transactions screen
    var search by mutableStateOf("")
    var chip by mutableStateOf("All")

    // Settings
    var smsOn by mutableStateOf(true)
    var catExpanded by mutableStateOf<String?>(null)
    var bankExpanded by mutableStateOf<String?>(null)
    var newBankName by mutableStateOf("")
    var newSenderId by mutableStateOf("")

    // Data
    val txns = mutableStateListOf<Txn>().apply { addAll(sampleTxns()) }
    val inbox = mutableStateListOf<InboxMsg>().apply { addAll(sampleInbox()) }
    val subsList = mutableStateListOf<Sub>().apply { addAll(sampleSubs()) }
    val banks = mutableStateListOf<Bank>().apply { addAll(sampleBanks()) }
    
    val categories = mutableStateListOf<CatInfo>().apply { addAll(catList) }
    val subCategories = mutableStateMapOf<String, MutableList<String>>().apply {
        catSubMap.forEach { (k, v) -> put(k, v.toMutableList()) }
    }

    // Add screen
    var addOpen by mutableStateOf(false)
    var addTab by mutableStateOf("txn") // txn | sub
    var addType by mutableStateOf("expense") // expense | income | transfer
    var addAmount by mutableStateOf("")
    var addMerchant by mutableStateOf("")
    var addCat by mutableStateOf("Food")
    var addSubCat by mutableStateOf<String?>(null)
    var addAccount by mutableStateOf("HDFC ••4521")
    var addFrom by mutableStateOf("HDFC ••4521")
    var addTo by mutableStateOf("SBI ••8890")
    var addDate by mutableStateOf("Today")
    var subName by mutableStateOf("")
    var subCycle by mutableStateOf("Monthly")

    // Bottom sheet
    var sheet by mutableStateOf<SheetTarget?>(null)
    var editMerchant by mutableStateOf("")
    var editAmount by mutableStateOf("")
    var editCat by mutableStateOf("Food")
    
    var newCatName by mutableStateOf("")
    var newSubCatName by mutableStateOf("")

    // Toast
    var toast by mutableStateOf<String?>(null)
    var toastTick by mutableIntStateOf(0)

    fun showToast(msg: String) { toast = msg; toastTick++ }

    fun openSheet(target: SheetTarget) {
        when (target.type) {
            "inbox" -> {
                editMerchant = target.inbox!!.merchant.replace(" (Amazon?)", "")
                editAmount = groupINR(target.inbox.amount, currency.locale).replace(",", "")
                editCat = target.inbox.cat
            }
            "txn" -> {
                editMerchant = target.txn!!.merchant
                editAmount = groupINR(kotlin.math.abs(target.txn.amount), currency.locale).replace(",", "")
                editCat = target.txn.cat
            }
            "add_cat" -> {
                newCatName = ""
            }
            "add_subcat" -> {
                newSubCatName = ""
            }
        }
        sheet = target
    }

    fun closeSheet() { sheet = null }

    fun confirmInbox(item: InboxMsg, merchant: String = item.merchant, amount: Double = item.amount, cat: String = item.cat) {
        inbox.removeAll { it.id == item.id }
        txns.add(
            0,
            Txn(
                id = "n" + item.id,
                merchant = merchant,
                amount = -kotlin.math.abs(amount),
                cat = cat,
                account = item.account + " · UPI",
                whenLabel = item.whenLabel.replace("Today, ", ""),
                day = "Today",
                icon = item.icon,
                auto = true,
            ),
        )
        showToast("Added to Transactions")
        notify("Transaction Captured", "Added ${fmt(amount)} at $merchant")
    }

    fun ignoreInbox(item: InboxMsg) {
        inbox.removeAll { it.id == item.id }
        showToast("Message ignored")
    }

    fun saveAdd() {
        val amt = evalAmount(addAmount)
        if (amt == 0.0) { showToast("Enter an amount"); return }
        val rec = when (addType) {
            "transfer" -> Txn(
                id = "m" + System.currentTimeMillis(), merchant = "$addFrom → $addTo", amount = -amt,
                cat = "Transfer", account = "Transfer · Manual", whenLabel = "Just now",
                day = if (addDate == "Yesterday") "Yesterday" else "Today",
                icon = iconForCat("Transfer"), auto = false,
            )
            "income" -> Txn(
                id = "m" + System.currentTimeMillis(), merchant = addMerchant.trim().ifEmpty { "Income" }, amount = amt,
                cat = "Income", account = "$addAccount · Manual", whenLabel = "Just now",
                day = if (addDate == "Yesterday") "Yesterday" else "Today",
                icon = iconForCat("Income"), auto = false,
            )
            else -> Txn(
                id = "m" + System.currentTimeMillis(), merchant = addMerchant.trim().ifEmpty { "Expense" }, amount = -amt,
                cat = addCat, account = "$addAccount · Manual", whenLabel = "Just now",
                day = if (addDate == "Yesterday") "Yesterday" else "Today",
                icon = iconForCat(addCat), auto = false, sub = addSubCat,
            )
        }
        txns.add(0, rec)
        addOpen = false; addAmount = ""; addMerchant = ""; addSubCat = null
        showToast(when (addType) { "transfer" -> "Transfer recorded"; "income" -> "Income added"; else -> "Expense added" })
        notify("New Transaction", "${if (addType == "income") "+" else "-"}${fmt(amt)} at ${rec.merchant}")
    }

    fun saveSub() {
        val amt = evalAmount(addAmount)
        if (amt == 0.0) { showToast("Enter an amount"); return }
        val name = subName.trim().ifEmpty { "Subscription" }
        subsList.add(0, Sub(name, subscriptionsIcon, amt, "starts today", subCycle))
        addOpen = false; addAmount = ""; subName = ""
        showToast("Subscription added")
    }

    fun saveSheet() {
        val target = sheet ?: return
        when (target.type) {
            "inbox" -> confirmInbox(
                target.inbox!!,
                merchant = editMerchant,
                amount = editAmount.toDoubleOrNull() ?: target.inbox.amount,
                cat = editCat,
            )
            "txn" -> {
                val t = target.txn!!
                val idx = txns.indexOfFirst { it.id == t.id }
                if (idx >= 0) {
                    val old = txns[idx]
                    val sign = if (old.amount > 0) 1 else -1
                    val newAmt = editAmount.toDoubleOrNull()?.let { kotlin.math.abs(it) } ?: kotlin.math.abs(old.amount)
                    txns[idx] = old.copy(
                        merchant = editMerchant,
                        cat = editCat,
                        amount = sign * newAmt,
                        icon = catIcons[editCat] ?: old.icon,
                    )
                }
                showToast("Transaction updated")
            }
            "add_cat" -> {
                if (newCatName.isNotBlank()) {
                    categories.add(CatInfo(newCatName, Icons.Rounded.Category, "New category"))
                    showToast("Category added")
                }
            }
            "add_subcat" -> {
                if (newSubCatName.isNotBlank() && target.parentCat != null) {
                    val list = subCategories.getOrPut(target.parentCat) { mutableStateListOf() }
                    list.add(newSubCatName)
                    showToast("Subcategory added")
                }
            }
        }
        closeSheet()
    }

    fun export(format: String) {
        val content = StringBuilder("Date,Merchant,Amount,Category,Account\n")
        txns.forEach { t ->
            content.append("${t.day},${t.merchant},${t.amount},${t.cat},${t.account}\n")
        }
        
        val filename = "normpix_export_${System.currentTimeMillis()}.$format"
        val file = File(context.cacheDir, filename)
        file.writeText(content.toString())
        
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (format == "csv") "text/csv" else "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export via"))
    }

    private fun notify(title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel("txns", "Transactions", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
        val builder = androidx.core.app.NotificationCompat.Builder(context, "txns")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        
        // In a real app we'd check permission here, for demo we just try to post
        try {
            manager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            // Silently fail if no permission
        }
    }
}

@Composable
fun rememberCashflowState(): CashflowState {
    val context = LocalContext.current
    return remember { CashflowState(context, context.getSharedPreferences("cashflow", Context.MODE_PRIVATE)) }
}
