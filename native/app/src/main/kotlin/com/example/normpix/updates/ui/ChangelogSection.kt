package com.example.normpix.updates.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.AppLoadingIndicator
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.updates.ChangelogEntry
import com.example.normpix.updates.ChangelogState
import com.example.normpix.updates.InstalledApp
import com.example.normpix.updates.NoteBlock
import com.example.normpix.updates.ReleaseNotes

/**
 * A release's notes as a list of what changed: each change on a bullet of its own, with the headings a release gives to groups
 * of them. A box of raw text ran the changes together and showed Markdown's marks as symbols.
 */
@Composable
internal fun ReleaseNotesList(notes: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val blocks = remember(notes) { ReleaseNotes.parse(notes) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        for (block in blocks) {
            when (block) {
                is NoteBlock.Heading -> Text(
                    block.text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(top = AppSpacing.xs).semantics { heading() },
                )
                is NoteBlock.Change -> Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    // Sits on the first line's middle, whatever the text's size.
                    Box(Modifier.padding(top = 7.dp).size(6.dp).background(scheme.primary, CircleShape))
                    Text(block.text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                is NoteBlock.Paragraph ->
                    Text(block.text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The changelog, on the update page: every published version, newest first, each a row that opens to the changes it made. The
 * version installed (or, if it isn't in the list, the newest one before it) is open to begin with, so what the user has is
 * the first thing they read; a newer one is marked, and its notes are the update's own, shown above.
 *
 * Saved notes are shown at once and the list refreshes under them; with no connection they stay, and say so.
 */
@Composable
fun ChangelogSection(state: ChangelogState, installed: InstalledApp, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val entries = state.entries
    val opening = entries.firstOrNull { it.versionCode <= installed.versionCode }?.versionCode
    ListSection(
        modifier = modifier,
        title = "Changelog",
        footer = if (entries.isNotEmpty() && state.problem != null) {
            "Couldn't refresh the changelog just now, so these are the changes NormPix saved earlier."
        } else {
            null
        },
    ) {
        when {
            entries.isNotEmpty() -> for (entry in entries) {
                item { ChangelogRow(entry, installed, initiallyOpen = entry.versionCode == opening) }
            }
            state.problem != null -> item {
                ListRow(
                    headline = "Couldn't load the changelog",
                    supporting = state.problem.describe() + " Tap to try again.",
                    leading = {
                        LeadingIcon(
                            Icons.Outlined.ErrorOutline,
                            background = MaterialTheme.colorScheme.errorContainer,
                            foreground = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    },
                    supportingMaxLines = 4,
                    onClick = onRetry,
                )
            }
            state.loading -> item {
                ListRow(headline = "Loading the changelog…", leading = { AppLoadingIndicator(Modifier.size(24.dp)) })
            }
            else -> item {
                ListRow(
                    headline = "No changes to show yet",
                    supporting = "Once a release is published, what it changed is listed here.",
                    leading = { LeadingIcon(Icons.Outlined.History) },
                )
            }
        }
    }
}

/** One version of the changelog: its name, when it came out and how many changes it made, opening to the changes themselves. */
@Composable
private fun ChangelogRow(entry: ChangelogEntry, installed: InstalledApp, initiallyOpen: Boolean) {
    var open by rememberSaveable(entry.versionCode) { mutableStateOf(initiallyOpen) }
    val blocks = remember(entry.notes) { ReleaseNotes.parse(entry.notes) }
    val changes = ReleaseNotes.changes(blocks)
    val summary = listOfNotNull(
        entry.publishedAt?.let(::releaseDate),
        if (changes > 0) "$changes ${if (changes == 1) "change" else "changes"}" else null,
    ).joinToString(" · ")
    Column(Modifier.fillMaxWidth()) {
        ListRow(
            headline = "Version ${entry.versionName}",
            supporting = summary.ifEmpty { null },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    when {
                        entry.versionCode > installed.versionCode -> VersionPill("New", emphasized = true)
                        entry.versionCode == installed.versionCode -> VersionPill("Installed", emphasized = false)
                    }
                    val turn by animateFloatAsState(if (open) 180f else 0f, label = "changelog_chevron")
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.rotate(turn))
                }
            },
            modifier = Modifier.semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            onClick = { open = !open },
        )
        RevealVisibility(visible = open) {
            Box(Modifier.fillMaxWidth().padding(start = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.lg)) {
                if (blocks.isEmpty()) {
                    Text(
                        "No notes were written for this version.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ReleaseNotesList(entry.notes)
                }
            }
        }
    }
}

/** A small rounded tag on a version: "New" in the accent colour, "Installed" in a quiet one. */
@Composable
private fun VersionPill(label: String, emphasized: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (emphasized) scheme.primary else scheme.secondaryContainer,
        contentColor = if (emphasized) scheme.onPrimary else scheme.onSecondaryContainer,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = AppSpacing.md - 2.dp, vertical = AppSpacing.xs),
        )
    }
}
