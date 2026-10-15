package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.pathName
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve
import kotlin.math.max
import kotlin.math.roundToInt

/** One line of a receipt's details: what it is, and its value. */
@Immutable
class ReceiptRow(val label: String, val value: String)

/**
 * Everything a receipt shows, as plain values - so the same receipt can be drawn on screen now and, later, drawn again into
 * the picture or the page that is shared.
 */
@Immutable
class ReceiptData(
    val title: String,
    val type: TransactionType,
    /** Its avatar; null for a transfer, and for a transaction whose category is gone. */
    val category: Category?,
    /** Who it was with, as typed: a payee the app knows by its logo is drawn as that logo instead of the category's icon. */
    val payee: String? = null,
    val amount: Long,
    val money: MoneyFormatter,
    /** "12 Sep, 6:30 PM". */
    val moment: String,
    val recurring: Boolean,
    val rows: List<ReceiptRow>,
    val footnotes: List<String>,
)

/** [t] as a receipt: who it was with, the amount, and the facts in the order they are asked about. */
fun receiptOf(t: FinanceTransaction, finance: FinanceState, is24Hour: Boolean): ReceiptData {
    val category = finance.category(t.categoryId)
    val account = finance.account(t.accountId)
    val to = finance.account(t.toAccountId)
    val isTransfer = t.type == TransactionType.Transfer
    val rows = buildList {
        add(ReceiptRow("Date", shortDate(t.occurredAt)))
        add(ReceiptRow("Time", timeLabel(t.occurredAt, is24Hour)))
        if (isTransfer) {
            add(ReceiptRow("From", account?.name ?: "Unknown"))
            add(ReceiptRow("To", to?.name ?: "Unknown"))
        } else {
            add(ReceiptRow("Category", category?.let { finance.categories.pathName(it) } ?: "Uncategorized"))
            add(ReceiptRow(if (t.type == TransactionType.Income) "Into" else "Paid from", account?.name ?: "Unknown"))
            finance.person(t.personId)?.let { add(ReceiptRow("For", it.name)) }
        }
        if (t.note != null) add(ReceiptRow("Note", t.note))
        t.id?.let { add(ReceiptRow("Reference", receiptReference(it))) }
    }
    val footnotes = buildList {
        if (t.recurringId != null) add("Recorded automatically by a recurring payment")
        add(
            if (kotlin.math.abs(java.time.Duration.between(t.createdAt, t.updatedAt).seconds) > 1) {
                "Added ${dateTimeLabel(t.createdAt, is24Hour)} · Edited ${dateTimeLabel(t.updatedAt, is24Hour)}"
            } else {
                "Added ${dateTimeLabel(t.createdAt, is24Hour)}"
            },
        )
    }
    return ReceiptData(
        title = t.payee ?: if (isTransfer) "Transfer" else category?.name ?: "Uncategorized",
        type = t.type,
        category = if (isTransfer) null else category,
        payee = if (isTransfer) null else t.payee,
        amount = t.amount,
        money = finance.money,
        moment = dateTimeLabel(t.occurredAt, is24Hour),
        recurring = t.recurringId != null,
        rows = rows,
        footnotes = footnotes,
    )
}

/** The number a receipt carries to say which transaction it is: "NP-000123". */
fun receiptReference(id: Long): String = "NP-" + id.toString().padStart(6, '0')

private val TicketCorner = 28.dp
private val TearNotch = 12.dp
private val ScallopTarget = 18.dp

/**
 * A transaction as a paper receipt in the app's own colors: a badge, who it was with, and the amount above a torn line
 * (a dashed rule with a notch bitten out of each side); the details below it, under a scalloped bottom edge. Only the
 * receipt - the sheet around it holds the actions - so it can be photographed whole.
 */
@Composable
fun TransactionReceipt(data: ReceiptData, modifier: Modifier = Modifier) {
    // The receipt sets its own ink: it is a piece of paper, and is drawn again as a picture outside any surface that would give it one.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { ReceiptPaper(data, modifier) }
}

