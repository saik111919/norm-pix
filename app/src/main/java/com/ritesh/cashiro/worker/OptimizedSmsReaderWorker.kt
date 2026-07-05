package com.ritesh.cashiro.worker

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ritesh.cashiro.data.database.entity.UnrecognizedSmsEntity
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import com.ritesh.cashiro.data.repository.AccountBalanceRepository
import com.ritesh.cashiro.data.repository.LlmRepository
import com.ritesh.cashiro.data.repository.SubscriptionRepository
import com.ritesh.cashiro.data.repository.TransactionRepository
import com.ritesh.cashiro.data.repository.UnrecognizedSmsRepository
import com.ritesh.cashiro.data.manager.SmsTransactionProcessor
import com.ritesh.cashiro.utils.PiiRedactor
import com.ritesh.cashiro.worker.OptimizedSmsReaderWorker.Companion.TAG
import com.ritesh.parser.core.ParsedTransaction
import com.ritesh.parser.core.SmsFilter
import com.ritesh.parser.core.bank.BankParserFactory
import com.ritesh.parser.core.bank.FederalBankParser
import com.ritesh.parser.core.bank.HDFCBankParser
import com.ritesh.parser.core.bank.IndianBankParser
import com.ritesh.parser.core.bank.SBIBankParser
import com.ritesh.parser.core.bank.IndusIndBankParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.system.measureTimeMillis

/**
 * Optimized SMS Worker with parallel processing and progress tracking.
 * This worker provides significant performance improvements through:
 * 1. Parallel processing of SMS messages
 * 2. Progress reporting with estimated time completion
 * 3. Optimized database operations
 * 4. Efficient memory usage
 */
