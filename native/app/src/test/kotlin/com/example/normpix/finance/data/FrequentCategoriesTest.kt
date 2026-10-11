package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The picker's "Frequently used": the categories a kind of transaction was recorded in most lately. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrequentCategoriesTest {
    private val now = LocalDateTime.of(2026, 9, 15, 12, 0)
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun record(category: String, times: Int, daysAgo: Long = 1, type: TransactionType = TransactionType.Expense) {
        val id = repo.categories().first { it.name == category }.id
        repeat(times) {
            repo.saveTransaction(
                FinanceTransaction(
                    type = type,
                    amount = 1_000,
                    accountId = repo.accounts().first().id!!,
                    categoryId = id,
                    occurredAt = now.minusDays(daysAgo),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private suspend fun names(ids: List<Long>) = ids.map { id -> repo.categories().first { it.id == id }.name }

    @Test
    fun `a new ledger has no shortcuts`() = runDb {
        assertTrue(repo.frequentCategoryIds(TransactionType.Expense).isEmpty())
    }

    @Test
    fun `most used comes first, and ties go to the one used most recently`() = runDb {
        record("Groceries", 3)
        record("Transport", 1, daysAgo = 5)
        record("Shopping", 1, daysAgo = 2)
        assertEquals(listOf("Groceries", "Shopping", "Transport"), names(repo.frequentCategoryIds(TransactionType.Expense)))
    }

    @Test
    fun `only the asked kind counts, and only the last 180 days`() = runDb {
        record("Groceries", 2)
        record("Transport", 5, daysAgo = 200)
        record("Salary", 4, type = TransactionType.Income)
        assertEquals(listOf("Groceries"), names(repo.frequentCategoryIds(TransactionType.Expense)))
        assertEquals(listOf("Salary"), names(repo.frequentCategoryIds(TransactionType.Income)))
    }

    @Test
    fun `the list is capped, and archived categories are left out`() = runDb {
        for (name in listOf("Groceries", "Transport", "Shopping", "Health", "Education")) record(name, 1)
        assertEquals(3, repo.frequentCategoryIds(TransactionType.Expense, limit = 3).size)
        val groceries = repo.categories().first { it.name == "Groceries" }
        repo.saveCategory(groceries.copy(archived = true))
        assertTrue(groceries.id!! !in repo.frequentCategoryIds(TransactionType.Expense))
    }
}
