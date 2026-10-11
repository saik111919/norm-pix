package com.example.normpix.reminders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve

/*
 * Overdue is derived, never stored (see Reminder.isOverdue), so every indicator below reads the live
 * reminders state: complete or snooze the last overdue item and they all disappear together. They are
 * deliberately calm - an amber count and a plain sentence, with the reminder's own row (icon + "Overdue
 * by 2d") carrying the detail - and they never repeat the notification the alarm already delivered.
 */

/** How many reminders are overdue right now; recomposes when one comes due or is resolved. */
@Composable
fun rememberOverdueCount(): Int {
    val state by LocalReminders.current.state.collectAsState()
    return state.overdue().size
}

fun overdueCountText(count: Int): String = if (count == 1) "1 reminder overdue" else "$count reminders overdue"

/** A small amber count, announced as "3 overdue" - the number is text, so it never relies on color. It eases in when it first appears. */
@Composable
fun OverdueBadge(count: Int, modifier: Modifier = Modifier) {
    val tone = StatusTone.Warning.resolve()
    val ms = AppMotion.duration(AppMotion.SHORT_MS)
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(ms, easing = AppMotion.enter)) + scaleIn(tween(ms, easing = AppMotion.enter), initialScale = 0.8f),
    ) {
        Badge(
            modifier = modifier.semantics { contentDescription = "$count overdue" },
            containerColor = tone.container,
            contentColor = tone.onContainer,
        ) {
            Text(if (count > 99) "99+" else "$count")
        }
    }
}

/**
 * "2 reminders overdue" with one way to deal with them: used where overdue items are not already on screen.
 * A quiet tonal row with an amber icon rather than a filled alert - it informs, it does not shout.
 */
@Composable
fun OverdueNotice(count: Int, onReview: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), color = scheme.surfaceContainerHigh, shape = AppRadius.rXl) {
        Row(Modifier.padding(start = AppSpacing.lg, top = AppSpacing.sm, end = AppSpacing.sm, bottom = AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(ReminderUrgency.Overdue.icon, contentDescription = null, tint = StatusTone.Warning.resolve().color, modifier = Modifier.size(AppIconSizes.lg))
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(overdueCountText(count), style = MaterialTheme.typography.titleSmall)
                Text("Mark them done or snooze them.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            TextButton(onClick = onReview) { Text("Review") }
        }
    }
}
