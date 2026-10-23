package com.example.normpix.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.backup.AppBackupManifest
import com.example.normpix.backup.AppBackupPlan
import com.example.normpix.backup.BackupPart
import com.example.normpix.finance.data.FinanceSnapshot
import com.example.normpix.reminders.Reminder
import com.example.normpix.reminders.ReminderCategory
import com.example.normpix.reminders.RepeatRule
import com.example.normpix.settings.ui.RestoreChoiceSheet
import com.example.normpix.settings.ui.RestoreOffer
import com.example.normpix.testing.TestEnvironment
import java.io.File
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sheet that asks which parts of an app backup to restore: a switch for each part the backup has, all on to begin with, and
 * nothing offered that the backup or this phone cannot do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class RestoreChoiceSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = LocalDateTime.of(2026, 10, 19, 9, 30)
    private lateinit var env: TestEnvironment
    private var chosen: Set<BackupPart>? = null

    private val everything = setOf(
        BackupPart.Finance,
        BackupPart.Settings,
        BackupPart.Reminders,
        BackupPart.Profile,
        BackupPart.Font,
        BackupPart.Cards,
    )

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("restore_choice_sheet")
    }

    @After
    fun tearDown() = env.close()

    private fun reminder(title: String) = Reminder(
        title = title,
        category = ReminderCategory.Payments,
        dueAt = now.plusDays(1),
        repeatRule = RepeatRule.Once,
        createdAt = now,
        updatedAt = now,
    )

    private fun plan(
        finance: Boolean = true,
        settings: Boolean = true,
        reminders: Boolean = true,
        photo: Boolean = true,
        font: Boolean = true,
        cards: Boolean = true,
        manifest: AppBackupManifest = AppBackupManifest("1.4.0", "2026-10-05T14:30:00", emptyList()),
    ) = AppBackupPlan(
        manifest = manifest,
        snapshot = if (finance) {
            FinanceSnapshot(
                currencyCode = "INR",
                accounts = emptyList(),
                categories = emptyList(),
                transactions = emptyList(),
                budgets = emptyList(),
                recurringRules = emptyList(),
            )
        } else {
            null
        },
        pictures = emptyMap(),
        settings = if (settings) mapOf("theme_mode" to PrefValue.StringPref("Dark")) else null,
        reminders = if (reminders) listOf(reminder("Rent"), reminder("Gym")) else null,
        profilePhotos = if (photo) mapOf("photo-1.jpg" to File("photo-1.jpg")) else emptyMap(),
        fonts = if (font) mapOf("custom-interface-font.ttf" to File("custom-interface-font.ttf")) else emptyMap(),
        cardsFile = if (cards) "{}" else null,
        skippedSettings = 0,
        skippedReminders = 0,
    )

    private fun show(plan: AppBackupPlan, cardsOn: Boolean = true, cvvOn: Boolean = true, sampleData: Boolean = false) {
        val offer = RestoreOffer(plan, financeNow = "3 transactions", remindersNow = 4, cardsOn = cardsOn, cvvOn = cvvOn, sampleData = sampleData)
        compose.setContent { env.Content { RestoreChoiceSheet(offer) { chosen = it } } }
        compose.waitForIdle()
    }

    private fun restore() {
        compose.onNodeWithText("Restore").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `every part the backup has is offered, and all of them are on to begin with`() {
        show(plan())

        for (title in listOf("Finance data", "Settings", "Reminders", "Profile and font", "Saved cards")) {
            compose.onNodeWithText(title).assertIsDisplayed()
        }
        compose.onNodeWithText("Replaces 3 transactions.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("replace the 4 here now", substring = true).assertIsDisplayed()
        restore()
        assertEquals(everything, chosen)
    }

    @Test
    fun `a part turned off is left out of what is restored`() {
        show(plan())

        compose.onNodeWithText("Reminders").performClick()
        compose.onNodeWithText("Finance data").performClick()
        restore()

        assertEquals(everything - BackupPart.Reminders - BackupPart.Finance, chosen)
    }

    @Test
    fun `the profile and the font are one switch`() {
        show(plan())

        compose.onNodeWithText("Profile and font").performClick()
        restore()

        assertEquals(everything - BackupPart.Profile - BackupPart.Font, chosen)
    }

    @Test
    fun `a part the backup does not have is not shown`() {
        show(plan(finance = false, photo = false, font = false, cards = false))

        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Reminders").assertIsDisplayed()
        for (absent in listOf("Finance data", "Profile and font", "Profile", "Font", "Saved cards")) {
            compose.onAllNodesWithText(absent).assertCountEquals(0)
        }
        restore()
        assertEquals(setOf(BackupPart.Settings, BackupPart.Reminders), chosen)
    }

    @Test
    fun `a profile with no font is named for what it is`() {
        show(plan(font = false))
        compose.onNodeWithText("Profile").assertIsDisplayed()
        compose.onAllNodesWithText("Profile and font").assertCountEquals(0)
    }

    @Test
    fun `a font with no profile is named for what it is`() {
        // A font file with settings that name no profile and no photo with it.
        show(plan(photo = false))
        compose.onNodeWithText("Font").assertIsDisplayed()
        compose.onAllNodesWithText("Profile and font").assertCountEquals(0)
        restore()
        assertEquals(setOf(BackupPart.Finance, BackupPart.Settings, BackupPart.Reminders, BackupPart.Font, BackupPart.Cards), chosen)
    }

    @Test
    fun `saved cards are left out while Store cards is off, and the row says so`() {
        show(plan(), cardsOn = false)

        compose.onNodeWithText("Saved cards").assertIsNotEnabled()
        compose.onNodeWithText("Store cards is off on this phone", substring = true).assertIsDisplayed()
        // Tapping it does nothing.
        compose.onNodeWithText("Saved cards").performClick()
        restore()
        assertEquals(everything - BackupPart.Cards, chosen)
    }

    @Test
    fun `with Save CVV off the row says the cards arrive without one`() {
        show(plan(), cvvOn = false)

        compose.onNodeWithText("Saved cards").assertIsEnabled()
        compose.onNodeWithText("Save CVV is off on this phone", substring = true).assertIsDisplayed()
        compose.onNodeWithText("arrive without a CVV", substring = true).assertIsDisplayed()
    }

    @Test
    fun `with Save CVV on there is no word about it`() {
        show(plan())

        compose.onAllNodesWithText("Save CVV is off", substring = true).assertCountEquals(0)
        compose.onNodeWithText("You'll be asked for the password", substring = true).assertIsDisplayed()
    }

    @Test
    fun `restore cannot be pressed with nothing left on`() {
        show(plan())
        compose.onNodeWithText("Restore").assertIsEnabled()

        for (title in listOf("Finance data", "Settings", "Reminders", "Profile and font", "Saved cards")) {
            compose.onNodeWithText(title).performClick()
        }
        compose.waitForIdle()

        compose.onNodeWithText("Restore").assertIsNotEnabled()
        assertNull(chosen, "nothing was pressed")
    }

    @Test
    fun `sample data is mentioned only when it is on`() {
        show(plan(), sampleData = true)
        compose.onNodeWithText("Sample data is on", substring = true).assertIsDisplayed()
    }

    @Test
    fun `no word of sample data when it is off`() {
        show(plan())
        compose.onAllNodesWithText("Sample data is on", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the sheet says when the backup was made and by what`() {
        show(plan())
        compose.onNodeWithText("Made 5 Oct 2026, 14:30, by NormPix 1.4.0").assertIsDisplayed()
    }

    @Test
    fun `what the sheet says about the maker is as much as the backup says`() {
        fun madeBy(manifest: AppBackupManifest) = RestoreOffer(plan(manifest = manifest), "", 0, cardsOn = true, cvvOn = true, sampleData = false).madeBy

        assertEquals("Made 5 Oct 2026, 14:30, by NormPix 1.4.0", madeBy(AppBackupManifest("1.4.0", "2026-10-05T14:30:00", emptyList())))
        assertEquals("Made 5 Oct 2026, 14:30", madeBy(AppBackupManifest("", "2026-10-05T14:30:00", emptyList())))
        assertEquals("Made by NormPix 1.4.0", madeBy(AppBackupManifest("1.4.0", null, emptyList())))
        assertEquals("Made by NormPix 1.4.0", madeBy(AppBackupManifest("1.4.0", "yesterday", emptyList())))
        assertEquals("What this backup holds", madeBy(AppBackupManifest("", null, emptyList())))
    }
}
