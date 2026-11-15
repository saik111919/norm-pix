package com.example.normpix.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.DummyData
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.CardsTransfer
import com.example.normpix.wallet.StoredCard
import com.example.normpix.wallet.VaultKeyProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import com.example.normpix.finance.data.FinanceBackupCodec
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Making an automatic backup, as the job and "Back up now" do: what is written (the app backup, locked with the password, that the
 * restore reads), where, under what name, what is deleted to keep the newest five - and what happens when something goes wrong.
 * The folder is in memory, the Keystore's key is a plain AES key, the clock is fixed; the data is a real (in-memory) ledger.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupServiceTest {
    /** A clock that can be moved on. */
    private class TestClock(var now: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = TestClock(now, zone)
        override fun instant(): Instant = now
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val morning = LocalDateTime.of(2026, 11, 14, 9, 30)
    private val clock = TestClock(morning.atZone(zone).toInstant(), zone)
    private val folders = FakeBackupFolders()
    private val folder get() = folders.folder
    private val tree = "content://com.android.externalstorage.documents/tree/primary%3ABackups"
    private val password = "backup password 1"
    private val card = StoredCard("a", "HDFC Bank", "K SAI", "4111111111111111", "08", "2029", "123", CardKind.Credit, morning)

    private lateinit var dir: File
    private var keystore: SecretKey = newKey()
    private lateinit var secret: SealedSecret
    private lateinit var env: TestEnvironment
    private lateinit var service: AutoBackupService

    /** Stands in for the data when a test needs to (the sample set, cards that can't be read). */
    private var data: AutoBackupData? = null
    private var reads = 0

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("auto-backup").toFile()
        secret = SealedSecret(File(dir, AutoBackupService.FILE_NAME), VaultKeyProvider { keystore })
        env = TestEnvironment("auto_backup_service_test", vault = InMemoryVault(listOf(card))) { preferences, container ->
            AutoBackupService(
                preferences = preferences,
                secret = secret,
                folders = folders,
                data = { data ?: ContainerBackupData(context, container()) },
                clock = clock,
                iterations = 10_000,
            )
        }
        service = assertNotNull(env.container.autoBackup)
        spend("Cafe")
    }

    @After
    fun tearDown() {
        env.close()
        dir.deleteRecursively()
    }

    private val prefs get() = env.preferences.state.value

    private fun spend(payee: String) = runBlocking {
        val finance = env.container.finance
        val bank = finance.state.value.accounts.firstOrNull { it.name == "Bank" }
            ?: finance.saveAccount(Account(name = "Bank", kind = AccountKind.Bank, createdAt = morning, updatedAt = morning))
        val category = finance.state.value.categories.first { it.type == CategoryType.Expense }
        finance.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense,
                amount = 40_000,
                accountId = bank.id!!,
                categoryId = category.id,
                occurredAt = morning,
                payee = payee,
                createdAt = morning,
                updatedAt = morning,
            ),
        )
        Unit
    }

    private fun turnOn(with: String = password) = runBlocking { service.turnOn(tree, "Backups", with.toCharArray()) }

    private fun backUp(): AutoBackupOutcome = runBlocking { service.backUpNow() }

    /** What a locked backup holds, unlocked with [with] and read to its end, as the restore reads it. */
    private fun opened(bytes: ByteArray, with: String = password): AppBackupArchive.Contents {
        assertTrue(LockedFile.isLocked(bytes), "the file is locked")
        val opening = assertIs<LockedFile.Opening.Locked>(LockedFile.open(ByteArrayInputStream(bytes)))
        val plain = assertNotNull(opening.unlock(with.toCharArray()), "the password opens it").readBytes()
        return AppBackupArchive.read(ByteArrayInputStream(plain), File(dir, "read-${reads++}"))
    }

    private fun bytesOf(name: String): ByteArray = assertNotNull(folder.named(name), name).bytes.toByteArray()

    // ------------------------------------------------------------------ turning on and off

    @Test
    fun `turning on keeps the folder, seals the password and puts nothing of it in the preferences`() {
        turnOn()
        assertTrue(service.isOn)
        assertEquals(listOf(tree), folders.kept)
        assertTrue(secret.exists)
        assertContentEquals(password.toByteArray(), secret.open())
        assertTrue(prefs.autoBackupEnabled)
        assertEquals(tree, prefs.autoBackupFolder)
        assertEquals("Backups", prefs.autoBackupFolderName)
        // Nowhere in the preferences file, and nothing about automatic backups in what a backup carries.
        assertFalse(env.prefsStore.all.values.any { password in it.toString() })
        assertTrue(env.preferences.exportAll().keys.none { it.startsWith("auto_backup") })
    }

    @Test
    fun `a password shorter than the least a locked backup takes is refused, and zeroed`() {
        val short = "short".toCharArray()
        assertFailsWith<IllegalArgumentException> { runBlocking { service.turnOn(tree, "Backups", short) } }
        assertTrue(short.all { it == '\u0000' })
        assertFalse(prefs.autoBackupEnabled)
        assertFalse(secret.exists)
    }

    @Test
    fun `the password handed in is zeroed once sealed`() {
        val typed = password.toCharArray()
        runBlocking { service.turnOn(tree, "Backups", typed) }
        assertTrue(typed.all { it == '\u0000' })
    }

    @Test
    fun `a folder whose access can't be kept turns nothing on`() {
        folders.refuseKeep = true
        assertFailsWith<SecurityException> { turnOn() }
        assertFalse(prefs.autoBackupEnabled)
        assertFalse(secret.exists)
    }

    @Test
    fun `a password that can't be sealed turns nothing on, and gives the folder back`() {
        service = AutoBackupService(env.preferences, SealedSecret(File(dir, "broken.key")) { error("no Keystore") }, folders, { ContainerBackupData(context, env.container) }, clock, 10_000)
        assertFailsWith<SealedSecretException> { turnOn() }
        assertFalse(prefs.autoBackupEnabled)
        assertEquals(listOf(tree), folders.released)
        assertTrue(folders.kept.isEmpty())
    }

    @Test
    fun `turning off deletes the password, gives the folder back and forgets the choices, and the backups made stay`() {
        turnOn()
        env.preferences.setAutoBackupCards(true)
        val made = assertIs<AutoBackupOutcome.Saved>(backUp())
        assertTrue(runBlocking { service.turnOff() })
        assertFalse(secret.exists)
        assertFalse(File(dir, AutoBackupService.FILE_NAME).exists())
        assertEquals(listOf(tree), folders.released)
        assertFalse(service.isOn)
        assertFalse(prefs.autoBackupEnabled)
        assertEquals("", prefs.autoBackupFolder)
        assertFalse(prefs.autoBackupCards)
        assertNotNull(folder.named(made.fileName), "a backup already made is left where it is")
        assertEquals(AutoBackupOutcome.Off, backUp())
    }

    @Test
    fun `a password left behind with automatic backups off is deleted when the app starts, and kept while they are on`() {
        turnOn()
        runBlocking { service.tidy() }
        assertTrue(secret.exists)
        env.preferences.turnOffAutoBackup()
        runBlocking { service.tidy() }
        assertFalse(secret.exists)
    }

    @Test
    fun `changing the folder keeps the new one, gives the old one back and ends a folder problem`() {
        turnOn()
        env.preferences.recordAutoBackupProblem(AutoBackupProblem.FolderGone, 1L)
        val other = "content://com.android.externalstorage.documents/tree/primary%3AOther"
        runBlocking { service.changeFolder(other, "Other") }
        assertEquals(listOf(other), folders.kept)
        assertEquals(listOf(tree), folders.released)
        assertEquals(other, prefs.autoBackupFolder)
        assertEquals("Other", prefs.autoBackupFolderName)
        assertNull(prefs.autoBackupProblem)
    }

    @Test
    fun `a changed password locks the next backup, and the old one no longer opens it`() {
        turnOn()
        runBlocking { service.changePassword("a new password".toCharArray()) }
        val made = assertIs<AutoBackupOutcome.Saved>(backUp())
        val bytes = bytesOf(made.fileName)
        val opening = assertIs<LockedFile.Opening.Locked>(LockedFile.open(ByteArrayInputStream(bytes)))
        assertNull(opening.unlock(password.toCharArray()))
        assertNotNull(opened(bytes, with = "a new password").finance.json)
    }

    // ------------------------------------------------------------------ what is written

    @Test
    fun `nothing is done while automatic backups are off`() {
        assertEquals(AutoBackupOutcome.Off, backUp())
        assertTrue(folder.files.isEmpty())
        assertNull(prefs.autoBackupProblem)
    }

    @Test
    fun `a backup is the app backup locked with the password, which the restore reads`() {
        turnOn()
        val made = assertIs<AutoBackupOutcome.Saved>(backUp())
        assertEquals("NormPix-auto-backup-2026-11-14.zip.locked", made.fileName)
        assertEquals(listOf(made.fileName), folder.names)
        val bytes = bytesOf(made.fileName)

        val wrong = assertIs<LockedFile.Opening.Locked>(LockedFile.open(ByteArrayInputStream(bytes)))
        assertNull(wrong.unlock("not the password".toCharArray()), "another password doesn't open it")

        val contents = opened(bytes)
        assertTrue(contents.finance.json!!.contains("Cafe"))
        val app = assertNotNull(contents.app)
        assertNull(app.cards)
        val plan = assertIs<AppBackupReader.Parsed.Ok>(AppBackupReader.parse(contents.finance.json, contents.finance.pictures, app)).plan
        assertTrue(BackupPart.Finance in plan.parts && BackupPart.Settings in plan.parts && BackupPart.Reminders in plan.parts)
        assertFalse(BackupPart.Cards in plan.parts)
    }

    @Test
    fun `a backup is recorded as the last one, and counts as a backup for the reminder`() {
        turnOn()
        env.preferences.recordAutoBackupProblem(AutoBackupProblem.Failed, 5L)
        env.preferences.snoozeBackupNudge(Long.MAX_VALUE)
        val made = assertIs<AutoBackupOutcome.Saved>(backUp())
        assertEquals(clock.millis(), prefs.autoBackupLastAt)
        assertEquals(made.fileName, prefs.autoBackupLastFile)
        assertEquals(clock.millis(), prefs.lastBackupAt)
        assertEquals(0L, prefs.backupNudgeSnoozedUntil)
        assertNull(prefs.autoBackupProblem, "a backup that went well ends the problem before it")
    }

    @Test
    fun `saved cards are left out unless included, and then they are a cards file locked with the same password`() {
        turnOn()
        val without = assertIs<AutoBackupOutcome.Saved>(backUp())
        assertNull(opened(bytesOf(without.fileName)).app!!.cards)

        env.preferences.setAutoBackupCards(true)
        val with = assertIs<AutoBackupOutcome.Saved>(backUp())
        val cardsFile = assertNotNull(opened(bytesOf(with.fileName)).app!!.cards)
        assertFalse(cardsFile.contains("4111111111111111"), "never a card number in the clear")
        val cards = assertIs<CardsTransfer.Result.Cards>(CardsTransfer.decode(cardsFile, password.toCharArray())).cards
        assertEquals(listOf("4111111111111111"), cards.map { it.cardNumber })
        assertEquals(CardsTransfer.Result.WrongPassword, CardsTransfer.decode(cardsFile, "not the password".toCharArray()))
    }

    @Test
    fun `cards that can't be read are left out, the backup still made, and that is said`() {
        turnOn()
        env.preferences.setAutoBackupCards(true)
        val real = ContainerBackupData(context, env.container)
        data = object : AutoBackupData by real {
            override suspend fun cards(): List<StoredCard>? = null
        }
        val made = assertIs<AutoBackupOutcome.Saved>(backUp())
        assertTrue(made.cardsLeftOut)
        assertNull(opened(bytesOf(made.fileName)).app!!.cards)
        assertEquals(AutoBackupProblem.CardsLeftOut, prefs.autoBackupProblem)
        assertEquals(made.fileName, prefs.autoBackupLastFile)
    }

    @Test
    fun `cards are left out while Store cards is off, whatever the switch says`() {
        turnOn()
        env.preferences.setAutoBackupCards(true)
        env.preferences.setWalletCardsEnabled(false)
        val made = assertIs<AutoBackupOutcome.Saved>(backUp())
        assertFalse(made.cardsLeftOut)
        assertNull(opened(bytesOf(made.fileName)).app!!.cards)
    }

    @Test
    fun `a second backup the same day gets a number`() {
        turnOn()
        assertEquals("NormPix-auto-backup-2026-11-14.zip.locked", assertIs<AutoBackupOutcome.Saved>(backUp()).fileName)
        assertEquals("NormPix-auto-backup-2026-11-14-2.zip.locked", assertIs<AutoBackupOutcome.Saved>(backUp()).fileName)
    }

    // ------------------------------------------------------------------ keeping the newest five

    private fun auto(day: String) = "NormPix-auto-backup-$day.zip.locked"

    @Test
    fun `five are kept`() {
        assertEquals(5, AutoBackupNames.KEEP, "owner, 2026-11-14: delete old backups if there are more than 5")
    }

    @Test
    fun `the newest five automatic backups are kept, and nothing else in the folder is touched`() {
        turnOn()
        val others = listOf(
            "notes.txt",
            "normpix-app-backup-2026-11-01.zip.locked",
            "NormPix-auto-backup-2026-11-01 (1).zip.locked",
            "NormPix-auto-backup-2026-11-01.zip",
            "Copy of NormPix-auto-backup-2026-11-02.zip.locked",
        ).associateWith { folder.add(it, it.toByteArray()) }
        for (day in listOf("2026-11-01", "2026-11-03", "2026-11-05", "2026-11-07", "2026-11-10", "2026-11-10-2")) folder.add(auto(day))
        val made = assertIs<AutoBackupOutcome.Saved>(backUp())
        assertEquals(2, made.removed)
        assertEquals(listOf(auto("2026-11-01"), auto("2026-11-03")), folder.deleted.sorted())
        val autos = folder.names.filter { AutoBackupNames.isAutoBackupName(it) }.sorted()
        assertEquals(listOf("2026-11-05", "2026-11-07", "2026-11-10", "2026-11-10-2", "2026-11-14").map(::auto).sorted(), autos)
        for ((name, stored) in others) {
            assertSame(stored, folder.named(name), "$name is still there")
            assertContentEquals(name.toByteArray(), stored.bytes.toByteArray(), "$name is as it was")
        }
    }

    @Test
    fun `up to five, nothing is deleted`() {
        turnOn()
        for (day in listOf("2026-11-01", "2026-11-05", "2026-11-07", "2026-11-10")) folder.add(auto(day))
        val made = assertIs<AutoBackupOutcome.Saved>(backUp())
        assertEquals(0, made.removed)
        assertTrue(folder.deleted.isEmpty())
        assertEquals(5, folder.names.size)
    }

    @Test
    fun `the backup just made is never the one deleted, even when the clock is behind the others`() {
        turnOn()
        for (day in listOf("2027-01-01", "2027-01-02", "2027-01-03", "2027-01-04", "2027-01-05")) folder.add(auto(day))
        val made = assertIs<AutoBackupOutcome.Saved>(backUp())
        assertEquals(listOf(auto("2027-01-01")), folder.deleted)
        assertNotNull(folder.named(made.fileName))
        assertEquals(5, folder.names.size)
    }

    // ------------------------------------------------------------------ when something goes wrong

    @Test
    fun `a write that fails part way deletes the partial file and records why`() {
        turnOn()
        folder.failAfterBytes = 100
        val failed = assertIs<AutoBackupOutcome.Failed>(backUp())
        assertEquals(AutoBackupProblem.OutOfSpace, failed.problem)
        assertEquals(listOf("NormPix-auto-backup-2026-11-14.zip.locked"), folder.deleted)
        assertTrue(folder.files.isEmpty(), "no half-written file is left")
        assertEquals(AutoBackupProblem.OutOfSpace, prefs.autoBackupProblem)
        assertEquals(clock.millis(), prefs.autoBackupProblemAt)
        assertEquals(0L, prefs.autoBackupLastAt)
        assertEquals(0L, prefs.lastBackupAt, "a failed backup is no backup")

        folder.writeError = SecurityException("Permission Denial: writing")
        assertEquals(AutoBackupProblem.FolderGone, assertIs<AutoBackupOutcome.Failed>(backUp()).problem)
        assertTrue(folder.files.isEmpty())

        folder.writeError = java.io.IOException("Broken pipe")
        assertEquals(AutoBackupProblem.Failed, assertIs<AutoBackupOutcome.Failed>(backUp()).problem)
        assertTrue(folder.files.isEmpty())
    }

    @Test
    fun `a folder that is gone or can't be reached says to choose it again, and nothing is written`() {
        turnOn()
        folders.gone = true
        assertEquals(AutoBackupProblem.FolderGone, assertIs<AutoBackupOutcome.Failed>(backUp()).problem)
        assertEquals(AutoBackupProblem.FolderGone, prefs.autoBackupProblem)
        folders.gone = false
        folder.listError = FileNotFoundException("The folder can't be listed")
        assertEquals(AutoBackupProblem.FolderGone, assertIs<AutoBackupOutcome.Failed>(backUp()).problem)
        folder.listError = null
        folder.createError = SecurityException("Permission Denial")
        assertEquals(AutoBackupProblem.FolderGone, assertIs<AutoBackupOutcome.Failed>(backUp()).problem)
        assertTrue(folder.files.isEmpty())
    }

    @Test
    fun `a password that can't be opened is said, and nothing is written`() {
        turnOn()
        secret.delete()
        assertEquals(AutoBackupProblem.PasswordMissing, assertIs<AutoBackupOutcome.Failed>(backUp()).problem)
        assertTrue(folder.files.isEmpty())

        // The Keystore's key gone (another key now): the sealed file opens nothing.
        turnOn()
        keystore = newKey()
        assertEquals(AutoBackupProblem.PasswordMissing, assertIs<AutoBackupOutcome.Failed>(backUp()).problem)
        assertEquals(AutoBackupProblem.PasswordMissing, prefs.autoBackupProblem)
        assertTrue(folder.files.isEmpty())

        // A new password puts it right.
        runBlocking { service.changePassword("a new password".toCharArray()) }
        assertNull(prefs.autoBackupProblem)
        assertIs<AutoBackupOutcome.Saved>(backUp())
    }

    // ------------------------------------------------------------------ the sample set

    @Test
    fun `the sample set is never written`() {
        turnOn()
        val dummy = runBlocking { DummyData.open(context, env.container) { morning } }
        try {
            data = ContainerBackupData(context, dummy.container)
            assertEquals(AutoBackupProblem.SampleData, assertIs<AutoBackupOutcome.Failed>(backUp()).problem)
            assertTrue(folder.files.isEmpty())
        } finally {
            dummy.close()
        }
    }

    @Test
    fun `with the sample set showing, its container hands back the app's service, which backs up the user's own data`() {
        turnOn()
        val dummy = runBlocking { DummyData.open(context, env.container) { morning } }
        try {
            assertTrue(dummy.container.isSampleData)
            assertFalse(env.container.isSampleData)
            val shared = assertNotNull(dummy.container.autoBackup)
            assertSame(service, shared)
            val made = assertIs<AutoBackupOutcome.Saved>(runBlocking { shared.backUpNow() })
            val json = assertNotNull(opened(bytesOf(made.fileName)).finance.json)
            val backedUp = assertNotNull(FinanceBackupCodec.decode(json).snapshot)
            val real = runBlocking { env.container.finance.repository.snapshot() }
            val sample = runBlocking { dummy.container.finance.repository.snapshot() }
            assertTrue(sample.transactions.size > real.transactions.size)
            // Exactly the user's own transactions and accounts: not one of the sample set's.
            assertEquals(real.transactions.map { it.payee }.sortedBy { it }, backedUp.transactions.map { it.payee }.sortedBy { it })
            assertEquals(listOf("Cafe"), backedUp.transactions.map { it.payee })
            assertEquals(real.accounts.map { it.name }.sorted(), backedUp.accounts.map { it.name }.sorted())
        } finally {
            dummy.close()
        }
    }
}
