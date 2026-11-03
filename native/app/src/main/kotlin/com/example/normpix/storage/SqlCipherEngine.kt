package com.example.normpix.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * SQLCipher (the community edition, `net.zetetic:sqlcipher-android`): the only place the app calls it. Its native library is loaded
 * the first time it is asked for; where it won't load (the JVM the tests run on) [available] is false and nothing else here is used.
 *
 * The key is handed over as raw key data - `x'<64 hex digits>'` - so SQLCipher uses it as it is: it is already 32 random bytes, and
 * drawing a key from it again would only slow every start.
 */
object SqlCipherEngine : CipherEngine {
    override val available: Boolean by lazy {
        try {
            System.loadLibrary("sqlcipher")
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun helper(context: Context, name: String, key: ByteArray, callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteOpenHelper =
        SupportOpenHelperFactory(rawKey(key), null, false).create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        )

    /**
     * The new file is opened with its key - set through SQLCipher's API, so the key is never part of any SQL text, which SQLCipher
     * writes to the log when a statement fails - and the plain database is attached to it with an empty key (no encryption) and
     * exported into it: the order the SQLCipher API documentation gives for this (`sqlcipher_export('main', 'plain')`).
     */
    override fun export(plain: File, target: File, key: ByteArray) {
        // Whatever the plain database's write-ahead log still holds goes into its file first, so the copy has all of it.
        SQLiteDatabase.openDatabase(plain.path, ByteArray(0), null, SQLiteDatabase.OPEN_READWRITE, null, null).use { db ->
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        }
        val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
        SQLiteDatabase.openDatabase(target.path, rawKey(key), null, flags, null, null).use { db ->
            db.rawExecSQL("ATTACH DATABASE '${plain.path.replace("'", "''")}' AS plain KEY ''")
            try {
                db.rawExecSQL("SELECT sqlcipher_export('main', 'plain')")
                // The export copies the schema and every row, not the version the migrations count from.
                val version = db.query("PRAGMA plain.user_version").use { it.moveToFirst(); it.getInt(0) }
                db.rawExecSQL("PRAGMA main.user_version = $version")
            } finally {
                db.rawExecSQL("DETACH DATABASE plain")
            }
        }
    }

    override fun open(file: File, key: ByteArray?): SupportSQLiteDatabase =
        SQLiteDatabase.openDatabase(file.path, key?.let(::rawKey) ?: ByteArray(0), null, SQLiteDatabase.OPEN_READWRITE, null, null)

    private fun rawKey(key: ByteArray): ByteArray = "x'${hex(key)}'".toByteArray(Charsets.US_ASCII)

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }
}
