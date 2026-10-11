package com.example.normpix.finance.data

import android.content.ContentValues
import android.database.Cursor
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.Budget
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.CustomBank
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDateTime
import java.time.ZoneOffset

/*
 * Conversions between SQLite rows and domain entities.
 *
 * Moments are stored as the *wall-clock* time the user saw, encoded as
 * milliseconds since epoch as if that wall clock were UTC. A purchase entered
 * at 23:30 therefore stays on that calendar day no matter what time zone the
 * phone later travels to, and SQL day/month grouping needs no `localtime`
 * conversion - results are identical on every device and in tests.
 */

internal fun LocalDateTime.toStorage(): Long = toEpochSecond(ZoneOffset.UTC) * 1000 + nano / 1_000_000

internal fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofEpochSecond(Math.floorDiv(this, 1000L), Math.floorMod(this, 1000L).toInt() * 1_000_000, ZoneOffset.UTC)

// ---- Cursor access ---------------------------------------------------------

internal fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

internal fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

internal fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

internal fun Cursor.longOrNull(column: String): Long? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getLong(index)
}

internal fun Cursor.intOrNull(column: String): Int? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getInt(index)
}

internal fun Cursor.stringOrNull(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
}

internal inline fun <T> Cursor.map(transform: (Cursor) -> T): List<T> = use {
    buildList(count) { while (moveToNext()) add(transform(this@map)) }
}

// ---- ContentValues ---------------------------------------------------------

internal fun ContentValues.putNullable(key: String, value: Long?) {
    if (value == null) putNull(key) else put(key, value)
}

internal fun ContentValues.putNullable(key: String, value: Int?) {
    if (value == null) putNull(key) else put(key, value)
}

internal fun ContentValues.putNullable(key: String, value: String?) {
    if (value == null) putNull(key) else put(key, value)
}

// ---- Entities <-> rows -----------------------------------------------------

internal object FinanceRows {
    fun account(a: Account) = ContentValues().apply {
        a.id?.let { put("id", it) }
        put("name", a.name)
        put("kind", a.kind.key)
        put("opening_balance", a.openingBalance)
        put("archived", if (a.archived) 1 else 0)
        put("sort_order", a.sortOrder)
        put("created_at", a.createdAt.toStorage())
        put("updated_at", a.updatedAt.toStorage())
        putNullable("bank_key", a.bankKey)
        putNullable("credit_limit", a.creditLimit)
        putNullable("statement_day", a.statementDay)
        putNullable("due_day", a.dueDay)
    }

    fun bank(b: CustomBank) = ContentValues().apply {
        put("key", b.key)
        put("name", b.name)
        put("created_at", b.createdAt.toStorage())
    }

    fun toBank(c: Cursor) = CustomBank(
        key = c.string("key"),
        name = c.string("name"),
        createdAt = c.long("created_at").toLocalDateTime(),
    )

    fun toAccount(c: Cursor) = Account(
        id = c.long("id"),
        name = c.string("name"),
        kind = AccountKind.fromKey(c.string("kind")) ?: AccountKind.Other,
        openingBalance = c.long("opening_balance"),
        archived = c.int("archived") == 1,
        sortOrder = c.int("sort_order"),
        createdAt = c.long("created_at").toLocalDateTime(),
        updatedAt = c.long("updated_at").toLocalDateTime(),
        bankKey = c.stringOrNull("bank_key"),
        creditLimit = c.longOrNull("credit_limit"),
        statementDay = c.intOrNull("statement_day"),
        dueDay = c.intOrNull("due_day"),
    )

    fun category(cat: Category) = ContentValues().apply {
        cat.id?.let { put("id", it) }
        put("name", cat.name)
        put("type", cat.type.key)
        put("icon", cat.icon)
        put("color", cat.color)
        put("archived", if (cat.archived) 1 else 0)
        put("sort_order", cat.sortOrder)
        put("created_at", cat.createdAt.toStorage())
        put("updated_at", cat.updatedAt.toStorage())
        putNullable("parent_id", cat.parentId)
    }

    fun toCategory(c: Cursor) = Category(
        id = c.long("id"),
        name = c.string("name"),
        type = CategoryType.fromKey(c.string("type")) ?: CategoryType.Expense,
        icon = c.string("icon"),
        color = c.int("color"),
        archived = c.int("archived") == 1,
        sortOrder = c.int("sort_order"),
        createdAt = c.long("created_at").toLocalDateTime(),
        updatedAt = c.long("updated_at").toLocalDateTime(),
        parentId = c.longOrNull("parent_id"),
    )

