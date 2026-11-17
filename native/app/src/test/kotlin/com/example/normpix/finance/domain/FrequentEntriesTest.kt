package com.example.normpix.finance.domain

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** "Frequent" in a new expense or income (2026-11-18): what counts, how it is grouped and ordered, and what each pick carries. */
class FrequentEntriesTest {
    private val now = LocalDateTime.of(2026, 11, 18, 12, 0)
    private var nextId = 1L

    private fun entry(
        payee: String?,
        category: Long = FOOD,
        amount: Long = 25_000,
        account: Long = CASH,
        daysAgo: Long = 1,
        type: TransactionType = TransactionType.Expense,
        recurring: Long? = null,
        split: String? = null,
    ) = FinanceTransaction(
        id = nextId++,
        type = type,
        amount = amount,
        accountId = account,
        categoryId = category,
        occurredAt = now.minusDays(daysAgo),
        payee = payee,
        recurringId = recurring,
        splitGroup = split,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `a payee entered again and again is one pick, whatever its case and spaces, carrying what it had last time`() {
        val picks = frequentEntries(
            listOf(
                entry("Swiggy", amount = 30_000, daysAgo = 9),
                entry(" swiggy  ", amount = 45_000, account = CARD, daysAgo = 2),
                entry("SWIGGY", amount = 38_000, daysAgo = 5),
            ),
            TransactionType.Expense,
            now,
        )
        assertEquals(1, picks.size)
        val pick = picks.single()
        assertEquals("swiggy", pick.payee, "as last written, trimmed")
        assertEquals(45_000, pick.amount)
        assertEquals(CARD, pick.accountId)
        assertEquals(3, pick.uses)
        assertEquals(FOOD, pick.categoryId)
    }

    @Test
    fun `once is not frequent, and a category used with no payee is a pick of its own`() {
        val picks = frequentEntries(
            listOf(entry("Swiggy"), entry(null, category = FUEL), entry("", category = FUEL), entry("Apollo", category = HEALTH)),
            TransactionType.Expense,
            now,
        )
        assertEquals(listOf(FUEL), picks.map { it.categoryId })
        assertEquals(null, picks.single().payee)
    }

    @Test
    fun `the same payee in two categories is two picks`() {
        val picks = frequentEntries(
            listOf(entry("Amazon", category = SHOPPING), entry("Amazon", category = SHOPPING), entry("Amazon", category = HEALTH), entry("Amazon", category = HEALTH)),
            TransactionType.Expense,
            now,
        )
        assertEquals(setOf(SHOPPING, HEALTH), picks.map { it.categoryId }.toSet())
    }

    @Test
    fun `most used first, the latest first between equals, and no more than asked`() {
        val list = buildList {
            repeat(4) { add(entry("Metro", category = TRAVEL, daysAgo = 3L + it)) }
            repeat(2) { add(entry("Swiggy", daysAgo = 10L + it)) }
            repeat(2) { add(entry("Apollo", category = HEALTH, daysAgo = 1L + it)) }
            repeat(3) { add(entry("Zepto", category = SHOPPING, daysAgo = 20L + it)) }
        }
        assertEquals(listOf("Metro", "Zepto", "Apollo", "Swiggy"), frequentEntries(list, TransactionType.Expense, now).map { it.payee })
        assertEquals(listOf("Metro", "Zepto"), frequentEntries(list, TransactionType.Expense, now, limit = 2).map { it.payee })
    }

    @Test
    fun `only what the user entered counts - not a repeating payment's entries, a split's parts, the other type, or what is dated ahead`() {
        val list = listOf(
            entry("Rent", category = HOUSING, recurring = 7), entry("Rent", category = HOUSING, recurring = 7),
            entry("DMart", category = SHOPPING, split = "a"), entry("DMart", category = SHOPPING, split = "b"),
            entry("Employer", category = SALARY, type = TransactionType.Income), entry("Employer", category = SALARY, type = TransactionType.Income),
            entry("Swiggy", daysAgo = -1), entry("Swiggy", daysAgo = -2),
        )
        assertTrue(frequentEntries(list, TransactionType.Expense, now).isEmpty())
        assertEquals(listOf("Employer"), frequentEntries(list, TransactionType.Income, now).map { it.payee })
        assertTrue(frequentEntries(list, TransactionType.Transfer, now).isEmpty(), "a transfer has no picks")
    }

    private companion object {
        const val CASH = 1L
        const val CARD = 2L
        const val FOOD = 10L
        const val FUEL = 11L
        const val HEALTH = 12L
        const val SHOPPING = 13L
        const val TRAVEL = 14L
        const val HOUSING = 15L
        const val SALARY = 20L
    }
}
