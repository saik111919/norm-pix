package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Payments that look like they repeat: the same payee at about the same amount on a regular rhythm, from the expenses typed in
 * one by one - and not when there are too few, the gaps are irregular, the amounts differ, the newest is stale, a repeating payment
 * already exists, the payee was dismissed or there is no payee. Every date is fixed: today is 14 November 2026.
 */
class RepeatSuggestionsTest {
    private val today = LocalDate.of(2026, 11, 14)
    private var nextId = 1L

    private fun payment(
        payee: String?,
        amount: Long,
        on: LocalDate,
        recurringId: Long? = null,
        type: TransactionType = TransactionType.Expense,
        account: Long = 1,
        category: Long? = 7,
    ): FinanceTransaction {
        val at = on.atTime(18, 30)
        return FinanceTransaction(
            id = nextId++,
            type = type,
            amount = amount,
            accountId = account,
            toAccountId = if (type == TransactionType.Transfer) 2 else null,
            categoryId = if (type == TransactionType.Transfer) null else category,
            occurredAt = at,
            payee = payee,
            recurringId = recurringId,
            createdAt = at,
            updatedAt = at,
        )
    }

    /** [count] payments to [payee], the newest [newestAgo] days before today, each [gap] days after the one before. */
    private fun rhythm(payee: String, amount: Long, gap: Long, count: Int, newestAgo: Long): List<FinanceTransaction> =
        (0 until count).map { i -> payment(payee, amount, today.minusDays(newestAgo + gap * (count - 1 - i))) }

    private fun rule(payee: String?, active: Boolean = true, ended: Boolean = false, type: TransactionType = TransactionType.Expense): RecurringRule {
        val at = LocalDateTime.of(2026, 1, 1, 9, 0)
        return RecurringRule(
            type = type,
            amount = 64_900,
            accountId = 1,
            categoryId = 7,
            payee = payee,
            frequency = RecurrenceFrequency.Monthly,
            startDate = at,
            endDate = if (ended) at.plusMonths(2) else null,
            nextDate = if (ended) at.plusMonths(3) else today.plusDays(10).atStartOfDay(),
            active = active,
            createdAt = at,
            updatedAt = at,
        )
    }

    private fun suggest(
        transactions: List<FinanceTransaction>,
        rules: List<RecurringRule> = emptyList(),
        dismissed: Set<String> = emptySet(),
    ) = suggestRepeats(transactions, rules, dismissed, today)

    // ------------------------------------------------------------------------------------------ what qualifies

    @Test
    fun `a payee paid about every month is offered, with what the newest payment says`() {
        val payments = listOf(
            payment("Netflix", 64_900, LocalDate.of(2026, 7, 12), account = 4, category = 9),
            payment("Netflix", 64_900, LocalDate.of(2026, 8, 12), account = 4, category = 9),
            payment("Netflix", 64_900, LocalDate.of(2026, 9, 12), account = 4, category = 9),
            payment("Netflix", 64_900, LocalDate.of(2026, 10, 12), account = 3, category = 8),
        )
        val found = suggest(payments).single()
        assertEquals("netflix", found.key)
        assertEquals("Netflix", found.payee)
        assertEquals(64_900, found.amount)
        assertEquals(RecurrenceFrequency.Monthly, found.frequency)
        assertEquals(4, found.seen)
        // The newest payment's account and category.
        assertEquals(3, found.accountId)
        assertEquals(8L, found.categoryId)
        assertEquals(LocalDate.of(2026, 10, 12), found.lastDate)
        // The newest and one period on.
        assertEquals(LocalDate.of(2026, 11, 12), found.nextDate)
    }

    @Test
    fun `a weekly rhythm is six to eight days apart`() {
        for (gap in 6L..8L) {
            assertEquals(RecurrenceFrequency.Weekly, suggest(rhythm("Milk man", 12_000, gap, 4, 3)).single().frequency, "gap $gap")
        }
        assertTrue(suggest(rhythm("Milk man", 12_000, 5, 4, 3)).isEmpty(), "five days is no frequency")
        assertTrue(suggest(rhythm("Milk man", 12_000, 9, 4, 3)).isEmpty(), "nine days is no frequency")
        assertEquals(LocalDate.of(2026, 11, 18), suggest(rhythm("Milk man", 12_000, 7, 4, 3)).single().nextDate, "the newest (11 Nov) and a week")
    }

