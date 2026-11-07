package com.example.normpix.about.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.example.normpix.diagnostics.CrashLog
import com.example.normpix.diagnostics.CrashReport
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.theme.AppSpacing
import kotlinx.coroutines.launch

/*
 * Crash log (2026-11-07; the owner asked for it on 2026-10-31): About > Crash log lists what NormPix kept when it closed unexpectedly
 * (see `diagnostics/CrashLog.kt`), and Home offers the last one on the next start. The reports are the owner's: kept on this phone,
 * shown here, shared only when the owner taps Share, cleared when the owner says so. Nothing is sent by the app.
 */

/** The newest few reports are what Share sends: a whole log is more than a share sheet should carry. */
const val SHARED_REPORTS = 5

/** What the About row says: whether anything was kept, and when the last was. */
fun crashLogSummary(entries: List<CrashLog.Entry>): String = when {
    entries.isEmpty() -> "Nothing has gone wrong. A report is kept here if NormPix ever closes unexpectedly."
    entries.size == 1 -> "1 report, from ${CrashReport.time(entries.first().at)}"
    else -> "${entries.size} reports, the last from ${CrashReport.time(entries.first().at)}"
}

/** The reports as one text to send: the newest [SHARED_REPORTS], newest first. */
fun crashLogShareText(entries: List<CrashLog.Entry>): String = entries.take(SHARED_REPORTS).joinToString("\n\n") { it.text }

/** About's "Crash log" section: one row that opens the reports. */
@Composable
fun CrashLogSection() {
    val context = LocalContext.current
    val overlays = LocalOverlays.current
    val scope = LocalAppScope.current
    // Read from the phone's storage once, and again when the sheet that can change it has closed.
    var entries by remember { mutableStateOf(CrashLog.of(context).entries()) }
    ListSection(title = "Crash log") {
        item {
            ListRow(
                headline = "Crash log",
                supporting = crashLogSummary(entries),
                leading = { Icon(Icons.Outlined.ReportProblem, contentDescription = null) },
                trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                supportingMaxLines = 3,
                onClick = {
                    scope.launch {
                        overlays.sheet<Unit>(expandable = true) { done -> CrashLogSheet { done(Unit) } }
                        entries = CrashLog.of(context).entries()
                    }
                },
            )
        }
    }
}

/**
 * The reports, newest first, with Share (the newest few, through Android's own share sheet) and Clear (asks first). Opening it counts
 * as seeing them: Home stops offering the ones in it.
 */
@Composable
fun CrashLogSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val overlays = LocalOverlays.current
    val scope = LocalAppScope.current
    val log = remember { CrashLog.of(context) }
    var entries by remember { mutableStateOf(log.entries()) }
    LaunchedEffect(Unit) { container.preferences.markCrashLogSeen(log.newestAt()) }

    EditorSheet(title = "Crash log", subtitle = "Kept on this phone only", saveLabel = "Close", onSave = onClose) {
        Text(
            "When NormPix closes unexpectedly it keeps a short report here: the app and Android versions, your phone's model, and " +
                "where in the code it stopped. Numbers, names and anything in quotes are left out of the messages. Nothing is sent " +
                "anywhere unless you tap Share.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entries.isEmpty()) {
            Spacer(Modifier.height(AppSpacing.lg))
            Text("Nothing has gone wrong.", style = MaterialTheme.typography.titleMedium)
            return@EditorSheet
        }
        Spacer(Modifier.height(AppSpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            OutlinedButton(onClick = { shareText(context, crashLogShareText(entries)) }) { Text("Share") }
            TextButton(
                onClick = {
                    scope.launch {
                        val clear = overlays.confirm(
                            title = "Clear the crash log?",
                            message = "The reports kept on this phone are deleted. This can't be undone.",
                            confirmLabel = "Clear",
                            destructive = true,
                        )
                        if (clear) {
                            log.clear()
                            entries = log.entries()
                        }
                    }
                },
            ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(AppSpacing.md))
        Text(
            entries.joinToString("\n\n") { it.text },
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Android's share sheet for [text]: the user picks where it goes, and can pick nowhere. */
private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "NormPix crash log")
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, "Share crash log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
