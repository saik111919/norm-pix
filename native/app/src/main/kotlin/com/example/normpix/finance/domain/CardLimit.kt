package com.example.normpix.finance.domain

/**
 * A credit card whose limit is used to or past the level the user asked to hear about (Settings > Wallet & Cards > Card limit
 * warning). [usedPercent] is what is owed as a share of the limit, to the nearest whole percent and never under the level that made
 * it a warning; it goes past 100 when the card is over its limit ([over]).
 */
data class CardLimitAlert(val account: Account, val room: CreditRoom, val usedPercent: Int) {
    /** More is owed than the limit allows: [CreditRoom.available] is below zero. */
    val over: Boolean get() = room.overLimit
}

/** [owed] as a share of [limit], rounded half up to a whole percent; 0 for a limit that is not positive. */
fun usedPercentOf(owed: Long, limit: Long): Int {
    if (limit <= 0 || owed <= 0) return 0
    return ((owed * 100 + limit / 2) / limit).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/**
 * The credit cards among [accounts] that have used [warnAtPercent] of their limit or more, the most used first.
 *
 * - A card is an account with a limit above zero that is not archived; a limit is only ever kept for a card, so that is what
 *   tells a credit card from the rest, as the Wallet's meter does.
 * - What is used is what is owed ([creditRoom]: the balance below zero) against the limit. The level is met **exactly**, in whole
 *   paise: 30% of ₹1,00,000 warns at ₹30,000 and not a paisa under it. A card in credit owes nothing.
 * - [warnAtPercent] of 0 or less is "off": nothing is warned about.
 *
 * Nothing is stored: the answer is worked out from the balances each time, so a card paid down under the level is not listed at
 * the next look.
 */
fun cardLimitAlerts(accounts: List<Account>, balanceOf: (Long) -> Long, warnAtPercent: Int): List<CardLimitAlert> {
    if (warnAtPercent <= 0) return emptyList()
    return accounts.mapNotNull { account ->
        val id = account.id ?: return@mapNotNull null
        val limit = account.creditLimit?.takeIf { it > 0 && !account.archived } ?: return@mapNotNull null
        val room = creditRoom(limit, balanceOf(id)) ?: return@mapNotNull null
        // owed / limit >= level / 100, without rounding either side.
        if (room.owed * 100 < limit * warnAtPercent) return@mapNotNull null
        CardLimitAlert(account, room, usedPercentOf(room.owed, limit))
    }.sortedWith(
        compareByDescending<CardLimitAlert> { it.room.owed.toDouble() / it.room.limit }
            .thenBy { it.account.name.lowercase() }
            .thenBy { it.account.id },
    )
}
