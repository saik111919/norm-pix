package com.example.normpix.finance.data

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountFlows
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.BILLING_DAYS
import com.example.normpix.finance.domain.Budget
import com.example.normpix.finance.domain.BudgetOverview
import com.example.normpix.finance.domain.BudgetStatus
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryTotal
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.EMOJI_ICON_PREFIX
import com.example.normpix.finance.domain.emojiOf
import com.example.normpix.finance.domain.foldIntoParents
import com.example.normpix.finance.domain.isPictureName
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.CustomBank
import com.example.normpix.finance.domain.DailyTotal
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.MAX_PERSON_NAME
import com.example.normpix.finance.domain.MAX_SPLIT_PARTS
import com.example.normpix.finance.domain.MIN_SPLIT_PARTS
import com.example.normpix.finance.domain.NUMBER_ENDING_LENGTH
import com.example.normpix.finance.domain.NUMBER_START_LENGTH
import com.example.normpix.finance.domain.LedgerIssues
import com.example.normpix.finance.domain.LoanEmi
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.MonthTotals
import com.example.normpix.finance.domain.PeopleSpending
import com.example.normpix.finance.domain.PeriodSummary
import com.example.normpix.finance.domain.StatementFigures
import com.example.normpix.finance.domain.previousOnDay
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.PersonTotal
import com.example.normpix.finance.domain.cleanGoalName
import com.example.normpix.finance.domain.cleanPersonName
import com.example.normpix.finance.domain.isSingleEmoji
import com.example.normpix.finance.domain.numberEndingOrNull
import com.example.normpix.finance.domain.numberStartOrNull
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.RepeatSchedule
import com.example.normpix.finance.domain.SavingsGoal
import com.example.normpix.finance.domain.SelfTransferPair
import com.example.normpix.finance.domain.SplitFault
import com.example.normpix.finance.domain.SplitPart
import com.example.normpix.finance.domain.MAX_TAG_NAME
import com.example.normpix.finance.domain.Tag
import com.example.normpix.finance.domain.TagTotal
import com.example.normpix.finance.domain.cleanTagName
import com.example.normpix.finance.domain.tagNameFault
import com.example.normpix.finance.domain.asTransfer
import com.example.normpix.finance.domain.checkLedger
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.accountBalance
import com.example.normpix.finance.domain.buildBudgetOverview
import com.example.normpix.finance.domain.dueOccurrences
import com.example.normpix.finance.domain.fault
import com.example.normpix.finance.domain.joinedSplitNotes
import com.example.normpix.finance.domain.largestPartCategory
import com.example.normpix.finance.domain.loanEmiEntries
import com.example.normpix.finance.domain.EMI_NOTE_PREFIX
import com.example.normpix.finance.domain.emiCovered
import com.example.normpix.finance.domain.emiInterestCategoryOrFallback
import com.example.normpix.finance.domain.emiRule
import com.example.normpix.finance.domain.emiSplit
import com.example.normpix.finance.domain.firstAutoEmiIndex
import com.example.normpix.finance.domain.loanOutlook
import com.example.normpix.finance.domain.recordsEmiOf
import com.example.normpix.finance.domain.nextRecurrence
import com.example.normpix.finance.domain.resolveBudgets
import com.example.normpix.finance.domain.settledBy
import com.example.normpix.finance.domain.splitFault
import com.example.normpix.finance.domain.startOfDay
import com.example.normpix.finance.domain.withoutBrokenSplits
import com.example.normpix.wallet.BankLogoResolver
import com.example.normpix.storage.insert
import com.example.normpix.storage.insertOrThrow
import com.example.normpix.storage.query
import com.example.normpix.storage.rawQuery
import com.example.normpix.storage.replaceOrThrow
import com.example.normpix.storage.transaction
import com.example.normpix.storage.update
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/** Filters for the transaction history. Empty sets mean "any". */
data class TransactionQuery(
    val range: DateRange? = null,
    val types: Set<TransactionType> = emptySet(),
    /** Matches transactions from *or* to any of these accounts. */
    val accountIds: Set<Long> = emptySet(),
    val categoryIds: Set<Long> = emptySet(),
    /** Only expenses spent on any of these people (only an expense can name one). */
    val personIds: Set<Long> = emptySet(),
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
    /** Case-insensitive match on payee, note, category, account, person or tag name. */
    val text: String? = null,
    /** Only what is dated before this moment, whatever the [range] says: what has happened by then (see `settledCutoff`). */
    val before: LocalDateTime? = null,
    /** Only what is dated at or after this moment: what is still to come, when it is the settled cutoff. */
    val notBefore: LocalDateTime? = null,
    /** Only transactions that carry any of these tags. */
    val tagIds: Set<Long> = emptySet(),
) {
    /** True when anything besides [range] narrows the results. */
    val hasFilters: Boolean
        get() = types.isNotEmpty() ||
            accountIds.isNotEmpty() ||
            categoryIds.isNotEmpty() ||
            personIds.isNotEmpty() ||
            tagIds.isNotEmpty() ||
            minAmount != null ||
            maxAmount != null ||
            !text.isNullOrBlank()
}

/** What [FinanceRepository.checkAndRepair] read, put right and found (see `LedgerCheck.kt`). */
data class LedgerCheckResult(
    val accounts: Int,
    val categories: Int,
    val transactions: Int,
    val rules: Int,
    val issues: LedgerIssues,
    /** Repeating payments that were due and are now recorded. */
    val recordedRepeats: Int = 0,
)

/** Keyset pagination position: newest-first by (occurred_at, id). */
data class TransactionCursor(val occurredAtMs: Long, val id: Long)

class TransactionPage(
    val items: List<FinanceTransaction>,
    /** Null when there are no more results. */
    val next: TransactionCursor?,
)

/** One month of budget history. */
class BudgetHistoryEntry(val overview: BudgetOverview) {
    val month: MonthKey get() = overview.month
    val status: BudgetStatus get() = overview.headline!!
}

/** How far either side of an EMI's day a payment made by hand is looked for: wide enough for [emiCovered]'s half a month. */
private const val HAND_PAID_WINDOW_DAYS = 16L

/**
 * The single gateway to finance data. Every write is validated here (with the
 * database's own constraints as a second line of defense), every derived
 * number is computed from transactions on read via the domain ledger
 * functions, and multi-step writes run in one SQL transaction.
 *
 * All work runs on one dedicated thread, so reads and writes are naturally
 * serialized: a validate-then-write can never interleave with another write.
 */
