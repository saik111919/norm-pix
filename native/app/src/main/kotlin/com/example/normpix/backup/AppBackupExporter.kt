package com.example.normpix.backup

import android.content.Context
import com.example.normpix.AppContainer
import com.example.normpix.BuildConfig
import com.example.normpix.finance.data.FinanceBackupCodec
import com.example.normpix.finance.data.FinanceSnapshot
import com.example.normpix.reminders.ReminderBackupCodec
import com.example.normpix.settings.CustomFont
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
 * [CustomFont.file], and the preference its display name.
 */
internal val FONT_KEYS: Set<String> = setOf("custom_font_name")

/** The preference that says which interface font is in use; it is a setting, and may name the font the user picked ("Custom"). */
internal const val INTERFACE_FONT_KEY = "interface_font"

/** What [INTERFACE_FONT_KEY] holds when the font in use is the one the user picked (the name of `InterfaceFont.Custom`). */
internal const val CUSTOM_FONT_CHOICE = "Custom"

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

    /** The font the user picked, if there is one that a restore would take back: a whole font file, no bigger than the app takes. */
    private fun fontFiles(): List<File> {
        val font = CustomFont.file(context)
        val size = font.length()
        return if (font.isFile && size in 1L..AppBackupArchive.MAX_FONT_BYTES && CustomFont.hasFontHeader(font)) listOf(font) else emptyList()
    }
}
