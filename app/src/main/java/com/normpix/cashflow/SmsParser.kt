package com.normpix.cashflow

/**
 * Turns a bank's human-readable SMS template (e.g. "Rs.{amount} debited from a/c **{last4}
 * on {date} to VPA {vpa} (UPI Ref {ref})", as edited in Settings > Banks) into a regex with
 * named capture groups, then matches real SMS bodies against every enabled bank's templates.
 */
private val PLACEHOLDER_PATTERNS = mapOf(
    "amount" to """[0-9][0-9,]*(?:\.[0-9]{1,2})?""",
    "last4" to """\w{2,6}""",
    "date" to """[0-9]{1,2}[-/ A-Za-z]{1,5}[0-9]{2,4}""",
    "vpa" to """[\w.\-]+@[\w]+""",
    "ref" to """\w+""",
    "merchant" to """.+?""",
    "limit" to """[0-9][0-9,]*(?:\.[0-9]{1,2})?""",
)

private fun compileSmsTemplate(template: String): Regex {
    val sb = StringBuilder()
    var i = 0
    while (i < template.length) {
        val c = template[i]
        if (c == '{') {
            val end = template.indexOf('}', i)
            if (end != -1) {
                val name = template.substring(i + 1, end)
                val pattern = PLACEHOLDER_PATTERNS[name] ?: """.+?"""
                sb.append("(?<$name>$pattern)")
                i = end + 1
                continue
            }
        }
        sb.append(Regex.escape(c.toString()))
        i++
    }
    return Regex(sb.toString(), RegexOption.IGNORE_CASE)
}

data class ParsedSms(val bank: Bank, val amount: Double, val merchant: String?, val last4: String?)

private fun MatchResult.namedOrNull(name: String): String? =
    runCatching { (groups as? MatchNamedGroupCollection)?.get(name)?.value }.getOrNull()?.trim()?.ifEmpty { null }

/** Tries every enabled bank's sender IDs + formats against [body]; returns the first match. */
fun parseSms(sender: String, body: String, banks: List<Bank>): ParsedSms? {
    for (bank in banks) {
        if (!bank.on) continue
        val senderIds = bank.senders.split("·", ",").map { it.trim() }
        if (senderIds.none { it.isNotEmpty() && sender.contains(it, ignoreCase = true) }) continue
        for (format in bank.formats) {
            val match = runCatching { compileSmsTemplate(format).find(body) }.getOrNull() ?: continue
            val amount = match.namedOrNull("amount")?.replace(",", "")?.toDoubleOrNull() ?: continue
            return ParsedSms(bank, amount, match.namedOrNull("merchant"), match.namedOrNull("last4"))
        }
    }
    return null
}
