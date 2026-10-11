package com.example.normpix.wallet.expiry

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardExpiryTest {
    private val nov26 = YearMonth.of(2026, 11)
    private fun record(expiry: YearMonth = nov26, completedFor: YearMonth? = null, remindedAt: Long? = null) =
        CardExpiryRecord("card-1", "HDFC Bank", "4242", expiry, completedFor, remindedAt)

    @Test
    fun theFormsMonthAndYearAreRead() {
        assertEquals(YearMonth.of(2027, 7), parseExpiry("07", "27"))
        assertEquals(YearMonth.of(2027, 7), parseExpiry("7", "2027"))
        assertEquals(YearMonth.of(2030, 12), parseExpiry(" 12 ", " 30 "))
    }

    @Test
    fun placeholdersAndNonsenseAreNotExpiries() {
        assertNull(parseExpiry("MM", "YY"))
        assertNull(parseExpiry("00", "27"))
        assertNull(parseExpiry("13", "27"))
        assertNull(parseExpiry("07", ""))
        assertNull(parseExpiry("", "27"))
        assertNull(parseExpiry("-1", "27"))
    }

    @Test
    fun alertsStartOnTheFirstOfTheMonthBeforeTheExpiryMonth() {
        assertEquals(LocalDate.of(2026, 10, 1), nov26.alertStart)
        assertEquals(LocalDate.of(2026, 12, 1), YearMonth.of(2027, 1).alertStart, "across a year boundary")
        assertEquals(LocalDate.of(2026, 11, 30), nov26.lastValidDay, "the card is good through the last day of its month")
    }

    @Test
    fun theCycleBeginsExactlyAtTheWindowAndNotADayBefore() {
        val r = record()
        assertEquals(CardExpiryPhase.Normal, r.phaseOn(LocalDate.of(2026, 9, 30)))
        assertEquals(CardExpiryPhase.ExpiringSoon, r.phaseOn(LocalDate.of(2026, 10, 1)))
        assertEquals(CardExpiryPhase.ExpiringSoon, r.phaseOn(LocalDate.of(2026, 11, 30)))
        assertFalse(r.hasExpiredOn(LocalDate.of(2026, 11, 30)), "still valid on its last day")
        assertTrue(r.hasExpiredOn(LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun anAlreadyExpiredCardIsInItsWindowAndStaysSoUntilDone() {
        val r = record(expiry = YearMonth.of(2025, 3))
        val state = r.stateOn(LocalDate.of(2026, 10, 6))
        assertEquals(CardExpiryPhase.ExpiringSoon, state.phase)
        assertTrue(state.expired)
        assertTrue(state.needsAttention)
    }

    @Test
    fun deliveredRemindersMakeItActiveAndDoneMakesItCompleted() {
        val today = LocalDate.of(2026, 10, 20)
        assertEquals(CardExpiryPhase.ReminderActive, record(remindedAt = 1L).phaseOn(today))
        assertTrue(record(remindedAt = 1L).stateOn(today).needsAttention)

        val done = record(completedFor = nov26, remindedAt = 1L)
        assertEquals(CardExpiryPhase.Completed, done.phaseOn(today))
        assertEquals(CardExpiryPhase.Completed, done.phaseOn(LocalDate.of(2027, 5, 1)), "stays done after the expiry too")
        assertFalse(done.stateOn(today).needsAttention)
    }

    @Test
    fun doneBelongsToTheExpiryItWasMarkedFor() {
        // Renewed with a new expiry: the old "done" no longer applies.
        val renewed = record(expiry = YearMonth.of(2029, 11), completedFor = nov26)
        assertFalse(renewed.completed)
        assertEquals(CardExpiryPhase.Normal, renewed.phaseOn(LocalDate.of(2026, 10, 20)))
    }

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    @Test
    fun theFirstReminderIsTheMorningTheWindowOpens() {
        assertEquals(at(2026, 10, 1, 9), record().nextReminder(at(2026, 6, 15, 14)))
    }

    @Test
    fun insideTheWindowTheNextReminderIsTheNextNineAm() {
        assertEquals(at(2026, 10, 20, 9), record().nextReminder(at(2026, 10, 20, 7, 30)), "this morning, still to come")
        assertEquals(at(2026, 10, 21, 9), record().nextReminder(at(2026, 10, 20, 9, 0)), "9:00 sharp has passed")
        assertEquals(at(2026, 10, 21, 9), record().nextReminder(at(2026, 10, 20, 15)))
    }

    @Test
    fun remindersKeepComingAfterTheExpiryUntilDone() {
        assertEquals(at(2027, 3, 5, 9), record().nextReminder(at(2027, 3, 4, 22)))
    }

    @Test
    fun aCompletedCardHasNoNextReminder() {
        assertNull(record(completedFor = nov26).nextReminder(at(2026, 10, 20, 7)))
    }

    @Test
    fun theReminderTimeIsInTheUsersZone() {
        val utc = ZonedDateTime.of(2026, 10, 20, 2, 0, 0, 0, ZoneId.of("UTC"))
        assertEquals(LocalTime.of(9, 0), record().nextReminder(utc)!!.toLocalTime())
        assertEquals(ZoneId.of("UTC"), record().nextReminder(utc)!!.zone)
    }
}
