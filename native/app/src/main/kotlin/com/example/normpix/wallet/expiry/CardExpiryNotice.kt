package com.example.normpix.wallet.expiry

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve

/** "11/26", the way the card prints it. */
fun CardExpiryState.printed(): String = "%02d/%02d".format(expiry.monthValue, expiry.year % 100)

fun CardExpiryState.title(): String = if (expired) "This card has expired" else "Expires soon"

fun CardExpiryState.message(): String =
    "Valid thru ${printed()}. We'll remind you every day until you mark this card done."

/**
 * Sits on top of the one card it is about, says what is going on and offers the one way out. A quiet tonal row
 * with an amber icon, like the overdue notice: it informs, it does not shout. If the system won't show notifications
 * it says so, because then this row is the only reminder there is.
 */
@Composable
fun CardExpiryNotice(
    state: CardExpiryState,
    card: String,
    notificationsOn: Boolean,
    onMarkDone: () -> Unit,
    onTurnOnNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), color = scheme.surfaceContainerHigh, shape = AppRadius.rXl) {
        Column(Modifier.padding(start = AppSpacing.lg, top = AppSpacing.md, end = AppSpacing.sm, bottom = AppSpacing.xs)) {
            Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
                Icon(
                    if (state.expired) Icons.Outlined.EventBusy else Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = StatusTone.Warning.resolve().color,
                    modifier = Modifier.size(AppIconSizes.lg),
                )
                Spacer(Modifier.width(AppSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text("$card · ${state.title()}", style = MaterialTheme.typography.titleSmall)
                    Text(state.message(), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    if (!notificationsOn) {
                        Text(
                            "Notifications are off, so this is the only reminder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                if (!notificationsOn) TextButton(onClick = onTurnOnNotifications) { Text("Turn on") }
                TextButton(onClick = onMarkDone) { Text("Mark as done") }
            }
        }
    }
}
