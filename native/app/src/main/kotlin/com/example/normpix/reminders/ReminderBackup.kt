package com.example.normpix.reminders

import com.example.normpix.finance.data.parseDateTime
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Reminders as a backup carries them (`app/reminders.json` in an app backup): `{"reminders": [reminder, ...]}`, each reminder
 * with everything it has - title, notes, category, due time, repeat rule, status, snooze, what it is about, and when it was
 * made, changed, completed and last rang. Times are the wall-clock time they were set for, with no zone, so a 9:00 reminder
 * is at 9:00 wherever the backup is restored.
 *
 * Reading is forgiving about what a reminder may lack and strict about what it can't do without: an entry with no title or no
 * due time is left out (and counted), an unknown category or status falls back the way the database does, and a repeat rule
 * is brought back inside what the app can use.
 *
 * A repeat rule is written and read by the rule itself ([RepeatRule.toJson] and [RepeatRule.fromJson]), so whatever the rule
 * carries - an end date, once it has one - goes into a backup and comes back, and a backup made before the rule had it reads
 * as a rule without it.
 */
object ReminderBackupCodec {
    private val pretty = Json { prettyPrint = true }

    /** The reminders read from a backup, and how many entries in it were unusable and left out. */
    class Decoded(val reminders: List<Reminder>, val skipped: Int)

    fun encode(reminders: List<Reminder>): String {
        val root = buildJsonObject { put("reminders", JsonArray(reminders.map { it.toJson() })) }
        return pretty.encodeToString(JsonElement.serializer(), root)
    }

    /** The reminders in [text], or null when it isn't a reminders file at all. */
    fun decode(text: String): Decoded? {
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val entries = root["reminders"] as? JsonArray ?: return null
        val seen = HashSet<Long>()
        var skipped = 0
        val reminders = ArrayList<Reminder>(entries.size)
        for (entry in entries) {
            val reminder = (entry as? JsonObject)?.let { runCatching { it.toReminder() }.getOrNull() }
            if (reminder == null) {
                skipped++
                continue
            }
            // Two reminders can't share an id: the later one is given a new one when it is stored.
            val id = reminder.id
            reminders += if (id != null && !seen.add(id)) reminder.copy(id = null) else reminder
        }
        return Decoded(reminders, skipped)
    }

    private fun Reminder.toJson(): JsonObject = buildJsonObject {
        id?.let { put("id", it) }
        put("title", title)
        notes?.let { put("notes", it) }
        put("category", category.key)
        customCategoryLabel?.let { put("customCategoryLabel", it) }
        put("dueAt", dueAt.toString())
        put("repeat", repeatRule.toJson())
        put("status", status.key)
        snoozedUntil?.let { put("snoozedUntil", it.toString()) }
        link?.let { link ->
            put(
                "link",
                buildJsonObject {
                    put("kind", link.kind.key)
                    put("id", link.id)
                },
            )
        }
        put("createdAt", createdAt.toString())
        put("updatedAt", updatedAt.toString())
        completedAt?.let { put("completedAt", it.toString()) }
        lastFiredAt?.let { put("lastFiredAt", it.toString()) }
    }

    private fun JsonObject.toReminder(): Reminder? {
        fun text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun moment(key: String): LocalDateTime? = text(key)?.let(::parseDateTime)
        val title = text("title")?.takeIf { it.isNotBlank() } ?: return null
        val dueAt = moment("dueAt") ?: return null
        val created = moment("createdAt") ?: dueAt
        return Reminder(
            // A notification id is an Int, so only ids that fit one are kept.
            id = (this["id"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it in 1..Int.MAX_VALUE },
            title = title,
            notes = text("notes"),
            category = text("category")?.let(ReminderCategory::fromKey) ?: ReminderCategory.Personal,
            customCategoryLabel = text("customCategoryLabel"),
            dueAt = dueAt,
            repeatRule = (this["repeat"] as? JsonObject)?.let { runCatching { RepeatRule.fromJson(it).usable() }.getOrNull() } ?: RepeatRule.Once,
            status = text("status")?.let(ReminderStatus::fromKey) ?: ReminderStatus.Active,
            snoozedUntil = moment("snoozedUntil"),
            link = (this["link"] as? JsonObject)?.let { link ->
                val kind = (link["kind"] as? JsonPrimitive)?.content?.let(ReminderLinkKind::fromKey)
                val id = (link["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                if (kind != null && id != null) ReminderLink(kind, id) else null
            },
            createdAt = created,
            updatedAt = moment("updatedAt") ?: created,
            completedAt = moment("completedAt"),
            lastFiredAt = moment("lastFiredAt"),
        )
    }

    /** The rule with its numbers inside what repeating can use: weekdays 1-7, a day of the month 1-31, an interval of a day or more. */
    private fun RepeatRule.usable(): RepeatRule = copy(
        weekdays = weekdays?.filter { it in 1..7 }?.toSet(),
        dayOfMonth = dayOfMonth?.coerceIn(1, 31),
        intervalDays = intervalDays?.coerceAtLeast(1),
    )
}
