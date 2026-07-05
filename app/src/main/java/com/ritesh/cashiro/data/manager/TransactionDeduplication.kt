package com.ritesh.cashiro.data.manager

import com.ritesh.cashiro.data.database.entity.TransactionEntity
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime

sealed class DedupResult {
    data object NotDuplicate : DedupResult()
    data object PreviouslyDeleted : DedupResult()
    data object HashDuplicate : DedupResult()
    data object UpiDuplicate : DedupResult()
    data class Replaced(val replacementId: Long) : DedupResult()
}

object TransactionDeduplication {

    private val upiReferencePattern = Regex("""\d{12}""")
    val UPI_DUPLICATE_WINDOW: Duration = Duration.ofMinutes(3)

    fun checkHash(existing: TransactionEntity): DedupResult {
        return if (existing.isDeleted) {
            DedupResult.PreviouslyDeleted
        } else {
            DedupResult.HashDuplicate
        }
    }

    fun hasUpiReference(transaction: TransactionEntity): Boolean =
        transaction.reference?.let { upiReferencePattern.matches(it) } == true

    fun isSameUpiTransaction(
        existing: TransactionEntity,
        incoming: TransactionEntity,
        window: Duration = UPI_DUPLICATE_WINDOW
    ): Boolean {
        if (!hasUpiReference(existing) || !hasUpiReference(incoming)) return false
        if (existing.reference != incoming.reference) return false
        if (existing.transactionType != incoming.transactionType) return false
        if (existing.amount.compareTo(incoming.amount) != 0) return false

        val gap = Duration.between(existing.dateTime, incoming.dateTime).abs()
        return gap <= window
    }

    fun shouldReplaceWithIncoming(
        existing: TransactionEntity,
        incoming: TransactionEntity
    ): Boolean {
        if (!isSameUpiTransaction(existing, incoming)) return false

        val existingIsPartnerBank = existing.bankName.equals("State Bank of India", ignoreCase = true)
        val incomingIsPartnerBank = incoming.bankName.equals("State Bank of India", ignoreCase = true)
        if (existingIsPartnerBank && !incomingIsPartnerBank) return true
        if (!existingIsPartnerBank && incomingIsPartnerBank) return false

        return existing.balanceAfter == null && incoming.balanceAfter != null
    }
}