    @Test
    fun `a monthly rhythm is twenty-six to thirty-five days apart`() {
        for (gap in listOf(26L, 30L, 35L)) {
            assertEquals(RecurrenceFrequency.Monthly, suggest(rhythm("Gym", 250_000, gap, 3, 10)).single().frequency, "gap $gap")
        }
        assertTrue(suggest(rhythm("Gym", 250_000, 25, 3, 10)).isEmpty())
        assertTrue(suggest(rhythm("Gym", 250_000, 36, 3, 10)).isEmpty())
    }

    @Test
    fun `a yearly one needs two payments a year apart, since two years of history would never fit the window`() {
        val found = suggest(rhythm("Prime", 149_900, 365, 2, 40)).single()
        assertEquals(RecurrenceFrequency.Yearly, found.frequency)
        assertEquals(2, found.seen)
        assertEquals(today.minusDays(40).plusYears(1), found.nextDate)
        assertTrue(suggest(rhythm("Prime", 149_900, 349, 2, 40)).isEmpty(), "349 days is not a year")
        assertEquals(1, suggest(rhythm("Prime", 149_900, 380, 2, 40)).size, "380 days is")
        assertTrue(suggest(rhythm("Prime", 149_900, 381, 2, 40)).isEmpty())
        assertTrue(suggest(listOf(payment("Prime", 149_900, today.minusDays(40)))).isEmpty(), "one payment is no rhythm")
    }

    @Test
    fun `at least three payments make a weekly or monthly pattern`() {
        assertEquals(1, suggest(rhythm("Gym", 250_000, 30, 3, 10)).size)
        assertTrue(suggest(rhythm("Gym", 250_000, 30, 2, 10)).isEmpty(), "two payments are not a pattern")
        assertTrue(suggest(rhythm("Gym", 250_000, 7, 2, 3)).isEmpty())
        assertTrue(suggest(emptyList()).isEmpty())
    }

    @Test
    fun `only the payments of the last thirteen months are counted`() {
        // Sixteen monthly payments, the newest 10 days ago: 13 months back from today is 396 days, so the three oldest are out.
        val long = (0 until 16).map { payment("Gym", 250_000, today.minusDays(10L + 30 * (15 - it))) }
        assertEquals(13, suggest(long).single().seen)
    }

    // ---------------------------------------------------------------------------------------- what does not

