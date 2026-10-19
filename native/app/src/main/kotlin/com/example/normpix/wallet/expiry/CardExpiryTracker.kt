package com.example.normpix.wallet.expiry

import com.example.normpix.wallet.StoredCard
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where [CardExpiryRecord]s are kept between launches. */
interface CardExpiryStore {
    fun all(): Map<String, CardExpiryRecord>

    fun put(record: CardExpiryRecord)

    fun remove(cardId: String)
}

/** Wakes the app at a card's next reminder time. */
interface ExpiryScheduler {
    fun schedule(cardId: String, atMillis: Long)

    fun cancel(cardId: String)
}

/** Shows (and takes back) the system notification for a card. */
interface ExpiryNotifier {
    /** False when the system won't show notifications from NormPix (switched off, or no permission). */
    fun areEnabled(): Boolean

    /** Shows the reminder for [record], replacing yesterday's. Returns whether it was actually shown. */
    fun show(record: CardExpiryRecord, expired: Boolean): Boolean

    fun cancel(cardId: String)
}

/**
 * Every card's expiry cycle: Normal -> Expiring soon -> Reminder active -> Completed (see [CardExpiryPhase]).
 *
 * The tracker keeps its own small record per card (bank, last four digits, expiry, whether it was marked done),
 * not the card vault, so a reminder can fire - and be dismissed from its notification - without the vault
 * being opened. [sync] keeps the records in step with the card list; alarms and notifications follow the records.
 *
 * State is per card and survives restarts. Edge cases, each covered by a test:
 *  - a card already past its expiry is in its window and reminds until marked done;
 *  - changing a card's expiry (a renewal) starts a fresh cycle and withdraws the old notification;
 *  - deleting a card cancels its alarm and notification and forgets it;
 *  - cards with no usable expiry are ignored;
 *  - with notifications off nothing is lost: the card still shows its in-app notice, and reminders resume as
 *    soon as they are allowed;
 *  - two cards expiring together are independent, each with its own notification and its own "done".
 */
class CardExpiryTracker(
    private val store: CardExpiryStore,
    private val scheduler: ExpiryScheduler,
    private val notifier: ExpiryNotifier,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val reminderTime: LocalTime = CardExpiryRecord.REMINDER_TIME,
) {
    private val _states = MutableStateFlow<Map<String, CardExpiryState>>(emptyMap())

    /** The state of every tracked card, by card id. Cards that aren't tracked have none. */
    val states: StateFlow<Map<String, CardExpiryState>> = _states.asStateFlow()

    private val now: ZonedDateTime get() = ZonedDateTime.now(clock)
    private val today: LocalDate get() = LocalDate.now(clock)

    /** Whether reminders can actually be shown right now. */
    fun notificationsEnabled(): Boolean = notifier.areEnabled()

    /** Brings the records in line with [cards] (the full, successfully loaded list - never a partial or failed read). */
    @Synchronized
    fun sync(cards: List<StoredCard>) {
        val existing = store.all()
        val seen = HashSet<String>()
        for (card in cards) {
            val expiry = parseExpiry(card.expiryMonth, card.expiryYear) ?: continue
            seen += card.id
            val old = existing[card.id]
            val next = when {
                old == null -> CardExpiryRecord(card.id, card.bankName, card.last4, expiry)
                old.expiry != expiry -> {
                    // Renewed: the old cycle is over, so the old notification goes and a new one can begin.
                    notifier.cancel(card.id)
                    CardExpiryRecord(card.id, card.bankName, card.last4, expiry)
                }
                else -> old.copy(bank = card.bankName, last4 = card.last4)
            }
            if (next != old) store.put(next)
        }
        for (id in existing.keys - seen) {
            store.remove(id)
            scheduler.cancel(id)
            notifier.cancel(id)
        }
        rearm()
    }

    /**
     * Forgets every card: each one's alarm and notification are cancelled and its record removed, so nothing about a card's
     * expiry is left once the cards are deleted (Store cards turned off).
     */
    fun clear() = sync(emptyList())

    /** The user marked this card done: no more reminders for this expiry. */
    @Synchronized
    fun markDone(cardId: String) {
        val record = store.all()[cardId] ?: return
        store.put(record.copy(completedFor = record.expiry))
        scheduler.cancel(cardId)
        notifier.cancel(cardId)
        publish()
    }

    /** A card's alarm went off: deliver today's reminder if the card is still due one, and queue tomorrow's. */
    @Synchronized
    fun onAlarm(cardId: String) {
        val record = store.all()[cardId] ?: return
        val phase = record.phaseOn(today)
        if (phase == CardExpiryPhase.ExpiringSoon || phase == CardExpiryPhase.ReminderActive) {
            if (notifier.show(record, expired = record.hasExpiredOn(today))) {
                store.put(record.copy(remindedAt = clock.millis()))
            }
        }
        schedule(store.all()[cardId] ?: return)
        publish()
    }

    /** Puts every alarm back (after a reboot, an app update or a clock or time-zone change) and refreshes [states] for today's date. */
    @Synchronized
    fun rearmAll() = rearm()

    private fun rearm() {
        for (record in store.all().values) schedule(record)
        publish()
    }

    private fun schedule(record: CardExpiryRecord) {
        val next = record.nextReminder(now, reminderTime)
        if (next == null) scheduler.cancel(record.cardId) else scheduler.schedule(record.cardId, next.toInstant().toEpochMilli())
    }

    private fun publish() {
        val day = today
        _states.value = store.all().mapValues { (_, record) -> record.stateOn(day) }
    }

    companion object {
        /** A tracker that remembers in memory and never wakes or notifies anyone: tests, and the sample-data container. */
        fun inert(clock: Clock = Clock.systemDefaultZone()) =
            CardExpiryTracker(InMemoryCardExpiryStore(), NoExpiryScheduler, NoExpiryNotifier, clock)
    }
}

class InMemoryCardExpiryStore : CardExpiryStore {
    private val records = LinkedHashMap<String, CardExpiryRecord>()

    @Synchronized
    override fun all(): Map<String, CardExpiryRecord> = LinkedHashMap(records)

    @Synchronized
    override fun put(record: CardExpiryRecord) {
        records[record.cardId] = record
    }

    @Synchronized
    override fun remove(cardId: String) {
        records.remove(cardId)
    }
}

object NoExpiryScheduler : ExpiryScheduler {
    override fun schedule(cardId: String, atMillis: Long) = Unit

    override fun cancel(cardId: String) = Unit
}

object NoExpiryNotifier : ExpiryNotifier {
    override fun areEnabled(): Boolean = false

    override fun show(record: CardExpiryRecord, expired: Boolean): Boolean = false

    override fun cancel(cardId: String) = Unit
}
