package com.ritesh.parser.core.bank

import com.ritesh.parser.core.ParsedTransaction
import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

class MPesaMozambiqueParser : BankParser() {

    override fun getBankName() = "M-PESA Mozambique"

    override fun getCurrency() = "MZN"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("MPESA") ||
                normalizedSender.contains("M-PESA") ||
                normalizedSender == "MPESA" ||
                normalizedSender == "M-PESA"
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        if (!smsBody.contains("MZN", ignoreCase = true) &&
            !smsBody.contains("MT", ignoreCase = true)
        ) {
            return null
        }
        return super.parse(smsBody, sender, timestamp)
    }

    override fun extractAmount(message: String): BigDecimal? {
        val mznSpacePattern = Regex(
            """MZN\s+([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        mznSpacePattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }

        val mznNoSpacePattern = Regex(
            """MZN([0-9,]+(?:\.[0-9]{2})?)""",
            RegexOption.IGNORE_CASE
        )
        mznNoSpacePattern.find(message)?.let { match ->
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
            lowerMessage.contains("received mzn") ||
            lowerMessage.contains("received mt") -> TransactionType.INCOME

            lowerMessage.contains("sent to") ||
            lowerMessage.contains("paid to") ||
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE

            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val fromPattern = Regex(
            """from\s+([A-Z][A-Za-z\s]+?)(?:\s*\(|$)""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        val sentToPattern = Regex(
            """sent to\s+([A-Z][A-Za-z\s]+?)(?:\s*\(|$)""",
            RegexOption.IGNORE_CASE
        )
        sentToPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        val balancePattern = Regex(
            """New M-Pesa balance is (?:MZN|MT)\s*([0-9,]+(?:\.[0-9]{2})?)""",
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

        if (!lowerMessage.contains("mzn") && !lowerMessage.contains(" mt ") &&
            !lowerMessage.startsWith("mt ") && !lowerMessage.contains(" mt,")
        ) {
            return false
        }

        val transactionKeywords = listOf(
            "received", "sent to", "paid to", "withdrawn", "new m-pesa balance"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }
}
