package com.example.normpix.settings.ui

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.normpix.AppContainer
import com.example.normpix.backup.AppBackupArchive
import com.example.normpix.backup.AppBackupExporter
import com.example.normpix.backup.AppBackupPlan
import com.example.normpix.backup.AppBackupReader
import com.example.normpix.backup.AppRestorer
import com.example.normpix.backup.BackupPart
import com.example.normpix.finance.ui.SwitchRow
import com.example.normpix.ui.components.AppHaptics
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.StoredCard
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val appBackupDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val madeOnFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

/** At most this many problems are listed when a file can't be used. */
private const val PROBLEMS_SHOWN = 5

/** Says that a file can't be used and why ([errors], a few of them), and that nothing was changed. */
internal suspend fun AppOverlays.cantUse(errors: List<String>) {
    val lines = buildList {
        add("Nothing was changed.")
        add("")
        addAll(errors.take(PROBLEMS_SHOWN))
        if (errors.size > PROBLEMS_SHOWN) add("and ${errors.size - PROBLEMS_SHOWN} more problems")
    }
    alert("Can't use this file", lines.joinToString("\n"), icon = Icons.Outlined.ErrorOutline)
}

/**
 * Backing up everything the app keeps, and putting it back (Settings > Data & backup): the finance data with its pictures, the
 * settings, the reminders, the profile, the font and - only if the user says so - the saved cards, locked with a password. The
 * file is the app backup zip of [AppBackupArchive]; [AppBackupExporter] reads what goes in and [AppRestorer] puts it back.
 *
 * Restoring shows what the backup holds and lets the user pick which parts to restore (all, to begin with), and opens the
 * saved cards - which needs their password - before anything is changed, so that a restore is never left half done for want of it.
 */
