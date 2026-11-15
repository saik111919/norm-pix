package com.example.normpix.finance.domain

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Split payments as a rule (2026-11-15): what a group that holds together is (2 to 10 rows, spending or income - never a transfer -, one
 * account, one date and time, one payee, one person), what it says when it doesn't, the helpers unsplit uses, and what the ledger check
 * makes of a group - finds a broken one, and leaves the parts of a good one out of its other findings.
 */
class SplitsTest {
    private val at = LocalDateTime.of(2026, 11, 12, 18, 30)
    private val now = LocalDateTime.of(2026, 11, 15, 9, 0)
    private var nextId = 1L

    private fun part(
        amount: Long = 500,
        category: Long? = 10,
        note: String? = null,
        type: TransactionType = TransactionType.Expense,
        account: Long = 1,
        on: LocalDateTime = at,
        payee: String? = "DMart",
        person: Long? = null,
        group: String? = "g",
        to: Long? = null,
        id: Long = nextId++,
    ) = FinanceTransaction(
        id = id, type = type, amount = amount, accountId = account, toAccountId = to, categoryId = category, occurredAt = on, payee = payee, note = note,
        createdAt = at, updatedAt = at, personId = person, splitGroup = group,
    )

    private fun parts(n: Int) = List(n) { part() }

    // ------------------------------------------------------------------------------------------------ what holds

    @Test
    fun `two to ten parts of one payment hold together`() {
        for (n in 2..10) assertNull(splitFault(parts(n)), "$n parts")
        assertEquals(2, MIN_SPLIT_PARTS)
        assertEquals(10, MAX_SPLIT_PARTS)
    }

    @Test
    fun `one part, or eleven, does not`() {
        assertEquals(SplitFault.TooFew, splitFault(parts(1)))
        assertEquals(SplitFault.TooFew, splitFault(emptyList()))
        assertEquals(SplitFault.TooMany, splitFault(parts(11)))
    }

    @Test
    fun `income holds as spending does, a mix of the two does not, and a transfer never does`() {
        assertNull(splitFault(listOf(part(type = TransactionType.Income), part(type = TransactionType.Income))))
        assertEquals(SplitFault.MixedType, splitFault(listOf(part(), part(type = TransactionType.Income))))
        assertEquals(SplitFault.Transfer, splitFault(listOf(part(), part(type = TransactionType.Transfer, category = null, to = 2))))
        assertEquals(
            SplitFault.Transfer,
            splitFault(listOf(part(type = TransactionType.Transfer, category = null, to = 2), part(type = TransactionType.Transfer, category = null, to = 2))),
        )
    }

    @Test
    fun `parts must share the account, the moment, the payee and the person`() {
        assertEquals(SplitFault.MixedAccount, splitFault(listOf(part(), part(account = 2))))
        assertEquals(SplitFault.MixedMoment, splitFault(listOf(part(), part(on = at.plusMinutes(1)))))
        assertEquals(SplitFault.MixedMoment, splitFault(listOf(part(), part(on = at.plusSeconds(1)))))
        assertEquals(SplitFault.MixedPayee, splitFault(listOf(part(), part(payee = "Other"))))
        assertEquals(SplitFault.MixedPayee, splitFault(listOf(part(), part(payee = null))))
        assertEquals(SplitFault.MixedPerson, splitFault(listOf(part(person = 1), part(person = 2))))
        assertEquals(SplitFault.MixedPerson, splitFault(listOf(part(person = 1), part())))
    }

    @Test
    fun `no payee, a blank one, and a payee with spaces round it are the same payee`() {
        assertNull(splitFault(listOf(part(payee = null), part(payee = "  "))))
        assertNull(splitFault(listOf(part(payee = " DMart"), part(payee = "DMart "))))
        assertEquals(SplitFault.MixedPayee, splitFault(listOf(part(payee = "dmart"), part(payee = "DMart"))), "the case is part of a name")
    }

    @Test
    fun `parts may differ in what is their own - category, amount and note`() {
        assertNull(splitFault(listOf(part(amount = 100, category = 10, note = "a"), part(amount = 900, category = 11, note = null))))
        assertNull(splitFault(listOf(part(amount = 500, category = 10), part(amount = 500, category = 10))), "even two just alike")
    }

