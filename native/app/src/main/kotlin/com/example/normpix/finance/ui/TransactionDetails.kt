package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.ui.components.ConnectedButtonGroup
import com.example.normpix.ui.components.GroupButton
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.solidColorScheme
import com.example.normpix.ui.theme.AppSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DetailAction { Edit, Remind, Delete }

/**
 * One transaction as a receipt - the badge, who it was with and the amount above a torn line, the details below it - with
 * what can be done with it underneath: Share (as an image now, a PDF to come), and Edit and Delete as one grouped control.
 * Shown in a bottom sheet that opens compact and drags to full height when there is a lot to read.
 *
 * Share is the loudest action, as it is the one that takes the receipt somewhere; Edit is the quieter way on, and Delete
 * stays visibly destructive.
 */
@Composable
fun TransactionDetailsSheet(
    transaction: FinanceTransaction,
    /** Reads the recorded receipt back as a bitmap for the picture that is shared (a seam: a test has no GPU to read it from). */
    snapshot: suspend (GraphicsLayer) -> Bitmap = { it.toImageBitmap().asAndroidBitmap() },
    /** Whether "Remind me" is offered (a reminder about this transaction, already attached to it). */
    canRemind: Boolean = false,
    onAction: (DetailAction?) -> Unit,
) {
    val finance = rememberFinanceState()
    val scheme = MaterialTheme.colorScheme
    // The sheet's own tone, solid: the receipt's paper is a tint over it (the sheet lets the page show through), so the picture
    // has to be laid on this and not on the tint, which would leave it as transparent as the sheet is.
    val surface = solidColorScheme.surfaceContainerLow
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    val receipts = LocalAppContainer.current.receipts
    val density = LocalDensity.current
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val receipt = receiptOf(transaction, finance, is24Hour)
    // What the receipt draws is recorded as it is drawn, so it can be drawn again as the picture that is shared.
    val layer = rememberGraphicsLayer()
    val picture: suspend () -> Boolean = {
        val shot = snapshot(layer)
        val png = withContext(Dispatchers.Default) {
            framedReceiptPng(shot, background = surface.toArgb(), margin = with(density) { PICTURE_MARGIN.roundToPx() })
        }
        receipts.shareImage(png, "NormPix-receipt-${transaction.id?.let(::receiptReference) ?: "new"}.png")
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = AppSpacing.xl, end = AppSpacing.xl, bottom = AppSpacing.xl),
    ) {
        TransactionReceipt(
            receipt,
            Modifier.drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        )
        Spacer(Modifier.height(AppSpacing.lg))
        Button(
            // The options sheet opens over this one, so the receipt stays where it is behind it.
            onClick = {
                scope.launch {
                    overlays.sheet<Boolean> { dismiss -> ShareReceiptSheet(onImage = picture, onShared = { dismiss(true) }) }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpacing.sm))
            Text("Share receipt")
        }
        Spacer(Modifier.height(AppSpacing.sm))
        ConnectedButtonGroup(
            buttons = buildList {
                // One calm fill for all: only Delete's words are in the warning colour, so it is visibly destructive without shouting.
                add(GroupButton("Edit", Icons.Outlined.Edit, scheme.surfaceContainerHighest, scheme.onSurface) { onAction(DetailAction.Edit) })
                if (canRemind) {
                    add(GroupButton("Remind", Icons.Outlined.NotificationAdd, scheme.surfaceContainerHighest, scheme.onSurface) { onAction(DetailAction.Remind) })
                }
                add(GroupButton("Delete", Icons.Outlined.DeleteOutline, scheme.surfaceContainerHighest, scheme.error) { onAction(DetailAction.Delete) })
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The room round the receipt in the picture that is shared. */
private val PICTURE_MARGIN = 24.dp

/** The signed amount at display size in the type's own color; shrinks rather than wraps for very large sums. */
@Composable
fun HeroTransactionAmount(type: TransactionType, amount: Long, money: com.example.normpix.finance.domain.MoneyFormatter, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.displayMedium
    Text(
        signedAmount(money, type, amount),
        modifier = modifier,
        style = style.copy(fontFeatureSettings = TABULAR_FIGURES, fontWeight = FontWeight.SemiBold),
        color = type.role().color,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = style.fontSize),
    )
}
