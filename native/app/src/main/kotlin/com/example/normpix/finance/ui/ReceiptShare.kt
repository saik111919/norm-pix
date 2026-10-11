package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.AppLoadingIndicator
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import kotlinx.coroutines.launch

/**
 * The ways a receipt can leave the app: drawn once more into a picture (works), or onto a page (still to be built).
 * [TransactionReceipt] is what either draws.
 */
enum class ReceiptFormat(val label: String, val description: String, val icon: ImageVector, val available: Boolean) {
    Image("Image", "A picture of the receipt, just as it looks here", Icons.Outlined.Image, available = true),
    Pdf("PDF", "One page, to print or keep", Icons.Outlined.PictureAsPdf, available = false),
}

/**
 * "Share receipt": one row per [ReceiptFormat]. The image is made by [onImage] (true once it has been offered to the system
 * share sheet, which then closes this one through [onShared]); the PDF says, in place, that it is not available yet instead
 * of pretending - and nothing is sent.
 */
@Composable
fun ShareReceiptSheet(onImage: suspend () -> Boolean, onShared: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(bottom = AppSpacing.xl)) {
        Column(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.sm)) {
            SheetHeader(title = "Share receipt", subtitle = "Sent in the same design as the receipt")
        }
        for (format in ReceiptFormat.entries) {
            ListRow(
                headline = "Share as ${format.label}",
                supporting = format.description,
                leading = { LeadingIcon(format.icon) },
                trailing = {
                    when {
                        format == ReceiptFormat.Image && sharing -> AppLoadingIndicator(Modifier.size(24.dp))
                        !format.available -> Text(
                            "Coming soon",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onTertiaryContainer,
                            modifier = Modifier
                                .background(scheme.tertiaryContainer, AppRadius.rFull)
                                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                        )
                    }
                },
                enabled = !sharing,
                onClick = {
                    haptics.selection()
                    if (!format.available) {
                        note = "Sharing as ${format.label} isn't available yet. It's still being built, so nothing was sent."
                    } else {
                        scope.launch {
                            sharing = true
                            note = null
                            val shared = try {
                                onImage()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.w("ReceiptShare", "Could not prepare the receipt image", e)
                                false
                            } finally {
                                sharing = false
                            }
                            if (shared) onShared() else note = "Couldn't prepare the image. Try again."
                        }
                    }
                },
            )
        }
        note?.let { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpacing.xxl, end = AppSpacing.xxl, top = AppSpacing.md)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