    @Test
    fun `irregular gaps are no pattern`() {
        val on = listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 12), LocalDate.of(2026, 10, 12)) // 11 days, then 61
        assertTrue(suggest(on.map { payment("Gym", 250_000, it) }).isEmpty())
        // One odd gap in the middle ends the run: three fitting payments are left at the newest end - enough.
        val gappy = listOf(
            LocalDate.of(2026, 3, 1), // a long way before
            LocalDate.of(2026, 8, 12),
            LocalDate.of(2026, 9, 12),
            LocalDate.of(2026, 10, 12),
        )
        val found = suggest(gappy.map { payment("Gym", 250_000, it) }).single()
        assertEquals(3, found.seen, "the older payment that no longer fits is left out")
        // ... but only two at the newest end is not enough.
        val twoFit = listOf(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 12), LocalDate.of(2026, 10, 12))
        assertTrue(suggest(twoFit.map { payment("Gym", 250_000, it) }).isEmpty())
    }

    @Test
    fun `amounts have to be within twenty percent of their median`() {
        fun amounts(vararg values: Long) = values.mapIndexed { i, v -> payment("Cab", v, today.minusDays(10L + 30 * (values.size - 1 - i))) }
        assertEquals(1, suggest(amounts(100, 110, 120)).size, "9% and 9% off 110")
        assertEquals(1, suggest(amounts(100, 100, 120)).size, "exactly 20% over the median is still in")
        assertTrue(suggest(amounts(100, 100, 121)).isEmpty(), "20.1% over is out")
        assertTrue(suggest(amounts(100, 100, 79)).isEmpty(), "21% under is out")
        assertEquals(1, suggest(amounts(100, 100, 80)).size, "exactly 20% under is in")
        // A price change long ago does not hide a plain pattern now: the older payment that does not agree is left out.
        val raised = suggest(amounts(44_900, 64_900, 64_900, 64_900, 64_900)).single()
        assertEquals(4, raised.seen)
        assertEquals(64_900, raised.amount)
        // The newest payments being the ones that differ is no pattern.
        assertTrue(suggest(amounts(64_900, 64_900, 150_000, 150_000)).isEmpty(), "two at the newest end are not three")
    }

    @Test
    fun `the amounts of an even number of payments are read against the mean of the two middle ones`() {
        fun amounts(vararg values: Long) = values.mapIndexed { i, v -> payment("Cab", v, today.minusDays(10L + 30 * (values.size - 1 - i))) }
        // Median of 100, 100, 120, 130 is 110; 130 is 18% over, 100 is 9% under.
        assertEquals(4, suggest(amounts(100, 100, 120, 130)).single().seen)
        // Median of 150, 100, 100, 110 is 105: 150 is 43% over, so the oldest is cut back and 100, 100, 110 agree.
        assertEquals(3, suggest(amounts(150, 100, 100, 110)).single().seen, "the oldest is cut back until they agree")
    }

    @Test
    fun `a payment that stopped is not offered - the newest may be at most one and a half periods ago`() {
        assertEquals(1, suggest(rhythm("Gym", 250_000, 30, 3, 45)).size, "45 days is one and a half months")
        assertTrue(suggest(rhythm("Gym", 250_000, 30, 3, 46)).isEmpty())
        assertEquals(1, suggest(rhythm("Milk man", 12_000, 7, 4, 10)).size, "10 days is one and a half weeks")
        assertTrue(suggest(rhythm("Milk man", 12_000, 7, 4, 11)).isEmpty())
        assertEquals(1, suggest(rhythm("Prime", 149_900, 365, 2, 547)).size)
        assertTrue(suggest(rhythm("Prime", 149_900, 365, 2, 548)).isEmpty())
    }

    @Test
    fun `an active repeating payment for the payee, however it is written, means no suggestion`() {
        val payments = rhythm("Netflix", 64_900, 30, 4, 10)
        assertTrue(suggest(payments, rules = listOf(rule("Netflix"))).isEmpty())
        assertTrue(suggest(payments, rules = listOf(rule("  NETFLIX  "))).isEmpty(), "case and spaces do not matter")
        assertEquals(1, suggest(payments, rules = listOf(rule("Netflix India"))).size, "a different name is a different payee")
        // A paused or ended repeating payment is not "active", and a rule for another payee changes nothing.
        assertEquals(1, suggest(payments, rules = listOf(rule("Netflix", active = false))).size)
        assertEquals(1, suggest(payments, rules = listOf(rule("Netflix", ended = true))).size)
        assertEquals(1, suggest(payments, rules = listOf(rule("Spotify"))).size)
        assertEquals(1, suggest(payments, rules = listOf(rule(null))).size)
    }

    @Test
    fun `a payee the user said not this one to is not offered again`() {
        val payments = rhythm("Netflix", 64_900, 30, 4, 10)
        assertTrue(suggest(payments, dismissed = setOf("netflix")).isEmpty())
        assertEquals(1, suggest(payments, dismissed = setOf("spotify")).size)
    }

    @Test
    fun `no payee, no suggestion`() {
        val blank = rhythm("x", 64_900, 30, 4, 10).map { payment(null, it.amount, it.occurredAt.toLocalDate()) }
        assertTrue(suggest(blank).isEmpty())
        assertTrue(suggest(blank.map { payment("   ", it.amount, it.occurredAt.toLocalDate()) }).isEmpty())
        assertNull(payeeKey(null))
        assertNull(payeeKey("   "))
    }

    @Test
    fun `only past expenses that no repeating payment recorded count`() {
        val base = rhythm("Netflix", 64_900, 30, 3, 10)
        // Recorded by a rule: not typed in, so not evidence of a payment that should be made repeating.
        assertTrue(suggest(base.map { payment("Netflix", it.amount, it.occurredAt.toLocalDate(), recurringId = 4) }).isEmpty())
        // Income and transfers are not spending.
        assertTrue(suggest(base.map { payment("Netflix", it.amount, it.occurredAt.toLocalDate(), type = TransactionType.Income) }).isEmpty())
        assertTrue(suggest(base.map { payment("Netflix", it.amount, it.occurredAt.toLocalDate(), type = TransactionType.Transfer) }).isEmpty())
        // What is dated ahead has not happened: three real payments and two upcoming are still three.
        val ahead = base + listOf(payment("Netflix", 64_900, today.plusDays(20)), payment("Netflix", 64_900, today.plusDays(50)))
        assertEquals(3, suggest(ahead).single().seen)
        // A payment dated today is a payment.
        val todays = listOf(
            payment("Gym", 250_000, today.minusDays(60)),
            payment("Gym", 250_000, today.minusDays(30)),
            payment("Gym", 250_000, today),
        )
        assertEquals(3, suggest(todays).single().seen)
    }

    // -------------------------------------------------------------------------------------- grouping and order

    @Test
    fun `payees are grouped by trimmed lower case with runs of spaces made one`() {
        assertEquals("amazon prime", payeeKey("  Amazon   PRIME\t"))
        val payments = listOf(
            payment("Amazon Prime", 149_900, today.minusDays(70)),
            payment("amazon  prime", 149_900, today.minusDays(40)),
            payment(" AMAZON PRIME ", 149_900, today.minusDays(10)),
        )
        val found = suggest(payments).single()
        assertEquals(3, found.seen)
        assertEquals("AMAZON PRIME", found.payee, "the payee is as the newest payment wrote it, trimmed")
    }

    @Test
    fun `payees are read on their own`() {
        val payments = rhythm("Netflix", 64_900, 30, 3, 10) + rhythm("Spotify", 11_900, 30, 3, 14) + rhythm("Cab", 30_000, 4, 5, 2)
        assertEquals(setOf("netflix", "spotify"), suggest(payments).map { it.key }.toSet())
    }

    @Test
    fun `the dearest a year comes first and at most three are shown`() {
        val payments = rhythm("Spotify", 11_900, 30, 3, 14) + // 142,800 a year
            rhythm("Milk man", 12_000, 7, 4, 3) + // 624,000
            rhythm("Netflix", 64_900, 30, 3, 10) + // 778,800
            rhythm("Gym", 250_000, 30, 3, 5) + // 3,000,000
            rhythm("Cloud", 13_000, 30, 3, 6) // 156,000
        assertEquals(listOf("gym", "netflix", "milk man"), suggest(payments).map { it.key })
    }

    @Test
    fun `a suggestion starts on its next day, or the next one after it when that has gone by`() {
        // Newest on 12 Oct, monthly: expected 12 Nov - already past on 14 Nov - so 12 Dec.
        val found = suggest(rhythm("Netflix", 64_900, 30, 3, 33)).single()
        assertEquals(today.minusDays(33).plusMonths(1), found.nextDate)
        assertEquals(LocalDate.of(2026, 12, 12), found.startOnOrAfter(today).also { assertTrue(!it.isBefore(today)) })
        // A day that has not come yet is kept, and so is today.
        assertEquals(found.nextDate, found.startOnOrAfter(found.nextDate.minusDays(3)))
        assertEquals(found.nextDate, found.startOnOrAfter(found.nextDate))
        // Weekly goes on a week at a time, a month's end clamps the way a month does.
        val weekly = suggest(rhythm("Milk man", 12_000, 7, 4, 10)).single()
        assertEquals(today.minusDays(10).plusWeeks(2), weekly.startOnOrAfter(today))
        val endOfMonth = suggest(
            listOf(
                payment("Rent", 100, LocalDate.of(2026, 8, 31)),
                payment("Rent", 100, LocalDate.of(2026, 9, 30)),
                payment("Rent", 100, LocalDate.of(2026, 10, 31)),
            ),
        ).single()
        assertEquals(LocalDate.of(2026, 11, 30), endOfMonth.nextDate, "31 Oct and a month is the last of November")
    }
}
