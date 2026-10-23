package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RepeatSchedule
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Saving a transaction as the first of a repeating payment: one step that writes the transaction and its rule together, links
 * them, never records an occurrence twice, and carries who it was spent on to every repeat.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepeatFromTransactionTest {
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

    private suspend fun draft(
        at: LocalDateTime,
        type: TransactionType = TransactionType.Expense,
        personId: Long? = null,
        amount: Long = 64_900,
    ) = FinanceTransaction(
        type = type,
        amount = amount,
        accountId = repo.accounts().first().id!!,
        categoryId = repo.categories().first { it.name == if (type == TransactionType.Income) "Salary" else "Bills & utilities" }.id,
        occurredAt = at,
        payee = "Netflix",
        note = "Family plan",
        createdAt = now,
        updatedAt = now,
        personId = personId,
    )

    private val monthly = RepeatSchedule(RecurrenceFrequency.Monthly)

    @Test
    fun `the transaction and its rule are written together and linked`() = runDb {
        val (saved, rule) = repo.saveTransactionAndRepeat(draft(now.plusDays(1)), monthly)

        assertNotNull(saved.id)
        assertEquals(rule.id, saved.recurringId)
        assertEquals(rule.id, repo.transactionById(saved.id)!!.recurringId)
        val stored = repo.recurringRules().single()
        assertEquals(64_900L, stored.amount)
        assertEquals("Netflix", stored.payee)
        assertEquals("Family plan", stored.note)
        assertEquals(RecurrenceFrequency.Monthly, stored.frequency)
        assertEquals(saved.occurredAt, stored.startDate, "the rule starts at the transaction's own date and time")
        assertEquals(saved.occurredAt.plusMonths(1), stored.nextDate, "and its next one is the occurrence after it")
        assertTrue(stored.active)
    }

    @Test
    fun `processing straight after records nothing twice`() = runDb {
        repo.saveTransactionAndRepeat(draft(now.plusDays(1)), monthly)
        assertEquals(0, repo.processRecurring(), "the first occurrence is the transaction just saved; the next is a month away")
        assertEquals(1, repo.transactions(TransactionQuery()).items.size)

        // A first date of today's morning: this one is the transaction; the following month is not yet due either.
        repo.saveTransactionAndRepeat(draft(now.minusHours(2)), monthly)
        assertEquals(0, repo.processRecurring())
        assertEquals(2, repo.transactions(TransactionQuery()).items.size)
    }

    @Test
    fun `a first date in the past records the repeats that have already fallen due, once each`() = runDb {
        val first = now.minusMonths(3).withDayOfMonth(10)
        val (saved, _) = repo.saveTransactionAndRepeat(draft(first), monthly)
        assertEquals(3, repo.processRecurring(), "the 10th of the next three months, up to this month's")
        val all = repo.transactions(TransactionQuery()).items.sortedBy { it.occurredAt }
        assertEquals(4, all.size)
        assertEquals(first, all.first().occurredAt)
        assertEquals(saved.id, all.first().id, "the transaction the user saved is the first, not a copy of it")
        assertEquals(all.map { it.occurredAt }.distinct().size, all.size, "no date recorded twice")
        assertTrue(all.all { it.recurringId == saved.recurringId })
        assertEquals(0, repo.processRecurring(), "and a second run records nothing more")
    }

    @Test
    fun `who it was spent on is carried to every repeat`() = runDb {
        val mom = repo.savePerson(Person(name = "Mom", createdAt = now, updatedAt = now))
        val (saved, rule) = repo.saveTransactionAndRepeat(draft(now.minusMonths(2).withDayOfMonth(5), personId = mom.id), monthly)
        assertEquals(mom.id, saved.personId)
        assertEquals(mom.id, repo.recurringRules().single { it.id == rule.id }.personId)
        repo.processRecurring()
        val all = repo.transactions(TransactionQuery()).items
        assertEquals(3, all.size)
        assertTrue(all.all { it.personId == mom.id }, "every repeat is Mom's")
        assertEquals(3 * 64_900L, repo.peopleSpending(null).of(mom.id!!)!!.amount)
    }

    @Test
    fun `an existing transaction can start repeating, keeping its identity`() = runDb {
        val plain = repo.saveTransaction(draft(now.minusDays(3)))
        assertNull(plain.recurringId)
        val (saved, rule) = repo.saveTransactionAndRepeat(plain.copy(amount = 70_000), RepeatSchedule(RecurrenceFrequency.Weekly, interval = 2))
        assertEquals(plain.id, saved.id)
        assertEquals(rule.id, repo.transactionById(plain.id!!)!!.recurringId)
        assertEquals(70_000L, repo.transactionById(plain.id)!!.amount)
        assertEquals(70_000L, repo.recurringRules().single().amount, "the rule repeats what was saved, edits included")
        assertEquals(plain.occurredAt.plusDays(14), repo.recurringRules().single().nextDate)
    }

    @Test
    fun `an end date stops it, and a transfer or income can repeat as well`() = runDb {
        val first = now.minusDays(10)
        val schedule = RepeatSchedule(RecurrenceFrequency.Daily, endDate = first.plusDays(2))
        repo.saveTransactionAndRepeat(draft(first), schedule)
        assertEquals(2, repo.processRecurring(), "the two days after the first, then it is over")
        assertEquals(3, repo.transactions(TransactionQuery()).items.size)

        val (income, rule) = repo.saveTransactionAndRepeat(draft(now.plusDays(2), TransactionType.Income, amount = 5_000_000), monthly)
        assertEquals(TransactionType.Income, repo.recurringRules().first { it.id == rule.id }.type)
        assertEquals(rule.id, income.recurringId)
    }

    @Test
    fun `it is all or nothing - a bad schedule or a bad transaction writes neither`() = runDb {
        val first = now.plusDays(1)
        assertFailsWith<FinanceValidationException> { repo.saveTransactionAndRepeat(draft(first), RepeatSchedule(RecurrenceFrequency.Monthly, interval = 0)) }
        assertFailsWith<FinanceValidationException> {
            repo.saveTransactionAndRepeat(draft(first), RepeatSchedule(RecurrenceFrequency.Monthly, endDate = first.minusDays(5)))
        }
        assertFailsWith<FinanceValidationException> { repo.saveTransactionAndRepeat(draft(first, amount = 0), monthly) }
        assertFailsWith<FinanceValidationException> { repo.saveTransactionAndRepeat(draft(first, personId = 999), monthly) }
        assertTrue(repo.recurringRules().isEmpty(), "no rule left behind")
        assertTrue(repo.transactions(TransactionQuery()).items.isEmpty(), "and no transaction")
    }

    @Test
    fun `one already in a repeating payment is refused rather than started again`() = runDb {
        val (saved, _) = repo.saveTransactionAndRepeat(draft(now.plusDays(1)), monthly)
        assertFailsWith<FinanceValidationException> { repo.saveTransactionAndRepeat(saved, monthly) }
        assertEquals(1, repo.recurringRules().size)
    }

    @Test
    fun `a rule can be for a person only on an expense, and an archived person takes no new rule`() = runDb {
        val mom = repo.savePerson(Person(name = "Mom", createdAt = now, updatedAt = now))
        val rule = repo.saveRecurringRule(
            com.example.normpix.finance.domain.RecurringRule(
                type = TransactionType.Expense,
                amount = 1_000,
                accountId = repo.accounts().first().id!!,
                categoryId = repo.categories().first { it.name == "Groceries" }.id,
                frequency = RecurrenceFrequency.Weekly,
                startDate = now.plusDays(1),
                nextDate = now.plusDays(1),
                createdAt = now,
                updatedAt = now,
                personId = mom.id,
            ),
        )
        assertEquals(mom.id, repo.recurringRules().single().personId)

        assertFailsWith<FinanceValidationException>("income is not spent on anyone") {
            repo.saveRecurringRule(
                rule.copy(
                    id = null,
                    type = TransactionType.Income,
                    categoryId = repo.categories().first { it.name == "Salary" }.id,
                ),
            )
        }

        repo.setPersonArchived(mom.id!!, true)
        repo.saveRecurringRule(rule.copy(amount = 2_000)) // what is already hers stays
        assertFailsWith<FinanceValidationException>("a new rule cannot take her") { repo.saveRecurringRule(rule.copy(id = null)) }
    }

    @Test
    fun `deleting the person lets their rule go on, for no one`() = runDb {
        val mom = repo.savePerson(Person(name = "Mom", createdAt = now, updatedAt = now))
        repo.saveTransactionAndRepeat(draft(now.plusDays(1), personId = mom.id), monthly)
        repo.deletePerson(mom.id!!)
        assertNull(repo.recurringRules().single().personId)
        assertEquals(1, repo.transactions(TransactionQuery()).items.size)
    }

    @Test
    fun `the person on a rule survives a backup and restore`() = runDb {
        val mom = repo.savePerson(Person(name = "Mom", createdAt = now, updatedAt = now))
        repo.saveTransactionAndRepeat(draft(now.plusDays(1), personId = mom.id), monthly)
        val json = FinanceBackupCodec.encode(repo.snapshot(), exportedAt = now)
        val read = FinanceBackupCodec.decode(json)
        assertTrue(read.isValid, read.errors.toString())
        repo.resetAll()
        repo.restore(read.snapshot!!)
        assertEquals(mom.id, repo.recurringRules().single().personId)
    }
}
