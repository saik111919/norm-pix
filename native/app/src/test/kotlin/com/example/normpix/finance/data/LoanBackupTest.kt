package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.LoanTerms
import com.example.normpix.finance.domain.emiFor
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Loans in a backup (format 4, 2026-11-15): an account may carry a "loan" and be of the kind "loan". A file of version 1 to 3 - which
 * has no loans - is read and restored exactly as before; a version 4 file round-trips every loan field; an impossible loan makes the
 * file refused with its error and nothing is written; a file newer than 4 is still refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoanBackupTest {
    private val now = LocalDateTime.of(2026, 11, 15, 9, 0)
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private val homeLoan = LoanTerms(
        principal = 100_000_000L,
        rateBp = 875,
        tenureMonths = 180,
        emi = emiFor(100_000_000L, 875, 180),
        start = LocalDate.of(2026, 12, 5),
    )
    private val carLoan = LoanTerms(
        principal = 7_500_050L,
        rateBp = 1050,
        tenureMonths = 36,
        emi = 243_719L,
        start = LocalDate.of(2027, 1, 31),
    )

    private fun account(name: String, kind: AccountKind, loan: LoanTerms? = null, opening: Long = 0) =
        Account(name = name, kind = kind, openingBalance = opening, createdAt = now, updatedAt = now, loan = loan)

    private fun snapshotOf(vararg accounts: Account) = FinanceSnapshot(
        currencyCode = "INR",
        accounts = accounts.mapIndexed { index, a -> a.copy(id = index + 1L) },
        categories = emptyList(),
        transactions = emptyList(),
        budgets = emptyList(),
        recurringRules = emptyList(),
    )

    private fun decoded(snapshot: FinanceSnapshot) = FinanceBackupCodec.decode(FinanceBackupCodec.encode(snapshot, exportedAt = now))

    // --------------------------------------------------------------------------------------------- the format

    @Test
    fun `the format is 4 or later, and a file newer than this release's is still refused`() {
        // 4 brought loans; 5 (2026-11-15) brought tags. A loan reads the same in either.
        assertTrue(FinanceBackupCodec.FORMAT_VERSION >= 4)
        val current = FinanceBackupCodec.FORMAT_VERSION
        val json = FinanceBackupCodec.encode(snapshotOf(account("Cash", AccountKind.Cash)), exportedAt = now)
        assertTrue("\"version\": $current" in json)
        assertTrue(FinanceBackupCodec.decode(json).isValid, "this version reads what it writes")
        assertTrue(FinanceBackupCodec.decode(json.replace("\"version\": $current", "\"version\": 4")).isValid, "a version 4 file with a loan still reads")
        val newer = FinanceBackupCodec.decode(json.replace("\"version\": $current", "\"version\": ${current + 1}"))
        assertFalse(newer.isValid)
        assertEquals(listOf("This backup was made by a newer version of NormPix. Update the app, then try again."), newer.errors)
    }

    @Test
    fun `a loan is written for a loan only, with all five fields`() {
        val json = FinanceBackupCodec.encode(
            snapshotOf(account("Cash", AccountKind.Cash), account("Home loan", AccountKind.Loan, homeLoan, -homeLoan.principal)),
            exportedAt = now,
        )
        assertEquals(1, Regex("\"loan\":").findAll(json).count(), "one account has the key: the loan")
        assertTrue("\"kind\": \"loan\"" in json)
        assertTrue("\"principal\": 100000000" in json)
        assertTrue("\"rateBp\": 875" in json)
        assertTrue("\"tenureMonths\": 180" in json)
        assertTrue("\"emi\": ${homeLoan.emi}" in json)
        assertTrue("\"start\": \"2026-12-05\"" in json)
    }

    @Test
    fun `a loan round-trips every field, a loan account and an account with the switch on alike`() {
        val snapshot = snapshotOf(
            account("Home loan", AccountKind.Loan, homeLoan, -homeLoan.principal),
            account("Old car loan", AccountKind.Other, carLoan, -carLoan.principal),
            account("Savings", AccountKind.Savings),
        )
        val read = decoded(snapshot)
        assertTrue(read.isValid, read.errors.toString())
        val accounts = read.snapshot!!.accounts
        assertEquals(homeLoan, accounts[0].loan)
        assertEquals(AccountKind.Loan, accounts[0].kind, "the kind \"loan\" is accepted")
        assertEquals(carLoan, accounts[1].loan, "the 31st of the month and an odd amount survive")
        assertEquals(AccountKind.Other, accounts[1].kind)
        assertNull(accounts[2].loan)
        assertEquals(snapshot.accounts, accounts, "every field of every account is as it was")
    }

    @Test
    fun `an EMI left out of a loan is worked out from the rest`() {
        val json = FinanceBackupCodec.encode(snapshotOf(account("Home loan", AccountKind.Loan, homeLoan, -homeLoan.principal)), exportedAt = now)
            .replace(Regex("\"emi\": \\d+,\\s*"), "")
        assertFalse("\"emi\"" in json)
        val read = FinanceBackupCodec.decode(json)
        assertTrue(read.isValid, read.errors.toString())
        assertEquals(homeLoan, read.snapshot!!.accounts.single().loan)
    }

    // ----------------------------------------------------------------------------------- the restore

    @Test
    fun `export, reset and restore brings the loan back, with its terms and what is owed`() = runDb {
        val loan = repo.saveAccount(account("Home loan", AccountKind.Loan, homeLoan, -homeLoan.principal))
        val other = repo.saveAccount(account("Old car loan", AccountKind.Bank, carLoan, -carLoan.principal))
        val before = repo.accounts()

        val read = FinanceBackupCodec.decode(FinanceBackupCodec.encode(repo.snapshot(), exportedAt = now))
        assertTrue(read.isValid, read.errors.toString())
        repo.resetAll()
        assertTrue(repo.accounts().none { it.isLoan })
        repo.restore(read.snapshot!!)

        val after = repo.accounts()
        assertEquals(before, after, "every account as it was, loans included")
        assertEquals(homeLoan, after.first { it.id == loan.id }.loan)
        assertEquals(carLoan, after.first { it.id == other.id }.loan)
        assertEquals(-homeLoan.principal, repo.accountBalances()[loan.id])
    }

    @Test
    fun `a snapshot taken for an undo puts a loan back too`() = runDb {
        val loan = repo.saveAccount(account("Home loan", AccountKind.Loan, homeLoan, -homeLoan.principal))
        val snapshot = repo.snapshot()
        repo.saveAccount(loan.copy(loan = homeLoan.copy(rateBp = 999), updatedAt = now))
        repo.restore(snapshot)
        assertEquals(homeLoan, repo.accounts().first { it.id == loan.id }.loan)
    }

    // ------------------------------------------------------------------------------- the version 3 file

    /** A file as the release before this one wrote it (format 3): no "loan" anywhere, and the kinds it had. */
    private val formatThree = """
        {
          "format": "normpix-finance-backup",
          "version": 3,
          "exportedAt": "2026-11-12T08:00:00.000",
          "currency": "INR",
          "settings": { "defaultAccountId": 1 },
          "accounts": [
            { "id": 1, "name": "Cash", "kind": "cash", "openingBalance": 250000, "archived": false, "sortOrder": 0,
              "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000", "bankKey": null, "creditLimit": null,
              "statementDay": null, "dueDay": null, "numberEnding": null, "numberStart": null, "cardId": null },
            { "id": 2, "name": "HDFC Credit", "kind": "card", "openingBalance": -845000, "archived": false, "sortOrder": 1,
              "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-02T10:00:00.000", "bankKey": "hdfc", "creditLimit": 5000000,
              "statementDay": 5, "dueDay": 20, "numberEnding": "3588", "numberStart": "652850", "cardId": "card-1" },
            { "id": 3, "name": "Old Car Loan", "kind": "other", "openingBalance": -30000000, "archived": true, "sortOrder": 2,
              "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000", "bankKey": null, "creditLimit": null,
              "statementDay": null, "dueDay": null, "numberEnding": null, "numberStart": null, "cardId": null }
          ],
          "banks": [],
          "people": [],
          "categories": [
            { "id": 1, "name": "Food & dining", "type": "expense", "icon": "restaurant", "color": 3, "archived": false, "parentId": null,
              "sortOrder": 1, "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000" },
            { "id": 2, "name": "Salary", "type": "income", "icon": "salary", "color": 4, "archived": false, "parentId": null,
              "sortOrder": 2, "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000" }
          ],
          "transactions": [
            { "id": 1, "type": "expense", "amount": 45000, "accountId": 1, "toAccountId": null, "categoryId": 1,
              "occurredAt": "2026-09-01T10:00:00.000", "payee": "Lunch", "note": null, "recurringId": null, "personId": null,
              "picture": null, "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000" },
            { "id": 2, "type": "transfer", "amount": 845000, "accountId": 1, "toAccountId": 2, "categoryId": null,
              "occurredAt": "2026-09-02T10:00:00.000", "payee": null, "note": "Card bill payment", "recurringId": null, "personId": null,
              "picture": null, "createdAt": "2026-09-02T10:00:00.000", "updatedAt": "2026-09-02T10:00:00.000" }
          ],
          "budgets": [ { "id": 1, "categoryId": 1, "amount": 1200000, "effectiveMonth": 202609 } ],
          "recurringRules": []
        }
    """.trimIndent()

    @Test
    fun `a format 3 file reads and restores exactly as before, with no loans`() = runDb {
        val read = FinanceBackupCodec.decode(formatThree)
        assertTrue(read.isValid, read.errors.toString())
        val snapshot = read.snapshot!!
        assertEquals(listOf("Cash", "HDFC Credit", "Old Car Loan"), snapshot.accounts.map { it.name })
        assertTrue(snapshot.accounts.all { it.loan == null && !it.isLoan })
        assertEquals(listOf(AccountKind.Cash, AccountKind.Card, AccountKind.Other), snapshot.accounts.map { it.kind })

        repo.restore(snapshot)
        val accounts = repo.accounts()
        assertEquals(listOf("Cash", "HDFC Credit", "Old Car Loan"), accounts.map { it.name })
        assertEquals(listOf(250_000L, -845_000L, -30_000_000L), accounts.map { it.openingBalance })
        val card = accounts[1]
        assertEquals(5_000_000L, card.creditLimit)
        assertEquals("652850", card.numberStart)
        assertEquals("3588", card.numberEnding)
        assertEquals("card-1", card.cardId)
        assertTrue(accounts[2].archived)
        assertTrue(accounts.none { it.loan != null }, "an old file made no loan")
        assertEquals(2, repo.transactions(TransactionQuery(), limit = 10).items.size)

        // The same file is what the next backup says too, plus the format it is written in now.
        val again = FinanceBackupCodec.decode(FinanceBackupCodec.encode(repo.snapshot(), exportedAt = now))
        assertTrue(again.isValid)
        assertEquals(accounts, again.snapshot!!.accounts)
    }

    @Test
    fun `a format 1 or 2 file reads as it did`() {
        for (version in 1..2) {
            val read = FinanceBackupCodec.decode(formatThree.replace("\"version\": 3", "\"version\": $version"))
            assertTrue(read.isValid, "version $version: ${read.errors}")
            assertTrue(read.snapshot!!.accounts.none { it.isLoan })
        }
    }

    // ------------------------------------------------------------------------------------ refused

    private fun refusedFor(loan: LoanTerms, kind: AccountKind = AccountKind.Bank): List<String> {
        val read = decoded(snapshotOf(account("Home loan", kind, loan)))
        assertFalse(read.isValid, "$loan was accepted")
        return read.errors
    }

    @Test
    fun `an impossible loan makes the backup refused, with what is wrong`() {
        assertEquals(
            listOf("Account \"Home loan\" has a loan amount that is not above zero."),
            refusedFor(homeLoan.copy(principal = 0)),
        )
        assertEquals(listOf("Account \"Home loan\" has a loan amount that is not above zero."), refusedFor(homeLoan.copy(principal = -5)))
        assertEquals(
            listOf("Account \"Home loan\" has a loan interest rate outside 0% to 100% a year."),
            refusedFor(homeLoan.copy(rateBp = 10_001)),
        )
        assertEquals(
            listOf("Account \"Home loan\" has a loan interest rate outside 0% to 100% a year."),
            refusedFor(homeLoan.copy(rateBp = -1)),
        )
        assertEquals(listOf("Account \"Home loan\" has a loan length outside 1 to 600 months."), refusedFor(homeLoan.copy(tenureMonths = 0)))
        assertEquals(listOf("Account \"Home loan\" has a loan length outside 1 to 600 months."), refusedFor(homeLoan.copy(tenureMonths = 601)))
        assertEquals(listOf("Account \"Home loan\" has a loan EMI that is not above zero."), refusedFor(homeLoan.copy(emi = 0)))
        assertEquals(listOf("Account \"Home loan\" has a loan EMI that is not above zero."), refusedFor(homeLoan.copy(emi = -1)))
    }

    @Test
    fun `the edges of what a loan allows are accepted`() {
        for (terms in listOf(
            homeLoan.copy(rateBp = 0),
            homeLoan.copy(rateBp = 10_000),
            homeLoan.copy(tenureMonths = 1),
            homeLoan.copy(tenureMonths = 600),
            homeLoan.copy(principal = 1, emi = 1),
        )) {
            val read = decoded(snapshotOf(account("Loan", AccountKind.Loan, terms)))
            assertTrue(read.isValid, "$terms: ${read.errors}")
        }
    }

    @Test
    fun `a rate or a length too big for a number is refused, not wrapped into range`() {
        val json = FinanceBackupCodec.encode(snapshotOf(account("Home loan", AccountKind.Loan, homeLoan)), exportedAt = now)
        // 2^32 + 875 and 2^32 + 180 would be what a valid loan has if they were narrowed to an Int.
        val rate = FinanceBackupCodec.decode(json.replace("\"rateBp\": 875", "\"rateBp\": 4294968171"))
        assertEquals(listOf("Account \"Home loan\" has a loan interest rate outside 0% to 100% a year."), rate.errors)
        val months = FinanceBackupCodec.decode(json.replace("\"tenureMonths\": 180", "\"tenureMonths\": 4294967476"))
        assertEquals(listOf("Account \"Home loan\" has a loan length outside 1 to 600 months."), months.errors)
    }

    @Test
    fun `a loan with damaged fields is a damaged file`() {
        val json = FinanceBackupCodec.encode(snapshotOf(account("Home loan", AccountKind.Loan, homeLoan)), exportedAt = now)
        val badDate = FinanceBackupCodec.decode(json.replace("\"start\": \"2026-12-05\"", "\"start\": \"someday\""))
        assertEquals(listOf("A loan has an invalid date in \"start\"."), badDate.errors)
        val badNumber = FinanceBackupCodec.decode(json.replace("\"principal\": 100000000", "\"principal\": \"lots\""))
        assertEquals(listOf("A loan has an invalid \"principal\"."), badNumber.errors)
        val notAnObject = FinanceBackupCodec.decode(json.replace(Regex("\"loan\": \\{[^}]*\\}"), "\"loan\": 7"))
        assertEquals(listOf("An account has an invalid \"loan\"."), notAnObject.errors)
        val noRate = FinanceBackupCodec.decode(json.replace(Regex("\"rateBp\": \\d+,\\s*"), ""))
        assertEquals(listOf("A loan has an invalid \"rateBp\"."), noRate.errors)
    }

    @Test
    fun `a null loan is no loan`() {
        val json = FinanceBackupCodec.encode(snapshotOf(account("Savings", AccountKind.Savings)), exportedAt = now)
            .replace("\"cardId\": null", "\"cardId\": null, \"loan\": null")
        val read = FinanceBackupCodec.decode(json)
        assertTrue(read.isValid, read.errors.toString())
        assertNull(read.snapshot!!.accounts.single().loan)
    }

    @Test
    fun `the kind loan needs its details, and a card can't be a loan`() {
        val withoutDetails = decoded(snapshotOf(account("Home loan", AccountKind.Loan)))
        assertEquals(listOf("Account \"Home loan\" is a loan but has no loan details."), withoutDetails.errors)
        assertEquals(listOf("Account \"Home loan\" has loan details but is a card."), refusedFor(homeLoan, AccountKind.Card))
    }

    @Test
    fun `a refused loan writes nothing when it is restored`() = runDb {
        val keep = repo.saveAccount(account("Savings", AccountKind.Savings, opening = 12_345))
        val bad = snapshotOf(account("Home loan", AccountKind.Loan, homeLoan.copy(rateBp = 20_000)))
        val failure = assertFailsWith<FinanceValidationException> { repo.restore(bad) }
        assertEquals("Account \"Home loan\" has a loan interest rate outside 0% to 100% a year.", failure.message)
        assertEquals(keep, repo.accounts().first { it.name == "Savings" }, "the ledger is as it was")
        assertNotNull(repo.accounts().firstOrNull { it.name == "Cash" })
        assertTrue(repo.accounts().none { it.isLoan })
    }
}
