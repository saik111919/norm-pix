package com.example.normpix

import android.content.Context
import android.util.Log
import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.data.TransactionPictures
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.reminders.NoOpReminderScheduler
import com.example.normpix.reminders.Reminder
import com.example.normpix.reminders.ReminderCategory
import com.example.normpix.reminders.ReminderDao
import com.example.normpix.reminders.RemindersController
import com.example.normpix.reminders.RemindersDatabase
import com.example.normpix.reminders.RepeatKind
import com.example.normpix.reminders.RepeatRule
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.CardVault
import com.example.normpix.wallet.StoredCard
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dummy data: a realistic sample set - accounts, about three months of
 * transactions, budgets, recurring payments, reminders and cards - shown
 * instead of the user's own data while Settings > Dummy data is on.
 *
 * It lives only in private in-memory stores (SQLite databases with no file
 * behind them, and an in-memory card vault), so nothing shown or changed in
 * this mode can reach the user's data, and switching it off simply drops the
 * set. Pictures attached to sample transactions are files, so they go to a
 * folder of the set's own in the app's cache, deleted with it. Services that
 * aren't data (preferences, app lock, NFC, camera) are shared with the real
 * container; sample reminders never schedule notifications.
 */
class DummyData private constructor(
    context: Context,
    real: AppContainer,
    clock: () -> LocalDateTime = { LocalDateTime.now() },
) {
    private val financeDatabase = FinanceDatabase(context, name = null, clock = clock)
    private val remindersDatabase = RemindersDatabase(context, name = null)
    private val remindersScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The set's databases work where the user's do - in the app, their dedicated threads. A test that runs the user's
    // databases in place on its own thread so their results can't be lost crossing threads (see TestEnvironment) then gets
    // the same for the sample set, instead of the dedicated threads behind its back.
    private val financeDispatcher = real.finance.repository.dispatcher
    private val remindersDispatcher = real.reminders.databaseDispatcher

    /** This set's pictures, apart from the user's: its own folder, so its sweep never sees theirs (or another set's). */
    private val picturesDir = File(File(context.cacheDir, SAMPLE_PICTURES_DIR), UUID.randomUUID().toString())
        .also { synchronized(openSets) { openSets += it.name } }

    val container = AppContainer(
        preferences = real.preferences,
        finance = FinanceController(
            FinanceRepository(financeDatabase, clock = clock, dispatcher = financeDispatcher),
            TransactionPictures(context, kept = File(picturesDir, "kept"), drafts = File(picturesDir, "drafts")),
        ),
        reminders = RemindersController(
            ReminderDao(remindersDatabase, dispatcher = remindersDispatcher),
            NoOpReminderScheduler,
            clock = clock,
            scope = remindersScope,
        ),
        vault = InMemoryCardVault(),
        lock = real.lock,
        host = real.host,
        nfc = real.nfc,
        registeredTags = real.registeredTags,
        scanner = real.scanner,
        alarms = null,
        clipboard = real.clipboard,
        // Sharing a receipt takes nothing from the sample set but its picture, so it goes through the real share sheet too.
        receipts = real.receipts,
        // A share that arrives while the sample set is showing is the user's own: the UI offers to switch back for it.
        sharedText = real.sharedText,
        sharedPictures = real.sharedPictures,
        // Updates are the app's, not the data's: the same updater whichever set is showing.
        updates = real.updates,
    )

    /** Drops the sample set and frees its stores, its pictures too. */
    fun close() {
        container.reminders.close()
        remindersScope.cancel()
        financeDatabase.close()
        remindersDatabase.close()
        synchronized(openSets) { openSets -= picturesDir.name }
        CoroutineScope(Dispatchers.IO).launch { picturesDir.deleteRecursively() }
    }

    companion object {
        suspend fun open(context: Context, real: AppContainer, now: LocalDateTime): DummyData =
            open(context, real) { now }

        private const val TAG = "NormPixDummyData"

        /** Under the app's cache: the folders of the sample sets' pictures, one per set (see `res/xml/scan_file_paths.xml`). */
        internal const val SAMPLE_PICTURES_DIR = "sample-pictures"

        /** The sample sets open in this process, by the name of their pictures' folder. */
        private val openSets = mutableSetOf<String>()

        /** Builds and loads a fresh sample set, dated so that it ends at [clock]. */
        suspend fun open(context: Context, real: AppContainer, clock: () -> LocalDateTime = { LocalDateTime.now() }): DummyData {
            val now = clock()
            val data = DummyData(context.applicationContext, real, clock)
            try {
                // Pictures of a set that was never closed (the app was stopped while it showed) go now.
                withContext(Dispatchers.IO) {
                    File(context.applicationContext.cacheDir, SAMPLE_PICTURES_DIR).listFiles()
                        ?.filter { synchronized(openSets) { it.name !in openSets } }
                        ?.forEach { it.deleteRecursively() }
                }
                try {
                    SampleSet(now).writeTo(data)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Show whatever was written rather than nothing; the user's data is unaffected either way.
                    Log.e(TAG, "Couldn't write the whole sample set", e)
                }
                data.container.finance.load()
                data.container.reminders.load()
                data.container.cards.load()
            } catch (e: CancellationException) {
                data.close()
                throw e
            }
            return data
        }
    }

    /** Holds the sample cards in memory only - never in the encrypted on-device vault. */
    private class InMemoryCardVault : CardVault {
        @Volatile private var cards: List<StoredCard> = emptyList()

        override suspend fun loadCards(): List<StoredCard> = cards

        override suspend fun saveCards(cards: List<StoredCard>) {
            this.cards = cards.toList()
        }
    }

    /**
     * One consistent household: a salary into HDFC Savings pays the rent, the
     * ICICI credit card is settled each month for exactly what was spent on
     * it, cash and the Paytm wallet are topped up from the bank, and every
     * amount comes from a fixed seed, so the set looks the same each time.
     */
    private class SampleSet(private val now: LocalDateTime) {
        private val random = Random(SEED)
        private val firstMonth = MonthKey.of(now).addMonths(-(MONTHS - 1))
        private val start = firstMonth.start

        suspend fun writeTo(data: DummyData) {
            writeFinance(data.container.finance.repository)
            writeReminders(ReminderDao(data.remindersDatabase, dispatcher = data.remindersDispatcher))
            data.container.vault.saveCards(cards())
        }

        private suspend fun writeFinance(repo: FinanceRepository) {
            // The database seeds a Cash account and the default categories on creation.
            val cash = repo.accounts().first()
            repo.saveAccount(cash.copy(openingBalance = rupees(2_500)))
            val bank = repo.account("HDFC Savings", AccountKind.Bank, opening = rupees(1_48_000), order = 1)
            val card = repo.account("ICICI Credit Card", AccountKind.Card, opening = -rupees(8_450), order = 2, statementDay = 5, dueDay = 20)
            val upi = repo.account("Paytm Wallet", AccountKind.Wallet, opening = rupees(1_200), order = 3)
            repo.setDefaultAccountId(bank)

            // The people the household spends on, so the People page and the per-person totals have something to show.
            val mom = repo.person("Mom", "👩", 6)
            val dad = repo.person("Dad", "👨", 2)
            val riya = repo.person("Riya", "💞", 0)
            val arjun = repo.person("Arjun", null, 5)

            val categories = repo.categories().associate { it.name to it.id!! }
            fun category(name: String) = checkNotNull(categories[name]) { "No default category \"$name\"" }
            val food = category("Food & dining")
            val groceries = category("Groceries")
            val transport = category("Transport")
            val shopping = category("Shopping")
            val bills = category("Bills & utilities")
            val rent = category("Rent & housing")
            val health = category("Health")
            val entertainment = category("Entertainment")
            val salary = category("Salary")
            val freelance = category("Freelance")
            val interest = category("Interest")

            // Fixed monthly payments become real transactions through the normal recurring
            // engine when the controller loads (FinanceRepository.processRecurring).
            repo.monthly("Acme Technologies Pvt Ltd", TransactionType.Income, rupees(92_000), bank, salary, day = 1, hour = 9, note = "Salary")
            repo.monthly("Ramesh Iyer", TransactionType.Expense, rupees(24_000), bank, rent, day = 5, hour = 10, note = "Rent - Flat 304")
            repo.monthly("Airtel Xstream Fiber", TransactionType.Expense, rupees(999), bank, bills, day = 8, hour = 8)
            repo.monthly("Netflix", TransactionType.Expense, rupees(649), card, entertainment, day = 12, hour = 6)
            repo.monthly("Cult.fit", TransactionType.Expense, rupees(1_499), card, health, day = 15, hour = 7, note = "Gym membership")
            val recurringOnCard = rupees(649) + rupees(1_499)

            // Monthly budgets, set from the first month so history has something to compare.
            repo.setBudget(null, firstMonth, rupees(70_000))
            repo.setBudget(food, firstMonth, rupees(8_000))
            repo.setBudget(groceries, firstMonth, rupees(10_000))
            repo.setBudget(transport, firstMonth, rupees(8_000))
            repo.setBudget(shopping, firstMonth, rupees(6_000))
            repo.setBudget(entertainment, firstMonth, rupees(2_500))

            val cardSpend = mutableMapOf<MonthKey, Long>()
            val transactions = mutableListOf<FinanceTransaction>()

            fun spend(account: Long, category: Long, amount: Long, payee: String, at: LocalDateTime, note: String? = null, person: Long? = null) {
                if (at.isAfter(now)) return
                transactions += FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = amount,
                    accountId = account,
                    categoryId = category,
                    occurredAt = at,
                    payee = payee,
                    note = note,
                    createdAt = at,
                    updatedAt = at,
                    personId = person,
                )
                if (account == card) cardSpend.merge(MonthKey.of(at), amount, Long::plus)
            }

            fun transfer(amount: Long, from: Long, to: Long, note: String, at: LocalDateTime) {
                if (at.isAfter(now) || amount <= 0) return
                transactions += FinanceTransaction(
                    type = TransactionType.Transfer,
                    amount = amount,
                    accountId = from,
                    toAccountId = to,
                    occurredAt = at,
                    payee = note,
                    createdAt = at,
                    updatedAt = at,
                )
            }

            fun income(amount: Long, account: Long, category: Long, payee: String, at: LocalDateTime, note: String) {
                if (at.isAfter(now)) return
                transactions += FinanceTransaction(
                    type = TransactionType.Income,
                    amount = amount,
                    accountId = account,
                    categoryId = category,
                    occurredAt = at,
                    payee = payee,
                    note = note,
                    createdAt = at,
                    updatedAt = at,
                )
            }

            var day = start.toLocalDate()
            var index = 0
            while (!day.isAfter(now.toLocalDate())) {
                val dom = day.dayOfMonth
                val weekday = day.dayOfWeek.value <= 5
                val month = MonthKey.of(day)

                if (index % 5 == 2) {
                    val (payee, account) = listOf("BigBasket" to bank, "DMart" to card, "Reliance Fresh" to bank, "Nature's Basket" to card)[index / 5 % 4]
                    spend(account, groceries, pick(900, 2_600), payee, day.at(18, 40))
                }
                if (index % 7 == 4) spend(cash.id!!, groceries, pick(120, 320), "Vegetable market", day.at(8, 15))
                when (index % 7) {
                    1 -> spend(upi, food, pick(90, 280), listOf("Chai Point", "Third Wave Coffee").random(random), day.at(11, 5))
                    3 -> spend(card, food, pick(380, 1_100), listOf("Swiggy", "Zomato").random(random), day.at(20, 45))
                    5 -> spend(card, food, pick(650, 1_400), listOf("Meghana Foods", "Truffles", "Toit").random(random), day.at(13, 30), person = if (index / 7 % 2 == 0) riya else null)
                }
                if (weekday && index % 2 == 0) {
                    if (index % 4 == 0) {
                        spend(upi, transport, pick(40, 80), "Namma Metro", day.at(9, 10))
                    } else {
                        spend(bank, transport, pick(180, 420), listOf("Uber", "Ola", "Rapido").random(random), day.at(9, 25))
                    }
                }
                when (dom) {
                    1 -> transfer(rupees(1_500), bank, upi, "Paytm top-up", day.at(9, 30))
                    3, 17 -> spend(card, transport, pick(2_000, 2_800) + random.nextLong(0, 100), "Indian Oil", day.at(19, 0), note = "Fuel")
                    6 -> if (!weekday || index % 2 == 1) spend(card, entertainment, pick(520, 980), "PVR INOX", day.at(21, 15), person = riya)
                    9 -> spend(card, shopping, pick(1_200, 3_500), "Amazon", day.at(22, 10), person = if (month.toInt() % 2 == 0) mom else arjun)
                    10 -> transfer(rupees(3_000), bank, cash.id!!, "ATM withdrawal", day.at(17, 20))
                    13 -> spend(bank, health, pick(260, 890), "Apollo Pharmacy", day.at(19, 50), person = mom)
                    18 -> spend(bank, bills, pick(1_450, 1_950), "BESCOM", day.at(10, 0), note = "Electricity")
                    20 -> {
                        // Settle the card: last month's statement (or, in the first month, the balance carried in).
                        val owed = if (month == firstMonth) rupees(8_450) else (cardSpend[month.previous] ?: 0) + recurringOnCard
                        transfer(owed, bank, card, "ICICI card bill", day.at(11, 0))
                    }
                    21 -> spend(card, entertainment, pick(450, 900), "BookMyShow", day.at(18, 0), person = riya)
                    22 -> spend(upi, bills, rupees(299), "Jio prepaid", day.at(12, 0), note = "Mobile recharge")
                    24 -> spend(card, shopping, pick(1_500, 4_500), listOf("Myntra", "Decathlon", "IKEA").random(random), day.at(16, 30), person = if (month.toInt() % 2 == 0) dad else riya)
                }
                if (month == firstMonth && dom == day.lengthOfMonth()) {
                    income(rupees(1_186), bank, interest, "HDFC Bank", day.at(23, 0), note = "Savings interest")
                }
                if (month == firstMonth.next && dom == 14) {
                    income(rupees(18_500), bank, freelance, "Upwork", day.at(15, 0), note = "Logo design project")
                }
                day = day.plusDays(1)
                index++
            }
            repo.saveTransactions(transactions)
        }

        private suspend fun FinanceRepository.person(name: String, emoji: String?, color: Int): Long =
            savePerson(Person(name = name, emoji = emoji, color = color, createdAt = start, updatedAt = start)).id!!

        private suspend fun FinanceRepository.account(
            name: String,
            kind: AccountKind,
            opening: Long,
            order: Int,
            statementDay: Int? = null,
            dueDay: Int? = null,
        ): Long =
            saveAccount(
                Account(
                    name = name,
                    kind = kind,
                    openingBalance = opening,
                    sortOrder = order,
                    statementDay = statementDay,
                    dueDay = dueDay,
                    createdAt = start,
                    updatedAt = start,
                    // "HDFC Savings" belongs to HDFC Bank, as the app's own migration would link it.
                    bankKey = BankCatalog.BuiltIn.leading(name)?.key,
                ),
            ).id!!

        private suspend fun FinanceRepository.monthly(
            payee: String,
            type: TransactionType,
            amount: Long,
            accountId: Long,
            categoryId: Long,
            day: Int,
            hour: Int,
            note: String? = null,
        ) {
            val first = start.toLocalDate().withDayOfMonth(day).at(hour, 0)
            saveRecurringRule(
                RecurringRule(
                    type = type,
                    amount = amount,
                    accountId = accountId,
                    categoryId = categoryId,
                    payee = payee,
                    note = note,
                    frequency = RecurrenceFrequency.Monthly,
                    startDate = first,
                    nextDate = first,
                    createdAt = start,
                    updatedAt = start,
                ),
            )
        }

        private suspend fun writeReminders(dao: ReminderDao) {
            fun reminder(title: String, notes: String, category: ReminderCategory, dueAt: LocalDateTime, repeat: RepeatRule) =
                Reminder(
                    title = title,
                    notes = notes,
                    category = category,
                    dueAt = dueAt,
                    repeatRule = repeat,
                    createdAt = start,
                    updatedAt = start,
                )

            // Already due: shows how an overdue reminder is flagged across the app.
            val yesterday = now.toLocalDate().minusDays(1)
            dao.insert(
                reminder(
                    "Pay society maintenance",
                    "Flat 304 - ₹3,500 to Prestige Lakeside RWA",
                    ReminderCategory.Payments,
                    yesterday.at(10, 0),
                    RepeatRule(RepeatKind.Monthly, dayOfMonth = yesterday.dayOfMonth),
                ),
            )
            dao.insert(
                reminder(
                    "Keep ₹10,000 for the SIP",
                    "Mutual fund SIP debits from HDFC Savings on the 7th",
                    ReminderCategory.Accounts,
                    nextDayOfMonth(7),
                    RepeatRule(RepeatKind.Monthly, dayOfMonth = 7),
                ),
            )
            dao.insert(
                reminder(
                    "Pay ICICI credit card bill",
                    "Pay the full statement amount to avoid interest",
                    ReminderCategory.Cards,
                    nextDayOfMonth(20),
                    RepeatRule(RepeatKind.Monthly, dayOfMonth = 20),
                ),
            )
            dao.insert(
                reminder(
                    "Renew car insurance",
                    "HDFC ERGO policy ending 8821",
                    ReminderCategory.Payments,
                    now.toLocalDate().plusDays(12).at(11, 0),
                    RepeatRule.Once,
                ),
            )
        }

        /** Public test card numbers - they pass validation but belong to no real account. */
        private fun cards(): List<StoredCard> {
            val expiry = ((now.year + 3) % 100).toString().padStart(2, '0')
            return listOf(
                StoredCard(
                    id = "dummy-hdfc-debit",
                    bankName = "HDFC Bank",
                    cardholderName = "ANANYA RAO",
                    bankKey = "hdfc",
                    cardNumber = "4012888888881881",
                    expiryMonth = "08",
                    expiryYear = expiry,
                    cvv = "123",
                    kind = CardKind.Debit,
                    createdAt = start,
                ),
                StoredCard(
                    id = "dummy-icici-credit",
                    bankName = "ICICI Bank",
                    cardholderName = "ANANYA RAO",
                    bankKey = "icici",
                    cardNumber = "5555555555554444",
                    expiryMonth = "11",
                    expiryYear = expiry,
                    cvv = "456",
                    kind = CardKind.Credit,
                    createdAt = start,
                ),
            )
        }

        /** The next [day] of the month at [hour], from today on. */
        private fun nextDayOfMonth(day: Int, hour: Int = 9): LocalDateTime {
            val thisMonth = now.toLocalDate().withDayOfMonth(day).at(hour, 0)
            return if (thisMonth.isAfter(now)) thisMonth else thisMonth.plusMonths(1)
        }

        private fun pick(min: Int, max: Int): Long = rupees(random.nextInt(min, max + 1))

        private fun rupees(amount: Int): Long = amount * 100L

        private fun LocalDate.at(hour: Int, minute: Int): LocalDateTime = atTime(LocalTime.of(hour, minute))

        companion object {
            private const val SEED = 20_26L
            private const val MONTHS = 3
        }
    }
}
