package com.example.normpix.finance.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountFlows
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.Budget
import com.example.normpix.finance.domain.BudgetOverview
import com.example.normpix.finance.domain.BudgetStatus
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryTotal
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.EMOJI_ICON_PREFIX
import com.example.normpix.finance.domain.emojiOf
import com.example.normpix.finance.domain.foldIntoParents
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.CustomBank
import com.example.normpix.finance.domain.DailyTotal
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.MAX_PERSON_NAME
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.MonthTotals
import com.example.normpix.finance.domain.PeopleSpending
import com.example.normpix.finance.domain.PeriodSummary
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.PersonTotal
import com.example.normpix.finance.domain.cleanPersonName
import com.example.normpix.finance.domain.isSingleEmoji
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.RepeatSchedule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.accountBalance
import com.example.normpix.finance.domain.buildBudgetOverview
import com.example.normpix.finance.domain.dueOccurrences
import com.example.normpix.finance.domain.nextRecurrence
import com.example.normpix.finance.domain.resolveBudgets
import com.example.normpix.finance.domain.startOfDay
import com.example.normpix.wallet.BankLogoResolver
import java.time.LocalDate
import java.time.LocalDateTime
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
    /** Case-insensitive match on payee, note, category, account or person name. */
    val text: String? = null,
) {
    /** True when anything besides [range] narrows the results. */
    val hasFilters: Boolean
        get() = types.isNotEmpty() ||
            accountIds.isNotEmpty() ||
            categoryIds.isNotEmpty() ||
            personIds.isNotEmpty() ||
            minAmount != null ||
            maxAmount != null ||
            !text.isNullOrBlank()
}

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
    private val dispatcher: CoroutineDispatcher = defaultDispatcher,
) {
    private suspend fun <T> io(block: SQLiteDatabase.() -> T): T =
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

    private fun SQLiteDatabase.storeDefaultAccount(id: Long?) {
        if (id == null) {
            delete(FinanceDatabase.META, "key = ?", arrayOf("default_account_id"))
        } else {
            setMeta("default_account_id", id.toString())
        }
    }

    private fun SQLiteDatabase.meta(key: String): String? =
        query(FinanceDatabase.META, arrayOf("value"), "key = ?", arrayOf(key), null, null, null).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun SQLiteDatabase.setMeta(key: String, value: String) {
        replaceOrThrow(
            FinanceDatabase.META,
            null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
            },
        )
    }

    private fun SQLiteDatabase.hasAmounts(): Boolean = scalarLong(
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
        val toSave = account.copy(name = name, bankKey = bankKey, creditLimit = creditLimit, updatedAt = clock())
        if (account.id == null) {
            val id = insertOrThrow(FinanceDatabase.ACCOUNTS, null, FinanceRows.account(toSave))
            toSave.copy(id = id)
        } else {
            update(FinanceDatabase.ACCOUNTS, FinanceRows.account(toSave), "id = ?", arrayOf(account.id.toString()))
            toSave
        }
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

    private fun SQLiteDatabase.readBanks(): List<CustomBank> =
        query(FinanceDatabase.BANKS, null, null, null, null, null, "name COLLATE NOCASE ASC").map(FinanceRows::toBank)

    private fun SQLiteDatabase.bankCatalog() = BankCatalog(readBanks())

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

    /** Current balance of every account (archived included), by id. */
    suspend fun accountBalances(): Map<Long, Long> = io {
        val openings = query(FinanceDatabase.ACCOUNTS, arrayOf("id", "opening_balance"), null, null, null, null, null)
            .map { it.long("id") to it.long("opening_balance") }

        val income = mutableMapOf<Long, Long>()
        val expense = mutableMapOf<Long, Long>()
        val transferOut = mutableMapOf<Long, Long>()
        rawQuery(
            "SELECT account_id, type, SUM(amount) AS total FROM ${FinanceDatabase.TRANSACTIONS} GROUP BY account_id, type",
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
                "WHERE type = 'transfer' GROUP BY to_account_id",
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

    private fun SQLiteDatabase.readCategory(id: Long): Category? =
        query(FinanceDatabase.CATEGORIES, null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) FinanceRows.toCategory(it) else null }

    private fun SQLiteDatabase.hasSubCategories(id: Long): Boolean =
        (scalarLong("SELECT COUNT(*) FROM ${FinanceDatabase.CATEGORIES} WHERE parent_id = $id") ?: 0) > 0

    /** Child id to parent id for every sub-category. */
    private fun SQLiteDatabase.parentIds(): Map<Long, Long> =
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

    private fun SQLiteDatabase.readPeople(): List<Person> =
        query(FinanceDatabase.PEOPLE, null, null, null, null, null, "archived ASC, name COLLATE NOCASE ASC, id ASC")
            .map(FinanceRows::toPerson)

    private fun SQLiteDatabase.readPerson(id: Long): Person? =
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
    suspend fun peopleSpending(range: DateRange? = null): PeopleSpending = io {
        val window = range?.let { " AND occurred_at >= ${it.start.toStorage()} AND occurred_at < ${it.end.toStorage()}" }.orEmpty()
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
    private fun SQLiteDatabase.validatePerson(type: TransactionType, personId: Long?, transactionId: Long?) =
        validatePersonLink(type, personId) {
            transactionId != null &&
                scalarLong("SELECT person_id FROM ${FinanceDatabase.TRANSACTIONS} WHERE id = $transactionId") == personId
        }

    /** The same rules for a recurring payment's person: what it records is spent on them. */
    private fun SQLiteDatabase.validateRulePerson(rule: RecurringRule) =
        validatePersonLink(rule.type, rule.personId) {
            rule.id != null &&
                scalarLong("SELECT person_id FROM ${FinanceDatabase.RECURRING_RULES} WHERE id = ${rule.id}") == rule.personId
        }

    private fun SQLiteDatabase.validatePersonLink(type: TransactionType, personId: Long?, alreadyLinked: () -> Boolean) {
        if (personId == null) return
        if (type != TransactionType.Expense) throw FinanceValidationException("Only an expense can be spent on a person.")
        val person = readPerson(personId) ?: throw FinanceValidationException("Choose a person from the list, or add them first.")
        // An archived person keeps what is already theirs, but takes nothing new.
        if (person.archived && !alreadyLinked()) throw FinanceValidationException("${person.name} is archived. Restore them first.")
    }

    // -------------------------------------------------------- transactions

    private fun SQLiteDatabase.exists(table: String, id: Long): Boolean =
        query(table, arrayOf("id"), "id = ?", arrayOf(id.toString()), null, null, null).use { it.moveToFirst() }

    private fun SQLiteDatabase.validateShape(
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
        val toSave = t.copy(payee = clean(t.payee), note = clean(t.note), updatedAt = clock())
        if (t.id == null) {
            val id = insertOrThrow(FinanceDatabase.TRANSACTIONS, null, FinanceRows.transaction(toSave))
            toSave.copy(id = id)
        } else {
            val count = update(
                FinanceDatabase.TRANSACTIONS,
                FinanceRows.transaction(toSave),
                "id = ?",
                arrayOf(t.id.toString()),
            )
            if (count == 0) throw FinanceConflictException("This transaction no longer exists.")
            toSave
        }
    }

    /**
     * Inserts multiple transactions in a single SQLite transaction.
     */
    suspend fun saveTransactions(transactions: List<FinanceTransaction>) = io {
        val now = clock()
        transaction {
            for (t in transactions) {
                validateShape(t.type, t.amount, t.accountId, t.toAccountId, t.categoryId)
                validatePerson(t.type, t.personId, t.id)
                val toSave = t.copy(payee = clean(t.payee), note = clean(t.note), updatedAt = now)
                insertOrThrow(FinanceDatabase.TRANSACTIONS, null, FinanceRows.transaction(toSave))
            }
        }
    }

    suspend fun deleteTransaction(id: Long) = io {
        delete(FinanceDatabase.TRANSACTIONS, "id = ?", arrayOf(id.toString()))
    }

    suspend fun transactionById(id: Long): FinanceTransaction? = io {
        query(FinanceDatabase.TRANSACTIONS, null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) FinanceRows.toTransaction(it) else null }
    }

    /** WHERE clause + string bind args. Numbers are inlined: they are `Long`s, never text. */
    private fun where(q: TransactionQuery): Pair<String, List<String>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        q.range?.let { range ->
            clauses += "t.occurred_at >= ${range.start.toStorage()} AND t.occurred_at < ${range.end.toStorage()}"
        }
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
        q.minAmount?.let { clauses += "t.amount >= $it" }
        q.maxAmount?.let { clauses += "t.amount <= $it" }
        val text = q.text?.trim()
        if (!text.isNullOrEmpty()) {
            val pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            clauses += "(t.payee LIKE ? ESCAPE '\\' OR t.note LIKE ? ESCAPE '\\' " +
                "OR c.name LIKE ? ESCAPE '\\' OR a.name LIKE ? ESCAPE '\\' OR p.name LIKE ? ESCAPE '\\')"
            repeat(5) { args += pattern }
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
        val items = rows.take(limit)
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
     * payment message, which can be shared twice. A note that names the same [reference] matches whatever else differs;
     * otherwise the same [type] and [amount] on the same day as [occurredAt], with the same payee where both have one.
     * Only finds them, newest first: whether to save anyway is the user's call (two coffees on one day are both real).
     */
    suspend fun possibleDuplicates(
        type: TransactionType?,
        amount: Long?,
        occurredAt: LocalDateTime,
        payee: String?,
        reference: String?,
        limit: Int = 3,
    ): List<FinanceTransaction> = io {
        val found = LinkedHashMap<Long, FinanceTransaction>()
        val ref = reference?.trim()
        if (!ref.isNullOrEmpty()) {
            val pattern = "%" + ref.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            rawQuery(
                "SELECT * FROM ${FinanceDatabase.TRANSACTIONS} WHERE note LIKE ? ESCAPE '\\' ORDER BY occurred_at DESC, id DESC LIMIT $limit",
                arrayOf(pattern),
            ).map(FinanceRows::toTransaction).forEach { found[it.id!!] = it }
        }
        if (type != null && amount != null) {
            val day = startOfDay(occurredAt)
            val name = payee?.trim()?.takeIf { it.isNotEmpty() }
            rawQuery(
                "SELECT * FROM ${FinanceDatabase.TRANSACTIONS} WHERE type = ? AND amount = $amount " +
                    "AND occurred_at >= ${day.toStorage()} AND occurred_at < ${day.plusDays(1).toStorage()} " +
                    "ORDER BY occurred_at DESC, id DESC",
                arrayOf(type.key),
            ).map(FinanceRows::toTransaction)
                .filter { other -> name == null || other.payee == null || other.payee.equals(name, ignoreCase = true) }
                .forEach { found.putIfAbsent(it.id!!, it) }
        }
        found.values.take(limit)
    }

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

    /** Income and expense per month from [from] through [to] inclusive - months without activity are included as zeros. */
    suspend fun monthlyTotals(from: MonthKey, to: MonthKey): List<MonthTotals> = io {
        val income = mutableMapOf<Int, Long>()
        val expense = mutableMapOf<Int, Long>()
        rawQuery(
            "SELECT CAST(strftime('%Y%m', occurred_at / 1000, 'unixepoch') AS INTEGER) AS month, type, SUM(amount) AS total " +
                "FROM ${FinanceDatabase.TRANSACTIONS} " +
                "WHERE type IN ('income', 'expense') AND occurred_at >= ${from.start.toStorage()} AND occurred_at < ${to.end.toStorage()} " +
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

    /** The first month with any recorded transaction, if any. */
    suspend fun firstActiveMonth(): MonthKey? = io {
        scalarLong("SELECT MIN(occurred_at) FROM ${FinanceDatabase.TRANSACTIONS}")?.let { MonthKey.of(it.toLocalDateTime()) }
    }

    // -------------------------------------------------------------- budgets

    suspend fun budgetRows(): List<Budget> = io { readBudgets() }

    private fun SQLiteDatabase.readBudgets(): List<Budget> =
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

    suspend fun budgetOverview(month: MonthKey): BudgetOverview {
        val limits = resolveBudgets(budgetRows(), month)
        val totals = categoryTotals(month.range, TransactionType.Expense)
        val parents = io { parentIds() }
        return buildBudgetOverview(
            month = month,
            limits = limits,
            spentByCategory = foldIntoParents(totals.associate { it.categoryId to it.amount }, parents),
            totalExpense = totals.sumOf { it.amount },
            parents = parents,
        )
    }

    /**
     * Month-by-month budget results, newest first, for months (up to
     * [maxMonths] back from [until]) that had any budget in effect.
     */
    suspend fun budgetHistory(until: MonthKey, maxMonths: Int = 24): List<BudgetHistoryEntry> {
        val rows = budgetRows()
        if (rows.isEmpty()) return emptyList()
        val parents = io { parentIds() }
        var start = rows.first().effectiveMonth
        val earliest = until.addMonths(-(maxMonths - 1))
        if (start.isBefore(earliest)) start = earliest
        if (start.isAfter(until)) return emptyList()

        val byMonth = io {
            val result = mutableMapOf<Int, MutableMap<Long, Long>>()
            rawQuery(
                "SELECT CAST(strftime('%Y%m', occurred_at / 1000, 'unixepoch') AS INTEGER) AS month, category_id, SUM(amount) AS total " +
                    "FROM ${FinanceDatabase.TRANSACTIONS} " +
                    "WHERE type = 'expense' AND occurred_at >= ${start.start.toStorage()} AND occurred_at < ${until.end.toStorage()} " +
                    "GROUP BY month, category_id",
                null,
            ).map { Triple(it.int("month"), it.long("category_id"), it.long("total")) }
                .forEach { (month, category, total) -> result.getOrPut(month) { mutableMapOf() }[category] = total }
            result
        }

        val entries = mutableListOf<BudgetHistoryEntry>()
        var m = until
        while (!m.isBefore(start)) {
            val limits = resolveBudgets(rows, m)
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

    private fun SQLiteDatabase.readRules(): List<RecurringRule> = query(
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
        if (t.recurringId != null) throw FinanceValidationException("This is already part of a repeating payment.")
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
            val toSave = t.copy(payee = clean(t.payee), note = clean(t.note), recurringId = ruleId, updatedAt = now)
            val saved = if (t.id == null) {
                toSave.copy(id = insertOrThrow(FinanceDatabase.TRANSACTIONS, null, FinanceRows.transaction(toSave)))
            } else {
                val count = update(FinanceDatabase.TRANSACTIONS, FinanceRows.transaction(toSave), "id = ?", arrayOf(t.id.toString()))
                if (count == 0) throw FinanceConflictException("This transaction no longer exists.")
                toSave
            }
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
            for (rule in due) {
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
                .map(FinanceRows::toTransaction),
            budgets = readBudgets(),
            recurringRules = readRules(),
            banks = readBanks(),
            people = readPeople(),
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
            clearAll()
            for (b in snapshot.banks) insertOrThrow(FinanceDatabase.BANKS, null, FinanceRows.bank(b))
            for (a in snapshot.accounts) insertOrThrow(FinanceDatabase.ACCOUNTS, null, FinanceRows.account(a))
            for (p in snapshot.people) insertOrThrow(FinanceDatabase.PEOPLE, null, FinanceRows.person(p))
            // Parents first: a sub-category's row refers to its parent's.
            for (c in snapshot.categories.sortedBy { it.parentId != null }) {
                insertOrThrow(FinanceDatabase.CATEGORIES, null, FinanceRows.category(c))
            }
            for (r in snapshot.recurringRules) {
                insertOrThrow(FinanceDatabase.RECURRING_RULES, null, FinanceRows.recurring(r))
            }
            for (t in snapshot.transactions) insertOrThrow(FinanceDatabase.TRANSACTIONS, null, FinanceRows.transaction(t))
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

    private fun SQLiteDatabase.clearAll() {
        // Children before parents (foreign keys are enforced).
        for (table in listOf(
            FinanceDatabase.TRANSACTIONS,
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

        /** One daemon thread: serializes all finance database work. */
        val defaultDispatcher: CoroutineDispatcher by lazy {
            Executors.newSingleThreadExecutor { r -> Thread(r, "normpix-finance-db").apply { isDaemon = true } }
                .asCoroutineDispatcher()
        }
    }
}

private fun SQLiteDatabase.scalarLong(sql: String): Long? =
    rawQuery(sql, null).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

private fun marks(n: Int): String = List(n) { "?" }.joinToString(", ")

private fun clean(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }

private fun count(n: Long, noun: String): String = "$n $noun${if (n == 1L) "" else "s"}"