@HiltWorker
class OptimizedSmsReaderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val llmRepository: LlmRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val unrecognizedSmsRepository: UnrecognizedSmsRepository,
    private val smsTransactionProcessor: SmsTransactionProcessor
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "OptimizedSmsReaderWorker"
        const val WORK_NAME = "optimized_sms_reader_work"

        // Input keys
        const val INPUT_FORCE_RESYNC = "input_force_resync"

        // Progress keys
        const val PROGRESS_TOTAL = "progress_total"
        const val PROGRESS_PROCESSED = "progress_processed"
        const val PROGRESS_PARSED = "progress_parsed"
        const val PROGRESS_SAVED = "progress_saved"
        const val PROGRESS_BLOCKED = "progress_blocked"
        const val PROGRESS_TIME_ELAPSED = "progress_time_elapsed"
        const val PROGRESS_ESTIMATED_TIME_REMAINING = "progress_estimated_time_remaining"
        const val PROGRESS_CURRENT_BATCH = "progress_current_batch"
        const val PROGRESS_TOTAL_BATCHES = "progress_total_batches"

        // SMS Content Provider columns
        private val SMS_PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.BODY,
            Telephony.Sms.TYPE
        )

        // Parallel processing configuration
        private const val PROGRESS_REPORT_INTERVAL = 10 // Report progress every 10 messages
    }

    /**
     * Calculates optimal batch size based on available cores and total messages
     */
    private fun calculateOptimalBatchSize(totalMessages: Int, availableCores: Int): Int {
        return when {
            totalMessages < 100 -> 10 // Small datasets: small batches for better progress tracking
            totalMessages < 500 -> 25 // Medium datasets: moderate batches
            totalMessages < 2000 -> 50 // Large datasets: standard batches
            else -> {
                // Very large datasets: scale batch size with cores but cap at reasonable limit
                val coreBasedBatch = availableCores * 15
                minOf(coreBasedBatch, 200)
            }
        }
    }

    /**
     * Calculates optimal parallelism based on available cores and message count
     *
     * IMPORTANT: Sequential processing (parallelism=1) to prevent race conditions
     * in balance calculations. When multiple threads process transactions for the
     * same account simultaneously, they read the same "previous balance" value,
     * causing incorrect balance calculations.
     */
    private fun calculateParseParallelism(availableCores: Int): Int {
        return maxOf(1, availableCores - 1)
    }

    data class ProcessingStats(
        var totalMessages: Int = 0,
        var processedMessages: Int = 0,
        var parsedTransactions: Int = 0,
        var savedTransactions: Int = 0,
        var blockedTransactions: Int = 0,
        var subscriptionCount: Int = 0,
        var startTime: Long = System.currentTimeMillis(),
        var messagesPerSecond: Double = 0.0
    ) {
        fun updateTimeElapsed(): Long = System.currentTimeMillis() - startTime

        fun updateMessagesPerSecond() {
            val elapsedSeconds = updateTimeElapsed() / 1000.0
            messagesPerSecond = if (elapsedSeconds > 0) processedMessages / elapsedSeconds else 0.0
        }

        fun getEstimatedTimeRemaining(): Long {
            return if (messagesPerSecond > 0 && processedMessages > 0) {
                val remainingMessages = totalMessages - processedMessages
                (remainingMessages / messagesPerSecond * 1000).toLong()
            } else 0L
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Check if this is a force resync request
            val forceResync = inputData.getBoolean(INPUT_FORCE_RESYNC, false)
            Log.d(TAG, "Starting optimized SMS reading and parsing work... (forceResync: $forceResync)")

            // If force resync, clear existing data first
            if (forceResync) {
                Log.d(TAG, "Force resync: Clearing existing transactions and account balances...")
                transactionRepository.deleteAllTransactions()
                accountBalanceRepository.deleteAllBalances()
                Log.d(TAG, "Force resync: Database cleared, starting fresh scan")
            }

            val stats = ProcessingStats()

            // Calculate scan parameters
            val lastScanTimestamp = userPreferencesRepository.getLastScanTimestamp().first() ?: 0L
            val scanMonths = userPreferencesRepository.getSmsScanMonths()
            val scanAllTime = userPreferencesRepository.getSmsScanAllTime()
            val lastScanPeriod = userPreferencesRepository.getLastScanPeriod().first() ?: 0
            val now = System.currentTimeMillis()

            val needsFullScan = forceResync || lastScanTimestamp == 0L || scanAllTime || scanMonths > lastScanPeriod

            val scanStartTime = if (needsFullScan) {
                val calendar = java.util.Calendar.getInstance().apply {
                    if (scanAllTime) {
                        add(java.util.Calendar.YEAR, -10)
                    } else {
                        add(java.util.Calendar.MONTH, -scanMonths)
                    }
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                calendar.timeInMillis
            } else {
                val threeDaysAgo = now - (3 * 24 * 60 * 60 * 1000L)
                val periodLimit = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.MONTH, -scanMonths)
                }.timeInMillis

                maxOf(
                    minOf(lastScanTimestamp, threeDaysAgo),
                    periodLimit
                )
            }

            // Get total count upfront for stats
            val totalMsgCount = getSmsAndRcsCount(scanStartTime)
            stats.totalMessages = totalMsgCount
            Log.d(TAG, "Found $totalMsgCount SMS & RCS messages to process")

            // Calculate optimal batch size and parse parallelism
            val availableCores = Runtime.getRuntime().availableProcessors()
            val batchSize = calculateOptimalBatchSize(totalMsgCount, availableCores)
            val parseParallelism = calculateParseParallelism(availableCores)

            Log.d(TAG, "Auto-calculated optimization parameters:")
            Log.d(TAG, "- Available CPU cores: $availableCores")
            Log.d(TAG, "- Batch size: $batchSize")
            Log.d(TAG, "- Parse parallelism: $parseParallelism")
            Log.d(TAG, "- Total batches: ${(totalMsgCount + batchSize - 1) / batchSize}")

            // Update scan tracking immediately
            userPreferencesRepository.setLastScanTimestamp(System.currentTimeMillis())
            if (needsFullScan) {
                userPreferencesRepository.setLastScanPeriod(scanMonths)
            }

            // Report initial progress
            setProgress(
                workDataOf(
                    PROGRESS_TOTAL to totalMsgCount,
                    PROGRESS_PROCESSED to 0,
                    PROGRESS_PARSED to 0,
                    PROGRESS_SAVED to 0,
                    PROGRESS_TIME_ELAPSED to 0L,
                    PROGRESS_ESTIMATED_TIME_REMAINING to 0L,
                    PROGRESS_CURRENT_BATCH to 1,
                    PROGRESS_TOTAL_BATCHES to (totalMsgCount + batchSize - 1) / batchSize
                )
            )

            // Process messages via 3-stage channel pipeline
            val processingTime = measureTimeMillis {
                processWithChannelPipeline(scanStartTime, stats, batchSize, parseParallelism)
            }

            stats.updateTimeElapsed()
            stats.updateMessagesPerSecond()

            Log.d(
                TAG, """
                SMS parsing completed in ${processingTime}ms:
                - Total Messages: ${stats.totalMessages}
                - Processed: ${stats.processedMessages}
                - Parsed Transactions: ${stats.parsedTransactions}
                - Saved Transactions: ${stats.savedTransactions}
                - Subscriptions: ${stats.subscriptionCount}
                - Processing Speed: ${"%.2f".format(stats.messagesPerSecond)} msg/sec
            """.trimIndent()
            )

            // Clean up old unrecognized SMS entries
            try {
                unrecognizedSmsRepository.cleanupOldEntries()
                Log.d(TAG, "Cleaned up old unrecognized SMS entries")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up unrecognized SMS: ${e.message}")
            }

            // Update system prompt with new financial data if any transactions were saved
            if (stats.savedTransactions > 0) {
                try {
                    llmRepository.updateSystemPrompt()
                    Log.d(TAG, "Updated system prompt with latest financial data")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating system prompt: ${e.message}")
                }
            }

            // Report final progress
            setProgress(
                workDataOf(
                    PROGRESS_TOTAL to totalMsgCount,
                    PROGRESS_PROCESSED to totalMsgCount,
                    PROGRESS_PARSED to stats.parsedTransactions,
                    PROGRESS_SAVED to stats.savedTransactions,
                    PROGRESS_TIME_ELAPSED to stats.updateTimeElapsed(),
                    PROGRESS_ESTIMATED_TIME_REMAINING to 0L,
                    PROGRESS_CURRENT_BATCH to (totalMsgCount + batchSize - 1) / batchSize,
                    PROGRESS_TOTAL_BATCHES to (totalMsgCount + batchSize - 1) / batchSize
                )
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in optimized SMS parsing work", e)
            Result.failure()
        }
    }

    private suspend fun processWithChannelPipeline(
        scanStartTime: Long,
        stats: ProcessingStats,
        batchSize: Int,
        parseParallelism: Int
    ) = coroutineScope {
        val totalBatches = (stats.totalMessages + batchSize - 1) / batchSize

        val inputChannel = Channel<SmsMessage>(Channel.UNLIMITED)
        val outputChannel = Channel<ParseResult>(Channel.UNLIMITED)

        val atomicProcessed = java.util.concurrent.atomic.AtomicInteger(0)

        // Stage 1: Feed — stream messages into input channel
        val feedJob = launch {
            streamSmsToChannel(inputChannel, scanStartTime)
            streamRcsToChannel(inputChannel, scanStartTime)
            inputChannel.close()
        }

        // Stage 2: Parse — N coroutines pull from input, parse, emit results
        val parseJobs = (0 until parseParallelism).map { _ ->
            launch(Dispatchers.IO) {
                for (msg in inputChannel) {
                    val result = parseMessage(msg)
                    outputChannel.send(result)
                    atomicProcessed.incrementAndGet()
                }
            }
        }

        // Stage 3: Save — single coroutine, sequential DB writes
        var parsedCount = 0
        var savedCount = 0
        val saveJob = launch {
            for (result in outputChannel) {
                when (result) {
                    is ParseResult.Transaction -> {
                        parsedCount++
                        val success = smsTransactionProcessor.saveParsedTransaction(
                            result.parsed,
                            result.smsBody
                        ).success
                        if (success) savedCount++
                    }
                    is ParseResult.Subscription -> { /* counted during parse */ }
                    is ParseResult.Unrecognized -> { /* already stored during parse */ }
                    is ParseResult.Skipped -> { /* no-op */ }
                }
            }
        }

        // Progress monitoring
        val progressJob = launch {
            var lastReported = 0
            while (atomicProcessed.get() < stats.totalMessages) {
                val current = atomicProcessed.get()
                if (current - lastReported >= PROGRESS_REPORT_INTERVAL || current >= stats.totalMessages) {
                    stats.processedMessages = current
                    stats.parsedTransactions = parsedCount
                    stats.savedTransactions = savedCount
                    stats.updateMessagesPerSecond()
                    setProgress(
                        workDataOf(
                            PROGRESS_TOTAL to stats.totalMessages,
                            PROGRESS_PROCESSED to current,
                            PROGRESS_PARSED to parsedCount,
                            PROGRESS_SAVED to savedCount,
                            PROGRESS_TIME_ELAPSED to stats.updateTimeElapsed(),
                            PROGRESS_ESTIMATED_TIME_REMAINING to stats.getEstimatedTimeRemaining(),
                            PROGRESS_CURRENT_BATCH to (current + batchSize - 1) / batchSize,
                            PROGRESS_TOTAL_BATCHES to totalBatches
                        )
                    )
                    lastReported = current
                }
                delay(50)
            }
        }

        feedJob.join()
        parseJobs.forEach { it.join() }
        outputChannel.close()
        saveJob.join()
        progressJob.cancel()

        stats.processedMessages = stats.totalMessages
        stats.parsedTransactions = parsedCount
        stats.savedTransactions = savedCount
        stats.updateMessagesPerSecond()

        setProgress(
            workDataOf(
                PROGRESS_TOTAL to stats.totalMessages,
                PROGRESS_PROCESSED to stats.totalMessages,
                PROGRESS_PARSED to parsedCount,
                PROGRESS_SAVED to savedCount,
                PROGRESS_TIME_ELAPSED to stats.updateTimeElapsed(),
                PROGRESS_ESTIMATED_TIME_REMAINING to 0L,
                PROGRESS_CURRENT_BATCH to totalBatches,
                PROGRESS_TOTAL_BATCHES to totalBatches
            )
        )
    }

