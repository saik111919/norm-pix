package com.example.normpix.finance.share

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.RevealVisibility
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.normpix.finance.ui.rememberFinanceActions
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve

/**
 * Opens what other apps share with NormPix ([SharedTextInbox]) as a new-transaction draft, one share at a time, reading a
 * shared picture's text with [pictures]. Not while the app is locked - the share waits for the unlock - and never into the
 * sample data ([sampleData]: the screens are showing the sample set, which is thrown away with everything added to it):
 * then it asks to switch to the user's own data and leaves the share waiting, so the rebuilt screens open it.
 */
@Composable
fun ReceiveSharedTransactions(inbox: SharedTextInbox, pictures: SharedPictureReader, appLocked: Boolean, sampleData: Boolean) {
    val actions = rememberFinanceActions()
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val preferences = LocalAppPreferences.current
    val waiting by inbox.waiting.collectAsStateWithLifecycle()
    val next = waiting.firstOrNull()
    LaunchedEffect(next, appLocked) {
        val share = next ?: return@LaunchedEffect
        if (appLocked) return@LaunchedEffect
        if (sampleData) {
            val switch = overlays.confirm(
                title = "Sample data is on",
                message = "A shared transaction goes into your own data, not the sample set, which is thrown away. " +
                    "Turn sample data off to add it?",
                confirmLabel = "Turn off sample data",
            )
            if (switch && preferences != null) {
                preferences.setDummyDataEnabled(false)
            } else if (inbox.take(share)) {
                snackbars.show("Shared transaction not added", SnackbarKind.Info)
            }
            return@LaunchedEffect
        }
        if (inbox.take(share)) actions.addFromShare(share.text, share.picture, pictures)
    }
}

/**
 * The top of a form filled in from a share - or from a picture attached in the form, with its own [title], [body] and
 * [icon]: where the values came from (text, or a picture read on the phone, which can be misread), that nothing is
 * saved until the user saves, and what to check. Each point has its own icon - a warning sign for what needs attention -
 * so none of it rests on colour alone.
 */
@Composable
fun SharedDraftNotice(
    notices: List<ShareNotice>,
    modifier: Modifier = Modifier,
    fromPicture: Boolean = false,
    /** The text read from the picture, which the user can open to check it or copy it. */
    readText: String? = null,
    title: String = if (fromPicture) "Filled in from a shared picture" else "Filled in from shared text",
    body: String = if (fromPicture) {
        "Read from the picture on this phone, which can get things wrong. Check each detail, then save."
    } else {
        "Check the details, then save. Nothing is saved until you do."
    },
    icon: ImageVector = Icons.Outlined.MoveToInbox,
) {
    val scheme = MaterialTheme.colorScheme
    val text = MaterialTheme.typography
    val warning = StatusTone.Warning.resolve().color
    Surface(modifier.fillMaxWidth(), color = scheme.surfaceContainerHigh, shape = AppRadius.rXl) {
        Column(Modifier.padding(AppSpacing.lg)) {
            Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(AppIconSizes.lg))
                Spacer(Modifier.width(AppSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(title, style = text.titleSmall, color = scheme.onSurface)
                    Text(body, style = text.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            for (notice in notices) {
                Spacer(Modifier.height(AppSpacing.sm))
                Row(Modifier.semantics(mergeDescendants = true) {}) {
                    Icon(
                        if (notice.attention) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
                        contentDescription = if (notice.attention) "Needs checking" else null,
                        tint = if (notice.attention) warning else scheme.onSurfaceVariant,
                        modifier = Modifier.size(AppIconSizes.md),
                    )
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(notice.text, style = text.bodyMedium, color = scheme.onSurface)
                }
            }
            if (!readText.isNullOrBlank()) ReadTextReveal(readText)
        }
    }
}

/**
 * What the phone read from the picture, as it read it, behind a toggle: to see why a detail came out wrong, and to
 * select and copy it. Only shown here, on the device; it is never kept.
 */
@Composable
private fun ReadTextReveal(readText: String) {
    val scheme = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Icon(
            if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(AppIconSizes.md),
        )
        Spacer(Modifier.width(AppSpacing.xs))
        Text(if (open) "Hide the text it read" else "Show the text it read")
    }
    RevealVisibility(open) {
        Surface(Modifier.fillMaxWidth(), color = scheme.surfaceContainerLowest, shape = AppRadius.rMd) {
            SelectionContainer {
                Text(
                    readText.trim(),
                    modifier = Modifier.padding(AppSpacing.md),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
