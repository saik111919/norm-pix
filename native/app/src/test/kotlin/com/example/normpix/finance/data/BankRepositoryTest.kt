package com.example.normpix.finance.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Banks in the ledger: one shared list, accounts that name a bank, backups, and history that a bank change never touches. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankRepositoryTest {
    private val now = LocalDateTime.of(2026, 9, 15, 12, 0)
    private lateinit var database: FinanceDatabase
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = FinanceDatabase(context, name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private fun run(block: suspend () -> Unit) = runBlocking { block() }

    private fun account(name: String, kind: AccountKind = AccountKind.Bank, bankKey: String? = null) =
        Account(name = name, kind = kind, createdAt = now, updatedAt = now, bankKey = bankKey)

    @Test
    fun aBuiltInBankIsFoundNotAdded() = run {
        assertEquals("hdfc", repo.bankKeyFor("hdfc"))
        assertEquals("hdfc", repo.bankKeyFor("  HDFC bank  ltd "))
        assertTrue(repo.customBanks().isEmpty(), "nothing was stored for a bank the app already has")
    }

    @Test
    fun aNewBankIsAddedOnceHoweverItIsWritten() = run {
        val first = repo.bankKeyFor("My Credit Union")
        assertTrue(first.startsWith("custom-"))
        assertEquals(first, repo.bankKeyFor("my credit union"))
        assertEquals(first, repo.bankKeyFor("  MY   CREDIT  UNION  "))
        assertEquals(first, repo.bankKeyFor("My Credit Union Ltd."))
        assertEquals(listOf("My Credit Union"), repo.customBanks().map { it.name })
        assertNotEquals(first, repo.bankKeyFor("Another Co-op"))
        assertEquals(2, repo.customBanks().size)
    }

    @Test
    fun aBankNeedsARealName() = run {
        assertFailsWith<FinanceValidationException> { repo.bankKeyFor("") }
        assertFailsWith<FinanceValidationException> { repo.bankKeyFor("   ") }
        assertFailsWith<FinanceValidationException> { repo.bankKeyFor("...") }
        assertFailsWith<FinanceValidationException> { repo.bankKeyFor("x".repeat(61)) }
        assertTrue(repo.customBanks().isEmpty())
    }

    @Test
    fun anAccountKeepsItsBank() = run {
        val custom = repo.bankKeyFor("My Credit Union")
        val saved = repo.saveAccount(account("HDFC Savings", bankKey = "hdfc"))
        val other = repo.saveAccount(account("Co-op", kind = AccountKind.Savings, bankKey = custom))
        val none = repo.saveAccount(account("Mystery"))

        val byName = repo.accounts().associateBy { it.name }
        assertEquals("hdfc", byName["HDFC Savings"]?.bankKey)
        assertEquals(custom, byName["Co-op"]?.bankKey)
        assertNull(byName["Mystery"]?.bankKey)
        assertEquals("hdfc", saved.bankKey)
        assertEquals(custom, other.bankKey)
        assertNull(none.bankKey)
    }

    @Test
    fun anAccountCannotNameABankTheAppDoesNotKnow() = run {
        assertFailsWith<FinanceValidationException> { repo.saveAccount(account("Ghost", bankKey = "custom-does-not-exist")) }
        assertFailsWith<FinanceValidationException> { repo.saveAccount(account("Ghost", bankKey = "not-a-bank")) }
        assertTrue(repo.accounts().none { it.name == "Ghost" })
    }

    @Test
    fun cashHasNoBank() = run {
        val saved = repo.saveAccount(account("Wallet cash", kind = AccountKind.Cash, bankKey = "hdfc"))
        assertNull(saved.bankKey)
        assertNull(repo.accounts().first { it.name == "Wallet cash" }.bankKey)
    }

    @Test
    fun theBankCanBeChangedOrClearedLater() = run {
        val saved = repo.saveAccount(account("Salary", bankKey = "hdfc"))
        repo.saveAccount(saved.copy(bankKey = "axis"))
        assertEquals("axis", repo.accounts().first { it.id == saved.id }.bankKey)
        repo.saveAccount(saved.copy(bankKey = null))
        assertNull(repo.accounts().first { it.id == saved.id }.bankKey)
    }

    @Test
    fun changingTheBankNeverTouchesHistory() = run {
        val cash = repo.accounts().first { it.kind == AccountKind.Cash }
        val category = repo.categories().first { it.type.key == "expense" }
        val salary = repo.saveAccount(account("Salary", bankKey = "hdfc"))
        repo.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense,
                amount = 12_500,
                accountId = salary.id!!,
                categoryId = category.id,
                occurredAt = now.minusDays(3),
                createdAt = now,
                updatedAt = now,
            ),
        )
        repo.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Transfer,
                amount = 5_000,
                accountId = salary.id,
                toAccountId = cash.id,
                occurredAt = now.minusDays(2),
                createdAt = now,
                updatedAt = now,
            ),
        )
        val everything = DateRange(now.minusYears(1), now.plusDays(1))
        val balancesBefore = repo.accountBalances()
        val historyBefore = repo.transactions(TransactionQuery()).items
        val spentBefore = repo.summary(everything).expense

        repo.saveAccount(salary.copy(bankKey = "axis"))
        repo.saveAccount(salary.copy(bankKey = null))
        repo.bankKeyFor("Some New Bank")

        assertEquals(balancesBefore, repo.accountBalances())
        assertEquals(historyBefore, repo.transactions(TransactionQuery()).items)
        assertEquals(spentBefore, repo.summary(everything).expense)
        assertEquals(2, historyBefore.size)
        assertTrue(historyBefore.all { it.accountId == salary.id })
    }

    @Test
    fun deletingAnAccountLeavesItsBank() = run {
        val key = repo.bankKeyFor("My Credit Union")
        val a = repo.saveAccount(account("Co-op", bankKey = key))
        repo.deleteAccount(a.id!!)
        assertEquals(listOf(key), repo.customBanks().map { it.key })
    }

    // ------------------------------------------------------------- backups

    @Test
    fun aBackupCarriesTheBanksAndEachAccountsLink() = run {
        val key = repo.bankKeyFor("My Credit Union")
        repo.saveAccount(account("HDFC Savings", bankKey = "hdfc"))
        repo.saveAccount(account("Co-op", bankKey = key))
        repo.saveAccount(account("Mystery"))

        val decoded = FinanceBackupCodec.decode(FinanceBackupCodec.encode(repo.snapshot(), now))
        val snapshot = decoded.snapshot ?: error(decoded.errors.toString())
        assertEquals(listOf("My Credit Union"), snapshot.banks.map { it.name })
        assertEquals(
            mapOf("HDFC Savings" to "hdfc", "Co-op" to key, "Mystery" to null),
            snapshot.accounts.filter { it.name != "Cash" }.associate { it.name to it.bankKey },
        )

        // Into a fresh app: everything comes back, links included.
        val context: Context = ApplicationProvider.getApplicationContext()
        val fresh = FinanceRepository(
            FinanceDatabase(context, name = null, clock = { now }),
            clock = { now },
            dispatcher = Dispatchers.Unconfined,
        )
        try {
            fresh.restore(snapshot)
            assertEquals(listOf(key), fresh.customBanks().map { it.key })
            assertEquals(key, fresh.accounts().first { it.name == "Co-op" }.bankKey)
            assertEquals("hdfc", fresh.accounts().first { it.name == "HDFC Savings" }.bankKey)
        } finally {
            fresh.close()
        }
    }

    private fun backup(accounts: String) = """
        {"format":"normpix-finance-backup","version":1,"exportedAt":"2026-01-01T00:00:00.000","currency":"INR",
         "settings":{"defaultAccountId":null},
         "accounts":[$accounts],
         "categories":[],"transactions":[],"budgets":[],"recurringRules":[]}
    """.trimIndent()

    private fun accountJson(id: Int, name: String, kind: String, extra: String = "") =
        """{"id":$id,"name":"$name","kind":"$kind","openingBalance":0,"archived":false,"sortOrder":$id,$extra"createdAt":"2026-01-01T00:00:00.000","updatedAt":"2026-01-01T00:00:00.000"}"""

    @Test
    fun aBackupFromBeforeBanksExistedStillRestoresAndLinksWhatItCanTell() = run {
        val old = backup(
            listOf(
                accountJson(1, "Cash", "cash"),
                accountJson(2, "HDFC Savings", "bank"),
                accountJson(3, "Savings for Axis bike", "savings"),
            ).joinToString(","),
        )
        val snapshot = FinanceBackupCodec.decode(old).snapshot ?: error("old backup rejected")
        assertTrue(snapshot.banks.isEmpty())
        assertEquals(listOf(null, "hdfc", null), snapshot.accounts.map { it.bankKey })
        repo.restore(snapshot)
        assertEquals(listOf("Cash", "HDFC Savings", "Savings for Axis bike"), repo.accounts().map { it.name })
        assertEquals("hdfc", repo.accounts()[1].bankKey)
    }

    @Test
    fun anExplicitNullBankIsRespectedNotInferred() = run {
        val snapshot = FinanceBackupCodec.decode(backup(accountJson(1, "HDFC Savings", "bank", "\"bankKey\":null,"))).snapshot
            ?: error("rejected")
        assertNull(snapshot.accounts.single().bankKey, "the user chose no bank")
    }

    @Test
    fun aBackupNamingAnUnknownBankIsRefused() = run {
        val result = FinanceBackupCodec.decode(backup(accountJson(1, "Ghost", "bank", "\"bankKey\":\"custom-missing\",")))
        assertNull(result.snapshot)
        assertTrue(result.errors.single().contains("missing bank"))
    }

    @Test
    fun resettingEverythingClearsTheUsersBanksToo() = run {
        val key = repo.bankKeyFor("My Credit Union")
        repo.saveAccount(account("Co-op", bankKey = key))
        repo.resetAll()
        assertTrue(repo.customBanks().isEmpty())
        assertTrue(repo.accounts().all { it.bankKey == null })
    }
}
