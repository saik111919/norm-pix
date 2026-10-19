package com.example.normpix.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** The parts of an app backup, as the user chooses them when restoring. [key] is how the manifest names a part. */
enum class BackupPart(val key: String) {
    /** Accounts, transactions, budgets and the rest of the ledger, with the pictures attached to transactions. */
    Finance("finance"),

    /** The app's preferences (theme, layout, and every other choice), apart from the profile and the font. */
    Settings("settings"),

    Reminders("reminders"),

    /** The user's name, emoji and photo. */
    Profile("profile"),

    /** The font file the user picked for the interface, with its name. */
    Font("font"),

    /** The saved cards, locked with a password. */
    Cards("cards"),
}

/**
 * What an app backup says about itself (`app/manifest.json`): that it is one, which version of the format it is, which NormPix
 * made it and when, and which [BackupPart]s it holds. What is restored is whatever is really in the file, never what this
 * claims - it is for people to read and for later versions to tell what they are looking at.
 */
class AppBackupManifest(
    val appVersion: String,
    /** When it was made, as written (local time, no zone); null if the file doesn't say. */
    val exportedAt: String?,
    val contents: List<String>,
    val version: Int = VERSION,
) {
    fun encode(): String {
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", version)
            put("appVersion", appVersion)
            exportedAt?.let { put("exportedAt", it) }
            put("contents", JsonArray(contents.map { JsonPrimitive(it) }))
        }
        return pretty.encodeToString(JsonElement.serializer(), root)
    }

    /** What reading a manifest came to. */
    sealed interface Read {
        class Valid(val manifest: AppBackupManifest) : Read

        /** Made by a newer NormPix than this one: it may hold things this version would silently drop. */
        data object Newer : Read

        /** Not a manifest of an app backup (some other zip that happens to have this file). */
        data object Invalid : Read
    }

    companion object {
        const val FORMAT = "normpix-app-backup"

        /** The version of the app backup format this NormPix writes and reads. */
        const val VERSION = 1

        private val pretty = Json { prettyPrint = true }

        fun decode(text: String): Read {
            val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return Read.Invalid
            fun string(key: String) = (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (string("format") != FORMAT) return Read.Invalid
            val version = (root["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: return Read.Invalid
            if (version > VERSION) return Read.Newer
            val contents = (root["contents"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .orEmpty()
            return Read.Valid(AppBackupManifest(string("appVersion").orEmpty(), string("exportedAt"), contents, version))
        }
    }
}
