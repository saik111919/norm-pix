package com.example.normpix.backup

import com.example.normpix.finance.data.FinanceBackupCodec
import com.example.normpix.finance.data.FinanceSnapshot
import com.example.normpix.reminders.Reminder
import com.example.normpix.reminders.ReminderBackupCodec
import com.example.normpix.reminders.ReminderCategory
import com.example.normpix.reminders.RepeatRule
import com.example.normpix.settings.PrefValue
import com.example.normpix.wallet.CardsTransfer
import java.io.File
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An app backup is checked whole before anything is shown or changed: what it can restore, or why it can't be used. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppBackupReaderTest {
    private val now = LocalDateTime.of(2026, 10, 19, 9, 30)
    private val manifest = AppBackupManifest("1.4.0", now.toString(), listOf("finance", "settings"))

    private val goodFinance = FinanceBackupCodec.encode(
        FinanceSnapshot(
            currencyCode = "INR",
            accounts = emptyList(),
            categories = emptyList(),
            transactions = emptyList(),
            budgets = emptyList(),
            recurringRules = emptyList(),
        ),
        exportedAt = now,
    )
    private val goodSettings = SettingsBackupCodec.encode(mapOf("theme_mode" to PrefValue.StringPref("Dark")))
    private val goodReminders = ReminderBackupCodec.encode(
        listOf(
            Reminder(
                id = 1,
                title = "Rent",
                category = ReminderCategory.Payments,
                dueAt = now.plusDays(3),
                repeatRule = RepeatRule.Once,
                createdAt = now,
                updatedAt = now,
            ),
        ),
    )
    private val goodCards = CardsTransfer.encode(emptyList(), "password".toCharArray(), exportedAt = now, iterations = 10_000)

    private fun appPart(
        settings: String? = null,
        reminders: String? = null,
        cards: String? = null,
        photos: Map<String, File> = emptyMap(),
        fonts: Map<String, File> = emptyMap(),
    ) = AppBackupArchive.App(manifest, settings, reminders, cards, photos, fonts)

    private fun parse(finance: String? = goodFinance, app: AppBackupArchive.App) = AppBackupReader.parse(finance, emptyMap(), app)

    private fun invalid(finance: String? = goodFinance, app: AppBackupArchive.App): List<String> =
        assertIs<AppBackupReader.Parsed.Invalid>(parse(finance, app)).errors

    private fun plan(finance: String? = goodFinance, app: AppBackupArchive.App): AppBackupPlan =
        assertIs<AppBackupReader.Parsed.Ok>(parse(finance, app)).plan

    @Test
    fun `the fixtures themselves are whole`() {
        assertNotNull(FinanceBackupCodec.decode(goodFinance).snapshot)
        assertNotNull(SettingsBackupCodec.decode(goodSettings))
        assertNotNull(ReminderBackupCodec.decode(goodReminders))
        assertTrue(CardsTransfer.isCardFile(goodCards))
    }

    @Test
    fun `a backup with every part offers every part`() {
        val read = plan(
            app = appPart(
                SettingsBackupCodec.encode(mapOf("theme_mode" to PrefValue.StringPref("Dark"), "profile_name" to PrefValue.StringPref("Asha"))),
                goodReminders,
                goodCards,
                photos = mapOf("photo-1.jpg" to File("photo-1.jpg")),
                fonts = mapOf("custom-interface-font.ttf" to File("custom-interface-font.ttf")),
            ),
        )
        assertEquals(
            setOf(BackupPart.Finance, BackupPart.Settings, BackupPart.Reminders, BackupPart.Profile, BackupPart.Font, BackupPart.Cards),
            read.parts,
        )
        assertNotNull(read.snapshot)
        assertEquals("Dark", (read.settings?.get("theme_mode") as? PrefValue.StringPref)?.value)
        assertEquals(listOf("Rent"), read.reminders?.map { it.title })
        assertEquals(goodCards, read.cardsFile, "still locked: the password comes later")
        assertEquals(0, read.skippedSettings)
        assertEquals(0, read.skippedReminders)
    }

    @Test
    fun `parts the backup does not have are not offered`() {
        val onlyReminders = plan(finance = null, app = appPart(reminders = goodReminders))
        assertEquals(setOf(BackupPart.Reminders), onlyReminders.parts)
        assertNull(onlyReminders.snapshot)
        assertNull(onlyReminders.settings)
        assertNull(onlyReminders.cardsFile)

        assertTrue(plan(finance = null, app = appPart()).parts.isEmpty(), "a manifest and nothing else restores nothing")
    }

    @Test
    fun `the profile is offered when its name, emoji or photo is there`() {
        fun profileOffered(settings: String?, photos: Map<String, File> = emptyMap()) =
            BackupPart.Profile in plan(app = appPart(settings = settings, photos = photos)).parts

        val named = SettingsBackupCodec.encode(mapOf("profile_name" to PrefValue.StringPref("Asha")))
        val emoji = SettingsBackupCodec.encode(mapOf("profile_emoji" to PrefValue.StringPref("x")))
        val aPhoto = mapOf("photo-1.jpg" to File("photo-1.jpg"))

        assertTrue(profileOffered(named))
        assertTrue(profileOffered(emoji))
        assertTrue(profileOffered(goodSettings, aPhoto))
        assertFalse(profileOffered(goodSettings), "a theme alone is not a profile")
        assertFalse(profileOffered(null, aPhoto), "a photo with no settings to name it is not restorable")
    }

    @Test
    fun `the font is offered only with its file, and the settings that name it`() {
        val font = mapOf("custom-interface-font.ttf" to File("custom-interface-font.ttf"))
        assertTrue(BackupPart.Font in plan(app = appPart(settings = goodSettings, fonts = font)).parts)
        assertFalse(BackupPart.Font in plan(app = appPart(settings = goodSettings)).parts)
        assertFalse(BackupPart.Font in plan(app = appPart(settings = null, fonts = font)).parts)
    }

    @Test
    fun `entries that could not be read are counted, and the rest is offered`() {
        val settings = """{"ok": {"type": "int", "value": 5}, "bad": {"type": "int", "value": "5"}}"""
        val reminders = """{"reminders": [{"title": "Fine", "dueAt": "2026-10-04T09:00:00"}, {"title": "No due time"}]}"""
        val read = plan(app = appPart(settings = settings, reminders = reminders))
        assertEquals(1, read.skippedSettings)
        assertEquals(setOf("ok"), read.settings?.keys)
        assertEquals(1, read.skippedReminders)
        assertEquals(listOf("Fine"), read.reminders?.map { it.title })
    }

    @Test
    fun `a damaged part makes the whole backup unusable, and says which`() {
        assertEquals(
            listOf("The settings in this backup are damaged."),
            invalid(app = appPart(settings = "not json")),
        )
        assertEquals(
            listOf("The reminders in this backup are damaged."),
            invalid(app = appPart(reminders = """{"other": []}""")),
        )
        assertEquals(
            listOf("The saved cards in this backup are damaged."),
            invalid(app = appPart(cards = """{"format": "something-else"}""")),
        )
        assertTrue(invalid(finance = "not json", app = appPart(settings = goodSettings)).isNotEmpty())
        assertTrue(invalid(finance = """{"format": "normpix-finance-backup"}""", app = appPart()).isNotEmpty())
    }

    @Test
    fun `every damaged part is reported, not just the first`() {
        val errors = invalid(
            finance = "not json",
            app = appPart(settings = "not json", reminders = "not json", cards = "not json"),
        )
        assertTrue("The settings in this backup are damaged." in errors)
        assertTrue("The reminders in this backup are damaged." in errors)
        assertTrue("The saved cards in this backup are damaged." in errors)
        assertTrue(errors.size >= 4, "and the finance data's own problem: $errors")
    }
}
