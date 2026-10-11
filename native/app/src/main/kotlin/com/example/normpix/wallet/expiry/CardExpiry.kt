package com.example.normpix.wallet.expiry

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/*
 * A card carries a month and a year, no day, so everything about its expiry works in whole months:
 *  - a card is good through the last day of its expiry month;
 *  - alerts begin on the first day of the month before that (a card expiring in 11/26 starts alerting on 1 Oct 2026),
 *    and repeat daily until the user marks that card done - past the expiry, too.
 */

/** Reads the form's month and year ("07" and "27", or "2027") into a real [YearMonth]; null for anything else, placeholders like "MM" included. */
fun parseExpiry(month: String, year: String): YearMonth? {
    val m = month.trim().toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val y = year.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val fullYear = if (year.trim().length <= 2) 2000 + y else y
    return runCatching { YearMonth.of(fullYear, m) }.getOrNull()
}

/** The first day alerts show: the first of the month before the expiry month. */
val YearMonth.alertStart: LocalDate get() = minusMonths(1).atDay(1)

/** The last day the card can still be used. */
val YearMonth.lastValidDay: LocalDate get() = atEndOfMonth()

/**
 * Where one card is in its expiry cycle.
 *  - [Normal]: the alert window has not begun.
 *  - [ExpiringSoon]: the window has begun and no reminder has been delivered yet.
 *  - [ReminderActive]: reminders are being delivered, daily, until the card is marked done.
 *  - [Completed]: the user marked this card done for this expiry.
 */
enum class CardExpiryPhase { Normal, ExpiringSoon, ReminderActive, Completed }

/**
 * What NormPix remembers about one card's expiry - never the number, the holder or the CVV, because this lives
 * outside the encrypted card vault so alarms can work without opening it. Bank and last four digits are what a
 * reminder says to tell two cards apart.
 *
 * The cycle belongs to an expiry: [completedFor] and [remindedAt] mean nothing once the card is renewed with a
 * new expiry (see [CardExpiryTracker.sync]), so a renewed card starts a fresh cycle on its own.
 */
data class CardExpiryRecord(
    val cardId: String,
    val bank: String,
    val last4: String,
    val expiry: YearMonth,
    /** The expiry this card's reminders were marked done for. */
    val completedFor: YearMonth? = null,
    /** When the last reminder for this expiry was delivered (epoch millis), if any. */
    val remindedAt: Long? = null,
) {
    val completed: Boolean get() = completedFor == expiry

    fun phaseOn(today: LocalDate): CardExpiryPhase = when {
        completed -> CardExpiryPhase.Completed
        today.isBefore(expiry.alertStart) -> CardExpiryPhase.Normal
        remindedAt != null -> CardExpiryPhase.ReminderActive
        else -> CardExpiryPhase.ExpiringSoon
    }

    fun hasExpiredOn(today: LocalDate): Boolean = today.isAfter(expiry.lastValidDay)

    fun stateOn(today: LocalDate) = CardExpiryState(phaseOn(today), expiry, hasExpiredOn(today))

    /**
     * When the next reminder is due: the first morning of the window, then every morning after it - past the
     * expiry month too - until [completed]. Null when there is nothing more to remind about.
     */
    fun nextReminder(now: ZonedDateTime, at: LocalTime = REMINDER_TIME): ZonedDateTime? {
        if (completed) return null
        val zone: ZoneId = now.zone
        val first = expiry.alertStart.atTime(at).atZone(zone)
        if (now.isBefore(first)) return first
        val day = if (now.toLocalTime().isBefore(at)) now.toLocalDate() else now.toLocalDate().plusDays(1)
        return day.atTime(at).atZone(zone)
    }

    companion object {
        /** Reminders arrive at 9:00 in the morning, local time. */
        val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
    }
}

/** [phase] plus what the wording needs: the card's [expiry] and whether it is already past it. */
data class CardExpiryState(val phase: CardExpiryPhase, val expiry: YearMonth, val expired: Boolean) {
    /** Whether the card shows its notice with "Mark as done". */
    val needsAttention: Boolean get() = phase == CardExpiryPhase.ExpiringSoon || phase == CardExpiryPhase.ReminderActive
}
