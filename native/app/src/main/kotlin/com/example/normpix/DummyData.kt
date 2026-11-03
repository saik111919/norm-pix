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
        // So are the widgets: a tap on one reaches the app whichever set is showing (and the UI offers the way back).
        widgetRequests = real.widgetRequests,
    )

    /** Drops the sample set and frees its stores, its pictures too. */
    fun close() {
        container.reminders.close()
        remindersScope.cancel()
        financeDatabase.close()
        remindersDatabase.close()
        synchronized(openSets) { openSets -= picturesDir.name }
        val sampleParent = picturesDir.parentFile
        CoroutineScope(Dispatchers.IO).launch {
            picturesDir.deleteRecursively()
            if (sampleParent != null) cleanLeftoverSamplePictures(sampleParent)
        }
    }

    companion object {
        suspend fun open(context: Context, real: AppContainer, now: LocalDateTime): DummyData =
            open(context, real) { now }

        private const val TAG = "NormPixDummyData"

        /** Under the app's cache: the folders of the sample sets' pictures, one per set (see `res/xml/scan_file_paths.xml`). */
        internal const val SAMPLE_PICTURES_DIR = "sample-pictures"

        /** The sample sets open in this process, by the name of their pictures' folder. */
        private val openSets = mutableSetOf<String>()

        private fun cleanLeftoverSamplePictures(samplePicturesDir: File) {
            samplePicturesDir.listFiles()
                ?.filter { synchronized(openSets) { it.name !in openSets } }
                ?.forEach { it.deleteRecursively() }
        }

        /** Builds and loads a fresh sample set, dated so that it ends at [clock]. */
        suspend fun open(context: Context, real: AppContainer, clock: () -> LocalDateTime = { LocalDateTime.now() }): DummyData {
            val now = clock()
            val data = DummyData(context.applicationContext, real, clock)
            try {
                // Pictures of a set that was never closed (the app was stopped while it showed) go now.
                withContext(Dispatchers.IO) {
                    cleanLeftoverSamplePictures(File(context.applicationContext.cacheDir, SAMPLE_PICTURES_DIR))
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
     * One consistent household: a salary into HDFC Savings pays the rent, credit
     * cards are settled each month for exactly what was spent on them, cash and
     * wallets are topped up from the bank, and every amount comes from a fixed
     * seed, so the set looks the same each time.
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
            repo.saveAccount(cash.copy(openingBalance = rupees(3_000)))
            val bank = repo.account("HDFC Savings", AccountKind.Bank, opening = rupees(1_50_000), order = 1)
            val card = repo.account("ICICI Credit Card", AccountKind.Card, opening = -rupees(8_450), order = 2, statementDay = 5, dueDay = 20)
            val upi = repo.account("Paytm Wallet", AccountKind.Wallet, opening = rupees(2_000), order = 3)
            val bank2 = repo.account("SBI Savings", AccountKind.Bank, opening = rupees(45_000), order = 4)
            val card2 = repo.account("Axis Credit Card", AccountKind.Card, opening = -rupees(4_200), order = 5, statementDay = 12, dueDay = 28)
            repo.setDefaultAccountId(bank)

            // The people the household spends on, so the People page and the per-person totals have something to show.
            val mom = repo.person("Mom", "👩", 6)
            val dad = repo.person("Dad", "👨", 2)
            val riya = repo.person("Riya", "💖", 0)
            val arjun = repo.person("Arjun", null, 5)
            val kavya = repo.person("Kavya", "✨", 3)
            val rohit = repo.person("Rohit", "🎮", 1)

            val categories = repo.categories().associate { it.name to it.id!! }
            fun category(name: String) = checkNotNull(categories[name]) { "No default category \"$name\"" }
            val food = category("Food & dining")
            val groceries = category("Groceries")
            val transport = category("Transport")
            val shopping = category("Shopping")
            val bills = category("Bills & utilities")
            val rent = category("Rent & housing")
            val health = category("Health")
            val personalCare = category("Personal care")
            val fitness = category("Fitness")
            val entertainment = category("Entertainment")
            val salary = category("Salary")
            val freelance = category("Freelance")
            val interest = category("Interest")

            // Sub-categories from CategorySuggestions seed list
            val restaurants = category("Restaurants")
            val coffeeTea = category("Coffee & tea")
            val deliveryApps = category("Delivery apps")
            val fruitsVeg = category("Fruits & vegetables")
            val staplesGrains = category("Staples & grains")
            val dairyEggs = category("Dairy & eggs")
            val packagedSnacks = category("Packaged snacks")
            val petrolDiesel = category("Petrol & diesel")
            val busMetro = category("Bus & metro")
            val cabsRideshare = category("Cabs & rideshare")
            val autoRickshaw = category("Auto rickshaw")
            val clothing = category("Clothing")
            val onlineOrders = category("Online orders")
            val electricity = category("Electricity")
            val mobileRecharge = category("Mobile recharge")
            val broadband = category("Broadband")
            val rentSub = category("Rent")
            val cleaningMaid = category("Cleaning & maid")
            val doctor = category("Doctor")
            val pharmacy = category("Pharmacy")
            val salonHaircut = category("Salon & haircut")
            val gymMembership = category("Gym membership")
            val movies = category("Movies")
            val netflixSub = category("Netflix")
            val spotifySub = category("Spotify")
            val mutualFunds = category("Mutual funds")
            val monthlySalary = category("Monthly salary")
            val clientProjects = category("Client projects")
            val savingsInterest = category("Savings account")

            // Fixed monthly payments become real transactions through the normal recurring
            // engine when the controller loads (FinanceRepository.processRecurring).
            repo.monthly("Acme Technologies Pvt Ltd", TransactionType.Income, rupees(95_000), bank, monthlySalary, day = 1, hour = 9, note = "Salary")
            repo.monthly("Ramesh Iyer", TransactionType.Expense, rupees(24_000), bank, rentSub, day = 5, hour = 10, note = "Rent - Flat 304")
            repo.monthly("Airtel Xstream Fiber", TransactionType.Expense, rupees(999), bank, broadband, day = 8, hour = 8)
            repo.monthly("Cult.fit", TransactionType.Expense, rupees(2_500), card, gymMembership, day = 1, hour = 7, note = "Cult Pass Elite")
            repo.monthly("Netflix", TransactionType.Expense, rupees(649), card, netflixSub, day = 12, hour = 6)
            repo.monthly("Spotify", TransactionType.Expense, rupees(119), card2, spotifySub, day = 3, hour = 7)
            repo.monthly("House Maid & Cook", TransactionType.Expense, rupees(4_500), bank2, cleaningMaid, day = 1, hour = 11, note = "Monthly maid & cook")
            repo.monthly("SIP - Index Fund", TransactionType.Expense, rupees(10_000), bank, mutualFunds, day = 7, hour = 9, note = "Mutual fund SIP")

            val recurringOnIcici = rupees(2_500) + rupees(649)
            val recurringOnAxis = rupees(119)

            // Monthly budgets, set from the first month so history has something to compare.
            // In the current month:
            //  - Shopping is exceeded (> 100%) due to early clothing purchases;
            //  - Fitness is approaching (>= 80%) with Cult.fit gym membership at ₹2,500 / ₹3,000 (83.3%);
            //  - Groceries, Food & dining, Transport are well under (Healthy, < 65%);
            //  - Overall budget (₹85,000) stays safely on track (Healthy).
            repo.setBudget(null, firstMonth, rupees(85_000))
            repo.setBudget(groceries, firstMonth, rupees(15_000))
            repo.setBudget(food, firstMonth, rupees(12_000))
            repo.setBudget(transport, firstMonth, rupees(9_000))
            repo.setBudget(shopping, firstMonth, rupees(5_000))
            repo.setBudget(fitness, firstMonth, rupees(3_000))

            val iciciSpend = mutableMapOf<MonthKey, Long>()
            val axisSpend = mutableMapOf<MonthKey, Long>()
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
                if (account == card) iciciSpend.merge(MonthKey.of(at), amount, Long::plus)
                if (account == card2) axisSpend.merge(MonthKey.of(at), amount, Long::plus)
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
                    val (payee, account) = listOf("BigBasket" to bank, "DMart" to card, "Reliance Fresh" to bank, "Nature's Basket" to card2)[index / 5 % 4]
                    val subcat = listOf(fruitsVeg, staplesGrains, dairyEggs, packagedSnacks)[index / 5 % 4]
                    spend(account, subcat, pick(900, 2_600), payee, day.at(18, 40))
                }
                if (index % 7 == 4) spend(cash.id!!, fruitsVeg, pick(120, 320), "Vegetable market", day.at(8, 15))
                when (index % 7) {
                    1 -> spend(upi, coffeeTea, pick(90, 280), listOf("Chai Point", "Third Wave Coffee").random(random), day.at(11, 5))
                    3 -> spend(card, deliveryApps, pick(380, 1_100), listOf("Swiggy", "Zomato").random(random), day.at(20, 45))
                    5 -> spend(card2, restaurants, pick(650, 1_400), listOf("Meghana Foods", "Truffles", "Toit").random(random), day.at(13, 30), person = if (index / 7 % 2 == 0) riya else rohit)
                }
                if (weekday && index % 2 == 0) {
                    if (index % 4 == 0) {
                        spend(upi, busMetro, pick(40, 80), "Namma Metro", day.at(9, 10))
                    } else {
                        spend(bank, cabsRideshare, pick(180, 420), listOf("Uber", "Ola", "Rapido").random(random), day.at(9, 25))
                    }
                }
                when (dom) {
                    1 -> transfer(rupees(2_000), bank, upi, "Paytm top-up", day.at(9, 30))
                    2 -> {
                        transfer(rupees(15_000), bank, bank2, "Monthly savings", day.at(10, 30))
                        // A stylish wardrobe purchase early in the month pushes Shopping over budget (₹5,600 spent vs ₹5,000 limit).
                        spend(card, clothing, rupees(5_600), "Zara", day.at(14, 0), person = riya)
                    }
                    3 -> spend(card, petrolDiesel, pick(1_800, 2_400), "Indian Oil", day.at(19, 0), note = "Fuel")
                    6 -> if (!weekday || index % 2 == 1) spend(card, movies, pick(650, 1_100), "PVR INOX", day.at(21, 15), person = riya)
                    9 -> spend(card2, onlineOrders, pick(1_200, 2_800), "Amazon", day.at(22, 10), person = if (month.toInt() % 2 == 0) mom else arjun)
                    10 -> transfer(rupees(3_500), bank, cash.id!!, "ATM withdrawal", day.at(17, 20))
                    13 -> spend(bank, pharmacy, pick(260, 950), "Apollo Pharmacy", day.at(19, 50), person = mom)
                    16 -> spend(card2, salonHaircut, pick(400, 850), "Looks Salon", day.at(15, 30), person = kavya)
                    17 -> spend(card2, petrolDiesel, pick(1_800, 2_500), "Shell", day.at(19, 30), note = "Fuel")
                    18 -> spend(bank, electricity, pick(1_450, 1_950), "BESCOM", day.at(10, 0), note = "Electricity")
                    20 -> {
                        // Settle the ICICI credit card: last month's statement (or, in the first month, the opening balance carried in).
                        val owed = if (month == firstMonth) rupees(8_450) else (iciciSpend[month.previous] ?: 0) + recurringOnIcici
                        transfer(owed, bank, card, "ICICI card bill", day.at(11, 0))
                    }
                    21 -> spend(card2, movies, pick(450, 950), "BookMyShow", day.at(18, 0), person = rohit)
                    22 -> spend(upi, mobileRecharge, rupees(299), "Jio prepaid", day.at(12, 0), note = "Mobile recharge")
                    24 -> spend(card, clothing, pick(1_500, 3_500), listOf("Myntra", "Fabindia").random(random), day.at(16, 30), person = if (month.toInt() % 2 == 0) dad else kavya)
                    26 -> spend(bank, doctor, pick(600, 1_200), "Dr. Sharma Clinic", day.at(11, 0), person = dad)
                    28 -> {
                        // Settle the Axis credit card: last month's statement (or, in the first month, the opening balance carried in).
                        val owedAxis = if (month == firstMonth) rupees(4_200) else (axisSpend[month.previous] ?: 0) + recurringOnAxis
                        transfer(owedAxis, bank2, card2, "Axis card bill", day.at(11, 30))
                    }
                }
                if (month == firstMonth && dom == day.lengthOfMonth()) {
                    income(rupees(1_186), bank, savingsInterest, "HDFC Bank", day.at(23, 0), note = "Savings interest")
                }
                if (month == firstMonth.addMonths(1) && dom == 14) {
                    income(rupees(18_500), bank2, clientProjects, "Upwork", day.at(15, 0), note = "Logo design project")
                }
                if (month == firstMonth.addMonths(3) && dom == 19) {
                    income(rupees(24_000), bank2, clientProjects, "Upwork", day.at(16, 0), note = "Mobile app redesign")
                }
                if (month == firstMonth.addMonths(4) && dom == day.lengthOfMonth()) {
                    income(rupees(1_240), bank, savingsInterest, "HDFC Bank", day.at(23, 0), note = "Savings interest")
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

            // Overdue reminders: clearly visible across the app and on Home banners
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
                    "Submit medical insurance claim",
                    "Apollo Pharmacy bills for reimbursement",
                    ReminderCategory.Personal,
                    now.toLocalDate().minusDays(3).at(16, 30),
                    RepeatRule.Once,
                ),
            )

            // Upcoming in the next 7 days
            dao.insert(
                reminder(
                    "Keep ₹10,000 for the SIP",
                    "Mutual fund SIP debits from HDFC Savings",
                    ReminderCategory.Accounts,
                    now.toLocalDate().plusDays(3).at(9, 0),
                    RepeatRule(RepeatKind.Monthly, dayOfMonth = now.toLocalDate().plusDays(3).dayOfMonth),
                ),
            )
            dao.insert(
                reminder(
                    "Pay BESCOM electricity bill",
                    "Consumer No. 4482109 - ₹1,850",
                    ReminderCategory.Payments,
                    now.toLocalDate().plusDays(5).at(11, 0),
                    RepeatRule.Once,
                ),
            )

            // Due later
            dao.insert(
                reminder(
                    "Pay ICICI credit card bill",
                    "Pay the full statement amount to avoid interest",
                    ReminderCategory.Cards,
                    now.toLocalDate().plusDays(16).at(10, 0),
                    RepeatRule(RepeatKind.Monthly, dayOfMonth = 20),
                ),
            )
            dao.insert(
                reminder(
                    "Renew car insurance",
                    "HDFC ERGO policy ending 8821",
                    ReminderCategory.Payments,
                    now.toLocalDate().plusDays(20).at(11, 0),
                    RepeatRule.Once,
                ),
            )
            dao.insert(
                reminder(
                    "Pay Axis credit card bill",
                    "Pay Axis Bank credit card outstanding",
                    ReminderCategory.Cards,
                    now.toLocalDate().plusDays(24).at(10, 0),
                    RepeatRule(RepeatKind.Monthly, dayOfMonth = 28),
                ),
            )
        }

        /** Public synthetic test card numbers from different issuers; one expiring next month to trigger the expiry banner. */
        private fun cards(): List<StoredCard> {
            val expiryFuture = ((now.year + 3) % 100).toString().padStart(2, '0')
            val expiring = now.toLocalDate().plusMonths(1)
            val expiringMonth = expiring.monthValue.toString().padStart(2, '0')
            val expiringYear = (expiring.year % 100).toString().padStart(2, '0')
            return listOf(
                StoredCard(
                    id = "dummy-hdfc-debit",
                    bankName = "HDFC Bank",
                    cardholderName = "ANANYA RAO",
                    bankKey = "hdfc",
                    cardNumber = "4012888888881881",
                    expiryMonth = "08",
                    expiryYear = expiryFuture,
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
                    expiryYear = expiryFuture,
                    cvv = "456",
                    kind = CardKind.Credit,
                    createdAt = start,
                ),
                StoredCard(
                    id = "dummy-sbi-credit",
                    bankName = "State Bank of India",
                    cardholderName = "ANANYA RAO",
                    bankKey = "sbi",
                    cardNumber = "4000056655665556", // a published test number (Stripe's Visa debit), as the others are: never a real card
                    expiryMonth = "09",
                    expiryYear = ((now.year + 4) % 100).toString().padStart(2, '0'),
                    cvv = "789",
                    kind = CardKind.Credit,
                    createdAt = start,
                ),
                StoredCard(
                    id = "dummy-axis-debit",
                    bankName = "Axis Bank",
                    cardholderName = "ANANYA RAO",
                    bankKey = "axis",
                    cardNumber = "4111111111111111",
                    expiryMonth = expiringMonth,
                    expiryYear = expiringYear,
                    cvv = "321",
                    kind = CardKind.Debit,
                    createdAt = start,
                ),
            )
        }

        private fun pick(min: Int, max: Int): Long = rupees(random.nextInt(min, max + 1))

        private fun rupees(amount: Int): Long = amount * 100L

        private fun LocalDate.at(hour: Int, minute: Int): LocalDateTime = atTime(LocalTime.of(hour, minute))

        companion object {
            private const val SEED = 20_26L
            private const val MONTHS = 6
        }
    }
}
