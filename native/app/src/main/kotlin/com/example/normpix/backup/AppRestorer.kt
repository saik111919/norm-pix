package com.example.normpix.backup

import android.content.Context
import com.example.normpix.AppContainer
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.finance.ui.FinanceUndo
import com.example.normpix.settings.PrefValue
import com.example.normpix.settings.ProfilePhotos
import com.example.normpix.wallet.CardsTransfer
import com.example.normpix.wallet.StoredCard
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Puts the parts of an app backup the user chose in place of what the app has now (see [AppBackupPlan]). Each part replaces its
 * own and nothing else: the finance data, the reminders (their alarms and notifications are set again), the settings, the
 * profile and its picture, the font. The saved cards are the exception - they are only added to, never replaced.
 *
 * Everything but the cards can be taken back: [Restored.undo]. If a step fails half way, the steps before it are taken back and
 * the failure is thrown, so a restore is all of it or none of it (the cards, last, aside: they are added to the vault in one
 * write, and one that couldn't be saved is reported in [Restored.cardsFailed]).
 */
class AppRestorer(private val context: Context, private val container: AppContainer) {
    /** What a restore did. */
    class Restored internal constructor(
        val undo: Undo,
        /** How the saved cards in the backup were added; null when they weren't restored or couldn't be saved. */
        val cards: CardsTransfer.Merge?,
        val cardsFailed: Boolean,
        /** Preferences in the backup that were left as they were on this phone: of a kind the app can't use. */
        val skippedSettings: Int,
    )

    /** One step of a restore, with how to take it back and how to let go of what it kept aside. */
    internal class Step(val revert: suspend () -> Unit, val settle: suspend () -> Unit)

    /** A restore that can still be taken back, for as long as the user is offered to. */
    class Undo internal constructor(
        private val finance: FinanceController,
        private val financeUndo: FinanceUndo?,
        private val steps: List<Step>,
    ) {
        /** Whether there is anything to take back (the cards, alone, can't be). */
        val canUndo: Boolean get() = financeUndo != null || steps.isNotEmpty()

        /**
         * Puts everything back as it was. Only while the finance data is as the restore left it - otherwise taking it back
         * would also take away what came after - so it says false then, and changes nothing.
         */
        suspend fun undo(): Boolean {
            if (financeUndo != null && !finance.undo(financeUndo)) return false
            for (step in steps.asReversed()) step.revert()
            return true
        }

        /** The restore is final: what was kept aside for taking it back is let go. */
        suspend fun settle() {
            financeUndo?.let { finance.settle(it) }
            for (step in steps) step.settle()
        }
    }

    /**
     * Restores [wanted] (as far as [plan] has it). [cards] are the saved cards opened with the user's password, for
     * [BackupPart.Cards]. Throws when a step fails, after taking back the ones before it.
     */
    suspend fun restore(plan: AppBackupPlan, wanted: Set<BackupPart>, cards: List<StoredCard>?): Restored {
        val parts = wanted.intersect(plan.parts)
        val finance = container.finance
        val preferences = container.preferences
        val steps = ArrayList<Step>()
        var financeUndo: FinanceUndo? = null
        var skipped = 0
        var merge: CardsTransfer.Merge? = null
        var cardsFailed = false
        try {
            val snapshot = plan.snapshot
            if (BackupPart.Finance in parts && snapshot != null) {
                // The backup's pictures go in first, so the transactions that name them find them there.
                finance.pictures?.let { store -> for ((name, file) in plan.pictures) store.adopt(file, name) }
                financeUndo = finance.undoable {
                    this.restore(snapshot)
                    emptyList()
                }
            }

            val reminders = plan.reminders
            if (BackupPart.Reminders in parts && reminders != null) {
                container.reminders.ensureLoaded()
                val before = container.reminders.state.value.reminders
                container.reminders.replaceAll(reminders)
                steps += Step(revert = { container.reminders.replaceAll(before) }, settle = {})
            }

            val values = plan.settings
            val settingsOn = BackupPart.Settings in parts
            val profileOn = BackupPart.Profile in parts
            val fontOn = BackupPart.Font in parts
            if (values != null && (settingsOn || profileOn || fontOn)) {
                val photos = ProfilePhotos(context)
                var toWrite: Map<String, PrefValue> = values
                if (profileOn) {
                    for ((name, file) in plan.profilePhotos) photos.adopt(file, name)
                    // A photo the backup names but doesn't carry, and this phone doesn't have, can't be shown: leave the name out.
                    val named = (values[PROFILE_PHOTO_KEY] as? PrefValue.StringPref)?.value
                    if (named != null && photos.file(named) == null) toWrite = toWrite - PROFILE_PHOTO_KEY
                }
                val fonts = if (fontOn) swapFonts(plan.fonts) else null
                if (fonts != null) steps += Step(revert = { fonts.revert() }, settle = { fonts.settle() })

                val inScope: (String) -> Boolean = { key ->
                    when (key) {
                        in PROFILE_KEYS -> profileOn
                        in FONT_KEYS -> fontOn
                        else -> settingsOn
                    }
                }
                val before = preferences.exportAll()
                skipped = preferences.importAll(toWrite, inScope)
                // Photos the preferences no longer name are let go: the old one stays while the user can still undo.
                suspend fun tidyPhotos() {
                    if (profileOn) photos.keepOnly(preferences.state.value.profilePhoto.ifEmpty { null })
                }
                steps += Step(
                    revert = {
                        preferences.importAll(before, inScope)
                        tidyPhotos()
                    },
                    settle = { tidyPhotos() },
                )
            }

            val incoming = cards
            if (BackupPart.Cards in parts && incoming != null) {
                merge = container.cards.addAll(incoming)
                cardsFailed = merge == null
            }
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                financeUndo?.let { runCatching { finance.undo(it) } }
                for (step in steps.asReversed()) runCatching { step.revert() }
            }
            throw e
        }
        return Restored(Undo(finance, financeUndo, steps), merge, cardsFailed, skipped)
    }

    /**
     * Puts [fonts] in the fonts folder in place of what is there. The old folder is kept aside, in the cache, until the swap
     * is settled or taken back.
     */
    private suspend fun swapFonts(fonts: Map<String, File>): FontSwap = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, FONTS_DIR)
        val aside = File(context.cacheDir, "fonts-before-${UUID.randomUUID()}")
        if (dir.exists() && !dir.renameTo(aside)) {
            dir.copyRecursively(aside)
            dir.deleteRecursively()
        }
        try {
            dir.mkdirs()
            for ((name, file) in fonts) file.copyTo(File(dir, name), overwrite = true)
        } catch (e: Exception) {
            dir.deleteRecursively()
            if (aside.exists()) aside.renameTo(dir)
            throw e
        }
        FontSwap(dir, aside)
    }

    private class FontSwap(private val dir: File, private val aside: File) {
        suspend fun revert() = withContext(Dispatchers.IO) {
            dir.deleteRecursively()
            if (aside.exists() && !aside.renameTo(dir)) {
                aside.copyRecursively(dir)
                aside.deleteRecursively()
            }
        }

        suspend fun settle() = withContext(Dispatchers.IO) {
            aside.deleteRecursively()
        }
    }
}