private suspend fun parseMessage(sms: SmsMessage): ParseResult {
    return try {
        val senderUpper = sms.sender.uppercase()
        val isKnownBank = BankParserFactory.isKnownBankSender(sms.sender)
        if ((senderUpper.endsWith("-P") || senderUpper.endsWith("-G")) && !isKnownBank) {
            return ParseResult.Skipped("Promotional/government message")
        }

        val matchingParsers = BankParserFactory.getParsers(sms.sender)
        if (matchingParsers.isEmpty()) {
            val upperSender = sms.sender.uppercase()
            if (upperSender.endsWith("-T") || upperSender.endsWith("-S")) {
                processUnrecognizedSms(sms)
            }
            return ParseResult.Skipped("No parser found")
        }

        val firstParser = matchingParsers.first()
        val smsDateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(sms.timestamp),
            ZoneId.systemDefault()
        )
        val thirtyDaysAgo = LocalDateTime.now().minusDays(30)
        val isRecentMessage = smsDateTime.isAfter(thirtyDaysAgo)

        val subscriptionResult = processSubscriptionNotifications(
            firstParser, sms, smsDateTime, isRecentMessage
        )
        if (subscriptionResult.shouldSkipTransaction) {
            return ParseResult.Subscription(subscriptionResult.subscriptionCount)
        }

        val parsedTransaction = matchingParsers.firstNotNullOfOrNull { parser ->
            parser.parse(sms.body, sms.sender, sms.timestamp)
        }

        if (parsedTransaction != null) {
            Log.d(TAG, """
                Parsed: ${parsedTransaction.bankName}
                Amount: ${parsedTransaction.amount} Type: ${parsedTransaction.type}
                Merchant: ${parsedTransaction.merchant}
            """.trimIndent())
            ParseResult.Transaction(parsedTransaction, sms.timestamp, sms.body)
        } else {
            if (isRecentMessage) {
                Log.d(TAG, "Failed to parse from ${sms.sender}: ${sms.body.take(100)}...")
            }
            ParseResult.Skipped("Parsing returned null")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing SMS from ${sms.sender}: ${e.message}")
        ParseResult.Skipped("Error: ${e.message}")
    }
}

