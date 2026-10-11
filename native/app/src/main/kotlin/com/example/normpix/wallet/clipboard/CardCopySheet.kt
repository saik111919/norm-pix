package com.example.normpix.wallet.clipboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.normpix.settings.ClipboardTimeout
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.StoredCard

/** What a row shows of a detail before it is copied: the number and CVV never appear in full. */
internal fun CardField.preview(card: StoredCard): String = when (this) {
    CardField.Number -> "•••• •••• •••• ${card.last4}"
    CardField.Holder -> card.cardholderName
    CardField.Expiry -> valueOf(card)
    CardField.Cvv -> "•".repeat(card.cvv.length.coerceAtLeast(3))
}

private val CardField.icon: ImageVector
    get() = when (this) {
        CardField.Number -> Icons.Outlined.CreditCard
        CardField.Holder -> Icons.Outlined.Person
        CardField.Expiry -> Icons.Outlined.CalendarMonth
        CardField.Cvv -> Icons.Outlined.Lock
    }

/** What the user is told after a copy, and what a screen reader announces. */
fun copiedMessage(field: CardField, timeout: ClipboardTimeout): String =
    if (timeout.clears) "${field.label} copied. It clears from the clipboard in ${timeout.spoken}." else "${field.label} copied."

/**
 * "Copy card details": the four things a card can give, each one tap. Copying one never reveals the card on screen,
 * and the number and CVV are masked here too. [onCopy] does the copy and says whether it worked.
 */
@Composable
fun CardCopySheet(
    card: StoredCard,
    timeout: ClipboardTimeout,
    onCopy: (CardField) -> Boolean,
    onDone: () -> Unit,
) {
    var copied by remember { mutableStateOf<CardField?>(null) }
    var failed by remember { mutableStateOf(false) }
    Column(Modifier.padding(AppSpacing.page).padding(bottom = AppSpacing.lg)) {
        SheetHeader(title = "Copy card details", subtitle = "${card.bankName} •••• ${card.last4}")
        Spacer(Modifier.height(AppSpacing.md))
        ListSection {
            for (field in CardField.entries) {
                item {
                    ListRow(
                        headline = field.label,
                        supporting = field.preview(card),
                        leading = { LeadingIcon(field.icon) },
                        trailing = {
                            Icon(
                                if (copied == field) Icons.Rounded.Check else Icons.Outlined.ContentCopy,
                                contentDescription = if (copied == field) "Copied" else "Copy ${field.label.lowercase()}",
                            )
                        },
                        onClick = {
                            failed = !onCopy(field)
                            copied = if (failed) null else field
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.md))
        Text(
            when {
                failed -> "Couldn't copy to the clipboard."
                copied != null -> copiedMessage(copied!!, timeout)
                timeout.clears -> "Copied details clear from the clipboard after ${timeout.spoken}."
                else -> "Copied details stay on the clipboard until you copy something else."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AppSpacing.lg).semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(AppSpacing.lg))
        FilledTonalButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
