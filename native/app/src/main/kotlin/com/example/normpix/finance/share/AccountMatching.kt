package com.example.normpix.finance.share

import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.StoredCard

/**
 * Which of the user's accounts a shared receipt - a picture of one, or its text - is about: pure, on the accounts and Wallet
 * cards in memory.
 *
 * What a receipt shows of an account is a bank's name and a number partly hidden. It is looked for in this order, and the
 * order is the point: the **accounts** are asked first, and only when none of them fits are the **Wallet's cards** asked.
 *
 * 1. **The accounts.**
 *    a. *The digits.* An account is the one when what it is known by - the number of its linked Wallet card, or the first six
 *       and last four digits the user gave it - is the number shown (see [AccountNumbers]). Two accounts with those digits are
 *       told apart by the bank the receipt names, or left.
 *    b. *The bank*, when no account has the digits: the one account of the bank the receipt names, of the sort it says (a
 *       card, a bank account, a wallet) when it says. An account whose number is known and different from the one shown is
 *       not one of those.
 * 2. **The Wallet's cards**, when no account was found: a card that has the digits but is linked to no account points to the
 *    one account of that card's bank and kind - a credit card's bank has one card account, say. Two such accounts are not told
 *    apart by a guess. When the card is the one but no account of its bank and kind exists, the receipt still says which card it
 *    is ([Pick.card]), so the user is told what the number was while they choose the account.
 *
 * Whatever is left - no account chosen, a number shown - is for the caller's own checks: it asks the user to choose (see
 * `SharedTransactionImport.prefill`), as it always has.
 *
 * With the Wallet locked, or its cards not kept ("Store cards" off), [find] gets no cards, and the second step has nothing to
 * ask: the accounts are all it goes by.
 */
internal object AccountMatching {
    /**
     * The account found, how it was found, and how many accounts the digits fitted (more than one: they could not choose).
     * [card] is the Wallet card the digits are, when that is known - found in the second step, with or without an account for it.
     */
    class Pick(val account: Account?, val byNumber: Boolean = false, val fitting: Int = 0, val card: StoredCard? = null)

    /** What the receipt's own wording says about the user's bank: not its payee, not the bank that "powers" the UPI app. */
    private val poweredBy = Regex("""(?i)\bpowered\s+by\b[^\n]*(?:\n[^\n]{0,40})?""")

    fun find(
        text: String,
        counterparty: String?,
        instrument: SharedInstrument?,
        accounts: List<Account>,
        catalog: BankCatalog,
        masks: List<MaskedNumber>,
        cards: List<StoredCard>,
    ): Pick {
        val active = accounts.filter { !it.archived }
        val fits = active.associateWith { AccountNumbers.fit(it, masks, cards) }
        val bank = bankOf(text, counterparty, catalog)
        val ofBank = bank?.let { key -> inBank(key, instrument, active) }.orEmpty()

        // 1a. The accounts, by the digits.
        val withDigits = active.filter { fits[it] == NumberFit.Match }
        if (withDigits.size == 1) return Pick(withDigits.single(), byNumber = true, fitting = 1)
        if (withDigits.size > 1) {
            // Two accounts end the same way (at different banks, say): the message's bank may tell them apart.
            val narrowed = withDigits.filter { it in ofBank }.singleOrNull()
            return Pick(narrowed, byNumber = narrowed != null, fitting = withDigits.size)
        }
        // 1b. The accounts, by the bank - while no account has the digits, an account whose digits are different is out.
        ofBank.filter { fits[it] != NumberFit.Contradicts }.singleOrNull()?.let { return Pick(it) }

        // 2. Only now the Wallet's cards: the card the digits are, and the account of its bank and kind.
        val shown = cardsShown(masks, cards, accounts, bank, catalog)
        val viaCards = accountsOfCards(shown, cards, accounts, active, fits, catalog)
        if (viaCards.size == 1) return Pick(viaCards.single(), byNumber = true, fitting = 1, card = shown.singleOrNull())
        if (viaCards.size > 1) {
            val narrowed = viaCards.filter { it in ofBank }.singleOrNull()
            return Pick(narrowed, byNumber = narrowed != null, fitting = viaCards.size, card = shown.singleOrNull())
        }
        return Pick(null, card = shown.singleOrNull())
    }

    /** The key of the bank the receipt's [text] names as the user's, or null: its payee and its "Powered by" bank left out. */
    private fun bankOf(text: String, counterparty: String?, catalog: BankCatalog): String? {
        val withoutPayee = if (counterparty.isNullOrBlank()) text else text.replace(counterparty, " ", ignoreCase = true)
        return catalog.resolve(poweredBy.replace(withoutPayee, " "))?.key
    }

    /** The accounts of the bank [key], of the kind [instrument] says (a card, a bank account, a wallet) when it says. */
    private fun inBank(key: String, instrument: SharedInstrument?, accounts: List<Account>): List<Account> {
        val ofBank = accounts.filter { it.bankKey == key }
        return when (instrument) {
            SharedInstrument.CreditCard -> ofBank.filter { it.kind == AccountKind.Card }
            SharedInstrument.DebitCard, SharedInstrument.BankAccount, SharedInstrument.Upi -> ofBank.filter { it.kind != AccountKind.Card }
            SharedInstrument.Wallet -> ofBank.filter { it.kind == AccountKind.Wallet }
            SharedInstrument.Card, null -> ofBank
        }
    }

    /**
     * The Wallet cards whose number is the one shown, for digits no account carries: cards linked to no account at all ([every]
     * account counted, archived ones too), narrowed to the receipt's [bank] when several have the digits.
     */
    private fun cardsShown(
        masks: List<MaskedNumber>,
        cards: List<StoredCard>,
        every: List<Account>,
        bank: String?,
        catalog: BankCatalog,
    ): List<StoredCard> {
        if (masks.isEmpty() || cards.isEmpty()) return emptyList()
        val linked = every.mapNotNull { it.cardId }.toSet()
        val named = cards.filter { it.id !in linked && AccountNumbers.cardFits(it, masks) }
        return if (named.size > 1 && bank != null) named.filter { bankOf(it, catalog) == bank } else named
    }

    /**
     * For each of the [shown] cards, the accounts of the card's bank and kind (a credit card is a card account, a debit card a
     * bank one) that are not another card's and not ruled out by their own digits.
     */
    private fun accountsOfCards(
        shown: List<StoredCard>,
        cards: List<StoredCard>,
        every: List<Account>,
        active: List<Account>,
        fits: Map<Account, NumberFit>,
        catalog: BankCatalog,
    ): List<Account> = shown.flatMap { card ->
        val cardBank = bankOf(card, catalog) ?: return@flatMap emptyList()
        active.filter { account ->
            account.bankKey == cardBank &&
                kindFits(account, card.kind) &&
                (account.cardId == null || cards.none { it.id == account.cardId }) &&
                fits[account] != NumberFit.Contradicts
        }
    }.distinct()

    private fun bankOf(card: StoredCard, catalog: BankCatalog): String? = card.bankKey ?: catalog.resolve(card.bankName)?.key

    /** Whether [account] is the sort an [kind] card belongs to: a credit card on a card account, a debit card on a bank one. */
    private fun kindFits(account: Account, kind: CardKind): Boolean = when (kind) {
        CardKind.Credit -> account.kind == AccountKind.Card
        CardKind.Debit -> account.kind == AccountKind.Bank || account.kind == AccountKind.Savings || account.kind == AccountKind.Other
    }
}
