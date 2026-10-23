package com.example.normpix.finance.share

import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.StoredCard
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which account a shared receipt's partly hidden number points to: pure, on accounts and cards in memory. */
class AccountNumberMatchTest {
    private val now = LocalDateTime.of(2026, 10, 12, 9, 30)
    private val catalog = BankCatalog.BuiltIn

    private var nextId = 1L

    private fun account(
        name: String,
        kind: AccountKind = AccountKind.Bank,
        bankKey: String? = "hdfc",
        ending: String? = null,
        cardId: String? = null,
        archived: Boolean = false,
    ) = Account(
        id = nextId++, name = name, kind = kind, bankKey = bankKey, numberEnding = ending, cardId = cardId, archived = archived,
        createdAt = now, updatedAt = now,
    )

    private fun card(id: String, number: String, kind: CardKind = CardKind.Credit, bank: String = "HDFC Bank") =
        StoredCard(id, bank, "THE USER", number, "08", "29", "123", kind, now)

    private fun masks(vararg m: Pair<String, String>) = m.map { (prefix, suffix) -> MaskedNumber(prefix, suffix) }

    // ------------------------------------------------------------------ fit

    @Test
    fun `an ending fits the digits a message shows at the end of a number`() {
        val acct = account("Salary", ending = "3588")
        assertEquals(NumberFit.Match, AccountNumbers.fit(acct, masks("" to "3588"), emptyList()))
        // More digits shown than kept: the last four are compared.
        assertEquals(NumberFit.Match, AccountNumbers.fit(acct, masks("" to "03588"), emptyList()))
        // Three digits are enough; two are not, though they agree.
        assertEquals(NumberFit.Match, AccountNumbers.fit(acct, masks("" to "588"), emptyList()))
        assertEquals(NumberFit.Weak, AccountNumbers.fit(acct, masks("" to "88"), emptyList()))
        // Different digits.
        assertEquals(NumberFit.Contradicts, AccountNumbers.fit(acct, masks("" to "1234"), emptyList()))
        assertEquals(NumberFit.Contradicts, AccountNumbers.fit(acct, masks("" to "99"), emptyList()))
    }

    @Test
    fun `an account with no number, or a message with none, has nothing to compare`() {
        assertEquals(NumberFit.Unknown, AccountNumbers.fit(account("A"), masks("" to "3588"), emptyList()))
        assertEquals(NumberFit.Unknown, AccountNumbers.fit(account("A", ending = "3588"), emptyList(), emptyList()))
        // An ending that isn't four digits is not an ending.
        assertEquals(NumberFit.Unknown, AccountNumbers.fit(account("A", ending = "35"), masks("" to "3588"), emptyList()))
    }

    @Test
    fun `a linked card's whole number checks both ends of what a message shows`() {
        val acct = account("RuPay card", AccountKind.Card, cardId = "rupay")
        val cards = listOf(card("rupay", "6528505555554455"))
        assertEquals(NumberFit.Match, AccountNumbers.fit(acct, masks("652850" to "55"), cards))
        assertEquals(NumberFit.Match, AccountNumbers.fit(acct, masks("" to "4455"), cards))
        // Same last two digits, other first six: not this card.
        assertEquals(NumberFit.Contradicts, AccountNumbers.fit(acct, masks("411111" to "55"), cards))
        assertEquals(NumberFit.Contradicts, AccountNumbers.fit(acct, masks("" to "1111"), cards))
    }

    @Test
    fun `a card that is no longer in the Wallet counts for nothing`() {
        val acct = account("Old card", AccountKind.Card, cardId = "gone")
        assertEquals(NumberFit.Unknown, AccountNumbers.fit(acct, masks("" to "4455"), listOf(card("other", "4111111111114455"))))
        // Its ending still counts.
        val withEnding = acct.copy(numberEnding = "4455")
        assertEquals(NumberFit.Match, AccountNumbers.fit(withEnding, masks("" to "4455"), emptyList()))
    }

    @Test
    fun `a bank account linked to its debit card is still known by its own last digits`() {
        // Google Pay prints "HDFC Bank 3588": the account's digits, not the card's. The debit card's number ends otherwise.
        val acct = account("HDFC Salary", AccountKind.Bank, ending = "3588", cardId = "debit")
        val cards = listOf(card("debit", "4111111111114421", CardKind.Debit))
        assertEquals(NumberFit.Match, AccountNumbers.fit(acct, masks("" to "3588"), cards))
        // The card's own digits, as a receipt of a card payment shows them, are this account's too.
        assertEquals(NumberFit.Match, AccountNumbers.fit(acct, masks("411111" to "4421"), cards))
        assertEquals(NumberFit.Match, AccountNumbers.fit(acct, masks("" to "4421"), cards))
        // Digits that are neither the account's nor the card's are not this account.
        assertEquals(NumberFit.Contradicts, AccountNumbers.fit(acct, masks("" to "9999"), cards))
    }

