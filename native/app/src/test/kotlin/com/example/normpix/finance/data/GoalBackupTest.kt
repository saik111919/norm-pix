package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.SavingsGoal
import com.example.normpix.finance.domain.Tag
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDate
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
 * Goals in a backup (format 6, 2026-11-15): a top-level "goals" list, the account by its id. A file of version 1 to 5 - which has none -
 * is read and restored exactly as before; a version 6 file round-trips both kinds of goal (one that counts an account, one that keeps its
 * own amount) with every field; a file whose goals don't hold (a target of zero, an account the file doesn't have, a saved amount below
 * zero, a date that is no date, a name that is empty or too long, a picture that is not one emoji, the same id twice) is refused with
 * what is wrong, and nothing is written; a restore over a ledger that has goals replaces them; and a file newer than 6 is refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoalBackupTest {
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

    private fun rupees(amount: Long) = amount * 100

    private fun encode(snapshot: FinanceSnapshot) = FinanceBackupCodec.encode(snapshot, exportedAt = now)

    private suspend fun savings(name: String = "SBI Savings"): Account =
        repo.saveAccount(Account(name = name, kind = AccountKind.Savings, openingBalance = rupees(45_000), createdAt = now, updatedAt = now))

    private suspend fun both(): Pair<SavingsGoal, SavingsGoal> {
        val account = savings()
        val linked = repo.saveGoal(
            SavingsGoal(name = "Emergency fund", target = rupees(3_00_000), targetDate = LocalDate.of(2027, 12, 31), emoji = "💰", accountId = account.id, createdAt = now, updatedAt = now),
        )
        val own = repo.saveGoal(
            SavingsGoal(name = "Trip to Goa", target = rupees(40_000), targetDate = LocalDate.of(2027, 3, 15), emoji = "🌴", saved = rupees(18_000), createdAt = now, updatedAt = now),
        )
        return linked to own
    }

    // --------------------------------------------------------------------------------------------- the format

    @Test
    fun `goals arrived with format 6, and a file newer than the current one is refused`() {
        // Goals came with format 6; later formats keep them, so this reads the current version instead of pinning 6.
        val current = FinanceBackupCodec.FORMAT_VERSION
        assertTrue(current >= 6)
        val json = encode(FinanceSnapshot("INR", accounts = emptyList(), categories = emptyList(), transactions = emptyList(), budgets = emptyList(), recurringRules = emptyList()))
        assertTrue("\"version\": $current" in json)
        assertTrue(FinanceBackupCodec.decode(json).isValid)
        val newer = FinanceBackupCodec.decode(json.replace("\"version\": $current", "\"version\": ${current + 1}"))
        assertEquals(listOf("This backup was made by a newer version of NormPix. Update the app, then try again."), newer.errors)
    }

    @Test
    fun `goals are written at the top with every field, the account by its id, the date as a day`() = runDb {
        val (linked, own) = both()
        val json = encode(repo.snapshot())
        assertTrue("\"goals\": [" in json)
        assertEquals(1, Regex("\"goals\":").findAll(json).count())
        for (text in listOf(
            "\"name\": \"Emergency fund\"", "\"name\": \"Trip to Goa\"", "\"targetDate\": \"2027-12-31\"", "\"targetDate\": \"2027-03-15\"",
            "\"emoji\": \"💰\"", "\"emoji\": \"🌴\"", "\"accountId\": ${linked.accountId}", "\"saved\": ${rupees(18_000)}", "\"target\": ${rupees(40_000)}",
        )) assertTrue(text in json, "$text in:\n$json")
        assertEquals(setOf(linked.id, own.id), Regex("\"id\": (\\d+),\\s*\"name\": \"(Emergency fund|Trip to Goa)\"").findAll(json).map { it.groupValues[1].toLong() }.toSet())
    }

    @Test
    fun `a ledger with no goals writes an empty list`() {
        val json = encode(FinanceSnapshot("INR", accounts = emptyList(), categories = emptyList(), transactions = emptyList(), budgets = emptyList(), recurringRules = emptyList()))
        assertTrue(Regex("\"goals\": \\[\\s*]").containsMatchIn(json), json)
        assertTrue(FinanceBackupCodec.decode(json).snapshot!!.goals.isEmpty())
    }

    // ------------------------------------------------------------------------------------------------- the round trip

    @Test
    fun `a format 6 file round-trips both kinds of goal, every field, and puts them back after a reset`() = runDb {
        val (linked, own) = both()
        val before = repo.snapshot()
        assertEquals(listOf(linked, own), before.goals)

        val read = FinanceBackupCodec.decode(encode(before))
        assertTrue(read.isValid, read.errors.toString())
        val snapshot = read.snapshot!!
        assertEquals(before.goals, snapshot.goals, "every goal with its id, name, target, date, emoji, account, amount, place and times")

        repo.resetAll()
        assertTrue(repo.goals().isEmpty())
        repo.restore(snapshot)
        assertEquals(before.goals, repo.goals())
        val restored = repo.goals()
        assertEquals(linked.accountId, restored.first { it.name == "Emergency fund" }.accountId)
        assertNull(restored.first { it.name == "Trip to Goa" }.accountId)
        assertEquals(rupees(18_000), restored.first { it.name == "Trip to Goa" }.saved)
        assertEquals(before, repo.snapshot(), "the same ledger, goals and all")
    }

    @Test
    fun `a goal with no date, no picture and no amount round-trips as that`() = runDb {
        repo.saveGoal(SavingsGoal(name = "Someday", target = 100, createdAt = now, updatedAt = now))
        val before = repo.snapshot()
        val snapshot = FinanceBackupCodec.decode(encode(before)).snapshot!!
        assertEquals(before.goals, snapshot.goals)
        val goal = snapshot.goals.single()
        assertNull(goal.targetDate)
        assertNull(goal.emoji)
        assertNull(goal.accountId)
        assertEquals(0L, goal.saved)
    }

    @Test
    fun `a restore over a ledger that has goals replaces them`() = runDb {
        both()
        val file = repo.snapshot()
        // A different ledger now: its own goals, one the file doesn't have.
        repo.resetAll()
        repo.saveGoal(SavingsGoal(name = "Laptop", target = rupees(80_000), saved = rupees(5_000), createdAt = now, updatedAt = now))
        repo.saveGoal(SavingsGoal(name = "Bike", target = rupees(60_000), createdAt = now, updatedAt = now))
        assertEquals(2, repo.goals().size)

        repo.restore(file)
        assertEquals(listOf("Emergency fund", "Trip to Goa"), repo.goals().map { it.name }, "the file's goals, and only those")
        assertEquals(file.goals, repo.goals())
    }

    @Test
    fun `a file with no goals restores a ledger without them - what was there goes`() = runDb {
        both()
        val without = repo.snapshot().copy(goals = emptyList())
        repo.restore(without)
        assertTrue(repo.goals().isEmpty())
    }

    @Test
    fun `a linked goal written with an amount of its own is restored with none`() = runDb {
        val account = savings()
        // As a hand-made file might have it: the account's balance is what is saved, so the amount is not kept.
        val json = base(
            """[ { "id": 1, "name": "Fund", "target": 5000000, "targetDate": null, "emoji": null, "accountId": ${account.id}, "saved": 99900,
                   "sortOrder": 0, "createdAt": "2026-11-01T10:00:00.000", "updatedAt": "2026-11-01T10:00:00.000" } ]""",
            account.id!!,
        )
        val read = FinanceBackupCodec.decode(json)
        assertTrue(read.isValid, read.errors.toString())
        repo.restore(read.snapshot!!)
        val goal = repo.goals().single()
        assertEquals(account.id, goal.accountId)
        assertEquals(0L, goal.saved)
        // Then deleting the account leaves it on its own with nothing, as in the app.
        repo.deleteAccount(account.id!!)
        assertEquals(0L, repo.goals().single().saved)
        assertNull(repo.goals().single().accountId)
    }

    @Test
    fun `the backup categories trimming leaves goals alone`() = runDb {
        both()
        val trimmed = repo.snapshot().trimmedForBackup()
        assertEquals(2, trimmed.goals.size)
        val read = FinanceBackupCodec.decode(encode(trimmed))
        assertTrue(read.isValid, read.errors.toString())
        repo.restore(read.snapshot!!)
        assertEquals(trimmed.goals, repo.goals())
    }

    // ------------------------------------------------------------------------------------------------ the older files

    /** A ledger as an older release wrote it (the version is the caller's) with two accounts and a transaction, and no "goals" key. */
    private fun older(version: Int, withTags: Boolean = false): String = """
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
          "people": [],
          ${if (withTags) "\"tags\": [ { \"id\": 1, \"name\": \"Tax-deductible\", \"createdAt\": \"2026-09-01T10:00:00.000\" } ]," else ""}
          "categories": [
            { "id": 1, "name": "Health", "type": "expense", "icon": "health", "color": 3, "archived": false, "parentId": null,
              "sortOrder": 1, "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000" }
          ],
          "transactions": [
            { "id": 1, "type": "expense", "amount": 45000, "accountId": 1, "toAccountId": null, "categoryId": 1,
              "occurredAt": "2026-09-01T10:00:00.000", "payee": "Apollo", "note": null, "recurringId": null, "personId": null,
              "picture": null, "createdAt": "2026-09-01T10:00:00.000", "updatedAt": "2026-09-01T10:00:00.000"${if (withTags) ", \"tags\": [1]" else ""} }
          ],
          "budgets": [ { "id": 1, "categoryId": 1, "amount": 1200000, "effectiveMonth": 202609 } ],
          "recurringRules": []
        }
    """.trimIndent()

    @Test
    fun `a file of format 1 to 5 reads and restores exactly as before, with no goals`() = runDb {
        for (version in 1..5) {
            // Tags came with 5; the older files have none.
            val read = FinanceBackupCodec.decode(older(version, withTags = version == 5))
            assertTrue(read.isValid, "version $version: ${read.errors}")
            val snapshot = read.snapshot!!
            assertTrue(snapshot.goals.isEmpty(), "version $version has no goals")
            assertEquals(2, snapshot.accounts.size)
            assertEquals(1, snapshot.transactions.size)
            assertEquals(if (version == 5) 1 else 0, snapshot.tags.size)

            // A ledger with goals in it is replaced whole: what was there goes, as with every restore.
            repo.resetAll()
            repo.saveGoal(SavingsGoal(name = "Old goal", target = 100, createdAt = now, updatedAt = now))
            repo.restore(snapshot)
            assertTrue(repo.goals().isEmpty(), "version $version: the goal that was there is gone")
            val restored = repo.snapshot()
            assertEquals(snapshot.accounts, restored.accounts)
            assertEquals(snapshot.transactions, restored.transactions, "every transaction exactly as the file has it")
            assertEquals(snapshot.budgets, restored.budgets)
            assertEquals(rupees(45_000), repo.accountBalances()[2L])
        }
    }

    @Test
    fun `a goals key that is null or an empty list is no goals`() {
        for (goals in listOf("null", "[]")) {
            val read = FinanceBackupCodec.decode(base(goals, 2))
            assertTrue(read.isValid, read.errors.toString())
            assertTrue(read.snapshot!!.goals.isEmpty())
        }
    }

    // ------------------------------------------------------------------------------------------------------- refused

    /** A format 6 file with two accounts (ids 1 and [account]) and the given "goals" text. */
    private fun base(goals: String, account: Long): String = older(6)
        .replace("\"id\": 2, \"name\": \"SBI Savings\"", "\"id\": $account, \"name\": \"SBI Savings\"")
        .replace("\"recurringRules\": []", "\"recurringRules\": [], \"goals\": $goals")

    private fun goalJson(
        id: Int = 1,
        name: String = "\"Trip\"",
        target: String = "4000000",
        targetDate: String = "\"2027-03-15\"",
        emoji: String = "\"🌴\"",
        accountId: String = "null",
        saved: String = "1800000",
    ) = """{ "id": $id, "name": $name, "target": $target, "targetDate": $targetDate, "emoji": $emoji, "accountId": $accountId, "saved": $saved,
            "sortOrder": 0, "createdAt": "2026-11-01T10:00:00.000", "updatedAt": "2026-11-01T10:00:00.000" }"""

    private fun withGoals(vararg goals: String): BackupReadResult = FinanceBackupCodec.decode(base("[${goals.joinToString(",")}]", 2))

    @Test
    fun `goals that hold are accepted`() {
        val ok = withGoals(goalJson(), goalJson(id = 2, name = "\"Emergency fund\"", accountId = "2", saved = "0", targetDate = "null", emoji = "null"))
        assertTrue(ok.isValid, ok.errors.toString())
        assertEquals(2, ok.snapshot!!.goals.size)
        assertEquals(LocalDate.of(2027, 3, 15), ok.snapshot!!.goals[0].targetDate)
        // A date written with a time, or with a zone, is the day it says.
        val dated = withGoals(goalJson(targetDate = "\"2027-03-15T00:00:00.000\""))
        assertEquals(LocalDate.of(2027, 3, 15), dated.snapshot!!.goals.single().targetDate)
    }

    @Test
    fun `goals that don't hold make the backup refused, with what is wrong`() {
        assertEquals(listOf("Goal \"Trip\" has a target that is not above zero."), withGoals(goalJson(target = "0")).errors)
        assertEquals(listOf("Goal \"Trip\" has a target that is not above zero."), withGoals(goalJson(target = "-5")).errors)
        assertEquals(listOf("Goal \"Trip\" has a saved amount below zero."), withGoals(goalJson(saved = "-1")).errors)
        assertEquals(listOf("Goal \"Trip\" is linked to an account that is not in the backup."), withGoals(goalJson(accountId = "99", saved = "0")).errors)
        assertEquals(listOf("A goal has no name."), withGoals(goalJson(name = "\"   \"")).errors)
        assertEquals(
            listOf("Goal \"${"x".repeat(41)}\" has a name longer than 40 characters."),
            withGoals(goalJson(name = "\"${"x".repeat(41)}\"")).errors,
        )
        assertEquals(listOf("Goal \"Trip\" has a picture that is not one emoji."), withGoals(goalJson(emoji = "\"ab\"")).errors)
        assertEquals(listOf("The backup has missing or duplicate goal ids."), withGoals(goalJson(id = 3), goalJson(id = 3, name = "\"Other\"")).errors)
        // Forty characters, an emoji counted as one, is a name.
        assertTrue(withGoals(goalJson(name = "\"${"x".repeat(39)}🌴\"")).isValid)
    }

    @Test
    fun `a goal that is damaged makes the backup refused`() {
        assertEquals(listOf("A goal has an invalid date in \"targetDate\"."), withGoals(goalJson(targetDate = "\"2027-13-45\"")).errors)
        assertEquals(listOf("A goal has an invalid date in \"targetDate\"."), withGoals(goalJson(targetDate = "\"soon\"")).errors)
        assertEquals(listOf("A goal has an invalid date in \"targetDate\"."), withGoals(goalJson(targetDate = "20270315")).errors)
        assertEquals(listOf("A goal has an invalid \"target\"."), withGoals(goalJson(target = "\"4000000\"")).errors)
        assertEquals(listOf("A goal has an invalid \"target\"."), withGoals(goalJson(target = "1.5")).errors)
        assertEquals(listOf("A goal has an invalid \"saved\"."), withGoals(goalJson(saved = "\"lots\"")).errors)
        assertEquals(listOf("A goal has an invalid \"accountId\"."), withGoals(goalJson(accountId = "\"2\"")).errors)
        assertEquals(listOf("A goal has an invalid \"name\"."), withGoals(goalJson(name = "7")).errors)
        assertEquals(listOf("\"goals\" must be a list."), FinanceBackupCodec.decode(base("{}", 2)).errors)
        assertEquals(listOf("\"goals\" contains an invalid entry."), FinanceBackupCodec.decode(base("[1]", 2)).errors)
    }

    @Test
    fun `a refused snapshot writes nothing when it is restored`() = runDb {
        val (_, own) = both()
        val before = repo.snapshot()
        // A goal that names an account the ledger doesn't have, and one with a target of zero.
        val badAccount = before.copy(goals = before.goals + own.copy(id = 77, accountId = 999))
        assertEquals(
            "Goal \"Trip to Goa\" is linked to an account that is not in the backup.",
            assertFailsWith<FinanceValidationException> { repo.restore(badAccount) }.message,
        )
        val noTarget = before.copy(goals = listOf(own.copy(target = 0)))
        assertEquals(
            "Goal \"Trip to Goa\" has a target that is not above zero.",
            assertFailsWith<FinanceValidationException> { repo.restore(noTarget) }.message,
        )
        val negative = before.copy(goals = listOf(own.copy(saved = -1)))
        assertEquals(
            "Goal \"Trip to Goa\" has a saved amount below zero.",
            assertFailsWith<FinanceValidationException> { repo.restore(negative) }.message,
        )
        assertEquals(before, repo.snapshot(), "the ledger is exactly as it was")
        assertFalse(repo.goals().isEmpty())
    }

    // ---------------------------------------------------------------------------------------------- beside the rest

    @Test
    fun `goals travel with tags and transactions, and the CSV is unchanged`() = runDb {
        both()
        val tax = repo.saveTag(Tag(name = "Tax-deductible", createdAt = now))
        val cash = repo.accounts().first { it.kind == AccountKind.Cash }
        val health = repo.categories().first { it.name == "Health" }
        repo.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense, amount = 45_000, accountId = cash.id!!, categoryId = health.id, occurredAt = now,
                payee = "Apollo", createdAt = now, updatedAt = now, tagIds = setOf(tax.id!!),
            ),
        )
        val before = repo.snapshot()
        val snapshot = FinanceBackupCodec.decode(encode(before)).snapshot!!
        repo.resetAll()
        repo.restore(snapshot)
        assertEquals(before, repo.snapshot())
        val csv = FinanceBackupCodec.encodeCsv(before) { it.toString() }.trim().lines()
        assertTrue(csv.first().endsWith(",Person,Tags"), "goals add no column: ${csv.first()}")
        assertEquals(2, csv.size)
    }
}
