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
    private val bank = "com.somebank.mobile"
    private val chat = "com.chat.app"

    /** Everything in order for the card to show. */
    private fun ready(
        featureOn: Boolean = true,
        canDrawOverlays: Boolean = true,
        screenOn: Boolean = true,
        locked: Boolean = false,
        appInFront: Boolean = false,
        usageAccess: Boolean = true,
        foregroundPackage: String? = chat,
        excluded: Set<String> = setOf(bank),
    ) = OverlayContext(featureOn, canDrawOverlays, screenOn, locked, appInFront, usageAccess, foregroundPackage, excluded)

    private fun skip(c: OverlayContext) = (decideOverlay(c) as? OverlayDecision.Skip)?.reason

    @Test
    fun showsOverAnOrdinaryAppOnAnUnlockedPhone() {
        assertEquals(OverlayDecision.Show, decideOverlay(ready()))
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
    fun neverOverAnExcludedApp() {
        assertEquals(OverlaySkip.ExcludedApp, skip(ready(foregroundPackage = bank)))
    }

    @Test
    fun withAppsExcludedItDoesNotGuessWhenItCannotTellWhatIsInFront() {
        assertEquals(OverlaySkip.NeedsUsageAccess, skip(ready(usageAccess = false)))
        assertEquals(OverlaySkip.UnknownApp, skip(ready(foregroundPackage = null)))
    }

    @Test
    fun withNothingExcludedItNeedsNoUsageAccessAtAll() {
        val none = ready(excluded = emptySet(), usageAccess = false, foregroundPackage = null)
        assertEquals(OverlayDecision.Show, decideOverlay(none))
    }

    @Test
    fun theFirstReasonIsTheOneGiven() {
        // Off beats a missing permission; a locked phone beats an excluded app.
        assertEquals(OverlaySkip.Off, skip(ready(featureOn = false, canDrawOverlays = false)))
        assertEquals(OverlaySkip.Locked, skip(ready(locked = true, foregroundPackage = bank)))
    }
}

class ExcludedAppsTest {
    private fun app(label: String, pkg: String) = InstalledApp(pkg, label)

    @Test
    fun banksPaymentAppsAndPasswordManagersAreSuggested() {
        val apps = listOf(
            app("HDFC Bank MobileBanking", "com.snapwork.hdfc"),
            app("Google Pay", "com.google.android.apps.nbu.paisa.user"),
            app("PhonePe", "com.phonepe.app"),
            app("Paytm", "net.one97.paytm"),
            app("Bitwarden", "com.x8bit.bitwarden"),
            app("Authenticator", "com.google.android.apps.authenticator2"),
            app("Zerodha Kite", "com.zerodha.kite3"),
            app("Samsung Pay", "com.samsung.android.spay"),
        )
        assertEquals(apps.map { it.packageName }.toSet(), ExcludedApps.suggested(apps))
    }

    @Test
    fun everydayAppsAreLeftAlone() {
        val apps = listOf(
            app("Chrome", "com.android.chrome"),
            app("WhatsApp", "com.whatsapp"),
            app("Camera", "com.android.camera"),
            app("Spay Clean", "com.example.cleaner"),
            app("Incredible Hulk Game", "com.games.hulk"),
            app("Maps", "com.google.android.apps.maps"),
        )
        assertTrue(ExcludedApps.suggested(apps).isEmpty(), "none of these is a banking app")
    }

    @Test
    fun aShortWordCountsOnlyAsAWholeWord() {
        assertTrue(ExcludedApps.isSensitive(app("Pay", "x.y")))
        assertTrue(ExcludedApps.isSensitive(app("CRED", "x.y")))
        assertFalse(ExcludedApps.isSensitive(app("Payload Viewer", "x.y")))
        assertFalse(ExcludedApps.isSensitive(app("Credulous", "x.y")))
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
