package com.ritesh.cashiro.domain.usecase

import com.ritesh.cashiro.utils.SubscriptionUtils

import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.data.database.entity.SubscriptionState
import com.ritesh.cashiro.data.database.entity.TransactionEntity
import com.ritesh.cashiro.data.database.entity.TransactionType
import com.ritesh.cashiro.data.repository.AccountBalanceRepository
import com.ritesh.cashiro.data.repository.SubscriptionRepository
import com.ritesh.cashiro.data.repository.TransactionRepository
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class AddTransactionUseCase
@Inject
constructor(
        private val transactionRepository: TransactionRepository,
        private val subscriptionRepository: SubscriptionRepository,
        private val accountBalanceRepository: AccountBalanceRepository
) {
    suspend fun execute(
            amount: BigDecimal,
            merchant: String,
            category: String,
            type: TransactionType,
            date: LocalDateTime,
            notes: String? = null,
            subcategory: String? = null,
            isRecurring: Boolean = false,
            bankName: String? = null,
            accountLast4: String? = null,
            currency: String = "INR",
            sourceAccountId: Long? = null,
            targetAccountBankName: String? = null,
            targetAccountLast4: String? = null,
            billingCycle: String? = null,
            createSubscription: Boolean = true,
            attachments: String = ""
    ) {
        // Generate a unique hash for manual transactions
        val transactionHash =
                generateManualTransactionHash(amount = amount, merchant = merchant, date = date)

        // Create the transaction entity
        val transaction =
                TransactionEntity(
                        amount = amount,
                        merchantName = merchant,
                        category = category,
                        subcategory = subcategory,
                        transactionType = type,
                        dateTime = date,
                        description = notes,
                        smsBody = null, // null indicates manual entry
                        bankName = bankName ?: "Manual Entry",
                        smsSender = null, // null indicates manual entry
                        accountNumber = accountLast4,
                        balanceAfter = null,
                        transactionHash = transactionHash,
                        isRecurring = isRecurring,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now(),
                        currency = currency,
                        billingCycle = billingCycle,
                        attachments = attachments
                )

        // Insert the transaction
        val transactionId = transactionRepository.insertTransaction(transaction)

        // Update account balances based on transaction type
        if (bankName != null && accountLast4 != null) {
            when (type) {
                TransactionType.INCOME -> {
                    val currentBalance = accountBalanceRepository.getLatestBalance(bankName, accountLast4)
                    val newBalance = (currentBalance?.balance ?: BigDecimal.ZERO) + amount
                    accountBalanceRepository.insertBalance(
                        AccountBalanceEntity(
                            bankName = bankName,
                            accountLast4 = accountLast4,
                            balance = newBalance,
                            timestamp = date,
                            transactionId = transactionId,
                            sourceType = "MANUAL",
                            iconResId = currentBalance?.iconResId ?: 0,
                            isCreditCard = currentBalance?.isCreditCard ?: false,
                            creditLimit = currentBalance?.creditLimit,
                            currency = currency
                        )
                    )
                }
                TransactionType.EXPENSE -> {
                    // Subtract amount from the selected account
                    val currentBalance = accountBalanceRepository.getLatestBalance(bankName, accountLast4)
                    val newBalance = (currentBalance?.balance ?: BigDecimal.ZERO) - amount
                    accountBalanceRepository.insertBalance(
                        AccountBalanceEntity(
                            bankName = bankName,
                            accountLast4 = accountLast4,
                            balance = newBalance,
                            timestamp = date,
                            transactionId = transactionId,
                            sourceType = "MANUAL",
                            iconResId = currentBalance?.iconResId ?: 0,
                            isCreditCard = currentBalance?.isCreditCard ?: false,
                            creditLimit = currentBalance?.creditLimit,
                            currency = currency
                        )
                    )
                }
                TransactionType.CREDIT -> {
                    // Credit transactions: add to outstanding balance (credit usage)
                    val currentBalance = accountBalanceRepository.getLatestBalance(bankName, accountLast4)
                    val newBalance = (currentBalance?.balance ?: BigDecimal.ZERO) + amount
                    accountBalanceRepository.insertBalance(
                        AccountBalanceEntity(
                            bankName = bankName,
                            accountLast4 = accountLast4,
                            balance = newBalance,
                            timestamp = date,
                            transactionId = transactionId,
                            sourceType = "MANUAL",
                            iconResId = currentBalance?.iconResId ?: 0,
                            isCreditCard = currentBalance?.isCreditCard ?: false,
                            creditLimit = currentBalance?.creditLimit,
                            currency = currency
                        )
                    )
                }
                TransactionType.TRANSFER -> {
                    // Transfer: subtract from source, add to target
                    if (targetAccountBankName != null && targetAccountLast4 != null) {
                        // Subtract from source account
                        val sourceBalance = accountBalanceRepository.getLatestBalance(bankName, accountLast4)
                        val newSourceBalance = (sourceBalance?.balance ?: BigDecimal.ZERO) - amount
                        accountBalanceRepository.insertBalance(
                            AccountBalanceEntity(
                                bankName = bankName,
                                accountLast4 = accountLast4,
                                balance = newSourceBalance,
                                timestamp = date,
                                transactionId = transactionId,
                                sourceType = "MANUAL",
                                iconResId = sourceBalance?.iconResId ?: 0,
                                isCreditCard = sourceBalance?.isCreditCard ?: false,
                                creditLimit = sourceBalance?.creditLimit,
                                currency = currency
                            )
                        )
                        
                        // Add to target account
                        val targetBalance = accountBalanceRepository.getLatestBalance(targetAccountBankName, targetAccountLast4)
                        val newTargetBalance = (targetBalance?.balance ?: BigDecimal.ZERO) + amount
                        accountBalanceRepository.insertBalance(
                            AccountBalanceEntity(
                                bankName = targetAccountBankName,
                                accountLast4 = targetAccountLast4,
                                balance = newTargetBalance,
                                timestamp = date,
                                transactionId = transactionId,
                                sourceType = "MANUAL",
                                iconResId = targetBalance?.iconResId ?: 0,
                                isCreditCard = targetBalance?.isCreditCard ?: false,
                                creditLimit = targetBalance?.creditLimit,
                                currency = currency
                            )
                        )
                    }
                }
                TransactionType.INVESTMENT -> {
                    // Investment transactions: subtract from balance
                    val currentBalance = accountBalanceRepository.getLatestBalance(bankName, accountLast4)
                    val newBalance = (currentBalance?.balance ?: BigDecimal.ZERO) - amount
                    accountBalanceRepository.insertBalance(
                        AccountBalanceEntity(
                            bankName = bankName,
                            accountLast4 = accountLast4,
                            balance = newBalance,
                            timestamp = date,
                            transactionId = transactionId,
                            sourceType = "MANUAL",
                            iconResId = currentBalance?.iconResId ?: 0,
                            isCreditCard = currentBalance?.isCreditCard ?: false,
                            creditLimit = currentBalance?.creditLimit,
                            currency = currency
                        )
                    )
                }
                TransactionType.BALANCE_UPDATE -> {
                    // Balance update already comes with its own balance, no adjustment needed
                }
            }
        }

        // If marked as recurring, create a subscription
        if (createSubscription && isRecurring && transactionId != -1L) {
            val nextPaymentDate = SubscriptionUtils.calculateNextPaymentDate(date.toLocalDate(), billingCycle)

            val subscription =
                    SubscriptionEntity(
                            merchantName = merchant,
                            amount = amount,
                            nextPaymentDate = nextPaymentDate,
                            state = SubscriptionState.ACTIVE,
                            bankName = bankName ?: "Manual Entry",
                            category = category,
                            subcategory = subcategory,
                            createdAt = LocalDateTime.now(),
                            updatedAt = LocalDateTime.now(),
                            currency = currency,
                            billingCycle = billingCycle,
                            lastPaidDate = date.toLocalDate()
                    )

            subscriptionRepository.insertSubscription(subscription)
        }
    }


    private fun generateManualTransactionHash(
            amount: BigDecimal,
            merchant: String,
            date: LocalDateTime
    ): String {
        // Create a unique hash for manual transactions
        // Format: MANUAL_<amount>_<merchant>_<datetime>
        val data = "MANUAL_${amount}_${merchant}_${date}"

        return MessageDigest.getInstance("MD5").digest(data.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
    }
}