    @Test
    fun `every fault has words that finish a sentence`() {
        for (fault in SplitFault.entries) assertTrue(fault.problem.isNotBlank(), fault.name)
        assertEquals("has only one part", SplitFault.TooFew.problem)
        assertEquals("has more than 10 parts", SplitFault.TooMany.problem)
    }

    // ------------------------------------------------------------------------------------------------ the groups

    @Test
    fun `rows are grouped by their id, each group in the order they were entered, and rows without one are left out`() {
        val a2 = part(group = "a", id = 7)
        val b1 = part(group = "b", id = 3)
        val a1 = part(group = "a", id = 2)
        val none = part(group = null, id = 1)
        val groups = splitGroupsOf(listOf(a2, b1, none, a1))
        assertEquals(setOf("a", "b"), groups.keys)
        assertEquals(listOf(2L, 7L), groups.getValue("a").map { it.id })
        assertEquals(listOf(3L), groups.getValue("b").map { it.id })
    }

    @Test
    fun `broken groups are found, the oldest first, and good ones are not`() {
        val good = listOf(part(group = "good", id = 1), part(group = "good", id = 2))
        val alone = listOf(part(group = "alone", id = 5))
        val mixed = listOf(part(group = "mixed", id = 3), part(group = "mixed", id = 4, account = 2))
        val broken = brokenSplitGroups(good + alone + mixed)
        assertEquals(listOf("mixed", "alone"), broken.map { it.group })
        assertEquals(listOf(SplitFault.MixedAccount, SplitFault.TooFew), broken.map { it.fault })
        assertEquals(listOf(3L, 4L), broken.first().rows.map { it.id })
        assertTrue(brokenSplitGroups(good).isEmpty())
        assertTrue(brokenSplitGroups(emptyList()).isEmpty())
    }

    @Test
    fun `the mark of a group that does not hold is taken off, every row else exactly as it is`() {
        val good = listOf(part(group = "good", id = 1), part(group = "good", id = 2, amount = 900))
        val alone = part(group = "alone", id = 5, amount = 123, note = "keep me")
        val plain = part(group = null, id = 6)
        val all = good + alone + plain
        val cleaned = withoutBrokenSplits(all)
        assertEquals(listOf("good", "good", null, null), cleaned.map { it.splitGroup })
        assertEquals(alone.copy(splitGroup = null), cleaned[2], "nothing else of the row changed")
        assertSame(good[0], cleaned[0])
        // A ledger with nothing broken comes back as it is.
        assertSame(good, withoutBrokenSplits(good))
    }

    // -------------------------------------------------------------------------------------------- what unsplit uses

    @Test
    fun `the total is the sum of the parts`() {
        assertEquals(1_400L, splitTotal(listOf(part(amount = 500), part(amount = 900))))
        assertEquals(0L, splitTotal(emptyList()))
    }

    @Test
    fun `the default category is the largest part's, the first of two as large`() {
        assertEquals(11L, largestPartCategory(listOf(part(amount = 100, category = 10), part(amount = 900, category = 11), part(amount = 50, category = 12))))
        assertEquals(10L, largestPartCategory(listOf(part(amount = 500, category = 10), part(amount = 500, category = 11))))
        assertNull(largestPartCategory(emptyList()))
    }

    @Test
    fun `the notes are joined with a dot, tidied, and the empty ones left out`() {
        assertEquals("rice · soap", joinedSplitNotes(listOf(part(note = " rice "), part(note = null), part(note = "  "), part(note = "soap"))))
        assertNull(joinedSplitNotes(listOf(part(note = null), part(note = ""))))
        assertEquals("only", joinedSplitNotes(listOf(part(note = "only"))))
    }

    // ----------------------------------------------------------------------------------------------- the ledger check

    private val bank = Account(id = 1, name = "HDFC Salary", kind = AccountKind.Bank, createdAt = at, updatedAt = at)
    private val savings = Account(id = 2, name = "SBI Savings", kind = AccountKind.Bank, createdAt = at, updatedAt = at)
    private val card = Account(id = 3, name = "HDFC Millennia", kind = AccountKind.Card, createdAt = at, updatedAt = at, numberEnding = "4455")
    private val food = Category(id = 10, name = "Food", type = CategoryType.Expense, icon = "other", createdAt = at, updatedAt = at)
    private val home = Category(id = 11, name = "Home", type = CategoryType.Expense, icon = "other", createdAt = at, updatedAt = at)
    private val salary = Category(id = 20, name = "Salary", type = CategoryType.Income, icon = "other", createdAt = at, updatedAt = at)