    fun transaction(t: FinanceTransaction) = ContentValues().apply {
        t.id?.let { put("id", it) }
        put("type", t.type.key)
        put("amount", t.amount)
        put("account_id", t.accountId)
        putNullable("to_account_id", t.toAccountId)
        putNullable("category_id", t.categoryId)
        put("occurred_at", t.occurredAt.toStorage())
        putNullable("payee", t.payee)
        putNullable("note", t.note)
        putNullable("recurring_id", t.recurringId)
        put("created_at", t.createdAt.toStorage())
        put("updated_at", t.updatedAt.toStorage())
        putNullable("person_id", t.personId)
        putNullable("picture", t.picture)
    }

    fun person(p: Person) = ContentValues().apply {
        p.id?.let { put("id", it) }
        put("name", p.name)
        putNullable("emoji", p.emoji)
        put("color", p.color)
        put("archived", if (p.archived) 1 else 0)
        put("created_at", p.createdAt.toStorage())
        put("updated_at", p.updatedAt.toStorage())
    }

    fun toPerson(c: Cursor) = Person(
        id = c.long("id"),
        name = c.string("name"),
        emoji = c.stringOrNull("emoji"),
        color = c.int("color"),
        archived = c.int("archived") == 1,
        createdAt = c.long("created_at").toLocalDateTime(),
        updatedAt = c.long("updated_at").toLocalDateTime(),
    )

    fun toTransaction(c: Cursor) = FinanceTransaction(
        id = c.long("id"),
        type = checkNotNull(TransactionType.fromKey(c.string("type"))),
        amount = c.long("amount"),
        accountId = c.long("account_id"),
        toAccountId = c.longOrNull("to_account_id"),
        categoryId = c.longOrNull("category_id"),
        occurredAt = c.long("occurred_at").toLocalDateTime(),
        payee = c.stringOrNull("payee"),
        note = c.stringOrNull("note"),
        recurringId = c.longOrNull("recurring_id"),
        createdAt = c.long("created_at").toLocalDateTime(),
        updatedAt = c.long("updated_at").toLocalDateTime(),
        personId = c.longOrNull("person_id"),
        picture = c.stringOrNull("picture"),
    )

    fun toBudget(c: Cursor) = Budget(
        id = c.long("id"),
        categoryId = c.longOrNull("category_id"),
        amount = c.longOrNull("amount"),
        effectiveMonth = MonthKey.fromInt(c.int("effective_month")),
    )

    fun recurring(r: RecurringRule) = ContentValues().apply {
        r.id?.let { put("id", it) }
        put("type", r.type.key)
        put("amount", r.amount)
        put("account_id", r.accountId)
        putNullable("to_account_id", r.toAccountId)
        putNullable("category_id", r.categoryId)
        putNullable("payee", r.payee)
        putNullable("note", r.note)
        put("frequency", r.frequency.key)
        put("interval", r.interval)
        put("start_date", r.startDate.toStorage())
        putNullable("end_date", r.endDate?.toStorage())
        put("next_date", r.nextDate.toStorage())
        put("active", if (r.active) 1 else 0)
        put("created_at", r.createdAt.toStorage())
        put("updated_at", r.updatedAt.toStorage())
        putNullable("person_id", r.personId)
    }

    fun toRecurring(c: Cursor) = RecurringRule(
        id = c.long("id"),
        type = checkNotNull(TransactionType.fromKey(c.string("type"))),
        amount = c.long("amount"),
        accountId = c.long("account_id"),
        toAccountId = c.longOrNull("to_account_id"),
        categoryId = c.longOrNull("category_id"),
        payee = c.stringOrNull("payee"),
        note = c.stringOrNull("note"),
        frequency = checkNotNull(RecurrenceFrequency.fromKey(c.string("frequency"))),
        interval = c.int("interval"),
        startDate = c.long("start_date").toLocalDateTime(),
        endDate = c.longOrNull("end_date")?.toLocalDateTime(),
        nextDate = c.long("next_date").toLocalDateTime(),
        active = c.int("active") == 1,
        createdAt = c.long("created_at").toLocalDateTime(),
        updatedAt = c.long("updated_at").toLocalDateTime(),
        personId = c.longOrNull("person_id"),
    )
}
