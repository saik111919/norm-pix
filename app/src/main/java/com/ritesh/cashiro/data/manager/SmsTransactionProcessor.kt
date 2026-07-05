package com.ritesh.cashiro.data.manager

import android.util.Log
import com.ritesh.parser.core.ParsedTransaction
import com.ritesh.parser.core.bank.BankParserFactory
import com.ritesh.cashiro.data.database.entity.TransactionEntity
import com.ritesh.cashiro.data.mapper.toEntity
import com.ritesh.cashiro.data.repository.AccountBalanceRepository
import com.ritesh.cashiro.data.repository.MerchantMappingRepository
import com.ritesh.cashiro.data.repository.SubscriptionRepository
import com.ritesh.cashiro.data.repository.TransactionRepository
import com.ritesh.cashiro.domain.repository.RuleRepository
import com.ritesh.cashiro.domain.service.RuleEngine
import com.ritesh.cashiro.data.manager.TransactionDeduplication
import com.ritesh.cashiro.data.manager.DedupResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared processor for SMS transactions. Used by both SmsBroadcastReceiver
 * and OptimizedSmsReaderWorker to ensure consistent transaction processing.
 */
@Singleton
class SmsTransactionProcessor @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val merchantMappingRepository: MerchantMappingRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
    private val balanceUpdateProcessor: BalanceUpdateProcessor
) {
    companion object {
        private const val TAG = "SmsTransactionProcessor"
    }

    /**
     * Result of processing an SMS message
     */
    data class ProcessingResult(
        val success: Boolean,
        val transactionId: Long? = null,
        val reason: String? = null
    )

    /**
     * Parses and saves a transaction from an SMS message.
     *
     * @param sender SMS sender address
     * @param body SMS body text
     * @param timestamp SMS timestamp in milliseconds
     * @return ProcessingResult indicating success/failure and transaction ID
     */
    suspend fun processAndSaveTransaction(
        sender: String,
        body: String,
        timestamp: Long
    ): ProcessingResult {
        try {
            // Get all parsers that can handle this sender and let content decide
            val parsers = BankParserFactory.getParsers(sender)
            if (parsers.isEmpty()) return ProcessingResult(
                false,
                reason = "No parser found for sender: $sender"
            )

            // Parse the SMS — try each matching parser in order, return first result
            val parsedTransaction = parsers.firstNotNullOfOrNull { parser ->
                parser.parse(body, sender, timestamp)
            } ?: return ProcessingResult(
                false,
                reason = "Could not parse transaction from SMS"
            )

            Log.d(TAG, "Parsed transaction: ${parsedTransaction.amount} from ${parsedTransaction.bankName}")

            // Save the transaction
            return saveParsedTransaction(parsedTransaction, body)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
            return ProcessingResult(false, reason = e.message)
        }
    }

    /**
     * Saves a parsed transaction to the database with all necessary processing:
     * - Duplicate detection
     * - Merchant mapping
     * - Rule application
     * - Subscription matching
     * - Balance updates
     */
    suspend fun saveParsedTransaction(
        parsedTransaction: ParsedTransaction,
        smsBody: String
    ): ProcessingResult {
        return try {
            // Convert to entity
            val entity = parsedTransaction.toEntity()

            // Check if this transaction was previously deleted or is a duplicate
            val existingTransaction = transactionRepository.getTransactionByHash(entity.transactionHash)
            if (existingTransaction != null) {
                when (TransactionDeduplication.checkHash(existingTransaction)) {
                    DedupResult.PreviouslyDeleted -> {
                        Log.d(TAG, "Skipping previously deleted transaction with hash: ${entity.transactionHash}")
                        return ProcessingResult(false, reason = "Transaction was previously deleted")
                    }
                    DedupResult.HashDuplicate -> {
                        Log.d(TAG, "Transaction already exists: ${entity.transactionHash}")
                        return ProcessingResult(false, reason = "Duplicate transaction")
                    }
                    else -> {} // Not reached
                }
            }

            // Check for UPI duplicate within the time window
            if (TransactionDeduplication.hasUpiReference(entity)) {
                val windowEnd = entity.dateTime
                val windowStart = windowEnd.minus(TransactionDeduplication.UPI_DUPLICATE_WINDOW)
                val upiCandidates = transactionRepository.getTransactionsByReferenceAndAmount(
                    reference = entity.reference!!,
                    amount = entity.amount,
                    accountLast4 = entity.accountNumber,
                    startDate = windowStart,
                    endDate = windowEnd
                )
                val candidateForReplacement = upiCandidates.firstOrNull { existing ->
                    TransactionDeduplication.shouldReplaceWithIncoming(existing, entity)
                }
                if (candidateForReplacement != null) {
                    Log.d(TAG, "Replacing UPI transaction ${candidateForReplacement.id} with incoming from ${entity.bankName}")
                    transactionRepository.deleteTransaction(candidateForReplacement, hardDelete = false)
                } else {
                    val upiDuplicate = upiCandidates.any { existing ->
                        TransactionDeduplication.isSameUpiTransaction(existing, entity)
                    }
                    if (upiDuplicate) {
                        Log.d(TAG, "UPI duplicate transaction detected for reference: ${entity.reference}")
                        return ProcessingResult(false, reason = "UPI duplicate transaction")
                    }
                }
            }

            // Check for custom merchant mapping
            val customCategory = merchantMappingRepository.getCategoryForMerchant(entity.merchantName)
            val entityWithMapping = if (customCategory != null) {
                Log.d(TAG, "Found custom category mapping: ${entity.merchantName} -> $customCategory")
                entity.copy(category = customCategory)
            } else {
                entity
            }

            // Apply rule engine to the transaction
            val activeRules = ruleRepository.getActiveRulesByType(entityWithMapping.transactionType)

            // Check if this transaction should be blocked
            val blockingRule = ruleEngine.shouldBlockTransaction(
                entityWithMapping,
                smsBody,
                activeRules
            )

            if (blockingRule != null) {
                Log.d(TAG, "Transaction blocked by rule: ${blockingRule.name}")
                return ProcessingResult(false, reason = "Blocked by rule: ${blockingRule.name}")
            }

            val (entityWithRules, ruleApplications) = ruleEngine.evaluateRules(
                entityWithMapping,
                smsBody,
                activeRules
            )

            if (ruleApplications.isNotEmpty()) {
                Log.d(TAG, "Applied ${ruleApplications.size} rules to transaction")
            }

            // Check if this transaction matches an active subscription
            val matchedSubscription = subscriptionRepository.matchTransactionToSubscription(
                entityWithRules.merchantName,
                entityWithRules.amount
            )

            val finalEntity = if (matchedSubscription != null) {
                Log.d(TAG, "Transaction matched to active subscription: ${matchedSubscription.merchantName}")
                subscriptionRepository.updateNextPaymentDateAfterCharge(
                    matchedSubscription.id,
                    entityWithRules.dateTime.toLocalDate()
                )
                entityWithRules.copy(isRecurring = true)
            } else {
                entityWithRules
            }
            val finalEntityForInsert = accountBalanceRepository.resolveEntityAccountNumber(finalEntity, parsedTransaction)

            val rowId = transactionRepository.insertTransaction(finalEntityForInsert)
            if (rowId != -1L) {
                Log.d(TAG, "Saved new transaction with ID: $rowId${if (finalEntityForInsert.isRecurring) " (Recurring)" else ""}")

                // Save rule applications if any rules were applied
                if (ruleApplications.isNotEmpty()) {
                    val applicationsWithId = ruleApplications.map { 
                        it.copy(transactionId = rowId.toString())
                    }
                    ruleRepository.saveRuleApplications(applicationsWithId)
                }

                // Process balance updates
                balanceUpdateProcessor.process(parsedTransaction, finalEntityForInsert, rowId)

                return ProcessingResult(true, transactionId = rowId)
            } else {
                Log.d(TAG, "Transaction already exists (duplicate): ${entity.transactionHash}")
                return ProcessingResult(false, reason = "Duplicate transaction")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving transaction: ${e.message}")
            return ProcessingResult(false, reason = e.message)
        }
    }

}
