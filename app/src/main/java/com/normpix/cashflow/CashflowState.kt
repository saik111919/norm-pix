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
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Payments
import java.io.File
import kotlin.math.roundToInt

/** What the edit bottom sheet is targeting. */
data class SheetTarget(
    val type: String, // "txn" | "inbox"
    val txn: Txn? = null,
    val inbox: InboxMsg? = null,
)

/**
 * Single state holder for the whole Cashflow app — mirrors the design prototype's
 * component state. Appearance + nav settings persist via SharedPreferences.
 */
@Stable
class CashflowState(private val context: Context, private val prefs: SharedPreferences) {

    // Navigation
    var tab: Int by mutableIntStateOf(0)
    var subScreen: String? by mutableStateOf(null) // null | "banks" | "categories" | "privacy"
    var catDetail: String? by mutableStateOf(null) // null | "Food" | "Shopping" | "Transport" | "Bills" | "Income"

    // Long-press menu popped from the floating nav bar's Inbox tab
    var inboxMenuOpen: Boolean by mutableStateOf(false)

    // Appearance (persisted)
    var seed: Seed by mutableStateOf(runCatching { Seed.valueOf(prefs.getString("seed", "GREEN")!!) }.getOrDefault(Seed.GREEN))
        private set
    var mode: ThemeMode by mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("mode", "LIGHT")!!) }.getOrDefault(ThemeMode.LIGHT))
        private set
    var navBlur: Float by mutableStateOf(prefs.getFloat("navBlur", 22f))
        private set
    var navOpacity: Float by mutableStateOf(prefs.getFloat("navOpacity", 0.55f))
        private set
    var showFab: Boolean by mutableStateOf(prefs.getBoolean("showFab", true))
        private set

    // Centralized blur strength + tint opacity for the Add screen and Edit/confirm sheets
    // (design uses fixed values; this makes them user-adjustable settings like the nav bar's).
    var sheetBlur: Float by mutableStateOf(prefs.getFloat("sheetBlur", 24f))
        private set
    var sheetOpacity: Float by mutableStateOf(prefs.getFloat("sheetOpacity", 0.9f))
        private set

    // Currency (persisted)
    var currency: Currency by mutableStateOf(runCatching { 
        currencies.find { it.code == prefs.getString("currency", "INR") } ?: currencies[0] 
    }.getOrDefault(currencies[0]))
        private set

    fun setSeedPref(v: Seed) { seed = v; prefs.edit().putString("seed", v.name).apply() }
    fun setModePref(v: ThemeMode) { mode = v; prefs.edit().putString("mode", v.name).apply() }
    fun setNavBlurPref(v: Float) { navBlur = v; prefs.edit().putFloat("navBlur", v).apply() }
    fun setSheetBlurPref(v: Float) { sheetBlur = v; prefs.edit().putFloat("sheetBlur", v).apply() }
    fun setSheetOpacityPref(v: Float) { sheetOpacity = v; prefs.edit().putFloat("sheetOpacity", v).apply() }
    fun setNavOpacityPref(v: Float) { navOpacity = v; prefs.edit().putFloat("navOpacity", v).apply() }
    fun setShowFabPref(v: Boolean) { showFab = v; prefs.edit().putBoolean("showFab", v).apply() }
    fun setCurrencyPref(v: Currency) { currency = v; prefs.edit().putString("currency", v.code).apply() }

    // Formatting helper
    fun fmt(n: Double): String = com.normpix.cashflow.fmt(n, currency)

    // Transactions screen
    var search: String by mutableStateOf("")
    var chip: String by mutableStateOf("All")

    // Settings
    // Whether the OS has actually granted SMS read/receive permission — refreshed on
    // creation and whenever the app resumes (e.g. coming back from system Settings).
    var smsPermissionGranted: Boolean by mutableStateOf(com.normpix.cashflow.hasSmsPermission(context))
        private set

    // Whether the system permission dialog has been shown before, so we know when a
    // renewed request would be a silent no-op (permanently denied) vs. a fresh ask.
    var smsPermissionRequestedBefore: Boolean by mutableStateOf(prefs.getBoolean("smsPermRequested", false))
        private set

    fun markSmsPermissionRequested() {
        smsPermissionRequestedBefore = true
        prefs.edit().putBoolean("smsPermRequested", true).apply()
    }

    /** Called with the result of a permission request (or a fresh check on resume). */
    fun onSmsPermissionResult(granted: Boolean) {
        smsPermissionGranted = granted
        setSmsOnPref(granted)
        showToast(if (granted) "SMS access granted" else "SMS permission denied")
        if (granted) refreshSmsInbox()
    }

    /** Re-checks the OS permission state, e.g. after the app resumes from system Settings. */
    fun refreshSmsPermission() {
        val granted = com.normpix.cashflow.hasSmsPermission(context)
        if (granted != smsPermissionGranted) {
            smsPermissionGranted = granted
            if (!granted) setSmsOnPref(false)
        }
    }

    /** Merges a single parsed SMS into the inbox, inferring category from smart rules and
     * account from the card/account last-4 digits when possible. Idempotent on [id]. */
    private fun addParsedInboxEntry(id: String, sender: String, body: String, parsed: ParsedSms): Boolean {
        if (inbox.any { it.id == id } || txns.any { it.id == id }) return false
        val merchant = parsed.merchant?.takeIf { it.isNotBlank() } ?: parsed.bank.name
        val cat = smartRules.firstOrNull { merchant.contains(it.match, ignoreCase = true) }?.cat ?: "Shopping"
        val account = accounts.firstOrNull { a -> parsed.last4 != null && a.mask?.endsWith(parsed.last4) == true }
            ?.let { "${it.name} ${it.mask}" }
            ?: parsed.last4?.let { "${parsed.bank.name} ••$it" }
            ?: parsed.bank.name
        inbox.add(
            0,
            InboxMsg(
                id = id, sender = sender, conf = if (parsed.merchant != null) "high" else "low", dup = false,
                sms = body, merchant = merchant, amount = parsed.amount, cat = cat, account = account,
                whenLabel = "Just now", icon = iconForCat(cat),
            ),
        )
        return true
    }

    /** Picks up anything [SmsReceiver] parsed while this state wasn't active, then scans SMS
     * already on the device (the READ_SMS half of the permission) for anything newer than the
     * last scan. Cheap to call on every resume — the content-provider query is watermarked. */
    fun refreshSmsInbox() {
        if (!smsOn || !com.normpix.cashflow.hasSmsPermission(context)) return
        var found = 0
        com.normpix.cashflow.drainPendingInboxEntries(context).forEach { entry ->
            val amount = entry.optDouble("amount", 0.0)
            if (amount <= 0.0) return@forEach
            val bank = entry.optString("bank")
            val parsed = ParsedSms(
                Bank(bank, "", "", true, emptyList()), amount,
                entry.optString("merchant").ifBlank { null }, entry.optString("last4").ifBlank { null },
            )
            if (addParsedInboxEntry(entry.optString("id"), entry.optString("sender"), entry.optString("sms"), parsed)) found++
        }

        val lastScan = prefs.getLong("smsLastScan", 0L)
        var maxDate = lastScan
        val projection = arrayOf(
            android.provider.Telephony.Sms.ADDRESS, android.provider.Telephony.Sms.BODY, android.provider.Telephony.Sms.DATE,
        )
        val cursor = runCatching {
            context.contentResolver.query(
                android.provider.Telephony.Sms.Inbox.CONTENT_URI, projection,
                "${android.provider.Telephony.Sms.DATE} > ?", arrayOf(lastScan.toString()),
                "${android.provider.Telephony.Sms.DATE} ASC",
            )
        }.getOrNull()
        cursor?.use { c ->
            val addressIdx = c.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
            val bodyIdx = c.getColumnIndex(android.provider.Telephony.Sms.BODY)
            val dateIdx = c.getColumnIndex(android.provider.Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val date = c.getLong(dateIdx)
                if (date > maxDate) maxDate = date
                val sender = c.getString(addressIdx) ?: continue
                val body = c.getString(bodyIdx) ?: continue
                val parsed = com.normpix.cashflow.parseSms(sender, body, banks) ?: continue
                if (addParsedInboxEntry("sms$date$sender".hashCode().toString(), sender, body, parsed)) found++
            }
        }
        prefs.edit().putLong("smsLastScan", maxDate).apply()

        if (found > 0) showToast("$found new message${if (found == 1) "" else "s"} detected")
    }

    // User's intent to auto-capture from SMS. Only meaningful when smsPermissionGranted
    // is also true — the Settings toggle and Inbox banner keep the two in sync.
    var smsOn: Boolean by mutableStateOf(prefs.getBoolean("smsOn", true) && com.normpix.cashflow.hasSmsPermission(context))
        private set

    fun setSmsOnPref(v: Boolean) { smsOn = v; prefs.edit().putBoolean("smsOn", v).apply() }

    var catExpanded: String? by mutableStateOf(null)
    var bankExpanded: String? by mutableStateOf(null)
    var newBankName: String by mutableStateOf("")
    var newSenderId: String by mutableStateOf("")

    // Bank SMS-format sub-screen: inline add-format form
    var addingFormatFor: String? by mutableStateOf(null)
    var newFormatText: String by mutableStateOf("")

    fun startAddFormat(bankName: String) { addingFormatFor = bankName; newFormatText = "" }
    fun cancelAddFormat() { addingFormatFor = null; newFormatText = "" }

    fun saveNewFormat() {
        val bankName = addingFormatFor ?: return
        val text = newFormatText.trim()
        if (text.isEmpty()) { showToast("Enter a format pattern"); return }
        val i = banks.indexOfFirst { it.name == bankName }
        if (i >= 0) banks[i] = banks[i].copy(formats = banks[i].formats + text)
        addingFormatFor = null; newFormatText = ""
        showToast("Format added")
    }

    // Profile (persisted)
    var profileName: String by mutableStateOf(prefs.getString("profileName", "Aditi Rao")!!)
        private set
    var profileColor: String by mutableStateOf(prefs.getString("profileColor", "green")!!)
        private set

    fun setProfileNamePref(v: String) { profileName = v; prefs.edit().putString("profileName", v).apply() }
    fun setProfileColorPref(v: String) { profileColor = v; prefs.edit().putString("profileColor", v).apply() }

    // Language (persisted)
    var language: String by mutableStateOf(prefs.getString("language", "English")!!)
        private set

    fun setLanguagePref(l: String) { language = l; prefs.edit().putString("language", l).apply(); showToast("Language set to $l") }

    fun setCurrencyByCode(code: String) {
        val c = currencies.find { it.code == code } ?: return
        setCurrencyPref(c)
        showToast("Currency updated (demo — amounts stay in ₹)")
    }

    // Developer mode + auto-backup (persisted)
    var devMode: Boolean by mutableStateOf(prefs.getBoolean("devMode", false))
        private set
    var autoBackup: Boolean by mutableStateOf(prefs.getBoolean("autoBackup", true))
        private set

    fun toggleDevMode() {
        devMode = !devMode
        prefs.edit().putBoolean("devMode", devMode).apply()
        if (devMode) showToast("Developer mode enabled")
    }

    fun toggleAutoBackup() { autoBackup = !autoBackup; prefs.edit().putBoolean("autoBackup", autoBackup).apply() }

    // Notification preferences (persisted)
    var notifPrefs: NotifPrefs by mutableStateOf(
        NotifPrefs(
            budget = prefs.getBoolean("notif_budget", true),
            weekly = prefs.getBoolean("notif_weekly", true),
            bills = prefs.getBoolean("notif_bills", true),
            sms = prefs.getBoolean("notif_sms", true),
        ),
    )
        private set

    fun toggleNotif(key: String) {
        notifPrefs = when (key) {
            "budget" -> notifPrefs.copy(budget = !notifPrefs.budget)
            "weekly" -> notifPrefs.copy(weekly = !notifPrefs.weekly)
            "bills" -> notifPrefs.copy(bills = !notifPrefs.bills)
            else -> notifPrefs.copy(sms = !notifPrefs.sms)
        }
        prefs.edit()
            .putBoolean("notif_budget", notifPrefs.budget)
            .putBoolean("notif_weekly", notifPrefs.weekly)
            .putBoolean("notif_bills", notifPrefs.bills)
            .putBoolean("notif_sms", notifPrefs.sms)
            .apply()
    }

    // Reminders sub-screen — persisted to SharedPreferences (not just in-memory) so the real
    // AlarmManager alarms scheduled for them keep working across app restarts and reboots.
    val reminders = mutableStateListOf<Reminder>().apply { addAll(com.normpix.cashflow.loadReminders(context) ?: com.normpix.cashflow.sampleReminders()) }
    var reminderEditing: String? by mutableStateOf(null)
    var alarmFiring: Reminder? by mutableStateOf(null)

    var addingReminder: Boolean by mutableStateOf(false)
    var newReminderTitle: String by mutableStateOf("")
    var newReminderKind: String by mutableStateOf("event") // event | review_spend
    var newReminderTxnId: String? by mutableStateOf(null)
    var newReminderDate: String? by mutableStateOf(null)
    var newReminderTime: String by mutableStateOf("09:00")

    // Whether the OS will let NormPix schedule exact-time alarms (API 31+ only; always true
    // below that). Without it, reminders still fire, just not necessarily to the exact minute.
    var exactAlarmsAllowed: Boolean by mutableStateOf(canScheduleExactAlarms(context))
        private set

    fun refreshExactAlarmPermission() { exactAlarmsAllowed = canScheduleExactAlarms(context) }
    fun requestExactAlarmPermission() = com.normpix.cashflow.requestExactAlarmPermission(context)

    /** Saves the reminder list and re-syncs every reminder's real OS alarm to match. */
    private fun persistReminders() {
        com.normpix.cashflow.saveReminders(context, reminders)
        com.normpix.cashflow.syncReminderAlarms(context, reminders)
    }

    init {
        // First run (or a fresh install): seed persisted storage from the sample defaults so
        // their alarms are actually scheduled, not just shown in the list.
        if (com.normpix.cashflow.loadReminders(context) == null) persistReminders()
    }

    fun toggleReminder(id: String) {
        val i = reminders.indexOfFirst { it.id == id }
        if (i >= 0) reminders[i] = reminders[i].copy(on = !reminders[i].on)
        persistReminders()
    }

    private fun updateReminder(id: String, transform: (Reminder) -> Reminder) {
        val i = reminders.indexOfFirst { it.id == id }
        if (i >= 0) reminders[i] = transform(reminders[i])
        persistReminders()
    }

    fun setReminderFreq(id: String, freq: String) = updateReminder(id) { it.copy(freq = freq) }
    fun setReminderTime(id: String, time: String) = updateReminder(id) { it.copy(time = time) }
    fun setReminderDate(id: String, date: String) = updateReminder(id) { it.copy(date = date) }
    fun setReminderPriority(id: String, priority: String) = updateReminder(id) { it.copy(priority = priority) }
    fun setReminderSound(id: String, v: Boolean) = updateReminder(id) { it.copy(sound = v) }
    fun setReminderVibration(id: String, v: Boolean) = updateReminder(id) { it.copy(vibration = v) }
    fun toggleReminderDay(id: String, day: String) = updateReminder(id) {
        it.copy(days = if (day in it.days) it.days - day else it.days + day)
    }

    fun removeReminder(id: String) {
        reminders.removeAll { it.id == id }
        if (reminderEditing == id) reminderEditing = null
        com.normpix.cashflow.cancelReminder(context, id)
        persistReminders()
    }

    /** Shows the full-screen alarm UI for [r] (a foreground preview of the real thing). */
    fun fireAlarm(r: Reminder) { alarmFiring = r }
    fun dismissAlarm() { alarmFiring = null }
    fun snoozeAlarm() {
        val r = alarmFiring
        alarmFiring = null
        if (r != null) com.normpix.cashflow.scheduleExactAt(context, r, System.currentTimeMillis() + 10 * 60 * 1000)
        showToast("Snoozed for 10 minutes")
    }

    fun startAddReminder() {
        addingReminder = true
        newReminderTitle = ""; newReminderKind = "event"; newReminderTxnId = null
        newReminderDate = null; newReminderTime = "09:00"
    }

    fun cancelAddReminder() { addingReminder = false }

    fun saveAddReminder() {
        val title = newReminderTitle.trim()
        if (title.isEmpty()) { showToast("Enter a reminder title"); return }
        if (newReminderKind == "review_spend" && newReminderTxnId == null) { showToast("Pick a spend to review"); return }
        reminders.add(
            Reminder(
                id = "cr" + System.currentTimeMillis(),
                type = "custom",
                icon = com.normpix.cashflow.reminderIcon("custom", hasLinkedTxn = newReminderKind == "review_spend"),
                title = title, on = true, time = newReminderTime.ifBlank { "09:00" }, freq = "once",
                date = newReminderDate, sound = true, vibration = true, priority = "normal",
                linkedTxnId = if (newReminderKind == "review_spend") newReminderTxnId else null,
            ),
        )
        addingReminder = false
        persistReminders()
        showToast("Reminder added")
    }

    // Full-screen behavior exceptions (which apps a reminder alarm may cover the screen of)
    val appExceptions = mutableStateMapOf<String, Boolean>().apply { putAll(defaultAppExceptions()) }
    var appExceptionSearch: String by mutableStateOf("")

    fun setAppException(name: String, fullScreen: Boolean) { appExceptions[name] = fullScreen }
    fun resetAppExceptions() {
        appExceptions.clear(); appExceptions.putAll(defaultAppExceptions())
        showToast("Reset to defaults")
    }

    // Synced-messages sub-screen
    val syncHistory = mutableStateListOf<SyncHistoryEntry>().apply { addAll(sampleSyncHistory()) }
    var smsSyncing: Boolean by mutableStateOf(false)
    var smsLastSynced: Long? by mutableStateOf(null)
    var syncTick: Int by mutableIntStateOf(0)

    /** Kicks off a visible sync pass; the spinner clears itself via [finishSync] after a short delay. */
    fun syncSms() {
        if (smsSyncing) return
        smsSyncing = true
        syncTick++
    }

    /** Called by the UI after its delay — keeps the coroutine/delay concern out of this state holder. */
    fun finishSync() {
        refreshSmsInbox()
        smsSyncing = false
        smsLastSynced = System.currentTimeMillis()
        showToast("SMS sync complete")
    }

    // About screen: star rating (persisted) + Licenses/Privacy accordions
    var rating: Int by mutableStateOf(prefs.getInt("rating", 0))
        private set

    fun rate(v: Int) {
        rating = v
        prefs.edit().putInt("rating", v).apply()
        showToast("Thanks for the $v-star rating!")
    }

    var aboutExpanded: String? by mutableStateOf(null) // null | "licenses" | "privacy"

    val currentVersionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    }.getOrDefault("1.0")

    // Budgets — per-category caps, single source of truth for the Dashboard hero, Insights
    // ring/bars, and the category drill-down. The overall monthly budget is their sum
    // (matches the design: `budget = Object.values(S.budgetCaps).reduce((a,b) => a+b, 0)`).
    val budgetCaps = mutableStateMapOf<String, Double>().apply {
        putAll(mapOf("Food" to 15000.0, "Shopping" to 10000.0, "Transport" to 9000.0, "Bills" to 8000.0))
    }

    val monthlyBudget: Double get() = budgetCaps.values.sum()

    fun setBudgetCap(cat: String, cap: Double) { budgetCaps[cat] = cap }

    // Budgets sub-screen: inline edit-existing / add-new flows (design's editingBudgetFor / addingBudget)
    var editingBudgetFor: String? by mutableStateOf(null)
    var editBudgetVal: String by mutableStateOf("")
    var addingBudget: Boolean by mutableStateOf(false)
    var newBudgetKey: String? by mutableStateOf(null)
    var newBudgetVal: String by mutableStateOf("")

    /** Categories not yet budgeted, as (key, display name) — the 4 canonical ones first, then any other catalog category. */
    fun availableBudgetCats(): List<Pair<String, String>> {
        val canonical = listOf("Food" to "Food & dining", "Shopping" to "Shopping", "Transport" to "Transport", "Bills" to "Bills & utilities")
        val canonicalAvail = canonical.filter { it.first !in budgetCaps }
        val otherAvail = catList.map { it.name }
            .filter { name -> canonical.none { c -> c.second == name } && name !in budgetCaps }
            .map { it to it }
        return canonicalAvail + otherAvail
    }

    fun startEditBudget(cat: String) {
        editingBudgetFor = cat
        editBudgetVal = groupINR(budgetCaps[cat] ?: 0.0).replace(",", "")
    }

    fun cancelEditBudget() { editingBudgetFor = null; editBudgetVal = "" }

    fun saveEditBudget() {
        val cat = editingBudgetFor ?: return
        val v = editBudgetVal.toDoubleOrNull()
        if (v == null || v <= 0.0) { showToast("Enter a valid amount"); return }
        budgetCaps[cat] = v
        editingBudgetFor = null; editBudgetVal = ""
        showToast("Budget updated")
    }

    fun startAddBudget() {
        addingBudget = true
        newBudgetKey = availableBudgetCats().firstOrNull()?.first
        newBudgetVal = ""
    }

    fun cancelAddBudget() { addingBudget = false; newBudgetKey = null; newBudgetVal = "" }

    fun saveAddBudget() {
        val key = newBudgetKey ?: return
        val v = newBudgetVal.toDoubleOrNull()
        if (v == null || v <= 0.0) { showToast("Enter a valid amount"); return }
        budgetCaps[key] = v
        addingBudget = false; newBudgetKey = null; newBudgetVal = ""
        showToast("Budget added")
    }

    /** This month's spend for [cat] — live-computed from [txns], not a snapshot. */
    fun categorySpent(cat: String): Double = com.normpix.cashflow.categorySpent(txns, cat)

    /** Total expense this month across all non-transfer categories — live-computed from [txns]. */
    val spentMonth: Double get() = txns.filter { it.amount < 0 && it.cat != "Transfer" }.sumOf { -it.amount }

    /** Today's expense total — live-computed from [txns]. */
    val todaySpent: Double get() = txns.filter { it.day == "Today" && it.amount < 0 && it.cat != "Transfer" }.sumOf { -it.amount }

    /** Percent of [monthlyBudget] spent so far this month. */
    val budgetPct: Int get() = if (monthlyBudget <= 0) 0 else ((spentMonth / monthlyBudget) * 100).roundToInt()

    // Data
    val txns = mutableStateListOf<Txn>().apply { addAll(sampleTxns()) }
    val inbox = mutableStateListOf<InboxMsg>().apply { addAll(sampleInbox()) }
    val subsList = mutableStateListOf<Sub>().apply { addAll(sampleSubs()) }
    val banks = mutableStateListOf<Bank>().apply { addAll(sampleBanks()) }
    val accounts = mutableStateListOf<Account>().apply { addAll(sampleAccounts()) }
    val smartRules = mutableStateListOf<SmartRule>().apply { addAll(sampleSmartRules()) }
    val webhooks = mutableStateListOf<Webhook>().apply { addAll(sampleWebhooks()) }

    val categories = mutableStateListOf<CatInfo>().apply { addAll(catList) }
    val subCategories = mutableStateMapOf<String, MutableList<String>>().apply {
        catSubMap.forEach { (k, v) -> put(k, v.toMutableList()) }
    }

    // Accounts sub-screen
    fun toggleAccount(i: Int) { accounts[i] = accounts[i].copy(on = !accounts[i].on) }

    var addingAccount: Boolean by mutableStateOf(false)
    var newAccountName: String by mutableStateOf("")
    var newAccountType: String by mutableStateOf("Savings") // Savings | Credit card | Cash
    var newAccountBalance: String by mutableStateOf("")

    fun startAddAccount() {
        addingAccount = true
        newAccountName = ""; newAccountType = "Savings"; newAccountBalance = ""
    }

    fun cancelAddAccount() { addingAccount = false }

    fun addAccount() {
        val name = newAccountName.trim()
        if (name.isEmpty()) { showToast("Enter an account name"); return }
        val balance = newAccountBalance.toDoubleOrNull() ?: 0.0
        val icon = when (newAccountType) {
            "Cash" -> Icons.Rounded.Payments
            "Credit card" -> Icons.Rounded.CreditCard
            else -> Icons.Rounded.AccountBalance
        }
        accounts.add(Account(name, null, newAccountType, if (newAccountType == "Credit card") -balance else balance, icon, true))
        addingAccount = false
        showToast("Account linked")
    }

    // Smart rules sub-screen
    var newRuleMatch: String by mutableStateOf("")
    var newRuleCat: String by mutableStateOf("Food")

    fun addSmartRule() {
        val m = newRuleMatch.trim()
        if (m.isEmpty()) { showToast("Enter merchant keywords"); return }
        smartRules.add(0, SmartRule("r" + System.currentTimeMillis(), m, newRuleCat))
        newRuleMatch = ""
        showToast("Rule added")
    }

    fun removeSmartRule(id: String) { smartRules.removeAll { it.id == id } }

    // Webhooks sub-screen (dev mode only)
    var newWebhookUrl: String by mutableStateOf("")

    fun addWebhook() {
        val u = newWebhookUrl.trim()
        if (u.isEmpty()) { showToast("Enter a webhook URL"); return }
        webhooks.add(0, Webhook("w" + System.currentTimeMillis(), u, "transaction.created", true))
        newWebhookUrl = ""
        showToast("Webhook added")
    }

    fun removeWebhook(id: String) { webhooks.removeAll { it.id == id } }
    fun toggleWebhook(id: String) {
        val i = webhooks.indexOfFirst { it.id == id }
        if (i >= 0) webhooks[i] = webhooks[i].copy(on = !webhooks[i].on)
    }

    var eraseConfirming: Boolean by mutableStateOf(false)

    fun eraseAllData() { eraseConfirming = true }
    fun cancelErase() { eraseConfirming = false }

    fun confirmErase() {
        txns.clear()
        inbox.clear()
        subsList.clear()
        smartRules.clear()
        webhooks.clear()
        accounts.clear()
        syncHistory.clear()
        eraseConfirming = false
        showToast("All local data erased")
    }

    // Add screen
    var addOpen: Boolean by mutableStateOf(false)
    var addTab: String by mutableStateOf("txn") // txn | sub
    var addType: String by mutableStateOf("expense") // expense | income | transfer
    var addAmount: String by mutableStateOf("")
    var addMerchant: String by mutableStateOf("")
    var addCat: String by mutableStateOf("Food")
    var addSubCat: String? by mutableStateOf(null)
    var addAccount: String by mutableStateOf("HDFC ••4521")
    var addFrom: String by mutableStateOf("HDFC ••4521")
    var addTo: String by mutableStateOf("SBI ••8890")
    var addDate: String by mutableStateOf("Today") // Today | Yesterday | Custom
    var addCustomDateMillis: Long? by mutableStateOf(null)
    var showDatePicker: Boolean by mutableStateOf(false)
    var subName: String by mutableStateOf("")
    var subCycle: String by mutableStateOf("Monthly")

    fun openDatePicker() { showDatePicker = true }
    fun closeDatePicker() { showDatePicker = false }
    fun pickCustomDate(millis: Long) {
        addCustomDateMillis = millis
        addDate = "Custom"
        showDatePicker = false
    }

    private fun resolvedAddDay(): String = when (addDate) {
        "Yesterday" -> "Yesterday"
        "Custom" -> addCustomDateMillis?.let { dayBucketFor(it) } ?: "Today"
        else -> "Today"
    }

    private fun resolvedAddWhenLabel(): String =
        if (addDate == "Custom") addCustomDateMillis?.let { formatShortDate(it) } ?: "Just now" else "Just now"

    // Bottom sheet
    var sheet: SheetTarget? by mutableStateOf(null)
    var editMerchant: String by mutableStateOf("")
    var editAmount: String by mutableStateOf("")
    var editCat: String by mutableStateOf("Food")

    // Categories sub-screen: inline add-category / add-subcategory forms
    var addingCategory: Boolean by mutableStateOf(false)
    var newCatName: String by mutableStateOf("")
    var newCatIconIdx: Int by mutableIntStateOf(0)

    fun startAddCategory() { addingCategory = true; newCatName = ""; newCatIconIdx = 0 }
    fun cancelAddCategory() { addingCategory = false; newCatName = "" }
    fun saveNewCategory() {
        val name = newCatName.trim()
        if (name.isEmpty()) { showToast("Enter a category name"); return }
        categories.add(CatInfo(name, addCategoryIcons[newCatIconIdx], "New category"))
        addingCategory = false; newCatName = ""
        showToast("Category added")
    }

    var addingSubFor: String? by mutableStateOf(null)
    var newSubCatName: String by mutableStateOf("")

    fun startAddSubcat(parent: String) { addingSubFor = parent; newSubCatName = "" }
    fun cancelAddSubcat() { addingSubFor = null; newSubCatName = "" }
    fun saveNewSubcat() {
        val parent = addingSubFor ?: return
        val name = newSubCatName.trim()
        if (name.isEmpty()) { showToast("Enter a subcategory name"); return }
        subCategories.getOrPut(parent) { mutableStateListOf() }.add(name)
        addingSubFor = null; newSubCatName = ""
        showToast("Subcategory added")
    }

    // Toast
    var toast: String? by mutableStateOf(null)
    var toastTick: Int by mutableIntStateOf(0)

    fun showToast(msg: String) { toast = msg; toastTick++ }

    fun openSheet(target: SheetTarget) {
        when (target.type) {
            "inbox" -> {
                editMerchant = target.inbox!!.merchant.replace(" (Amazon?)", "")
                editAmount = com.normpix.cashflow.groupINR(target.inbox.amount, currency.locale).replace(",", "")
                editCat = target.inbox.cat
            }
            "txn" -> {
                editMerchant = target.txn!!.merchant
                editAmount = com.normpix.cashflow.groupINR(kotlin.math.abs(target.txn.amount), currency.locale).replace(",", "")
                editCat = target.txn.cat
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
        syncHistory.add(0, SyncHistoryEntry(item.id, item.sender, item.sms, merchant, amount, cat, item.whenLabel, item.icon, "added"))
        showToast("Added to Transactions")
        notify("Transaction Captured", "Added ${fmt(amount)} at $merchant")
    }

    fun ignoreInbox(item: InboxMsg) {
        inbox.removeAll { it.id == item.id }
        syncHistory.add(0, SyncHistoryEntry(item.id, item.sender, item.sms, item.merchant, item.amount, item.cat, item.whenLabel, item.icon, "ignored"))
        showToast("Message ignored")
    }

    fun saveAdd() {
        val amt = evalAmount(addAmount)
        if (amt == 0.0) { showToast("Enter an amount"); return }
        val day = resolvedAddDay()
        val whenLabel = resolvedAddWhenLabel()
        val rec = when (addType) {
            "transfer" -> Txn(
                id = "m" + System.currentTimeMillis(), merchant = "$addFrom → $addTo", amount = -amt,
                cat = "Transfer", account = "Transfer · Manual", whenLabel = whenLabel, day = day,
                icon = iconForCat("Transfer"), auto = false,
            )
            "income" -> Txn(
                id = "m" + System.currentTimeMillis(), merchant = addMerchant.trim().ifEmpty { "Income" }, amount = amt,
                cat = "Income", account = "$addAccount · Manual", whenLabel = whenLabel, day = day,
                icon = iconForCat("Income"), auto = false,
            )
            else -> Txn(
                id = "m" + System.currentTimeMillis(), merchant = addMerchant.trim().ifEmpty { "Expense" }, amount = -amt,
                cat = addCat, account = "$addAccount · Manual", whenLabel = whenLabel, day = day,
                icon = iconForCat(addCat), auto = false, sub = addSubCat,
            )
        }
        txns.add(0, rec)
        addOpen = false; addAmount = ""; addMerchant = ""; addSubCat = null
        addDate = "Today"; addCustomDateMillis = null
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
        }
        closeSheet()
    }

    /** Imports transactions from a CSV file with the same columns [export] writes. */
    fun importCsv(uri: android.net.Uri) {
        val lines = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines()
        } catch (e: Exception) {
            null
        }
        if (lines == null) { showToast("Couldn't read file"); return }
        var imported = 0
        lines.drop(1).forEach { line ->
            val parts = line.split(",")
            val amount = parts.getOrNull(2)?.toDoubleOrNull()
            if (parts.size >= 5 && amount != null) {
                val day = parts[0].takeIf { it == "Today" || it == "Yesterday" } ?: "Earlier"
                val cat = parts[3]
                txns.add(
                    0,
                    Txn(
                        id = "imp" + System.currentTimeMillis() + imported,
                        merchant = parts[1], amount = amount, cat = cat, account = parts[4],
                        whenLabel = "Imported", day = day, icon = iconForCat(cat), auto = false,
                    ),
                )
                imported++
            }
        }
        showToast(if (imported > 0) "Imported $imported transaction${if (imported == 1) "" else "s"}" else "No valid rows found")
    }

    fun export() {
        val content = StringBuilder("Date,Merchant,Amount,Category,Account\n")
        txns.forEach { t ->
            content.append("${t.day},${t.merchant},${t.amount},${t.cat},${t.account}\n")
        }

        val filename = "normpix_export_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, filename)
        file.writeText(content.toString())

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export via"))
    }

    private fun notify(title: String, body: String) {
        if (!com.normpix.cashflow.hasNotificationPermission(context)) return
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
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}

@Composable
fun rememberCashflowState(): CashflowState {
    val context = LocalContext.current
    return remember { CashflowState(context, context.getSharedPreferences("cashflow", Context.MODE_PRIVATE)) }
}
