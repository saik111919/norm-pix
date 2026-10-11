package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.data.FinanceBackupCodec
import com.example.normpix.finance.data.FinanceSnapshot
import com.example.normpix.finance.ui.LocalFinance
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppLoadingIndicator
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
                snackbars.show("Something went wrong. Nothing was changed.")
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
            snackbars.show("Couldn't save the backup there. Try another location.")
            return@run
        }
        if (saved) {
            haptics.success()
            snackbars.show("Backup saved: ${describe(snapshot)}")
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
            snackbars.show("Couldn't save the file there. Try another location.")
            return@run
        }
        if (saved) {
            haptics.success()
            val n = snapshot.transactions.size
            snackbars.show("$n ${if (n == 1) "transaction" else "transactions"} exported")
        }
    }

    fun restore() = run("restore") {
        val bytes = try {
            files.open() ?: return@run
        } catch (e: CancellationException) {
            throw e
        } catch (_: BackupTooLargeException) {
            problem(listOf("This file is too large to be a NormPix backup."))
            return@run
        } catch (_: Exception) {
            problem(listOf("The file couldn't be read."))
            return@run
        }
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .removePrefix("\uFEFF")
        } catch (_: CharacterCodingException) {
            problem(listOf("This file isn't a NormPix backup."))
            return@run
        }
        val result = FinanceBackupCodec.decode(text)
        val snapshot = result.snapshot
        if (snapshot == null) {
            problem(result.errors)
            return@run
        }

        val current = finance.repository.snapshot()
        val confirmed = overlays.confirm(
            title = "Replace all finance data?",
            message = "The backup has ${describe(snapshot)}.\n\n" +
                "It will replace everything here now: ${describe(current)}. " +
                "This can't be undone - export a backup first if you might want the current data back. " +
                "Saved cards and reminders are not affected.",
            confirmLabel = "Replace",
            destructive = true,
            icon = Icons.Rounded.Restore,
        )
        if (!confirmed) return@run
        try {
            finance.restore(snapshot)
            haptics.success()
            snackbars.show("Backup restored")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            problem(listOf("The backup couldn't be restored, so nothing was changed."))
        }
    }

    fun eraseAll() = run("erase") {
        val current = finance.repository.snapshot()
        val confirmed = overlays.confirm(
            title = "Delete all finance data?",
            message = "This permanently deletes ${describe(current)}, and restores the default Cash account and " +
                "categories. This can't be undone.\n\nSaved cards, reminders and app settings are kept.",
            confirmLabel = "Delete everything",
            destructive = true,
            icon = Icons.Outlined.DeleteForever,
        )
        if (!confirmed) return@run
        finance.resetAll()
        snackbars.show("Finance data deleted")
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
                ListSection(
                    title = "Back up",
                    footer = "Backups include accounts, categories, transactions, budgets and recurring payments. " +
                        "Saved cards and reminders aren't included. Files are saved only where you choose, and are " +
                        "not encrypted - keep them somewhere safe.",
                ) {
                    item { Row("backup", Icons.Rounded.SaveAlt, "Export backup", "A JSON file you can restore later", ::exportBackup) }
                    item {
                        Row("csv", Icons.Outlined.TableView, "Export transactions as CSV", "For spreadsheets. Not restorable.", ::exportCsv)
                    }
                }
                ListSection(title = "Restore") {
                    item {
                        Row(
                            "restore",
                            Icons.Rounded.Restore,
                            "Restore from backup",
                            "The file is checked first. You confirm before anything is replaced.",
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