    @Test
    fun `a card's number cannot rule out a bank account whose own digits are not known`() {
        val acct = account("HDFC Salary", AccountKind.Bank, cardId = "debit")
        val cards = listOf(card("debit", "4111111111114421", CardKind.Debit))
        // An account's number is not its card's: a receipt's "…3588" says nothing against this account.
        assertEquals(NumberFit.Unknown, AccountNumbers.fit(acct, masks("" to "3588"), cards))
        // A number printed with its first digits is a card's, and this card's digits differ: that is a different card.
        assertEquals(NumberFit.Contradicts, AccountNumbers.fit(acct, masks("652850" to "55"), cards))
        assertEquals(NumberFit.Match, AccountNumbers.fit(acct, masks("" to "4421"), cards))
        // A card account is its card: other digits rule it out, as before.
        val creditCard = account("HDFC Credit", AccountKind.Card, cardId = "debit")
        assertEquals(NumberFit.Contradicts, AccountNumbers.fit(creditCard, masks("" to "3588"), cards))
    }

    @Test
    fun `a credit card account is told by more than its last four digits when a receipt shows less of them`() {
        // A RuPay credit card on UPI shows its first six and last two digits ("652850XXXXXXXX55"): the last four alone cannot be checked.
        val withStart = account("HDFC RuPay", AccountKind.Card, ending = "4455").copy(numberStart = "652850")
        val endingOnly = account("HDFC Millennia", AccountKind.Card, ending = "4455")
        // First six + last two agree with what was kept: this card.
        assertEquals(NumberFit.Match, AccountNumbers.fit(withStart, masks("652850" to "55"), emptyList()))
        // The same two last digits with other first six: not this card.
        assertEquals(NumberFit.Contradicts, AccountNumbers.fit(withStart, masks("411111" to "55"), emptyList()))
        // Only the last four were kept, so two shown digits agree but cannot say it is this card: the choice is left.
        assertEquals(NumberFit.Weak, AccountNumbers.fit(endingOnly, masks("652850" to "55"), emptyList()))
        // A statement-style "ending 4455" (four digits) is enough by itself.
        assertEquals(NumberFit.Match, AccountNumbers.fit(endingOnly, masks("" to "4455"), emptyList()))
    }

    @Test
    fun `any of several numbers shown may be the account's`() {
        val acct = account("Salary", ending = "3588")
        assertEquals(NumberFit.Match, AccountNumbers.fit(acct, masks("" to "1234", "" to "3588"), emptyList()))
        assertEquals(NumberFit.Weak, AccountNumbers.fit(acct, masks("" to "1234", "" to "88"), emptyList()))
    }

    // -------------------------------------------------------------- finding

    private fun find(
        text: String,
        accounts: List<Account>,
        cards: List<StoredCard> = emptyList(),
    ): SharedTransactionImport.AccountMatch {
        val draft = assertIs<SharedTextReading.Draft>(SharedTransactionParser.read(text, CurrencySpec.INR, LocalDate.of(2026, 10, 12))).draft
        return SharedTransactionImport.findAccount(text, draft.counterparty, draft.instrument, accounts, catalog, draft.maskedNumbers, cards)
    }

    private val phonePe = "Paid to\nTransaction Successful\n11:34 am on 05 Oct 2026\nSuman Sharma\nQ505076279 @ybl\nPayment Details\n" +
        "PhonePe Transaction ID\nT2610051134004403220212\nDebited from\nXXXXXXXXXX3588\nUTR: 225197484310\nPowered by\nUPI AXIS BANK\nZ10\n10"

    @Test
    fun `the digits a receipt shows pick the account that has them`() {
        val salary = account("Salary", ending = "3588")
        val spending = account("Spending", ending = "7001")
        val found = find(phonePe, listOf(spending, salary))
        assertEquals(salary.id, found.account?.id)
        assertTrue(found.byNumber)
    }

    @Test
    fun `the bank a UPI app is powered by is not the user's bank`() {
        // "Powered by UPI AXIS BANK" names the app's bank. With only an Axis account, that is not a reason to pick it.
        val axis = account("Axis Savings", bankKey = "axis")
        val found = find(phonePe, listOf(axis))
        assertNull(found.account)
    }

    @Test
    fun `nothing has those digits, so the choice is left`() {
        val salary = account("Salary", ending = "1111")
        val unnumbered = account("Spending")
        val found = find(phonePe, listOf(salary, unnumbered))
        assertNull(found.account)
        assertFalse(found.byNumber)
    }

    @Test
    fun `a Wallet card's number picks the account it was linked to`() {
        val rupay = account("HDFC RuPay", AccountKind.Card, cardId = "rupay")
        val other = account("HDFC Millennia", AccountKind.Card, cardId = "millennia")
        val cards = listOf(card("rupay", "6528505555554455"), card("millennia", "5282000011112222"))
        val googlePay = "To Mr AKBAR\n₹10\nCompleted\n10 Oct 2026, 10:07pm\nHDFC Bank\n652850XXXXXXXX55\nPayment of ₹10 completed\nUPI transaction ID\n131074460564"
        val found = find(googlePay, listOf(other, rupay), cards)
        assertEquals(rupay.id, found.account?.id)
        assertTrue(found.byNumber)
    }

