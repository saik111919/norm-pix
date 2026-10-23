package com.example.normpix.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditScore
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.SummaryStat
import com.example.normpix.finance.ui.TABULAR_FIGURES
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing

/** At or above this font scale the two figures under the total stack one over the other instead of sitting side by side. */
private const val STACK_AT_FONT_SCALE = 1.4f

/**
 * The Wallet's summary, at the top of the screen: the total balance - what is held minus what is owed - as the biggest thing
 * on it, and under it the two figures it is made of, each named and with an icon: [held] "In accounts" and, when something is
 * owed on a card or a loan, [owed] "You owe", in the error colour. The answer to "how much do I have?" and the sum that checks it,
 * in one place, rather than a figure and a sentence of arithmetic.
 *
 * A screen reader gets all of it as one thing, as a sentence ([spoken]) - the figures, not the layout - instead of five
 * fragments to swipe through. [note] is the fine print: how the balances are worked out.
 */
@Composable
fun WalletOverview(
    total: String,
    held: String,
    owed: String?,
    negative: Boolean,
    spoken: String,
    note: String?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = AppRadius.rXxl
    val base = scheme.surfaceContainerHigh
    // The page's accent washed over the container's own tone: lit from the top, settled by the foot.
    val wash = Brush.verticalGradient(listOf(lerp(base, scheme.primaryContainer, 0.55f), lerp(base, scheme.primaryContainer, 0.14f)))
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(wash)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), shape)
            .padding(AppSpacing.xl)
            // One thing for a screen reader: the sentence, not the pieces.
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        SummaryStat(
            label = "Total balance",
            value = total,
            valueStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (negative) scheme.error else null,
        )
        Spacer(Modifier.height(AppSpacing.lg))
        val stacked = LocalDensity.current.fontScale >= STACK_AT_FONT_SCALE
        if (stacked) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                FigureTile(Icons.Outlined.AccountBalanceWallet, "In accounts", held, Modifier.fillMaxWidth())
                if (owed != null) FigureTile(Icons.Outlined.CreditScore, "You owe", owed, Modifier.fillMaxWidth(), tone = scheme.error)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                FigureTile(Icons.Outlined.AccountBalanceWallet, "In accounts", held, Modifier.weight(1f))
                if (owed != null) FigureTile(Icons.Outlined.CreditScore, "You owe", owed, Modifier.weight(1f), tone = scheme.error)
            }
        }
        if (note != null) {
            Spacer(Modifier.height(AppSpacing.md))
            Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

/** One of the figures under the total: an icon in a disc, what it is called and the amount. */
@Composable
private fun FigureTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier, tone: Color = Color.Unspecified) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (tone == Color.Unspecified) scheme.onSurfaceVariant else tone
    Row(
        modifier
            .clip(AppRadius.rLg)
            .background(scheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f, fill = false)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 2)
            // The figure is never cut: it shrinks to fit its half of the row, down to a size still easy to read.
            val style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TABULAR_FIGURES)
            Text(
                value,
                style = style,
                fontWeight = FontWeight.SemiBold,
                color = if (tone == Color.Unspecified) scheme.onSurface else tone,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize),
            )
        }
    }
}

/**
 * A section of the Wallet: its name as a heading - the screen's outline, for a screen reader moving by headings - with a
 * line under it saying what is in it ("3 accounts", "2 cards, encrypted on this device") and, at the end, the way to add to
 * it as a button with its words ([action]).
 */
@Composable
fun WalletSectionHeader(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(start = AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action?.invoke()
    }
}

/** The "add" button of a [WalletSectionHeader]: a tonal button with a plus and its words. */
@Composable
fun SectionAddButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(AppSpacing.xs))
        Text(label, maxLines = 1)
    }
}
