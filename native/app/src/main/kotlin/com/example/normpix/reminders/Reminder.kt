package com.example.normpix.reminders

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import com.example.normpix.finance.domain.RepeatEnd
import com.example.normpix.finance.domain.resolve
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** How a reminder recurs once it's completed. */
enum class RepeatKind(val key: String) {
    Once("once"),
    Daily("daily"),
    Weekdays("weekdays"),
    Weekly("weekly"),
    Monthly("monthly"),
    Custom("custom"),
    ;

    companion object {
        fun fromKey(key: String): RepeatKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Recurrence rule for a [Reminder]. Which of [weekdays] / [dayOfMonth] /
 * [intervalDays] is meaningful depends on [kind]:
 * - [RepeatKind.Weekly] uses [weekdays] (ISO weekday numbers, 1=Mon..7=Sun).
 * - [RepeatKind.Monthly] uses [dayOfMonth] (1-31, clamped to the target month's
 *   actual length - the 31st of a 30-day month lands on its 30th).
 * - [RepeatKind.Custom] uses [intervalDays] (a fixed day interval).
 *
 * A repeat goes on for ever unless it has an [endDate]: the last day a reminder may fall on. After it, completing the last
 * reminder finishes it instead of moving it on. "After 12 times" is kept as the day of the 12th ([ending]), the way a repeating
 * payment keeps it, so there is no count to fall out of step when a reminder is moved.
 */
data class RepeatRule(
    val kind: RepeatKind,
    val weekdays: Set<Int>? = null,
    val dayOfMonth: Int? = null,
    val intervalDays: Int? = null,
    /** The last day a reminder may fall on (that day included), or null for no end. Ignored for a reminder that does not repeat. */
    val endDate: LocalDate? = null,
) {
    val repeats: Boolean get() = kind != RepeatKind.Once

    /** What the rhythm is, then where it stops: "Every day", "Weekly on Mon, Thu until 3 Mar 2027". */
    val label: String
        get() {
            val every = when (kind) {
                RepeatKind.Once -> "Does not repeat"
                RepeatKind.Daily -> "Every day"
                RepeatKind.Weekdays -> "Every weekday"
                RepeatKind.Weekly -> weeklyLabel()
                RepeatKind.Monthly -> "Monthly on day ${dayOfMonth ?: 1}"
                RepeatKind.Custom -> "Every ${intervalDays ?: 1} day${if (intervalDays == 1) "" else "s"}"
            }
            val end = endDate
            return if (repeats && end != null) "$every until ${dayText(end)}" else every
        }

    /** The same rhythm with no end: the steps an "after N times" is counted in. */
    val unending: RepeatRule get() = if (endDate == null) this else copy(endDate = null)

    /** How this rule ends, as the form holds it: a day, or never. */
    val ends: RepeatEnd get() = endDate?.let { RepeatEnd.OnDate(it) } ?: RepeatEnd.Never

    /** This rhythm ending as [ends] says, when [first] is the first reminder; a count becomes the day of the last one. */
    fun ending(ends: RepeatEnd, first: LocalDateTime): RepeatRule =
        if (!repeats) this else copy(endDate = ends.resolve(first, unending::nextOccurrence)?.toLocalDate())

    private fun weeklyLabel(): String {
        val days = weekdays
        if (days.isNullOrEmpty()) return "Weekly"
        return "Weekly on ${days.sorted().joinToString(", ") { WEEKDAY_NAMES[it - 1] }}"
    }

    /**
     * The next occurrence strictly after [from], preserving [from]'s time of
     * day. Null for [RepeatKind.Once] - there is no next one - and once the next
     * would fall after the [endDate]: the repeat is over.
     */
    fun nextOccurrence(from: LocalDateTime): LocalDateTime? {
        val next = following(from) ?: return null
        val end = endDate
        return if (end != null && next.toLocalDate().isAfter(end)) null else next
    }

    private fun following(from: LocalDateTime): LocalDateTime? = when (kind) {
        RepeatKind.Once -> null
        RepeatKind.Daily -> from.plusDays(1)
        RepeatKind.Weekdays -> {
            var next = from.plusDays(1)
            while (next.dayOfWeek.value >= 6) next = next.plusDays(1)
            next
        }
        RepeatKind.Weekly -> nextWeekly(from)
        RepeatKind.Monthly -> nextMonthly(from)
        RepeatKind.Custom -> from.plusDays((intervalDays ?: 1).toLong())
    }

    private fun nextWeekly(from: LocalDateTime): LocalDateTime {
        val days = weekdays
        if (days.isNullOrEmpty()) return from.plusDays(7)
        for (offset in 1..7) {
            val candidate = from.plusDays(offset.toLong())
            if (candidate.dayOfWeek.value in days) return candidate
        }
        return from.plusDays(7)
    }

    private fun nextMonthly(from: LocalDateTime): LocalDateTime {
        val target = dayOfMonth ?: from.dayOfMonth
        val next = YearMonth.of(from.year, from.monthValue).plusMonths(1)
        val day = target.coerceIn(1, next.lengthOfMonth())
        return LocalDateTime.of(LocalDate.of(next.year, next.monthValue, day), LocalTime.of(from.hour, from.minute, from.second))
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("kind", kind.key)
        weekdays?.let { put("weekdays", JsonArray(it.sorted().map(::JsonPrimitive))) }
        dayOfMonth?.let { put("dayOfMonth", it) }
        intervalDays?.let { put("intervalDays", it) }
        endDate?.let { put("endDate", it.toString()) }
    }

    companion object {
        val Once = RepeatRule(RepeatKind.Once)

        private val WEEKDAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        private val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        /** "3 Mar 2027". */
        private fun dayText(day: LocalDate): String = "${day.dayOfMonth} ${MONTH_NAMES[day.monthValue - 1]} ${day.year}"

        fun fromJson(json: JsonObject): RepeatRule = RepeatRule(
            kind = RepeatKind.fromKey((json["kind"] as JsonPrimitive).content) ?: RepeatKind.Once,
            weekdays = (json["weekdays"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }?.toSet(),
            dayOfMonth = (json["dayOfMonth"] as? JsonPrimitive)?.intOrNull,
            intervalDays = (json["intervalDays"] as? JsonPrimitive)?.intOrNull,
            // Absent in a rule saved before repeats could end, and in one with no end: no end. A date that can't be read is no end too.
            endDate = (json["endDate"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    }
}

/**
 * Broad grouping for a reminder, used for filtering and the category chip
 * shown on each reminder.
 */
enum class ReminderCategory(val key: String, val label: String) {
    Payments("payments", "Payments"),
    Expenses("expenses", "Expenses"),
    Income("income", "Income"),
    Accounts("accounts", "Accounts"),
    Cards("cards", "Cards"),
    Personal("personal", "Personal"),
    Custom("custom", "Custom"),
    ;

    companion object {
        fun fromKey(key: String): ReminderCategory? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Where a reminder is in its lifecycle. There is no separate "overdue" value -
 * a reminder stays [Active] past its due time; overdue-ness is a computed
 * property ([Reminder.isOverdue]), not stored state.
 */
enum class ReminderStatus(val key: String) {
    Active("active"),
    Completed("completed"),
    Snoozed("snoozed"),
    ;

    companion object {
        fun fromKey(key: String): ReminderStatus? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What a reminder can be attached to. A card lives in the encrypted vault; the rest are finance records, referred to by id.
 * The reference is soft: the reminder outlives what it names (a deleted expense leaves a reminder that says so).
 */
enum class ReminderLinkKind(val key: String) {
    Card("card"),
    Account("account"),
    Transaction("transaction"),
    Recurring("recurring"),
    ;

    companion object {
        fun fromKey(key: String): ReminderLinkKind? = entries.firstOrNull { it.key == key }
    }
}

/** The thing a reminder is about: a [kind] and its [id] (a card's id, or the finance record's numeric id as text). */
data class ReminderLink(val kind: ReminderLinkKind, val id: String) {
    companion object {
        fun card(id: String) = ReminderLink(ReminderLinkKind.Card, id)
        fun account(id: Long) = ReminderLink(ReminderLinkKind.Account, id.toString())
        fun transaction(id: Long) = ReminderLink(ReminderLinkKind.Transaction, id.toString())
        fun recurring(id: Long) = ReminderLink(ReminderLinkKind.Recurring, id.toString())
    }
}

/**
 * A single reminder. Plain, storage-agnostic data: it round-trips through
 * [RemindersDatabase] via the DAO.
 */
data class Reminder(
    /** Null before the first insert; assigned by SQLite afterwards. Reused as the notification id. */
    val id: Long? = null,
    val title: String,
    val notes: String? = null,
    val category: ReminderCategory,
    /** User-entered label when [category] is [ReminderCategory.Custom]. */
    val customCategoryLabel: String? = null,
    val dueAt: LocalDateTime,
    val repeatRule: RepeatRule,
    val status: ReminderStatus = ReminderStatus.Active,
    /**
     * Set while [status] is [ReminderStatus.Snoozed]; the notification is
     * rescheduled for this time instead of [dueAt], which is left untouched so a
     * repeat chain isn't disturbed by where the snooze happened to land.
     */
    val snoozedUntil: LocalDateTime? = null,
    /** What this is about, if anything: a card, an account, a transaction or a repeating payment. */
    val link: ReminderLink? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val completedAt: LocalDateTime? = null,
    val lastFiredAt: LocalDateTime? = null,
) {
    /** The due time that actually matters right now: [snoozedUntil] while snoozed, otherwise [dueAt]. */
    val effectiveDueAt: LocalDateTime
        get() = if (status == ReminderStatus.Snoozed && snoozedUntil != null) snoozedUntil else dueAt

    /** The vault card it is about, when it is attached to one. */
    val linkedCardId: String?
        get() = link?.takeIf { it.kind == ReminderLinkKind.Card }?.id

    /**
     * Past its due moment and not done - including a snoozed reminder whose
     * snooze has run out. Exactly complements "upcoming" for unfinished
     * reminders, so every reminder always shows in one list.
     */
    fun isOverdue(now: LocalDateTime = LocalDateTime.now()): Boolean =
        status != ReminderStatus.Completed && !effectiveDueAt.isAfter(now)

    val categoryLabel: String
        get() = if (category == ReminderCategory.Custom) {
            customCategoryLabel?.trim()?.takeIf { it.isNotEmpty() } ?: "Custom"
        } else {
            category.label
        }
}

/**
 * Pure Complete / Snooze / Reschedule state transitions for a [Reminder],
 * shared by the in-app controller and by the notification action receivers, so
 * both apply exactly the same semantics.
 */
object ReminderActions {
    /**
     * A one-time reminder completes outright. A repeating one instead advances to
     * its next occurrence, computed from the *original* due time (not [now]) so a
     * 9am daily reminder completed a few minutes late still lands on 9am the next
     * day.
     */
    fun complete(reminder: Reminder, now: LocalDateTime = LocalDateTime.now()): Reminder {
        val next = reminder.repeatRule.nextOccurrence(reminder.dueAt)
            ?: return reminder.copy(status = ReminderStatus.Completed, completedAt = now, updatedAt = now)
        return reminder.copy(
            dueAt = next,
            status = ReminderStatus.Active,
            snoozedUntil = null,
            lastFiredAt = null,
            updatedAt = now,
        )
    }

    /** Sets the snooze time without touching the due time, so the repeat chain isn't disturbed. */
    fun snooze(reminder: Reminder, by: Duration, now: LocalDateTime = LocalDateTime.now()): Reminder =
        reminder.copy(status = ReminderStatus.Snoozed, snoozedUntil = now.plus(by), updatedAt = now)

    /** An explicit user override: a brand-new due time, which rebases future repeat occurrences. */
    fun reschedule(reminder: Reminder, newDueAt: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): Reminder =
        reminder.copy(
            dueAt = newDueAt,
            status = ReminderStatus.Active,
            snoozedUntil = null,
            lastFiredAt = null,
            updatedAt = now,
        )
}
