package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.Tag
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tags in a backup (format 5, 2026-11-15): a top-level "tags" list and, on a transaction that has any, a "tags" list of their ids. A
 * file of version 1 to 4 - which has neither - is read and restored exactly as before; a version 5 file round-trips every tag's id and
 * name and every link; a file whose tags don't hold (two of one name, a name too long, a transaction naming a tag the file doesn't have,
 * the same tag twice, a transfer with tags) is refused with what is wrong, and nothing is written; a file newer than 5 is refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TagBackupTest {
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

    private suspend fun category(name: String): Category = repo.categories().first { it.name == name }

    private suspend fun cash(): Account = repo.accounts().first { it.kind == AccountKind.Cash }

    private suspend fun add(type: TransactionType, amount: Long, tags: Set<Long> = emptySet(), payee: String? = null, at: LocalDateTime = now): FinanceTransaction {
        val category = when (type) {
            TransactionType.Expense -> category("Health").id
            TransactionType.Income -> category("Salary").id
            TransactionType.Transfer -> null
        }
        return repo.saveTransaction(
            FinanceTransaction(
                type = type, amount = amount, accountId = cash().id!!, categoryId = category, occurredAt = at, payee = payee,
                createdAt = now, updatedAt = now, tagIds = tags,
                toAccountId = if (type == TransactionType.Transfer) repo.saveAccount(Account(name = "Savings", kind = AccountKind.Savings, createdAt = now, updatedAt = now)).id else null,
            ),
        )
    }

    private fun encode(snapshot: FinanceSnapshot) = FinanceBackupCodec.encode(snapshot, exportedAt = now)

    // --------------------------------------------------------------------------------------------- the format

    @Test
    fun `tags came with format 5, and a file newer than the current format is refused`() {
        assertTrue(FinanceBackupCodec.FORMAT_VERSION >= 5)
        val current = FinanceBackupCodec.FORMAT_VERSION
        val json = encode(FinanceSnapshot("INR", accounts = emptyList(), categories = emptyList(), transactions = emptyList(), budgets = emptyList(), recurringRules = emptyList()))
        assertTrue("\"version\": $current" in json)
        assertTrue(FinanceBackupCodec.decode(json).isValid)
        val newer = FinanceBackupCodec.decode(json.replace("\"version\": $current", "\"version\": ${current + 1}"))
        assertEquals(listOf("This backup was made by a newer version of NormPix. Update the app, then try again."), newer.errors)
    }

    @Test
    fun `tags are written at the top and on a tagged transaction only`() = runDb {
        val tax = repo.saveTag(Tag(name = "Tax-deductible", createdAt = now))
        val trip = repo.saveTag(Tag(name = "Goa trip", createdAt = now))
        add(TransactionType.Expense, 45_000, setOf(tax.id!!, trip.id!!), payee = "Apollo")
        add(TransactionType.Expense, 1_000, payee = "Chai")
        val json = encode(repo.snapshot())
        assertTrue("\"tags\": [" in json)
        assertTrue("\"name\": \"Tax-deductible\"" in json && "\"name\": \"Goa trip\"" in json)
        // One "tags" at the top and one on Apollo: the untagged transaction is written as it always was.
        assertEquals(2, Regex("\"tags\":").findAll(json).count(), json)
    }

    // ------------------------------------------------------------------------------------- the round trip

    @Test
    fun `a format 5 file round-trips every tag, its id and name, and every link`() = runDb {
        val tax = repo.saveTag(Tag(name = "Tax-deductible", createdAt = now))
        val reimbursable = repo.saveTag(Tag(name = "Reimbursable", createdAt = now))
        repo.saveTag(Tag(name = "Unused", createdAt = now))
        val apollo = add(TransactionType.Expense, 45_000, setOf(tax.id!!), payee = "Apollo")
        val cab = add(TransactionType.Expense, 32_000, setOf(tax.id!!, reimbursable.id!!), payee = "Uber")
        val refund = add(TransactionType.Income, 32_000, setOf(reimbursable.id!!), payee = "Office")
        add(TransactionType.Expense, 1_000, payee = "Chai")
        val before = repo.snapshot()

        val read = FinanceBackupCodec.decode(encode(before))
        assertTrue(read.isValid, read.errors.toString())
        val snapshot = read.snapshot!!
        assertEquals(before.tags, snapshot.tags, "every tag with its id, name and when it was made")
        assertEquals(before.transactions, snapshot.transactions, "every transaction with its tags")

        repo.resetAll()
        assertTrue(repo.tags().isEmpty())
        repo.restore(snapshot)
        assertEquals(before.tags, repo.tags())
        assertEquals(setOf(tax.id), repo.transactionById(apollo.id!!)!!.tagIds)
        assertEquals(setOf(tax.id, reimbursable.id), repo.transactionById(cab.id!!)!!.tagIds)
        assertEquals(setOf(reimbursable.id), repo.transactionById(refund.id!!)!!.tagIds)
        assertEquals(before.transactions, repo.snapshot().transactions, "the same ledger, links and all")
    }

    @Test
    fun `an undo puts the tags and the links back`() = runDb {
        val tax = repo.saveTag(Tag(name = "Tax-deductible", createdAt = now))
        val apollo = add(TransactionType.Expense, 45_000, setOf(tax.id!!), payee = "Apollo")
        val before = repo.snapshot()
        repo.deleteTag(tax.id!!)
        repo.deleteTransaction(apollo.id!!)
        repo.restore(before)
        assertEquals(listOf(tax), repo.tags())
        assertEquals(setOf(tax.id), repo.transactionById(apollo.id!!)!!.tagIds)
    }

    @Test
    fun `the CSV has a Tags column with each transaction's tag names`() = runDb {
        val tax = repo.saveTag(Tag(name = "Tax-deductible", createdAt = now))
        val reimbursable = repo.saveTag(Tag(name = "Reimbursable", createdAt = now))
        add(TransactionType.Expense, 45_000, setOf(tax.id!!, reimbursable.id!!), payee = "Apollo")
        add(TransactionType.Expense, 1_000, payee = "Chai")
        val csv = FinanceBackupCodec.encodeCsv(repo.snapshot()) { it.toString() }.trim().lines()
        assertTrue(csv.first().endsWith(",Person,Tags"), csv.first())
        assertTrue(csv.any { it.contains("Apollo") && it.endsWith(",Reimbursable; Tax-deductible") }, csv.toString())
        assertTrue(csv.any { it.contains("Chai") && it.endsWith(",,") }, csv.toString())
    }

    // ------------------------------------------------------------------------------------ the older files

    /** A file as the release before tags wrote it (format 4): a loan, people, and no "tags" anywhere. */
    private val formatFour = """
        {
          "format": "normpix-finance-backup",
          "version": 4,
          "exportedAt": "2026-11-15T08:00:00.000",
          "currency": "INR",
          "settings": { "defaultAccountId": 1 },
          "accounts": [
            { "id": 1, "name": "Cash", "kind": "cash", "openingBalance": 250000, "archived": false, "sortOrder": 0,
              "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000", "bankKey": null, "creditLimit": null,
              "statementDay": null, "dueDay": null, "numberEnding": null, "numberStart": null, "cardId": null },
            { "id": 2, "name": "Home Loan", "kind": "loan", "openingBalance": -100000000, "archived": false, "sortOrder": 1,
              "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000", "bankKey": null, "creditLimit": null,
              "statementDay": null, "dueDay": null, "numberEnding": null, "numberStart": null, "cardId": null,
              "loan": { "principal": 100000000, "rateBp": 875, "tenureMonths": 180, "emi": 999461, "start": "2026-10-05" } }
          ],
          "banks": [],
          "people": [
            { "id": 1, "name": "Mom", "emoji": null, "color": 6, "archived": false,
              "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000" }
          ],
          "categories": [
            { "id": 1, "name": "Health", "type": "expense", "icon": "health", "color": 3, "archived": false, "parentId": null,
              "sortOrder": 1, "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000" },
            { "id": 2, "name": "Salary", "type": "income", "icon": "salary", "color": 4, "archived": false, "parentId": null,
              "sortOrder": 2, "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000" }
          ],
          "transactions": [
            { "id": 1, "type": "expense", "amount": 45000, "accountId": 1, "toAccountId": null, "categoryId": 1,
              "occurredAt": "2026-09-01T10:00:00.000", "payee": "Apollo", "note": null, "recurringId": null, "personId": 1,
              "picture": null, "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000" },
            { "id": 2, "type": "income", "amount": 9500000, "accountId": 1, "toAccountId": null, "categoryId": 2,
              "occurredAt": "2026-09-01T11:00:00.000", "payee": "Acme", "note": null, "recurringId": null, "personId": null,
              "picture": null, "createdAt": "2026-09-01T11:00:00.000", "updatedAt": "2026-09-01T11:00:00.000" },
            { "id": 3, "type": "transfer", "amount": 250000, "accountId": 1, "toAccountId": 2, "categoryId": null,
              "occurredAt": "2026-10-05T10:00:00.000", "payee": null, "note": "EMI Oct 2026 - principal", "recurringId": null, "personId": null,
              "picture": null, "createdAt": "2026-10-05T10:00:00.000", "updatedAt": "2026-10-05T10:00:00.000" }
          ],
          "budgets": [ { "id": 1, "categoryId": 1, "amount": 1200000, "effectiveMonth": 202609 } ],
          "recurringRules": []
        }
    """.trimIndent()

    @Test
    fun `a format 4 file reads and restores exactly as before, with no tags`() = runDb {
        val read = FinanceBackupCodec.decode(formatFour)
        assertTrue(read.isValid, read.errors.toString())
        val snapshot = read.snapshot!!
        assertTrue(snapshot.tags.isEmpty())
        assertTrue(snapshot.transactions.all { it.tagIds.isEmpty() })

        // A ledger with tags in it is replaced whole: what was there goes, as with every restore.
        repo.saveTag(Tag(name = "Old tag", createdAt = now))
        repo.restore(snapshot)
        assertTrue(repo.tags().isEmpty())
        val restored = repo.snapshot()
        assertEquals(snapshot.accounts, restored.accounts)
        assertEquals(snapshot.people, restored.people)
        assertEquals(snapshot.transactions.sortedBy { it.id }, restored.transactions.sortedBy { it.id }, "every transaction exactly as the file has it")
        assertEquals(listOf(1L, 2L, 3L), restored.transactions.mapNotNull { it.id }.sorted())
        assertTrue(restored.transactions.all { it.tagIds.isEmpty() })
        assertEquals(-100_000_000L + 250_000L, repo.accountBalances()[2L], "the loan as it was, its EMI paid")
    }

    @Test
    fun `a format 1, 2 or 3 file reads as it did, with no tags`() {
        // The same file with no loan in it, as those versions had none.
        val noLoan = formatFour
            .replace("\"kind\": \"loan\"", "\"kind\": \"other\"")
            .replace(Regex(",\\s*\"loan\": \\{[^}]*\\}"), "")
        for (version in 1..3) {
            val read = FinanceBackupCodec.decode(noLoan.replace("\"version\": 4", "\"version\": $version"))
            assertTrue(read.isValid, "version $version: ${read.errors}")
            assertTrue(read.snapshot!!.tags.isEmpty())
            assertTrue(read.snapshot!!.transactions.all { it.tagIds.isEmpty() })
            assertEquals(3, read.snapshot!!.transactions.size)
        }
    }

    @Test
    fun `a tag written without its date is read as made when the file was`() {
        val json = formatFour.replace("\"version\": 4", "\"version\": 5")
            .replace("\"recurringRules\": []", "\"recurringRules\": [], \"tags\": [ { \"id\": 7, \"name\": \"Tax-deductible\" } ]")
            .replace("\"personId\": 1,", "\"personId\": 1, \"tags\": [7],")
        val read = FinanceBackupCodec.decode(json)
        assertTrue(read.isValid, read.errors.toString())
        assertEquals(listOf(Tag(7, "Tax-deductible", LocalDateTime.of(2026, 11, 15, 8, 0))), read.snapshot!!.tags)
        assertEquals(setOf(7L), read.snapshot!!.transactions.first { it.id == 1L }.tagIds)
    }

    // ------------------------------------------------------------------------------------------- refused

    private fun withTags(tags: String, onApollo: String = "[7]", onTransfer: String? = null): BackupReadResult {
        var json = formatFour.replace("\"version\": 4", "\"version\": 5")
            .replace("\"recurringRules\": []", "\"recurringRules\": [], \"tags\": $tags")
            .replace("\"personId\": 1,", "\"personId\": 1, \"tags\": $onApollo,")
        if (onTransfer != null) json = json.replace("\"note\": \"EMI Oct 2026 - principal\",", "\"note\": \"EMI Oct 2026 - principal\", \"tags\": $onTransfer,")
        return FinanceBackupCodec.decode(json)
    }

    @Test
    fun `tags that don't hold make the backup refused, with what is wrong`() {
        val tax = """{ "id": 7, "name": "Tax-deductible", "createdAt": "2026-11-01T10:00:00.000" }"""
        assertTrue(withTags("[$tax]").isValid, withTags("[$tax]").errors.toString())

        assertEquals(
            listOf("The backup has two tags with the same name."),
            withTags("""[$tax, { "id": 8, "name": "tax-DEDUCTIBLE", "createdAt": "2026-11-01T10:00:00.000" }]""").errors,
        )
        assertEquals(
            listOf("Tag \"${"x".repeat(31)}\" has a name longer than 30 characters."),
            withTags("""[{ "id": 7, "name": "${"x".repeat(31)}", "createdAt": "2026-11-01T10:00:00.000" }]""").errors,
        )
        assertEquals(listOf("A tag has no name."), withTags("""[{ "id": 7, "name": "   ", "createdAt": "2026-11-01T10:00:00.000" }]""").errors)
        assertEquals(listOf("Transaction #1 refers to a missing tag."), withTags("[$tax]", onApollo = "[7, 99]").errors)
        assertEquals(listOf("Transaction #1 refers to a missing tag."), withTags("[]", onApollo = "[7]").errors)
        assertEquals(listOf("Transaction #1 has the same tag twice."), withTags("[$tax]", onApollo = "[7, 7]").errors)
        assertEquals(listOf("Transaction #3 is a transfer with tags."), withTags("[$tax]", onTransfer = "[7]").errors)
        assertEquals(
            listOf("The backup has missing or duplicate tag ids."),
            withTags("""[$tax, { "id": 7, "name": "Trip", "createdAt": "2026-11-01T10:00:00.000" }]""").errors,
        )
        assertEquals(listOf("Transaction #1 has an invalid \"tags\"."), withTags("[$tax]", onApollo = "[\"7\"]").errors)
        assertEquals(listOf("Transaction #1 has an invalid \"tags\"."), withTags("[$tax]", onApollo = "7").errors)
        assertEquals(listOf("A tag has an invalid \"name\"."), withTags("""[{ "id": 7, "createdAt": "2026-11-01T10:00:00.000" }]""").errors)
        // Thirty characters, an emoji counted as one, is a name.
        assertTrue(withTags("""[{ "id": 7, "name": "${"x".repeat(29)}🧾", "createdAt": "2026-11-01T10:00:00.000" }]""").isValid)
    }

    @Test
    fun `a refused file writes nothing when it is restored`() = runDb {
        val tax = repo.saveTag(Tag(name = "Tax-deductible", createdAt = now))
        val apollo = add(TransactionType.Expense, 45_000, setOf(tax.id!!), payee = "Apollo")
        val before = repo.snapshot()
        // A transaction that names a tag the file doesn't have.
        val bad = before.copy(tags = emptyList())
        val failure = assertFailsWith<FinanceValidationException> { repo.restore(bad) }
        assertEquals("Transaction #${apollo.id} refers to a missing tag.", failure.message)
        assertEquals(before, repo.snapshot(), "the ledger is exactly as it was")
        assertFalse(repo.snapshot().tags.isEmpty())
    }

    @Test
    fun `the backup categories trimming leaves tags alone`() = runDb {
        val tax = repo.saveTag(Tag(name = "Tax-deductible", createdAt = now))
        add(TransactionType.Expense, 45_000, setOf(tax.id!!), payee = "Apollo")
        val trimmed = repo.snapshot().trimmedForBackup()
        assertEquals(listOf(tax), trimmed.tags)
        val read = FinanceBackupCodec.decode(encode(trimmed))
        assertTrue(read.isValid, read.errors.toString())
        repo.restore(read.snapshot!!)
        assertEquals(listOf(tax), repo.tags())
        assertEquals(CategoryType.Expense, repo.categories().first { it.name == "Health" }.type)
    }
}
