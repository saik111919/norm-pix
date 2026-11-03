package com.example.normpix.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.io.File

/**
 * What SQLCipher does for [EncryptedDatabases], on its own so that tests - where SQLCipher's native library can't load - can stand
 * in for it ([SqlCipherEngine] is the real one).
 */
interface CipherEngine {
    /** Whether SQLCipher can be used here: its native library is loaded. */
    val available: Boolean

    /** The helper for the encrypted database file [name] in the databases folder, keyed with [key]. */
    fun helper(context: Context, name: String, key: ByteArray, callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteOpenHelper

    /** Copies the plain database [plain] - its tables, indexes, triggers and version - into a new file [target] encrypted with [key]. */
    fun export(plain: File, target: File, key: ByteArray)

    /** [file] opened as it is: encrypted with [key], or a plain database when [key] is null. The caller closes it. */
    fun open(file: File, key: ByteArray?): SupportSQLiteDatabase
}

/**
 * The app's databases encrypted on this phone (SQLCipher, AES-256), under the [key] the Android Keystore keeps. A database is
 * encrypted in a file of its own, `<name>.encrypted.db`, beside where its plain file was: a build from before this never looks
 * there, so it can't take the encrypted file for a damaged one and delete it.
 *
 * Opening a database (the first time the app reads or writes it, on its database thread) settles which file it uses:
 * - its encrypted file, once there is one - always, whatever goes wrong after: then the database fails to open, and its file
 *   stays as it is; it is never swapped for a plain one;
 * - a new encrypted file, on a new install;
 * - its plain file moved over: copied into an encrypted file beside it, the copy checked (intact, and the same as the original to
 *   the last row, see [DatabaseDigest]) and only then put in place and the plain file deleted;
 * - its plain file as it always was, untouched, when SQLCipher can't be used here, the key can't be had, or the move fails - the
 *   move is tried again the next time the app starts.
 */
class EncryptedDatabases(
    private val key: DatabaseKey,
    private val cipher: CipherEngine,
) : DatabaseEngine {
    override fun open(context: Context, name: String?, callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteOpenHelper {
        // A database in memory never reaches the disk, so there is nothing to encrypt (the sample data).
        if (name == null) return DatabaseEngine.Plain.open(context, null, callback)
        val app = context.applicationContext
        return Deferred(name) { choose(app, name, callback) }
    }

    private fun choose(context: Context, name: String, callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteOpenHelper {
        val plain = context.getDatabasePath(name)
        val encryptedName = encryptedName(name)
        val encrypted = context.getDatabasePath(encryptedName)
        fun asBefore() = DatabaseEngine.Plain.open(context, name, callback)

        if (encrypted.exists()) {
            check(cipher.available) { "The encrypted database can't be opened here." }
            val key = key.get(mayCreate = false)
            // A plain file beside it: a move that stopped before deleting it, or one a build from before this made since. It goes
            // only if it holds exactly what the encrypted one does; anything else in it is left alone.
            if (plain.exists()) removeIfCopied(plain, encrypted, key)
            return cipher.helper(context, encryptedName, key, callback)
        }
        if (!cipher.available) return asBefore()
        val key = keyForNew(context) ?: return asBefore()
        if (!plain.exists()) return cipher.helper(context, encryptedName, key, callback)
        return try {
            move(plain, encrypted, key)
            cipher.helper(context, encryptedName, key, callback)
        } catch (_: Exception) {
            asBefore()
        }
    }

    /**
     * The key for a database being encrypted now: the one there is, or a new one when nothing is encrypted yet. A key that can't be
     * opened is replaced only while nothing is encrypted with it; otherwise null, and the database stays plain for now.
     */
    private fun keyForNew(context: Context): ByteArray? {
        val anyEncrypted = context.databaseList().any { it.endsWith(ENCRYPTED_SUFFIX) }
        return try {
            key.get(mayCreate = !anyEncrypted)
        } catch (_: DatabaseKeyException) {
            if (anyEncrypted) {
                null
            } else {
                try {
                    key.discard()
                    key.get(mayCreate = true)
                } catch (_: DatabaseKeyException) {
                    null
                }
            }
        }
    }

    /** Encrypts [plain] into [encrypted]: copied, proven the same, put in place, and only then the plain file deleted. */
    private fun move(plain: File, encrypted: File, key: ByteArray) {
        val partial = File(encrypted.path + PARTIAL_SUFFIX)
        // What a move cut short (the app stopped half way) left behind.
        SQLiteDatabase.deleteDatabase(partial)
        try {
            cipher.export(plain, partial, key)
            val same = cipher.open(partial, key).use { copy ->
                cipher.open(plain, null).use { original -> intact(copy) && DatabaseDigest.of(copy) == DatabaseDigest.of(original) }
            }
            check(same) { "The encrypted copy is not the same as the database." }
            check(partial.renameTo(encrypted)) { "The encrypted copy couldn't be put in place." }
        } catch (e: Exception) {
            SQLiteDatabase.deleteDatabase(partial)
            throw e
        }
        // The copy's own empty journal, which stayed under the copy's name.
        SQLiteDatabase.deleteDatabase(partial)
        // From here on the encrypted file is the database. Should deleting the plain one fail, the next start finds both and does it.
        SQLiteDatabase.deleteDatabase(plain)
    }

    /** Deletes [plain] when it holds exactly what [encrypted] does; leaves it alone otherwise, or when either can't be read. */
    private fun removeIfCopied(plain: File, encrypted: File, key: ByteArray) {
        try {
            val same = cipher.open(encrypted, key).use { copy ->
                cipher.open(plain, null).use { original -> DatabaseDigest.of(copy) == DatabaseDigest.of(original) }
            }
            if (same) SQLiteDatabase.deleteDatabase(plain)
        } catch (_: Exception) {
            // Kept: nothing is lost by keeping it.
        }
    }

    /** SQLite's own check of every page and index, and - where there is one - SQLCipher's check of every page's seal. */
    private fun intact(db: SupportSQLiteDatabase): Boolean {
        val ok = db.query("PRAGMA integrity_check").use { it.moveToFirst() && it.getString(0) == "ok" }
        // Rows only for pages that fail their check; plain SQLite doesn't know the pragma and gives none.
        val sealed = db.query("PRAGMA cipher_integrity_check").use { it.count == 0 }
        return ok && sealed
    }

    /** Opens a database only when it is first used, so that settling which file it is runs on its database thread. */
    private class Deferred(private val name: String, private val make: () -> SupportSQLiteOpenHelper) : SupportSQLiteOpenHelper {
        @Volatile
        private var made: SupportSQLiteOpenHelper? = null
        private var writeAheadLogging: Boolean? = null

        private fun helper(): SupportSQLiteOpenHelper = made ?: synchronized(this) {
            made ?: make().also { helper ->
                writeAheadLogging?.let(helper::setWriteAheadLoggingEnabled)
                made = helper
            }
        }

        override val databaseName: String get() = name

        override fun setWriteAheadLoggingEnabled(enabled: Boolean) = synchronized(this) {
            writeAheadLogging = enabled
            made?.setWriteAheadLoggingEnabled(enabled)
            Unit
        }

        override val writableDatabase: SupportSQLiteDatabase get() = helper().writableDatabase

        override val readableDatabase: SupportSQLiteDatabase get() = helper().readableDatabase

        override fun close() = synchronized(this) {
            made?.close()
            Unit
        }
    }

    companion object {
        const val ENCRYPTED_SUFFIX = ".encrypted.db"
        private const val PARTIAL_SUFFIX = ".partial"

        /** The file the database [name] is encrypted in: `normpix_finance.db` is in `normpix_finance.encrypted.db`. */
        fun encryptedName(name: String) = name.removeSuffix(".db") + ENCRYPTED_SUFFIX

        /** Whether this phone keeps the database [name] at all, plain or encrypted (a new install keeps neither). */
        fun exists(context: Context, name: String) =
            context.getDatabasePath(name).exists() || context.getDatabasePath(encryptedName(name)).exists()

        /** Whether the database [name] is kept encrypted on this phone, with no plain copy of it beside it. */
        fun isEncrypted(context: Context, name: String) =
            context.getDatabasePath(encryptedName(name)).exists() && !context.getDatabasePath(name).exists()

        /** The app's own: SQLCipher, and the key in the app's no-backup folder. */
        fun forApp(context: Context) = EncryptedDatabases(
            key = DatabaseKey(File(context.applicationContext.noBackupFilesDir, DatabaseKey.FILE_NAME)),
            cipher = SqlCipherEngine,
        )
    }
}
