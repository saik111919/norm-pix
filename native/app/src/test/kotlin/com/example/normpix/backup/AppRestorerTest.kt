package com.example.normpix.backup

import android.content.Context
import com.example.normpix.finance.data.FinanceBackupArchive
import com.example.normpix.finance.data.FinanceBackupCodec
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.reminders.Reminder
import com.example.normpix.reminders.ReminderCategory
import com.example.normpix.reminders.RepeatRule
import com.example.normpix.settings.CustomFont
import com.example.normpix.settings.InterfaceFont
import com.example.normpix.settings.ProfilePhotos
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.CardsTransfer
import com.example.normpix.wallet.StoredCard
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.zip.ZipInputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Backing up one phone's app and restoring it onto another: the parts that were picked are replaced, the rest left alone, and a
 * restore can be taken back - or, if a step fails, takes itself back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppRestorerTest {
    private val now = LocalDateTime.of(2026, 10, 19, 9, 30)
    private lateinit var source: TestEnvironment
    private lateinit var target: TestEnvironment
    private lateinit var scratch: File
    private var reads = 0

    private val context: Context get() = source.context
    private val photoName = "photo-1760000000000.jpg"
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
    private val sourceFont = byteArrayOf(0, 1, 0, 0) + "the font from the backup".toByteArray()
    private val targetFont = byteArrayOf(0, 1, 0, 0) + "the font on this phone".toByteArray()

    @BeforeTest
    fun setUp() {
        source = TestEnvironment(prefsName = "backup_source")
        target = TestEnvironment(prefsName = "backup_target")
        scratch = Files.createTempDirectory("app-restorer").toFile()
        forgetFiles()
    }

    @AfterTest
    fun tearDown() {
        source.close()
        target.close()
        scratch.deleteRecursively()
    }

    /** The profile photo and font files live in the app's files, which both phones in a test share: clear them. */
    private fun forgetFiles() {
        CustomFont.delete(context)
        File(context.filesDir, "profile").deleteRecursively()
    }

    private fun reminder(title: String, id: Long? = null) = Reminder(
        id = id,
        title = title,
        category = ReminderCategory.Payments,
        dueAt = now.plusDays(2),
        repeatRule = RepeatRule.Once,
        createdAt = now,
        updatedAt = now,
    )

    /** One expense at [payee] from the account called Bank (made the first time), in the first expense category the app starts with. */
    private fun spend(env: TestEnvironment, payee: String) {
        val finance = env.container.finance
        runBlocking {
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
        }
    }

    private fun payees(env: TestEnvironment): List<String?> =
        runBlocking { env.container.finance.repository.snapshot().transactions.map { it.payee } }

    private fun titles(env: TestEnvironment): Set<String> = env.container.reminders.state.value.reminders.map { it.title }.toSet()

    /** The phone the backup is made on: some finance data, settings, two reminders, a profile with a photo, a font. */
    private fun fillSource() {
        spend(source, "Cafe")
        source.preferences.setMode(ThemeMode.Dark)
        source.preferences.setCardTiltEnabled(false)
        source.preferences.setProfile("Asha", "🙂")
        File(context.filesDir, "profile").also { it.mkdirs() }.resolve(photoName).writeBytes(jpeg)
        source.preferences.setProfilePhoto(photoName)
        CustomFont.file(context).also { it.parentFile?.mkdirs() }.writeBytes(sourceFont)
        source.preferences.useCustomFont("My font.ttf")
        runBlocking {
            source.container.reminders.addReminder(reminder("Electricity"))
            source.container.reminders.addReminder(reminder("Rent"))
        }
    }

    /** The phone it is restored onto: different in every part. */
    private fun fillTarget() {
        spend(target, "Old shop")
        target.preferences.setMode(ThemeMode.Light)
        target.preferences.setProfile("Old name", "")
        runBlocking { target.container.reminders.addReminder(reminder("Only on this phone")) }
    }

    private fun backup(cardsFile: String? = null): ByteArray = runBlocking {
        val exporter = AppBackupExporter(context, source.container)
        val gathered = exporter.gather(now, cardsFile)
        ByteArrayOutputStream().also { exporter.write(it, gathered) }.toByteArray()
    }

    private fun planOf(bytes: ByteArray): AppBackupPlan {
        val read = AppBackupArchive.read(ByteArrayInputStream(bytes), File(scratch, "read-${reads++}"))
        val parsed = AppBackupReader.parse(read.finance.json, read.finance.pictures, assertNotNull(read.app))
        return assertIs<AppBackupReader.Parsed.Ok>(parsed).plan
    }

    private fun restore(plan: AppBackupPlan, wanted: Set<BackupPart> = plan.parts, cards: List<StoredCard>? = null): AppRestorer.Restored =
        runBlocking { AppRestorer(context, target.container).restore(plan, wanted, cards) }

    private fun card(id: String, number: String) = StoredCard(
        id = id,
        bankName = "HDFC Bank",
        cardholderName = "K SAI",
        cardNumber = number,
        expiryMonth = "08",
        expiryYear = "2029",
        cvv = "123",
        kind = CardKind.Credit,
        createdAt = now,
    )

    private fun lockedCards(cards: List<StoredCard>) = CardsTransfer.encode(cards, "pw".toCharArray(), exportedAt = now, iterations = 10_000)

    private fun entryTexts(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes().toString(Charsets.ISO_8859_1))
            }
        }
    }

    @Test
    fun `what the backup was made of comes back, every part of it`() {
        fillSource()
        val bytes = backup()
        forgetFiles()
        fillTarget()
        val plan = planOf(bytes)
        assertEquals(setOf(BackupPart.Finance, BackupPart.Settings, BackupPart.Reminders, BackupPart.Profile, BackupPart.Font), plan.parts)

        val restored = restore(plan)

        assertEquals(listOf<String?>("Cafe"), payees(target))
        val state = target.preferences.state.value
        assertEquals(ThemeMode.Dark, state.mode)
        assertFalse(state.cardTiltEnabled)
        assertEquals("Asha", state.profileName)
        assertEquals(photoName, state.profilePhoto)
        assertContentEquals(jpeg, assertNotNull(ProfilePhotos(context).file(photoName)).readBytes())
        assertContentEquals(sourceFont, CustomFont.file(context).readBytes())
        assertEquals("My font.ttf", state.customFontName)
        assertEquals(InterfaceFont.Custom, state.interfaceFont)
        assertEquals(setOf("Electricity", "Rent"), titles(target))
        assertTrue(target.container.reminders.state.value.reminders.all { it.id != null })
        assertEquals(0, restored.skippedSettings)
        assertNull(restored.cards)
        assertFalse(restored.cardsFailed)
        assertTrue(restored.undo.canUndo)
    }

    @Test
    fun `only the parts that were picked are replaced`() {
        fillSource()
        val bytes = backup()
        forgetFiles()
        fillTarget()

        restore(planOf(bytes), setOf(BackupPart.Reminders))

        assertEquals(setOf("Electricity", "Rent"), titles(target))
        assertEquals(listOf<String?>("Old shop"), payees(target))
        assertEquals(ThemeMode.Light, target.preferences.state.value.mode)
        assertEquals("Old name", target.preferences.state.value.profileName)
        assertFalse(CustomFont.exists(context))
    }

    @Test
    fun `the profile and the settings are separate parts`() {
        fillSource()
        val bytes = backup()
        forgetFiles()
        fillTarget()

        restore(planOf(bytes), setOf(BackupPart.Profile))
        assertEquals("Asha", target.preferences.state.value.profileName)
        assertEquals(photoName, target.preferences.state.value.profilePhoto)
        assertEquals(ThemeMode.Light, target.preferences.state.value.mode, "the settings weren't picked")
        assertFalse(CustomFont.exists(context), "nor was the font")

        restore(planOf(bytes), setOf(BackupPart.Settings))
        assertEquals(ThemeMode.Dark, target.preferences.state.value.mode)
        assertEquals("Asha", target.preferences.state.value.profileName, "the profile stays as the last restore left it")
    }

    @Test
    fun `a part the backup does not have is not touched`() {
        // A backup of a phone with no font: restoring the font part must not take away this phone's.
        spend(source, "Cafe")
        source.preferences.setMode(ThemeMode.Dark)
        val plan = planOf(backup())
        assertFalse(BackupPart.Font in plan.parts)
        assertFalse(BackupPart.Cards in plan.parts)
        fillTarget()
        CustomFont.file(context).also { it.parentFile?.mkdirs() }.writeBytes(targetFont)

        restore(plan, BackupPart.entries.toSet())

        assertContentEquals(targetFont, CustomFont.file(context).readBytes())
        assertEquals(ThemeMode.Dark, target.preferences.state.value.mode)
    }

    @Test
    fun `the lock and the card settings of this phone are not changed by a backup`() {
        fillSource()
        val bytes = backup()
        forgetFiles()
        target.prefsStore.edit()
            .putBoolean("wallet_lock_enabled", true)
            .putBoolean("nfc_tag_reveal_enabled", true)
            .putBoolean("card_cvv_enabled", false)
            .commit()

        restore(planOf(bytes))

        assertTrue(target.prefsStore.getBoolean("wallet_lock_enabled", false))
        assertTrue(target.prefsStore.getBoolean("nfc_tag_reveal_enabled", false))
        assertFalse(target.prefsStore.getBoolean("card_cvv_enabled", true))
    }

    @Test
    fun `a font choice with no font to draw with is not taken`() {
        fillSource()
        val bytes = backup()
        forgetFiles()
        fillTarget()

        // The settings say "your font", but the font part isn't restored and this phone has none: the choice would only fall back.
        restore(planOf(bytes), setOf(BackupPart.Settings))
        assertEquals(ThemeMode.Dark, target.preferences.state.value.mode)
        assertEquals(InterfaceFont.GsFlex, target.preferences.state.value.interfaceFont)

        // With a font on this phone it is the same choice as the backup's, and is taken.
        CustomFont.file(context).also { it.parentFile?.mkdirs() }.writeBytes(targetFont)
        restore(planOf(bytes), setOf(BackupPart.Settings))
        assertEquals(InterfaceFont.Custom, target.preferences.state.value.interfaceFont)
        assertContentEquals(targetFont, CustomFont.file(context).readBytes())
    }

    @Test
    fun `taking a restore back puts everything as it was`() {
        fillSource()
        val bytes = backup()
        forgetFiles()
        fillTarget()
        val plan = planOf(bytes)

        val restored = restore(plan)
        assertEquals(listOf<String?>("Cafe"), payees(target))
        assertTrue(runBlocking { restored.undo.undo() })

        assertEquals(listOf<String?>("Old shop"), payees(target))
        val state = target.preferences.state.value
        assertEquals(ThemeMode.Light, state.mode)
        assertTrue(state.cardTiltEnabled)
        assertEquals("Old name", state.profileName)
        assertEquals("", state.profilePhoto)
        assertNull(ProfilePhotos(context).file(photoName), "the photo the restore brought is let go")
        assertFalse(CustomFont.exists(context))
        assertEquals("", state.customFontName)
        assertEquals(InterfaceFont.GsFlex, state.interfaceFont)
        assertEquals(setOf("Only on this phone"), titles(target))
    }

    @Test
    fun `the font on this phone is kept aside until the restore is final`() {
        fillSource()
        val bytes = backup()
        forgetFiles()
        fillTarget()
        CustomFont.file(context).also { it.parentFile?.mkdirs() }.writeBytes(targetFont)
        fun keptAside() = context.cacheDir.listFiles { f -> f.name.startsWith("fonts-before-") }.orEmpty().toList()

        val taken = restore(planOf(bytes))
        assertContentEquals(sourceFont, CustomFont.file(context).readBytes())
        assertEquals(1, keptAside().size, "the old font waits in the cache while the restore can still be undone")
        runBlocking { taken.undo.undo() }
        assertContentEquals(targetFont, CustomFont.file(context).readBytes())
        assertTrue(keptAside().isEmpty())

        val final = restore(planOf(bytes))
        assertEquals(1, keptAside().size)
        runBlocking { final.undo.settle() }
        assertTrue(keptAside().isEmpty(), "settled: nothing is kept")
        assertContentEquals(sourceFont, CustomFont.file(context).readBytes())
    }

    @Test
    fun `a restore cannot be undone once the finance data has changed again`() {
        fillSource()
        val bytes = backup()
        forgetFiles()
        fillTarget()

        val restored = restore(planOf(bytes))
        spend(target, "Afterwards")

        assertFalse(runBlocking { restored.undo.undo() }, "undoing would also take away the new expense")
        assertEquals(setOf("Cafe", "Afterwards"), payees(target).toSet())
        assertEquals(ThemeMode.Dark, target.preferences.state.value.mode, "and nothing else was undone either")
        assertEquals(setOf("Electricity", "Rent"), titles(target))
    }

    @Test
    fun `a restore that fails half way is taken back, so it is all or none`() {
        fillSource()
        val good = planOf(backup())
        forgetFiles()
        fillTarget()
        // Two reminders with one id cannot be stored, which is found out after the finance data has been replaced.
        val broken = AppBackupPlan(
            manifest = good.manifest,
            snapshot = good.snapshot,
            pictures = emptyMap(),
            settings = good.settings,
            reminders = listOf(reminder("A", id = 5), reminder("B", id = 5)),
            profilePhotos = emptyMap(),
            fonts = emptyMap(),
            cardsFile = null,
            skippedSettings = 0,
            skippedReminders = 0,
        )

        assertFailsWith<Exception> { restore(broken, setOf(BackupPart.Finance, BackupPart.Reminders)) }

        assertEquals(listOf<String?>("Old shop"), payees(target))
        assertEquals(setOf("Only on this phone"), titles(target))
    }

    @Test
    fun `cards are added from the locked file, once, and are never plain text in the backup`() {
        val cards = listOf(card("a", "4111111111111111"), card("b", "5500005555555559"))
        fillSource()
        val bytes = backup(lockedCards(cards))

        for ((name, text) in entryTexts(bytes)) {
            for (secret in listOf("4111111111111111", "5500005555555559", "K SAI", "HDFC Bank")) assertFalse(secret in text, "$secret in $name")
        }
        val plan = planOf(bytes)
        assertTrue(BackupPart.Cards in plan.parts)
        val opened = assertIs<CardsTransfer.Result.Cards>(CardsTransfer.decode(assertNotNull(plan.cardsFile), "pw".toCharArray()))
        assertEquals(cards, opened.cards)

        forgetFiles()
        val first = restore(plan, setOf(BackupPart.Cards), opened.cards)
        assertEquals(2, assertNotNull(first.cards).added.size)
        assertEquals(cards.map { it.id }, target.vault.cards.map { it.id })
        assertFalse(first.undo.canUndo, "the cards are only added to; there is nothing to take back")

        val second = restore(plan, setOf(BackupPart.Cards), opened.cards)
        assertEquals(0, assertNotNull(second.cards).added.size)
        assertEquals(2, second.cards?.alreadyThere)
        assertEquals(2, target.vault.cards.size, "no card twice")
        assertEquals(listOf<String?>(), payees(target), "the finance data was not part of this restore")
    }

    @Test
    fun `without the cards file there is no cards part`() {
        fillSource()
        val plan = planOf(backup())
        assertFalse(BackupPart.Cards in plan.parts)
        assertNull(plan.cardsFile)
        // Asking for the cards anyway restores nothing of them.
        forgetFiles()
        val restored = restore(plan, BackupPart.entries.toSet(), listOf(card("a", "4111111111111111")))
        assertNull(restored.cards)
        assertTrue(target.vault.cards.isEmpty())
    }

    @Test
    fun `cards follow the card settings of the phone they come to`() {
        val cards = listOf(card("a", "4111111111111111"))
        target.container.cards.followSettings(target.preferences)

        // Save CVV off: the cards arrive without one.
        target.preferences.setCardCvvEnabled(false)
        val noCvv = restore(planOf(backup(lockedCards(cards))), setOf(BackupPart.Cards), cards)
        assertEquals(1, assertNotNull(noCvv.cards).added.size)
        assertEquals(listOf(""), target.vault.cards.map { it.cvv })

        // Store cards off: nothing is added, and it is said so.
        runBlocking { target.container.cards.deleteAll() }
        target.preferences.setWalletCardsEnabled(false)
        val refused = restore(planOf(backup(lockedCards(cards))), setOf(BackupPart.Cards), cards)
        assertTrue(assertNotNull(refused.cards).refused)
        assertTrue(target.vault.cards.isEmpty())
    }

    @Test
    fun `an old full backup, a zip with only the finance data, still restores as it did`() {
        spend(source, "Cafe")
        val snapshot = runBlocking { source.container.finance.repository.snapshot() }
        val out = ByteArrayOutputStream()
        FinanceBackupArchive.write(out, FinanceBackupCodec.encode(snapshot, exportedAt = now), emptyList()) { null }

        val read = AppBackupArchive.read(ByteArrayInputStream(out.toByteArray()), File(scratch, "old"))
        assertNull(read.app, "no app part: the finance restore applies, as before")
        val decoded = FinanceBackupCodec.decode(assertNotNull(read.finance.json))
        runBlocking { target.container.finance.restore(assertNotNull(decoded.snapshot)) }

        assertEquals(listOf<String?>("Cafe"), payees(target))
    }

    @Test
    fun `an old backup, a json file, still restores as it did`() {
        spend(source, "Cafe")
        val json = FinanceBackupCodec.encode(runBlocking { source.container.finance.repository.snapshot() }, exportedAt = now)
        runBlocking { target.container.finance.restore(assertNotNull(FinanceBackupCodec.decode(json).snapshot)) }
        assertEquals(listOf<String?>("Cafe"), payees(target))
    }
}
