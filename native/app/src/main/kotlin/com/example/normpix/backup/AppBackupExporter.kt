package com.example.normpix.backup

import android.content.Context
import com.example.normpix.AppContainer
import com.example.normpix.BuildConfig
import com.example.normpix.finance.data.FinanceBackupCodec
import com.example.normpix.finance.data.FinanceSnapshot
import com.example.normpix.reminders.ReminderBackupCodec
import com.example.normpix.settings.ProfilePhotos
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The preferences that make up the profile: a name, an emoji and a photo. They are restored with [BackupPart.Profile], not with the settings. */
internal val PROFILE_KEYS: Set<String> = setOf("profile_name", "profile_emoji", "profile_photo")

/** The preference holding the profile photo's file name. */
internal const val PROFILE_PHOTO_KEY = "profile_photo"

/**
 * The preferences that name the font the user picked: restored with [BackupPart.Font] and its file. The file itself is
 * `fonts/custom-interface-font.ttf` in the app's files, and the preference its display name (the custom font's contract).
 */
internal val FONT_KEYS: Set<String> = setOf("custom_font_name")

/** The folder of the app's files the interface font the user picked is kept in. */
internal const val FONTS_DIR = "fonts"

/**
 * Reads everything an app backup carries from the app's stores, and writes it as a zip ([AppBackupArchive]). The saved cards
 * are not read here: the caller passes them already locked with the user's password (a cards file), so card numbers never
 * exist in plain text anywhere in a backup.
 */
class AppBackupExporter(private val context: Context, private val container: AppContainer) {
    /** A backup ready to be written: everything read, so writing it is just copying. */
    class Source internal constructor(
        val snapshot: FinanceSnapshot,
        internal val financeJson: String,
        internal val parts: AppBackupArchive.Parts,
        /** How many preferences, reminders, and whether a profile photo and a font went in, for telling the user. */
        val settingCount: Int,
        val reminderCount: Int,
        val hasPhoto: Boolean,
        val hasFont: Boolean,
        val hasCards: Boolean,
    ) {
        /** The names of the pictures the transactions in the backup have. */
        val pictureNames: List<String> get() = snapshot.transactions.mapNotNull { it.picture }
    }

    /** Reads it all, as of [now]. [cardsFile] is the saved cards as a locked cards file, or null to leave them out. */
    suspend fun gather(now: LocalDateTime, cardsFile: String?): Source {
        val snapshot = container.finance.repository.snapshot()
        val settings = container.preferences.exportAll()
        container.reminders.ensureLoaded()
        val reminders = container.reminders.state.value.reminders
        val photo = ProfilePhotos(context).file(container.preferences.state.value.profilePhoto)
            ?.takeIf { it.length() <= AppBackupArchive.MAX_PHOTO_BYTES }
        val fonts = withContext(Dispatchers.IO) { fontFiles() }
        val contents = buildList {
            add(BackupPart.Finance)
            add(BackupPart.Settings)
            add(BackupPart.Reminders)
            if (photo != null || settings.keys.any { it in PROFILE_KEYS }) add(BackupPart.Profile)
            if (fonts.isNotEmpty()) add(BackupPart.Font)
            if (cardsFile != null) add(BackupPart.Cards)
        }
        val manifest = AppBackupManifest(BuildConfig.VERSION_NAME, now.toString(), contents.map { it.key })
        val parts = AppBackupArchive.Parts(
            manifest = manifest,
            settings = SettingsBackupCodec.encode(settings),
            reminders = ReminderBackupCodec.encode(reminders),
            profilePhotos = listOfNotNull(photo),
            fonts = fonts,
            cards = cardsFile,
        )
        return Source(
            snapshot = snapshot,
            financeJson = FinanceBackupCodec.encode(snapshot, exportedAt = now),
            parts = parts,
            settingCount = settings.size,
            reminderCount = reminders.size,
            hasPhoto = photo != null,
            hasFont = fonts.isNotEmpty(),
            hasCards = cardsFile != null,
        )
    }

    /** Writes [source] to [out] as a zip, the pictures of its transactions with it. Returns how many pictures went in. */
    fun write(out: OutputStream, source: Source): Int =
        AppBackupArchive.write(out, source.financeJson, source.pictureNames, source.parts) { name -> container.finance.pictures?.file(name) }

    /** The font files in the app's fonts folder - the ones a restore would take back. */
    private fun fontFiles(): List<File> =
        File(context.filesDir, FONTS_DIR).listFiles { file -> file.isFile }.orEmpty()
            .filter { AppBackupArchive.isFontName(it.name) && it.length() <= AppBackupArchive.MAX_FONT_BYTES }
            .sortedBy { it.name }
            .take(AppBackupArchive.MAX_FILES)
}
