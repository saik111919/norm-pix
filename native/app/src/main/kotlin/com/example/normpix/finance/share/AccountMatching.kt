package com.example.normpix.finance.share

import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.StoredCard

/**
 * Which of the user's accounts a shared receipt is about: pure, on the accounts and Wallet cards in memory.
 *
 * What a receipt shows of an account is a bank's name and a number partly hidden. In order of how much each is worth:
 * 1. **The digits.** An account is the one when what it is known by - the number of its linked Wallet card, or the first six
 *    and last four digits the user gave it - is the number shown (see [AccountNumbers]). Two accounts with those digits are
 *    told apart by the bank the receipt names, or left.
 * 2. **A Wallet card that has the digits but is linked to no account** (or the Wallet's cards, when the accounts carry none
 *    of the digits): the one account of that card's bank and kind - a credit card's bank has one card account, say. Two such
 *    accounts are not told apart by a guess.
 * 3. **The bank**, when it names one the user has exactly one fitting account with. An account whose number is known and
 *    different from the one shown is not one of those.
 *
 * With the Wallet locked, or its cards not kept ("Store cards" off), [find] gets no cards and goes by the accounts' own
 * digits alone; with none of those either, by the bank.
 */
internal object AccountMatching {
    /** The account found, how it was found, and how many accounts the digits fitted (more than one: they could not choose). */
    class Pick(val account: Account?, val byNumber: Boolean = false, val fitting: Int = 0)

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

        val byNumber = active.filter { fits[it] == NumberFit.Match }.ifEmpty {
            viaWalletCards(masks, cards, accounts, active, fits, bank, catalog)
        }
        if (byNumber.size == 1) return Pick(byNumber.single(), byNumber = true, fitting = 1)
        if (byNumber.size > 1) {
            // Two accounts end the same way (at different banks, say): the message's bank may tell them apart.
            val narrowed = byNumber.filter { it in ofBank }.singleOrNull()
            return Pick(narrowed, byNumber = narrowed != null, fitting = byNumber.size)
        }
        return Pick(ofBank.filter { fits[it] != NumberFit.Contradicts }.singleOrNull())
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
     * The accounts the Wallet's cards point to, for digits no account carries: the Wallet cards (linked to no account at all,
     * [every] account counted, archived ones too) whose number is the one shown - narrowed to the receipt's [bank] when
     * several are - and for each the accounts of the card's bank and kind (a credit card is a card account, a debit card a
     * bank one) that are not another card's and not ruled out by their own digits.
     */
    private fun viaWalletCards(
        masks: List<MaskedNumber>,
        cards: List<StoredCard>,
        every: List<Account>,
        active: List<Account>,
        fits: Map<Account, NumberFit>,
        bank: String?,
        catalog: BankCatalog,
    ): List<Account> {
        if (masks.isEmpty() || cards.isEmpty()) return emptyList()
        val linked = every.mapNotNull { it.cardId }.toSet()
        var named = cards.filter { it.id !in linked && AccountNumbers.cardFits(it, masks) }
        if (named.size > 1 && bank != null) named = named.filter { bankOf(it, catalog) == bank }
        return named.flatMap { card ->
            val cardBank = bankOf(card, catalog) ?: return@flatMap emptyList()
            active.filter { account ->
                account.bankKey == cardBank &&
                    kindFits(account, card.kind) &&
                    (account.cardId == null || cards.none { it.id == account.cardId }) &&
                    fits[account] != NumberFit.Contradicts
            }
        }.distinct()
    }

    private fun bankOf(card: StoredCard, catalog: BankCatalog): String? = card.bankKey ?: catalog.resolve(card.bankName)?.key

    /** Whether [account] is the sort an [kind] card belongs to: a credit card on a card account, a debit card on a bank one. */
    private fun kindFits(account: Account, kind: CardKind): Boolean = when (kind) {
        CardKind.Credit -> account.kind == AccountKind.Card
        CardKind.Debit -> account.kind == AccountKind.Bank || account.kind == AccountKind.Savings || account.kind == AccountKind.Other
    }
}
