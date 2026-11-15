package com.example.normpix.backup

import android.content.Context
import com.example.normpix.AppContainer
import com.example.normpix.settings.AppPreferences
import com.example.normpix.wallet.CardsState
import com.example.normpix.wallet.CardsTransfer
import com.example.normpix.wallet.KeystoreKeyProvider
import com.example.normpix.wallet.StoredCard
import com.example.normpix.wallet.VaultKeyProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/*
 * Automatic backups (owner, 2026-11-14: "Set a one time password and your suggestion also user will decide"): NormPix makes the
 * app backup - the same zip as Settings > Data & backup > Everything - by itself, on a schedule, locks it with a password of its
 * own exactly as a locked backup is locked ([LockedFile]), and writes it into a folder the user picked once. It is restored the
 * way any locked backup is: open the file, type the password.
 *
 * The password is the one secret NormPix keeps for this. It is set once (asked twice) and kept only sealed by a key of the Android
 * Keystore ([SealedSecret], `noBackupFilesDir/auto_backup.key`), never in the preferences or a backup, never logged. Saved cards
 * are left out unless the user turns "Include saved cards" on; then they go in as a cards file locked with the same password, as
 * in a locked manual backup. Nothing goes over the network: a cloud folder's own app may upload the (locked) file.
 */

/** What stopped an automatic backup, kept (by name) until the next one goes well, and shown in Settings and the notifications. */
enum class AutoBackupProblem(
    /** A few words for a heading: what to do about it. */
    val title: String,
    /** What happened, in a sentence. */
    val message: String,
    /** What happened, in a few words: a banner's one line. */
    val short: String,
) {
    FolderGone("Choose the folder again", "NormPix can't write to the backup folder any more.", "Can't write to the folder"),
    PasswordMissing("Set the password again", "The automatic backup password can't be opened on this phone.", "Its password can't be opened"),
    OutOfSpace("Not enough space", "There wasn't room in the backup folder for the backup.", "No room in the folder"),
    /** The backup was made, without the saved cards: they couldn't be read. */
    CardsLeftOut(
        "Saved cards were left out",
        "The last automatic backup was made without your saved cards: they couldn't be read.",
        "Made without your saved cards",
    ),
    /** Never in practice: the backup only ever reads the user's own data. Here so the guard has something to say. */
    SampleData("Skipped while sample data is on", "Only your own data is backed up, never the sample set.", "Sample data was showing"),
    Failed("Automatic backup didn't finish", "Something went wrong while writing it, so nothing was kept of it.", "The last one didn't finish"),
}

/** What an automatic backup came to. */
sealed interface AutoBackupOutcome {
    /** Written as [fileName]; [removed] older automatic backups deleted to keep the newest [AutoBackupNames.KEEP]. */
    data class Saved(val fileName: String, val removed: Int, val cardsLeftOut: Boolean) : AutoBackupOutcome

    /** Not written; [problem] says why, and was recorded. */
    data class Failed(val problem: AutoBackupProblem) : AutoBackupOutcome

    /** Automatic backups are off: nothing was done. */
    data object Off : AutoBackupOutcome
}

/**
 * The names automatic backups are written under - `NormPix-auto-backup-2026-11-14.zip.locked`, then `-2`, `-3` for more on one
 * day - and the only names [AutoBackupService] ever deletes: a file in the folder that isn't named exactly so is never touched.
 */
object AutoBackupNames {
    /** How many automatic backups are kept in the folder: the newest ones (owner, 2026-11-14: "delete old backups if it's more than 5"). */
    const val KEEP = 5

    /** The most backups one day can have names for. */
    private const val MAX_PER_DAY = 999

    private val PATTERN = Regex("""NormPix-auto-backup-(\d{4}-\d{2}-\d{2})(?:-([1-9]\d{0,2}))?\.zip\.locked""")

    /** The [n]th name for a backup made on [date] (the first has no number). */
    fun nameFor(date: LocalDate, n: Int = 1): String {
        require(n in 1..MAX_PER_DAY)
        return "NormPix-auto-backup-$date" + (if (n > 1) "-$n" else "") + ".zip.locked"
    }

