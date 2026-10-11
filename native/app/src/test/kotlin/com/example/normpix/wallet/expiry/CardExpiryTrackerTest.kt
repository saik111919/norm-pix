package com.example.normpix.wallet.expiry

import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.StoredCard
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MutableClock(var now: ZonedDateTime) : Clock() {
    override fun getZone(): ZoneId = now.zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now.withZoneSameInstant(zone))

    override fun instant(): Instant = now.toInstant()
}

private class FakeScheduler : ExpiryScheduler {
    val alarms = mutableMapOf<String, Long>()
    val cancelled = mutableListOf<String>()

    override fun schedule(cardId: String, atMillis: Long) {
        alarms[cardId] = atMillis
    }

    override fun cancel(cardId: String) {
        alarms.remove(cardId)
        cancelled += cardId
    }
}

private class FakeNotifier(var enabled: Boolean = true) : ExpiryNotifier {
    data class Shown(val cardId: String, val expired: Boolean)

    val shown = mutableListOf<Shown>()
    val cancelled = mutableListOf<String>()

    override fun areEnabled() = enabled

    override fun show(record: CardExpiryRecord, expired: Boolean): Boolean {
        if (!enabled) return false
        shown += Shown(record.cardId, expired)
        return true
    }

    override fun cancel(cardId: String) {
        cancelled += cardId
    }
}

class CardExpiryTrackerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = MutableClock(ZonedDateTime.of(2026, 10, 6, 14, 0, 0, 0, zone))
    private val store = InMemoryCardExpiryStore()
    private val scheduler = FakeScheduler()
    private val notifier = FakeNotifier()
    private val tracker = tracker()

    private fun tracker() = CardExpiryTracker(store, scheduler, notifier, clock)

    private fun card(id: String, month: String, year: String, bank: String = "HDFC Bank", number: String = "4111111111114242") = StoredCard(
        id = id,
        bankName = bank,
        cardholderName = "A Person",
        cardNumber = number,
        expiryMonth = month,
        expiryYear = year,
        cvv = "123",
        kind = CardKind.Debit,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
    )

    private fun millis(y: Int, m: Int, d: Int, h: Int = 9) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun phaseOf(id: String) = tracker.states.value[id]?.phase

    @Test
    fun aCardOutsideTheWindowIsNormalAndWakesAtTheWindowsFirstMorning() {
        tracker.sync(listOf(card("a", "05", "27")))
        assertEquals(CardExpiryPhase.Normal, phaseOf("a"))
        assertEquals(millis(2027, 4, 1), scheduler.alarms["a"], "alerts start 1 April for a card expiring in May")
    }

    @Test
    fun aCardInsideTheWindowIsExpiringSoon() {
        tracker.sync(listOf(card("a", "11", "26")))
        assertEquals(CardExpiryPhase.ExpiringSoon, phaseOf("a"))
        assertTrue(tracker.states.value["a"]!!.needsAttention)
        assertEquals(millis(2026, 10, 7), scheduler.alarms["a"], "the next 9:00 - 14:00 today is past")
    }

    @Test
    fun theReminderIsDeliveredThenRepeatedEveryDayUntilDone() {
        tracker.sync(listOf(card("a", "11", "26")))

        clock.now = ZonedDateTime.of(2026, 10, 7, 9, 0, 5, 0, zone)
        tracker.onAlarm("a")
        assertEquals(listOf(FakeNotifier.Shown("a", expired = false)), notifier.shown)
        assertEquals(CardExpiryPhase.ReminderActive, phaseOf("a"))
        assertEquals(millis(2026, 10, 8), scheduler.alarms["a"], "tomorrow's is already queued")

        for (day in 8..12) {
            clock.now = ZonedDateTime.of(2026, 10, day, 9, 0, 5, 0, zone)
            tracker.onAlarm("a")
        }
        assertEquals(6, notifier.shown.size, "one reminder a day, none skipped")
        assertEquals(CardExpiryPhase.ReminderActive, phaseOf("a"))
    }

    @Test
    fun markingADoneStopsItsRemindersAndWithdrawsItsNotification() {
        tracker.sync(listOf(card("a", "11", "26")))
        clock.now = ZonedDateTime.of(2026, 10, 7, 9, 0, 5, 0, zone)
        tracker.onAlarm("a")

        tracker.markDone("a")
        assertEquals(CardExpiryPhase.Completed, phaseOf("a"))
        assertNull(scheduler.alarms["a"], "no more alarms")
        assertTrue("a" in notifier.cancelled, "the notification goes")

        clock.now = ZonedDateTime.of(2026, 10, 8, 9, 0, 5, 0, zone)
        tracker.onAlarm("a") // a stray alarm
        assertEquals(1, notifier.shown.size, "nothing more is shown")
    }

    @Test
    fun theDoneStateSurvivesARestart() {
        tracker.sync(listOf(card("a", "11", "26")))
        tracker.markDone("a")

        val restarted = tracker() // new process, same store
        restarted.sync(listOf(card("a", "11", "26")))
        assertEquals(CardExpiryPhase.Completed, restarted.states.value["a"]!!.phase)
        assertNull(scheduler.alarms["a"])
    }

    @Test
    fun theReminderStateSurvivesARestart() {
        tracker.sync(listOf(card("a", "11", "26")))
        clock.now = ZonedDateTime.of(2026, 10, 7, 9, 0, 5, 0, zone)
        tracker.onAlarm("a")

        val restarted = tracker()
        restarted.rearmAll()
        assertEquals(CardExpiryPhase.ReminderActive, restarted.states.value["a"]!!.phase)
    }

    @Test
    fun aCardAlreadyPastItsExpiryRemindsAndSaysSo() {
        tracker.sync(listOf(card("a", "03", "25")))
        assertEquals(CardExpiryPhase.ExpiringSoon, phaseOf("a"))
        assertTrue(tracker.states.value["a"]!!.expired)

        clock.now = ZonedDateTime.of(2026, 10, 7, 9, 0, 5, 0, zone)
        tracker.onAlarm("a")
        assertEquals(listOf(FakeNotifier.Shown("a", expired = true)), notifier.shown)
    }

    @Test
    fun changingTheExpiryStartsAFreshCycle() {
        tracker.sync(listOf(card("a", "11", "26")))
        clock.now = ZonedDateTime.of(2026, 10, 7, 9, 0, 5, 0, zone)
        tracker.onAlarm("a")
        tracker.markDone("a")
        assertEquals(CardExpiryPhase.Completed, phaseOf("a"))

        // Renewed: the new card runs to 2030.
        notifier.cancelled.clear()
        tracker.sync(listOf(card("a", "11", "30")))
        assertEquals(CardExpiryPhase.Normal, phaseOf("a"), "the old done does not carry over")
        assertEquals(millis(2030, 10, 1), scheduler.alarms["a"])
        assertTrue("a" in notifier.cancelled, "and any old notification is withdrawn")
    }

    @Test
    fun aCorrectedExpiryThatIsInTheWindowRemindsAgain() {
        tracker.sync(listOf(card("a", "12", "26")))
        assertEquals(CardExpiryPhase.Normal, phaseOf("a"), "December: the window opens in November")
        tracker.markDone("a")
        tracker.sync(listOf(card("a", "10", "26"))) // fixed to October: a new expiry, a new cycle, already in its window
        assertEquals(CardExpiryPhase.ExpiringSoon, phaseOf("a"))
    }

    @Test
    fun aDeletedCardIsForgottenAlarmAndNotificationIncluded() {
        tracker.sync(listOf(card("a", "11", "26"), card("b", "11", "26")))
        tracker.sync(listOf(card("b", "11", "26")))

        assertNull(tracker.states.value["a"])
        assertNull(scheduler.alarms["a"])
        assertTrue("a" in notifier.cancelled)
        assertTrue("a" !in store.all())
        assertEquals(CardExpiryPhase.ExpiringSoon, phaseOf("b"), "the other card is untouched")
    }

    @Test
    fun anEmptyListLoadedFromAWorkingVaultClearsEverything() {
        tracker.sync(listOf(card("a", "11", "26")))
        tracker.sync(emptyList())
        assertTrue(tracker.states.value.isEmpty())
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun withNotificationsOffNothingIsLostAndRemindersResumeWhenAllowed() {
        notifier.enabled = false
        tracker.sync(listOf(card("a", "11", "26")))

        clock.now = ZonedDateTime.of(2026, 10, 7, 9, 0, 5, 0, zone)
        tracker.onAlarm("a")
        assertTrue(notifier.shown.isEmpty())
        assertEquals(CardExpiryPhase.ExpiringSoon, phaseOf("a"), "still expiring soon, so the card still shows its in-app notice")
        assertTrue(tracker.states.value["a"]!!.needsAttention)
        assertEquals(millis(2026, 10, 8), scheduler.alarms["a"], "and the alarm keeps going")

        notifier.enabled = true
        clock.now = ZonedDateTime.of(2026, 10, 8, 9, 0, 5, 0, zone)
        tracker.onAlarm("a")
        assertEquals(1, notifier.shown.size)
        assertEquals(CardExpiryPhase.ReminderActive, phaseOf("a"))
        assertTrue(tracker.notificationsEnabled())
    }

    @Test
    fun twoCardsAreIndependent() {
        tracker.sync(listOf(card("a", "11", "26", bank = "HDFC Bank"), card("b", "11", "26", bank = "ICICI Bank")))
        clock.now = ZonedDateTime.of(2026, 10, 7, 9, 0, 5, 0, zone)
        tracker.onAlarm("a")
        tracker.onAlarm("b")
        assertEquals(setOf("a", "b"), notifier.shown.map { it.cardId }.toSet(), "each has its own notification")

        tracker.markDone("a")
        assertEquals(CardExpiryPhase.Completed, phaseOf("a"))
        assertEquals(CardExpiryPhase.ReminderActive, phaseOf("b"), "marking one done leaves the other reminding")
        assertNull(scheduler.alarms["a"])
        assertEquals(millis(2026, 10, 8), scheduler.alarms["b"])
    }

    @Test
    fun cardsWithNoUsableExpiryAreIgnored() {
        tracker.sync(listOf(card("a", "MM", "YY"), card("b", "13", "26"), card("c", "", "")))
        assertTrue(tracker.states.value.isEmpty())
        assertTrue(scheduler.alarms.isEmpty())
    }

    @Test
    fun bankAndLastDigitsStayCurrentWhenTheCardIsEditedWithoutChangingItsExpiry() {
        tracker.sync(listOf(card("a", "11", "26", bank = "HDFC Bank", number = "4111111111114242")))
        tracker.markDone("a")
        tracker.sync(listOf(card("a", "11", "26", bank = "HDFC Credit", number = "4111111111110001")))

        val stored = store.all().getValue("a")
        assertEquals("HDFC Credit", stored.bank)
        assertEquals("0001", stored.last4)
        assertTrue(stored.completed, "an edit that keeps the expiry keeps the done")
    }

    @Test
    fun rearmingAfterARebootPutsEveryPendingAlarmBack() {
        tracker.sync(listOf(card("a", "11", "26"), card("b", "05", "27"), card("c", "11", "26")))
        tracker.markDone("c")
        scheduler.alarms.clear() // a reboot forgets all alarms

        tracker.rearmAll()
        assertEquals(millis(2026, 10, 7), scheduler.alarms["a"])
        assertEquals(millis(2027, 4, 1), scheduler.alarms["b"])
        assertNull(scheduler.alarms["c"], "a finished card is not re-armed")
    }

    @Test
    fun theDateChangingMovesACardIntoItsWindow() {
        tracker.sync(listOf(card("a", "11", "26")))
        clock.now = ZonedDateTime.of(2026, 9, 20, 10, 0, 0, 0, zone)
        tracker.rearmAll()
        assertEquals(CardExpiryPhase.Normal, phaseOf("a"))

        clock.now = ZonedDateTime.of(2026, 10, 1, 10, 0, 0, 0, zone)
        tracker.rearmAll()
        assertEquals(CardExpiryPhase.ExpiringSoon, phaseOf("a"))
    }

    @Test
    fun theTrackerKeepsOnlyWhatAReminderNeeds() {
        tracker.sync(listOf(card("a", "11", "26", number = "4111111111114242")))
        val stored = store.all().getValue("a").toString()
        assertTrue("4242" in stored)
        assertFalse("4111111111114242" in stored, "never the card number")
        assertFalse("123" in stored, "never the CVV")
        assertEquals(YearMonth.of(2026, 11), store.all().getValue("a").expiry)
    }
}
