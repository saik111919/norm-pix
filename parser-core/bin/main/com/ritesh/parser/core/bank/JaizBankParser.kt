package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

class JaizBankParser : BankParser() {
    override fun getBankName() = "Jaiz Bank"
    override fun getCurrency() = "NGN"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        return normalized.contains("JAIZ")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val ngnPattern = Regex(
            """NGN\s*([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        ngnPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) { null }
        }
        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("credit") || lowerMessage.contains("received") ||
                lowerMessage.contains("deposit") -> TransactionType.INCOME
            lowerMessage.contains("debit") || lowerMessage.contains("withdrawal") ||
                lowerMessage.contains("purchase") || lowerMessage.contains("payment") -> TransactionType.EXPENSE
            else -> super.extractTransactionType(message)
        }
    }
}
