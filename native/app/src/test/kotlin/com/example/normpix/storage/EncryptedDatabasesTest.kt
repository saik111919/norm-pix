package com.example.normpix.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.reminders.Reminder
import com.example.normpix.reminders.ReminderCategory
import com.example.normpix.reminders.ReminderDao
import com.example.normpix.reminders.RemindersDatabase
import com.example.normpix.reminders.RepeatRule
import com.example.normpix.wallet.VaultKeyProvider
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
 * Which file a database is kept in, and how a plain one is moved into an encrypted one: never losing a row, never deleting the plain
 * file before its copy is proven the same, and never opening a plain file in place of an encrypted one. SQLCipher can't load on the
 * JVM, so a stand-in copies databases without encrypting them; what is tested is every decision around it (the encryption itself
 * is checked on a phone).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EncryptedDatabasesTest {
    private lateinit var context: Context
    private val now = LocalDateTime.of(2026, 11, 3, 10, 0)
    private val name = "fin.db"
    private val remindersName = "rem.db"
    private val keystoreKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    /** SQLCipher's stand-in: copies a database as it is (VACUUM INTO) and opens files with plain SQLite. */
    private inner class StandIn : CipherEngine {
        var loads = true
        var exportFails = false
        var copyLosesARow = false
        var exports = 0

        override val available: Boolean get() = loads

        override fun helper(context: Context, name: String, key: ByteArray, callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteOpenHelper {
            assertEquals(DatabaseKey.KEY_BYTES, key.size)
            return DatabaseEngine.Plain.open(context, name, callback)
        }

        override fun export(plain: File, target: File, key: ByteArray) {
            if (exportFails) throw IOException("The export failed.")
            SQLiteDatabase.openDatabase(plain.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("VACUUM INTO ?", arrayOf(target.path)) }
            if (copyLosesARow) {
                SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use {
                    it.execSQL("DELETE FROM transactions WHERE id = (SELECT MAX(id) FROM transactions)")
                }
            }
            exports++
        }

        override fun open(file: File, key: ByteArray?): SupportSQLiteDatabase {
            val version = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
            val callback = object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }
            return FrameworkSQLiteOpenHelperFactory()
                .create(SupportSQLiteOpenHelper.Configuration.builder(context).name(file.name).callback(callback).build())
                .writableDatabase
        }
    }

    private val cipher = StandIn()
    private fun keyFile() = File(context.noBackupFilesDir, DatabaseKey.FILE_NAME)
    private fun engine(keystore: SecretKey = keystoreKey) = EncryptedDatabases(DatabaseKey(keyFile(), VaultKeyProvider { keystore }), cipher)
    private fun plainFile(of: String = name) = context.getDatabasePath(of)
    private fun encryptedFile(of: String = name) = context.getDatabasePath(EncryptedDatabases.encryptedName(of))
    private fun partialFile() = File(encryptedFile().path + ".partial")

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clean()
    }

    @AfterTest
    fun tearDown() = clean()

    private fun clean() {
        for (db in listOf(name, remindersName)) {
            SQLiteDatabase.deleteDatabase(plainFile(db))
            SQLiteDatabase.deleteDatabase(encryptedFile(db))
            SQLiteDatabase.deleteDatabase(File(encryptedFile(db).path + ".partial"))
        }
        keyFile().delete()
    }

    private fun ledger(engine: DatabaseEngine): FinanceRepository =
        FinanceRepository(FinanceDatabase(context, name, clock = { now }, engine = engine), clock = { now }, dispatcher = Dispatchers.Unconfined)

    private fun FinanceRepository.spend(payee: String) = runBlocking {
        val cash = accounts().first()
        val category = categories().first { it.type == CategoryType.Expense }
        saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense,
                amount = 25_000,
                accountId = cash.id!!,
                categoryId = category.id,
                occurredAt = now,
                payee = payee,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** Opens the ledger through [engine] - the first use, which settles its file - and closes it again. */
    private fun openOnce(engine: DatabaseEngine) = ledger(engine).let { repo ->
        runBlocking { repo.snapshot() }
        repo.close()
    }

    private fun FinanceRepository.payees(): List<String?> = runBlocking { snapshot().transactions.map { it.payee } }

    /** A ledger kept the way the app kept it before: in its plain file, with [payees] spent. Its snapshot. */
    private fun plainLedger(vararg payees: String) = ledger(DatabaseEngine.Plain).let { repo ->
        payees.forEach { repo.spend(it) }
        runBlocking { repo.snapshot() }.also { repo.close() }
    }

    // ----------------------------------------------------------------------------------------------------- new install

    @Test
    fun `a new install keeps its ledger encrypted from the start, and no plain file is made`() {
        val repo = ledger(engine())
        repo.spend("Cafe")
        repo.close()

        assertTrue(encryptedFile().exists())
        assertFalse(plainFile().exists())
        assertTrue(keyFile().exists())
        assertEquals(0, cipher.exports, "nothing to move")
        val again = ledger(engine())
        assertEquals(listOf<String?>("Cafe"), again.payees())
        again.close()
    }

    @Test
    fun `a database in memory stays in memory`() {
        val repo = FinanceRepository(FinanceDatabase(context, null, clock = { now }, engine = engine()), clock = { now }, dispatcher = Dispatchers.Unconfined)
        repo.spend("Cafe")
        assertEquals(listOf<String?>("Cafe"), repo.payees())
        repo.close()
        assertFalse(keyFile().exists())
        assertFalse(context.databaseList().any { it.endsWith(EncryptedDatabases.ENCRYPTED_SUFFIX) })
    }

    // ------------------------------------------------------------------------------------------------------------ move

    @Test
    fun `a plain ledger is moved into its encrypted file, the same to the last row, and the plain file goes`() {
        val before = plainLedger("Cafe", "Bus", "Rent")

        val repo = ledger(engine())
        assertEquals(before, runBlocking { repo.snapshot() })
        repo.close()

        assertEquals(1, cipher.exports)
        assertTrue(encryptedFile().exists())
        for (left in listOf("", "-journal", "-wal", "-shm")) assertFalse(File(plainFile().path + left).exists(), "plain file$left")
        assertFalse(partialFile().exists())

        // Moved once: opening it again opens the encrypted file and moves nothing.
        val again = ledger(engine())
        assertEquals(listOf<String?>("Cafe", "Bus", "Rent"), again.payees().sortedBy { listOf("Cafe", "Bus", "Rent").indexOf(it) })
        again.close()
        assertEquals(1, cipher.exports)
    }

    @Test
    fun `when the move fails the plain ledger is used just as it was, and the move is tried again next time`() {
        val before = plainLedger("Cafe")
        cipher.exportFails = true

        val repo = ledger(engine())
        assertEquals(before, runBlocking { repo.snapshot() })
        repo.spend("Bus")
        repo.close()
        assertTrue(plainFile().exists())
        assertFalse(encryptedFile().exists())
        assertFalse(partialFile().exists())

        cipher.exportFails = false
        val later = ledger(engine())
        assertEquals(setOf<String?>("Cafe", "Bus"), later.payees().toSet())
        later.close()
        assertTrue(encryptedFile().exists())
        assertFalse(plainFile().exists())
    }

    @Test
    fun `a copy that is not the same is never put in place, and the plain ledger stays`() {
        val before = plainLedger("Cafe", "Bus")
        cipher.copyLosesARow = true

        val repo = ledger(engine())
        assertEquals(before, runBlocking { repo.snapshot() })
        repo.close()

        assertEquals(1, cipher.exports)
        assertTrue(plainFile().exists())
        assertFalse(encryptedFile().exists())
        assertFalse(partialFile().exists())
    }

    @Test
    fun `what a move cut short left behind is cleared, and the move is done again`() {
        val before = plainLedger("Cafe")
        partialFile().writeBytes(ByteArray(4096) { 7 })

        val repo = ledger(engine())
        assertEquals(before, runBlocking { repo.snapshot() })
        repo.close()

        assertTrue(encryptedFile().exists())
        assertFalse(plainFile().exists())
        assertFalse(partialFile().exists())
    }

    // ---------------------------------------------------------------------------------------------- both files there

    @Test
    fun `a plain file holding the same as the encrypted one - a move stopped before deleting it - is deleted`() {
        plainLedger("Cafe")
        openOnce(engine())
        // As if the app stopped between putting the encrypted file in place and deleting the plain one.
        encryptedFile().copyTo(plainFile())

        val repo = ledger(engine())
        assertEquals(listOf<String?>("Cafe"), repo.payees())
        repo.close()
        assertFalse(plainFile().exists())
    }

    @Test
    fun `a plain file holding anything else is left alone, and the encrypted ledger is the one used`() {
        plainLedger("Cafe")
        openOnce(engine())
        // A build from before encryption, run since, made a plain ledger of its own.
        plainLedger("Made by an older build")
        val untouched = plainFile().readBytes()

        val repo = ledger(engine())
        assertEquals(listOf<String?>("Cafe"), repo.payees())
        repo.close()
        assertTrue(plainFile().exists())
        assertContentEquals(untouched, plainFile().readBytes())
    }

    // ------------------------------------------------------------------------------------------- SQLCipher, the key

    @Test
    fun `where SQLCipher can't be used the plain ledger is used as before, and no key is made`() {
        val before = plainLedger("Cafe")
        cipher.loads = false

        val repo = ledger(engine())
        assertEquals(before, runBlocking { repo.snapshot() })
        repo.close()

        assertTrue(plainFile().exists())
        assertFalse(encryptedFile().exists())
        assertFalse(keyFile().exists())
    }

    @Test
    fun `an encrypted ledger whose key is gone does not open, is left as it is, and no plain one is made in its place`() {
        val repo = ledger(engine())
        repo.spend("Cafe")
        repo.close()
        val kept = encryptedFile().readBytes()

        val otherKeystore = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val stranded = ledger(engine(keystore = otherKeystore))
        assertFailsWith<DatabaseKeyException> { runBlocking { stranded.snapshot() } }
        stranded.close()

        assertContentEquals(kept, encryptedFile().readBytes())
        assertFalse(plainFile().exists())
        assertTrue(keyFile().exists(), "the key file is kept: the Keystore key may come back")
    }

    @Test
    fun `an encrypted ledger is not opened as plain where SQLCipher won't load`() {
        openOnce(engine())
        cipher.loads = false

        val stranded = ledger(engine())
        assertFailsWith<IllegalStateException> { runBlocking { stranded.snapshot() } }
        stranded.close()
        assertFalse(plainFile().exists())
        assertTrue(encryptedFile().exists())
    }

    @Test
    fun `a key that can't be opened is replaced while nothing is encrypted with it`() {
        val lostKeystoreKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        DatabaseKey(keyFile(), VaultKeyProvider { lostKeystoreKey }).get(mayCreate = true)
        val before = plainLedger("Cafe")

        val repo = ledger(engine())
        assertEquals(before, runBlocking { repo.snapshot() })
        repo.close()
        assertTrue(encryptedFile().exists())
        assertFalse(plainFile().exists())
    }

    @Test
    fun `the reminders move too, under the same key, made once`() {
        plainLedger("Cafe")
        val reminder = Reminder(
            title = "Pay rent",
            category = ReminderCategory.Payments,
            dueAt = now.plusDays(2),
            repeatRule = RepeatRule.Once,
            createdAt = now,
            updatedAt = now,
        )
        RemindersDatabase(context, remindersName).let { db ->
            runBlocking { ReminderDao(db, dispatcher = Dispatchers.Unconfined).insert(reminder) }
            db.close()
        }

        val engine = engine()
        openOnce(engine)
        val key = keyFile().readBytes()
        val reminders = RemindersDatabase(context, remindersName, engine)
        val titles = runBlocking { ReminderDao(reminders, dispatcher = Dispatchers.Unconfined).getAll() }.map { it.title }
        reminders.close()

        assertEquals(listOf("Pay rent"), titles)
        assertTrue(encryptedFile(remindersName).exists())
        assertFalse(plainFile(remindersName).exists())
        assertContentEquals(key, keyFile().readBytes(), "one key for both")
    }
}