@Composable
private fun ReceiptPaper(data: ReceiptData, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val text = MaterialTheme.typography
    val ticket = scheme.surfaceContainerHigh
    val edge = scheme.outlineVariant
    val rule = scheme.outline.copy(alpha = 0.55f)
    // Where the torn line runs: the bottom of the top half, once that has been measured.
    var tear by remember { mutableIntStateOf(0) }

    Column(
        modifier
            .fillMaxWidth()
            .drawBehind { drawReceipt(tear.toFloat(), ticket, edge, rule) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { tear = it.height }
                .padding(start = AppSpacing.xxl, top = AppSpacing.xxxl, end = AppSpacing.xxl, bottom = AppSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ReceiptBadge(data)
            Spacer(Modifier.height(AppSpacing.lg))
            Text(
                data.title,
                style = text.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TransactionTypeTag(data.type)
                if (data.recurring) {
                    Spacer(Modifier.width(AppSpacing.sm))
                    Icon(Icons.Rounded.Repeat, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text("Recurring", style = text.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(AppSpacing.lg))
            HeroTransactionAmount(
                type = data.type,
                amount = data.amount,
                money = data.money,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "${data.type.label} ${data.money.format(data.amount)}" },
            )
            Spacer(Modifier.height(AppSpacing.xs))
            Text(data.moment, style = text.bodyMedium, color = scheme.onSurfaceVariant)
        }
        Column(
            Modifier
                .fillMaxWidth()
                // Clear of the scalloped edge below.
                .padding(start = AppSpacing.xxl, top = AppSpacing.xl, end = AppSpacing.xxl, bottom = AppSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            for (row in data.rows) ReceiptLine(row)
            Spacer(Modifier.height(AppSpacing.xs))
            for (note in data.footnotes) {
                Text(
                    note,
                    style = text.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The category's avatar (the swap mark for a transfer) with the small tick that says it is on the books. */
@Composable
private fun ReceiptBadge(data: ReceiptData) {
    val ticket = MaterialTheme.colorScheme.surfaceContainerHigh
    val done = StatusTone.Success.resolve()
    Box(Modifier.clearAndSetSemantics { }) {
        if (data.type == TransactionType.Transfer) TransferAvatar(size = 64.dp) else CategoryAvatar(data.category, size = 64.dp, payee = data.payee)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp)
                .size(26.dp)
                .background(done.color, CircleShape)
                // A ring in the ticket's own color, so the tick reads as laid over the avatar.
                .border(3.dp, ticket, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = done.onColor, modifier = Modifier.size(14.dp))
        }
    }
}

/** "Paid from ........ HDFC Savings": the label gives way to the value, which may wrap, aligned to the end. */
@Composable
private fun ReceiptLine(row: ReceiptRow) {
    val scheme = MaterialTheme.colorScheme
    val text = MaterialTheme.typography
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Text(row.label, style = text.bodyMedium, color = scheme.onSurfaceVariant)
        Text(
            row.value,
            style = text.bodyLarge.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR_FIGURES),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The receipt's paper: rounded at the top, a half-circle notch bitten out of each side at the torn line [tear] (with a
 * dashed rule between them) and a row of circular bites along the bottom edge, all inside one hairline.
 */
private fun DrawScope.drawReceipt(tear: Float, fill: Color, edge: Color, rule: Color) {
    val w = size.width
    val h = size.height
    val corner = TicketCorner.toPx()
    val notch = TearNotch.toPx()
    // Whole bites, so the row ends in a point at each corner, like a ticket's.
    val bites = max(6, (w / ScallopTarget.toPx()).roundToInt())
    val bite = w / bites / 2f
    val torn = tear > notch && tear < h - notch
    val path = Path().apply {
        moveTo(corner, 0f)
        lineTo(w - corner, 0f)
        arcTo(Rect(w - 2 * corner, 0f, w, 2 * corner), -90f, 90f, false)
        if (torn) {
            lineTo(w, tear - notch)
            arcTo(Rect(w - notch, tear - notch, w + notch, tear + notch), -90f, -180f, false)
        }
        lineTo(w, h)
        for (i in 0 until bites) {
            val cx = w - bite - 2 * bite * i
            arcTo(Rect(cx - bite, h - bite, cx + bite, h + bite), 0f, -180f, false)
        }
        if (torn) {
            lineTo(0f, tear + notch)
            arcTo(Rect(-notch, tear - notch, notch, tear + notch), 90f, -180f, false)
        }
        lineTo(0f, corner)
        arcTo(Rect(0f, 0f, 2 * corner, 2 * corner), 180f, 90f, false)
        close()
    }
    drawPath(path, fill)
    drawPath(path, edge.copy(alpha = 0.7f), style = Stroke(1.dp.toPx()))
    if (torn) {
        val inset = notch + 6.dp.toPx()
        drawLine(
            rule,
            Offset(inset, tear),
            Offset(w - inset, tear),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
        )
    }
}
