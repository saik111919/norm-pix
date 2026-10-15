package com.example.normpix.reminders

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Local SQLite database for [Reminder]s. Unlike the encrypted card vault,
 * reminders need real WHERE / ORDER BY queries as the list grows, so they live
 * in a proper table. Pass a null [name] for a private in-memory database (tests).
 */
class RemindersDatabase(context: Context, name: String? = DB_NAME) :
    SQLiteOpenHelper(context.applicationContext, name, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              notes TEXT,
              category TEXT NOT NULL,
              custom_category_label TEXT,
              due_at INTEGER NOT NULL,
              repeat_rule_json TEXT NOT NULL,
              status TEXT NOT NULL DEFAULT 'active',
              snoozed_until INTEGER,
              linked_account_id TEXT,
              link_kind TEXT,
              link_ref TEXT,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              completed_at INTEGER,
              last_fired_at INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_reminders_status_due ON $TABLE(status, due_at)")
        db.execSQL("CREATE INDEX idx_reminders_category ON $TABLE(category)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema changes add `if (oldVersion < N) { ALTER TABLE ... }` steps here so existing
        // reminders survive an app update.
        if (oldVersion < 2) addLinks(db)
    }

    companion object {
        const val DB_NAME = "normpix_reminders.db"
        const val VERSION = 2
        const val TABLE = "reminders"

        /**
         * Version 2: a reminder can be about a card, an account, a transaction or a repeating payment, not only a card. Adds the
         * kind and the reference, and gives every reminder that was linked to a card (the only link there was) that link in
         * the new form. The old column stays, still written for a card, and is read when the new ones are empty.
         */
        fun addLinks(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN link_kind TEXT")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN link_ref TEXT")
            db.execSQL(
                "UPDATE $TABLE SET link_kind = '${ReminderLinkKind.Card.key}', link_ref = linked_account_id " +
                    "WHERE linked_account_id IS NOT NULL AND linked_account_id != ''",
            )
        }
    }
}

/** Data-access object for [Reminder] rows. All work runs on one dedicated thread. */
class ReminderDao(
    private val database: RemindersDatabase,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Where the database work runs: the one dedicated thread, unless a test runs it in place. The sample set uses the same. */
    internal val dispatcher: CoroutineDispatcher = defaultDispatcher,
) {
    private fun LocalDateTime.millis(): Long = atZone(zone).toInstant().toEpochMilli()

    private fun Long.moment(): LocalDateTime = java.time.Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()

    private suspend fun <T> io(block: SQLiteDatabase.() -> T): T = withContext(dispatcher) { database.writableDatabase.block() }

    private fun Reminder.toValues() = ContentValues().apply {
        id?.let { put("id", it) }
        put("title", title)
        put("notes", notes)
        put("category", category.key)
        put("custom_category_label", customCategoryLabel)
        put("due_at", dueAt.millis())
        put("repeat_rule_json", Json.encodeToString(JsonElement.serializer(), repeatRule.toJson()))
        put("status", status.key)
        put("snoozed_until", snoozedUntil?.millis())
        put("link_kind", link?.kind?.key)
        put("link_ref", link?.id)
        // A card is also kept in the column it always had, so the two never disagree about it.
        put("linked_account_id", linkedCardId)
        put("created_at", createdAt.millis())
        put("updated_at", updatedAt.millis())
        put("completed_at", completedAt?.millis())
        put("last_fired_at", lastFiredAt?.millis())
    }

    private fun Cursor.reminder(): Reminder {
        fun long(c: String) = getLong(getColumnIndexOrThrow(c))
        fun longOrNull(c: String) = getColumnIndexOrThrow(c).let { if (isNull(it)) null else getLong(it) }
        fun string(c: String) = getString(getColumnIndexOrThrow(c))
        fun stringOrNull(c: String) = getColumnIndexOrThrow(c).let { if (isNull(it)) null else getString(it) }
        return Reminder(
            id = long("id"),
            title = string("title"),
            notes = stringOrNull("notes"),
            category = ReminderCategory.fromKey(string("category")) ?: ReminderCategory.Personal,
            customCategoryLabel = stringOrNull("custom_category_label"),
            dueAt = long("due_at").moment(),
            repeatRule = RepeatRule.fromJson(Json.parseToJsonElement(string("repeat_rule_json")) as JsonObject),
            status = ReminderStatus.fromKey(string("status")) ?: ReminderStatus.Active,
            snoozedUntil = longOrNull("snoozed_until")?.moment(),
            link = readLink(stringOrNull("link_kind"), stringOrNull("link_ref"), stringOrNull("linked_account_id")),
            createdAt = long("created_at").moment(),
            updatedAt = long("updated_at").moment(),
            completedAt = longOrNull("completed_at")?.moment(),
            lastFiredAt = longOrNull("last_fired_at")?.moment(),
        )
    }

    /** The link as stored: the new kind and reference, or - for a row older than them - the card in the old column. */
    private fun readLink(kind: String?, ref: String?, legacyCard: String?): ReminderLink? {
        val parsed = kind?.let(ReminderLinkKind::fromKey)
        if (parsed != null && !ref.isNullOrEmpty()) return ReminderLink(parsed, ref)
        return legacyCard?.takeIf { it.isNotEmpty() }?.let(ReminderLink::card)
    }

    suspend fun insert(reminder: Reminder): Long = io { insertOrThrow(RemindersDatabase.TABLE, null, reminder.toValues()) }

    suspend fun update(reminder: Reminder) {
        io { update(RemindersDatabase.TABLE, reminder.toValues(), "id = ?", arrayOf(reminder.id.toString())) }
    }

    suspend fun delete(id: Long) {
        io { delete(RemindersDatabase.TABLE, "id = ?", arrayOf(id.toString())) }
    }

    suspend fun getById(id: Long): Reminder? = io {
        query(RemindersDatabase.TABLE, null, "id = ?", arrayOf(id.toString()), null, null, null, "1")
            .use { if (it.moveToFirst()) it.reminder() else null }
    }

    suspend fun getAll(): List<Reminder> = io {
        query(RemindersDatabase.TABLE, null, null, null, null, null, "due_at ASC").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.reminder()) }
        }
    }

    companion object {
        val defaultDispatcher: CoroutineDispatcher by lazy {
            Executors.newSingleThreadExecutor { r -> Thread(r, "normpix-reminders-db").apply { isDaemon = true } }
                .asCoroutineDispatcher()
        }
    }
}