    private fun check(transactions: List<FinanceTransaction>) = checkLedger(
        accounts = listOf(bank, savings, card), categories = listOf(food, home, salary), transactions = transactions, rules = emptyList(), budgets = emptyList(),
        balances = emptyMap(), defaultAccountId = null, now = now,
    )

    @Test
    fun `the check finds a group that no longer holds, and counts it as a finding but not as a repair`() {
        val issues = check(listOf(part(group = "alone", id = 1), part(group = "ok", id = 2), part(group = "ok", id = 3), part(group = "mixed", id = 4), part(group = "mixed", id = 5, account = 2)))
        assertEquals(listOf("alone", "mixed"), issues.brokenSplits.map { it.group })
        assertEquals(listOf(SplitFault.TooFew, SplitFault.MixedAccount), issues.brokenSplits.map { it.fault })
        assertEquals(2, issues.findingCount)
        assertEquals(0, issues.repairCount, "the check never ends a split by itself: it offers")
    }

    @Test
    fun `a ledger of good groups, or none, has nothing to say about splits`() {
        assertTrue(check(listOf(part(group = "ok", id = 1), part(group = "ok", id = 2))).brokenSplits.isEmpty())
        assertEquals(0, check(listOf(part(group = "ok", id = 1), part(group = "ok", id = 2))).findingCount)
        assertTrue(check(emptyList()).brokenSplits.isEmpty())
    }

    @Test
    fun `parts alike in amount are never taken for a payment entered twice`() {
        // Two parts of one bill are on one account, one day, one payee - and may be the same amount: that is a split, not a repeat.
        val alike = check(listOf(part(amount = 500, group = "ok", id = 1), part(amount = 500, group = "ok", id = 2)))
        assertTrue(alike.duplicates.isEmpty(), "the parts of a split are not duplicates")
        // Rows that are not parts are still found as before.
        val twice = check(listOf(part(amount = 500, group = null, id = 1), part(amount = 500, group = null, id = 2)))
        assertEquals(1, twice.duplicates.size)
        // And a part is not a repeat of a payment of its own that happens to be as large.
        val beside = check(listOf(part(amount = 500, group = "ok", id = 1), part(amount = 300, group = "ok", id = 2), part(amount = 500, group = null, id = 3)))
        assertTrue(beside.duplicates.isEmpty())
    }

    @Test
    fun `a part is never offered as one half of a transfer between the user's accounts, nor as a card bill`() {
        val income = part(amount = 500, type = TransactionType.Income, category = 20, account = 2, payee = null, group = null, id = 9)
        // As a plain expense the same row would be paired with the income of the same amount on another account.
        assertEquals(1, check(listOf(part(amount = 500, group = null, id = 1), income)).selfTransfers.size)
        assertTrue(check(listOf(part(amount = 500, group = "ok", id = 1), part(amount = 300, group = "ok", id = 2), income)).selfTransfers.isEmpty())
        // "HDFC Millennia bill" on a plain expense is a card bill paid from another account; on a part it is left alone.
        val bill = part(amount = 500, payee = "HDFC Millennia credit card bill", group = null, id = 1)
        assertEquals(1, check(listOf(bill)).cardBills.size)
        assertTrue(check(listOf(bill.copy(splitGroup = "ok"), bill.copy(id = 2, splitGroup = "ok", amount = 700))).cardBills.isEmpty())
    }

    @Test
    fun `a part's category is checked like any other row's`() {
        val wrong = part(category = 20, group = "ok", id = 1)
        val issues = check(listOf(wrong, part(group = "ok", id = 2)))
        assertEquals(listOf(wrong), issues.wrongCategory)
        assertTrue(issues.brokenSplits.isEmpty(), "the group still holds: only its category is wrong")
    }

    @Test
    fun `the transfers the repairs make carry no split group`() {
        val expense = part(group = "g", id = 1)
        val income = part(type = TransactionType.Income, category = 20, account = 2, group = null, id = 2)
        assertNull(SelfTransferPair(expense, income).asTransfer(now).splitGroup)
        assertNull(CardBillExpense(expense, card).asTransfer(now).splitGroup)
    }
}
