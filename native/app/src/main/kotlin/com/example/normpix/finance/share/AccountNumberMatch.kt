package com.example.normpix.finance.share

import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.numberEndingOrNull
import com.example.normpix.wallet.StoredCard

/** How the digits a message shows compare with what an account is known by. */
internal enum class NumberFit {
    /** The same digits: this is the account. */
    Match,

    /** Agrees as far as it goes, but only two digits are shown - too few to say it is this one. */
    Weak,

    /** Different digits: it is not this account. */
    Contradicts,

    /** The account has no number to compare (none given, no card linked), or the message shows none. */
    Unknown,
}

/**
 * Compares the account and card numbers a shared message shows partly hidden ([MaskedNumber]) with an account: the number
 * of the Wallet card the user linked to it - whole, so the first digits a RuPay card shows on UPI ("652850XXXXXXXX55") can be
 * checked too - and the last digits the user gave it. Runs on the phone, on what is already in memory; nothing is logged.
 */
internal object AccountNumbers {
    /** A stored card number shorter than this is not a card number. */
    private const val MIN_CARD_NUMBER = 12

    fun fit(account: Account, masks: List<MaskedNumber>, cards: List<StoredCard>): NumberFit {
        if (masks.isEmpty()) return NumberFit.Unknown
        val full = account.cardId
            ?.let { id -> cards.firstOrNull { it.id == id } }
            ?.cardNumber?.filter { it in '0'..'9' }
            ?.takeIf { it.length >= MIN_CARD_NUMBER }
        val ending = account.numberEnding?.let(::numberEndingOrNull)
        if (full == null && ending == null) return NumberFit.Unknown
        var weak = false
        for (mask in masks) {
            if (full != null && full.startsWith(mask.prefix) && full.endsWith(mask.suffix)) return NumberFit.Match
            if (ending == null) continue
            when (endingFit(ending, mask)) {
                NumberFit.Match -> return NumberFit.Match
                NumberFit.Weak -> weak = true
                else -> Unit
            }
        }
        return if (weak) NumberFit.Weak else NumberFit.Contradicts
    }

    /** The account's last digits [ending] against the digits [mask] shows at the end of its number (the first ones can't be checked). */
    private fun endingFit(ending: String, mask: MaskedNumber): NumberFit {
        val shared = minOf(ending.length, mask.suffix.length)
        if (ending.takeLast(shared) != mask.suffix.takeLast(shared)) return NumberFit.Contradicts
        // "…55" fits one account in a hundred: not enough, by itself, to choose by.
        return if (mask.suffix.length >= 3) NumberFit.Match else NumberFit.Weak
    }
}
