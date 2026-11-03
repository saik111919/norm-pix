package com.example.normpix.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.normpix.backup.AppBackupArchive
import com.example.normpix.backup.AppBackupExporter
import com.example.normpix.backup.AppBackupManifest
import com.example.normpix.backup.LockedFile
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.reminders.Reminder
import com.example.normpix.reminders.ReminderCategory
import com.example.normpix.reminders.RepeatRule
import com.example.normpix.settings.ui.AppBackupFlows
import com.example.normpix.settings.ui.BackupFiles
import com.example.normpix.settings.ui.BackupSource
import com.example.normpix.settings.ui.openBackup
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.CardsTransfer
import com.example.normpix.wallet.StoredCard
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.Locale
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Backing up everything and restoring it (Settings > Data & backup), as the user goes through it: what is asked, what is said,
 * what is written and what comes back - with the real sheets and snackbars on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class AppBackupFlowsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Where a backup goes: remembers what it was asked to save. */
    private class FakeFiles : BackupFiles {
        /** The user cancels the save dialog. */
        var cancels = false

        /** Writing fails, the way a full disk would. */
        var fails = false
        var asked = 0
        var fileName: String? = null
        var mimeType: String? = null
        var bytes: ByteArray? = null

        override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): Boolean = error("An app backup is saved as a stream.")

        override suspend fun open(): ByteArray? = null

        override suspend fun saveStreaming(fileName: String, mimeType: String, write: suspend (OutputStream) -> Unit): Boolean {
            asked++
            if (cancels) return false
            if (fails) throw IOException("The disk is full.")
            val buffer = ByteArrayOutputStream()
            write(buffer)
            this.fileName = fileName
            this.mimeType = mimeType
            this.bytes = buffer.toByteArray()
            return true
        }
    }

    private val now = LocalDateTime.of(2026, 10, 19, 9, 30)
    private val files = FakeFiles()
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var scratch: File

    /** The phone the app runs on: the flows act on it. */
    private lateinit var phone: TestEnvironment

    /** The phone a backup was made on. */
    private lateinit var oldPhone: TestEnvironment
    private var flows: AppBackupFlows? = null
    private var overlays: AppOverlays? = null
    private var finished = false
    private var reads = 0

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        phone = TestEnvironment("backup_flows_phone")
        oldPhone = TestEnvironment("backup_flows_old_phone")
        scratch = Files.createTempDirectory("backup-flows").toFile()
    }

    @After
    fun tearDown() {
        testScope.cancel()
        phone.close()
        oldPhone.close()
        scratch.deleteRecursively()
    }

    private fun host() {
        compose.setContent {
            phone.Content {
                // The app's own overlays, snackbars and scope, which the page around the screen hosts: the same ones the Data page is given.
                val overlays = LocalOverlays.current
                val snackbars = LocalSnackbars.current
                val scope = LocalAppScope.current
                val haptics = rememberAppHaptics()
                val made = remember { AppBackupFlows(phone.context, phone.container, overlays, snackbars, files, scope, haptics) }
                SideEffect {
                    flows = made
                    this@AppBackupFlowsTest.overlays = overlays
                }
            }
        }
        compose.waitForIdle()
    }

    private fun start(block: suspend () -> Unit) {
        finished = false
        compose.runOnUiThread {
            testScope.launch {
                try {
                    block()
                } finally {
                    finished = true
                }
            }
        }
    }

    // The flag is set when work done off the main thread resumes on it; nothing here reads a node, so the looper is idled by hand.
    private fun await() = compose.waitUntil(15_000) { compose.waitForIdle(); finished }

    private fun shown(text: String, substring: Boolean = false) = compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(15_000) { shown(text, substring) }

    /** Answers "Lock the backup?": locked with [password], or - null - saved as it is. */
    private fun answerLock(password: String? = null) {
        waitFor("Lock the backup?")
        if (password == null) {
            compose.onNodeWithText("Don't lock").performClick()
            compose.onNodeWithText("Save").performClick()
        } else {
            compose.onNodeWithText("Password").performTextInput(password)
            compose.onNodeWithText("Password again").performTextInput(password)
            compose.onNodeWithText("Lock and save").performClick()
        }
    }

    /** What a locked backup holds, unlocked with [password]. */
    private fun unlocked(bytes: ByteArray, password: String): ByteArray {
        assertTrue(LockedFile.isLocked(bytes))
        val opening = assertIs<LockedFile.Opening.Locked>(LockedFile.open(ByteArrayInputStream(bytes)))
        return assertNotNull(opening.unlock(password.toCharArray()), "the password opens it").readBytes()
    }

    private fun reminder(title: String) = Reminder(
        title = title,
        category = ReminderCategory.Payments,
        dueAt = now.plusDays(2),
        repeatRule = RepeatRule.Once,
        createdAt = now,
        updatedAt = now,
    )

    private fun spend(env: TestEnvironment, payee: String) = runBlocking {
        val finance = env.container.finance
        val bank = finance.state.value.accounts.firstOrNull { it.name == "Bank" }
            ?: finance.saveAccount(Account(name = "Bank", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        val category = finance.state.value.categories.first { it.type == CategoryType.Expense }
        finance.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense,
                amount = 40_000,
                accountId = bank.id!!,
                categoryId = category.id,
                occurredAt = now,
                payee = payee,
                createdAt = now,
                updatedAt = now,
            ),
        )
        Unit
    }

    private fun payees(env: TestEnvironment): List<String?> =
        runBlocking { env.container.finance.repository.snapshot().transactions.map { it.payee } }

    private fun titles(env: TestEnvironment): Set<String> = env.container.reminders.state.value.reminders.map { it.title }.toSet()

    /** Some data in [env]: an expense, a setting and a reminder. */
    private fun fill(env: TestEnvironment, payee: String, mode: ThemeMode, reminderTitle: String) {
        spend(env, payee)
        env.preferences.setMode(mode)
        runBlocking { env.container.reminders.addReminder(reminder(reminderTitle)) }
    }

    private fun card() = StoredCard("a", "HDFC Bank", "K SAI", "4111111111111111", "08", "2029", "123", CardKind.Credit, now)

    /** A backup of [env] as the restore reads it. */
    private fun backupOf(env: TestEnvironment, cardsFile: String? = null): AppBackupArchive.Contents = runBlocking {
        val exporter = AppBackupExporter(env.context, env.container)
        val out = ByteArrayOutputStream()
        exporter.write(out, exporter.gather(now, cardsFile))
        AppBackupArchive.read(ByteArrayInputStream(out.toByteArray()), File(scratch, "old-${reads++}"))
    }

    private fun savedBackup(): AppBackupArchive.Contents =
        AppBackupArchive.read(ByteArrayInputStream(assertNotNull(files.bytes)), File(scratch, "saved-${reads++}"))

    // ------------------------------------------------------------------ export

    @Test
    fun `a backup without cards, not locked, asks nothing more, is saved as a zip and says what went in`() {
        fill(phone, "Cafe", ThemeMode.Dark, "Rent")
        runBlocking { phone.container.reminders.addReminder(reminder("Gym")) }
        host()

        start { flows!!.export() }
        answerLock(null)
        await()

        assertTrue(Regex("normpix-app-backup-\\d{4}-\\d{2}-\\d{2}\\.zip").matches(assertNotNull(files.fileName)), files.fileName)
        assertEquals("application/zip", files.mimeType)
        val saved = savedBackup()
        val app = assertNotNull(saved.app)
        assertEquals(listOf("finance", "settings", "reminders"), app.manifest.contents)
        assertNull(app.cards)
        assertTrue("theme_mode" in assertNotNull(app.settings))
        waitFor("App backup saved: finance data, settings and 2 reminders")
    }

    @Test
    fun `with Store cards off the cards are not asked about, whatever is stored`() {
        phone.vault.cards = listOf(card())
        runBlocking { phone.container.cards.load() }
        phone.preferences.setWalletCardsEnabled(false)
        host()

        start { flows!!.export() }
        answerLock(null)
        await()

        assertEquals(1, files.asked)
        assertNull(savedBackup().app?.cards)
        assertFalse(shown("Include saved cards?"))
    }

    @Test
    fun `with cards stored the user is asked, and leaving them out leaves them out`() {
        phone.vault.cards = listOf(card())
        runBlocking { phone.container.cards.load() }
        host()

        start { flows!!.export() }
        answerLock(null)
        waitFor("Include saved cards?")
        assertEquals(0, files.asked, "nothing is saved before the question is answered")
        compose.onNodeWithText("Leave out").performClick()
        await()

        val app = assertNotNull(savedBackup().app)
        assertNull(app.cards)
        assertFalse("cards" in app.manifest.contents)
        waitFor("App backup saved: finance data and settings")
    }

    @Test
    fun `closing the save dialog saves nothing and says nothing`() {
        files.cancels = true
        host()

        start { flows!!.export() }
        answerLock(null)
        await()

        assertEquals(1, files.asked)
        assertNull(files.bytes)
        compose.waitForIdle()
        compose.onAllNodesWithText("App backup saved", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Couldn't save", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a backup that cannot be written says so`() {
        files.fails = true
        host()

        start { flows!!.export() }
        answerLock(null)
        await()

        waitFor("Couldn't save the backup there. Try another location.")
        compose.onAllNodesWithText("App backup saved", substring = true).assertCountEquals(0)
    }

    // ------------------------------------------------------------ locked export

    @Test
    fun `a backup locked with a password is saved locked, and that password opens it to the same backup`() {
        fill(phone, "Cafe", ThemeMode.Dark, "Rent")
        host()

        start { flows!!.export() }
        answerLock("long enough")
        await()

        assertTrue(Regex("normpix-app-backup-\\d{4}-\\d{2}-\\d{2}\\.zip\\.locked").matches(assertNotNull(files.fileName)), files.fileName)
        assertEquals(LockedFile.MIME_TYPE, files.mimeType)
        val bytes = assertNotNull(files.bytes)
        assertNull(assertIs<LockedFile.Opening.Locked>(LockedFile.open(ByteArrayInputStream(bytes))).unlock("wrong password".toCharArray()))
        val app = assertNotNull(AppBackupArchive.read(ByteArrayInputStream(unlocked(bytes, "long enough")), File(scratch, "locked")).app)
        assertEquals(listOf("finance", "settings", "reminders"), app.manifest.contents)
        waitFor("App backup saved, locked with your password: finance data, settings and 1 reminder")
    }

    @Test
    fun `the lock sheet refuses a short password or two that differ, and nothing is saved until they are right`() {
        host()

        start { flows!!.export() }
        waitFor("Lock the backup?")
        compose.onNodeWithText("Password").performTextInput("short")
        compose.onNodeWithText("Password again").performTextInput("short")
        compose.onNodeWithText("Lock and save").performClick()
        waitFor("Use at least 8 characters")
        compose.onNodeWithText("Password").performTextInput(" and longer")
        compose.onNodeWithText("Lock and save").performClick()
        waitFor("The two passwords don't match")
        assertEquals(0, files.asked)

        compose.onNodeWithText("Password again").performTextInput(" and longer")
        compose.onNodeWithText("Lock and save").performClick()
        await()
        assertEquals(1, files.asked)
        assertNotNull(unlocked(assertNotNull(files.bytes), "short and longer"))
    }

    @Test
    fun `the saved cards in a locked backup are locked with its password, and no other password is asked for`() {
        phone.vault.cards = listOf(card())
        runBlocking { phone.container.cards.load() }
        host()

        start { flows!!.export() }
        answerLock("long enough")
        waitFor("They are locked with the backup's password.", substring = true)
        compose.onNodeWithText("Include").performClick()
        await()

        assertFalse(shown("Lock the cards file"))
        val app = assertNotNull(AppBackupArchive.read(ByteArrayInputStream(unlocked(assertNotNull(files.bytes), "long enough")), File(scratch, "cards")).app)
        val opened = assertIs<CardsTransfer.Result.Cards>(CardsTransfer.decode(assertNotNull(app.cards), "long enough".toCharArray()))
        assertEquals(listOf("4111111111111111"), opened.cards.map { it.cardNumber })
        waitFor("App backup saved, locked with your password: finance data, settings and 1 card")
    }

    // ----------------------------------------------------------------- restore

    @Test
    fun `restoring asks which parts, restores only those, says so and can be undone`() {
        fill(oldPhone, "Cafe", ThemeMode.Dark, "Rent")
        fill(phone, "Old shop", ThemeMode.Light, "Only on this phone")
        val read = backupOf(oldPhone)
        host()

        start { flows!!.restore(read.finance.json, read.finance.pictures, assertNotNull(read.app)) }
        waitFor("Restore this backup?")
        // Leave the reminders out.
        compose.onNodeWithText("Reminders").performClick()
        compose.onNodeWithText("Restore").performClick()
        await()

        waitFor("Backup restored: finance data and settings")
        assertEquals(listOf<String?>("Cafe"), payees(phone))
        assertEquals(ThemeMode.Dark, phone.preferences.state.value.mode)
        assertEquals(setOf("Only on this phone"), titles(phone), "the reminders weren't picked")

        compose.onNodeWithText("Undo").performClick()
        waitFor("Undone")
        assertEquals(listOf<String?>("Old shop"), payees(phone))
        assertEquals(ThemeMode.Light, phone.preferences.state.value.mode)
        assertEquals(setOf("Only on this phone"), titles(phone))
    }

    @Test
    fun `the reminders of a backup replace the ones on the phone when they are picked`() {
        fill(oldPhone, "Cafe", ThemeMode.Dark, "Rent")
        fill(phone, "Old shop", ThemeMode.Light, "Only on this phone")
        val read = backupOf(oldPhone)
        host()

        start { flows!!.restore(read.finance.json, read.finance.pictures, assertNotNull(read.app)) }
        waitFor("Restore this backup?")
        compose.onNodeWithText("Restore").performClick()
        await()

        waitFor("Backup restored: finance data, settings and 1 reminder")
        assertEquals(setOf("Rent"), titles(phone))
    }

    @Test
    fun `saved cards in a backup are left out while Store cards is off, and it says so`() {
        fill(oldPhone, "Cafe", ThemeMode.Dark, "Rent")
        val cardsFile = CardsTransfer.encode(listOf(card()), "password".toCharArray(), exportedAt = now, iterations = 10_000)
        val read = backupOf(oldPhone, cardsFile)
        phone.preferences.setWalletCardsEnabled(false)
        host()

        start { flows!!.restore(read.finance.json, read.finance.pictures, assertNotNull(read.app)) }
        waitFor("Restore this backup?")
        compose.onNodeWithText("Saved cards").assertIsNotEnabled()
        compose.onNodeWithText("Restore").performClick()
        await()

        waitFor("The saved cards were left out: Store cards is off", substring = true)
        assertTrue(phone.vault.cards.isEmpty())
        assertEquals(listOf<String?>("Cafe"), payees(phone))
        assertFalse(phone.preferences.state.value.walletCardsEnabled, "a backup does not turn Store cards back on")
    }

    @Test
    fun `a damaged backup is refused with the reason and nothing is changed`() {
        fill(phone, "Old shop", ThemeMode.Light, "Only on this phone")
        val app = AppBackupArchive.App(AppBackupManifest("1.4.0", null, emptyList()), "not json", null, null, emptyMap(), emptyMap())
        host()

        start { flows!!.restore(null, emptyMap(), app) }
        waitFor("Can't use this file")
        assertTrue(shown("The settings in this backup are damaged.", substring = true))
        compose.onNodeWithText("OK").performClick()
        await()

        assertEquals(listOf<String?>("Old shop"), payees(phone))
        assertEquals(ThemeMode.Light, phone.preferences.state.value.mode)
        assertEquals(setOf("Only on this phone"), titles(phone))
    }

    @Test
    fun `a backup with nothing in it to restore says so`() {
        val app = AppBackupArchive.App(AppBackupManifest("1.4.0", null, emptyList()), null, null, null, emptyMap(), emptyMap())
        host()

        start { flows!!.restore(null, emptyMap(), app) }
        waitFor("There is nothing in this backup that can be restored.", substring = true)
        compose.onNodeWithText("OK").performClick()
        await()
        assertFalse(shown("Restore this backup?"))
    }

    @Test
    fun `the saved cards of a locked backup open with the backup's password, without asking`() {
        fill(oldPhone, "Cafe", ThemeMode.Dark, "Rent")
        val cardsFile = CardsTransfer.encode(listOf(card()), "long enough".toCharArray(), exportedAt = now, iterations = 10_000)
        val read = backupOf(oldPhone, cardsFile)
        host()

        start { flows!!.restore(read.finance.json, read.finance.pictures, assertNotNull(read.app), password = "long enough") }
        waitFor("Restore this backup?")
        compose.onNodeWithText("Restore").performClick()
        await()

        assertFalse(shown("Open the cards file"))
        assertEquals(listOf("4111111111111111"), phone.vault.cards.map { it.cardNumber })
        waitFor("1 card added to Wallet", substring = true)
    }

    @Test
    fun `saved cards locked with another password than the backup's are asked for`() {
        fill(oldPhone, "Cafe", ThemeMode.Dark, "Rent")
        val cardsFile = CardsTransfer.encode(listOf(card()), "cards password".toCharArray(), exportedAt = now, iterations = 10_000)
        val read = backupOf(oldPhone, cardsFile)
        host()

        start { flows!!.restore(read.finance.json, read.finance.pictures, assertNotNull(read.app), password = "backup password") }
        waitFor("Restore this backup?")
        compose.onNodeWithText("Restore").performClick()
        waitFor("Open the cards file")
        compose.onNodeWithText("Password").performTextInput("cards password")
        compose.onNodeWithText("Open").performClick()
        await()

        assertEquals(listOf("4111111111111111"), phone.vault.cards.map { it.cardNumber })
    }

    // ------------------------------------------------------------ opening a file

    @Test
    fun `a backup that isn't locked is read as it is, and nothing is asked`() {
        val plain = "{\"format\":\"normpix-finance-backup\"}".toByteArray()
        host()
        var source: BackupSource? = null

        start { source = overlays!!.openBackup(ByteArrayInputStream(plain)) }
        await()

        val opened = assertNotNull(source)
        assertNull(opened.password)
        assertContentEquals(plain, opened.stream.readBytes())
        assertFalse(shown("Open the locked backup"))
    }

    @Test
    fun `a locked backup asks for its password, again after a wrong one, and opens to exactly what was locked`() {
        val inner = Random(7).nextBytes(2 * LockedFile.CHUNK + 300)
        val file = LockedFile.locker("long enough".toCharArray(), iterations = 10_000).lock(inner)
        host()
        var source: BackupSource? = null

        start { source = overlays!!.openBackup(ByteArrayInputStream(file)) }
        waitFor("Open the locked backup")
        compose.onNodeWithText("Password").performTextInput("wrong password")
        compose.onNodeWithText("Open").performClick()
        waitFor("That password doesn't open this backup. Try again.")
        compose.onNodeWithText("Password").performTextInput("long enough")
        compose.onNodeWithText("Open").performClick()
        await()

        val opened = assertNotNull(source)
        assertEquals("long enough", opened.password)
        assertContentEquals(inner, opened.stream.readBytes())
    }

    @Test
    fun `a locked backup cut short opens, but finishing reading it refuses it as damaged`() {
        val inner = Random(8).nextBytes(2 * LockedFile.CHUNK + 300)
        val file = LockedFile.locker("long enough".toCharArray(), iterations = 10_000).lock(inner)
        // Its last block gone: what is left ends where the second block ends.
        val cut = file.copyOf(file.size - (300 + 16))
        host()
        var source: BackupSource? = null

        start { source = overlays!!.openBackup(ByteArrayInputStream(cut)) }
        waitFor("Open the locked backup")
        compose.onNodeWithText("Password").performTextInput("long enough")
        compose.onNodeWithText("Open").performClick()
        await()

        val opened = assertNotNull(source)
        assertFailsWith<LockedFile.DamagedException> { opened.finish() }
    }

    @Test
    fun `a locked backup NormPix can't open says why, and asks no password`() {
        val file = LockedFile.locker("long enough".toCharArray(), iterations = 10_000).lock(ByteArray(10))
        val newer = String(file, Charsets.ISO_8859_1).replace("\"version\":1", "\"version\":9").toByteArray(Charsets.ISO_8859_1)
        host()
        var source: BackupSource? = null

        start { source = overlays!!.openBackup(ByteArrayInputStream(newer)) }
        waitFor("Can't use this file")
        assertTrue(shown("This backup was locked by a newer NormPix. Update NormPix, then restore it.", substring = true))
        compose.onNodeWithText("OK").performClick()
        await()

        assertNull(source)
        assertFalse(shown("Open the locked backup"))
    }
}
