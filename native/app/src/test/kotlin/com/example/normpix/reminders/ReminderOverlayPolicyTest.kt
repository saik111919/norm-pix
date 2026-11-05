package com.example.normpix.reminders

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When the floating card may appear over another app - and, as important, every reason it may not. */
class ReminderOverlayPolicyTest {
    /** Everything in order for the card to show. */
    private fun ready(
        featureOn: Boolean = true,
        canDrawOverlays: Boolean = true,
        screenOn: Boolean = true,
        locked: Boolean = false,
        appInFront: Boolean = false,
    ) = OverlayContext(featureOn, canDrawOverlays, screenOn, locked, appInFront)

    private fun skip(c: OverlayContext) = (decideOverlay(c) as? OverlayDecision.Skip)?.reason

    @Test
    fun showsOverAnOrdinaryAppOnAnUnlockedPhone() {
        assertEquals(OverlayDecision.Show, decideOverlay(ready()))
    }

    @Test
    fun theDecisionKnowsNothingAboutWhichOtherAppIsOpen() {
        // The rule is made of what the phone says about itself, never of what the person is doing in another app: no field of it
        // names an app, so there is nothing a Usage access grant could be needed for.
        val fields = OverlayContext::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") }
        assertEquals(listOf("featureOn", "canDrawOverlays", "screenOn", "locked", "appInFront"), fields)
    }

    @Test
    fun staysOffUntilTheUserTurnedItOn_whateverElseAllowsIt() {
        assertEquals(OverlaySkip.Off, skip(ready(featureOn = false)))
    }

    @Test
    fun needsAndroidsOwnPermission() {
        assertEquals(OverlaySkip.NoPermission, skip(ready(canDrawOverlays = false)))
    }

    @Test
    fun neverOnALockedPhoneOrOneWithItsScreenOff() {
        assertEquals(OverlaySkip.ScreenOff, skip(ready(screenOn = false)))
        assertEquals(OverlaySkip.Locked, skip(ready(locked = true)))
    }

    @Test
    fun neverOverNormPixItself() {
        assertEquals(OverlaySkip.InApp, skip(ready(appInFront = true)))
    }

    @Test
    fun theFirstReasonIsTheOneGiven() {
        // Off beats a missing permission; a locked phone beats NormPix being in front.
        assertEquals(OverlaySkip.Off, skip(ready(featureOn = false, canDrawOverlays = false)))
        assertEquals(OverlaySkip.Locked, skip(ready(locked = true, appInFront = true)))
    }
}

/** The manifest no longer asks for Usage access, and the leftover list of excluded apps does not stay on the phone. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33])
class NoUsageAccessTest {
    @Test
    fun theManifestDoesNotDeclareUsageAccessOrAskToListEveryApp() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        // Only the comments may speak of it; no <uses-permission> or <queries> entry may.
        val live = manifest.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse("PACKAGE_USAGE_STATS" in live)
        assertFalse("CATEGORY_LAUNCHER" in live, "no query for every app with a launcher icon")
    }

    @Test
    fun theRemovedExcludedAppsListIsDeletedFromAnExistingPhone() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.getSharedPreferences("no_usage_access_test", android.content.Context.MODE_PRIVATE)
        file.edit().clear().putStringSet("reminder_overlay_excluded", setOf("com.somebank.mobile")).putBoolean("reminder_overlay_enabled", true).commit()

        val preferences = com.example.normpix.settings.AppPreferences(file)

        assertFalse(file.contains("reminder_overlay_excluded"), "the list of package names is gone")
        assertTrue(preferences.state.value.reminderOverlayEnabled, "what else the user chose is untouched")
    }
}

/** What a reminder made from a repeating payment is: when it rings, and how it repeats. */
class ReminderFromPaymentTest {
    private val now = LocalDateTime.of(2026, 11, 4, 10, 0)

    @Test
    fun aDayWithNoTimeIsRemindedAtNineNotAtMidnight() {
        assertEquals(
            LocalDateTime.of(2026, 12, 1, 9, 0),
            ReminderDrafts.morningIfDateOnly(LocalDateTime.of(LocalDate.of(2026, 12, 1), LocalTime.MIDNIGHT)),
        )
        // A payment with a time of its own keeps it.
        val evening = LocalDateTime.of(2026, 12, 1, 18, 30)
        assertEquals(evening, ReminderDrafts.morningIfDateOnly(evening))
    }

    private fun rule(frequency: com.example.normpix.finance.domain.RecurrenceFrequency, interval: Int) =
        com.example.normpix.finance.domain.RecurringRule(
            type = com.example.normpix.finance.domain.TransactionType.Expense,
            amount = 100_00,
            accountId = 1,
            frequency = frequency,
            interval = interval,
            startDate = LocalDateTime.of(2026, 12, 15, 0, 0),
            nextDate = LocalDateTime.of(2026, 12, 15, 0, 0),
            createdAt = now,
            updatedAt = now,
        )

    @Test
    fun aMonthlyPaymentsReminderRingsInTheMorningOfItsDay() {
        val reminder = ReminderDrafts.aboutRecurring(rule(com.example.normpix.finance.domain.RecurrenceFrequency.Monthly, 1), "Rent", "Rent", now)
        assertEquals(LocalDateTime.of(2026, 12, 15, 9, 0), reminder.dueAt)
        assertEquals(RepeatKind.Monthly, reminder.repeatRule.kind)
    }

    @Test
    fun aYearlyOrEveryOtherMonthPaymentStillRepeatsItsReminder() {
        val yearly = ReminderDrafts.reminderRepeatFor(rule(com.example.normpix.finance.domain.RecurrenceFrequency.Yearly, 1))
        assertEquals(RepeatKind.Months, yearly.kind)
        assertEquals(12, yearly.intervalMonths)
        val everyOther = ReminderDrafts.reminderRepeatFor(rule(com.example.normpix.finance.domain.RecurrenceFrequency.Monthly, 2))
        assertEquals(2, everyOther.intervalMonths)
        assertEquals(15, everyOther.dayOfMonth)
        // ... and it really does come round again.
        assertEquals(LocalDateTime.of(2027, 12, 15, 9, 0), yearly.nextOccurrence(LocalDateTime.of(2026, 12, 15, 9, 0)))
    }
}
