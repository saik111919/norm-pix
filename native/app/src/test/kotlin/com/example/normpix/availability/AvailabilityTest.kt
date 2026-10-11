package com.example.normpix.availability

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvailabilityTest {
    @BeforeTest
    fun usEnglish() = Locale.setDefault(Locale.US)

    // 2026-10-05 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    private val evenings = AvailabilityRule(
        AvailabilityMode.Scheduled,
        WeeklySchedule(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), LocalTime.of(18, 0), LocalTime.of(20, 0)),
    )

    @Test
    fun alwaysAndOffIgnoreTheClock() {
        assertEquals(Availability.Open(null), AvailabilityRule(AvailabilityMode.Always).availabilityAt(at(5, 3)))
        assertEquals(Availability.TurnedOff, AvailabilityRule(AvailabilityMode.Off).availabilityAt(at(5, 19)))
    }

    @Test
    fun insideTheWindowIsOpenUntilItCloses() {
        val open = evenings.availabilityAt(at(5, 18, 30))
        assertEquals(Availability.Open(at(5, 20)), open)
        assertEquals("Available now until 8:00 PM.", open.describe(at(5, 18, 30)))
    }

    @Test
    fun windowStartIsInclusiveAndEndIsExclusive() {
        assertTrue(evenings.availabilityAt(at(5, 18)).isOpen)
        assertFalse(evenings.availabilityAt(at(5, 20)).isOpen)
        assertFalse(evenings.availabilityAt(at(5, 17, 59)).isOpen)
    }

    @Test
    fun beforeTheWindowTodayReportsTodaysWindow() {
        val waiting = assertIs<Availability.Waiting>(evenings.availabilityAt(at(5, 9)))
        assertEquals(at(5, 18), waiting.opensAt)
        assertEquals("Available today from 6:00 PM to 8:00 PM.", waiting.describe(at(5, 9)))
    }

    @Test
    fun afterTheWindowPointsAtTheNextDay() {
        // Monday evening is over; Tuesday is not a window day, so the next one is Wednesday.
        val waiting = assertIs<Availability.Waiting>(evenings.availabilityAt(at(5, 21)))
        assertEquals(at(7, 18), waiting.opensAt)
        assertEquals("Available Wednesday from 6:00 PM to 8:00 PM.", waiting.describe(at(5, 21)))
        // From Tuesday it is "tomorrow".
        assertEquals("Available tomorrow from 6:00 PM to 8:00 PM.", evenings.availabilityAt(at(6, 12)).describe(at(6, 12)))
    }

    @Test
    fun aSingleDayWrapsAroundTheWeek() {
        val mondays = AvailabilityRule(AvailabilityMode.Scheduled, WeeklySchedule(setOf(DayOfWeek.MONDAY), LocalTime.of(9, 0), LocalTime.of(10, 0)))
        val waiting = assertIs<Availability.Waiting>(mondays.availabilityAt(at(5, 11)))
        assertEquals(at(12, 9), waiting.opensAt)
    }

    @Test
    fun overnightWindowStaysOpenPastMidnight() {
        val night = AvailabilityRule(
            AvailabilityMode.Scheduled,
            WeeklySchedule(setOf(DayOfWeek.FRIDAY), LocalTime.of(22, 0), LocalTime.of(6, 0)),
        )
        // Friday 2026-10-09 22:30 -> still open Saturday 02:00, closed Saturday 06:00.
        assertEquals(Availability.Open(at(10, 6)), night.availabilityAt(at(9, 22, 30)))
        assertEquals(Availability.Open(at(10, 6)), night.availabilityAt(at(10, 2)))
        assertFalse(night.availabilityAt(at(10, 6)).isOpen)
        // Saturday night is not a window day.
        assertFalse(night.availabilityAt(at(10, 23)).isOpen)
        assertEquals("Available now until 6:00 AM tomorrow.", night.availabilityAt(at(9, 23)).describe(at(9, 23)))
    }

    @Test
    fun equalTimesMeanAllDay() {
        val allDay = AvailabilityRule(AvailabilityMode.Scheduled, WeeklySchedule(setOf(DayOfWeek.TUESDAY), LocalTime.MIDNIGHT, LocalTime.MIDNIGHT))
        assertEquals(Availability.Open(at(7, 0)), allDay.availabilityAt(at(6, 13)))
        assertFalse(allDay.availabilityAt(at(5, 13)).isOpen)
    }

    @Test
    fun everyDayAllDayNeverCloses() {
        val always = AvailabilityRule(AvailabilityMode.Scheduled, WeeklySchedule(WeeklySchedule.EVERY_DAY, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT))
        assertEquals(Availability.Open(null), always.availabilityAt(at(5, 13)))
    }

    @Test
    fun noDaysCanNeverOpen() {
        val none = AvailabilityRule(AvailabilityMode.Scheduled, WeeklySchedule(emptySet()))
        assertEquals(Availability.NoDays, none.availabilityAt(at(5, 19)))
        assertEquals("The schedule has no days selected.", none.availabilityAt(at(5, 19)).describe(at(5, 19)))
    }

    @Test
    fun nextChangeTellsAScreenWhenToLookAgain() {
        assertEquals(30L, evenings.availabilityAt(at(5, 19, 30)).nextChangeAfter(at(5, 19, 30))?.toMinutes())
        assertEquals(60L, evenings.availabilityAt(at(5, 17)).nextChangeAfter(at(5, 17))?.toMinutes())
        assertNull(AvailabilityRule(AvailabilityMode.Always).availabilityAt(at(5, 17)).nextChangeAfter(at(5, 17)))
        assertNull(AvailabilityRule(AvailabilityMode.Off).availabilityAt(at(5, 17)).nextChangeAfter(at(5, 17)))
    }

    @Test
    fun summariesAreShortAndPlain() {
        assertEquals("Always on", AvailabilityRule().summary())
        assertEquals("Off", AvailabilityRule(AvailabilityMode.Off).summary())
        assertEquals("Mon, Wed: 6:00 PM to 8:00 PM", evenings.summary())
        assertEquals("Weekdays", describeDays(WEEKDAYS))
        assertEquals("Weekends", describeDays(WEEKEND))
        assertEquals("Every day", describeDays(WeeklySchedule.EVERY_DAY))
    }
}
