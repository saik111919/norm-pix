package com.example.normpix.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/** The label of a sheet's primary button: medium-bold, so the one action to take reads first. */
private val SheetButtonText: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

/** Standard sheet header: a title and optional supporting line, with the sheet's horizontal padding. */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * What a yes / no decision looks like: the same bottom sheet every form uses, not a dialog floating mid-screen.
 * An optional icon, the question as a heading, what it means, then the answer as full-width buttons at thumb height -
 * the action first, "Cancel" under it. [cancelLabel] null makes it an acknowledgement with a single button.
 * A destructive action wears the error colors, so it can't be mistaken for an ordinary one.
 */
@Composable
fun ConfirmSheetContent(
    title: String,
    message: String?,
    confirmLabel: String,
    cancelLabel: String?,
    destructive: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = AppSpacing.xxl, top = AppSpacing.xs, end = AppSpacing.xxl, bottom = AppSpacing.md),
    ) {
        if (icon != null) {
            LeadingIcon(
                icon,
                background = if (destructive) scheme.errorContainer else scheme.secondaryContainer,
                foreground = if (destructive) scheme.onErrorContainer else scheme.onSecondaryContainer,
                size = 48.dp,
            )
            Spacer(Modifier.height(AppSpacing.lg))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (message != null) {
            Spacer(Modifier.height(AppSpacing.sm))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(AppSpacing.xl))
        Button(
            onClick = onConfirm,
            colors = if (destructive) {
                ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError)
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(confirmLabel, style = SheetButtonText) }
        if (cancelLabel != null) {
            Spacer(Modifier.height(AppSpacing.xs))
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(cancelLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * The layout every add / edit form shares, set by the transaction editor: a title, a body that
 * scrolls when it is taller than the room above the keyboard, and one full-width Save pinned
 * below it. There is no close or Cancel button: the sheet closes by its handle, the scrim or back,
 * asking first when there are unsaved changes (see [DiscardChangesGuard]).
 */
@Composable
fun EditorSheet(
    title: String,
    saveLabel: String,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    saving: Boolean = false,
    saveColors: ButtonColors? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        // The body scrolls (it may be taller than the space above the keyboard); Save stays put below it.
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = AppSpacing.xxl, top = AppSpacing.xs, end = AppSpacing.xxl, bottom = AppSpacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(AppSpacing.xs))
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                titleTrailing?.invoke()
            }
            Spacer(Modifier.height(AppSpacing.lg))
            content()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.xxl, top = AppSpacing.xs, end = AppSpacing.xxl, bottom = AppSpacing.md),
        ) {
            Button(
                onClick = onSave,
                enabled = !saving,
                colors = saveColors ?: ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(saveLabel, style = SheetButtonText) }
        }
    }
}

/**
 * For an [EditorSheet]: swipe, scrim and back all end up here. A clean form just closes, one with
 * changes asks first, and one that is mid-save cannot be closed at all. [keptMessage] is what an
 * edit keeps when discarded ("The transaction stays as it was.").
 */
@Composable
fun DiscardChangesGuard(dirty: Boolean, isEditing: Boolean, keptMessage: String, saving: Boolean = false) {
    val overlays = LocalOverlays.current
    SheetDismissConfirmation(needed = dirty || saving) {
        if (saving) {
            false
        } else {
            overlays.confirm(
                title = if (isEditing) "Discard your changes?" else "Discard this entry?",
                message = if (isEditing) keptMessage else "What you've entered won't be saved.",
                confirmLabel = "Discard",
                cancelLabel = "Keep editing",
            )
        }
    }
}

/** Padding every sheet body uses. */
val SheetPadding = Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.xxl)

/** Row of end-aligned sheet/dialog actions (Cancel, Save). */
@Composable
fun SheetActions(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Fades and lifts a list item into place on first composition, optionally
 * after [delayMs] so consecutive items cascade. Explains "these just arrived"
 * without drawing attention; skipped entirely when the OS asks for reduced
 * motion.
 */
@Composable
fun AnimatedEntrance(
    modifier: Modifier = Modifier,
    delayMs: Long = 0,
    durationMs: Int = AppMotion.LONG_MS,
    offset: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            if (delayMs > 0) delay(delayMs)
            progress.animateTo(1f, tween(durationMs, easing = AppMotion.enter))
        }
    }
    val offsetPx = with(androidx.compose.ui.platform.LocalDensity.current) { offset.toPx() }
    Column(
        modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * offsetPx
        },
    ) { content() }
}
