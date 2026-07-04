package com.normpix.cashflow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import com.normpix.cashflow.CashflowState
import com.normpix.cashflow.LocalCashTokens
import com.normpix.cashflow.Txn
import com.normpix.cashflow.fmt
import com.normpix.ui.floatingnav.LocalGlassBackdropState

/** M3 emphasized easing used throughout the design. */
val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** 40dp circular icon button (design `iconBtn`). */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    bg: Color = LocalCashTokens.current.cont,
    tint: Color = LocalCashTokens.current.onSV,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Circular icon bubble (avatar-style, non-clickable). */
@Composable
fun IconBubble(icon: ImageVector, size: Dp, bg: Color, tint: Color, iconSize: Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Grouped card container: `cont` background, 26dp radius, 8dp padding, 6dp gaps. */
@Composable
fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    val t = LocalCashTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(t.cont)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(label: String, trailing: String? = null, onTrailingClick: (() -> Unit)? = null) {
    val t = LocalCashTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onS)
        if (trailing != null) {
            Text(
                trailing, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.p,
                modifier = if (onTrailingClick != null) Modifier.clickable(onClick = onTrailingClick) else Modifier,
            )
        }
    }
}

/** A transaction row inside a [GroupCard] (design `txRow`). */
@Composable
fun TxRow(tx: Txn, state: CashflowState? = null, onClick: () -> Unit) {
    val t = LocalCashTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(t.hi)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val income = tx.amount > 0
        IconBubble(
            tx.icon, 44.dp,
            bg = if (income) t.pc else t.sc,
            tint = if (income) t.onPc else t.onSc,
            iconSize = 22.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.merchant, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val extra = buildString {
                append(tx.whenLabel); append(" · "); append(tx.account)
                tx.sub?.let { append(" · "); append(it) }
                if (tx.auto) append(" · auto")
            }
            Text(extra, fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 3.dp))
        }
        Text(
            (if (income) "+" else "−") + (state?.fmt(tx.amount) ?: fmt(tx.amount)),
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = if (income) t.p else t.onS,
        )
    }
}

/** Selectable chip (design `chip`): 36dp tall, check icon when selected. */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    cornerRadius: Dp = 18.dp,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val t = LocalCashTokens.current
    val bg by animateColorAsState(if (selected) t.sc else Color.Transparent, tween(200, easing = Emphasized), label = "chipBg")
    val fg = if (selected) t.onSc else t.onSV
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bg)
            .border(1.dp, if (selected) t.sc else t.out, RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            selected -> Icon(Icons.Rounded.Check, null, tint = t.onSc, modifier = Modifier.size(16.dp))
            icon != null -> Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg)
        trailing?.invoke(this)
    }
}

/** Horizontally scrolling chip row that bleeds to the screen edges (design `chipRow`). */
@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Small caption above a field (design `fieldLabel`). */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    val t = LocalCashTokens.current
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.onSV,
        modifier = modifier.padding(start = 4.dp, top = 2.dp),
    )
}

/** 52×32 pill switch with a check on the thumb (design `toggle`). */
@Composable
fun TogglePill(on: Boolean, onClick: () -> Unit) {
    val t = LocalCashTokens.current
    val bg by animateColorAsState(if (on) t.p else t.cont, tween(240, easing = Emphasized), label = "toggleBg")
    val border by animateColorAsState(if (on) t.p else t.out, tween(240, easing = Emphasized), label = "toggleBorder")
    // Design: thumb sits inside the border, not flush against it — the trailing .padding(2.dp)
    // below insets the content area so these offsets leave a consistent 2dp gap on both ends.
    val thumbX by animateDpAsState(if (on) 22.dp else 2.dp, tween(240, easing = Emphasized), label = "toggleThumb")
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbX)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (on) t.onP else t.onSV),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(Icons.Rounded.Check, null, tint = t.p, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * Bottom content padding that clears the floating bar (and the FAB, when shown). Tracks
 * the bar's actual position (16dp or the gesture inset, whichever is larger) + 64dp bar +
 * 50dp breathing room, mirroring the design's 130px scroll padding.
 *
 * The FAB floats independently of scroll position at `bottom = navClearance + 80.dp`
 * with a 60dp size, so its top edge sits `navClearance + 140.dp` up from the screen
 * bottom. Without extra clearance here, scrolling a screen all the way down parks its
 * last row directly behind the FAB — which then swallows taps meant for that row (e.g.
 * the "Floating add button" toggle in Settings). Pass [showFab] so screens reserve that
 * extra space only while the FAB is actually on-screen.
 */
@Composable
fun contentBottomPadding(showFab: Boolean): Dp =
    114.dp +
        (if (showFab) 140.dp else 0.dp) +
        maxOf(16.dp, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())

/**
 * A [ScrollState] that keeps the glass nav-bar backdrop in sync while content scrolls.
 * Scrolling moves children inside their own render layers, which never re-runs the
 * glass source's draw — so without this nudge the bar's blur freezes on stale content.
 */
@Composable
fun rememberGlassSyncedScrollState(): ScrollState {
    val scroll = rememberScrollState()
    val glass = LocalGlassBackdropState.current
    if (glass != null) {
        LaunchedEffect(scroll, glass) {
            snapshotFlow { scroll.value }.collect { glass.invalidate() }
        }
    }
    return scroll
}

/** Settings-style row inside a [GroupCard] (design `settingRow`). */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    sub: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val t = LocalCashTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(t.hi)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, 40.dp, bg = t.sc, tint = t.onSc, iconSize = 20.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
            if (sub != null) Text(sub, fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 3.dp))
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = t.onSV, modifier = Modifier.size(20.dp))
        }
    }
}

