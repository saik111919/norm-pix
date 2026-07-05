package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

class EMolaParser : BankParser() {

    override fun getBankName() = "E-Mola"

    override fun getCurrency() = "MZN"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("EMOLA") ||
                normalizedSender.contains("E-MOLA")
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

        val mtPattern = Regex(
            """MT\s+([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        mtPattern.find(message)?.let { match ->
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
            lowerMessage.contains("you have received") ||
            lowerMessage.contains("credited") ||
            lowerMessage.contains("received") -> TransactionType.INCOME

            lowerMessage.contains("you have sent") ||
            lowerMessage.contains("you have paid") ||
            lowerMessage.contains("debited") ||
            lowerMessage.contains("paid") -> TransactionType.EXPENSE

            else -> null
        }
    }
}