internal class AppBackupFlows(
    context: Context,
    private val container: AppContainer,
    private val overlays: AppOverlays,
    private val snackbars: AppSnackbars,
    private val files: BackupFiles,
    private val scope: CoroutineScope,
    private val haptics: AppHaptics,
) {
    private val exporter = AppBackupExporter(context, container)
    private val restorer = AppRestorer(context, container)
    private val cards = CardsTransferFlows(container, overlays, snackbars, files)

    /** Asks about the cards, reads everything and saves it as `normpix-app-backup-<date>.zip` where the user chooses. */
    suspend fun export() {
        val now = LocalDateTime.now()
        val withCards = cards.forBackup(now) ?: return
        val locked = withCards as? CardsForBackup.Locked
        val source = exporter.gather(now, locked?.file)
        var pictures = 0
        val saved = try {
            files.saveStreaming("normpix-app-backup-${appBackupDate.format(now)}.zip", "application/zip") { out ->
                pictures = withContext(Dispatchers.IO) { exporter.write(out, source) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            snackbars.show("Couldn't save the backup there. Try another location.", SnackbarKind.Error)
            return
        }
        if (!saved) return
        haptics.success()
        val contents = buildList {
            add(if (pictures > 0) "finance data with ${count(pictures, "picture")}" else "finance data")
            add("settings")
            add(count(source.reminderCount, "reminder"))
            if (source.hasPhoto) add("your profile picture")
            if (source.hasFont) add("your font")
            if (locked != null) add("${count(locked.count, "card")}, locked with your password")
        }
        snackbars.show("App backup saved: ${contents.joinWords()}")
    }

    /**
     * Restores from an app backup that was read: [financeText] and [pictures] are its finance part, [app] the rest. Says so when
     * the backup is damaged or holds nothing to restore. Otherwise the user picks what to restore, and what they pick is restored
     * with an Undo for a few seconds.
     */
    suspend fun restore(financeText: String?, pictures: Map<String, File>, app: AppBackupArchive.App) {
        val plan = when (val parsed = AppBackupReader.parse(financeText, pictures, app)) {
            is AppBackupReader.Parsed.Invalid -> {
                overlays.cantUse(parsed.errors)
                return
            }
            is AppBackupReader.Parsed.Ok -> parsed.plan
        }
        if (plan.parts.isEmpty()) {
            overlays.cantUse(listOf("There is nothing in this backup that can be restored."))
            return
        }
        container.reminders.ensureLoaded()
        val prefs = container.preferences.state.value
        val offer = RestoreOffer(
            plan = plan,
            financeNow = describe(container.finance.repository.snapshot()),
            remindersNow = container.reminders.state.value.reminders.size,
            cardsOn = prefs.walletCardsEnabled,
            cvvOn = prefs.cardCvvEnabled,
            sampleData = prefs.dummyDataEnabled,
        )
        val chosen = overlays.sheet<Set<BackupPart>>(expandable = true) { dismiss -> RestoreChoiceSheet(offer, onDone = dismiss) } ?: return
        if (chosen.isEmpty()) return

        // The saved cards are opened before anything is changed: without their password the user can still go back.
        var opened: List<StoredCard>? = null
        if (BackupPart.Cards in chosen) {
            val file = plan.cardsFile
            val unlocked = if (file != null) cards.unlock(file, alone = false) else null
            if (unlocked != null) {
                opened = unlocked.cards
            } else {
                if ((chosen - BackupPart.Cards).isEmpty()) return
                val goOn = overlays.confirm(
                    title = "Restore without the saved cards?",
                    message = "The saved cards weren't opened, so they won't be restored. Everything else you picked will be.",
                    confirmLabel = "Restore the rest",
                    destructive = true,
                    icon = Icons.Rounded.Restore,
                )
                if (!goOn) return
            }
        }
        val wanted = if (opened == null) chosen - BackupPart.Cards else chosen

        val restored = try {
            restorer.restore(plan, wanted, opened)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            overlays.alert(
                "The backup couldn't be restored",
                "Nothing was changed.",
                icon = Icons.Outlined.ErrorOutline,
            )
            return
        }
        haptics.success()
        tell(plan, wanted, restored, offer.cardsOn)
    }

    /** Says what was restored, in a snackbar with Undo for as long as anything of it can be taken back. */
    private fun tell(plan: AppBackupPlan, wanted: Set<BackupPart>, restored: AppRestorer.Restored, cardsOn: Boolean) {
        val done = buildList {
            if (BackupPart.Finance in wanted) add("finance data")
            if (BackupPart.Settings in wanted) add("settings")
            if (BackupPart.Reminders in wanted) add(count(plan.reminders?.size ?: 0, "reminder"))
            if (BackupPart.Profile in wanted) add("profile")
            if (BackupPart.Font in wanted) add("font")
        }
        val merge = restored.cards
        val notes = buildList {
            if (BackupPart.Cards in wanted) {
                when {
                    restored.cardsFailed -> add("The saved cards couldn't be saved")
                    merge == null -> add("The saved cards were left out")
                    merge.refused -> add("The saved cards were left out: Store cards is off")
                    merge.added.isEmpty() -> add("Every saved card was already in Wallet")
                    merge.alreadyThere == 0 -> add("${count(merge.added.size, "card")} added to Wallet")
                    else -> add("${count(merge.added.size, "card")} added. ${merge.alreadyThere} already in Wallet, kept as they were")
                }
            } else if (BackupPart.Cards in plan.parts && !cardsOn) {
                add("The saved cards were left out: Store cards is off")
            }
            val settingsLeft = restored.skippedSettings + (if (BackupPart.Settings in wanted) plan.skippedSettings else 0)
            if (settingsLeft > 0) add("${count(settingsLeft, "setting")} couldn't be used and stayed as they were")
            if (BackupPart.Reminders in wanted && plan.skippedReminders > 0) add("${count(plan.skippedReminders, "reminder")} couldn't be read")
        }
        val message = (listOfNotNull(if (done.isEmpty()) null else "Backup restored: ${done.joinWords()}") + notes)
            .joinToString(". ")
            .ifEmpty { "Backup restored" }
        val cardsAdded = merge != null && merge.added.isNotEmpty()
        offerUndo(message, restored, cardsAdded, warn = restored.cardsFailed)
    }

    private fun offerUndo(message: String, restored: AppRestorer.Restored, cardsAdded: Boolean, warn: Boolean) {
        val undo = restored.undo
        if (!undo.canUndo) {
            snackbars.show(message, if (warn) SnackbarKind.Warning else SnackbarKind.Done)
            return
        }
        snackbars.showUndoable(
            message,
            undo = {
                scope.launch {
                    val back = try {
                        undo.undo()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                    if (back) {
                        snackbars.show(if (cardsAdded) "Undone. The cards that were added stay in Wallet." else "Undone")
                    } else {
                        letGo(undo)
                        snackbars.show("Couldn't undo, because something else changed since.", SnackbarKind.Warning)
                    }
                }
            },
            settle = { scope.launch { letGo(undo) } },
        )
    }

    /** Makes a restore final, whatever goes wrong doing it: what it kept aside is not worth a message. */
    private suspend fun letGo(undo: AppRestorer.Undo) {
        try {
            undo.settle()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}

/** What the restore sheet shows about a backup ([plan]) and the phone it would be restored to. */
internal class RestoreOffer(
    val plan: AppBackupPlan,
    /** The finance data here now, in words (see [describe]). */
    val financeNow: String,
    val remindersNow: Int,
    /** Wallet keeps cards on this phone (Settings > Wallet & Cards > Store cards). */
    val cardsOn: Boolean,
    /** Wallet keeps a card's CVV on this phone (Settings > Wallet & Cards > Save CVV). */
    val cvvOn: Boolean,
    /** Sample data is showing: the finance data, reminders and cards here are the sample set. */
    val sampleData: Boolean,
) {
    /** "Made 5 Oct 2026, 14:30, by NormPix 1.4.0", as much of it as the backup says. */
    val madeBy: String
        get() {
            val manifest = plan.manifest
            val at = manifest.exportedAt?.let { runCatching { LocalDateTime.parse(it).format(madeOnFormat) }.getOrNull() }
            val by = manifest.appVersion.takeIf { it.isNotBlank() }?.let { "NormPix $it" }
            return when {
                at != null && by != null -> "Made $at, by $by"
                at != null -> "Made $at"
                by != null -> "Made by $by"
                else -> "What this backup holds"
            }
        }
}

/**
 * The sheet that asks which parts of an app backup to restore: one switch for each part the backup has (all on to begin with),
 * with what each replaces. The profile and the font are one switch. Calls [onDone] with the parts picked, or null if closed.
 * Saved cards are only added to, never replaced, and are not offered while Wallet keeps no cards.
 */
@Composable
internal fun RestoreChoiceSheet(offer: RestoreOffer, onDone: (Set<BackupPart>?) -> Unit) {
    val plan = offer.plan
    val parts = plan.parts
    val haptics = rememberAppHaptics()
    var finance by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf(true) }
    var reminders by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf(true) }
    var cards by remember { mutableStateOf(true) }

    val hasProfile = BackupPart.Profile in parts
    val hasFont = BackupPart.Font in parts
    val chosen = buildSet<BackupPart> {
        if (BackupPart.Finance in parts && finance) add(BackupPart.Finance)
        if (BackupPart.Settings in parts && settings) add(BackupPart.Settings)
        if (BackupPart.Reminders in parts && reminders) add(BackupPart.Reminders)
        if (hasProfile && profile) add(BackupPart.Profile)
        if (hasFont && profile) add(BackupPart.Font)
        if (BackupPart.Cards in parts && cards && offer.cardsOn) add(BackupPart.Cards)
    }
    val scheme = MaterialTheme.colorScheme

    EditorSheet(
        title = "Restore this backup?",
        subtitle = offer.madeBy,
        saveLabel = "Restore",
        onSave = { onDone(chosen) },
        saving = chosen.isEmpty(),
        saveColors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
    ) {
        Text(
            "Each part you leave on replaces what is on this phone now. You can undo it for a few seconds; after that, " +
                "back up first if you might want what is here now.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        if (offer.sampleData) {
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                "Sample data is on, so the finance data, reminders and cards here are the sample set. Settings, profile and " +
                    "font are your own.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(AppSpacing.md))

        val snapshot = plan.snapshot
        if (snapshot != null) {
            val withPictures = snapshot.transactions.count { it.picture != null && it.picture in plan.pictures }
            val pictures = if (withPictures > 0) ", with ${count(withPictures, "picture")}" else ""
            SwitchRow(
                title = "Finance data",
                checked = finance,
                onChange = {
                    haptics.selection()
                    finance = it
                },
                subtitle = "${describe(snapshot)}$pictures. Replaces ${offer.financeNow}.",
            )
        }
        if (BackupPart.Settings in parts) {
            val left = plan.skippedSettings
            SwitchRow(
                title = "Settings",
                checked = settings,
                onChange = {
                    haptics.selection()
                    settings = it
                },
                subtitle = "Theme, layout, updates and every other choice in Settings. Your lock and card settings stay as they " +
                    "are on this phone." + (if (left > 0) " ${count(left, "setting")} in the file can't be read and stay as they are." else ""),
            )
        }
        if (plan.reminders != null) {
            SwitchRow(
                title = "Reminders",
                checked = reminders,
                onChange = {
                    haptics.selection()
                    reminders = it
                },
                subtitle = "${count(plan.reminders.size, "reminder")} replace the ${offer.remindersNow} here now. " +
                    "Their alarms and notifications are set again.",
            )
        }
        if (hasProfile || hasFont) {
            val what = listOfNotNull(
                if (hasProfile) "your name, emoji and picture" else null,
                if (hasFont) "the font you picked" else null,
            ).joinWords()
            SwitchRow(
                title = if (hasProfile && hasFont) "Profile and font" else if (hasProfile) "Profile" else "Font",
                checked = profile,
                onChange = {
                    haptics.selection()
                    profile = it
                },
                subtitle = "Replaces $what.",
            )
        }
        if (BackupPart.Cards in parts) {
            SwitchRow(
                title = "Saved cards",
                checked = cards && offer.cardsOn,
                enabled = offer.cardsOn,
                onChange = {
                    haptics.selection()
                    cards = it
                },
                subtitle = when {
                    !offer.cardsOn -> "Store cards is off on this phone (Settings > Wallet & Cards), so these are left out."
                    offer.cvvOn -> "Added to the cards you have; none are replaced. You'll be asked for the password they are locked with."
                    else -> "Added to the cards you have; none are replaced. Save CVV is off on this phone, so they arrive without a " +
                        "CVV. You'll be asked for the password they are locked with."
                },
            )
        }
    }
}

private fun count(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"

/** "a", "a and b", "a, b and c". */
private fun List<String>.joinWords(): String = when (size) {
    0 -> ""
    1 -> first()
    else -> dropLast(1).joinToString(", ") + " and " + last()
}
