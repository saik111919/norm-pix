package com.example.normpix.wallet.nfc

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion

/**
 * The sheet shown while the phone waits for an NFC tap: a pulsing contactless mark, what to tap and why, and - when a
 * tag was tapped and refused - the reason (announced to a screen reader), while it keeps waiting. The tag's contents are never read.
 */
@Composable
fun NfcTapSheet(title: String, message: String, notice: String?, onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val pulse = rememberInfiniteTransition(label = "nfc-pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = if (reduced) 0.92f else 1.08f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "scale",
    )
    Column(
        Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).scale(scale).background(scheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Nfc, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(AppIconSizes.xl))
        }
        Spacer(Modifier.height(AppSpacing.lg))
        Text(
            title,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        if (notice != null) {
            Spacer(Modifier.height(AppSpacing.md))
            Text(
                notice,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        Spacer(Modifier.height(AppSpacing.xl))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}