/** Dashed rounded border (for the "Add" affordances in the design). */
fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.dp): Modifier = drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()), 0f),
    )
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = stroke,
    )
}

/** Simple placeholder-capable single-line text input matching the design's bordered fields. */
@Composable
fun CashTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    cornerRadius: Dp = 16.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
) {
    val t = LocalCashTokens.current
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .border(1.dp, t.out, RoundedCornerShape(cornerRadius))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = fontSize, color = t.onSV.copy(alpha = 0.7f), maxLines = 1)
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = fontSize, color = t.onS),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(t.p),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Solid filled pill CTA (design's primary "add" button: 56dp tall, 28dp radius, tinted shadow). */
@Composable
fun PrimaryCtaButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalCashTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(8.dp, RoundedCornerShape(28.dp), spotColor = t.p.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(28.dp))
            .background(t.p)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = t.onP, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = t.onP)
    }
}

/** Multi-line bordered text input for longer free-text entry (design's SMS-format textarea). */
@Composable
fun CashTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 84.dp,
    cornerRadius: Dp = 16.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
) {
    val t = LocalCashTokens.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .border(1.dp, t.out, RoundedCornerShape(cornerRadius))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = fontSize, color = t.onSV.copy(alpha = 0.7f))
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = false,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = fontSize, color = t.onS, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(t.p),
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = minHeight),
        )
    }
}

/** Donut progress ring rendered with rounded caps and a full track. */
@Composable
fun ProgressRing(
    pct: Int,
    size: Dp,
    thickness: Dp,
    ringColor: Color,
    trackColor: Color,
    center: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.size(size).drawBehind {
            val strokePx = thickness.toPx()
            val inset = strokePx / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - strokePx, this.size.height - strokePx)
            // Track
            drawArc(
                color = trackColor, startAngle = 0f, sweepAngle = 360f,
                useCenter = false, style = Stroke(strokePx),
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize,
            )
            // Progress — flat caps, matching the design's conic-gradient ring
            drawArc(
                color = ringColor, startAngle = -90f, sweepAngle = 360f * (pct / 100f),
                useCenter = false, style = Stroke(strokePx),
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize,
            )
        },
        contentAlignment = Alignment.Center,
    ) { center() }
}

/** 
 * A smooth area chart used for trends in the design. 
 * Renders a Bezier curve with a gradient fill.
 */
@Composable
fun TrendChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    color: Color,
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val path = Path()
        val width = size.width
        val height = size.height
        val max = data.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val stepX = width / (data.size - 1)

        val points = data.mapIndexed { i, v ->
            androidx.compose.ui.geometry.Offset(i * stepX, height - (v / max) * height)
        }

        path.moveTo(points[0].x, points[0].y)
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val con1 = androidx.compose.ui.geometry.Offset(p1.x + stepX / 2f, p1.y)
            val con2 = androidx.compose.ui.geometry.Offset(p2.x - stepX / 2f, p2.y)
            path.cubicTo(con1.x, con1.y, con2.x, con2.y, p2.x, p2.y)
        }

        // Fill path
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent))
        )

        // Line path
        drawPath(
            path,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Dots
        points.forEachIndexed { i, point ->
            val isLast = i == points.size - 1
            drawCircle(
                color = Color.White,
                radius = (if (isLast) 5.dp else 3.5.dp).toPx(),
                center = point
            )
            drawCircle(
                color = color,
                radius = (if (isLast) 5.dp else 3.5.dp).toPx(),
                center = point,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
