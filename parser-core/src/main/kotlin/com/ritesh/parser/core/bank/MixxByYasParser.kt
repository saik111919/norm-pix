package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Mixx by Yas (Tanzania) mobile money SMS messages.
 * Note: TigoPesaParser also handles MIXX senders; this parser is
 * intended for senders that are exclusively Mixx by Yas branded.
 */
class MixxByYasParser : BankParser() {

    override fun getBankName() = "Mixx by Yas"

    override fun getCurrency() = "TZS"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("MIXX")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val tshPattern = Regex(
            """TSh\s*([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        tshPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

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
            lowerMessage.contains("you have received") ||
            lowerMessage.contains("received tsh") ||
            lowerMessage.contains("cash-in") -> TransactionType.INCOME

            lowerMessage.contains("you have sent") ||
            lowerMessage.contains("you have paid") ||
            lowerMessage.contains("paid tsh") ||
            lowerMessage.contains("sent tsh") -> TransactionType.EXPENSE

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val fromPattern = Regex(
            """from\s+([A-Z][A-Za-z\s]+?)(?:\s+is\s+successful|\s*\(|$)""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        val toPattern = Regex(
            """to\s+([A-Z][A-Za-z\s]+?)(?:\s+is\s+successful|\s*\.|$)""",
            RegexOption.IGNORE_CASE
        )
        toPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        val balancePattern = Regex(
            """New balance is TSh\s*([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        if (!lowerMessage.contains("tsh") && !lowerMessage.contains("tzs")) {
            return false
        }

        val transactionKeywords = listOf(
            "you have received", "you have sent", "you have paid",
            "cash-in", "successful", "new balance"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }

    override fun cleanMerchantName(merchant: String): String {
        return merchant
            .replace(Regex("""\s*\(.*?\)\s*$"""), "")
            .replace(Regex("""\s+on\s+\d{2}/.*"""), "")
            .replace(Regex("""\s*-\s*$"""), "")
            .replace(Regex("""^\s*-\s*"""), "")
            .trim()
    }
}
