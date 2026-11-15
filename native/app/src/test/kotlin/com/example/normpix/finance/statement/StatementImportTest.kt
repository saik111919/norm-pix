package com.example.normpix.finance.statement

import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a statement becomes in a real (in-memory) ledger: the category guess, the entries that are in NormPix already - by reference, by
 * amount and day, one entry for one row - and the import itself: all of the ticked rows or none, with the reference in the note.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatementImportTest {
    private val env = TestEnvironment(prefsName = "statement_import_test")
    private val finance = env.container.finance
    private val repository = finance.repository
    private val now = LocalDateTime.of(2026, 11, 15, 10, 0)
    private val today = LocalDate.of(2026, 11, 15)
    private val savings: Account = runBlocking {
        finance.saveAccount(Account(name = "HDFC Savings", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
    }

    @After
    fun tearDown() = env.close()

    // The family of that name: a sub-category may share it ("Other" under another family).
    private fun category(name: String) = finance.state.value.categories.first { it.name == name && it.parentId == null }

    private fun rowsOf(text: String): StatementReading {
        val sheet = StatementFixtures.sheet(text)
        val mapping = StatementLayout.guess(sheet)
        val format = assertNotNull(StatementDates.detect(sheet.values(mapping.date), today).format)
        return StatementReader.read(sheet, mapping, format, today)
    }

    private fun prepare(reading: StatementReading) =
        runBlocking { StatementImport.prepare(reading.rows, finance.state.value.categories, repository) }

    private fun entry(
        type: TransactionType,
        amount: Long,
        at: LocalDateTime,
        payee: String?,
        note: String? = null,
        account: Account = savings,
        categoryName: String = if (type == TransactionType.Income) "Other income" else "Other",
    ) = runBlocking {
        finance.saveTransaction(
            FinanceTransaction(
                type = type,
                amount = amount,
                accountId = account.id!!,
                categoryId = category(categoryName).id,
                occurredAt = at,
                payee = payee,
                note = note,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun import(drafts: List<StatementDraft>) = runBlocking {
        finance.saveTransactionsUndoably(StatementImport.transactions(drafts, savings.id!!, now))
    }

    private fun savedHere() = runBlocking { repository.snapshot().transactions }.filter { it.accountId == savings.id }

    // ---------------------------------------------------------------- categories

    @Test
    fun `a payee the user filed before keeps its category, a known merchant gets its own, the rest go under Other`() {
        val food = category("Food & dining")
        entry(TransactionType.Expense, 10_000, now.minusDays(30), "Sample Cafe", categoryName = "Food & dining")
        val reading = rowsOf(
            """
            Date,Narration,Withdrawal Amt.,Deposit Amt.
            13/10/2026,UPI-SAMPLE CAFE-samplecafe@bank-BANK0000001-428312345678-UPI,100.00,
            14/10/2026,UPI-SWIGGY-swiggy@bank-BANK0000001-428312345679-UPI,250.00,
            15/10/2026,POS 416021XXXXXX1234 EXAMPLE STORE,499.00,
            16/10/2026,NEFT CR-BANK0000001-EXAMPLE EMPLOYER LTD-N123456789012,,85000.00
            """.trimIndent(),
        )
        val drafts = prepare(reading)
        assertEquals(food.id, drafts[0].categoryId, "the user's own last filing of the payee")
        val swiggy = finance.state.value.categories.first { it.name == "Swiggy" }
        assertEquals(swiggy.id, drafts[1].categoryId, "the app's table of merchants")
        assertEquals(category("Other").id, drafts[2].categoryId)
        assertEquals(category("Other income").id, drafts[3].categoryId)
        assertTrue(drafts.all { it.importable })
    }

    @Test
    fun `with no Other category the first of its kind is used, and with none the row cannot be imported`() {
        val state = finance.state.value
        runBlocking {
            state.categories.filter { it.type == com.example.normpix.finance.domain.CategoryType.Income }.forEach { finance.setCategoryArchived(it.id!!, true) }
            finance.setCategoryArchived(category("Other").id!!, true)
        }
        val reading = rowsOf("Date,Narration,Debit,Credit\n13/10/2026,Mystery,100.00,\n14/10/2026,Windfall,,50.00\n")
        val drafts = prepare(reading)
        val firstExpense = finance.state.value.categories.first { it.type == com.example.normpix.finance.domain.CategoryType.Expense && !it.archived && it.parentId == null }
        assertEquals(firstExpense.id, drafts[0].categoryId)
        assertNull(drafts[1].categoryId)
        assertFalse(drafts[1].importable)
        assertFalse(drafts[1].tickedByDefault)
    }

    // ----------------------------------------------------------------- duplicates

    @Test
    fun `a statement imported twice finds every row already there the second time`() {
        val reading = rowsOf(StatementFixtures.withWithdrawals)
        val first = prepare(reading)
        assertTrue(first.all { it.duplicate == null && it.tickedByDefault })
        import(first)
        assertEquals(3, savedHere().size)

        val second = prepare(reading)
        assertTrue(second.all { it.duplicate != null }, "every row is in NormPix now")
        assertTrue(second.none { it.tickedByDefault })
        // Each row is its own entry, never two rows on one.
        assertEquals(3, second.map { it.duplicate!!.id }.toSet().size)
    }

    @Test
    fun `a row with the reference of a shared receipt's entry is that entry, whatever else differs`() {
        // A payment app's receipt: a different payee, another day, another amount - the same reference.
        val receipt = entry(TransactionType.Expense, 99_999, now.minusDays(20), "Somebody else", note = "Ref 428312345678")
        val drafts = prepare(rowsOf(StatementFixtures.withWithdrawals))
        assertEquals(receipt.id, drafts[0].duplicate?.id)
        assertNull(drafts[1].duplicate)
        assertNull(drafts[2].duplicate)
    }

    @Test
    fun `a row matching an entry by amount and day is found even when the payee is spelt another way, and a different reference is another payment`() {
        // 9 Oct: Rs 499 spent at "Example Store" - typed by hand as "Store near home".
        val typed = entry(TransactionType.Expense, 49_900, LocalDateTime.of(2026, 10, 9, 18, 40), "Store near home")
        // 1 Oct: Rs 1,250 with another payment's own reference: not the café's.
        entry(TransactionType.Expense, 125_000, LocalDateTime.of(2026, 10, 1, 9, 0), "Sample Cafe", note = "Ref 999999999999")
        val drafts = prepare(rowsOf(StatementFixtures.withWithdrawals))
        assertEquals(typed.id, drafts[2].duplicate?.id)
        assertNull(drafts[0].duplicate, "the same amount and day under a different reference is a different payment")
        assertFalse(drafts[2].tickedByDefault)
    }

    @Test
    fun `an entry accounts for one row, so two equal rows are not both put down to it`() {
        val one = entry(TransactionType.Expense, 10_000, LocalDateTime.of(2026, 10, 13, 12, 0), "Tea stall")
        val reading = rowsOf("Date,Description,Debit,Credit\n13/10/2026,TEA STALL,100.00,\n13/10/2026,TEA STALL,100.00,\n")
        val drafts = prepare(reading)
        assertEquals(one.id, drafts[0].duplicate?.id)
        assertNull(drafts[1].duplicate)
        assertEquals(listOf(false, true), drafts.map { it.tickedByDefault })
    }

    @Test
    fun `income is not matched with an expense of the same amount, and a row with a time is matched within three hours`() {
        entry(TransactionType.Expense, 50_000, LocalDateTime.of(2026, 10, 13, 12, 0), null)
        val day = prepare(rowsOf("Date,Description,Debit,Credit\n13/10/2026,REFUND,,500.00\n"))
        assertNull(day.single().duplicate)

        val timed = prepare(rowsOf("Date,Description,Debit,Credit\n13/10/2026 14:00,SHOP,500.00,\n13/10/2026 20:00,SHOP,500.00,\n"))
        assertNotNull(timed[0].duplicate, "two hours from the entry")
        assertNull(timed[1].duplicate, "eight hours from it")
    }

    // --------------------------------------------------------------------- import

    @Test
    fun `the import saves the ticked rows as the account's transactions, with the reference and the narration in the note`() {
        val drafts = prepare(rowsOf(StatementFixtures.withWithdrawals))
        val undo = import(drafts.take(2))
        assertNotNull(undo)
        val saved = savedHere().sortedBy { it.occurredAt }
        assertEquals(2, saved.size)

        val cafe = saved[0]
        assertEquals(TransactionType.Expense, cafe.type)
        assertEquals(125_000L, cafe.amount)
        assertEquals("Sample Cafe", cafe.payee)
        assertEquals(LocalDateTime.of(2026, 10, 1, 12, 0), cafe.occurredAt, "a statement that gives only the day is filed at noon")
        assertEquals("Ref 428312345678 · UPI-SAMPLE CAFE-samplecafe@bank-BANK0000001-428312345678-UPI", cafe.note)
        assertEquals(savings.id, cafe.accountId)

        val salary = saved[1]
        assertEquals(TransactionType.Income, salary.type)
        assertEquals(8_500_050L, salary.amount)
        assertTrue(salary.note!!.startsWith("Ref N123456789012 · NEFT CR-"))
    }

    @Test
    fun `a row with a time keeps it`() {
        val drafts = prepare(rowsOf("Date,Description,Debit,Credit\n13/10/2026 14:35,SHOP,500.00,\n"))
        import(drafts)
        assertEquals(LocalDateTime.of(2026, 10, 13, 14, 35), savedHere().single().occurredAt)
        assertEquals(LocalTime.NOON, StatementImport.DAY_ONLY_TIME)
    }

    @Test
    fun `a failure on one row saves none of them`() {
        val drafts = prepare(rowsOf(StatementFixtures.withWithdrawals))
        val before = runBlocking { repository.snapshot().transactions.size }
        // The third row is filed under an income category: an expense can't be, so its save fails after the others went in.
        val bad = drafts[2].let {
            StatementDraft(it.row, it.payee, it.note, it.reference, category("Other income").id, null)
        }
        val transactions = StatementImport.transactions(listOf(drafts[0], drafts[1], bad), savings.id!!, now)
        assertFailsWith<FinanceValidationException> { runBlocking { finance.saveTransactionsUndoably(transactions) } }
        assertEquals(before, runBlocking { repository.snapshot().transactions.size }, "not even the first two are there")
    }

    @Test
    fun `an account that has gone is refused and nothing is saved`() {
        val drafts = prepare(rowsOf(StatementFixtures.withWithdrawals))
        val before = runBlocking { repository.snapshot().transactions.size }
        val transactions = StatementImport.transactions(drafts, 987_654L, now)
        assertFailsWith<FinanceValidationException> { runBlocking { finance.saveTransactionsUndoably(transactions) } }
        assertEquals(before, runBlocking { repository.snapshot().transactions.size })
    }

    @Test
    fun `an import can be undone`() {
        val before = runBlocking { repository.snapshot().transactions.size }
        val undo = import(prepare(rowsOf(StatementFixtures.withWithdrawals)))
        assertEquals(before + 3, runBlocking { repository.snapshot().transactions.size })
        assertTrue(runBlocking { finance.undo(undo) })
        assertEquals(before, runBlocking { repository.snapshot().transactions.size })
    }
}
