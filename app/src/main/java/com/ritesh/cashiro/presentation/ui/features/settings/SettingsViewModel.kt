package com.ritesh.cashiro.presentation.ui.features.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.ritesh.cashiro.MainActivity
import com.ritesh.cashiro.R
import com.ritesh.cashiro.receiver.SmsBroadcastReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ritesh.cashiro.data.repository.SubscriptionRepository
import com.ritesh.cashiro.data.repository.UnrecognizedSmsRepository
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import com.ritesh.cashiro.data.backup.BackupExporter
import com.ritesh.cashiro.data.backup.BackupImporter
import com.ritesh.cashiro.data.backup.ExportResult
import com.ritesh.cashiro.data.backup.ImportResult
import com.ritesh.cashiro.data.backup.ImportStrategy
import com.ritesh.cashiro.data.repository.MerchantMappingRepository
import com.ritesh.cashiro.data.webhook.WebhookSyncScheduler
import com.ritesh.cashiro.domain.repository.RuleRepository
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.net.URLEncoder
import java.io.File
import javax.inject.Inject
import androidx.core.net.toUri
import com.ritesh.cashiro.data.database.CashiroDatabase
import com.ritesh.cashiro.data.repository.TransactionRepository
import com.ritesh.cashiro.data.repository.AccountBalanceRepository
import com.ritesh.cashiro.data.repository.CardRepository
import com.ritesh.cashiro.data.repository.BudgetRepository
import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import com.ritesh.cashiro.data.database.entity.CardEntity
import com.ritesh.cashiro.data.database.entity.CardType
import com.ritesh.cashiro.data.database.entity.BudgetEntity
import com.ritesh.cashiro.data.database.entity.BudgetPeriod
import com.ritesh.cashiro.data.database.entity.BudgetTrackType
import com.ritesh.cashiro.data.database.entity.BudgetType
import com.ritesh.cashiro.data.database.entity.TransactionEntity
import com.ritesh.cashiro.data.database.entity.TransactionType
import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.data.database.entity.SubscriptionState
import com.ritesh.cashiro.utils.IconResolutionUtils
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val unrecognizedSmsRepository: UnrecognizedSmsRepository,
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val cardRepository: CardRepository,
    private val budgetRepository: BudgetRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val ruleRepository: RuleRepository,
    private val merchantMappingRepository: MerchantMappingRepository,
    private val backupExporter: BackupExporter,
    private val backupImporter: BackupImporter,
    private val database: CashiroDatabase,
    private val webhookSyncScheduler: WebhookSyncScheduler
) : ViewModel() {

    val databaseVersion: Int
        get() = database.openHelper.readableDatabase.version

    val userPreferences = userPreferencesRepository.userPreferences

    val totalTransactions = transactionRepository.getTransactionCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Developer mode state
    val isDeveloperModeEnabled = userPreferencesRepository.isDeveloperModeEnabled
    val isWebhookModeEnabled = userPreferencesRepository.isWebhookModeEnabled
    val isTestNotificationAlertsEnabled = userPreferencesRepository.isTestNotificationAlertsEnabled

    // SMS scan period state
    val smsScanMonths = userPreferencesRepository.smsScanMonths
    val smsScanAllTime = userPreferencesRepository.smsScanAllTime

    // Unrecognized SMS state
    val unreportedSmsCount = unrecognizedSmsRepository.getUnreportedCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val isSampleDataSeeded: Flow<Boolean> = userPreferencesRepository.isSampleDataSeeded

    fun toggleWebhookMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setWebhookModeEnabled(enabled)
            try {
                webhookSyncScheduler.applyScheduling()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e("SettingsViewModel", "Failed to apply webhook scheduling", t)
            }
        }
    }

    fun toggleTestNotificationAlerts(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTestNotificationAlertsEnabled(enabled)
            if (enabled) {
                sendTestNotification()
            }
        }
    }

    fun toggleSampleData(enabled: Boolean) {
        if (enabled) {
            seedSampleData()
        } else {
            removeSampleData()
        }
    }

    private fun sendTestNotification() {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Ensure channel exists
            val channel = NotificationChannel(
                SmsBroadcastReceiver.CHANNEL_ID,
                SmsBroadcastReceiver.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new transactions"
            }
            notificationManager.createNotificationChannel(channel)

            // Create intent to open app
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, SmsBroadcastReceiver.CHANNEL_ID)
                .setSmallIcon(R.drawable.cashiro)
                .setContentTitle("Test Notification")
                .setContentText("This is a test notification from Cashiro.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(999, notification) // ID 999 for test
            Log.d("SettingsViewModel", "Sent test notification")
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Error sending test notification", e)
        }
    }

    fun updateSmsScanMonths(months: Int) {
        viewModelScope.launch {
            val currentMonths = userPreferencesRepository.getSmsScanMonths()

            // If increasing scan period, reset scan timestamp to force full scan
            if (months > currentMonths) {
                userPreferencesRepository.setLastScanTimestamp(0L)
                Log.d("SettingsViewModel", "Scan period increased from $currentMonths to $months months - will perform full scan")
            }

            userPreferencesRepository.updateSmsScanMonths(months)
        }
    }

    fun updateSmsScanAllTime(allTime: Boolean) {
        viewModelScope.launch {
            // If enabling all time scanning, reset scan timestamp to force full scan
            if (allTime) {
                userPreferencesRepository.setLastScanTimestamp(0L)
                Log.d("SettingsViewModel", "All time scanning enabled - will perform full scan")
            }

            userPreferencesRepository.updateSmsScanAllTime(allTime)
        }
    }

    fun openUnrecognizedSmsReport(context: Context) {
        viewModelScope.launch {
            try {
                val firstUnreported = unrecognizedSmsRepository.getFirstUnreported()

                if (firstUnreported != null) {
                    val issueTitle = "[Unrecognized SMS] From: ${firstUnreported.sender}"
                    val issueBody = """
                        ### Unrecognized SMS Details
                        - **Sender:** ${firstUnreported.sender}
                        - **Date:** ${firstUnreported.receivedAt}
                        
                        ### Original SMS
                        ```
                        ${firstUnreported.smsBody}
                        ```
                        
                        ### Expected Behavior
                        _Describe how this SMS should have been parsed_
                    """.trimIndent()

                    val encodedTitle = URLEncoder.encode(issueTitle, "UTF-8")
                    val encodedBody = URLEncoder.encode(issueBody, "UTF-8")

                    val url = "https://github.com/ritesh-kanwar/Cashiro/issues/new?title=$encodedTitle&body=$encodedBody"

                    // Open in browser
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    context.startActivity(intent)

                    // Mark as reported
                    unrecognizedSmsRepository.markAsReported(listOf(firstUnreported.id))

                    Log.d("SettingsViewModel", "Opened report for unrecognized SMS from: ${firstUnreported.sender}")
                } else {
                    Log.d("SettingsViewModel", "No unreported SMS messages found")
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error opening unrecognized SMS report", e)
            }
        }
    }

    fun exportBackup() {
        viewModelScope.launch {
            try {
                when (val result = backupExporter.exportBackup()) {
                    is ExportResult.Success -> {
                        // Store the file for later saving
                        _uiState.update { it.copy(
                            exportedBackupFile = result.file,
                            importExportMessage = "Backup created successfully! Choose where to save it."
                        ) }
                    }
                    is ExportResult.Error -> {
                        _uiState.update { it.copy(importExportMessage = "Export failed: ${result.message}") }
                        Log.e("SettingsViewModel", "Export failed: ${result.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(importExportMessage = "Export error: ${e.message}") }
                Log.e("SettingsViewModel", "Export error", e)
            }
        }
    }

    fun saveBackupToFile(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value.exportedBackupFile?.let { file ->
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        file.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    _uiState.update { it.copy(
                        importExportMessage = "Backup saved successfully!",
                        exportedBackupFile = null
                    ) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(importExportMessage = "Failed to save backup: ${e.message}") }
                Log.e("SettingsViewModel", "Error saving backup", e)
            }
        }
    }


    fun shareBackup() {
        _uiState.value.exportedBackupFile?.let { file ->
            shareBackupFile(file)
        }
    }

    private fun shareBackupFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Cashiro Backup")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Share Backup").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Error sharing backup file", e)
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(importExportMessage = "Importing backup...") }
                when (val result = backupImporter.importBackup(uri, ImportStrategy.MERGE)) {
                    is ImportResult.Success -> {
                        _uiState.update { it.copy(importExportMessage = "Import successful! Imported ${result.importedTransactions} transactions, ${result.importedCategories} categories. Skipped ${result.skippedDuplicates} duplicates.") }
                    }
                    is ImportResult.Error -> {
                        _uiState.update { it.copy(importExportMessage = "Import failed: ${result.message}") }
                        Log.e("SettingsViewModel", "Import failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(importExportMessage = "Import error: ${e.message}") }
                Log.e("SettingsViewModel", "Import error", e)
            }
        }
    }

    fun clearImportExportMessage() {
        _uiState.update { it.copy(importExportMessage = null) }
    }

    fun seedSampleData() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSeeding = true) }

                val now = LocalDateTime.now()

                // HDFC Bank (Savings) with History
                val hdfcLast4 = "1234"
                for (i in 4 downTo 0) {
                    accountBalanceRepository.insertBalance(
                        AccountBalanceEntity(
                            bankName = "HDFC Bank",
                            accountLast4 = hdfcLast4,
                            balance = BigDecimal(50000 - (i * 1000)),
                            timestamp = now.minusDays(i.toLong()),
                            sourceType = "MANUAL",
                            iconResId = R.drawable.type_finance_bank,
                            iconName = IconResolutionUtils.resIdToName(context, R.drawable.type_finance_bank),
                            color = "#33B5E5",
                            isSample = true
                        )
                    )
                }

                // ICICI Bank (Credit Card)
                accountBalanceRepository.insertBalance(
                    AccountBalanceEntity(
                        bankName = "ICICI Bank",
                        accountLast4 = "5678",
                        balance = BigDecimal(25000),
                        creditLimit = BigDecimal(100000),
                        timestamp = now,
                        isCreditCard = true,
                        sourceType = "MANUAL",
                        iconResId = R.drawable.type_stationary_card_file_box,
                        iconName = IconResolutionUtils.resIdToName(context, R.drawable.type_stationary_card_file_box),
                        color = "#E91E63",
                        isSample = true
                    )
                )

                // SBI Bank (Current)
                accountBalanceRepository.insertBalance(
                    AccountBalanceEntity(
                        bankName = "SBI Bank",
                        accountLast4 = "9012",
                        balance = BigDecimal(75000),
                        timestamp = now,
                        iconResId = R.drawable.type_finance_bank,
                        iconName = IconResolutionUtils.resIdToName(context, R.drawable.type_finance_bank),
                        color = "#1976D2",
                        isSample = true
                    )
                )

                // Cash (Wallet)
                accountBalanceRepository.insertBalance(
                    AccountBalanceEntity(
                        bankName = "Cash",
                        accountLast4 = "wallet", // Special identifier for wallet
                        balance = BigDecimal(2500),
                        timestamp = now,
                        sourceType = "MANUAL",
                        isWallet = true,
                        isSample = true,
                        iconResId = R.drawable.type_finance_dollar_banknote,
                        iconName = IconResolutionUtils.resIdToName(context, R.drawable.type_finance_dollar_banknote)
                    )
                )

                // Linked Card for HDFC
                cardRepository.insertCard(
                    CardEntity(
                        cardLast4 = "4321",
                        cardType = CardType.DEBIT,
                        bankName = "HDFC Bank",
                        accountLast4 = hdfcLast4,
                        nickname = "Salary Card",
                        lastBalance = BigDecimal(48000),
                        lastBalanceSource = "Your HDFC Bank account ending in 1234 has been credited with INR 50,000.00. Avl bal: INR 48,000.00",
                        lastBalanceDate = now,
                        isSample = true
                    )
                )

                // Unlinked Card
                cardRepository.insertCard(
                    CardEntity(
                        cardLast4 = "8765",
                        cardType = CardType.DEBIT,
                        bankName = "Axis Bank",
                        nickname = "Travel Card",
                        lastBalance = BigDecimal(15420),
                        lastBalanceSource = "Bank Alert: Your Axis Bank Card XX8765 was used for a transaction of INR 500 at STARBUCKS. Avl Bal: INR 15,420.75",
                        lastBalanceDate = now,
                        isSample = true
                    )
                )

                // --- SEED BUDGETS & TRANSACTIONS FOR HISTORY ---
                val foodCategory = "Food & Drinks"
                val entertainmentCategory = "Entertainment"

                // 1. Monthly Food Budget (Active in current month, but history goes back)
                val foodBudgetStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
                val foodBudgetId = budgetRepository.insertBudget(
                    BudgetEntity(
                        name = "Monthly Food",
                        amount = BigDecimal(15000),
                        year = foodBudgetStart.year,
                        month = foodBudgetStart.monthValue,
                        startDate = foodBudgetStart,
                        endDate = foodBudgetStart.plusMonths(1).minusSeconds(1),
                        periodType = BudgetPeriod.MONTHLY,
                        trackType = BudgetTrackType.ALL_TRANSACTIONS,
                        budgetType = BudgetType.EXPENSE,
                        createdAt = now.minusMonths(3).withDayOfMonth(1), // History from 3 months ago
                        color = "#FF9800", // Orange
                        isSample = true
                    )
                )

                // Seed transactions for Monthly Food (Last 4 months inclusive)
                for (monthOffset in 0..3) {
                    val monthDate = now.minusMonths(monthOffset.toLong())
                    val baseAmount = when(monthOffset) {
                        0 -> 8000 // Current month (ongoing)
                        1 -> 16500 // Last month (over budget)
                        2 -> 14000 // 2 months ago (within budget)
                        3 -> 12000 // 3 months ago (within budget)
                        else -> 10000
                    }
                    
                    // Split into few transactions per month
                    val foodMerchants = listOf("Swiggy", "Zomato", "Blinkit", "Zepto")
                    for (i in 1..4) {
                        transactionRepository.insertTransaction(
                            TransactionEntity(
                                amount = BigDecimal(baseAmount / 4),
                                merchantName = foodMerchants[i-1],
                                category = foodCategory,
                                subcategory = when(foodMerchants[i-1]) {
                                    "Swiggy", "Zomato" -> "Eating out"
                                    "Blinkit", "Zepto" -> "Groceries"
                                    else -> "Eating out"
                                },
                                transactionType = TransactionType.EXPENSE,
                                dateTime = monthDate.withDayOfMonth(i * 5).withHour(12),
                                transactionHash = UUID.randomUUID().toString(),
                                currency = "INR",
                                bankName = "HDFC Bank",
                                accountNumber = hdfcLast4,
                                isSample = true
                            )
                        )
                    }
                }

                // 2. Weekly Entertainment Budget (Active this week, history goes back)
                val weeklyStart = now.with(java.time.DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0).withNano(0)
                budgetRepository.insertBudget(
                    BudgetEntity(
                        name = "Weekly Fun",
                        amount = BigDecimal(2000),
                        year = weeklyStart.year,
                        month = weeklyStart.monthValue,
                        startDate = weeklyStart,
                        endDate = weeklyStart.plusWeeks(1).minusSeconds(1),
                        periodType = BudgetPeriod.WEEKLY,
                        trackType = BudgetTrackType.ALL_TRANSACTIONS,
                        budgetType = BudgetType.EXPENSE,
                        createdAt = now.minusWeeks(4), // History from 4 weeks ago
                        isSample = true,
                        color = "#9C27B0" // Purple
                    )
                )

                // Seed transactions for Weekly Fun (Last 5 weeks inclusive)
                val entertainmentMerchants = listOf("Netflix", "Spotify", "BookMyShow", "PVR", "Youtube")
                for (weekOffset in 0..4) {
                    val weekDate = now.minusWeeks(weekOffset.toLong())
                    val amount = when(weekOffset) {
                        0 -> 1200 // Current week
                        1 -> 2500 // Last week (over)
                        2 -> 1800 // 2 weeks ago
                        3 -> 2100 // 3 weeks ago (over)
                        4 -> 1500 // 4 weeks ago
                        else -> 1000
                    }

                    transactionRepository.insertTransaction(
                        TransactionEntity(
                            amount = BigDecimal(amount),
                            merchantName = entertainmentMerchants[weekOffset],
                            category = entertainmentCategory,
                            subcategory = when(entertainmentMerchants[weekOffset]) {
                                "Netflix", "Spotify", "Youtube" -> "Subscription"
                                else -> "Movies"
                            },
                            transactionType = TransactionType.EXPENSE,
                            dateTime = weekDate.withHour(20),
                            transactionHash = UUID.randomUUID().toString(),
                            isSample = true,
                            currency = "INR"
                        )
                    )
                }

                 // 3. Savings Budget: New Car Fund
                 val savingsStart = now.withDayOfMonth(1).withHour(0).withMinute(0)
                 budgetRepository.insertBudget(
                    BudgetEntity(
                        name = "New Car Fund",
                        amount = BigDecimal(500000),
                        year = savingsStart.year,
                        month = savingsStart.monthValue,
                        startDate = savingsStart,
                        endDate = savingsStart.plusYears(2), // 2 year goal
                        periodType = BudgetPeriod.CUSTOM,
                        trackType = BudgetTrackType.ALL_TRANSACTIONS,
                        budgetType = BudgetType.SAVINGS,
                        createdAt = now,
                        color = "#4CAF50", // Green
                        isSample = true
                    )
                 )

                 // --- SEED SUBSCRIPTIONS & THEIR TRANSACTIONS ---
                 val today = LocalDate.now()
                 
                 // 1. Weekly Subscription: Fruits & Vegetables
                 val weeklyFruits = SubscriptionEntity(
                     merchantName = "BigBasket",
                     amount = BigDecimal(500),
                     nextPaymentDate = today.plusDays(3),
                     billingCycle = "WEEKLY",
                     category = "Groceries",
                     bankName = "HDFC Bank",
                     isSample = true
                 )
                 val weeklyFruitsId = subscriptionRepository.insertSubscription(weeklyFruits)
                 
                 // Transactions for weekly fruits (Last 4 weeks)
                 for (i in 0..3) {
                     transactionRepository.insertTransaction(
                         TransactionEntity(
                             amount = BigDecimal(500),
                             merchantName = "BigBasket",
                             category = "Groceries",
                             transactionType = TransactionType.EXPENSE,
                             dateTime = now.minusWeeks(i.toLong()).withHour(10),
                             transactionHash = UUID.randomUUID().toString(),
                             bankName = "HDFC Bank",
                             accountNumber = hdfcLast4,
                             isSample = true,
                             billingCycle = "WEEKLY"
                         )
                     )
                 }

                 // 2. Monthly Subscription: Netflix
                 val netflix = SubscriptionEntity(
                     merchantName = "Netflix",
                     amount = BigDecimal(499),
                     nextPaymentDate = today.withDayOfMonth(15),
                     billingCycle = "MONTHLY",
                     category = "Entertainment",
                     bankName = "ICICI Bank",
                     isSample = true
                 )
                 val netflixId = subscriptionRepository.insertSubscription(netflix)
                 
                 // Transactions for Netflix (Last 3 months)
                 for (i in 1..3) {
                     transactionRepository.insertTransaction(
                         TransactionEntity(
                             amount = BigDecimal(499),
                             merchantName = "Netflix",
                             category = "Entertainment",
                             transactionType = TransactionType.EXPENSE,
                             dateTime = now.minusMonths(i.toLong()).withDayOfMonth(15).withHour(0),
                             transactionHash = UUID.randomUUID().toString(),
                             bankName = "ICICI Bank",
                             accountNumber = "5678",
                             isSample = true,
                             billingCycle = "MONTHLY"
                         )
                     )
                 }

                 // 3. Monthly Subscription: Rent
                 val rent = SubscriptionEntity(
                     merchantName = "NoBroker Rent",
                     amount = BigDecimal(25000),
                     nextPaymentDate = today.plusMonths(1).withDayOfMonth(1),
                     billingCycle = "MONTHLY",
                     category = "Bill",
                     bankName = "HDFC Bank",
                     isSample = true
                 )
                 val rentId = subscriptionRepository.insertSubscription(rent)
                 
                 // Transactions for Rent (Last 6 months)
                 for (i in 0..5) {
                     transactionRepository.insertTransaction(
                         TransactionEntity(
                             amount = BigDecimal(25000),
                             merchantName = "NoBroker Rent",
                             category = "Bill",
                             transactionType = TransactionType.EXPENSE,
                             dateTime = now.minusMonths(i.toLong()).withDayOfMonth(1).withHour(9),
                             transactionHash = UUID.randomUUID().toString(),
                             bankName = "HDFC Bank",
                             accountNumber = hdfcLast4,
                             isSample = true,
                             billingCycle = "MONTHLY"
                         )
                     )
                 }

                 // 4. Yearly Subscription: Amazon Prime
                 val prime = SubscriptionEntity(
                     merchantName = "Amazon Prime",
                     amount = BigDecimal(1499),
                     nextPaymentDate = today.minusMonths(2).plusYears(1),
                     billingCycle = "YEARLY",
                     category = "Shopping",
                     bankName = "SBI Bank",
                     isSample = true
                 )
                 val primeId = subscriptionRepository.insertSubscription(prime)
                 
                 // Transaction for Prime (10 months ago)
                 transactionRepository.insertTransaction(
                     TransactionEntity(
                         amount = BigDecimal(1499),
                         merchantName = "Amazon Prime",
                         category = "Shopping",
                         transactionType = TransactionType.EXPENSE,
                         dateTime = now.minusMonths(10),
                         transactionHash = UUID.randomUUID().toString(),
                         bankName = "SBI Bank",
                         accountNumber = "9012",
                         isSample = true,
                         billingCycle = "YEARLY"
                     )
                 )

                userPreferencesRepository.setSampleDataSeeded(true)

                _uiState.update {
                    it.copy(
                        isSeeding = false,
                        seedMessage = "Sample data (including history) seeded successfully"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSeeding = false,
                        seedMessage = "Failed to seed sample data: ${e.message}"
                    )
                }
            }
        }
    }

    fun removeSampleData() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSeeding = true) }

                transactionRepository.deleteSampleTransactions()
                accountBalanceRepository.deleteSampleBalances()
                cardRepository.deleteSampleCards()
                budgetRepository.deleteSampleBudgets()
                subscriptionRepository.deleteSampleSubscriptions()
                
                userPreferencesRepository.setSampleDataSeeded(false)

                _uiState.update {
                    it.copy(
                        isSeeding = false,
                        seedMessage = "Sample data removed successfully"
                    )
                }
                delay(3000)
                _uiState.update { it.copy(seedMessage = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSeeding = false,
                        seedMessage = "Failed to remove sample data: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearSeedMessage() {
        _uiState.update { it.copy(seedMessage = null) }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            try {
                transactionRepository.deleteAllTransactions()
                accountBalanceRepository.deleteAllBalances()
                budgetRepository.deleteAllBudgets()
                subscriptionRepository.deleteAllSubscriptions()
                cardRepository.deleteAllCards()
                ruleRepository.deleteAllRules()
                merchantMappingRepository.deleteAllMappings()
                unrecognizedSmsRepository.deleteAll()
                
                // Clear some relevant preferences
                userPreferencesRepository.setSampleDataSeeded(false)
                
                _uiState.update { it.copy(seedMessage = "All data deleted successfully") }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error deleting data", e)
                _uiState.update { it.copy(seedMessage = "Failed to delete data: ${e.message}") }
            }
        }
    }
}
