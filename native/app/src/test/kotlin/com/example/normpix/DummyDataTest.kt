package com.example.normpix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.data.TransactionPictures
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.data.forecastFacts
import com.example.normpix.finance.data.repeatSuggestions
import com.example.normpix.finance.domain.BudgetHealth
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.ForecastSource
import com.example.normpix.finance.domain.loanOutlook
import com.example.normpix.finance.domain.nextEmiIndex
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.buildForecast
import com.example.normpix.finance.domain.goalSaved
import com.example.normpix.finance.domain.repeatingCosts
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.finance.ui.FinanceStatus
import com.example.normpix.finance.ui.PictureEdit
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
import com.example.normpix.wallet.expiry.alertStart
import com.example.normpix.wallet.expiry.lastValidDay
import com.example.normpix.wallet.expiry.parseExpiry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
    fun theSampleSetHasRepeatingPaymentsToCost_somethingThatLooksLikeOne_andAForecastToDraw() = runBlocking {
        val dummy = DummyData.open(context, real) { now }
        try {
            val repo = dummy.container.finance.repository
            val today = now.toLocalDate()
            // What repeats costs something a month and a year.
            val costs = repeatingCosts(repo.recurringRules())
            assertTrue(costs.active.isNotEmpty() && costs.perMonth > 0 && costs.perYear > 0, "$costs")
            // Payments made by hand on a rhythm (the electricity, a mobile recharge) look like repeating payments; ones that already
            // repeat (Netflix, the rent) are not offered again.
            val suggestions = repo.repeatSuggestions(today)
            assertTrue(suggestions.isNotEmpty(), "nothing in the sample set looks like a repeating payment")
            assertTrue(suggestions.all { it.frequency == RecurrenceFrequency.Monthly }, "$suggestions")
            assertTrue(suggestions.none { it.key == "netflix" || it.key == "ramesh iyer" }, "$suggestions")
            // The forecast has the salary and the rent to come, and the cards' bills.
            val forecast = buildForecast(repo.forecastFacts(today), today.plusDays(60), everyday = true)
            assertTrue(forecast.startBalance > 0)
            assertTrue(forecast.items.any { it.source == ForecastSource.Repeating && it.effect > 0 }, "the salary")
            assertTrue(forecast.items.any { it.source == ForecastSource.Repeating && it.effect < 0 }, "the rent")
            assertTrue(forecast.items.any { it.source == ForecastSource.CardBill }, "a credit card's bill")
            assertTrue(forecast.everydayPerDay > 0)
        } finally {
            dummy.close()
        }
    }

    @Test
    fun theSampleSetHasTwoSavingsGoals_oneCountingAnAccountAndOneOnItsOwn_inMemoryOnly() = runBlocking {
        val dummy = DummyData.open(context, real) { now }
        try {
            val state = dummy.container.finance.state.value
            val goals = state.goals
            assertEquals(2, goals.size)
            val fund = goals.first { it.name == "Emergency fund" }
            val trip = goals.first { it.name == "Trip to Goa" }
            // One counts an account's balance, the other keeps its own amount; both have a date still ahead.
            assertEquals("SBI Savings", state.account(fund.accountId)?.name)
            assertNull(trip.accountId)
            assertTrue(trip.saved > 0 && trip.saved < trip.target)
            assertTrue(goals.all { it.targetDate!!.isAfter(now.toLocalDate()) })
            assertTrue(goalSaved(fund, state.balances) in 1 until fund.target, "the account's balance is some of it, not all")
            // The user's own ledger has none: the sample set is in memory only.
            assertTrue(real.finance.state.value.goals.isEmpty())
            assertTrue(real.finance.repository.goals().isEmpty())
        } finally {
            dummy.close()
        }
    }

    @Test
    fun theSampleSetHasOneSplitPayment_asARowForEachPart_inMemoryOnly() = runBlocking {
        val dummy = DummyData.open(context, real) { now }
        try {
            val repo = dummy.container.finance.repository
            val rows = repo.transactions(TransactionQuery(), limit = 10_000).items.filter { it.splitGroup != null }
            // One supermarket bill, shared between two categories: a row each, one account, one moment, one payee, never in the future.
            assertEquals(2, rows.size)
            assertEquals(1, rows.map { it.splitGroup }.toSet().size)
            assertEquals(setOf("DMart"), rows.map { it.payee }.toSet())
            assertEquals(setOf(TransactionType.Expense), rows.map { it.type }.toSet())
            assertEquals(1, rows.map { it.accountId }.toSet().size)
            assertEquals(1, rows.map { it.occurredAt }.toSet().size)
            assertTrue(rows.all { !it.occurredAt.isAfter(now) })
            assertEquals(2, rows.map { it.categoryId }.toSet().size, "two categories")
            assertEquals(202_000L, rows.sumOf { it.amount })
            assertTrue(rows.all { !it.note.isNullOrBlank() })
            // It holds together, as the check reads it, and the user's own ledger has none.
            assertTrue(com.example.normpix.finance.domain.brokenSplitGroups(rows).isEmpty())
            assertTrue(real.finance.repository.transactions(TransactionQuery(), limit = 10).items.none { it.splitGroup != null })
        } finally {
            dummy.close()
        }
    }

    @Test
    fun sampleSetIsRealisticAndConsistent() = runBlocking {
        val dummy = DummyData.open(context, real) { now }
        try {
            val state = dummy.container.finance.state.value
            val repo = dummy.container.finance.repository
            assertEquals(FinanceStatus.Ready, state.status)
            assertEquals(
                listOf("Cash", "HDFC Savings", "ICICI Credit Card", "Paytm Wallet", "SBI Savings", "Axis Credit Card", "HDFC Home Loan"),
                state.accounts.map { it.name },
            )
            assertEquals("HDFC Savings", state.defaultAccount?.name)

            // A home loan with a few EMIs recorded: each one a transfer of the principal onto it and an expense of the interest in
            // Loans & EMI, so what is owed is what was borrowed less the principal parts only.
            val homeLoan = state.accounts.single { it.name == "HDFC Home Loan" }
            val terms = assertNotNull(homeLoan.loan)
            assertEquals(AccountKind.Loan, homeLoan.kind)
            assertEquals(-terms.principal, homeLoan.openingBalance, "owed what was borrowed when it began")
            val onLoan = repo.transactions(TransactionQuery(accountIds = setOf(homeLoan.id!!)), limit = 100).items
            assertTrue(onLoan.size in 3..5, "a few EMIs so far, got ${onLoan.size}")
            assertTrue(onLoan.all { it.type == TransactionType.Transfer && it.note!!.startsWith("EMI ") && it.note!!.endsWith(" - principal") })
            val interests = repo.transactions(TransactionQuery(), limit = 1_000).items.filter { it.type == TransactionType.Expense && it.note?.endsWith(" - interest") == true }
            assertEquals(onLoan.size, interests.size, "one interest expense with each principal transfer")
            assertTrue(interests.all { it.payee == "HDFC Home Loan" && state.category(it.categoryId)?.name == "Loans & EMI" })
            assertEquals(-terms.principal + onLoan.sumOf { it.amount }, state.balanceOf(homeLoan.id!!))
            assertNotNull(loanOutlook(terms, -state.balanceOf(homeLoan.id!!), nextEmiIndex(terms, onLoan.maxOf { it.occurredAt.toLocalDate() }, now.toLocalDate())), "the loan can be paid off")

            val all = repo.transactions(TransactionQuery(), limit = 1_000).items
            assertTrue(all.size > 200, "about six months of day-to-day spending, got ${all.size}")
            assertTrue(all.none { it.occurredAt.isAfter(now) }, "nothing is dated in the future")

            // Fixed payments come from the recurring engine: salary on the 1st of May, Jun, Jul, Aug, Sep and Oct,
            // rent on the 5th of May, Jun, Jul, Aug and Sep (October's is still to come).
            // And a ninth: the home loan's EMI, recorded each month from the next one on (none of it recorded yet).
            assertEquals(9, repo.recurringRules().size)
            val emiRule = assertNotNull(repo.emiRuleOf(homeLoan.id!!))
            assertEquals(terms.emi, emiRule.amount)
            assertEquals(state.accounts.single { it.name == "HDFC Savings" }.id, emiRule.accountId)
            assertTrue(emiRule.nextDate.isAfter(now), "the next EMI is still to come")
            val salary = all.filter { it.payee == "Acme Technologies Pvt Ltd" }
            assertEquals(listOf(5, 6, 7, 8, 9, 10), salary.map { it.occurredAt.monthValue }.sorted())
            assertTrue(salary.all { it.recurringId != null && it.type == TransactionType.Income })
            assertEquals(5, all.count { it.payee == "Ramesh Iyer" })

            // The ICICI card is settled in September for exactly what was charged to it in August.
            val card = state.accounts.single { it.name == "ICICI Credit Card" }.id!!
            val augustCharges = all
                .filter { it.accountId == card && it.type == TransactionType.Expense && MonthKey.of(it.occurredAt) == MonthKey(2026, 8) }
                .sumOf { it.amount }
            val septemberBill = all.single {
                it.type == TransactionType.Transfer && it.toAccountId == card && MonthKey.of(it.occurredAt) == MonthKey(2026, 9)
            }
            assertEquals(augustCharges, septemberBill.amount)

            // The Axis card is also settled in September for exactly what was charged to it in August.
            val axisCard = state.accounts.single { it.name == "Axis Credit Card" }.id!!
            val axisAugustCharges = all
                .filter { it.accountId == axisCard && it.type == TransactionType.Expense && MonthKey.of(it.occurredAt) == MonthKey(2026, 8) }
                .sumOf { it.amount }
            val axisSeptemberBill = all.single {
                it.type == TransactionType.Transfer && it.toAccountId == axisCard && MonthKey.of(it.occurredAt) == MonthKey(2026, 9)
            }
            assertEquals(axisAugustCharges, axisSeptemberBill.amount)

            // Budgets apply to the current month, and demonstrate each health state:
            //  - overall stays on track (Healthy);
            //  - at least one category well under (Groceries, Food & dining, Transport);
            //  - at least one nearly used (Fitness, at or above 80%);
            //  - at least one over budget (Shopping, > 100%).
            val overview = repo.budgetOverview(MonthKey.of(now))
            assertTrue(overview.headline != null, "an overall budget is set")
            assertEquals(BudgetHealth.Healthy, overview.overall?.health, "overall monthly budget stays on track")
            assertTrue(overview.categories.values.any { it.health == BudgetHealth.Healthy }, "at least one category well under")
            assertTrue(overview.categories.values.any { it.health == BudgetHealth.Approaching }, "at least one category nearly used")
            assertTrue(overview.categories.values.any { it.health == BudgetHealth.Exceeded }, "at least one category over budget")
            val august = repo.budgetOverview(MonthKey(2026, 8))
            assertTrue(august.headline != null)

            // Reminders: some overdue, some in the next 7 days, some later.
            val reminders = dummy.container.reminders.state.value
            assertEquals(7, reminders.reminders.size)
            assertEquals(
                listOf("Pay society maintenance", "Submit medical insurance claim").sorted(),
                reminders.overdue(now).map { it.title }.sorted(),
            )
            val upcoming = reminders.upcoming(now)
            assertTrue(upcoming.any { !it.dueAt.isAfter(now.plusDays(7)) }, "some reminders due in next 7 days")
            assertTrue(upcoming.any { it.dueAt.isAfter(now.plusDays(7)) }, "some reminders due later")

            // Saved cards from different issuers, with one expiring next month to trigger the expiry banner.
            val cards = (dummy.container.cards.state.value as CardsState.Ready).cards
            assertEquals(listOf("HDFC Bank", "ICICI Bank", "State Bank of India", "Axis Bank"), cards.map { it.bankName })
            val expiringCard = cards.single { it.id == "dummy-axis-debit" }
            val ym = checkNotNull(parseExpiry(expiringCard.expiryMonth, expiringCard.expiryYear))
            assertTrue(now.toLocalDate() >= ym.alertStart && now.toLocalDate() <= ym.lastValidDay, "expiring card within alert window")
        } finally {
            dummy.close()
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun picturesCanBeAttachedInTheSampleSetKeptApartFromTheUsersAndGoWithIt(): Unit = runBlocking {
        // A picture the user kept with one of their own transactions.
        val mine = File(context.filesDir, "${TransactionPictures.KEPT_DIR}/pic-mine.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val sampleFolder = File(context.cacheDir, DummyData.SAMPLE_PICTURES_DIR)
        val dummy = DummyData.open(context, real) { now }
        try {
            // Left out, the form had no picture field while the sample set showed.
            val pictures = assertNotNull(dummy.container.finance.pictures, "pictures can be attached in the sample set")
            val source = Uri.parse("content://com.example.gallery/receipt")
            val bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            shadowOf(context.contentResolver).registerInputStreamSupplier(source) { ByteArrayInputStream(png) }
            val draft = assertNotNull(pictures.stage(source))
            val state = dummy.container.finance.state.value
            val saved = dummy.container.finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = 25_000,
                    accountId = state.accounts.first().id!!,
                    categoryId = state.categoriesOf(CategoryType.Expense).first().id!!,
                    occurredAt = now,
                    createdAt = now,
                    updatedAt = now,
                ),
                PictureEdit.Attach(draft),
            )
            val kept = assertNotNull(pictures.file(saved.picture))
            assertTrue(kept.path.startsWith(sampleFolder.path), "kept in the sample set's own folder, not the user's: ${kept.path}")
            // Its sweep (on every load) and erasing it never reach the user's pictures.
            dummy.container.finance.load()
            dummy.container.finance.resetAll()
            assertTrue(mine.isFile, "the user's picture is left alone")
        } finally {
            dummy.close()
        }
        val deadline = System.currentTimeMillis() + 5_000
        while (!sampleFolder.listFiles().isNullOrEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(sampleFolder.listFiles().isNullOrEmpty(), "the set's pictures went with it")
        assertTrue(mine.isFile)
        mine.delete()
    }

    @Test
    fun picturesOfASetThatWasNeverClosedGoWhenTheNextOneOpens(): Unit = runBlocking {
        val leftOver = File(context.cacheDir, "${DummyData.SAMPLE_PICTURES_DIR}/left-over/kept/pic-old.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val dummy = DummyData.open(context, real) { now }
        try {
            assertFalse(leftOver.exists())
            assertFalse(File(context.cacheDir, "${DummyData.SAMPLE_PICTURES_DIR}/left-over").exists())
        } finally {
            dummy.close()
        }
    }

    @Test
    fun picturesLeftOverFromEarlierAreDeletedWhenClosed(): Unit = runBlocking {
        val dummy = DummyData.open(context, real) { now }
        val orphaned = File(context.cacheDir, "${DummyData.SAMPLE_PICTURES_DIR}/orphaned-run/pic-old.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(9))
        }
        try {
            assertTrue(orphaned.exists())
        } finally {
            dummy.close()
        }
        val deadline = System.currentTimeMillis() + 5_000
        while (orphaned.exists() && System.currentTimeMillis() < deadline) Thread.sleep(25)
        assertFalse(orphaned.exists(), "orphaned sample folders are cleaned up on close")
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
