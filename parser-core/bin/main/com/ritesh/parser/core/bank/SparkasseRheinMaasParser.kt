package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

class SparkasseRheinMaasParser : BankParser() {

    override fun getBankName() = "Sparkasse Rhein-Maas"

    override fun getCurrency() = "EUR"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender.contains("SPARKASSERHEINMAAS") || upperSender.contains("SPARKASSE")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            Regex("""EUR\s+([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""([0-9]+(?:\,[0-9]{2})?)\s*EUR""", RegexOption.IGNORE_CASE),
            Regex("""€\s*([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""([0-9]+(?:\,[0-9]{2})?)\s*€""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "").replace(",", ".")
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
            lowerMessage.contains("abgebucht") -> TransactionType.EXPENSE
            lowerMessage.contains("lastschrift") -> TransactionType.EXPENSE
            lowerMessage.contains("ausgegangen") -> TransactionType.EXPENSE
            lowerMessage.contains("belastet") -> TransactionType.EXPENSE
            lowerMessage.contains("bezahlt") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE

            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("gutschrift") -> TransactionType.INCOME
            lowerMessage.contains("eingegangen") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("gutgeschrieben") -> TransactionType.INCOME

            else -> null
        }
    }
}
