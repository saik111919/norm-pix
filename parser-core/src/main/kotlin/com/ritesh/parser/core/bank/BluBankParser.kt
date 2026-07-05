package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

class BluBankParser : BankParser() {

    override fun getBankName() = "Blu Bank"

    override fun getCurrency() = "IRR"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("BLU")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            Regex("""IRR\s+([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""irr\s+([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
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
