package com.example.normpix.backup

import com.example.normpix.finance.data.FinanceBackupCodec
import com.example.normpix.finance.data.FinanceSnapshot
import com.example.normpix.reminders.Reminder
import com.example.normpix.reminders.ReminderBackupCodec
import com.example.normpix.settings.PrefValue
import com.example.normpix.wallet.CardsTransfer
import java.io.File

/**
 * An app backup that was read and found whole: what it holds, ready to be shown to the user and restored. Nothing here has
 * changed anything yet. The cards are still locked ([cardsFile]); the user's password opens them (see [CardsTransfer.decode]).
 */
class AppBackupPlan internal constructor(
    val manifest: AppBackupManifest,
    /** The finance data; null when the backup has none. */
    val snapshot: FinanceSnapshot?,
    /** The pictures the finance data names that the backup carried, read into files. */
    val pictures: Map<String, File>,
    val settings: Map<String, PrefValue>?,
    val reminders: List<Reminder>?,
    val profilePhotos: Map<String, File>,
    val fonts: Map<String, File>,
    val cardsFile: String?,
    /** Entries of the settings and the reminders that couldn't be read, and are left out. */
    val skippedSettings: Int,
    val skippedReminders: Int,
) {
    /** The user's name, emoji or photo are in the backup. */
    val hasProfile: Boolean get() = settings != null && (profilePhotos.isNotEmpty() || settings.keys.any { it in PROFILE_KEYS })

    /** A font file and its name are in the backup. */
    val hasFont: Boolean get() = settings != null && fonts.isNotEmpty()

    /** What the backup can restore, in the order the user sees them. */
    val parts: Set<BackupPart>
        get() = buildSet<BackupPart> {
            if (snapshot != null) add(BackupPart.Finance)
            if (settings != null) add(BackupPart.Settings)
            if (reminders != null) add(BackupPart.Reminders)
            if (hasProfile) add(BackupPart.Profile)
            if (hasFont) add(BackupPart.Font)
            if (cardsFile != null) add(BackupPart.Cards)
        }
}

/** Reads the parts of an app backup and checks them, before anything is shown or changed. */
object AppBackupReader {
    sealed interface Parsed {
        class Ok(val plan: AppBackupPlan) : Parsed

        /** The backup can't be used; [errors] say why, in words for the user. */
        class Invalid(val errors: List<String>) : Parsed
    }

    /**
     * Checks a backup read from a zip ([AppBackupArchive.read]): the finance JSON [financeText] (null if the zip had none) with
     * its [pictures], and the [app] part. Any part that is there but damaged makes the whole backup unusable - it is said so,
     * rather than restoring some of it without the user knowing the rest was lost.
     */
    fun parse(financeText: String?, pictures: Map<String, File>, app: AppBackupArchive.App): Parsed {
        val errors = ArrayList<String>()
        var snapshot: FinanceSnapshot? = null
        if (financeText != null) {
            val result = FinanceBackupCodec.decode(financeText)
            snapshot = result.snapshot
            if (snapshot == null) errors += result.errors
        }
        val settings = app.settings?.let { text ->
            SettingsBackupCodec.decode(text).also { if (it == null) errors += "The settings in this backup are damaged." }
        }
        val reminders = app.reminders?.let { text ->
            ReminderBackupCodec.decode(text).also { if (it == null) errors += "The reminders in this backup are damaged." }
        }
        val cards = app.cards?.also { if (!CardsTransfer.isCardFile(it)) errors += "The saved cards in this backup are damaged." }
        if (errors.isNotEmpty()) return Parsed.Invalid(errors)
        return Parsed.Ok(
            AppBackupPlan(
                manifest = app.manifest,
                snapshot = snapshot,
                pictures = pictures,
                settings = settings?.values,
                reminders = reminders?.reminders,
                profilePhotos = app.profilePhotos,
                fonts = app.fonts,
                cardsFile = cards,
                skippedSettings = settings?.skipped ?: 0,
                skippedReminders = reminders?.skipped ?: 0,
            ),
        )
    }
}