class FinanceRepository(
    private val database: FinanceDatabase,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
    /** Where the database work runs: the one dedicated thread, unless a test runs it in place. The sample set uses the same. */
    internal val dispatcher: CoroutineDispatcher = defaultDispatcher,
) {
    private suspend fun <T> io(block: SupportSQLiteDatabase.() -> T): T =
        withContext(dispatcher) { database.writableDatabase.block() }

    // ------------------------------------------------------------ settings

    suspend fun currencyCode(): String = io { meta("currency") ?: CurrencySpec.INR.code }

    /**
     * Changing currency relabels every stored amount, so it's only allowed
     * freely between currencies with the same number of decimal places.
     */
    suspend fun setCurrency(code: String) = io {
        val next = CurrencySpec.byCode(code)
        val current = CurrencySpec.byCode(meta("currency") ?: CurrencySpec.INR.code)
        if (next.decimals != current.decimals && hasAmounts()) {
            throw FinanceConflictException(
                "${next.name} uses ${next.decimals} decimal places, so switching " +
                    "would change the value of amounts you already recorded. Export a " +
                    "backup and reset your data to start a new ledger in ${next.code}.",
            )
        }
        setMeta("currency", next.code)
    }

    suspend fun defaultAccountId(): Long? = io { meta("default_account_id")?.toLongOrNull() }

    suspend fun setDefaultAccountId(id: Long?) = io { storeDefaultAccount(id) }

    private fun SupportSQLiteDatabase.storeDefaultAccount(id: Long?) {
        if (id == null) {
            delete(FinanceDatabase.META, "key = ?", arrayOf("default_account_id"))
        } else {
            setMeta("default_account_id", id.toString())
        }
    }

    private fun SupportSQLiteDatabase.meta(key: String): String? =
        query(FinanceDatabase.META, arrayOf("value"), "key = ?", arrayOf(key), null, null, null).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun SupportSQLiteDatabase.setMeta(key: String, value: String) {
        replaceOrThrow(
            FinanceDatabase.META,
            null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
            },
        )
    }

    private fun SupportSQLiteDatabase.hasAmounts(): Boolean = scalarLong(
        """
        SELECT
          (SELECT COUNT(*) FROM ${FinanceDatabase.TRANSACTIONS}) +
          (SELECT COUNT(*) FROM ${FinanceDatabase.BUDGETS}) +
          (SELECT COUNT(*) FROM ${FinanceDatabase.RECURRING_RULES}) +
          (SELECT COUNT(*) FROM ${FinanceDatabase.ACCOUNTS} WHERE opening_balance != 0)
        """.trimIndent(),
    ).let { (it ?: 0) > 0 }

    // ------------------------------------------------------------ accounts

    suspend fun accounts(): List<Account> = io {
        query(FinanceDatabase.ACCOUNTS, null, null, null, null, null, "archived ASC, sort_order ASC, id ASC")
            .map(FinanceRows::toAccount)
    }

    suspend fun saveAccount(account: Account): Account = io {
        val name = account.name.trim()
        if (name.isEmpty()) throw FinanceValidationException("Give the account a name.")
        // Cash has no bank; any other account may name one, but only one the app knows.
        val bankKey = account.bankKey?.takeIf { account.kind != AccountKind.Cash }
        if (bankKey != null && bankCatalog().byKey(bankKey) == null) {
            throw FinanceValidationException("Choose a bank from the list, or add it first.")
        }
        // Only a card has a limit, and a limit is an amount: anything else is no limit at all.
        if (account.creditLimit != null && account.creditLimit <= 0) {
            throw FinanceValidationException("Enter a credit limit greater than zero.")
        }
        val creditLimit = account.creditLimit?.takeIf { account.kind == AccountKind.Card }
        // The same goes for a card's two days of the month: a day, or nothing.
        if (account.statementDay != null && account.statementDay !in BILLING_DAYS) {
            throw FinanceValidationException("Choose a statement date between the 1st and the 31st.")
        }
        if (account.dueDay != null && account.dueDay !in BILLING_DAYS) {
            throw FinanceValidationException("Choose a payment due date between the 1st and the 31st.")
        }
        val isCard = account.kind == AccountKind.Card
        // The number's ending is its last four digits or nothing; cash has no number and no card. A Wallet card is the card of
        // one account at most (the form offers only the unlinked ones; this is for anything that gets past it).
        val typedEnding = account.numberEnding?.trim()?.takeIf { it.isNotEmpty() }
        if (typedEnding != null && numberEndingOrNull(typedEnding) == null) {
            throw FinanceValidationException("Enter the last $NUMBER_ENDING_LENGTH digits of the number, or leave it empty.")
        }
        // Its start - the first six digits, as a card number begins - is six digits or nothing, on the same terms.
        val typedStart = account.numberStart?.trim()?.takeIf { it.isNotEmpty() }
        if (typedStart != null && numberStartOrNull(typedStart) == null) {
            throw FinanceValidationException("Enter the first $NUMBER_START_LENGTH digits of the number, or leave it empty.")
        }
        val hasNumber = account.kind != AccountKind.Cash
        val cardId = account.cardId?.takeIf { it.isNotBlank() && hasNumber }
        if (cardId != null) {
            val holder = rawQuery(
                "SELECT name FROM ${FinanceDatabase.ACCOUNTS} WHERE card_id = ? AND id != ? AND archived = 0 LIMIT 1",
                arrayOf(cardId, (account.id ?: -1L).toString()),
            ).map { it.string("name") }.firstOrNull()
            if (holder != null) throw FinanceValidationException("That card is already the card of \"$holder\".")
        }
        // A loan has terms that hold, is never a card (a card has a bill, not EMIs), and an account of the Loan kind always has them.
        account.loan?.fault()?.let { throw FinanceValidationException(it.advice) }
        if (account.loan != null && isCard) throw FinanceValidationException("A card can't be a loan. Choose another type, or leave the loan off.")
        if (account.kind == AccountKind.Loan && account.loan == null) {
            throw FinanceValidationException("Enter what you borrowed, the interest and the EMI.")
        }
        val toSave = account.copy(
            name = name,
            bankKey = bankKey,
            creditLimit = creditLimit,
            statementDay = account.statementDay?.takeIf { isCard },
            dueDay = account.dueDay?.takeIf { isCard },
            numberEnding = typedEnding?.takeIf { hasNumber },
            numberStart = typedStart?.takeIf { hasNumber },
            cardId = cardId,
            updatedAt = clock(),
        )
        if (account.id == null) {
            val id = insertOrThrow(FinanceDatabase.ACCOUNTS, null, FinanceRows.account(toSave))
            toSave.copy(id = id)
        } else {
            val before = readAccount(account.id)
            update(FinanceDatabase.ACCOUNTS, FinanceRows.account(toSave), "id = ?", arrayOf(account.id.toString()))
            followNewEmi(before?.loan?.emi, toSave)
            toSave
        }
    }

    /**
     * A loan whose EMI was [oldEmi] and is now [loan]'s: the repeating payment recording its EMIs each month pays the new one from now on
     * - when it was paying the old one (an amount the user set by hand on the repeating payment is theirs and stays). Its end is taken off,
     * as the new EMI pays the loan off on another day; it ends when nothing is owed ([recordDueEmis]).
     */
    private fun SupportSQLiteDatabase.followNewEmi(oldEmi: Long?, loan: Account) {
        val newEmi = loan.loan?.emi ?: return
        if (oldEmi == null || oldEmi == newEmi) return
        val rule = activeEmiRule(loan.id ?: return) ?: return
        if (rule.amount != oldEmi) return
        update(
            FinanceDatabase.RECURRING_RULES,
            ContentValues().apply {
                put("amount", newEmi)
                putNull("end_date")
                put("updated_at", clock().toStorage())
            },
            "id = ?",
            arrayOf(rule.id.toString()),
        )
    }

    suspend fun setAccountArchived(id: Long, archived: Boolean) = io {
        update(
            FinanceDatabase.ACCOUNTS,
            ContentValues().apply {
                put("archived", if (archived) 1 else 0)
                put("updated_at", clock().toStorage())
            },
            "id = ?",
            arrayOf(id.toString()),
        )
        if (archived && meta("default_account_id")?.toLongOrNull() == id) storeDefaultAccount(null)
    }

    /**
     * Deleting an account that has history would silently rewrite balances,
     * totals and budgets, so it's refused - archive it instead.
     */
    suspend fun deleteAccount(id: Long) = io {
        val tx = scalarLong(
            "SELECT COUNT(*) FROM ${FinanceDatabase.TRANSACTIONS} WHERE account_id = $id OR to_account_id = $id",
        ) ?: 0
        val rules = scalarLong(
            "SELECT COUNT(*) FROM ${FinanceDatabase.RECURRING_RULES} WHERE account_id = $id OR to_account_id = $id",
        ) ?: 0
        if (tx > 0 || rules > 0) {
            throw FinanceConflictException(
                if (tx > 0) {
                    "This account has ${count(tx, "transaction")}. Archive it " +
                        "instead to hide it while keeping your history."
                } else {
                    "This account is used by ${count(rules, "recurring payment")}. " +
                        "Delete or change those first, or archive the account."
                },
            )
        }
        delete(FinanceDatabase.ACCOUNTS, "id = ?", arrayOf(id.toString()))
        if (meta("default_account_id")?.toLongOrNull() == id) storeDefaultAccount(null)
    }

    // --------------------------------------------------------------- banks

    /** The banks the user added (the built-in ones live in [BankCatalog] and are not stored). */
    suspend fun customBanks(): List<CustomBank> = io { readBanks() }

    private fun SupportSQLiteDatabase.readBanks(): List<CustomBank> =
        query(FinanceDatabase.BANKS, null, null, null, null, null, "name COLLATE NOCASE ASC").map(FinanceRows::toBank)

    private fun SupportSQLiteDatabase.bankCatalog() = BankCatalog(readBanks())

    /**
     * The key of the bank called [name], adding it first if the app doesn't know it yet. "hdfc bank ltd" finds the built-in
     * HDFC Bank and "my credit union" twice finds the one bank, so accounts and cards that name a bank always share it.
     */
    suspend fun bankKeyFor(name: String): String = io {
        val wanted = name.trim().replace(Regex("\\s+"), " ")
        if (wanted.isEmpty()) throw FinanceValidationException("Enter the bank's name.")
        if (wanted.length > MAX_BANK_NAME) throw FinanceValidationException("Keep the bank's name under $MAX_BANK_NAME characters.")
        val catalog = bankCatalog()
        catalog.exact(wanted)?.key ?: run {
            if (BankLogoResolver.normalize(wanted).isEmpty()) throw FinanceValidationException("Enter the bank's name.")
            val key = BankCatalog.newCustomKey()
            insertOrThrow(FinanceDatabase.BANKS, null, FinanceRows.bank(CustomBank(key, wanted, clock())))
            key
        }
    }

    /**
     * Renames a bank the user added. Its key is what accounts and cards point at and it does not change, so every one of them
     * follows the new name. Built-in banks are not stored and cannot be renamed; a name that is another bank's (built-in or the
     * user's own) is refused, so a bank is only ever known by one name.
     */
    suspend fun renameBank(key: String, name: String) = io {
        val wanted = name.trim().replace(Regex("\\s+"), " ")
        if (wanted.isEmpty() || BankLogoResolver.normalize(wanted).isEmpty()) throw FinanceValidationException("Enter the bank's name.")
        if (wanted.length > MAX_BANK_NAME) throw FinanceValidationException("Keep the bank's name under $MAX_BANK_NAME characters.")
        val catalog = bankCatalog()
        val bank = catalog.all.firstOrNull { it.key == key && !it.builtIn } ?: throw FinanceValidationException("That bank isn't one you added.")
        catalog.exact(wanted)?.takeIf { it.key != bank.key }?.let {
            throw FinanceValidationException("${it.name} is already in your list.")
        }
        update(FinanceDatabase.BANKS, ContentValues().apply { put("name", wanted) }, "key = ?", arrayOf(key))
    }

    /**
     * Removes a bank the user added, only while none of their accounts name it: an account that pointed at a bank the app no
     * longer knows would lose it, and nothing here removes what the user entered. (The caller checks the saved cards too, which
     * live in the card vault and are not read here.)
     */
    suspend fun deleteBank(key: String) = io {
        if (bankCatalog().all.none { it.key == key && !it.builtIn }) throw FinanceValidationException("That bank isn't one you added.")
        val users = rawQuery("SELECT COUNT(*) FROM ${FinanceDatabase.ACCOUNTS} WHERE bank_key = ?", arrayOf(key)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        if (users > 0) throw FinanceValidationException("${if (users == 1) "An account uses" else "$users accounts use"} this bank. Change theirs first.")
        delete(FinanceDatabase.BANKS, "key = ?", arrayOf(key))
    }

    /**
     * The first moment after today by this repository's clock: what is dated before it has happened, what is dated from it on is
     * upcoming (see `settledCutoff`).
     */
    fun settledCutoff(): LocalDateTime = com.example.normpix.finance.domain.settledCutoff(clock())

    /**
     * Balance of every account (archived included), by id: from everything recorded, or - with [asOf] - from what is dated
     * before it, so an entry dated ahead (a bill entered before its day) moves the balance on its day, not before.
     */
    suspend fun accountBalances(asOf: LocalDateTime? = null): Map<Long, Long> = io {
        val dated = asOf?.let { "WHERE occurred_at < ${it.toStorage()}" } ?: ""
        val datedAnd = asOf?.let { "AND occurred_at < ${it.toStorage()} " } ?: ""
        val openings = query(FinanceDatabase.ACCOUNTS, arrayOf("id", "opening_balance"), null, null, null, null, null)
            .map { it.long("id") to it.long("opening_balance") }

        val income = mutableMapOf<Long, Long>()
        val expense = mutableMapOf<Long, Long>()
        val transferOut = mutableMapOf<Long, Long>()
        rawQuery(
            "SELECT account_id, type, SUM(amount) AS total FROM ${FinanceDatabase.TRANSACTIONS} $dated GROUP BY account_id, type",
            null,
        ).map { Triple(it.long("account_id"), it.string("type"), it.long("total")) }
            .forEach { (account, type, total) ->
                when (type) {
                    "income" -> income[account] = total
                    "expense" -> expense[account] = total
                    "transfer" -> transferOut[account] = total
                }
            }
        val transferIn = rawQuery(
            "SELECT to_account_id, SUM(amount) AS total FROM ${FinanceDatabase.TRANSACTIONS} " +
                "WHERE type = 'transfer' ${datedAnd}GROUP BY to_account_id",
            null,
        ).map { it.long("to_account_id") to it.long("total") }.toMap()

        openings.associate { (id, opening) ->
            id to accountBalance(
                opening,
                AccountFlows(
                    income = income[id] ?: 0,
                    expense = expense[id] ?: 0,
                    transferIn = transferIn[id] ?: 0,
                    transferOut = transferOut[id] ?: 0,
                ),
            )
        }
    }

    /**
     * Money that moved through [accountId], by direction, for what is dated from [from] up to (not including) [to]. Add what came
     * in and take away what went out to get how the account's balance changed over that time.
     */
    suspend fun accountFlowsBetween(accountId: Long, from: LocalDateTime, to: LocalDateTime): AccountFlows = io {
        val range = "occurred_at >= ${from.toStorage()} AND occurred_at < ${to.toStorage()}"
        var income = 0L
        var expense = 0L
        var transferOut = 0L
        rawQuery(
            "SELECT type, SUM(amount) AS total FROM ${FinanceDatabase.TRANSACTIONS} WHERE account_id = $accountId AND $range GROUP BY type",
            null,
        ).map { it.string("type") to it.long("total") }.forEach { (type, total) ->
            when (type) {
                "income" -> income = total
                "expense" -> expense = total
                "transfer" -> transferOut = total
            }
        }
        val transferIn = rawQuery(
            "SELECT to_account_id, SUM(amount) AS total FROM ${FinanceDatabase.TRANSACTIONS} " +
                "WHERE type = 'transfer' AND to_account_id = $accountId AND $range GROUP BY to_account_id",
            null,
        ).map { it.long("total") }.sum()
        AccountFlows(income = income, expense = expense, transferIn = transferIn, transferOut = transferOut)
    }

    /**
     * Where each of [cards] stood at its last statement (the day it was made up, on or before [today]) and what has been put onto
     * it since, as of the end of [today] - the figures `cardBill` turns into what is due. A card with no statement day has none.
     */
    suspend fun cardStatementFigures(cards: List<Account>, today: LocalDate): Map<Long, StatementFigures> {
        val untilEndOfToday = today.plusDays(1).atStartOfDay()
        val result = HashMap<Long, StatementFigures>()
        val byStatement = cards.filter { it.id != null && it.statementDay != null }.groupBy { previousOnDay(it.statementDay!!, today) }
        for ((statement, group) in byStatement) {
            val endOfStatementDay = statement.plusDays(1).atStartOfDay()
            val balances = accountBalances(asOf = endOfStatementDay)
            for (card in group) {
                val id = card.id!!
                val flows = accountFlowsBetween(id, endOfStatementDay, untilEndOfToday)
                result[id] = StatementFigures(
                    statementDate = statement,
                    owedAtStatement = maxOf(0L, -(balances[id] ?: 0L)),
                    paidSince = flows.income + flows.transferIn,
                )
            }
        }
        return result
    }

    // ---------------------------------------------------------- categories

    suspend fun categories(): List<Category> = io {
        query(
            FinanceDatabase.CATEGORIES,
            null,
            null,
            null,
            null,
            null,
            "type ASC, archived ASC, sort_order ASC, id ASC",
        ).map(FinanceRows::toCategory)
    }

    private fun SupportSQLiteDatabase.readCategory(id: Long): Category? =
        query(FinanceDatabase.CATEGORIES, null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) FinanceRows.toCategory(it) else null }

    private fun SupportSQLiteDatabase.hasSubCategories(id: Long): Boolean =
        (scalarLong("SELECT COUNT(*) FROM ${FinanceDatabase.CATEGORIES} WHERE parent_id = $id") ?: 0) > 0

    /** Child id to parent id for every sub-category. */
    private fun SupportSQLiteDatabase.parentIds(): Map<Long, Long> =
        rawQuery("SELECT id, parent_id FROM ${FinanceDatabase.CATEGORIES} WHERE parent_id IS NOT NULL", null)
            .map { it.long("id") to it.long("parent_id") }
            .toMap()

    /**
     * Saves a category, new or edited. Its [Category.icon] is one of the app's keys or exactly one emoji. A category can
     * sit under a top-level category of the same kind - never under itself, never two levels down - and then takes its
     * parent's color, so a family reads as one; changing a parent's color changes its sub-categories' too.
     */
    suspend fun saveCategory(category: Category): Category = io {
        val name = category.name.trim()
        if (name.isEmpty()) throw FinanceValidationException("Give the category a name.")
        if (category.icon.startsWith(EMOJI_ICON_PREFIX) && emojiOf(category.icon) == null) {
            throw FinanceValidationException("Choose a single emoji for the icon.")
        }
        val existing = category.id?.let { readCategory(it) }
        val parent = category.parentId?.let { parentId ->
            readCategory(parentId) ?: throw FinanceValidationException("Choose a category to put this under.")
        }
        if (parent != null) {
            when {
                parent.id == category.id -> throw FinanceValidationException("A category can't go under itself.")
                parent.parentId != null -> throw FinanceValidationException("Sub-categories can't have sub-categories of their own.")
                parent.type != category.type -> throw FinanceValidationException("A sub-category must be the same kind as its parent.")
                parent.archived && !category.archived -> throw FinanceValidationException("Restore ${parent.name} first.")
            }
        }
        if (category.id != null && hasSubCategories(category.id)) {
            if (parent != null) {
                throw FinanceConflictException("$name has sub-categories, so it can't go under another one. Move them out first.")
            }
            if (existing != null && existing.type != category.type) {
                throw FinanceConflictException("$name has sub-categories of its own kind. Move or delete them first.")
            }
        }
        val toSave = category.copy(name = name, color = parent?.color ?: category.color, updatedAt = clock())
        if (category.id == null) {
            val maxOrder = scalarLong("SELECT MAX(sort_order) FROM ${FinanceDatabase.CATEGORIES}") ?: 0
            val withOrder = toSave.copy(sortOrder = (maxOrder + 1).toInt())
            val id = insertOrThrow(FinanceDatabase.CATEGORIES, null, FinanceRows.category(withOrder))
            withOrder.copy(id = id)
        } else {
            transaction {
                update(FinanceDatabase.CATEGORIES, FinanceRows.category(toSave), "id = ?", arrayOf(category.id.toString()))
                if (existing != null && existing.color != toSave.color) {
                    update(
                        FinanceDatabase.CATEGORIES,
                        ContentValues().apply { put("color", toSave.color) },
                        "parent_id = ?",
                        arrayOf(category.id.toString()),
                    )
                }
            }
            toSave
        }
    }

    /**
     * Adds the ready-made sub-categories ([CategorySuggestions]) that are missing (see [missingSuggestions]), each under its
     * top-level category and in that category's color, all or nothing. Safe to run again: a second run finds nothing to add.
     * Returns how many were added.
     */
    suspend fun addSuggestedSubCategories(): Int = io {
        val now = clock()
        var added = 0
        transaction {
            val all = query(FinanceDatabase.CATEGORIES, null, null, null, null, null, null).map(FinanceRows::toCategory)
            var order = (scalarLong("SELECT MAX(sort_order) FROM ${FinanceDatabase.CATEGORIES}") ?: 0).toInt()
            for ((parent, suggestion) in missingSuggestions(all)) {
                val sub = Category(
                    name = suggestion.name,
                    type = parent.type,
                    icon = suggestion.icon,
                    color = parent.color,
                    sortOrder = ++order,
                    createdAt = now,
                    updatedAt = now,
                    parentId = parent.id,
                )
                insertOrThrow(FinanceDatabase.CATEGORIES, null, FinanceRows.category(sub))
                added++
            }
        }
        added
    }

    /**
     * Hides a category (or brings it back). Archiving one takes its sub-categories with it - one left showing under a hidden
     * parent would have nowhere to be - and a sub-category can only come back once its parent has.
     */
    suspend fun setCategoryArchived(id: Long, archived: Boolean) {
        io {
            val category = readCategory(id)
            if (category != null) {
                if (!archived && category.parentId != null) {
                    val parent = readCategory(category.parentId)
                    if (parent != null && parent.archived) throw FinanceConflictException("Restore ${parent.name} first.")
                }
                update(
                    FinanceDatabase.CATEGORIES,
                    ContentValues().apply {
                        put("archived", if (archived) 1 else 0)
                        put("updated_at", clock().toStorage())
                    },
                    if (archived) "id = ? OR parent_id = ?" else "id = ?",
                    if (archived) arrayOf(id.toString(), id.toString()) else arrayOf(id.toString()),
                )
            }
        }
    }

    /**
     * Refused while transactions or recurring payments use the category. Its
     * budgets are removed with it.
     */
    suspend fun deleteCategory(id: Long) = io {
        if (hasSubCategories(id)) {
            throw FinanceConflictException("This category has sub-categories. Delete or move them first.")
        }
        val tx = scalarLong("SELECT COUNT(*) FROM ${FinanceDatabase.TRANSACTIONS} WHERE category_id = $id") ?: 0
        val rules = scalarLong("SELECT COUNT(*) FROM ${FinanceDatabase.RECURRING_RULES} WHERE category_id = $id") ?: 0
        if (tx > 0 || rules > 0) {
            throw FinanceConflictException(
                if (tx > 0) {
                    "This category has ${count(tx, "transaction")}. Archive it " +
                        "instead to hide it while keeping your history."
                } else {
                    "This category is used by ${count(rules, "recurring payment")}. " +
                        "Change those first, or archive the category."
                },
            )
        }
        delete(FinanceDatabase.CATEGORIES, "id = ?", arrayOf(id.toString()))
    }

    // -------------------------------------------------------------- people

    /** Everyone, the ones in use first (then by name); archived people come last. */
    suspend fun people(): List<Person> = io { readPeople() }

    private fun SupportSQLiteDatabase.readPeople(): List<Person> =
        query(FinanceDatabase.PEOPLE, null, null, null, null, null, "archived ASC, name COLLATE NOCASE ASC, id ASC")
            .map(FinanceRows::toPerson)

    private fun SupportSQLiteDatabase.readPerson(id: Long): Person? =
        query(FinanceDatabase.PEOPLE, null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) FinanceRows.toPerson(it) else null }

    /**
     * Saves a person, new or edited. The name is tidied (no stray spaces) and must be new to the list whatever its case -
     * "mom" and "Mom" are one person, archived or not - and the picture is none (the initial shows) or exactly one emoji.
     * Everything else about them stays, and so does every expense linked to them: the link is their id, not their name.
     */
    suspend fun savePerson(person: Person): Person = io {
        val name = cleanPersonName(person.name)
        if (name.isEmpty()) throw FinanceValidationException("Give the person a name.")
        if (name.length > MAX_PERSON_NAME) throw FinanceValidationException("Keep the name under $MAX_PERSON_NAME characters.")
        val emoji = person.emoji?.trim()?.takeIf { it.isNotEmpty() }
        if (emoji != null && !isSingleEmoji(emoji)) throw FinanceValidationException("Choose a single emoji for the picture.")
        val clash = readPeople().firstOrNull { it.id != person.id && it.name.equals(name, ignoreCase = true) }
        if (clash != null) {
            throw FinanceConflictException(
                if (clash.archived) {
                    "${clash.name} is already in your archived people. Restore them instead."
                } else {
                    "You already have someone called ${clash.name}."
                },
            )
        }
        val toSave = person.copy(name = name, emoji = emoji, color = person.color.coerceAtLeast(0), updatedAt = clock())
        if (person.id == null) {
            toSave.copy(id = insertOrThrow(FinanceDatabase.PEOPLE, null, FinanceRows.person(toSave)))
        } else {
            val count = update(FinanceDatabase.PEOPLE, FinanceRows.person(toSave), "id = ?", arrayOf(person.id.toString()))
            if (count == 0) throw FinanceConflictException("This person no longer exists.")
            toSave
        }
    }

    /** Hides a person from pickers and the main list (or brings them back). Their expenses keep them either way. */
    suspend fun setPersonArchived(id: Long, archived: Boolean) = io {
        update(
            FinanceDatabase.PEOPLE,
            ContentValues().apply {
                put("archived", if (archived) 1 else 0)
                put("updated_at", clock().toStorage())
            },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    /**
     * Deletes a person. The expenses that named them are **kept** - they are real spending, and stay in every balance, total
     * and budget - and simply name no one from then on, all in one step. Returns how many expenses that was. Archiving is the
     * way to hide someone while keeping what was spent on them attached.
     */
    suspend fun deletePerson(id: Long): Int = io {
        var unlinked = 0
        transaction {
            unlinked = update(
                FinanceDatabase.TRANSACTIONS,
                ContentValues().apply { putNull("person_id") },
                "person_id = ?",
                arrayOf(id.toString()),
            )
            delete(FinanceDatabase.PEOPLE, "id = ?", arrayOf(id.toString()))
        }
        unlinked
    }

    /**
     * What was spent over [range] (everything when null), in all and on each person. The whole is every expense in the period,
     * the same number the dashboard shows; a person's part is only ever a share of it, since an expense is one record that
     * names at most one person. Transfers and income are never spending.
     */
    suspend fun peopleSpending(range: DateRange? = null, asOf: LocalDateTime? = null): PeopleSpending = io {
        // With [asOf], only what is dated before it: what has been spent by then (see `settledCutoff`).
        val window = range?.let { " AND occurred_at >= ${it.start.toStorage()} AND occurred_at < ${it.end.toStorage()}" }.orEmpty() +
            asOf?.let { " AND occurred_at < ${it.toStorage()}" }.orEmpty()
        val (total, count) = rawQuery(
            "SELECT COALESCE(SUM(amount), 0), COUNT(*) FROM ${FinanceDatabase.TRANSACTIONS} WHERE type = 'expense'$window",
            null,
        ).use { it.moveToFirst(); it.getLong(0) to it.getInt(1) }
        val perPerson = rawQuery(
            "SELECT person_id, SUM(amount) AS total, COUNT(*) AS n, MAX(occurred_at) AS latest " +
                "FROM ${FinanceDatabase.TRANSACTIONS} WHERE type = 'expense' AND person_id IS NOT NULL$window " +
                "GROUP BY person_id ORDER BY total DESC",
            null,
        ).map {
            PersonTotal(
                personId = it.long("person_id"),
                amount = it.long("total"),
                count = it.int("n"),
                lastAt = it.long("latest").toLocalDateTime(),
            )
        }
        PeopleSpending(totalExpense = total, expenseCount = count, byPerson = perPerson)
    }

    /** The person an expense may name: an expense only, someone who exists, and - for a new link - not archived. */
    private fun SupportSQLiteDatabase.validatePerson(type: TransactionType, personId: Long?, transactionId: Long?) =
        validatePersonLink(type, personId) {
            transactionId != null &&
                scalarLong("SELECT person_id FROM ${FinanceDatabase.TRANSACTIONS} WHERE id = $transactionId") == personId
        }

    /** The same rules for a recurring payment's person: what it records is spent on them. */
    private fun SupportSQLiteDatabase.validateRulePerson(rule: RecurringRule) =
        validatePersonLink(rule.type, rule.personId) {
            rule.id != null &&
                scalarLong("SELECT person_id FROM ${FinanceDatabase.RECURRING_RULES} WHERE id = ${rule.id}") == rule.personId
        }

    private fun SupportSQLiteDatabase.validatePersonLink(type: TransactionType, personId: Long?, alreadyLinked: () -> Boolean) {
        if (personId == null) return
        if (type != TransactionType.Expense) throw FinanceValidationException("Only an expense can be spent on a person.")
        val person = readPerson(personId) ?: throw FinanceValidationException("Choose a person from the list, or add them first.")
        // An archived person keeps what is already theirs, but takes nothing new.
        if (person.archived && !alreadyLinked()) throw FinanceValidationException("${person.name} is archived. Restore them first.")
    }

    // -------------------------------------------------------- transactions

    private fun SupportSQLiteDatabase.exists(table: String, id: Long): Boolean =
        query(table, arrayOf("id"), "id = ?", arrayOf(id.toString()), null, null, null).use { it.moveToFirst() }

    private fun SupportSQLiteDatabase.validateShape(
        type: TransactionType,
        amount: Long,
        accountId: Long,
        toAccountId: Long?,
        categoryId: Long?,
    ) {
        if (amount <= 0) throw FinanceValidationException("Enter an amount greater than zero.")
        if (amount > 999_999_999_999L * 100) throw FinanceValidationException("That amount is too large.")
        if (!exists(FinanceDatabase.ACCOUNTS, accountId)) throw FinanceValidationException("Choose an account.")
        if (type == TransactionType.Transfer) {
            if (toAccountId == null || !exists(FinanceDatabase.ACCOUNTS, toAccountId)) {
                throw FinanceValidationException("Choose the account to transfer to.")
            }
            if (toAccountId == accountId) throw FinanceValidationException("Choose two different accounts.")
            if (categoryId != null) throw FinanceValidationException("Transfers don't have a category.")
            return
        }
        if (toAccountId != null) throw FinanceValidationException("Only transfers have a destination account.")
        if (categoryId == null) throw FinanceValidationException("Choose a category.")
        val categoryType = query(
            FinanceDatabase.CATEGORIES,
            arrayOf("type"),
            "id = ?",
            arrayOf(categoryId.toString()),
            null,
            null,
            null,
        ).use { if (it.moveToFirst()) it.getString(0) else null } ?: throw FinanceValidationException("Choose a category.")
        if (categoryType != CategoryType.forTransaction(type).key) {
            throw FinanceValidationException(
                if (type == TransactionType.Income) "Choose an income category." else "Choose an expense category.",
            )
        }
    }

    /**
     * Inserts or updates a transaction. Editing keeps the same id and
     * creation time; every total derived from it updates automatically
     * because nothing derived is stored.
     */
    suspend fun saveTransaction(t: FinanceTransaction): FinanceTransaction = io {
        validateShape(t.type, t.amount, t.accountId, t.toAccountId, t.categoryId)
        validatePerson(t.type, t.personId, t.id)
        validatePicture(t.picture)
        validateTags(t.type, t.tagIds)
        // A split group is never set here: only the split functions below make or end one (a new row is on its own; an edited one keeps
        // the group it has, or none).
        val toSave = t.copy(payee = clean(t.payee), note = clean(t.note), updatedAt = clock(), splitGroup = null)
        // The row and its tags are one step: both are written, or neither.
        transaction {
            if (t.id == null) {
                val id = insertOrThrow(FinanceDatabase.TRANSACTIONS, null, FinanceRows.transaction(toSave))
                writeTags(id, toSave.tagIds)
                toSave.copy(id = id)
            } else {
                val group = splitGroupOf(t.id)
                if (group != null) return@transaction savePartOfSplit(toSave.copy(splitGroup = group), group)
                val count = update(
                    FinanceDatabase.TRANSACTIONS,
                    FinanceRows.transaction(toSave),
                    "id = ?",
                    arrayOf(t.id.toString()),
                )
                if (count == 0) throw FinanceConflictException("This transaction no longer exists.")
                writeTags(t.id, toSave.tagIds)
                toSave
            }
        }
    }

    /**
     * Inserts multiple transactions in a single SQLite transaction: each is checked as [saveTransaction] checks one, and one that
     * fails (an account that has gone, a category of the wrong kind) takes every other back out, so it is all of them or none. Used for
     * the sample data and for a bank statement's rows.
     */
    suspend fun saveTransactions(transactions: List<FinanceTransaction>) = io {
        val now = clock()
        transaction {
            for (t in transactions) {
                validateShape(t.type, t.amount, t.accountId, t.toAccountId, t.categoryId)
                validatePerson(t.type, t.personId, t.id)
                validatePicture(t.picture)
                validateTags(t.type, t.tagIds)
                val toSave = t.copy(payee = clean(t.payee), note = clean(t.note), updatedAt = now, splitGroup = null)
                val id = insertOrThrow(FinanceDatabase.TRANSACTIONS, null, FinanceRows.transaction(toSave))
                writeTags(id, toSave.tagIds)
            }
        }
    }

    // ------------------------------------------------------------------ tags

    /** Every tag, by name. */
    suspend fun tags(): List<Tag> = io { readTags() }

    private fun SupportSQLiteDatabase.readTags(): List<Tag> =
        query(FinanceDatabase.TAGS, null, null, null, null, null, "name COLLATE NOCASE ASC, id ASC").map(FinanceRows::toTag)

    /**
     * Saves a tag, new or renamed. The name is tidied ([cleanTagName]), 1 to [MAX_TAG_NAME] characters, and new to the list whatever
     * its case - "tax" and "Tax" are one tag. Renaming keeps its id, so it is renamed on every transaction that has it.
     */
    suspend fun saveTag(tag: Tag): Tag = io {
        val name = cleanTagName(tag.name)
        tagNameFault(name)?.let { throw FinanceValidationException(it) }
        val clash = readTags().firstOrNull { it.id != tag.id && it.name.equals(name, ignoreCase = true) }
        if (clash != null) throw FinanceConflictException("You already have a tag called ${clash.name}.")
        val toSave = tag.copy(name = name)
        if (tag.id == null) {
            val made = toSave.copy(createdAt = clock())
            made.copy(id = insertOrThrow(FinanceDatabase.TAGS, null, FinanceRows.tag(made)))
        } else {
            val count = update(FinanceDatabase.TAGS, ContentValues().apply { put("name", name) }, "id = ?", arrayOf(tag.id.toString()))
            if (count == 0) throw FinanceConflictException("This tag no longer exists.")
            toSave
        }
    }

    /**
     * Deletes a tag. It comes off every transaction that had it - the transactions themselves are kept as they are, in every balance and
     * total - all in one step. Returns how many transactions it was on.
     */
    suspend fun deleteTag(id: Long): Int = io {
        transaction {
            val removed = delete(FinanceDatabase.TRANSACTION_TAGS, "tag_id = ?", arrayOf(id.toString()))
            delete(FinanceDatabase.TAGS, "id = ?", arrayOf(id.toString()))
            removed
        }
    }

    /** How many transactions each tag is on, by tag id (a tag on none is left out). */
    suspend fun tagUsage(): Map<Long, Int> = io {
        rawQuery("SELECT tag_id, COUNT(*) AS n FROM ${FinanceDatabase.TRANSACTION_TAGS} GROUP BY tag_id", null)
            .map { it.long("tag_id") to it.int("n") }
            .toMap()
    }

    /**
     * What the transactions with each tag came to over [range] - their expenses and their income apart, and how many - for the yearly
     * review. With [asOf], only what is dated before it: what has happened by then (see `settledCutoff`), so an entry made ahead counts
     * from its own day. Transfers never count (they have no tags, and are neither spending nor income). A tag with nothing in the range is
     * left out; a transaction with two tags counts under both.
     */
    suspend fun tagTotals(range: DateRange, asOf: LocalDateTime? = null): List<TagTotal> = io {
        val end = asOf?.takeIf { it.isBefore(range.end) } ?: range.end
        val totals = LinkedHashMap<Long, TagTotal>()
        rawQuery(
            "SELECT tt.tag_id AS tag_id, t.type AS type, SUM(t.amount) AS total, COUNT(*) AS n " +
                "FROM ${FinanceDatabase.TRANSACTION_TAGS} tt JOIN ${FinanceDatabase.TRANSACTIONS} t ON t.id = tt.transaction_id " +
                "WHERE t.type IN ('expense', 'income') AND t.occurred_at >= ${range.start.toStorage()} AND t.occurred_at < ${end.toStorage()} " +
                "GROUP BY tt.tag_id, t.type",
            null,
        ).map { Triple(it.long("tag_id"), it.string("type"), it.long("total") to it.int("n")) }
            .forEach { (tag, type, sum) ->
                val (amount, count) = sum
                val was = totals[tag] ?: TagTotal(tag)
                totals[tag] = if (type == TransactionType.Expense.key) {
                    was.copy(spent = amount, count = was.count + count)
                } else {
                    was.copy(received = amount, count = was.count + count)
                }
            }
        totals.values.toList()
    }

    /**
     * How many times the tags of a set of transactions have been read: one query a page or a batch, never one a row. Read by a test
     * that pins it; nothing in the app reads it.
     */
    internal var tagBatchReads: Int = 0
        private set

    /** [items] with their tags, read in one query for the whole list (see [tagLinks]). */
    private fun SupportSQLiteDatabase.withTags(items: List<FinanceTransaction>): List<FinanceTransaction> {
        if (items.isEmpty()) return items
        val links = tagLinks(items.mapNotNull { it.id })
        if (links.isEmpty()) return items.map { if (it.tagIds.isEmpty()) it else it.copy(tagIds = emptySet()) }
        return items.map { t -> t.copy(tagIds = links[t.id].orEmpty()) }
    }

    /**
     * The tags of the transactions [ids], by transaction id, in one query: by the ids themselves for a page, or - for more than
     * [INLINE_IDS], as a backup reads every transaction - the whole (small) link table, kept to the ones asked for.
     */
    private fun SupportSQLiteDatabase.tagLinks(ids: Collection<Long>): Map<Long, Set<Long>> {
        if (ids.isEmpty()) return emptyMap()
        tagBatchReads++
        val inline = ids.size <= INLINE_IDS
        val wanted = if (inline) null else ids.toHashSet()
        val where = if (inline) " WHERE transaction_id IN (${ids.joinToString(",")})" else ""
        val result = HashMap<Long, MutableSet<Long>>()
        rawQuery("SELECT transaction_id, tag_id FROM ${FinanceDatabase.TRANSACTION_TAGS}$where ORDER BY transaction_id, tag_id", null).use { cursor ->
            while (cursor.moveToNext()) {
                val transaction = cursor.getLong(0)
                if (wanted == null || transaction in wanted) result.getOrPut(transaction) { LinkedHashSet() } += cursor.getLong(1)
            }
        }
        return result
    }

    /** Makes [tagIds] exactly the tags on the transaction [transactionId]: the links it had go, these are written. */
    private fun SupportSQLiteDatabase.writeTags(transactionId: Long, tagIds: Set<Long>) {
        delete(FinanceDatabase.TRANSACTION_TAGS, "transaction_id = ?", arrayOf(transactionId.toString()))
        for (tag in tagIds) {
            insertOrThrow(
                FinanceDatabase.TRANSACTION_TAGS,
                null,
                ContentValues().apply {
                    put("transaction_id", transactionId)
                    put("tag_id", tag)
                },
            )
        }
    }

    /** The tags a transaction of [type] may carry: none on a transfer, and only tags that exist. */
    private fun SupportSQLiteDatabase.validateTags(type: TransactionType, tagIds: Set<Long>) {
        if (tagIds.isEmpty()) return
        if (type == TransactionType.Transfer) {
            throw FinanceValidationException("A transfer can't have tags: it only moves money between your own accounts.")
        }
        val known = scalarLong("SELECT COUNT(*) FROM ${FinanceDatabase.TAGS} WHERE id IN (${tagIds.joinToString(",")})") ?: 0
        if (known != tagIds.size.toLong()) throw FinanceValidationException("Choose tags from the list, or add them first.")
    }

    // --------------------------------------------------------- split payments

    /**
     * The rows of the split payment [group], in the order they were entered (by id), each with its tags: its parts. Empty when there
     * is no such group.
     */
    suspend fun splitParts(group: String): List<FinanceTransaction> = io { readSplitRows(group) }

    private fun SupportSQLiteDatabase.readSplitRows(group: String): List<FinanceTransaction> =
        query(FinanceDatabase.TRANSACTIONS, null, "split_group = ?", arrayOf(group), null, null, "id ASC")
            .map(FinanceRows::toTransaction)
            .let { withTags(it) }

    /** The split group the row [id] is a part of, or null for a row that is not (or that is not there). */
    private fun SupportSQLiteDatabase.splitGroupOf(id: Long): String? =
        rawQuery("SELECT split_group FROM ${FinanceDatabase.TRANSACTIONS} WHERE id = ?", arrayOf(id.toString()))
            .use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }

    /**
     * Saves one payment as a split into [parts] (see `Splits.kt`), as one step - **all of it or none of it**, in one SQL transaction -
     * and gives back the rows saved, in the order of [parts].
     *
     * [payment] says what the parts share: the type (an expense or an income, never a transfer), the account, the date and time, the
     * payee, who it was spent on, the tags and the picture; its amount is the total and must be what the [parts] add up to, each part
     * above zero with a category of the type's kind. The note and the category of [payment] itself are not used: a part's are its own.
     *
     * - **A new split** (no [group]): a part that names a row ([SplitPart.id]) is that existing plain row made into the first part - it
     *   keeps its id and when it was added; a row that is already in a split, or is not there, is refused. Every other part is a new row.
     * - **A split edited** ([group] is its id): a part that names a row of the group keeps it; a part without one is a new row; the rows
     *   of the group that no part names are deleted (their tag links go with them). A part that names a row of another group is refused.
     *
     * Every part gets the shared fields as [payment] has them, the tags written to each, and the group's id. A part that is refused
     * (a category of the wrong kind, an account that has gone) takes back whatever was written before it.
     */
    suspend fun saveSplit(payment: FinanceTransaction, parts: List<SplitPart>, group: String? = null): List<FinanceTransaction> = io {
        if (payment.type == TransactionType.Transfer) {
            throw FinanceValidationException("A transfer can't be split: it only moves money between your own accounts.")
        }
        if (parts.size < MIN_SPLIT_PARTS) throw FinanceValidationException("Split a payment into at least $MIN_SPLIT_PARTS parts.")
        if (parts.size > MAX_SPLIT_PARTS) throw FinanceValidationException("A payment can be split into at most $MAX_SPLIT_PARTS parts.")
        val named = parts.mapNotNull { it.id }
        if (named.size != named.toSet().size) throw FinanceValidationException("The same part is in the split twice.")
        val total = try {
            parts.fold(0L) { sum, part -> Math.addExact(sum, part.amount) }
        } catch (_: ArithmeticException) {
            throw FinanceValidationException("That is more than NormPix can keep.")
        }
        if (payment.amount != total) throw FinanceValidationException("The parts have to add up to the total.")
        validatePicture(payment.picture)
        validateTags(payment.type, payment.tagIds)
        val now = clock()
        val shared = payment.copy(payee = clean(payment.payee), toAccountId = null, updatedAt = now)
        transaction {
            val inGroup = if (group == null) emptyList() else readSplitRows(group)
            if (group != null && inGroup.isEmpty()) throw FinanceConflictException("This split payment no longer exists.")
            val groupId = group ?: UUID.randomUUID().toString()
            // The rows the parts keep, as they are stored now (what a part reuses it keeps of its own: when it was added, the repeating
            // payment that recorded it).
            val kept = HashMap<Long, FinanceTransaction>()
            for (id in named) {
                val row = inGroup.firstOrNull { it.id == id }
                    ?: if (group == null) readPlainRow(id) else throw FinanceConflictException("A part of this split payment no longer exists.")
                kept[id] = row
            }
            // Who it was spent on: an archived person keeps what is already theirs, but takes nothing new.
            validatePersonLink(shared.type, shared.personId) { kept.values.any { it.personId == shared.personId } }
            val saved = parts.map { part ->
                val old = part.id?.let { kept.getValue(it) }
                val row = shared.copy(
                    id = part.id,
                    amount = part.amount,
                    categoryId = part.categoryId,
                    note = clean(part.note),
                    recurringId = old?.recurringId ?: shared.recurringId,
                    createdAt = old?.createdAt ?: now,
                    splitGroup = groupId,
                )
                // Each part is checked as it is written, so one that fails takes the ones before it back out.
                validateShape(row.type, row.amount, row.accountId, null, row.categoryId)
                val written = if (row.id == null) {
                    row.copy(id = insertOrThrow(FinanceDatabase.TRANSACTIONS, null, FinanceRows.transaction(row)))
                } else {
                    val count = update(FinanceDatabase.TRANSACTIONS, FinanceRows.transaction(row), "id = ?", arrayOf(row.id.toString()))
                    if (count == 0) throw FinanceConflictException("A part of this split payment no longer exists.")
                    row
                }
                writeTags(written.id!!, written.tagIds)
                written
            }
            // The rows of the group that are no longer parts go (their tag links with them: those cascade).
            val still = saved.mapTo(HashSet()) { it.id }
            for (gone in inGroup) if (gone.id !in still) delete(FinanceDatabase.TRANSACTIONS, "id = ?", arrayOf(gone.id.toString()))
            saved
        }
    }

    /** The row [id] for a new split to start from: it has to be there and be on its own - not already part of a split. */
    private fun SupportSQLiteDatabase.readPlainRow(id: Long): FinanceTransaction {
        val row = query(FinanceDatabase.TRANSACTIONS, null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) FinanceRows.toTransaction(it) else null }
            ?: throw FinanceConflictException("This transaction no longer exists.")
        if (row.splitGroup != null) throw FinanceValidationException("This payment is already split.")
        return withTags(listOf(row)).single()
    }

    /**
     * Saves [row], one part of the split payment [group], with what it has of its own (its category, amount and note) and **what the
     * parts share changed in every part** - the account, the date and time, the payee, who it was spent on, the tags and the picture -
     * in the one step the caller is in. A part can't be changed between spending and income, nor to a transfer: that is a different
     * payment; unsplit it first.
     */
    private fun SupportSQLiteDatabase.savePartOfSplit(row: FinanceTransaction, group: String): FinanceTransaction {
        val parts = readSplitRows(group)
        val old = parts.firstOrNull { it.id == row.id } ?: throw FinanceConflictException("This transaction no longer exists.")
        if (row.type != old.type) {
            throw FinanceValidationException("This is one part of a split payment, so it can't change between spending and income. Unsplit it first.")
        }
        val count = update(FinanceDatabase.TRANSACTIONS, FinanceRows.transaction(row), "id = ?", arrayOf(row.id.toString()))
        if (count == 0) throw FinanceConflictException("This transaction no longer exists.")
        writeTags(row.id!!, row.tagIds)
        for (other in parts) {
            if (other.id == row.id) continue
            update(
                FinanceDatabase.TRANSACTIONS,
                ContentValues().apply {
                    put("account_id", row.accountId)
                    put("occurred_at", row.occurredAt.toStorage())
                    putNullable("payee", row.payee)
                    putNullable("person_id", row.personId)
                    putNullable("picture", row.picture)
                    put("updated_at", row.updatedAt.toStorage())
                },
                "id = ?",
                arrayOf(other.id.toString()),
            )
            writeTags(other.id!!, row.tagIds)
        }
        return row
    }

    /**
     * Makes the split payment [group] one payment again, as one step: the first part's row (the lowest id) becomes the whole payment -
     * the total of every part, the category [categoryId] (the largest part's when none is given), the parts' notes joined by " · " -
     * and the other parts' rows go. The account, date, payee, person, tags and picture are the parts' shared ones and stay. A group that
     * does not hold together (see `splitFault`) is refused, whatever its fault, except one with a single part left, which is simply
     * a payment on its own. Gives back the one row.
     */
    suspend fun unsplit(group: String, categoryId: Long? = null): FinanceTransaction = io {
        transaction {
            val rows = readSplitRows(group)
            if (rows.isEmpty()) throw FinanceConflictException("This split payment no longer exists.")
            splitFault(rows)?.takeIf { it != SplitFault.TooFew }?.let {
                throw FinanceValidationException("This split payment doesn't hold together: it ${it.problem}. Check and recalculate, in Settings, can put it right.")
            }
            val first = rows.first()
            val category = categoryId ?: largestPartCategory(rows)
            val total = rows.sumOf { it.amount }
            validateShape(first.type, total, first.accountId, null, category)
            val whole = first.copy(
                amount = total,
                categoryId = category,
                note = joinedSplitNotes(rows),
                splitGroup = null,
                updatedAt = clock(),
            )
            val count = update(FinanceDatabase.TRANSACTIONS, FinanceRows.transaction(whole), "id = ?", arrayOf(first.id.toString()))
            if (count == 0) throw FinanceConflictException("This split payment no longer exists.")
            for (other in rows.drop(1)) delete(FinanceDatabase.TRANSACTIONS, "id = ?", arrayOf(other.id.toString()))
            whole
        }
    }

    /**
     * Takes the split mark off every row of [group]: each row stays as it is, in every total, and is simply a payment of its own now.
     * What Check and recalculate does for a group that no longer holds together (a part left alone; parts on different accounts, dates
     * or kinds), where merging the rows would have to choose one account, one date or one kind. Gives back how many rows it was on.
     */
    suspend fun releaseSplit(group: String): Int = io {
        val released = update(
            FinanceDatabase.TRANSACTIONS,
            ContentValues().apply {
                putNull("split_group")
                put("updated_at", clock().toStorage())
            },
            "split_group = ?",
            arrayOf(group),
        )
        if (released == 0) throw FinanceConflictException("This split payment no longer exists.")
        released
    }

    // ----------------------------------------------------------------- goals

    /** Every savings goal, in the order the user keeps them. */
    suspend fun goals(): List<SavingsGoal> = io { readGoals() }

    private fun SupportSQLiteDatabase.readGoals(): List<SavingsGoal> =
        query(FinanceDatabase.GOALS, null, null, null, null, null, "sort_order ASC, id ASC").map(FinanceRows::toGoal)

    private fun SupportSQLiteDatabase.goalById(id: Long): SavingsGoal? =
        query(FinanceDatabase.GOALS, null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) FinanceRows.toGoal(it) else null }

    /**
     * Saves a goal, new or edited. It must hold (`SavingsGoal.fault`: a name of 1 to 40 characters, a target above zero, one emoji or
     * none). A goal linked to an account keeps no amount of its own - its [SavingsGoal.saved] is stored as zero - and the account must
     * be one a goal can count: not archived, not a card, not a loan (an account the goal already counts is left as it is, whatever it
     * has become since). A new goal goes last in the order; an edited one keeps its place and its date of making.
     */
    suspend fun saveGoal(goal: SavingsGoal): SavingsGoal = io {
        val toSave = goal.copy(
            name = cleanGoalName(goal.name),
            emoji = goal.emoji?.trim()?.takeIf { it.isNotEmpty() },
            saved = if (goal.accountId != null) 0L else goal.saved,
        )
        toSave.fault()?.let { throw FinanceValidationException(it.advice) }
        val stored = goal.id?.let { goalById(it) ?: throw FinanceConflictException("This goal no longer exists.") }
        val accountId = toSave.accountId
        if (accountId != null && accountId != stored?.accountId) {
            val account = query(FinanceDatabase.ACCOUNTS, null, "id = ?", arrayOf(accountId.toString()), null, null, null)
                .use { if (it.moveToFirst()) FinanceRows.toAccount(it) else null }
                ?: throw FinanceValidationException("Choose an account from the list.")
            if (account.archived) throw FinanceValidationException("That account is archived. Choose another one, or restore it first.")
            if (account.kind == AccountKind.Card || account.isLoan) {
                throw FinanceValidationException("A goal can count a cash, bank, wallet, savings or other account - not a card or a loan.")
            }
        }
        val now = clock()
        if (stored == null) {
            val order = (scalarLong("SELECT MAX(sort_order) FROM ${FinanceDatabase.GOALS}") ?: -1L).toInt() + 1
            val made = toSave.copy(sortOrder = order, createdAt = now, updatedAt = now)
            made.copy(id = insertOrThrow(FinanceDatabase.GOALS, null, FinanceRows.goal(made)))
        } else {
            val edited = toSave.copy(sortOrder = stored.sortOrder, createdAt = stored.createdAt, updatedAt = now)
            update(FinanceDatabase.GOALS, FinanceRows.goal(edited), "id = ?", arrayOf(stored.id.toString()))
            edited
        }
    }

    /** Deletes a goal. It is only a target: no account, balance or entry changes. */
    suspend fun deleteGoal(id: Long) = io {
        if (delete(FinanceDatabase.GOALS, "id = ?", arrayOf(id.toString())) == 0) throw FinanceConflictException("This goal no longer exists.")
    }

    /**
     * Adds [delta] to what a goal on its own has saved - positive for Add money, negative for Take out - and gives the goal back. It
     * makes no ledger entry: it does not move money between accounts. Refused for a goal linked to an account (what is saved there is
     * that account's balance) and for a change that would leave less than nothing saved.
     */
    suspend fun adjustGoalSaved(id: Long, delta: Long): SavingsGoal = io {
        if (delta == 0L) throw FinanceValidationException("Enter an amount above zero.")
        transaction {
            val goal = goalById(id) ?: throw FinanceConflictException("This goal no longer exists.")
            if (goal.isLinked) {
                throw FinanceValidationException("This goal counts the balance of an account, so there is nothing to add or take out here. Record the money in that account instead.")
            }
            val next = try {
                Math.addExact(goal.saved, delta)
            } catch (_: ArithmeticException) {
                throw FinanceValidationException("That is more than NormPix can keep.")
            }
            if (next < 0) throw FinanceValidationException("You can't take out more than is saved here.")
            val now = clock()
            update(
                FinanceDatabase.GOALS,
                ContentValues().apply {
                    put("saved", next)
                    put("updated_at", now.toStorage())
                },
                "id = ?",
                arrayOf(id.toString()),
            )
            goal.copy(saved = next, updatedAt = now)
        }
    }

    /**
     * Records one EMI on a loan (see `Loans.kt`): a Transfer of the principal from [LoanEmi.fromAccountId] onto the loan - what is owed
     * goes down by that and no money is spent - and an Expense of the interest from the same account in the interest category - which
     * is spending. The two are saved in **one SQL transaction**, each checked as it is written, so either both are in the ledger or
     * neither is: one that fails (a category of the wrong kind, an account that has gone) takes the other back out with it. A part of
     * zero makes no entry. Returns what was saved, with its ids.
     */
    suspend fun recordLoanEmi(emi: LoanEmi): List<FinanceTransaction> = io {
        val loan = query(FinanceDatabase.ACCOUNTS, null, "id = ?", arrayOf(emi.loanId.toString()), null, null, null)
            .use { if (it.moveToFirst()) FinanceRows.toAccount(it) else null }
            ?: throw FinanceConflictException("This loan no longer exists.")
        if (!loan.isLoan) throw FinanceValidationException("This account isn't a loan.")
        val entries = loanEmiEntries(emi, clock())
        transaction {
            entries.map { t ->
                validateShape(t.type, t.amount, t.accountId, t.toAccountId, t.categoryId)
                t.copy(id = insertOrThrow(FinanceDatabase.TRANSACTIONS, null, FinanceRows.transaction(t)))
            }
        }
    }

    /**
     * The latest payment made onto the loan [loanId] as of [asOf] (a transfer onto it dated before that): which day the last EMI was
     * paid, and from which account - what Record EMI starts from. Null when nothing has been paid onto it.
     */
    suspend fun lastLoanPayment(loanId: Long, asOf: LocalDateTime): FinanceTransaction? = io {
        query(
            FinanceDatabase.TRANSACTIONS,
            null,
            "type = ? AND to_account_id = ? AND occurred_at < ${asOf.toStorage()}",
            arrayOf(TransactionType.Transfer.key, loanId.toString()),
            null,
            null,
            "occurred_at DESC, id DESC",
            "1",
        ).use { if (it.moveToFirst()) FinanceRows.toTransaction(it) else null }
            ?.let { withTags(listOf(it)).single() }
    }

    // The picture is only ever a file name the app made: never a path, so a row can't point outside the app's own pictures.
    private fun validatePicture(name: String?) {
        if (name != null && !isPictureName(name)) throw FinanceValidationException("That picture can't be attached. Attach it again.")
    }

    /** The names of every picture a transaction has attached (see `TransactionPictures`). */
    suspend fun pictureNames(): Set<String> = io {
        rawQuery("SELECT picture FROM ${FinanceDatabase.TRANSACTIONS} WHERE picture IS NOT NULL", null).map { it.string("picture") }.toSet()
    }

    /**
     * Deletes the transaction - and, when it is one part of a split payment, the **whole payment**, every part, in the same step (a
     * part alone would leave the others, and the payment, wrong). Gives back how many rows went.
     */
    suspend fun deleteTransaction(id: Long) = io {
        transaction {
            val group = splitGroupOf(id)
            if (group == null) {
                delete(FinanceDatabase.TRANSACTIONS, "id = ?", arrayOf(id.toString()))
            } else {
                delete(FinanceDatabase.TRANSACTIONS, "split_group = ?", arrayOf(group))
            }
        }
    }

    suspend fun transactionById(id: Long): FinanceTransaction? = io {
        query(FinanceDatabase.TRANSACTIONS, null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) FinanceRows.toTransaction(it) else null }
            ?.let { withTags(listOf(it)).single() }
    }

    /** WHERE clause + string bind args. Numbers are inlined: they are `Long`s, never text. */
    private fun where(q: TransactionQuery): Pair<String, List<String>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        q.range?.let { range ->
            clauses += "t.occurred_at >= ${range.start.toStorage()} AND t.occurred_at < ${range.end.toStorage()}"
        }
        q.before?.let { clauses += "t.occurred_at < ${it.toStorage()}" }
        q.notBefore?.let { clauses += "t.occurred_at >= ${it.toStorage()}" }
        if (q.types.isNotEmpty()) {
            clauses += "t.type IN (${marks(q.types.size)})"
            args += q.types.map { it.key }
        }
        if (q.accountIds.isNotEmpty()) {
            val ids = q.accountIds.joinToString(",")
            clauses += "(t.account_id IN ($ids) OR t.to_account_id IN ($ids))"
        }
        if (q.categoryIds.isNotEmpty()) {
            // A category stands for its sub-categories too.
            val ids = q.categoryIds.joinToString(",")
            clauses += "(t.category_id IN ($ids) OR c.parent_id IN ($ids))"
        }
        if (q.personIds.isNotEmpty()) clauses += "t.person_id IN (${q.personIds.joinToString(",")})"
        if (q.tagIds.isNotEmpty()) {
            // Any of them: a transaction with one of the tags is in, however many of the others it has.
            clauses += "EXISTS (SELECT 1 FROM ${FinanceDatabase.TRANSACTION_TAGS} tt " +
                "WHERE tt.transaction_id = t.id AND tt.tag_id IN (${q.tagIds.joinToString(",")}))"
        }
        q.minAmount?.let { clauses += "t.amount >= $it" }
        q.maxAmount?.let { clauses += "t.amount <= $it" }
        val text = q.text?.trim()
        if (!text.isNullOrEmpty()) {
            val pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            clauses += "(t.payee LIKE ? ESCAPE '\\' OR t.note LIKE ? ESCAPE '\\' " +
                "OR c.name LIKE ? ESCAPE '\\' OR a.name LIKE ? ESCAPE '\\' OR p.name LIKE ? ESCAPE '\\' " +
                "OR EXISTS (SELECT 1 FROM ${FinanceDatabase.TRANSACTION_TAGS} st JOIN ${FinanceDatabase.TAGS} g ON g.id = st.tag_id " +
                "WHERE st.transaction_id = t.id AND g.name LIKE ? ESCAPE '\\'))"
            repeat(6) { args += pattern }
        }
        return (if (clauses.isEmpty()) "1 = 1" else clauses.joinToString(" AND ")) to args
    }

    private val joins =
        "FROM ${FinanceDatabase.TRANSACTIONS} t " +
            "LEFT JOIN ${FinanceDatabase.CATEGORIES} c ON c.id = t.category_id " +
            "LEFT JOIN ${FinanceDatabase.PEOPLE} p ON p.id = t.person_id " +
            "JOIN ${FinanceDatabase.ACCOUNTS} a ON a.id = t.account_id"

    /** Newest-first page of transactions matching [query]. */
    suspend fun transactions(
        query: TransactionQuery,
        after: TransactionCursor? = null,
        limit: Int = 50,
    ): TransactionPage = io {
        val (where, args) = where(query)
        val cursorClause = after?.let {
            " AND (t.occurred_at < ${it.occurredAtMs} OR (t.occurred_at = ${it.occurredAtMs} AND t.id < ${it.id}))"
        } ?: ""
        val rows = rawQuery(
            "SELECT t.* $joins WHERE $where$cursorClause ORDER BY t.occurred_at DESC, t.id DESC LIMIT ${limit + 1}",
            args.toTypedArray(),
        ).map(FinanceRows::toTransaction)
        val hasMore = rows.size > limit
        // The page's tags in one more query, whatever its size (not one a row).
        val items = withTags(rows.take(limit))
        TransactionPage(
            items,
            if (hasMore && items.isNotEmpty()) {
                TransactionCursor(items.last().occurredAt.toStorage(), items.last().id!!)
            } else {
                null
            },
        )
    }

    /** Income/expense totals for everything matching [query] (transfers are never income or spending). */
    suspend fun summaryFor(query: TransactionQuery): PeriodSummary = io {
        val (where, args) = where(query)
        var income = 0L
        var expense = 0L
        var count = 0
        rawQuery(
            "SELECT t.type AS type, SUM(t.amount) AS total, COUNT(*) AS n $joins " +
                "WHERE $where AND t.type IN ('income', 'expense') GROUP BY t.type",
            args.toTypedArray(),
        ).map { Triple(it.string("type"), it.long("total"), it.int("n")) }
            .forEach { (type, total, n) ->
                count += n
                if (type == "income") income = total
                if (type == "expense") expense = total
            }
        PeriodSummary(income = income, expense = expense, count = count)
    }

    suspend fun summary(range: DateRange): PeriodSummary = summaryFor(TransactionQuery(range = range))

    /** Everything dated from [from] up to (not including) [to], oldest first: what a forecast reads of the days ahead. */
    suspend fun transactionsBetween(from: LocalDateTime, to: LocalDateTime): List<FinanceTransaction> = io {
        query(
            FinanceDatabase.TRANSACTIONS,
            null,
            "occurred_at >= ${from.toStorage()} AND occurred_at < ${to.toStorage()}",
            null,
            null,
            null,
            "occurred_at ASC, id ASC",
        ).map(FinanceRows::toTransaction).let { withTags(it) }
    }

    /**
     * What was spent from [from] up to (not including) [to] that no repeating payment recorded - on every account, transfers never
     * counting as spending. A forecast reads a day's share of this as what the days to come will cost besides what is scheduled.
     */
    suspend fun spentOutsideRepeats(from: LocalDateTime, to: LocalDateTime): Long = io {
        scalarLong(
            "SELECT SUM(amount) FROM ${FinanceDatabase.TRANSACTIONS} WHERE type = 'expense' AND recurring_id IS NULL " +
                "AND occurred_at >= ${from.toStorage()} AND occurred_at < ${to.toStorage()}",
        ) ?: 0L
    }

    /**
     * The expenses with a payee that no repeating payment recorded, dated from [from] up to (not including) [to], oldest first: what
     * the detector of payments that look like they repeat reads.
     */
    suspend fun expensesOutsideRepeats(from: LocalDateTime, to: LocalDateTime): List<FinanceTransaction> = io {
        query(
            FinanceDatabase.TRANSACTIONS,
            null,
            "type = 'expense' AND recurring_id IS NULL AND payee IS NOT NULL AND TRIM(payee) != '' " +
                "AND occurred_at >= ${from.toStorage()} AND occurred_at < ${to.toStorage()}",
            null,
            null,
            null,
            "occurred_at ASC, id ASC",
        ).map(FinanceRows::toTransaction).let { withTags(it) }
    }

    /**
     * The payees the user said "not this one" to when they were offered as a repeating payment (their `payeeKey`s). Kept in the
     * `meta` table - no migration - as one line each; not in a backup, and kept when one is restored or an Undo puts the ledger back.
     */
    suspend fun dismissedRepeatPayees(): Set<String> = io { readDismissedRepeats() }

    /** Remembers that the payee [key] (a `payeeKey`) is not to be offered as a repeating payment again. */
    suspend fun dismissRepeatPayee(key: String) = io {
        if (key.isNotBlank()) setMeta(DISMISSED_REPEATS, (readDismissedRepeats() + key).sorted().joinToString("\n"))
    }

    private fun SupportSQLiteDatabase.readDismissedRepeats(): Set<String> =
        meta(DISMISSED_REPEATS)?.split('\n')?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    /** Totals per category for [type] (income or expense), largest first. */
    suspend fun categoryTotals(range: DateRange, type: TransactionType): List<CategoryTotal> = io {
        rawQuery(
            "SELECT category_id, SUM(amount) AS total, COUNT(*) AS n FROM ${FinanceDatabase.TRANSACTIONS} " +
                "WHERE type = ? AND occurred_at >= ${range.start.toStorage()} AND occurred_at < ${range.end.toStorage()} " +
                "GROUP BY category_id ORDER BY total DESC",
            arrayOf(type.key),
        ).map { CategoryTotal(categoryId = it.long("category_id"), amount = it.long("total"), count = it.int("n")) }
    }

    /**
     * The categories [type] (income or expense) was recorded in most over the last [days] days, most used first - ties go to
     * the one used most recently - for the picker's "Frequently used" shortcuts. Archived categories are left out. Empty
     * until there is some history, so a new ledger shows no shortcuts rather than arbitrary ones.
     */
    suspend fun frequentCategoryIds(type: TransactionType, limit: Int = 6, days: Int = 180): List<Long> = io {
        val since = startOfDay(clock()).minusDays(days.toLong()).toStorage()
        rawQuery(
            "SELECT t.category_id AS id FROM ${FinanceDatabase.TRANSACTIONS} t " +
                "JOIN ${FinanceDatabase.CATEGORIES} c ON c.id = t.category_id " +
                "WHERE t.type = ? AND c.archived = 0 AND t.occurred_at >= $since " +
                "GROUP BY t.category_id ORDER BY COUNT(*) DESC, MAX(t.occurred_at) DESC LIMIT ${limit.coerceAtLeast(0)}",
            arrayOf(type.key),
        ).map { it.long("id") }
    }

    /**
     * The category the latest [type] (income or expense) with this [payee] was recorded under, if that category is still in
     * use - so an entry for a payee seen before can start where the user filed the last one. The whole payee must match,
     * ignoring case; null when nothing does.
     */
    suspend fun lastCategoryForPayee(payee: String, type: TransactionType): Long? = io {
        val name = payee.trim()
        if (name.isEmpty()) return@io null
        // The latest entry's choice or none: an older entry's category is not dug up when the latest one's was archived.
        rawQuery(
            "SELECT t.category_id AS id, c.archived AS archived FROM ${FinanceDatabase.TRANSACTIONS} t " +
                "JOIN ${FinanceDatabase.CATEGORIES} c ON c.id = t.category_id " +
                "WHERE t.type = ? AND t.payee = ? COLLATE NOCASE " +
                "ORDER BY t.occurred_at DESC, t.id DESC LIMIT 1",
            arrayOf(type.key, name),
        ).map { if (it.int("archived") == 0) it.long("id") else null }.firstOrNull()
    }

    /**
     * Transactions already recorded that may be the one described here entered again - for an entry made from a shared
     * payment message, which can be shared twice. A note that names the same [reference] matches whatever else differs.
     * Otherwise the same [type] and [amount], with the same payee where both have one, near [occurredAt]: within
     * [SAME_PAYMENT_WINDOW] of it, or on the same day when only the day is known ([timeKnown] false).
     *
     * A payment's reference is its own, so one recorded with a different reference is a different payment, however alike
     * otherwise: the same cab ride taken in the morning and again in the evening is two rides. Only finds them, newest
     * first: whether to save anyway is the user's call (two coffees within the hour are both real).
     */
    suspend fun possibleDuplicates(
        type: TransactionType?,
        amount: Long?,
        occurredAt: LocalDateTime,
        payee: String?,
        reference: String?,
        limit: Int = 3,
        /** False when [occurredAt]'s time of day stands in for one the message didn't give. */
        timeKnown: Boolean = true,
    ): List<FinanceTransaction> = io {
        val found = LinkedHashMap<Long, FinanceTransaction>()
        val ref = reference?.trim()?.takeIf { it.isNotEmpty() }
        if (ref != null) {
            val pattern = "%" + ref.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            rawQuery(
                "SELECT * FROM ${FinanceDatabase.TRANSACTIONS} WHERE note LIKE ? ESCAPE '\\' ORDER BY occurred_at DESC, id DESC LIMIT $limit",
                arrayOf(pattern),
            ).map(FinanceRows::toTransaction).forEach { found[it.id!!] = it }
        }
        if (type != null && amount != null) {
            val from = if (timeKnown) occurredAt.minus(SAME_PAYMENT_WINDOW) else startOfDay(occurredAt)
            val until = if (timeKnown) occurredAt.plus(SAME_PAYMENT_WINDOW) else startOfDay(occurredAt).plusDays(1)
            val name = payee?.trim()?.takeIf { it.isNotEmpty() }
            rawQuery(
                "SELECT * FROM ${FinanceDatabase.TRANSACTIONS} WHERE type = ? AND amount = $amount " +
                    "AND occurred_at >= ${from.toStorage()} AND occurred_at ${if (timeKnown) "<=" else "<"} ${until.toStorage()} " +
                    "ORDER BY occurred_at DESC, id DESC",
                arrayOf(type.key),
            ).map(FinanceRows::toTransaction)
                .filter { other -> name == null || other.payee == null || other.payee.equals(name, ignoreCase = true) }
                .filter { other -> ref == null || referenceIn(other.note).let { it == null || it.equals(ref, ignoreCase = true) } }
                .forEach { found.putIfAbsent(it.id!!, it) }
        }
        withTags(found.values.take(limit))
    }

    /** The reference an entry made from a shared payment carries in its note ("Ref 428312345678"), or null. */
    private fun referenceIn(note: String?): String? = note?.let { noteReference.find(it)?.groupValues?.get(1) }

    private val noteReference = Regex("""\bRef\s+([A-Za-z0-9]{6,35})\b""")

    /** One entry per calendar day in [range] that has [type] activity. */
    suspend fun dailyTotals(range: DateRange, type: TransactionType): List<DailyTotal> = io {
        rawQuery(
            "SELECT strftime('%Y-%m-%d', occurred_at / 1000, 'unixepoch') AS day, SUM(amount) AS total " +
                "FROM ${FinanceDatabase.TRANSACTIONS} " +
                "WHERE type = ? AND occurred_at >= ${range.start.toStorage()} AND occurred_at < ${range.end.toStorage()} " +
                "GROUP BY day ORDER BY day",
            arrayOf(type.key),
        ).map { DailyTotal(day = LocalDate.parse(it.string("day")), amount = it.long("total")) }
    }

    /**
     * Income and expense per month from [from] through [to] inclusive - months without activity are included as zeros. With
     * [asOf], only what is dated before it (the month in progress so far, see `settledCutoff`).
     */
    suspend fun monthlyTotals(from: MonthKey, to: MonthKey, asOf: LocalDateTime? = null): List<MonthTotals> = io {
        val end = asOf?.takeIf { it.isBefore(to.end) } ?: to.end
        val income = mutableMapOf<Int, Long>()
        val expense = mutableMapOf<Int, Long>()
        rawQuery(
            "SELECT CAST(strftime('%Y%m', occurred_at / 1000, 'unixepoch') AS INTEGER) AS month, type, SUM(amount) AS total " +
                "FROM ${FinanceDatabase.TRANSACTIONS} " +
                "WHERE type IN ('income', 'expense') AND occurred_at >= ${from.start.toStorage()} AND occurred_at < ${end.toStorage()} " +
                "GROUP BY month, type",
            null,
        ).map { Triple(it.int("month"), it.string("type"), it.long("total")) }
            .forEach { (month, type, total) ->
                if (type == "income") income[month] = total
                if (type == "expense") expense[month] = total
            }
        generateSequence(from) { it.next }
            .takeWhile { !it.isAfter(to) }
            .map { MonthTotals(it, income[it.toInt()] ?: 0, expense[it.toInt()] ?: 0) }
            .toList()
    }

    /** How many transactions are dated at or after [from]: the upcoming ones, when it is the settled cutoff. */
    suspend fun countFrom(from: LocalDateTime): Int = io {
        (scalarLong("SELECT COUNT(*) FROM ${FinanceDatabase.TRANSACTIONS} WHERE occurred_at >= ${from.toStorage()}") ?: 0).toInt()
    }

    /** The first month with any recorded transaction, if any. */
    suspend fun firstActiveMonth(): MonthKey? = io {
        scalarLong("SELECT MIN(occurred_at) FROM ${FinanceDatabase.TRANSACTIONS}")?.let { MonthKey.of(it.toLocalDateTime()) }
    }

    // -------------------------------------------------------------- budgets

    suspend fun budgetRows(): List<Budget> = io { readBudgets() }

    private fun SupportSQLiteDatabase.readBudgets(): List<Budget> =
        query(FinanceDatabase.BUDGETS, null, null, null, null, null, "effective_month ASC").map(FinanceRows::toBudget)

    /**
     * Sets (or, with a null [amount], ends) the budget for a target from
     * [from] onward. Earlier months keep whatever applied to them.
     */
    suspend fun setBudget(categoryId: Long? = null, from: MonthKey, amount: Long?) = io {
        if (amount != null && amount <= 0) throw FinanceValidationException("Enter a budget greater than zero.")
        if (categoryId != null) {
            val type = query(
                FinanceDatabase.CATEGORIES,
                arrayOf("type"),
                "id = ?",
                arrayOf(categoryId.toString()),
                null,
                null,
                null,
            ).use { if (it.moveToFirst()) it.getString(0) else null }
            if (type != CategoryType.Expense.key) {
                throw FinanceValidationException("Budgets can only be set for expense categories.")
            }
        }
        val now = clock().toStorage()
        transaction {
            val existing = query(
                FinanceDatabase.BUDGETS,
                arrayOf("id"),
                if (categoryId == null) {
                    "category_id IS NULL AND effective_month = ?"
                } else {
                    "category_id = ? AND effective_month = ?"
                },
                listOfNotNull(categoryId?.toString(), from.toInt().toString()).toTypedArray(),
                null,
                null,
                null,
            ).use { if (it.moveToFirst()) it.getLong(0) else null }
            if (existing != null) {
                update(
                    FinanceDatabase.BUDGETS,
                    ContentValues().apply {
                        putNullable("amount", amount)
                        put("updated_at", now)
                    },
                    "id = ?",
                    arrayOf(existing.toString()),
                )
            } else {
                insertOrThrow(
                    FinanceDatabase.BUDGETS,
                    null,
                    ContentValues().apply {
                        putNullable("category_id", categoryId)
                        putNullable("amount", amount)
                        put("effective_month", from.toInt())
                        put("created_at", now)
                        put("updated_at", now)
                    },
                )
            }
        }
    }

    /**
     * [month]'s budgets against what was spent in it - with [asOf], what was spent by then (see `settledCutoff`). From the
     * current month on, a budget on an archived category is left out: nothing can be spent in it any more, and its unspent limit
     * would only add to what is "left to spend". Past months keep theirs, as they were.
     */
    suspend fun budgetOverview(month: MonthKey, asOf: LocalDateTime? = null): BudgetOverview {
        val limits = withoutArchived(resolveBudgets(budgetRows(), month), month)
        val totals = categoryTotals(asOf?.let { month.range.settledBy(it) } ?: month.range, TransactionType.Expense)
        val parents = io { parentIds() }
        return buildBudgetOverview(
            month = month,
            limits = limits,
            spentByCategory = foldIntoParents(totals.associate { it.categoryId to it.amount }, parents),
            totalExpense = totals.sumOf { it.amount },
            parents = parents,
        )
    }

    /** [limits] without the budgets on archived categories, from the current month on (see [budgetOverview]). */
    private suspend fun withoutArchived(limits: Map<Long?, Long>, month: MonthKey): Map<Long?, Long> {
        if (month.isBefore(MonthKey.of(clock())) || limits.keys.all { it == null }) return limits
        val archived = io {
            rawQuery("SELECT id FROM ${FinanceDatabase.CATEGORIES} WHERE archived = 1", null).map { it.long("id") }.toSet()
        }
        return if (archived.isEmpty()) limits else limits.filterKeys { it == null || it !in archived }
    }

    /**
     * Month-by-month budget results, newest first, for months (up to
     * [maxMonths] back from [until]) that had any budget in effect. With [asOf], spending dated from it on is left out (the
     * month in progress so far).
     */
    suspend fun budgetHistory(until: MonthKey, maxMonths: Int = 24, asOf: LocalDateTime? = null): List<BudgetHistoryEntry> {
        val rows = budgetRows()
        if (rows.isEmpty()) return emptyList()
        val parents = io { parentIds() }
        var start = rows.first().effectiveMonth
        val earliest = until.addMonths(-(maxMonths - 1))
        if (start.isBefore(earliest)) start = earliest
        if (start.isAfter(until)) return emptyList()
        val historyEnd = asOf?.takeIf { it.isBefore(until.end) } ?: until.end

        val byMonth = io {
            val result = mutableMapOf<Int, MutableMap<Long, Long>>()
            rawQuery(
                "SELECT CAST(strftime('%Y%m', occurred_at / 1000, 'unixepoch') AS INTEGER) AS month, category_id, SUM(amount) AS total " +
                    "FROM ${FinanceDatabase.TRANSACTIONS} " +
                    "WHERE type = 'expense' AND occurred_at >= ${start.start.toStorage()} AND occurred_at < ${historyEnd.toStorage()} " +
                    "GROUP BY month, category_id",
                null,
            ).map { Triple(it.int("month"), it.long("category_id"), it.long("total")) }
                .forEach { (month, category, total) -> result.getOrPut(month) { mutableMapOf() }[category] = total }
            result
        }

        val entries = mutableListOf<BudgetHistoryEntry>()
        var m = until
        while (!m.isBefore(start)) {
            val limits = withoutArchived(resolveBudgets(rows, m), m)
            if (limits.isNotEmpty()) {
                val categories = byMonth[m.toInt()] ?: emptyMap()
                entries += BudgetHistoryEntry(
                    buildBudgetOverview(
                        month = m,
                        limits = limits,
                        spentByCategory = foldIntoParents(categories, parents),
                        totalExpense = categories.values.sum(),
                        parents = parents,
                    ),
                )
            }
            m = m.previous
        }
        return entries
    }

    // ------------------------------------------------------------ recurring

    suspend fun recurringRules(): List<RecurringRule> = io { readRules() }

    private fun SupportSQLiteDatabase.readRules(): List<RecurringRule> = query(
        FinanceDatabase.RECURRING_RULES,
        null,
        null,
        null,
        null,
        null,
        "active DESC, next_date ASC, id ASC",
    ).map(FinanceRows::toRecurring)

    suspend fun saveRecurringRule(rule: RecurringRule): RecurringRule = io {
        validateShape(rule.type, rule.amount, rule.accountId, rule.toAccountId, rule.categoryId)
        validateRulePerson(rule)
        if (rule.interval < 1) throw FinanceValidationException("Repeat at least every 1.")
        val end = rule.endDate
        if (end != null && end.isBefore(startOfDay(rule.startDate))) {
            throw FinanceValidationException("The end date must be after the start date.")
        }
        val toSave = rule.copy(payee = clean(rule.payee), note = clean(rule.note), updatedAt = clock())
        if (rule.id == null) {
            val id = insertOrThrow(FinanceDatabase.RECURRING_RULES, null, FinanceRows.recurring(toSave))
            toSave.copy(id = id)
        } else {
            update(FinanceDatabase.RECURRING_RULES, FinanceRows.recurring(toSave), "id = ?", arrayOf(rule.id.toString()))
            toSave
        }
    }

    /**
     * Saves [t] and makes it the first occurrence of a new repeating payment, as one step: either both are written or neither
     * is. The rule starts at the transaction's own date and time, repeats as [schedule] says, and carries the
     * transaction's details - amount, accounts, category, payee, note and who it was spent on - so what it records later is
     * what this one was; [t] is linked to it. The rule's next date is the occurrence after this one, so nothing is recorded
     * twice; any that already fall due (a first date in the past) are recorded by [processRecurring].
     *
     * [t] must not already belong to a rule: changing a rule's schedule is the rule's own editor's job.
     */
    suspend fun saveTransactionAndRepeat(t: FinanceTransaction, schedule: RepeatSchedule): Pair<FinanceTransaction, RecurringRule> = io {
        validateShape(t.type, t.amount, t.accountId, t.toAccountId, t.categoryId)
        validatePerson(t.type, t.personId, t.id)
        validatePicture(t.picture)
        validateTags(t.type, t.tagIds)
        if (t.recurringId != null) throw FinanceValidationException("This is already part of a repeating payment.")
        if (t.id != null && splitGroupOf(t.id) != null) throw FinanceValidationException("A part of a split payment can't be made to repeat.")
        if (schedule.interval < 1) throw FinanceValidationException("Repeat at least every 1.")
        if (schedule.endDate != null && schedule.endDate.isBefore(startOfDay(t.occurredAt))) {
            throw FinanceValidationException("The last repeat must be on or after the first payment.")
        }
        val now = clock()
        transaction {
            val rule = RecurringRule(
                type = t.type,
                amount = t.amount,
                accountId = t.accountId,
                toAccountId = t.toAccountId,
                categoryId = t.categoryId,
                payee = clean(t.payee),
                note = clean(t.note),
                frequency = schedule.frequency,
                interval = schedule.interval,
                startDate = t.occurredAt,
                endDate = schedule.endDate,
                nextDate = nextRecurrence(t.occurredAt, schedule.frequency, schedule.interval, anchor = t.occurredAt),
                createdAt = now,
                updatedAt = now,
                personId = t.personId,
            )
            val ruleId = insertOrThrow(FinanceDatabase.RECURRING_RULES, null, FinanceRows.recurring(rule))
            val toSave = t.copy(payee = clean(t.payee), note = clean(t.note), recurringId = ruleId, updatedAt = now, splitGroup = null)
            val saved = if (t.id == null) {
                toSave.copy(id = insertOrThrow(FinanceDatabase.TRANSACTIONS, null, FinanceRows.transaction(toSave)))
            } else {
                val count = update(FinanceDatabase.TRANSACTIONS, FinanceRows.transaction(toSave), "id = ?", arrayOf(t.id.toString()))
                if (count == 0) throw FinanceConflictException("This transaction no longer exists.")
                toSave
            }
            // The tags are this transaction's only: what the rule records later is untagged (a rule has no tags of its own).
            writeTags(saved.id!!, saved.tagIds)
            saved to rule.copy(id = ruleId)
        }
    }

    /**
     * Deletes the rule. Transactions it already created stay (they're real
     * history) and simply lose the link.
     */
    suspend fun deleteRecurringRule(id: Long) = io {
        delete(FinanceDatabase.RECURRING_RULES, "id = ?", arrayOf(id.toString()))
    }

    /**
     * Records every due occurrence of every active rule up to now, in one SQL
     * transaction, and advances each rule. Safe to call repeatedly: an
     * occurrence is only ever recorded once because the rule's next date moves
     * past it in the same transaction.
     */
    suspend fun processRecurring(): Int = io {
        val now = clock()
        var created = 0
        transaction {
            val due = query(
                FinanceDatabase.RECURRING_RULES,
                null,
                "active = 1 AND next_date <= ${now.toStorage()}",
                null,
                null,
                null,
                null,
            ).map(FinanceRows::toRecurring)
            // A repeating transfer onto a loan records EMIs: each is split into principal and interest (see recordDueEmis).
            val loans = if (due.any { it.type == TransactionType.Transfer && it.toAccountId != null }) readLoans() else emptyMap()
            for (rule in due) {
                val loan = rule.toAccountId?.let { loans[it] }
                if (loan != null && rule.recordsEmiOf(loans.keys)) {
                    created += recordDueEmis(rule, loan, now)
                    continue
                }
                val result = dueOccurrences(rule, now)
                for (date in result.dates) {
                    insertOrThrow(
                        FinanceDatabase.TRANSACTIONS,
                        null,
                        FinanceRows.transaction(
                            FinanceTransaction(
                                type = rule.type,
                                amount = rule.amount,
                                accountId = rule.accountId,
                                toAccountId = rule.toAccountId,
                                categoryId = rule.categoryId,
                                occurredAt = date,
                                payee = rule.payee,
                                note = rule.note,
                                recurringId = rule.id,
                                personId = rule.personId,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                    )
                    created++
                }
                update(
                    FinanceDatabase.RECURRING_RULES,
                    ContentValues().apply {
                        put("next_date", result.nextDate.toStorage())
                        put("updated_at", now.toStorage())
                    },
                    "id = ?",
                    arrayOf(rule.id.toString()),
                )
            }
        }
        created
    }

    // ---------------------------------------------------------------- EMIs recorded each month

    /** Every account that is a loan (has terms), by id. */
    private fun SupportSQLiteDatabase.readLoans(): Map<Long, Account> =
        query(FinanceDatabase.ACCOUNTS, null, "loan_principal IS NOT NULL", null, null, null, null)
            .map(FinanceRows::toAccount)
            .filter { it.id != null && it.loan != null }
            .associateBy { it.id!! }

    /** [account]'s balance from its starting balance and everything dated before [before]. */
    private fun SupportSQLiteDatabase.balanceBefore(account: Account, before: LocalDateTime): Long {
        val id = account.id ?: return account.openingBalance
        val at = before.toStorage()
        var income = 0L
        var expense = 0L
        var transferOut = 0L
        rawQuery(
            "SELECT type, SUM(amount) AS total FROM ${FinanceDatabase.TRANSACTIONS} WHERE account_id = $id AND occurred_at < $at GROUP BY type",
            null,
        ).map { it.string("type") to it.long("total") }.forEach { (type, total) ->
            when (type) {
                "income" -> income = total
                "expense" -> expense = total
                "transfer" -> transferOut = total
            }
        }
        val transferIn = rawQuery(
            "SELECT COALESCE(SUM(amount), 0) AS total FROM ${FinanceDatabase.TRANSACTIONS} " +
                "WHERE type = 'transfer' AND to_account_id = $id AND occurred_at < $at",
            null,
        ).map { it.long("total") }.firstOrNull() ?: 0L
        return accountBalance(account.openingBalance, AccountFlows(income = income, expense = expense, transferIn = transferIn, transferOut = transferOut))
    }

    /**
     * The days EMIs were paid onto [loanId] by hand - with Record EMI, whose notes start "EMI " - from [from] up to (not including) [to],
     * leaving out what [ruleId] recorded itself.
     */
    private fun SupportSQLiteDatabase.handPaidEmiDays(loanId: Long, ruleId: Long?, from: LocalDateTime, to: LocalDateTime): List<LocalDate> =
        rawQuery(
            "SELECT occurred_at FROM ${FinanceDatabase.TRANSACTIONS} WHERE type = 'transfer' AND to_account_id = $loanId " +
                "AND note LIKE ? AND (recurring_id IS NULL OR recurring_id != ${ruleId ?: -1L}) " +
                "AND occurred_at >= ${from.toStorage()} AND occurred_at < ${to.toStorage()}",
            arrayOf("$EMI_NOTE_PREFIX%"),
        ).map { it.long("occurred_at").toLocalDateTime().toLocalDate() }

    /**
     * Records every due EMI of the repeating payment [rule] onto [loan], inside [processRecurring]'s transaction, and moves the rule on.
     * Each EMI is split by what is owed just before it ([emiSplit]): the principal a transfer onto the loan, the interest an expense in
     * [emiInterestCategoryOrFallback] paid to the loan, both marked as the rule's and noted as Record EMI notes them. An EMI that one
     * paid by hand already covers ([emiCovered]) is passed over. Once nothing is owed the rule ends there: its end is set just before
     * that day, so it is finished, and nothing more is recorded. With no expense category at all, an EMI with interest waits (the rule
     * stays due on it) rather than be recorded wrong. Gives back how many entries it made.
     */
    private fun SupportSQLiteDatabase.recordDueEmis(rule: RecurringRule, loan: Account, now: LocalDateTime): Int {
        val terms = loan.loan ?: return 0
        val loanId = loan.id ?: return 0
        val result = dueOccurrences(rule, now)
        var created = 0
        var next = result.nextDate
        var endAt: LocalDateTime? = null
        val category by lazy {
            emiInterestCategoryOrFallback(query(FinanceDatabase.CATEGORIES, null, null, null, null, null, "id ASC").map(FinanceRows::toCategory))
        }
        for (date in result.dates) {
            val owed = (-balanceBefore(loan, date)).coerceAtLeast(0L)
            val split = emiSplit(owed, terms.rateBp, rule.amount)
            if (split == null) {
                // Paid off: the rule ends before this day, and this and the later ones are never recorded.
                next = date
                endAt = date.minusMinutes(1)
                break
            }
            val day = date.toLocalDate()
            val window = handPaidEmiDays(loanId, rule.id, day.minusDays(HAND_PAID_WINDOW_DAYS).atStartOfDay(), day.plusDays(HAND_PAID_WINDOW_DAYS + 1).atStartOfDay())
            if (window.any { emiCovered(day, it) }) continue
            val interestCategoryId = if (split.interest > 0) category?.id else null
            if (split.interest > 0 && interestCategoryId == null) {
                next = date
                break
            }
            // Nothing here can be refused (the rule is a transfer between two accounts and the parts are worked out above), but one
            // EMI that somehow is must not stop every other repeating payment from being recorded: it is passed over.
            val entries = try {
                loanEmiEntries(LoanEmi(loanId, rule.accountId, date, split.principal, split.interest, interestCategoryId, loan.name), now)
            } catch (_: FinanceValidationException) {
                continue
            }
            for (entry in entries) {
                insertOrThrow(FinanceDatabase.TRANSACTIONS, null, FinanceRows.transaction(entry.copy(recurringId = rule.id, updatedAt = now)))
                created++
            }
        }
        update(
            FinanceDatabase.RECURRING_RULES,
            ContentValues().apply {
                put("next_date", next.toStorage())
                endAt?.let { put("end_date", it.toStorage()) }
                put("updated_at", now.toStorage())
            },
            "id = ?",
            arrayOf(rule.id.toString()),
        )
        return created
    }

    /**
     * The repeating payment that records [loanId]'s EMIs each month (an active transfer onto it that has not ended), or null when they
     * are recorded by hand.
     */
    suspend fun emiRuleOf(loanId: Long): RecurringRule? = io { activeEmiRule(loanId) }

    private fun SupportSQLiteDatabase.activeEmiRule(loanId: Long): RecurringRule? =
        readRules().firstOrNull { it.type == TransactionType.Transfer && it.toAccountId == loanId && it.active && !it.isFinished }

    /**
     * Starts recording the EMIs of the loan [loanId] each month, paid from [fromAccountId] (see [emiRule]): from the next EMI on or after
     * today ([firstAutoEmiIndex]) to the day the schedule pays the loan off, as of what is owed today. When they already are, the rule
     * that does it is given back as it is. Refused for an account that isn't a loan, one with nothing owed, and paying from the loan.
     */
    suspend fun startLoanEmis(loanId: Long, fromAccountId: Long): RecurringRule = io {
        val loan = readAccount(loanId) ?: throw FinanceConflictException("This loan no longer exists.")
        val terms = loan.loan ?: throw FinanceValidationException("This account isn't a loan.")
        activeEmiRule(loanId)?.let { return@io it }
        if (fromAccountId == loanId) throw FinanceValidationException("Pay the EMI from another account.")
        val now = clock()
        val today = now.toLocalDate()
        val cutoff = com.example.normpix.finance.domain.settledCutoff(now)
        val owed = (-balanceBefore(loan, cutoff)).coerceAtLeast(0L)
        if (owed <= 0) throw FinanceValidationException("Nothing is owed on this loan.")
        val lastPaid = query(
            FinanceDatabase.TRANSACTIONS,
            arrayOf("occurred_at"),
            "type = 'transfer' AND to_account_id = ? AND occurred_at < ${cutoff.toStorage()}",
            arrayOf(loanId.toString()),
            null,
            null,
            "occurred_at DESC",
            "1",
        ).map { it.long("occurred_at").toLocalDateTime().toLocalDate() }.firstOrNull()
        val first = firstAutoEmiIndex(terms, lastPaid, today)
        val lastDay = loanOutlook(terms, owed, first)?.payoff
        val rule = emiRule(loan, fromAccountId, first, lastDay, now)
        validateShape(rule.type, rule.amount, rule.accountId, rule.toAccountId, rule.categoryId)
        rule.copy(id = insertOrThrow(FinanceDatabase.RECURRING_RULES, null, FinanceRows.recurring(rule)))
    }

    /**
     * Stops recording [loanId]'s EMIs each month: the repeating payment that did it is deleted (what it recorded stays, as any repeating
     * payment's does). Gives back the rule that was deleted, or null when there was none.
     */
    suspend fun stopLoanEmis(loanId: Long): RecurringRule? = io {
        val rule = activeEmiRule(loanId) ?: return@io null
        delete(FinanceDatabase.RECURRING_RULES, "id = ?", arrayOf(rule.id.toString()))
        rule
    }

    private fun SupportSQLiteDatabase.readAccount(id: Long): Account? =
        query(FinanceDatabase.ACCOUNTS, null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) FinanceRows.toAccount(it) else null }

    // ---------------------------------------------------------------- check

    /**
     * Reads every account, category, transaction, repeating payment and budget, puts right what has one right answer (see
     * `LedgerCheck.kt`) in one SQL transaction, then records any repeating payments that are due - so what is shown afterwards
     * is worked out again from data that holds together. Says what it read, what it put right and what is left to look at.
     */
    suspend fun checkAndRepair(): LedgerCheckResult {
        val now = clock()
        val balances = accountBalances(asOf = com.example.normpix.finance.domain.settledCutoff(now))
        val read = io {
            val accounts = query(FinanceDatabase.ACCOUNTS, null, null, null, null, null, "archived ASC, sort_order ASC, id ASC").map(FinanceRows::toAccount)
            val categories = query(FinanceDatabase.CATEGORIES, null, null, null, null, null, "id ASC").map(FinanceRows::toCategory)
            val transactions = query(FinanceDatabase.TRANSACTIONS, null, null, null, null, null, "occurred_at DESC, id DESC").map(FinanceRows::toTransaction)
            val catalog = bankCatalog()
            val rules = readRules()
            val issues = checkLedger(
                accounts = accounts,
                categories = categories,
                transactions = transactions,
                rules = rules,
                budgets = readBudgets(),
                balances = balances,
                defaultAccountId = meta("default_account_id")?.toLongOrNull(),
                now = now,
                bankKeyIn = { catalog.resolve(it)?.key },
            )
            if (issues.repairCount > 0) {
                transaction {
                    val stamp = now.toStorage()
                    for (loose in issues.looseSubCategories) {
                        update(
                            FinanceDatabase.CATEGORIES,
                            ContentValues().apply {
                                putNull("parent_id")
                                put("updated_at", stamp)
                            },
                            "id = ?",
                            arrayOf(loose.category.id.toString()),
                        )
                    }
                    for (budget in issues.incomeBudgets) delete(FinanceDatabase.BUDGETS, "id = ?", arrayOf(budget.id.toString()))
                    for (catchUp in issues.ruleCatchUps) {
                        update(
                            FinanceDatabase.RECURRING_RULES,
                            ContentValues().apply {
                                put("next_date", catchUp.nextDate.toStorage())
                                put("updated_at", stamp)
                            },
                            "id = ?",
                            arrayOf(catchUp.rule.id.toString()),
                        )
                    }
                    if (issues.staleDefaultAccount) delete(FinanceDatabase.META, "key = ?", arrayOf("default_account_id"))
                }
            }
            LedgerCheckResult(
                accounts = accounts.size,
                categories = categories.size,
                transactions = transactions.size,
                rules = rules.size,
                issues = issues,
            )
        }
        val recorded = processRecurring()
        return read.copy(recordedRepeats = recorded)
    }

    /**
     * Records [pair] as the one transfer it is (see `SelfTransferPair`): the expense becomes a transfer to the account the income
     * came into, and the income goes - in one SQL transaction, so the money is never counted twice nor lost on the way.
     */
    suspend fun combineSelfTransfer(pair: SelfTransferPair): FinanceTransaction = io {
        val transfer = pair.asTransfer(clock())
        validateShape(transfer.type, transfer.amount, transfer.accountId, transfer.toAccountId, transfer.categoryId)
        if (splitGroupOf(pair.expense.id!!) != null || splitGroupOf(pair.income.id!!) != null) {
            throw FinanceValidationException("A part of a split payment can't be made a transfer. Unsplit it first.")
        }
        transaction {
            val updated = update(FinanceDatabase.TRANSACTIONS, FinanceRows.transaction(transfer), "id = ?", arrayOf(transfer.id.toString()))
            if (updated == 0) throw FinanceConflictException("This transaction no longer exists.")
            // A transfer has no tags: the expense's come off it (the income's go with the income).
            writeTags(transfer.id!!, emptySet())
            if (delete(FinanceDatabase.TRANSACTIONS, "id = ?", arrayOf(pair.income.id.toString())) == 0) {
                throw FinanceConflictException("This transaction no longer exists.")
            }
        }
        transfer
    }

    // ----------------------------------------------------- backup / restore

    suspend fun snapshot(): FinanceSnapshot = io {
        FinanceSnapshot(
            currencyCode = meta("currency") ?: CurrencySpec.INR.code,
            defaultAccountId = meta("default_account_id")?.toLongOrNull(),
            accounts = query(FinanceDatabase.ACCOUNTS, null, null, null, null, null, "archived ASC, sort_order ASC, id ASC")
                .map(FinanceRows::toAccount),
            categories = query(
                FinanceDatabase.CATEGORIES,
                null,
                null,
                null,
                null,
                null,
                "type ASC, archived ASC, sort_order ASC, id ASC",
            ).map(FinanceRows::toCategory),
            transactions = query(FinanceDatabase.TRANSACTIONS, null, null, null, null, null, "occurred_at DESC, id DESC")
                .map(FinanceRows::toTransaction)
                .let { withTags(it) }
                // A group that doesn't hold together (nothing the app writes makes one) goes into a snapshot - a backup, an Undo - as
                // plain rows, every amount, account and date as it is: a snapshot is only restorable if its groups hold, and a mark that
                // means nothing is not worth a backup that can't be put back. Check and recalculate is what finds and ends such a group.
                .let { withoutBrokenSplits(it) },
            budgets = readBudgets(),
            recurringRules = readRules(),
            banks = readBanks(),
            people = readPeople(),
            tags = readTags(),
            goals = readGoals(),
        )
    }

    /**
     * Replaces **all** finance data with [snapshot] atomically: either the
     * whole backup lands, or (on any error) nothing changes.
     */
    suspend fun restore(snapshot: FinanceSnapshot) = io {
        val problems = FinanceBackupCodec.validate(snapshot)
        if (problems.isNotEmpty()) throw FinanceValidationException(problems.first())
        val now = clock().toStorage()
        transaction {
            // The payees the user said "not this one" to are a choice about this phone, not ledger data: they outlive a restore.
            val dismissedRepeats = meta(DISMISSED_REPEATS)
            clearAll()
            for (b in snapshot.banks) insertOrThrow(FinanceDatabase.BANKS, null, FinanceRows.bank(b))
            for (a in snapshot.accounts) insertOrThrow(FinanceDatabase.ACCOUNTS, null, FinanceRows.account(a))
            // The goals refer to the accounts, so they go in after them, with their ids: a file from before goals (format 1 to 5) has none.
            for (g in snapshot.goals) insertOrThrow(FinanceDatabase.GOALS, null, FinanceRows.goal(g))
            for (p in snapshot.people) insertOrThrow(FinanceDatabase.PEOPLE, null, FinanceRows.person(p))
            // The tags keep their ids, as everything here does, so the links below name them as the file does.
            for (tag in snapshot.tags) insertOrThrow(FinanceDatabase.TAGS, null, FinanceRows.tag(tag))
            // Parents first: a sub-category's row refers to its parent's.
            for (c in snapshot.categories.sortedBy { it.parentId != null }) {
                insertOrThrow(FinanceDatabase.CATEGORIES, null, FinanceRows.category(c))
            }
            // A backup that left out the default categories nothing used (see trimmedForBackup): they are the app's, and are made
            // again as it has them now - all but the ones the user had removed. A file that lists every category has none to add.
            snapshot.removedDefaults?.let { removed -> FinanceDatabase.addMissingDefaults(this, clock(), removed.toSet()) }
            for (r in snapshot.recurringRules) {
                insertOrThrow(FinanceDatabase.RECURRING_RULES, null, FinanceRows.recurring(r))
            }
            for (t in snapshot.transactions) {
                insertOrThrow(FinanceDatabase.TRANSACTIONS, null, FinanceRows.transaction(t))
                // A file from before tags (format 1 to 4) has none: nothing is written.
                if (t.tagIds.isNotEmpty()) writeTags(t.id!!, t.tagIds)
            }
            for (b in snapshot.budgets) {
                insertOrThrow(
                    FinanceDatabase.BUDGETS,
                    null,
                    ContentValues().apply {
                        put("id", b.id)
                        putNullable("category_id", b.categoryId)
                        putNullable("amount", b.amount)
                        put("effective_month", b.effectiveMonth.toInt())
                        put("created_at", now)
                        put("updated_at", now)
                    },
                )
            }
            setMeta("currency", snapshot.currencyCode)
            snapshot.defaultAccountId?.let { setMeta("default_account_id", it.toString()) }
            dismissedRepeats?.let { setMeta(DISMISSED_REPEATS, it) }
        }
    }

    /** Erases all finance data and restores the first-run defaults. */
    suspend fun resetAll() = io {
        val now = clock()
        transaction {
            clearAll()
            FinanceDatabase.seedDefaults(this, now)
        }
    }

    private fun SupportSQLiteDatabase.clearAll() {
        // Children before parents (foreign keys are enforced).
        for (table in listOf(
            FinanceDatabase.GOALS,
            FinanceDatabase.TRANSACTION_TAGS,
            FinanceDatabase.TRANSACTIONS,
            FinanceDatabase.TAGS,
            FinanceDatabase.PEOPLE,
            FinanceDatabase.BUDGETS,
            FinanceDatabase.RECURRING_RULES,
            FinanceDatabase.CATEGORIES,
            FinanceDatabase.ACCOUNTS,
            FinanceDatabase.BANKS,
            FinanceDatabase.META,
        )) {
            delete(table, null, null)
        }
    }

    /** Closes the database (tests). */
    fun close() = database.close()

    companion object {
        private const val MAX_BANK_NAME = 60

        /** Up to this many transactions, their tags are read by their ids; past it (a backup's every one), the whole link table once. */
        private const val INLINE_IDS = 500

        /** The `meta` key holding the payees not to offer as a repeating payment, one per line. */
        private const val DISMISSED_REPEATS = "dismissed_repeat_payees"

        /**
         * How far apart two entries of the same amount to the same payee can be and still be one payment entered twice
         * ([possibleDuplicates]): an entry typed in by hand is dated when it was typed, usually soon after paying.
         */
        val SAME_PAYMENT_WINDOW: Duration = Duration.ofHours(3)

        /** One daemon thread: serializes all finance database work. */
        val defaultDispatcher: CoroutineDispatcher by lazy {
            Executors.newSingleThreadExecutor { r -> Thread(r, "normpix-finance-db").apply { isDaemon = true } }
                .asCoroutineDispatcher()
        }
    }
}

private fun SupportSQLiteDatabase.scalarLong(sql: String): Long? =
    rawQuery(sql, null).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

private fun marks(n: Int): String = List(n) { "?" }.joinToString(", ")

private fun clean(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }

private fun count(n: Long, noun: String): String = "$n $noun${if (n == 1L) "" else "s"}"
