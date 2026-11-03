package com.example.normpix.storage

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * A fingerprint of everything a database holds: its version, its schema (tables, indexes, triggers - each as written) and every
 * row of every table, value by value with its type. Two databases with the same digest hold the same data, which is what lets a
 * plain database be deleted once its encrypted copy is proven to match it. Nothing it reads is kept or logged: only the hash is.
 *
 * Rows are taken in the order of all their columns, not of their hidden row ids: copying a table renumbers the ids of a table
 * without an INTEGER PRIMARY KEY, and the data is the same all the same.
 */
object DatabaseDigest {
    fun of(db: SupportSQLiteDatabase): String {
        val sha = MessageDigest.getInstance("SHA-256")
        fun text(value: String?) {
            if (value == null) {
                sha.update(0)
            } else {
                val bytes = value.toByteArray(Charsets.UTF_8)
                sha.update(1)
                sha.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
                sha.update(bytes)
            }
        }

        text("version ${db.version}")
        val tables = mutableListOf<String>()
        // The query planner's statistics (sqlite_stat1..4) are not data, and a copy need not carry them.
        db.query("SELECT type, name, tbl_name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite\\_stat%' ESCAPE '\\' ORDER BY type, name").use { schema ->
            while (schema.moveToNext()) {
                for (i in 0 until 4) text(schema.stringOrNull(i))
                if (schema.getString(0) == "table") tables += schema.getString(1)
            }
        }
        for (table in tables) {
            val quoted = "\"" + table.replace("\"", "\"\"") + "\""
            val columns = db.query("SELECT * FROM $quoted LIMIT 0").use { it.columnCount }
            text("table $table $columns")
            val order = (1..columns).joinToString(", ")
            db.query("SELECT * FROM $quoted ORDER BY $order").use { rows ->
                while (rows.moveToNext()) {
                    for (i in 0 until columns) value(sha, rows, i)
                }
            }
        }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }

    private fun Cursor.stringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)

    private fun value(sha: MessageDigest, row: Cursor, i: Int) {
        when (row.getType(i)) {
            Cursor.FIELD_TYPE_NULL -> sha.update(0)
            Cursor.FIELD_TYPE_INTEGER -> {
                sha.update(1)
                sha.update(ByteBuffer.allocate(8).putLong(row.getLong(i)).array())
            }
            Cursor.FIELD_TYPE_FLOAT -> {
                sha.update(2)
                sha.update(ByteBuffer.allocate(8).putDouble(row.getDouble(i)).array())
            }
            Cursor.FIELD_TYPE_BLOB -> {
                val bytes = row.getBlob(i)
                sha.update(3)
                sha.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
                sha.update(bytes)
            }
            else -> {
                val bytes = row.getString(i).toByteArray(Charsets.UTF_8)
                sha.update(4)
                sha.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
                sha.update(bytes)
            }
        }
    }
}
