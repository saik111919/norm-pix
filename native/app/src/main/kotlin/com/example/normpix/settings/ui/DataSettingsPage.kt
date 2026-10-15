package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.data.FinanceBackupArchive
import com.example.normpix.finance.data.FinanceBackupCodec
import com.example.normpix.finance.data.FinanceSnapshot
import com.example.normpix.finance.ui.LocalFinance
import com.example.normpix.finance.ui.offerUndo
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

/** At most this many problems are listed when a backup can't be used. */
private const val PROBLEMS_SHOWN = 5

/**
 * Settings > Data: back up, export, restore and erase finance data.
 *
 * Everything is local and explicit: files are only written where the user
 * chooses in the system dialog, a backup is fully validated before anything
 * changes, and replacing or erasing data always asks first. Saved cards and
 * reminders are never part of a backup.
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
    val files = rememberBackupFiles()
    /** The operation in progress, if any (disables the other rows). */
    var busy by remember { mutableStateOf<String?>(null) }

    suspend fun problem(errors: List<String>) {
        val lines = buildList {
            add("Nothing was changed.")
            add("")
            addAll(errors.take(PROBLEMS_SHOWN))
            if (errors.size > PROBLEMS_SHOWN) add("and ${errors.size - PROBLEMS_SHOWN} more problems")
        }
        overlays.alert("Can't use this file", lines.joinToString("\n"), icon = Icons.Outlined.ErrorOutline)
    }

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
            snackbars.show("Backup saved: ${describe(snapshot)}")
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
            snackbars.show("Backup saved with ${if (pictures == 1) "1 picture" else "$pictures pictures"}: ${describe(snapshot)}")
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
            snackbars.show("$n ${if (n == 1) "transaction" else "transactions"} exported")
        }
    }

    fun restore() = run("restore") {
        // A full backup (.zip) brings its pictures along; they wait in a folder of their own until the restore is confirmed.
        val store = finance.pictures
        val staging = store?.newRestoreFolder()
        try {
            val read = try {
                files.openStreaming { input -> withContext(Dispatchers.IO) { readBackup(input, staging) } } ?: return@run
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackupTooLargeException) {
                problem(listOf("This file is too large to be a NormPix backup."))
                return@run
            } catch (_: Exception) {
                problem(listOf("The file couldn't be read."))
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
                if (store != null) for ((name, file) in read.pictures) store.adopt(file, name)
                val undo = finance.undoable {
                    this.restore(snapshot)
                    emptyList()
                }
                haptics.success()
                snackbars.offerUndo("Backup restored", finance, undo, scope)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                problem(listOf("The backup couldn't be restored, so nothing was changed."))
            }
        } finally {
            staging?.deleteRecursively()
        }
    }

    fun eraseAll() = run("erase") {
        val current = finance.repository.snapshot()
        val confirmed = overlays.confirm(
            title = "Delete all finance data?",
            message = "This deletes ${describe(current)}, and restores the default Cash account and categories. You can " +
                "undo it for a few seconds; after that it's gone.\n\nSaved cards, reminders and app settings are kept.",
            confirmLabel = "Delete everything",
            destructive = true,
            icon = Icons.Outlined.DeleteForever,
        )
        if (!confirmed) return@run
        val undo = finance.resetAllUndoably()
        snackbars.offerUndo("Finance data deleted", finance, undo, scope)
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
                    footer = "Your own data is kept aside, untouched, while sample data is on. Backing up, restoring and " +
                        "erasing here then work on the sample set, which is thrown away when you turn it off.",
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
                    footer = "Backups include accounts, categories, transactions, budgets, recurring payments and people. " +
                        "Saved cards and reminders aren't included (cards have their own export, in Settings > Cards). " +
                        "Files are saved only where you choose, and are not encrypted - keep them somewhere safe.",
                ) {
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
                            "A full backup (.zip) or one without pictures (.json). It's checked first, and you confirm before anything is replaced.",
                            ::restore,
                        )
                    }
                }
                ListSection(title = "Erase") {
                    item {
                        Row(
                            "erase",
                            Icons.Outlined.DeleteForever,
                            "Delete all finance data",
                            "Transactions, accounts, budgets and more",
                            ::eraseAll,
                            destructive = true,
                        )
                    }
                }
            }
        }
    }
}

private fun describe(s: FinanceSnapshot): String {
    fun count(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"
    return listOf(
        count(s.transactions.size, "transaction"),
        count(s.accounts.size, "account"),
        count(s.categories.size, "category", "categories"),
        count(s.budgets.size, "budget change"),
        count(s.recurringRules.size, "recurring payment"),
    ).joinToString(", ")
}

/** A backup file as read for [restore]: its JSON, and - for a full backup - the pictures it carried. */
private class ReadBackup(val text: String?, val pictures: Map<String, java.io.File>, val archive: Boolean)

/**
 * Reads a backup from [input]: a full backup (a zip, see [FinanceBackupArchive]) with its pictures read into [staging], or
 * a backup's JSON on its own. Throws [BackupTooLargeException] for a file bigger than any backup could be.
 */
private fun readBackup(input: InputStream, staging: java.io.File?): ReadBackup {
    val buffered = if (input.markSupported()) input else BufferedInputStream(input)
    buffered.mark(8)
    val head = ByteArray(4)
    val got = buffered.read(head)
    buffered.reset()
    if (got == 4 && FinanceBackupArchive.isArchive(head)) {
        // With nowhere to keep pictures (no picture store), they are read into a folder thrown away straight after.
        val folder = staging ?: kotlin.io.path.createTempDirectory("restore").toFile()
        val contents = try {
            FinanceBackupArchive.read(buffered, folder)
        } catch (e: java.io.IOException) {
            if (e.message == FinanceBackupArchive.TOO_LARGE) throw BackupTooLargeException() else throw e
        } finally {
            if (staging == null) folder.deleteRecursively()
        }
        return ReadBackup(contents.json, if (staging != null) contents.pictures else emptyMap(), archive = true)
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
