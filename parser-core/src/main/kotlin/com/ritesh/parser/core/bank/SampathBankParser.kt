package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

class SampathBankParser : BankParser() {

    override fun getBankName() = "Sampath Bank"

    override fun getCurrency() = "LKR"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("SAMPATH")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            Regex("""LKR\s+([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""RS\.?\s*([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE)
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

        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("spent") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE

            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME

            else -> null
        }
    }
}