private sealed class ParseResult {
    data class Transaction(
        val parsed: ParsedTransaction,
        val timestamp: Long,
        val smsBody: String
    ) : ParseResult()

    data class Subscription(val count: Int) : ParseResult()
    data class Unrecognized(val sms: SmsMessage) : ParseResult()
    data class Skipped(val reason: String) : ParseResult()
}

private data class SubscriptionResult(
    val shouldSkipTransaction: Boolean,
    val subscriptionCount: Int
)

private suspend fun processSubscriptionNotifications(
    parser: com.ritesh.parser.core.bank.BankParser,
    sms: SmsMessage,
    smsDateTime: LocalDateTime,
    isRecentMessage: Boolean
): SubscriptionResult {
    return when (parser) {
        is SBIBankParser -> {
            if (parser.isUPIMandateNotification(sms.body)) {
                if (!isRecentMessage) {
                    Log.d(TAG, "Skipping old SBI UPI-Mandate from ${smsDateTime.toLocalDate()}")
                    return SubscriptionResult(
                        false,
                        0
                    ) // Continue with transaction parsing for old messages
                }

                val upiMandateInfo = parser.parseUPIMandateSubscription(sms.body)
                if (upiMandateInfo != null) {
                    try {
                        val subscriptionId = subscriptionRepository.createOrUpdateFromSBIMandate(
                            upiMandateInfo,
                            parser.getBankName(),
                            sms.body
                        )
                        Log.d(
                            TAG,
                            "Created/Updated SBI UPI-Mandate subscription: $subscriptionId for ${upiMandateInfo.merchant}"
                        )
                        return SubscriptionResult(
                            true,
                            1
                        ) // Skip transaction parsing, count 1 subscription
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving SBI UPI-Mandate subscription: ${e.message}")
                    }
                }
            }
            SubscriptionResult(false, 0) // Continue with transaction parsing
        }

        is FederalBankParser -> {
            // Check for E-Mandate creation notifications
            // Note: Mandate creation messages should be processed regardless of age
            // as they create future subscriptions
            if (parser.isMandateCreationNotification(sms.body)) {
                // Don't skip old mandate creation messages - they create future subscriptions
                if (!isRecentMessage) {
                    Log.d(
                        TAG,
                        "Processing older Federal Bank Mandate Creation from ${smsDateTime.toLocalDate()} - mandate creation processed regardless of age"
                    )
                }

                val eMandateInfo = parser.parseEMandateSubscription(sms.body)
                if (eMandateInfo != null) {
                    try {
                        val subscriptionId = subscriptionRepository.createOrUpdateFromFederalBankMandate(
                            eMandateInfo,
                            parser.getBankName(),
                            sms.body
                        )
                        Log.d(
                            TAG,
                            "Created/Updated Federal Bank E-Mandate subscription: $subscriptionId for ${eMandateInfo.merchant}"
                        )
                        return SubscriptionResult(
                            true,
                            1
                        ) // Skip transaction parsing, count 1 subscription
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving Federal Bank E-Mandate subscription: ${e.message}")
                    }
                }
            }

            // Check for Future Debit notifications (payment due messages)
            // Note: Payment due messages should be processed regardless of age if they're for future dates
            val futureDebitInfo = parser.parseFutureDebit(sms.body)
            if (futureDebitInfo != null) {
                // Check if the payment due date is in the future
                val isFuturePayment = try {
                    val paymentDate = java.time.LocalDate.parse(
                        futureDebitInfo.nextDeductionDate,
                        java.time.format.DateTimeFormatter.ofPattern(futureDebitInfo.dateFormat)
                    )
                    paymentDate.isAfter(java.time.LocalDate.now())
                } catch (e: Exception) {
                    // If we can't parse the date, assume it's recent and apply normal filtering
                    isRecentMessage
                }

                if (!isFuturePayment && !isRecentMessage) {
                    Log.d(
                        TAG,
                        "Skipping old Federal Bank Future Debit from ${smsDateTime.toLocalDate()} - payment date is not in future"
                    )
                    return SubscriptionResult(
                        false,
                        0
                    ) // Continue with transaction parsing for old messages
                }

                // Process future debit messages regardless of SMS age if payment date is future
                if (!isRecentMessage && isFuturePayment) {
                    Log.d(
                        TAG,
                        "Processing older Federal Bank Future Debit from ${smsDateTime.toLocalDate()} - payment date is future: ${futureDebitInfo.nextDeductionDate}"
                    )
                }

                try {
                    val subscriptionId = subscriptionRepository.createOrUpdateFromFederalBankMandate(
                        futureDebitInfo,
                        parser.getBankName(),
                        sms.body
                    )
                    Log.d(
                        TAG,
                        "Created/Updated Federal Bank future debit subscription: $subscriptionId for ${futureDebitInfo.merchant}"
                    )
                    return SubscriptionResult(
                        true,
                        1
                    ) // Skip transaction parsing, count 1 subscription
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving Federal Bank future debit subscription: ${e.message}")
                }
            }

            SubscriptionResult(false, 0) // Continue with transaction parsing if no mandate found
        }

        is HDFCBankParser -> {
            var subscriptionCount = 0

            // Check for E-Mandate notifications
            if (parser.isEMandateNotification(sms.body)) {
                if (!isRecentMessage) {
                    Log.d(TAG, "Skipping old HDFC E-Mandate from ${smsDateTime.toLocalDate()}")
                    return SubscriptionResult(
                        false,
                        0
                    ) // Continue with transaction parsing for old messages
                }

                val eMandateInfo = parser.parseEMandateSubscription(sms.body)
                if (eMandateInfo != null) {
                    try {
                        val subscriptionId = subscriptionRepository.createOrUpdateFromEMandate(
                            eMandateInfo,
                            parser.getBankName(),
                            sms.body
                        )
                        Log.d(
                            TAG,
                            "Created/Updated HDFC E-Mandate subscription: $subscriptionId for ${eMandateInfo.merchant}"
                        )
                        subscriptionCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving HDFC E-Mandate subscription: ${e.message}")
                    }
                }
            }

            // Check for Future Debit notifications
            if (parser.isFutureDebitNotification(sms.body)) {
                if (!isRecentMessage) {
                    Log.d(TAG, "Skipping old HDFC Future Debit from ${smsDateTime.toLocalDate()}")
                    return SubscriptionResult(
                        false,
                        0
                    ) // Continue with transaction parsing for old messages
                }

                val futureDebitInfo = parser.parseFutureDebit(sms.body)
                if (futureDebitInfo != null) {
                    try {
                        val subscriptionId = subscriptionRepository.createOrUpdateFromEMandate(
                            futureDebitInfo,
                            parser.getBankName(),
                            sms.body
                        )
                        Log.d(
                            TAG,
                            "Created/Updated HDFC future debit subscription: $subscriptionId for ${futureDebitInfo.merchant}"
                        )
                        subscriptionCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving HDFC future debit subscription: ${e.message}")
                    }
                }
            }

            // Check for Balance Update notifications
            if (parser.isBalanceUpdateNotification(sms.body)) {
                val balanceUpdateInfo = parser.parseBalanceUpdate(sms.body)
                if (balanceUpdateInfo != null) {
                    try {
                        accountBalanceRepository.insertBalanceUpdate(
                            bankName = balanceUpdateInfo.bankName,
                            accountLast4 = balanceUpdateInfo.accountLast4,
                            balance = balanceUpdateInfo.balance,
                            timestamp = balanceUpdateInfo.asOfDate ?: smsDateTime,
                            currency = parser.getCurrency()
                        )
                        Log.d(TAG, "Saved balance update for ${balanceUpdateInfo.bankName}")
                        return SubscriptionResult(
                            true,
                            subscriptionCount
                        ) // Skip transaction parsing for balance updates
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving balance update: ${e.message}")
                    }
                }
            }

            if (subscriptionCount > 0) {
                SubscriptionResult(
                    true,
                    subscriptionCount
                ) // Skip transaction parsing for subscriptions
            } else {
                SubscriptionResult(false, 0) // Continue with transaction parsing
            }
        }

        is IndianBankParser -> {
            if (parser.isMandateNotification(sms.body)) {
                if (!isRecentMessage) {
                    Log.d(TAG, "Skipping old Indian Bank Mandate from ${smsDateTime.toLocalDate()}")
                    return SubscriptionResult(
                        false,
                        0
                    ) // Continue with transaction parsing for old messages
                }

                val mandateInfo = parser.parseMandateSubscription(sms.body)
                if (mandateInfo != null) {
                    try {
                        val subscriptionId =
                            subscriptionRepository.createOrUpdateFromIndianBankMandate(
                                mandateInfo,
                                parser.getBankName(),
                                sms.body
                            )
                        Log.d(
                            TAG,
                            "Created/Updated Indian Bank subscription: $subscriptionId for ${mandateInfo.merchant}"
                        )
                        return SubscriptionResult(
                            true,
                            1
                        ) // Skip transaction parsing, count 1 subscription
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving Indian Bank subscription: ${e.message}")
                    }
                }
            }
            SubscriptionResult(false, 0) // Continue with transaction parsing
        }

        is IndusIndBankParser -> {
            // Balance-only updates for IndusInd (hook like HDFC)
            if (parser.isBalanceUpdateNotification(sms.body)) {
                val balanceUpdateInfo = parser.parseBalanceUpdate(sms.body)
                if (balanceUpdateInfo != null) {
                    try {
                        accountBalanceRepository.insertBalanceUpdate(
                            bankName = balanceUpdateInfo.bankName,
                            accountLast4 = balanceUpdateInfo.accountLast4,
                            balance = balanceUpdateInfo.balance,
                            timestamp = balanceUpdateInfo.asOfDate ?: smsDateTime,
                            currency = parser.getCurrency()
                        )
                        Log.d(TAG, "Saved balance update for ${balanceUpdateInfo.bankName}")
                        return SubscriptionResult(true, 0) // Skip transaction parsing for balance updates
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving IndusInd balance update: ${e.message}")
                    }
                }
            }
            SubscriptionResult(false, 0)
        }

        else -> SubscriptionResult(false, 0) // Continue with transaction parsing for other banks
    }
}

