package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.normpix.backup.AppBackupArchive
import com.example.normpix.finance.data.FinanceBackupArchive
import com.example.normpix.finance.data.FinanceBackupCodec
import com.example.normpix.finance.data.FinanceSnapshot
import com.example.normpix.finance.ui.LocalFinance
import com.example.normpix.finance.ui.offerUndo
import com.example.normpix.finance.ui.rememberFinanceActions
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppLoadingIndicator
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val fileDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val csvDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * Settings > Data: back up, export, restore and erase finance data.
 *
 * Everything is local and explicit: files are only written where the user
 * chooses in the system dialog, a backup is fully validated before anything
 * changes, and replacing or erasing data always asks first. The finance backups hold
 * the finance data alone; the backup of everything (see [AppBackupFlows]) also holds the
 * settings, reminders, profile and font, and the saved cards only when the user says so.
 */
@Composable
fun DataSettingsPage() {
    val navigator = LocalNavigator.current
    val container = LocalAppContainer.current
    val prefs by container.preferences.state.collectAsState()
    val finance = LocalFinance.current
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val scope = LocalAppScope.current
    val haptics = rememberAppHaptics()
    val financeActions = rememberFinanceActions()
    val files = rememberBackupFiles()
    val appContext = LocalContext.current.applicationContext
    val appBackup = remember(appContext, container, overlays, snackbars, files, scope, haptics) {
        AppBackupFlows(appContext, container, overlays, snackbars, files, scope, haptics)
    }
    /** The operation in progress, if any (disables the other rows). */
    var busy by remember { mutableStateOf<String?>(null) }
    /** If restore was requested while sample data was active, continues after sample data turns off. */
    var pendingRestoreAfterSampleDataOff by rememberSaveable { mutableStateOf(false) }

    suspend fun problem(errors: List<String>) = overlays.cantUse(errors)

    fun run(id: String, op: suspend () -> Unit) {
        if (busy != null) return
        busy = id
        scope.launch {
            try {
                op()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                snackbars.show("Something went wrong. Nothing was changed.", SnackbarKind.Error)
            } finally {
                busy = null
            }
        }
    }

    fun exportAppBackup() = run("app") { appBackup.export() }

    // Works every figure out again from data that is checked first (see runLedgerCheck), under a loading sheet.
    fun check() = run("check") { runLedgerCheck(finance, overlays, snackbars, financeActions, scope) }

    fun exportBackup() = run("backup") {
        val now = LocalDateTime.now()
        val snapshot = finance.repository.snapshot()
        val text = FinanceBackupCodec.encode(snapshot, exportedAt = now)
        val saved = try {
            files.save("normpix-backup-${fileDate.format(now)}.json", "application/json", text.toByteArray(Charsets.UTF_8))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            snackbars.show("Couldn't save the backup there. Try another location.", SnackbarKind.Error)
            return@run
        }
        if (saved) {
            haptics.success()
            val prefix = if (prefs.dummyDataEnabled) "Sample data backup saved" else "Backup saved"
            snackbars.show("$prefix: ${describe(snapshot)}")
        }
    }

    fun exportFullBackup() = run("full") {
        val now = LocalDateTime.now()
        val snapshot = finance.repository.snapshot()
        val text = FinanceBackupCodec.encode(snapshot, exportedAt = now)
        val names = snapshot.transactions.mapNotNull { it.picture }
        val store = finance.pictures
        var pictures = 0
        val saved = try {
            files.saveStreaming("normpix-backup-${fileDate.format(now)}.zip", "application/zip") { out ->
                pictures = withContext(Dispatchers.IO) { FinanceBackupArchive.write(out, text, names) { store?.file(it) } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            snackbars.show("Couldn't save the backup there. Try another location.", SnackbarKind.Error)
            return@run
        }
        if (saved) {
            haptics.success()
            val prefix = if (prefs.dummyDataEnabled) "Sample data backup saved" else "Backup saved"
            snackbars.show("$prefix with ${if (pictures == 1) "1 picture" else "$pictures pictures"}: ${describe(snapshot)}")
        }
    }

    fun exportCsv() = run("csv") {
        val now = LocalDateTime.now()
        val snapshot = finance.repository.snapshot()
        val csv = FinanceBackupCodec.encodeCsv(snapshot) { csvDate.format(it) }
        val saved = try {
            files.save("normpix-transactions-${fileDate.format(now)}.csv", "text/csv", csv.toByteArray(Charsets.UTF_8))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            snackbars.show("Couldn't save the file there. Try another location.", SnackbarKind.Error)
            return@run
        }
        if (saved) {
            haptics.success()
            val n = snapshot.transactions.size
            val countWord = if (n == 1) "transaction" else "transactions"
            val prefix = if (prefs.dummyDataEnabled) "Sample $countWord" else countWord
            snackbars.show("$n $prefix exported")
        }
    }

    fun restore() = run("restore") {
        if (prefs.dummyDataEnabled) {
            val turnOff = overlays.confirm(
                title = "Sample data is on",
                message = "A backup restores your actual data, not the sample set, which is discarded when turned off. Turn off sample data to restore your backup?",
                confirmLabel = "Turn off sample data",
                icon = Icons.Rounded.Restore,
            )
            if (turnOff) {
                pendingRestoreAfterSampleDataOff = true
                container.preferences.setDummyDataEnabled(false)
            }
            return@run
        }
        // A backup in a .zip brings its pictures, and an app backup its profile picture and font along; they wait in a folder of
        // their own until the restore is confirmed. With nowhere to keep pictures (no picture store) the folder is a temporary one.
        val store = finance.pictures
        val staging = store?.newRestoreFolder() ?: kotlin.io.path.createTempDirectory("restore").toFile()
        try {
            val read = try {
                files.openStreaming { input ->
                    // Picked: read under a loading sheet, since a backup with pictures can take a while to unpack.
                    overlays.loading("Reading the backup", "Checking the file before anything is changed.") {
                        withContext(Dispatchers.IO) { readBackup(input, staging, keepPictures = store != null) }
                    }
                } ?: return@run
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackupTooLargeException) {
                problem(listOf("This file is too large to be a NormPix backup."))
                return@run
            } catch (e: BackupRejectedException) {
                problem(listOf(e.reason))
                return@run
            } catch (_: Exception) {
                problem(listOf("The file couldn't be read."))
                return@run
            }
            val app = read.app
            if (app != null) {
                // A backup of everything: what to restore is the user's to pick (see AppBackupFlows.restore).
                appBackup.restore(read.text, read.pictures, app)
                return@run
            }
            val text = read.text
            if (text == null) {
                problem(listOf("This file isn't a NormPix backup."))
                return@run
            }
            val result = FinanceBackupCodec.decode(text)
            val snapshot = result.snapshot
            if (snapshot == null) {
                problem(result.errors)
                return@run
            }
            val withPictures = snapshot.transactions.count { it.picture != null && it.picture in read.pictures }

            val current = finance.repository.snapshot()
            val confirmed = overlays.confirm(
                title = "Replace all finance data?",
                message = "The backup has ${describe(snapshot)}" +
                    (if (read.archive) ", with ${if (withPictures == 1) "1 picture" else "$withPictures pictures"}" else "") + ".\n\n" +
                    "It will replace everything here now: ${describe(current)}. " +
                    "You can undo it for a few seconds; after that, export a backup first if you might want the current data back. " +
                    "Saved cards and reminders are not affected.",
                confirmLabel = "Replace",
                destructive = true,
                icon = Icons.Rounded.Restore,
            )
            if (!confirmed) return@run
            try {
                // The backup's pictures go in first, so the transactions that name them find them there.
                // Replacing the data can't stop half way, so it runs under a sheet that has no Cancel.
                val undo = overlays.loading("Restoring your data", "Replacing what is here with the backup, then working out balances and totals.", cancellable = false) {
                    if (store != null) for ((name, file) in read.pictures) store.adopt(file, name)
                    finance.undoable {
                        this.restore(snapshot)
                        emptyList()
                    }
                } ?: return@run
                haptics.success()
                snackbars.offerUndo("Backup restored", finance, undo, scope)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                problem(listOf("The backup couldn't be restored, so nothing was changed."))
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    LaunchedEffect(pendingRestoreAfterSampleDataOff, prefs.dummyDataEnabled) {
        if (pendingRestoreAfterSampleDataOff && !prefs.dummyDataEnabled) {
            pendingRestoreAfterSampleDataOff = false
            restore()
        }
    }

    fun eraseAll() = run("erase") {
        val current = finance.repository.snapshot()
        val isSample = prefs.dummyDataEnabled
        val confirmed = overlays.confirm(
            title = if (isSample) "Reset sample data?" else "Delete all finance data?",
            message = if (isSample) {
                "This deletes the current sample finance data and resets to the default sample set. Your own real data is kept aside and untouched."
            } else {
                "This deletes ${describe(current)}, and restores the default Cash account and categories. You can " +
                    "undo it for a few seconds; after that it's gone.\n\nSaved cards, reminders and app settings are kept."
            },
            confirmLabel = if (isSample) "Reset sample data" else "Delete everything",
            destructive = true,
            icon = Icons.Outlined.DeleteForever,
        )
        if (!confirmed) return@run
        val undo = overlays.loading(if (isSample) "Resetting sample data" else "Deleting finance data", cancellable = false) {
            finance.resetAllUndoably()
        } ?: return@run
        snackbars.offerUndo(if (isSample) "Sample data reset" else "Finance data deleted", finance, undo, scope)
    }

    @Composable
    fun Row(id: String, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, destructive: Boolean = false) {
        val color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified
        ListRow(
            headline = title,
            supporting = subtitle,
            headlineColor = color,
            enabled = busy == null,
            onClick = onClick,
            leading = {
                if (busy == id) {
                    AppLoadingIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                    Icon(icon, contentDescription = null, tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    AppSubPage(title = "Data & backup", onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                // First, because it decides which data everything under it works on.
                ListSection(
                    title = "Try NormPix",
                    footer = "Your own data is kept aside, untouched, while sample data is on. Backing up and erasing here work on " +
                        "the sample set, which is thrown away when you turn it off. Restoring a backup requires turning off sample data so " +
                        "your actual data is restored. Settings, profile and font are always your own.",
                ) {
                    item {
                        SettingsSwitchRow(
                            checked = prefs.dummyDataEnabled,
                            onCheckedChange = { enabled -> container.preferences.setDummyDataEnabled(enabled) },
                            headline = "Sample data",
                            supporting = "Explore NormPix with realistic sample accounts and transactions",
                            icon = Icons.Outlined.Science,
                            hue = 6,
                        )
                    }
                }
                ListSection(
                    title = "Back up",
                    footer = if (prefs.dummyDataEnabled) {
                        "Sample data is on: backups exported now will hold the sample data, not your own data. Turn off Sample data above to back up your real data."
                    } else {
                        "The finance backups hold accounts, categories, transactions, budgets, recurring payments and " +
                            "people. The one with everything also holds your settings, reminders, profile and font, and your saved " +
                            "cards if you choose - those are locked with a password. Nothing else in a file is encrypted, so keep " +
                            "files somewhere safe. They are saved only where you choose."
                    },
                ) {
                    item {
                        Row(
                            "app",
                            Icons.Outlined.Backup,
                            "Everything: data, pictures, settings and reminders",
                            "A .zip to set up this app again, here or on another phone. Saved cards only if you choose.",
                            ::exportAppBackup,
                        )
                    }
                    item {
                        Row(
                            "full",
                            Icons.Outlined.FolderZip,
                            "Full backup, with pictures",
                            "A .zip with everything, and the bills and receipts attached to transactions. Restorable.",
                            ::exportFullBackup,
                        )
                    }
                    item {
                        Row(
                            "backup",
                            Icons.Rounded.SaveAlt,
                            "Backup without pictures",
                            "A small .json file. Restorable; attached pictures stay on this phone only.",
                            ::exportBackup,
                        )
                    }
                    item {
                        Row("csv", Icons.Outlined.TableView, "Transactions as a spreadsheet", "A .csv for Excel or Sheets. Not restorable.", ::exportCsv)
                    }
                }
                ListSection(title = "Restore") {
                    item {
                        Row(
                            "restore",
                            Icons.Rounded.Restore,
                            "Restore from backup",
                            if (prefs.dummyDataEnabled) {
                                "Sample data is on. Turn it off to restore a backup to your actual data."
                            } else {
                                "A backup of everything or a full backup (.zip), or one without pictures (.json). It's checked first, and you confirm what is replaced before anything is."
                            },
                            ::restore,
                        )
                    }
                }
                ListSection(
                    title = "Check",
                    footer = "Balances, budgets and totals are worked out from every entry each time they are shown. If a number looks " +
                        "wrong, this checks every entry, puts right what it safely can, and shows what's left to look at.",
                ) {
                    item {
                        Row(
                            "check",
                            Icons.Outlined.FactCheck,
                            "Check and recalculate",
                            if (prefs.dummyDataEnabled) {
                                "Checks the sample data and works its numbers out again"
                            } else {
                                "Checks every entry and works out balances and totals again"
                            },
                            ::check,
                        )
                    }
                }
                ListSection(title = "Erase") {
                    item {
                        Row(
                            "erase",
                            Icons.Outlined.DeleteForever,
                            if (prefs.dummyDataEnabled) "Reset sample data" else "Delete all finance data",
                            if (prefs.dummyDataEnabled) "Reset sample transactions, accounts and budgets" else "Transactions, accounts, budgets and more",
                            ::eraseAll,
                            destructive = true,
                        )
                    }
                }
            }
        }
    }
}

/** The finance data in a snapshot, counted in words. */
internal fun describe(s: FinanceSnapshot): String {
    fun count(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"
    return listOf(
        count(s.transactions.size, "transaction"),
        count(s.accounts.size, "account"),
        count(s.categories.size, "category", "categories"),
        count(s.budgets.size, "budget change"),
        count(s.recurringRules.size, "recurring payment"),
    ).joinToString(", ")
}

/**
 * A backup file as read for [restore]: its JSON, and - for a zip - the pictures it carried and, for a backup of everything, [app]
 * (what else it held, with its photo and font files in the staging folder).
 */
private class ReadBackup(
    val text: String?,
    val pictures: Map<String, java.io.File>,
    val archive: Boolean,
    val app: AppBackupArchive.App? = null,
)

/**
 * Reads a backup from [input]: a zip (a full backup, see [FinanceBackupArchive], or a backup of everything, see
 * [AppBackupArchive]) with what it carries read into [staging], or a backup's JSON on its own. Throws
 * [BackupTooLargeException] for a file bigger than any backup could be, and [BackupRejectedException] for a zip with an unsafe
 * entry or from a newer NormPix.
 */
private fun readBackup(input: InputStream, staging: java.io.File, keepPictures: Boolean): ReadBackup {
    val buffered = if (input.markSupported()) input else BufferedInputStream(input)
    buffered.mark(8)
    val head = ByteArray(4)
    val got = buffered.read(head)
    buffered.reset()
    if (got == 4 && FinanceBackupArchive.isArchive(head)) {
        val contents = try {
            AppBackupArchive.read(buffered, staging)
        } catch (e: java.io.IOException) {
            when (e.message) {
                FinanceBackupArchive.TOO_LARGE -> throw BackupTooLargeException()
                AppBackupArchive.UNSAFE ->
                    throw BackupRejectedException("This file has entries that aren't safe to unpack, so it isn't a NormPix backup.")
                AppBackupArchive.NEWER ->
                    throw BackupRejectedException("This backup was made by a newer NormPix. Update NormPix, then restore it.")
                else -> throw e
            }
        }
        return ReadBackup(
            text = contents.finance.json,
            pictures = if (keepPictures) contents.finance.pictures else emptyMap(),
            archive = true,
            app = contents.app,
        )
    }
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = buffered.read(buffer)
        if (read < 0) break
        total += read
        if (total > BackupFiles.MAX_BYTES) throw BackupTooLargeException()
        out.write(buffer, 0, read)
    }
    val text = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(out.toByteArray()))
            .toString()
            .removePrefix("\uFEFF")
    } catch (_: CharacterCodingException) {
        null
    }
    return ReadBackup(text, emptyMap(), archive = false)
}
