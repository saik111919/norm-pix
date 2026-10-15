package com.example.normpix.finance.domain

/*
 * Paying a credit card's bill is moving money onto the card, not spending: the spending was counted when it was charged to
 * the card. Recorded as an expense from a bank account instead, the card would go on showing the whole bill as owed and the
 * same money would be counted as spent twice. This tells, from what an entry says it was paid to, which of the user's cards
 * it is the bill of, so the entry can become a transfer onto that card.
 */

/** Words that say a payment went to a card: "credit card", "CC", "card". */
private val cardWords = Regex("""(?i)\bcredit\s*card\b|\bcc\b|\bcard\b""")

/** Words that say, without naming the card, that the payment was a card's bill: "credit card bill", "card payment". */
private val billWords = Regex("""(?i)\bcredit\s*card\b|\bcard\s+(?:bill|payment|dues?)\b|\bcc\s+(?:bill|payment)\b""")

/**
 * Which of [accounts]' credit cards a payment described by [text] (its payee, note, category - whatever names where it went)
 * is the bill of, or null when it doesn't say so clearly enough to pick one. A card that is [paidFrom] is never the answer:
 * money spent on a card is spending.
 *
 * The text has to speak of a card ("credit card", "CC", "card"). Then the card's own name in it decides; otherwise its last
 * digits, or its bank ([bankKeyIn] reads a bank from text). With only one card, a text that plainly speaks of a card bill is
 * enough.
 */
fun cardBillTarget(
    text: String,
    accounts: List<Account>,
    paidFrom: Long?,
    bankKeyIn: (String) -> String?,
): Account? {
    if (text.isBlank()) return null
    val cards = accounts.filter { it.kind == AccountKind.Card && !it.archived && it.id != null && it.id != paidFrom }
    // Only a payment that speaks of a card: a card named after a shop ("Amazon Pay") must not catch a purchase there.
    if (cards.isEmpty() || !cardWords.containsMatchIn(text)) return null
    val lower = text.lowercase()

    cards.filter { it.name.trim().length >= MIN_NAME && lower.contains(it.name.trim().lowercase()) }.singleOrNull()?.let { return it }
    cards.filter { card ->
        card.numberEnding?.let { Regex("""(?<!\d)${Regex.escape(it)}(?!\d)""").containsMatchIn(text) } == true
    }.singleOrNull()?.let { return it }
    val bank = bankKeyIn(text)
    if (bank != null) return cards.filter { it.bankKey == bank }.singleOrNull()
    return cards.singleOrNull()?.takeIf { billWords.containsMatchIn(text) }
}

/** A name shorter than this ("HD") could turn up inside unrelated words, so it doesn't decide anything on its own. */
private const val MIN_NAME = 3
