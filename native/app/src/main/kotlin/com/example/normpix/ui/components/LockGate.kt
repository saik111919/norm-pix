package com.example.normpix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppSpacing

/**
 * Full-area biometric lock screen, shared by the whole-app lock and the
 * wallet-only lock.
 */
@Composable
fun LockGate(
    title: String,
    message: String,
    unlockLabel: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.LockOpen,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.xxl)
                .widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(80.dp).background(scheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(AppIconSizes.hero))
            }
            Spacer(Modifier.height(AppSpacing.xl))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(AppSpacing.xl))
            Button(onClick = onUnlock) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(AppSpacing.sm))
                Text(unlockLabel)
            }
        }
    }
}
