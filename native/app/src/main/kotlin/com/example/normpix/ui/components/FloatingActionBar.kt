package com.example.normpix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing

object FloatingActionBarDefaults {
    /** How much of a form's end fades out above the bar (see [fadingBottomEdge]). */
    val FadeHeight: Dp = AppSpacing.lg

    /** Soft shadow elevation for the floating pill bar. */
    val Elevation: Dp = AppSpacing.sm // 8.dp

    /** Inner padding around the bar's actions. */
    val InnerPadding: Dp = AppSpacing.sm // 8.dp

    /** Minimum height of the primary action button. */
    val MinPrimaryHeight: Dp = 48.dp
}

/**
 * Fades the last [height] of a scrolling form out into the sheet, just above a [FloatingActionBar], so the form goes on under the
 * bar's top edge without a hard cut - and never behind it. (The form is not laid under the bar on purpose: a field that gets
 * focus is scrolled into the form's own bounds, which would put it behind the bar - the notes field, while typing - and a tap
 * on a field scrolled there would land on the bar.) Needs the form's own bottom padding to be at least [height], so the last
 * field, scrolled to the end, is not faded.
 */
fun Modifier.fadingBottomEdge(height: Dp = FloatingActionBarDefaults.FadeHeight): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = height.toPx().coerceAtMost(size.height)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fade,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - fade),
            size = Size(size.width, fade),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * A secondary 48 dp icon button designed for [FloatingActionBar]: tinted with [onSurfaceVariant] by default.
 */
@Composable
fun FloatingActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
        )
    }
}

/**
 * A floating pill bar laid over the bottom of a sheet or screen: a pill ([AppRadius.rFull]) with a soft
 * shadow, a solid tonal fill taken from [solidColorScheme], and 8 dp inner padding.
 * Secondary icon buttons (0 to 3) sit on the left, and the primary action takes the remaining width.
 */
@Composable
fun FloatingActionBar(
    modifier: Modifier = Modifier,
    containerColor: Color = solidColorScheme.surfaceContainer,
    actions: @Composable RowScope.() -> Unit = {},
    primary: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = AppRadius.rFull,
        color = containerColor,
        shadowElevation = FloatingActionBarDefaults.Elevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(FloatingActionBarDefaults.InnerPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            actions()
            primary()
        }
    }
}

/**
 * Standard [FloatingActionBar] with a single filled primary pill button taking the remaining width.
 */
@Composable
fun FloatingActionBar(
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    primaryIcon: ImageVector? = null,
    primaryColors: ButtonColors? = null,
    containerColor: Color = solidColorScheme.surfaceContainer,
    actions: @Composable RowScope.() -> Unit = {},
) {
    FloatingActionBar(
        modifier = modifier,
        containerColor = containerColor,
        actions = actions,
    ) {
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            shape = AppRadius.rFull,
            colors = primaryColors ?: ButtonDefaults.buttonColors(),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = FloatingActionBarDefaults.MinPrimaryHeight),
        ) {
            if (primaryIcon != null) {
                Icon(primaryIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(AppSpacing.sm))
            }
            Text(primaryLabel, style = SheetButtonText)
        }
    }
}
