package com.example.normpix.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.rememberReducedMotion
import com.example.normpix.ui.theme.resolve
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch

/** What a snackbar reports. It picks the badge at the snackbar's start: its icon and its [StatusTone]. */
enum class SnackbarKind(val tone: StatusTone, val icon: ImageVector) {
    /** Something was added, saved or changed. */
    Done(StatusTone.Success, Icons.Rounded.Check),

    /** Something was deleted or removed. */
    Deleted(StatusTone.Neutral, Icons.Outlined.DeleteOutline),

    /** Worth knowing; nothing went wrong ("That tag is already registered"). */
    Info(StatusTone.Info, Icons.Outlined.Info),

    /** Can't be done until the user does something first ("Turn on NFC"). */
    Warning(StatusTone.Warning, Icons.Rounded.WarningAmber),

    /** It failed. */
    Error(StatusTone.Error, Icons.Rounded.PriorityHigh),
}

/** A snackbar's text and action, and the [kind] of thing it reports. */
internal class AppSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    val kind: SnackbarKind,
) : SnackbarVisuals {
    override val withDismissAction: Boolean get() = false
    override val duration: SnackbarDuration get() = SnackbarDuration.Short
}

/** Widest a snackbar gets on a tablet or desktop window; on a phone it spans the screen, less the page gutters. */
private val SnackbarMaxWidth = 560.dp

private val BadgeSize = 36.dp

/** How far a snackbar rises as it comes in. */
private val RiseDistance = 16.dp

/** A fling at least this fast swipes a snackbar away, however short the drag. */
private val FlingVelocity = 600.dp

/** The share of its width a snackbar has to be dragged to go when let go. */
private const val SWIPE_AWAY_FRACTION = 0.35f

/**
 * The app's snackbar: a floating rounded tile in the theme's inverse surface - dark on a light theme, light on a dark one -
 * so it stands out from any page and from the navigation dock under it. A tonal badge says what kind of news it is
 * ([SnackbarKind]); the text says the rest, so the colour is never the only signal. It rises into place, and can be
 * swiped away sideways.
 */
@Composable
internal fun AppSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val kind = (data.visuals as? AppSnackbarVisuals)?.kind ?: SnackbarKind.Done
    val role = kind.tone.resolve()
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()

    val rise = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(AppMotion.MEDIUM_MS, easing = AppMotion.enter)) }

    val swipe = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val settle = AppMotion.spatial<Float>()
    val leaveMs = AppMotion.duration(AppMotion.SHORT_MS)
    val flingPx = with(density) { FlingVelocity.toPx() }
    val risePx = with(density) { RiseDistance.toPx() }

    Surface(
        modifier = modifier
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
            .widthIn(max = SnackbarMaxWidth)
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
            .graphicsLayer {
                translationX = swipe.value
                translationY = (1f - rise.value) * risePx
                alpha = if (width > 0) 1f - (abs(swipe.value) / width).coerceIn(0f, 1f) else 1f
            }
            .draggable(
                state = rememberDraggableState { delta -> scope.launch { swipe.snapTo(swipe.value + delta) } },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    val gone = abs(swipe.value) > width * SWIPE_AWAY_FRACTION || abs(velocity) > flingPx
                    if (gone && width > 0) {
                        val direction = if (swipe.value != 0f) sign(swipe.value) else sign(velocity)
                        swipe.animateTo(direction * width, tween(leaveMs, easing = AppMotion.exit))
                        data.dismiss()
                    } else {
                        swipe.animateTo(0f, settle)
                    }
                },
            ),
        shape = AppRadius.rXl,
        color = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, scheme.inverseOnSurface.copy(alpha = 0.12f)),
    ) {
        Row(
            Modifier
                .heightIn(min = BadgeSize + AppSpacing.sm * 2)
                .padding(start = AppSpacing.sm, end = if (data.visuals.actionLabel != null) AppSpacing.xs else AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The page's own tonal colours for this kind of news; on the inverse surface they read as a bright badge
            // (light theme) or a deep one (dark theme), either way well apart from the tile.
            Box(
                Modifier.size(BadgeSize).background(role.container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(kind.icon, contentDescription = null, tint = role.onContainer, modifier = Modifier.size(AppIconSizes.md))
            }
            Spacer(Modifier.width(AppSpacing.md))
            Text(
                data.visuals.message,
                modifier = Modifier.weight(1f).padding(vertical = AppSpacing.md),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val action = data.visuals.actionLabel
            if (action != null) {
                Spacer(Modifier.width(AppSpacing.xs))
                TextButton(
                    onClick = data::performAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.inversePrimary),
                ) { Text(action, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
