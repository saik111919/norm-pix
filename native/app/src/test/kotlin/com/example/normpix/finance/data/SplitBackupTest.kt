package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.SplitPart
import com.example.normpix.finance.domain.Tag
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Split payments in a backup (format 7, 2026-11-15): a transaction that is one part of a split payment carries `"splitGroup"`, the id its
 * parts share. A file of version 1 to 6 - which has none - is read and restored exactly as before; a version 7 file with a split
 * round-trips (the group, every part's own category, amount and note, and what they share); a group that doesn't hold together - one row,
 * more than ten, a mix of spending and income, a transfer, parts on two accounts, at two times, with two payees or for two people - is
 * refused with what is wrong and nothing is written; a group id that is no text is refused; and a file newer than 7 is refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SplitBackupTest {
    private val now = LocalDateTime.of(2026, 11, 15, 9, 0)
    private val at = LocalDateTime.of(2026, 11, 12, 18, 30)
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private fun encode(snapshot: FinanceSnapshot) = FinanceBackupCodec.encode(snapshot, exportedAt = now)

    private suspend fun category(name: String): Long = repo.categories().first { it.name == name }.id!!

    private val pic = "pic-11111111-2222-3333-4444-555555555555.jpg"

    private suspend fun aSplit(): List<FinanceTransaction> {
        val cash = repo.accounts().first { it.kind == AccountKind.Cash }.id!!
        val tax = repo.saveTag(Tag(name = "Tax-deductible", createdAt = now)).id!!
        val mom = repo.savePerson(Person(name = "Mom", createdAt = now, updatedAt = now)).id!!
        // A payment of its own beside it, so the file has rows with a group and rows without.
        repo.saveTransaction(
            FinanceTransaction(type = TransactionType.Expense, amount = 500, accountId = cash, categoryId = category("Health"), occurredAt = at.minusDays(1), createdAt = now, updatedAt = now),
        )
        return repo.saveSplit(
            FinanceTransaction(
                type = TransactionType.Expense, amount = 112_000, accountId = cash, occurredAt = at, payee = "DMart", createdAt = now, updatedAt = now,
                personId = mom, picture = pic, tagIds = setOf(tax),
            ),
            listOf(SplitPart(category("Groceries"), 82_000, "Rice and dal"), SplitPart(category("Shopping"), 30_000, "Detergent")),
        )
    }

    // --------------------------------------------------------------------------------------------- the format

    @Test
    fun `the format is 7, and a file newer than that is refused`() {
        assertEquals(7, FinanceBackupCodec.FORMAT_VERSION)
        val json = encode(FinanceSnapshot("INR", accounts = emptyList(), categories = emptyList(), transactions = emptyList(), budgets = emptyList(), recurringRules = emptyList()))
        assertTrue("\"version\": 7" in json)
        assertTrue(FinanceBackupCodec.decode(json).isValid)
        val newer = FinanceBackupCodec.decode(json.replace("\"version\": 7", "\"version\": 8"))
        assertEquals(listOf("This backup was made by a newer version of NormPix. Update the app, then try again."), newer.errors)
    }

    @Test
    fun `only a part of a split carries a splitGroup, and every part carries the same one`() = runDb {
        val parts = aSplit()
        val json = encode(repo.snapshot())
        val group = parts.first().splitGroup!!
        assertEquals(2, Regex("\"splitGroup\": \"$group\"").findAll(json).count(), json)
        assertEquals(2, Regex("\"splitGroup\"").findAll(json).count(), "the payment of its own has none")
    }

    @Test
    fun `a ledger with no split writes no splitGroup at all - the file is the one earlier releases wrote`() = runDb {
        val cash = repo.accounts().first { it.kind == AccountKind.Cash }.id!!
        repo.saveTransaction(
            FinanceTransaction(type = TransactionType.Expense, amount = 500, accountId = cash, categoryId = category("Health"), occurredAt = at, createdAt = now, updatedAt = now),
        )
        assertFalse("splitGroup" in encode(repo.snapshot()))
    }

    // ------------------------------------------------------------------------------------------------- the round trip

    @Test
    fun `a format 7 file round-trips a split - the group, each part's own fields and what they share - and puts it back after a reset`() = runDb {
        val parts = aSplit()
        val before = repo.snapshot()
        assertEquals(2, before.transactions.count { it.splitGroup != null })

        val read = FinanceBackupCodec.decode(encode(before))
        assertTrue(read.isValid, read.errors.toString())
        val snapshot = read.snapshot!!
        assertEquals(before.transactions, snapshot.transactions, "every transaction with its group")

        repo.resetAll()
        assertEquals(0, repo.transactions(TransactionQuery(), limit = 10).items.size)
        repo.restore(snapshot)
        val group = parts.first().splitGroup!!
        val restored = repo.splitParts(group)
        assertEquals(parts.map { it.id }, restored.map { it.id })
        assertEquals(listOf(82_000L, 30_000L), restored.map { it.amount })
        assertEquals(listOf("Rice and dal", "Detergent"), restored.map { it.note })
        assertEquals(setOf(pic), restored.map { it.picture }.toSet())
        assertEquals(1, restored.map { it.tagIds }.toSet().size)
        assertEquals(before, repo.snapshot(), "the same ledger, split and all")
    }

    @Test
    fun `a split in a ledger that is restored over another replaces it`() = runDb {
        val parts = aSplit()
        val backup = repo.snapshot()
        repo.deleteTransaction(parts.first().id!!)
        repo.saveSplit(
            FinanceTransaction(type = TransactionType.Expense, amount = 300, accountId = backup.accounts.first().id!!, occurredAt = at, createdAt = now, updatedAt = now),
            listOf(SplitPart(category("Health"), 100), SplitPart(category("Health"), 200)),
        )
        val other = repo.snapshot().transactions.mapNotNull { it.splitGroup }.distinct().single { it != parts.first().splitGroup }
        repo.restore(FinanceBackupCodec.decode(encode(backup)).snapshot!!)
        assertEquals(0, repo.splitParts(other).size, "the other split is gone")
        assertEquals(2, repo.splitParts(parts.first().splitGroup!!).size)
    }

    // ------------------------------------------------------------------------------------------------ the older files

    /** A row of the file's "transactions" list. */
    private fun row(
        id: Int,
        type: String = "expense",
        amount: Int = 1000,
        account: Int = 1,
        to: String = "null",
        category: String = "1",
        at: String = "2026-09-01T10:00:00.000",
        payee: String = "\"DMart\"",
        person: String = "null",
        group: String? = null,
    ) = """
        { "id": $id, "type": "$type", "amount": $amount, "accountId": $account, "toAccountId": $to, "categoryId": $category,
          "occurredAt": "$at", "payee": $payee, "note": null, "recurringId": null, "personId": $person, "picture": null,
          "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000"${if (group != null) ", \"splitGroup\": $group" else ""} }
    """.trimIndent()

    /** A file of the version [version] with two accounts, an expense category and an income category, a person, and these rows. */
    private fun file(version: Int, vararg rows: String): String = """
        {
          "format": "normpix-finance-backup",
          "version": $version,
          "exportedAt": "2026-11-15T08:00:00.000",
          "currency": "INR",
          "settings": { "defaultAccountId": 1 },
          "accounts": [
            { "id": 1, "name": "Cash", "kind": "cash", "openingBalance": 250000, "archived": false, "sortOrder": 0,
              "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000", "bankKey": null, "creditLimit": null,
              "statementDay": null, "dueDay": null, "numberEnding": null, "numberStart": null, "cardId": null },
            { "id": 2, "name": "SBI Savings", "kind": "savings", "openingBalance": 4500000, "archived": false, "sortOrder": 1,
              "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000", "bankKey": null, "creditLimit": null,
              "statementDay": null, "dueDay": null, "numberEnding": null, "numberStart": null, "cardId": null }
          ],
          "banks": [],
          "people": [
            { "id": 1, "name": "Mom", "emoji": null, "color": 0, "archived": false, "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000" },
            { "id": 2, "name": "Dad", "emoji": null, "color": 1, "archived": false, "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000" }
          ],
          "categories": [
            { "id": 1, "name": "Health", "type": "expense", "icon": "health", "color": 3, "archived": false, "parentId": null,
              "sortOrder": 1, "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000" },
            { "id": 2, "name": "Salary", "type": "income", "icon": "salary", "color": 1, "archived": false, "parentId": null,
              "sortOrder": 2, "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000" }
          ],
          "transactions": [ ${rows.joinToString(",\n")} ],
          "budgets": [],
          "recurringRules": []
        }
    """.trimIndent()

    @Test
    fun `a file of format 1 to 6 reads and restores exactly as before, with nothing split`() = runDb {
        for (version in 1..6) {
            val read = FinanceBackupCodec.decode(file(version, row(1), row(2, amount = 2000)))
            assertTrue(read.isValid, "version $version: ${read.errors}")
            val snapshot = read.snapshot!!
            assertTrue(snapshot.transactions.all { it.splitGroup == null }, "version $version has no split")
            repo.resetAll()
            repo.restore(snapshot)
            val restored = repo.snapshot()
            assertEquals(snapshot.accounts, restored.accounts)
            assertEquals(snapshot.transactions.sortedBy { it.id }, restored.transactions.sortedBy { it.id }, "every transaction exactly as the file has it")
            assertEquals(0L, repo.transactions(TransactionQuery(), limit = 10).items.count { it.splitGroup != null }.toLong())
        }
    }

    @Test
    fun `a splitGroup that is null is no split`() {
        val read = FinanceBackupCodec.decode(file(7, row(1, group = "null"), row(2)))
        assertTrue(read.isValid, read.errors.toString())
        assertTrue(read.snapshot!!.transactions.all { it.splitGroup == null })
    }

    // ----------------------------------------------------------------------------------------------- groups that hold

    private fun valid(vararg rows: String): BackupReadResult = FinanceBackupCodec.decode(file(7, *rows))

    @Test
    fun `groups that hold together are accepted, from two parts to ten, spending or income`() {
        val two = valid(row(1, group = "\"g1\""), row(2, amount = 500, group = "\"g1\""))
        assertTrue(two.isValid, two.errors.toString())
        assertEquals(listOf("g1", "g1"), two.snapshot!!.transactions.map { it.splitGroup })

        val ten = valid(*Array(10) { row(it + 1, group = "\"ten\"") })
        assertTrue(ten.isValid, ten.errors.toString())

        val income = valid(row(1, type = "income", category = "2", payee = "null", group = "\"inc\""), row(2, type = "income", category = "2", payee = "null", group = "\"inc\""))
        assertTrue(income.isValid, income.errors.toString())

        // No payee on any part, and one person on all of an expense's.
        val person = valid(row(1, payee = "null", person = "1", group = "\"p\""), row(2, payee = "null", person = "1", group = "\"p\""))
        assertTrue(person.isValid, person.errors.toString())

        // Two groups, a part of each in between, and rows with none.
        val many = valid(row(1, group = "\"a\""), row(2, group = "\"b\"", payee = "\"Other\""), row(3, group = "\"a\""), row(4, group = "\"b\"", payee = "\"Other\""), row(5))
        assertTrue(many.isValid, many.errors.toString())
        assertEquals(listOf("a", "b", "a", "b", null), many.snapshot!!.transactions.map { it.splitGroup })

        // The same payee in another spelling of "none" - a blank one and a missing one - is the same payee.
        val blank = valid(row(1, payee = "null", group = "\"blank\""), row(2, payee = "\"\"", group = "\"blank\""))
        assertTrue(blank.isValid, blank.errors.toString())
    }

    private fun refused(vararg rows: String): List<String> {
        val read = valid(*rows)
        assertFalse(read.isValid, "should be refused")
        assertNull(read.snapshot)
        return read.errors
    }

    @Test
    fun `a group of one row is refused`() {
        assertEquals(listOf("A split payment (transaction #1) has only one part."), refused(row(1, group = "\"g\""), row(2)))
    }

    @Test
    fun `a group of eleven rows is refused`() {
        assertEquals(listOf("A split payment (transaction #1) has more than 10 parts."), refused(*Array(11) { row(it + 1, group = "\"g\"") }))
    }

    @Test
    fun `a group that mixes spending and income is refused`() {
        assertEquals(
            listOf("A split payment (transaction #1) mixes spending and income."),
            refused(row(1, group = "\"g\""), row(2, type = "income", category = "2", group = "\"g\"")),
        )
    }

    @Test
    fun `a transfer in a group is refused`() {
        assertEquals(
            listOf("A split payment (transaction #1) includes a transfer."),
            refused(row(1, group = "\"g\""), row(2, type = "transfer", to = "2", category = "null", group = "\"g\"")),
        )
        // A group of transfers only is refused for the same reason.
        assertEquals(
            listOf("A split payment (transaction #1) includes a transfer."),
            refused(
                row(1, type = "transfer", to = "2", category = "null", group = "\"g\""),
                row(2, type = "transfer", to = "2", category = "null", group = "\"g\""),
            ),
        )
    }

    @Test
    fun `a group on two accounts, at two times, with two payees or for two people is refused`() {
        assertEquals(listOf("A split payment (transaction #1) is on more than one account."), refused(row(1, group = "\"g\""), row(2, account = 2, group = "\"g\"")))
        assertEquals(
            listOf("A split payment (transaction #1) is dated at more than one time."),
            refused(row(1, group = "\"g\""), row(2, at = "2026-09-01T10:01:00.000", group = "\"g\"")),
        )
        assertEquals(
            listOf("A split payment (transaction #1) is dated at more than one time."),
            refused(row(1, group = "\"g\""), row(2, at = "2026-09-02T10:00:00.000", group = "\"g\"")),
        )
        assertEquals(
            listOf("A split payment (transaction #1) names more than one payee."),
            refused(row(1, group = "\"g\""), row(2, payee = "\"Reliance\"", group = "\"g\"")),
        )
        assertEquals(
            listOf("A split payment (transaction #1) names more than one payee."),
            refused(row(1, group = "\"g\""), row(2, payee = "null", group = "\"g\"")),
        )
        assertEquals(
            listOf("A split payment (transaction #1) is for more than one person."),
            refused(row(1, person = "1", group = "\"g\""), row(2, person = "2", group = "\"g\"")),
        )
        assertEquals(
            listOf("A split payment (transaction #1) is for more than one person."),
            refused(row(1, person = "1", group = "\"g\""), row(2, group = "\"g\"")),
        )
    }

    @Test
    fun `each broken group is named by its first row, and a good group beside it is not mentioned`() {
        val errors = refused(
            row(1, group = "\"good\""), row(2, group = "\"good\""),
            row(3, group = "\"alone\""),
            row(4, group = "\"mixed\""), row(5, account = 2, group = "\"mixed\""),
        )
        assertEquals(
            setOf("A split payment (transaction #3) has only one part.", "A split payment (transaction #4) is on more than one account."),
            errors.toSet(),
        )
    }

    @Test
    fun `a group id that is no text of one to 64 characters is a damaged file`() {
        for (bad in listOf("5", "true", "[\"a\"]", "{}", "\"\"", "\"   \"", "\"${"x".repeat(65)}\"")) {
            val read = valid(row(1, group = bad), row(2, group = bad))
            assertFalse(read.isValid, "$bad should be refused")
            assertEquals(listOf("Transaction #1 has an invalid \"splitGroup\"."), read.errors, bad)
        }
        val longest = "x".repeat(64)
        assertTrue(valid(row(1, group = "\"$longest\""), row(2, group = "\"$longest\"")).isValid, "64 characters are fine")
    }

    // ------------------------------------------------------------------ nothing is written for a refused ledger

    @Test
    fun `restoring a ledger with a group that doesn't hold is refused and changes nothing`() = runDb {
        aSplit()
        val before = repo.snapshot()
        val good = FinanceBackupCodec.decode(file(7, row(1), row(2))).snapshot!!
        val broken = good.copy(transactions = good.transactions.mapIndexed { i, t -> if (i == 0) t.copy(splitGroup = "alone") else t })
        assertFailsWith<FinanceValidationException> { repo.restore(broken) }
        assertEquals(before, repo.snapshot(), "nothing was written")
    }
}
