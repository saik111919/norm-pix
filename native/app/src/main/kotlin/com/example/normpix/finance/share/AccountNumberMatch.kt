package com.example.normpix.finance.share

import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.numberEndingOrNull
import com.example.normpix.finance.domain.numberStartOrNull
import com.example.normpix.wallet.StoredCard

/** How the digits a message shows compare with what an account is known by. */
internal enum class NumberFit {
    /** The same digits: this is the account. */
    Match,

    /** Agrees as far as it goes, but too few digits are shown or known - two at the end, a card's first six alone - to say it is this one. */
    Weak,

    /** Different digits: it is not this account. */
    Contradicts,

    /** The account has no number to compare (none given, no card linked), or the message shows none. */
    Unknown,
}

/**
 * Compares the account and card numbers a shared message shows partly hidden ([MaskedNumber]) with an account: the number
 * of the Wallet card the user linked to it - whole, so the first digits a RuPay card shows on UPI ("652850XXXXXXXX55") can be
 * checked too - and the first six and last four digits the user gave it ([Account.numberStart], [Account.numberEnding]),
 * which are all there is to go by when the Wallet is locked or its card was never linked. Runs on the phone, on what is
 * already in memory; nothing is logged.
 */
internal object AccountNumbers {
    /** A stored card number shorter than this is not a card number. */
    private const val MIN_CARD_NUMBER = 12

    /** Digits that say "this one" by themselves: three at the end of a number, or two at the end and four or more at its start. */
    private const val ENDING_ENOUGH = 3
    private const val ENDING_WITH_START = 2
    private const val START_WITH_ENDING = 4

    /** The digits of [card]'s number, or null when it has none worth comparing. */
    fun digitsOf(card: StoredCard): String? = card.cardNumber.filter { it in '0'..'9' }.takeIf { it.length >= MIN_CARD_NUMBER }

    fun fit(account: Account, masks: List<MaskedNumber>, cards: List<StoredCard>): NumberFit {
        if (masks.isEmpty()) return NumberFit.Unknown
        val full = account.cardId?.let { id -> cards.firstOrNull { it.id == id } }?.let(::digitsOf)
        val ending = account.numberEnding?.let(::numberEndingOrNull)
        val start = account.numberStart?.let(::numberStartOrNull)
        if (full == null && ending == null && start == null) return NumberFit.Unknown
        var weak = false
        var unknown = false
        for (mask in masks) {
            // An account is known two ways, and either may be what a receipt shows: first its own digits, and only when those
            // don't match, the number of its linked card. A bank account's UPI digits ("HDFC Bank 3588") are not its debit
            // card's, so the card's number only rules the account out when the digits shown are a card's (a card account, or
            // a number printed with its first digits).
            val own = if (ending != null || start != null) fit(start, ending, mask) else NumberFit.Unknown
            if (own == NumberFit.Match) return NumberFit.Match
            val card = if (full != null) fit(full, full, mask) else NumberFit.Unknown
            val cardRulesOut = account.kind == AccountKind.Card || mask.prefix.isNotEmpty()
            val fits = listOf(own, if (card == NumberFit.Contradicts && !cardRulesOut) NumberFit.Unknown else card)
            when {
                NumberFit.Match in fits -> return NumberFit.Match
                NumberFit.Weak in fits -> weak = true
                NumberFit.Contradicts in fits -> Unit
                else -> unknown = true
            }
        }
        // Different from every number shown is what rules an account out; one it could not be compared with leaves it open.
        return when {
            weak -> NumberFit.Weak
            unknown -> NumberFit.Unknown
            else -> NumberFit.Contradicts
        }
    }

    /** Whether [card]'s number is one of the numbers [masks] show: both ends of it, as far as the message shows them. */
    fun cardFits(card: StoredCard, masks: List<MaskedNumber>): Boolean {
        val digits = digitsOf(card) ?: return false
        return masks.any { fit(digits, digits, it) == NumberFit.Match }
    }

    /**
     * One [mask] against what is known of a number's [head] (its first digits) and [tail] (its last): as many of each as are
     * known and shown are compared. Any that differ rule it out; the digits that agree say how sure the match is.
     */
    private fun fit(head: String?, tail: String?, mask: MaskedNumber): NumberFit {
        var startChecked = 0
        var endChecked = 0
        if (mask.prefix.isNotEmpty() && head != null) {
            val n = minOf(head.length, mask.prefix.length)
            if (head.take(n) != mask.prefix.take(n)) return NumberFit.Contradicts
            startChecked = n
        }
        if (tail != null) {
            val n = minOf(tail.length, mask.suffix.length)
            if (tail.takeLast(n) != mask.suffix.takeLast(n)) return NumberFit.Contradicts
            endChecked = n
        }
        return when {
            // "…55" fits one account in a hundred: not enough, by itself, to choose by. A card's first six digits name its
            // bank and network, not the card: not enough either.
            endChecked >= ENDING_ENOUGH || (endChecked >= ENDING_WITH_START && startChecked >= START_WITH_ENDING) -> NumberFit.Match
            endChecked > 0 || startChecked > 0 -> NumberFit.Weak
            else -> NumberFit.Unknown
        }
    }
}
