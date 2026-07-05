package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

class NaviMutualFundParser : BankParser() {

    override fun getBankName() = "Navi Mutual Fund"

    override fun getCurrency() = "INR"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("NAVI")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val inrPattern = Regex(
            """INR\s+(\d+(?:,\d{3})*(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        inrPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("debited") || lowerMessage.contains("spent") || lowerMessage.contains("paid") -> TransactionType.EXPENSE
            else -> super.extractTransactionType(message)
        }
    }
}
