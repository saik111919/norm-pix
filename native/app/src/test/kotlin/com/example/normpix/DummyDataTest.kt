package com.example.normpix

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.finance.ui.FinanceStatus
import com.example.normpix.finance.ui.ReceiptSharer
import com.example.normpix.reminders.NoOpReminderScheduler
import com.example.normpix.reminders.Reminder
import com.example.normpix.reminders.ReminderCategory
import com.example.normpix.reminders.ReminderDao
import com.example.normpix.reminders.RemindersController
import com.example.normpix.reminders.RemindersDatabase
import com.example.normpix.reminders.RepeatRule
import com.example.normpix.settings.AppPreferences
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.CardVault
import com.example.normpix.wallet.CardsState
import com.example.normpix.wallet.StoredCard
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DummyDataTest {
    private val now = LocalDateTime.of(2026, 10, 4, 12, 0)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var realFinanceDb: FinanceDatabase
    private lateinit var realRemindersDb: RemindersDatabase
    private lateinit var realVault: RecordingVault
    private lateinit var real: AppContainer
    private val sharer = object : ReceiptSharer {
        override suspend fun shareImage(png: ByteArray, fileName: String): Boolean = true
    }

    /** The user's card vault; counts writes so a test can prove there were none. */
    private class RecordingVault(var cards: List<StoredCard>) : CardVault {
        var saves = 0

        override suspend fun loadCards(): List<StoredCard> = cards

        override suspend fun saveCards(cards: List<StoredCard>) {
            saves++
            this.cards = cards
        }
    }

    @Before
    fun setUp() = runBlocking {
        realFinanceDb = FinanceDatabase(context, name = null, clock = { now })
        realRemindersDb = RemindersDatabase(context, name = null)
        realVault = RecordingVault(
            listOf(StoredCard("mine", "Axis Bank", "THE USER", "4111111111111111", "01", "30", "999", CardKind.Debit, now)),
        )
        val prefs = context.getSharedPreferences("dummy_data_test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        real = AppContainer(
            preferences = AppPreferences(prefs),
            finance = FinanceController(FinanceRepository(realFinanceDb, clock = { now })),
            reminders = RemindersController(ReminderDao(realRemindersDb), NoOpReminderScheduler, clock = { now }),
            vault = realVault,
            receipts = sharer,
            sharedPictures = { null },
        )
        real.finance.load()
        real.reminders.load()
        real.cards.load()
    }

    @After
    fun tearDown() {
        realFinanceDb.close()
        realRemindersDb.close()
    }

    @Test
    fun sampleSetIsRealisticAndConsistent() = runBlocking {
        val dummy = DummyData.open(context, real) { now }
        try {
            val state = dummy.container.finance.state.value
            val repo = dummy.container.finance.repository
            assertEquals(FinanceStatus.Ready, state.status)
            assertEquals(listOf("Cash", "HDFC Savings", "ICICI Credit Card", "Paytm Wallet"), state.accounts.map { it.name })
            assertEquals("HDFC Savings", state.defaultAccount?.name)

            val all = repo.transactions(TransactionQuery(), limit = 1_000).items
            assertTrue(all.size > 100, "about three months of day-to-day spending, got ${all.size}")
            assertTrue(all.none { it.occurredAt.isAfter(now) }, "nothing is dated in the future")

            // Fixed payments come from the recurring engine: salary on the 1st of Aug, Sep and Oct,
            // rent on the 5th of Aug and Sep (October's is still to come).
            assertEquals(5, repo.recurringRules().size)
            val salary = all.filter { it.payee == "Acme Technologies Pvt Ltd" }
            assertEquals(listOf(8, 9, 10), salary.map { it.occurredAt.monthValue }.sorted())
            assertTrue(salary.all { it.recurringId != null && it.type == TransactionType.Income })
            assertEquals(2, all.count { it.payee == "Ramesh Iyer" })

            // The card is settled in September for exactly what was charged to it in August.
            val card = state.accounts.single { it.name == "ICICI Credit Card" }.id!!
            val augustCharges = all
                .filter { it.accountId == card && it.type == TransactionType.Expense && MonthKey.of(it.occurredAt) == MonthKey(2026, 8) }
                .sumOf { it.amount }
            val septemberBill = all.single {
                it.type == TransactionType.Transfer && it.toAccountId == card && MonthKey.of(it.occurredAt) == MonthKey(2026, 9)
            }
            assertEquals(augustCharges, septemberBill.amount)

            // Budgets apply to the current month, and the household stays within its overall limit.
            val overview = repo.budgetOverview(MonthKey.of(now))
            assertTrue(overview.headline != null, "an overall budget is set")
            val august = repo.budgetOverview(MonthKey(2026, 8))
            assertTrue(august.headline != null)

            val reminders = dummy.container.reminders.state.value
            assertEquals(4, reminders.reminders.size)
            assertEquals(listOf("Pay society maintenance"), reminders.overdue(now).map { it.title })

            val cards = (dummy.container.cards.state.value as CardsState.Ready).cards
            assertEquals(listOf("HDFC Bank", "ICICI Bank"), cards.map { it.bankName })
        } finally {
            dummy.close()
        }
    }

    @Test
    fun neverReadsOrChangesTheUsersData() = runBlocking {
        // The user's own data: a transaction, a reminder and a card.
        val cash = real.finance.state.value.accounts.first().id!!
        val groceries = real.finance.state.value.categoriesOf(CategoryType.Expense).first().id!!
        real.finance.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense,
                amount = 12_300,
                accountId = cash,
                categoryId = groceries,
                occurredAt = now.minusDays(1),
                payee = "Corner store",
                createdAt = now,
                updatedAt = now,
            ),
        )
        real.reminders.addReminder(
            Reminder(title = "My own reminder", category = ReminderCategory.Personal, dueAt = now.plusDays(1), repeatRule = RepeatRule.Once, createdAt = now, updatedAt = now),
        )
        val financeBefore = real.finance.repository.snapshot()
        val remindersBefore = ReminderDao(realRemindersDb).getAll()
        val cardsBefore = realVault.cards
        val vaultWritesBefore = realVault.saves

        val dummy = DummyData.open(context, real) { now }
        // The user's own entries never show up in the sample set...
        val sample = dummy.container.finance.repository.transactions(TransactionQuery(text = "Corner store"), limit = 10).items
        assertTrue(sample.isEmpty())
        assertTrue(dummy.container.reminders.state.value.reminders.none { it.title == "My own reminder" })
        // ...and using it like a user would - adding, erasing, deleting a card - stays inside it.
        val sampleCash = dummy.container.finance.state.value.accounts.first().id!!
        dummy.container.finance.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense,
                amount = 50_000,
                accountId = sampleCash,
                categoryId = dummy.container.finance.state.value.categoriesOf(CategoryType.Expense).first().id!!,
                occurredAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        dummy.container.finance.resetAll()
        dummy.container.cards.delete("dummy-hdfc-debit")
        dummy.close()

        assertEquals(financeBefore, real.finance.repository.snapshot())
        assertEquals(remindersBefore, ReminderDao(realRemindersDb).getAll())
        assertEquals(cardsBefore, realVault.cards)
        assertEquals(vaultWritesBefore, realVault.saves)

        // App-wide services are shared; sample reminders can never schedule real alarms.
        assertSame(real.preferences, dummy.container.preferences)
        assertSame(real.lock, dummy.container.lock)
        assertNull(dummy.container.alarms)
        // A receipt in the sample set goes out through the real share sheet; left out, the container fell back to a sharer that
        // shares nothing and "Share as image" reported a failure.
        assertSame(sharer, dummy.container.receipts)
        // Text shared while the sample set shows is the user's own: the sample screens must see it, to offer switching back.
        assertSame(real.sharedText, dummy.container.sharedText)
        assertSame(real.sharedPictures, dummy.container.sharedPictures)
    }
}