private suspend fun processUnrecognizedSms(sms: SmsMessage) {
    val upperSender = sms.sender.uppercase()
    if (upperSender.endsWith("-T") || upperSender.endsWith("-S")) {
        try {
            if (!SmsFilter.isTransactionMessage(sms.body)) {
                Log.d(TAG, "Skipping non-transaction unrecognized SMS from: ${sms.sender}")
                return
            }
            val alreadyExists = unrecognizedSmsRepository.exists(sms.sender, sms.body)

            if (!alreadyExists) {
                val unrecognizedSms = UnrecognizedSmsEntity(
                    sender = sms.sender,
                    smsBody = sms.body,
                    receivedAt = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(sms.timestamp),
                        ZoneId.systemDefault()
                    )
                )
                unrecognizedSmsRepository.insert(unrecognizedSms)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error storing unrecognized SMS: ${e.message}")
        }
    }
}

private fun getSmsAndRcsCount(scanStartTime: Long): Int {
    var total = 0
    try {
        val smsCursor = applicationContext.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ?",
            arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), scanStartTime.toString()),
            null
        )
        smsCursor?.use {
            total += it.count
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error counting SMS: ${e.message}")
    }

    try {
        val scanStartTimeSeconds = scanStartTime / 1000
        val mmsCursor = applicationContext.contentResolver.query(
            Uri.parse("content://mms"),
            arrayOf("_id", "tr_id"),
            "date >= ?",
            arrayOf(scanStartTimeSeconds.toString()),
            null
        )
        mmsCursor?.use { cursor ->
            val trIdIndex = cursor.getColumnIndex("tr_id")
            while (cursor.moveToNext()) {
                val trId = if (trIdIndex >= 0) cursor.getString(trIdIndex) ?: "" else ""
                if (trId.startsWith("proto:")) {
                    // Extract sender from tr_id to verify if it's from recognized financial sender
                    val sender = extractRcsSender(trId)
                    if (sender != null) {
                        val senderUpper = sender.uppercase()
                        if (senderUpper.contains("PUNJAB NATIONAL BANK") ||
                            senderUpper.contains("DEPARTMENT OF POST") ||
                            senderUpper.contains("DOPBNK")) {
                            total++
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error counting RCS: ${e.message}")
    }
    return total
}

private suspend fun streamSmsToChannel(
    channel: Channel<SmsMessage>,
    scanStartTime: Long
) {
    try {
        val cursor = applicationContext.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            SMS_PROJECTION,
            "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ?",
            arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), scanStartTime.toString()),
            "${Telephony.Sms.DATE} ASC"  // Process oldest first (chronological order)
        )

        cursor?.use {
            val idIndex = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val typeIndex = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)

            while (it.moveToNext()) {
                val message = SmsMessage(
                    id = it.getLong(idIndex),
                    sender = it.getString(addressIndex) ?: "",
                    timestamp = it.getLong(dateIndex),
                    body = it.getString(bodyIndex) ?: "",
                    type = it.getInt(typeIndex)
                )
                channel.send(message)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error streaming SMS messages: ${e.message}", e)
    }
}

private suspend fun streamRcsToChannel(
    channel: Channel<SmsMessage>,
    scanStartTime: Long
) {
    try {
        val scanStartTimeSeconds = scanStartTime / 1000

        val mmsCursor = applicationContext.contentResolver.query(
            Uri.parse("content://mms"),
            arrayOf("_id", "thread_id", "date", "tr_id", "m_id"),
            "date >= ?",
            arrayOf(scanStartTimeSeconds.toString()),
            "date ASC"  // Process oldest first (chronological order)
        )

        mmsCursor?.use { cursor ->
            while (cursor.moveToNext()) {
                val messageId = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow("date"))
                val trIdIndex = cursor.getColumnIndex("tr_id")
                val trId = if (trIdIndex >= 0) cursor.getString(trIdIndex) ?: "" else ""

                // Check if this is an RCS message (has proto: in tr_id)
                if (trId.startsWith("proto:")) {
                    // Extract sender from tr_id (it's base64 encoded protobuf)
                    val sender = extractRcsSender(trId)

                    // Get message text from parts
                    var messageText = getRcsMessageText(messageId)

                    // If it's JSON (RCS Rich Card), extract the actual text
                    if (messageText != null && messageText.trim().startsWith("{")) {
                        messageText = extractTextFromRcsJson(messageText)
                    }

                    // Convert to SmsMessage format for processing
                    if (messageText != null && sender != null) {
                        val senderUpper = sender.uppercase()
                        // Process RCS messages from known financial senders (PNB, Department of Post)
                        if (senderUpper.contains("PUNJAB NATIONAL BANK") ||
                            senderUpper.contains("DEPARTMENT OF POST") ||
                            senderUpper.contains("DOPBNK")) {
                            Log.d(TAG, "RCS message from recognized financial sender: $sender")
                            val rcsMessage = SmsMessage(
                                id = messageId,
                                sender = sender,
                                timestamp = date * 1000, // MMS uses seconds, SMS uses milliseconds
                                body = messageText,
                                type = Telephony.Sms.MESSAGE_TYPE_INBOX
                            )
                            channel.send(rcsMessage)
                        } else {
                            Log.d(TAG, "Skipping RCS message from non-financial sender: $sender")
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error streaming RCS messages: ${e.message}")
    }
}

/**
 * Extracts sender name from RCS tr_id field
 * The tr_id contains base64 encoded protobuf data with sender info
 */
private fun extractRcsSender(trId: String): String? {
    return try {
        // Remove "proto:" prefix and decode base64
        val base64Data = trId.removePrefix("proto:")
        val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
        val decodedString = String(decodedBytes)

        // Look for sender patterns in the decoded data
        // Pattern 1: Agent ID like "ask_apollo_9xdchzx9_agent@rbm.goog"
        val agentPattern = Regex("""([a-z_]+)_[a-z0-9]+_agent@rbm\.goog""")
        agentPattern.find(decodedString)?.let { match ->
            // Convert agent ID to readable name (e.g., "ask_apollo" -> "Ask Apollo")
            return match.groupValues[1].split("_").joinToString(" ") {
                it.replaceFirstChar { char -> char.uppercase() }
            }
        }

        // Pattern 2: Look for actual sender name in the data
        // RCS messages often have the business name directly in the protobuf
        val namePattern = Regex("""[\x12\x1a][\x00-\x20]([A-Za-z][A-Za-z\s]+)""")
        namePattern.find(decodedString)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.length > 3 && name.length < 50) {
                return name
            }
        }

        // If no pattern matches, return null
        null
    } catch (e: Exception) {
        Log.e(TAG, "Error extracting RCS sender: ${e.message}")
        null
    }
}

/**
 * Gets the text content of an RCS/MMS message from its parts
 */
private fun getRcsMessageText(messageId: Long): String? {
    return try {
        // First, let's see what parts exist for this message
        val partsCursor = applicationContext.contentResolver.query(
            Uri.parse("content://mms/part"),
            null, // Get all columns to debug
            "mid = ?",
            arrayOf(messageId.toString()),
            null
        )

        partsCursor?.use { cursor ->
            while (cursor.moveToNext()) {
                val partId = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
                val ctIndex = cursor.getColumnIndex("ct")
                val contentType = if (ctIndex >= 0) cursor.getString(ctIndex) ?: "" else ""

                // Look for text content
                if (contentType.startsWith("text/") || contentType == "application/smil") {
                    // Try to get text directly from the text column
                    val textIndex = cursor.getColumnIndex("text")
                    if (textIndex >= 0) {
                        val text = cursor.getString(textIndex)
                        if (!text.isNullOrEmpty()) {
                            return text
                        }
                    }

                    // Try to read from _data path (file storage)
                    val dataIndex = cursor.getColumnIndex("_data")
                    if (dataIndex >= 0) {
                        val dataPath = cursor.getString(dataIndex)
                        if (!dataPath.isNullOrEmpty()) {
                            // Try to read the file
                            try {
                                val partUri = Uri.parse("content://mms/part/$partId")
                                val inputStream =
                                    applicationContext.contentResolver.openInputStream(partUri)
                                val text = inputStream?.bufferedReader()?.use { it.readText() }
                                if (!text.isNullOrEmpty()) {
                                    return text
                                }
                            } catch (e: Exception) {
                                // Ignore read errors
                            }
                        }
                    }
                }
            }
        }

        null
    } catch (e: Exception) {
        Log.e(TAG, "Error getting RCS message text: ${e.message}", e)
        null
    }
}

/**
 * Extracts text content from RCS JSON (Rich Cards)
 */
private fun extractTextFromRcsJson(json: String): String? {
    return try {
        val jsonObject = org.json.JSONObject(json)
        val texts = mutableListOf<String>()

        // Navigate through the JSON structure to find text
        fun extractTexts(obj: Any?, depth: Int = 0) {
            if (depth > 10) return // Prevent infinite recursion

            when (obj) {
                is org.json.JSONObject -> {
                    // Priority order for text fields
                    val textFields = listOf(
                        "text",           // Plain text message
                        "message",        // Message body
                        "body",           // Body content
                        "title",          // Card title
                        "description",    // Card description
                        "content",        // Content field
                        "caption"         // Media caption
                    )

                    for (field in textFields) {
                        if (obj.has(field)) {
                            val value = obj.getString(field)
                            if (value.isNotEmpty() && !value.startsWith("{")) {
                                texts.add(value)
                            }
                        }
                    }

                    // Recursively search nested objects
                    obj.keys().forEach { key ->
                        if (key !in listOf("media", "suggestions", "postback", "urlAction")) {
                            try {
                                extractTexts(obj.get(key), depth + 1)
                            } catch (e: Exception) {
                                // Skip problematic fields
                            }
                        }
                    }
                }

                is org.json.JSONArray -> {
                    for (i in 0 until obj.length()) {
                        extractTexts(obj.get(i), depth + 1)
                    }
                }
            }
        }

        // Check if it's a simple text message (not a rich card)
        if (jsonObject.has("text")) {
            return jsonObject.getString("text")
        }

        // Check for message.text structure
        if (jsonObject.has("message")) {
            val message = jsonObject.getJSONObject("message")
            if (message.has("text")) {
                return message.getString("text")
            }
        }

        // Extract from complex structures
        extractTexts(jsonObject)

        // Combine all found texts
        if (texts.isNotEmpty()) {
            return texts.distinct().joinToString(" | ")
        }

        // If no text found, it might be a media-only message
        null
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing RCS JSON: ${e.message}")
        // Not JSON, return as plain text
        json
    }
}

private data class SmsMessage(
    val id: Long,
    val sender: String,
    val timestamp: Long,
    val body: String,
    val type: Int
)
}