    /** Whether [name] is exactly an automatic backup's name. */
    fun isAutoBackupName(name: String): Boolean = PATTERN.matches(name)

    /** The first name for [date] none of [taken] has (case aside: some folders don't tell case apart), or null if the day is full. */
    fun freeName(date: LocalDate, taken: Collection<String>): String? {
        val used = taken.mapTo(HashSet()) { it.lowercase() }
        return (1..MAX_PER_DAY).asSequence().map { nameFor(date, it) }.firstOrNull { it.lowercase() !in used }
    }

    /** What orders automatic backups, newest last: the day in the name, then its number. Null for any other name. */
    internal fun orderOf(name: String): Pair<String, Int>? =
        PATTERN.matchEntire(name)?.let { it.groupValues[1] to (it.groupValues[2].toIntOrNull() ?: 1) }
}

/** What an automatic backup is made of: the user's own data, read by [AppBackupExporter]. An interface so a test can stand in. */
interface AutoBackupData {
    /** True for the in-memory sample set: never backed up. */
    val isSampleData: Boolean

    /** Everything an app backup carries, as of [now]; [cardsFile] is the saved cards already locked, or null to leave them out. */
    suspend fun gather(now: LocalDateTime, cardsFile: String?): AppBackupExporter.Source

    /** Writes [source] into [out] as the backup zip. */
    fun write(out: OutputStream, source: AppBackupExporter.Source)

    /** The saved cards; empty when there are none or NormPix keeps none (Store cards off); null when they can't be read. */
    suspend fun cards(): List<StoredCard>?
}

/** The data of [container] - the app's own, the real one (see [AppContainer.isSampleData]) - as an automatic backup reads it. */
class ContainerBackupData(context: Context, private val container: AppContainer) : AutoBackupData {
    private val exporter = AppBackupExporter(context.applicationContext, container)

    override val isSampleData: Boolean get() = container.isSampleData

    override suspend fun gather(now: LocalDateTime, cardsFile: String?): AppBackupExporter.Source = exporter.gather(now, cardsFile)

    override fun write(out: OutputStream, source: AppBackupExporter.Source) {
        exporter.write(out, source)
    }

    override suspend fun cards(): List<StoredCard>? {
        if (!container.preferences.state.value.walletCardsEnabled) return emptyList()
        val cards = container.cards
        if (cards.state.value !is CardsState.Ready) cards.load()
        return (cards.state.value as? CardsState.Ready)?.cards
    }
}

/**
 * Automatic backups on this phone: turning them on and off, the folder, the password, and making one ([backUpNow] - the job and
 * "Back up now" both). One per app, shared by the sample set's container, so it is always the user's own data that is read
 * ([data] is bound to the real container when the app is built).
 */
