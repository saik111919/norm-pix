package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

class ArabBankParser : BankParser() {

    override fun getBankName() = "Arab Bank"

    override fun getCurrency() = "EGP"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("ARAB")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            Regex("""EGP\s+([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""egp\s+([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("spent") -> TransactionType.EXPENSE
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE
            lowerMessage.contains("payment") -> TransactionType.EXPENSE
            else -> null
        }
    }
}