    @Test
    fun `the bank still picks an account whose number is not known, but not one whose number is different`() {
        val unnumbered = account("HDFC Savings")
        val different = account("HDFC Salary", ending = "9999")
        val sms = "Rs 250.00 debited from HDFC Bank A/c XX3588 on 10-10-26. UPI Ref 428312345678"
        // The one with a different number is out; the unnumbered one could be it.
        assertEquals(unnumbered.id, find(sms, listOf(different, unnumbered)).account?.id)
        // Alone, the one with a different number is no answer.
        assertNull(find(sms, listOf(different)).account)
        // With no digits in the message, the bank alone decides, as it always did.
        assertEquals(different.id, find("Rs 250.00 debited from HDFC Bank on 10-10-26", listOf(different)).account?.id)
    }

    @Test
    fun `accounts with the same digits are told apart by bank, or left`() {
        val hdfc = account("HDFC Savings", bankKey = "hdfc", ending = "3588")
        val icici = account("ICICI Savings", bankKey = "icici", ending = "3588")
        val sms = "Rs 250.00 debited from HDFC Bank A/c XX3588 on 10-10-26"
        val told = find(sms, listOf(icici, hdfc))
        assertEquals(hdfc.id, told.account?.id)
        assertTrue(told.byNumber)
        val left = find("Rs 250.00 debited from A/c XX3588 on 10-10-26", listOf(icici, hdfc))
        assertNull(left.account)
        assertEquals(2, left.fitting)
    }

    // ------------------------------------------------ accounts first, then cards

    @Test
    fun `the accounts are all asked before the Wallet's cards - their bank too`() {
        val hdfcSavings = account("HDFC Savings", AccountKind.Bank, "hdfc")
        val iciciCard = account("ICICI Card", AccountKind.Card, "icici")
        // A card in the Wallet has these digits and belongs to ICICI; the receipt names HDFC, and the user has one HDFC account.
        val iciciCredit = card("icici-card", "5282000011114242", bank = "ICICI Bank")
        val sms = "Rs 250.00 debited from HDFC Bank A/c XX4242 on 10-10-26. UPI Ref 428312345678"

        val found = find(sms, listOf(iciciCard, hdfcSavings), listOf(iciciCredit))
        assertEquals(hdfcSavings.id, found.account?.id, "the accounts answer first: the bank names the one HDFC account")
        assertFalse(found.byNumber, "by its bank, not by the digits")
        assertNull(found.card, "the cards were not asked")
    }

    @Test
    fun `when no account fits the Wallet's cards are asked, and point to the account of the card's bank and kind`() {
        // Nothing the receipt shows names a bank or matches an account's digits: both HDFC accounts are unnumbered.
        val millennia = account("HDFC Millennia", AccountKind.Card, "hdfc")
        val savings = account("HDFC Savings", AccountKind.Bank, "hdfc")
        val credit = card("millennia", "4111111111113588", CardKind.Credit)

        val found = find(phonePe, listOf(savings, millennia), listOf(credit))
        assertEquals(millennia.id, found.account?.id, "a credit card is a card account")
        assertTrue(found.byNumber, "the card's digits chose it")
        assertEquals("millennia", found.card?.id)
        // A debit card with the same digits goes to the bank account.
        val debit = card("debit", "4111111111113588", CardKind.Debit)
        assertEquals(savings.id, find(phonePe, listOf(savings, millennia), listOf(debit)).account?.id)
    }

    @Test
    fun `a card whose account does not exist is named, not guessed`() {
        val savings = account("HDFC Savings", AccountKind.Bank, "hdfc")
        val credit = card("millennia", "4111111111113588", CardKind.Credit)

        val found = find(phonePe, listOf(savings), listOf(credit))
        assertNull(found.account, "a credit card is not the savings account")
        assertEquals("millennia", found.card?.id, "but the receipt can say which card it is")
        assertFalse(found.byNumber)
    }

    @Test
    fun `an account's own digits still come before a card's`() {
        val salary = account("Salary", ending = "3588")
        val millennia = account("HDFC Millennia", AccountKind.Card, "hdfc")
        val credit = card("millennia", "4111111111113588")

        val found = find(phonePe, listOf(millennia, salary), listOf(credit))
        assertEquals(salary.id, found.account?.id)
        assertNull(found.card)
    }

    @Test
    fun `without the Wallet's cards only the accounts are asked`() {
        val millennia = account("HDFC Millennia", AccountKind.Card, "hdfc")
        val savings = account("HDFC Savings", AccountKind.Bank, "hdfc")
        // Locked, or "Store cards" off: no cards come in, and the digits no account has leave the choice.
        assertNull(find(phonePe, listOf(savings, millennia), emptyList()).account)
    }

    @Test
    fun `an archived account is never chosen`() {
        val old = account("Old", ending = "3588", archived = true)
        assertNull(find(phonePe, listOf(old)).account)
    }
}
