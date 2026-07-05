package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

class MillenniumBimParser : BankParser() {

    override fun getBankName() = "Millennium BIM"

    override fun getCurrency() = "MZN"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("MILLENNIUM") ||
                normalizedSender.contains("BIM")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val mznPattern = Regex(
            """MZN\s*([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        mznPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        return when {
            lowerMessage.contains("credited") ||
            lowerMessage.contains("deposited") ||
            lowerMessage.contains("received") -> TransactionType.INCOME

            lowerMessage.contains("debited") ||
            lowerMessage.contains("withdrawn") ||
            lowerMessage.contains("paid") ||
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE

            else -> null
        }
    }
}
