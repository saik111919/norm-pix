package com.example.normpix.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQueryBuilder

/*
 * The calls the app's databases were written with (Android's own SQLiteDatabase), on androidx.sqlite's SupportSQLiteDatabase - the
 * interface both the encrypted database (SQLCipher) and plain SQLite (tests, and a phone where SQLCipher won't load) implement.
 * Each does exactly what the platform call of the same name does, so the code that uses them reads and behaves as it always has.
 */

/** Runs [sql] and returns the rows, binding [args] to its `?`s. */
fun SupportSQLiteDatabase.rawQuery(sql: String, args: Array<out String>?): Cursor = query(sql, args ?: emptyArray())

/** Inserts [values] into [table]; throws if the row breaks a rule (a constraint, a trigger), as the platform call does. */
@Suppress("UNUSED_PARAMETER")
fun SupportSQLiteDatabase.insertOrThrow(table: String, nullColumnHack: String?, values: ContentValues): Long =
    insert(table, SQLiteDatabase.CONFLICT_NONE, values)

/** Inserts [values] into [table], first deleting a row it would clash with (`INSERT OR REPLACE`); throws if it breaks another rule. */
@Suppress("UNUSED_PARAMETER")
fun SupportSQLiteDatabase.replaceOrThrow(table: String, nullColumnHack: String?, values: ContentValues): Long =
    insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)

/** Inserts [values] into [table]: the new row's id, or -1 - not an exception - if it couldn't be inserted, as the platform call does. */
fun SupportSQLiteDatabase.insert(table: String, nullColumnHack: String?, values: ContentValues): Long = try {
    insertOrThrow(table, nullColumnHack, values)
} catch (_: SQLException) {
    -1
}

/** Updates the rows of [table] that [whereClause] picks to [values]; how many were changed. Throws if a row breaks a rule. */
fun SupportSQLiteDatabase.update(table: String, values: ContentValues, whereClause: String?, whereArgs: Array<String>?): Int =
    update(table, SQLiteDatabase.CONFLICT_NONE, values, whereClause, whereArgs)

/** The platform's query builder: the rows of [table] that [selection] picks, [columns] of them (all when null), in [orderBy]. */
fun SupportSQLiteDatabase.query(
    table: String,
    columns: Array<String>?,
    selection: String?,
    selectionArgs: Array<String>?,
    groupBy: String?,
    having: String?,
    orderBy: String?,
    limit: String? = null,
): Cursor = query(
    SupportSQLiteQueryBuilder.builder(table)
        .columns(columns)
        .selection(selection, selectionArgs)
        .groupBy(groupBy)
        .having(having)
        .orderBy(orderBy)
        .apply { if (limit != null) limit(limit) }
        .create(),
)

/** Runs [body] in one transaction: all of it is kept if it returns, none of it if it throws. */
inline fun <T> SupportSQLiteDatabase.transaction(body: SupportSQLiteDatabase.() -> T): T {
    beginTransaction()
    try {
        val result = body()
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}
