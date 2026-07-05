package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

class CrdbBankParser : BankParser() {

    override fun getBankName() = "CRDB Bank"

    override fun getCurrency() = "TZS"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("CRDB")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val tzsPattern = Regex(
            """TZS\s+([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        tzsPattern.find(message)?.let { match ->
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
            lowerMessage.contains("transfer") -> TransactionType.EXPENSE

            else -> null
        }
    }
}