class AutoBackupService(
    private val preferences: AppPreferences,
    private val secret: SealedSecret,
    private val folders: BackupFolders,
    private val data: () -> AutoBackupData,
    private val clock: Clock = Clock.systemDefaultZone(),
    /** PBKDF2 rounds for the backup and its cards file: [LockedFile.ITERATIONS], as a manual locked backup; fewer only in tests. */
    private val iterations: Int = LockedFile.ITERATIONS,
) {
    /** One backup at a time, and no turning off or changing the password in the middle of one. */
    private val running = Mutex()

    /** Whether automatic backups are on: switched on, with a folder. */
    val isOn: Boolean get() = preferences.state.value.let { it.autoBackupEnabled && it.autoBackupFolder.isNotEmpty() }

    /** Whether a password is kept (whether or not it can still be opened). */
    val hasPassword: Boolean get() = secret.exists

    /** The name of the folder [tree], as a file manager shows it. */
    suspend fun folderName(tree: String): String = withContext(Dispatchers.IO) { folders.nameOf(tree) }

    /**
     * Turns automatic backups on into the folder [tree] (called [folderName]), locked with [password] - at least
     * [LockedFile.MIN_PASSWORD] characters. Keeps NormPix's access to the folder and seals the password, then switches them on; if
     * either can't be done nothing is on and the exception says which ([SecurityException]: the folder; [SealedSecretException]:
     * the password). [password] is zeroed here, whatever happens.
     */
    suspend fun turnOn(tree: String, folderName: String, password: CharArray) {
        try {
            require(password.size >= LockedFile.MIN_PASSWORD) { "password too short" }
            running.withLock {
                withContext(Dispatchers.IO) {
                    folders.keep(tree)
                    try {
                        seal(password)
                    } catch (e: Exception) {
                        folders.release(tree)
                        throw e
                    }
                    val before = preferences.state.value.autoBackupFolder
                    preferences.turnOnAutoBackup(tree, folderName)
                    if (before.isNotEmpty() && before != tree) folders.release(before)
                }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    /** Automatic backups go to the folder [tree] (called [folderName]) from now on; the access to the one before is given back. */
    suspend fun changeFolder(tree: String, folderName: String) = running.withLock {
        withContext(Dispatchers.IO) {
            val before = preferences.state.value.autoBackupFolder
            folders.keep(tree)
            preferences.setAutoBackupFolder(tree, folderName)
            if (before.isNotEmpty() && before != tree) folders.release(before)
        }
    }

    /** Seals [password] in place of the one kept: the next backup is locked with it. Zeroed here, whatever happens. */
    suspend fun changePassword(password: CharArray) {
        try {
            require(password.size >= LockedFile.MIN_PASSWORD) { "password too short" }
            running.withLock {
                withContext(Dispatchers.IO) {
                    seal(password)
                    if (preferences.state.value.autoBackupProblem == AutoBackupProblem.PasswordMissing) preferences.clearAutoBackupProblem()
                }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    /**
     * Turns automatic backups off: the sealed password is deleted, the access to the folder given back and the choices about it
     * forgotten. The backups already made stay where they are. Waits for a backup under way to end first. True when no password
     * is left on the phone.
     */
    suspend fun turnOff(): Boolean = running.withLock {
        withContext(Dispatchers.IO) {
            val gone = secret.delete()
            val tree = preferences.state.value.autoBackupFolder
            if (tree.isNotEmpty()) folders.release(tree)
            preferences.turnOffAutoBackup()
            gone
        }
    }

    /** A password left behind with automatic backups off (the app stopped half way through turning them off) is deleted. */
    suspend fun tidy() = running.withLock {
        withContext(Dispatchers.IO) {
            if (!preferences.state.value.autoBackupEnabled && secret.exists) secret.delete()
        }
    }

    /**
     * Makes an automatic backup now, if they are on: the scheduled job and "Back up now" both come here. What came of it is
     * recorded - the time and the file name, or what went wrong - and returned.
     */
    suspend fun backUpNow(): AutoBackupOutcome = running.withLock { withContext(Dispatchers.IO) { backUp() } }

    private fun seal(password: CharArray) {
        val bytes = utf8Bytes(password)
        try {
            secret.seal(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private suspend fun backUp(): AutoBackupOutcome {
        val prefs = preferences.state.value
        if (!prefs.autoBackupEnabled || prefs.autoBackupFolder.isEmpty()) return AutoBackupOutcome.Off
        val data = data()
        // Only ever the user's own data: the sample set is not theirs to keep (and is not what this reads; this is the guard).
        if (data.isSampleData) return failed(AutoBackupProblem.SampleData)
        val folder = folders.open(prefs.autoBackupFolder) ?: return failed(AutoBackupProblem.FolderGone)
        // Listed first: a folder that can't be reached is found before the slow part.
        val existing = try {
            folder.list()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failed(problemOf(e))
        }

        // The password, opened only for as long as it takes to draw the keys from it.
        val password = try {
            val bytes = secret.open()
            try {
                utf8Chars(bytes)
            } finally {
                bytes.fill(0)
            }
        } catch (e: Exception) {
            return failed(AutoBackupProblem.PasswordMissing)
        }
        val at = LocalDateTime.now(clock)
        var cardsLeftOut = false
        val cardsFile: String?
        val locker: LockedFile.Locker
        try {
            cardsFile = if (prefs.autoBackupCards) {
                val cards = data.cards()
                when {
                    cards == null -> {
                        cardsLeftOut = true
                        null
                    }
                    cards.isEmpty() -> null
                    // Locked with the backup's password, as the cards in a locked manual backup are.
                    else -> CardsTransfer.encode(cards, password, exportedAt = at, iterations = iterations)
                }
            } else {
                null
            }
            locker = LockedFile.locker(password, iterations = iterations)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failed(AutoBackupProblem.Failed)
        } finally {
            password.fill('\u0000')
        }

        val source = try {
            data.gather(at, cardsFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failed(AutoBackupProblem.Failed)
        }
        val name = AutoBackupNames.freeName(at.toLocalDate(), existing.map { it.name }) ?: return failed(AutoBackupProblem.Failed)
        val entry = try {
            folder.create(name, LockedFile.MIME_TYPE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failed(problemOf(e))
        }
        // Straight into the file, locked on the way: the zip is never anywhere unlocked, not even in the cache.
        try {
            folder.write(entry).use { raw ->
                val buffered = raw.buffered(WRITE_BUFFER)
                locker.lock(buffered).use { sealed -> data.write(sealed, source) }
                buffered.flush()
            }
        } catch (e: Throwable) {
            // Half a backup is no backup: it goes.
            runCatching { folder.delete(entry) }
            if (e is CancellationException || e !is Exception) throw e
            return failed(problemOf(e))
        }
        val removed = keepNewest(folder, entry)
        preferences.recordAutoBackup(clock.millis(), entry.name, if (cardsLeftOut) AutoBackupProblem.CardsLeftOut else null)
        return AutoBackupOutcome.Saved(entry.name, removed, cardsLeftOut)
    }

    /**
     * Deletes the automatic backups in [folder] past the newest [AutoBackupNames.KEEP]: only files named exactly as one, never
     * [written] (the one just made: a clock set back could give it an older name than the rest). How many went.
     */
    private fun keepNewest(folder: BackupFolder, written: BackupFolder.Entry): Int {
        val ours = try {
            folder.list()
        } catch (e: Exception) {
            return 0
        }.filter { it.id != written.id && AutoBackupNames.isAutoBackupName(it.name) }
        val older = ours
            .sortedWith(compareByDescending<BackupFolder.Entry> { AutoBackupNames.orderOf(it.name)!!.first }.thenByDescending { AutoBackupNames.orderOf(it.name)!!.second })
            .drop(AutoBackupNames.KEEP - 1)
        return older.count { folder.delete(it) }
    }

    private fun failed(problem: AutoBackupProblem): AutoBackupOutcome.Failed {
        preferences.recordAutoBackupProblem(problem, clock.millis())
        return AutoBackupOutcome.Failed(problem)
    }

    companion object {
        /** The Keystore key the password is sealed with: its own, not bound to the user unlocking, so the job can open it. */
        const val KEY_ALIAS = "normpix_auto_backup_key_v1"

        /** The sealed password, in `noBackupFilesDir`. */
        const val FILE_NAME = "auto_backup.key"

        private const val WRITE_BUFFER = 64 * 1024

        /** The app's: the password sealed by the Keystore, folders through the system picker, [data] the real container's. */
        fun forApp(
            context: Context,
            preferences: AppPreferences,
            keys: VaultKeyProvider = KeystoreKeyProvider(alias = KEY_ALIAS),
            data: () -> AutoBackupData,
        ): AutoBackupService {
            val app = context.applicationContext
            return AutoBackupService(
                preferences = preferences,
                secret = SealedSecret(File(app.noBackupFilesDir, FILE_NAME), keys),
                folders = DocumentTreeFolders(app),
                data = data,
            )
        }

        /** What [e], thrown reaching or writing the folder, says went wrong. */
        internal fun problemOf(e: Throwable): AutoBackupProblem = when {
            generateSequence(e) { it.cause }.any { it.message.orEmpty().let { m -> "ENOSPC" in m || "No space left" in m } } ->
                AutoBackupProblem.OutOfSpace
            e is SecurityException || e is FileNotFoundException || e is IllegalArgumentException -> AutoBackupProblem.FolderGone
            else -> AutoBackupProblem.Failed
        }
    }
}
