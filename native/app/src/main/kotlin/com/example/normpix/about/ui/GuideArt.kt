package com.example.normpix.about.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.about.GuideTopic
import com.example.normpix.finance.ui.paletteColor
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ui.SwatchCircle
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppTheme
import com.example.normpix.ui.theme.isDark

/** The size every picture is drawn at; it is then scaled to the room it has (see [GuideArt]). */
private val ART_WIDTH = 320.dp
private val ART_HEIGHT = 240.dp

/**
 * The picture of a guide topic: a small drawing of the screen or the thing the topic is about, on the topic's color, in the colors
 * the app is wearing - so it follows the theme (light, dark, Pure, the wallpaper's colors) and the interface font, and is never out
 * of date the way a screenshot would be.
 *
 * It is drawn at one fixed size and scaled to the width it is given, so a card on the About page and the picture at the head of a
 * topic are the same drawing. Text in it ignores the font size the user chose, for the same reason: it is a picture, not a page.
 * It is inert and hidden from screen readers; the words around it say everything it shows.
 */
@Composable
internal fun GuideArt(topic: GuideTopic, modifier: Modifier = Modifier, shape: Shape = AppRadius.rXl) {
    val tone = paletteColor(topic.hue)
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(ART_WIDTH / ART_HEIGHT)
            .clip(shape)
            .background(tone.container)
            .clearAndSetSemantics {},
    ) {
        val scale = maxWidth / ART_WIDTH
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
            Box(
                Modifier
                    .requiredSize(ART_WIDTH, ART_HEIGHT)
                    .align(Alignment.Center)
                    .graphicsLayer(scaleX = scale, scaleY = scale),
            ) {
                when (topic) {
                    GuideTopic.AddTransaction -> AddTransactionArt()
                    GuideTopic.ShareReceipt -> ShareReceiptArt()
                    GuideTopic.Home -> HomeArt()
                    GuideTopic.Activity -> ActivityArt()
                    GuideTopic.Plans -> PlansArt()
                    GuideTopic.Insights -> InsightsArt()
                    GuideTopic.Wallet -> WalletArt(topic.hue)
                    GuideTopic.CardExchange -> CardExchangeArt()
                    GuideTopic.Widgets -> WidgetsArt()
                    GuideTopic.LookAndFeel -> LookAndFeelArt()
                    GuideTopic.Backup -> BackupArt()
                    GuideTopic.Privacy -> PrivacyArt()
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------- parts

/** A line of text in a drawing: a rounded bar, faint by default. */
@Composable
private fun Bar(width: Dp, height: Dp = 6.dp, strong: Boolean = false, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(modifier.width(width).height(height).background(ink.copy(alpha = if (strong) 0.55f else 0.16f), CircleShape))
}

/** The screen of a phone rising from the foot of the picture, the page's colors on it. */
@Composable
private fun BoxScope.MockScreen(width: Dp = 196.dp, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .width(width)
            .height(214.dp)
            .background(scheme.surface, shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** A small label: filled when [selected], tonal when not. */
@Composable
private fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .height(20.dp)
            .background(if (selected) scheme.primary else scheme.surfaceContainerHighest, CircleShape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
    }
}

/** A transaction in a list: its category's disc, two lines and an amount. */
@Composable
private fun TxnRow(hue: Int, modifier: Modifier = Modifier, amount: Dp = 30.dp) {
    val tone = paletteColor(hue)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(24.dp).background(tone.container, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(9.dp).background(tone.accent, CircleShape))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Bar(54.dp, 6.dp, strong = true)
            Bar(34.dp, 5.dp)
        }
        Bar(amount, 7.dp, strong = true)
    }
}

/** A round button with an icon, as a swipe action or a floating button. */
@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, background: Color, tint: Color, size: Dp = 26.dp) {
    Box(Modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** A phone seen whole, small, with [content] on its screen. */
@Composable
private fun MiniPhone(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .size(width = 84.dp, height = 128.dp)
            .background(scheme.surface, shape)
            .border(2.dp, scheme.outline, shape),
        content = content,
    )
}

/** A card of the wallet: a color, its last digits, a chip. */
@Composable
private fun MiniCard(color: Color, modifier: Modifier = Modifier, digits: Boolean = true) {
    val ink = if (color.luminance() > 0.5f) Color.Black else Color.White
    Box(modifier.background(color, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Box(Modifier.size(width = 14.dp, height = 10.dp).background(ink.copy(alpha = 0.35f), RoundedCornerShape(3.dp)))
        if (digits) {
            Text(
                "•••• 4242",
                fontSize = 9.sp,
                color = ink.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.BottomStart),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// ----------------------------------------------------------------------------------------------- pictures

@Composable
private fun BoxScope.AddTransactionArt() {
    val scheme = MaterialTheme.colorScheme
    MockScreen {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Pill("Expense", selected = true, modifier = Modifier.weight(1f))
            Pill("Income", selected = false, modifier = Modifier.weight(1f))
            Pill("Transfer", selected = false, modifier = Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) { Bar(90.dp, 18.dp, strong = true) }
        for (label in listOf(60.dp, 44.dp, 52.dp)) {
            Row(
                Modifier.fillMaxWidth().height(26.dp).background(scheme.surfaceContainer, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Bar(label, 6.dp) }
        }
    }
    Box(Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 22.dp)) {
        RoundIcon(Icons.Rounded.Add, scheme.primary, scheme.onPrimary, size = 44.dp)
    }
}

@Composable
private fun BoxScope.ShareReceiptArt() {
    val scheme = MaterialTheme.colorScheme
    val tone = paletteColor(5)
    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // The receipt, as a payment app shows it.
        Column(
            Modifier
                .rotate(-5f)
                .size(width = 92.dp, height = 132.dp)
                .background(scheme.surface, RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            RoundIcon(Icons.Rounded.Check, tone.accent, if (tone.accent.luminance() > 0.5f) Color.Black else Color.White, size = 22.dp)
            Bar(50.dp, 7.dp, strong = true)
            Bar(66.dp, 5.dp)
            Bar(58.dp, 5.dp)
            Bar(40.dp, 5.dp)
            Spacer(Modifier.height(2.dp))
            Bar(34.dp, 9.dp, strong = true)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = tone.onContainer, modifier = Modifier.size(24.dp))
        // The form it fills in.
        Column(
            Modifier
                .size(width = 104.dp, height = 132.dp)
                .background(scheme.surface, RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            for ((label, value) in listOf("Amount" to 42.dp, "Payee" to 54.dp, "Account" to 46.dp, "Category" to 38.dp)) {
                Text(label, fontSize = 8.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                Box(
                    Modifier.fillMaxWidth().height(16.dp).background(scheme.primary.copy(alpha = 0.16f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { Bar(value, 5.dp, strong = true) }
            }
        }
    }
}

@Composable
private fun BoxScope.HomeArt() {
    val scheme = MaterialTheme.colorScheme
    MockScreen {
        Column(
            Modifier.fillMaxWidth().background(scheme.primaryContainer, RoundedCornerShape(14.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.width(40.dp).height(5.dp).background(scheme.onPrimaryContainer.copy(alpha = 0.5f), CircleShape))
            Box(Modifier.width(84.dp).height(14.dp).background(scheme.onPrimaryContainer.copy(alpha = 0.85f), CircleShape))
        }
        Row(
            Modifier.fillMaxWidth().background(scheme.errorContainer, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(10.dp).background(scheme.onErrorContainer.copy(alpha = 0.7f), CircleShape))
            Box(Modifier.width(70.dp).height(5.dp).background(scheme.onErrorContainer.copy(alpha = 0.6f), CircleShape))
        }
        TxnRow(hue = 1)
        TxnRow(hue = 4, amount = 24.dp)
        TxnRow(hue = 6, amount = 36.dp)
    }
}

@Composable
private fun BoxScope.ActivityArt() {
    val scheme = MaterialTheme.colorScheme
    MockScreen {
        Row(
            Modifier.fillMaxWidth().height(24.dp).background(scheme.surfaceContainerHigh, CircleShape).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
            Bar(48.dp, 5.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("This month", selected = true)
            Pill("All time", selected = false)
            Icon(Icons.Rounded.FilterList, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
        TxnRow(hue = 2)
        // A row swiped open, its actions showing.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TxnRow(hue = 3, modifier = Modifier.weight(1f), amount = 24.dp)
            RoundIcon(Icons.Outlined.Edit, scheme.secondaryContainer, scheme.onSecondaryContainer)
            RoundIcon(Icons.Outlined.DeleteOutline, scheme.errorContainer, scheme.onErrorContainer)
        }
        TxnRow(hue = 5, amount = 36.dp)
    }
}

@Composable
private fun BoxScope.PlansArt() {
    val scheme = MaterialTheme.colorScheme
    MockScreen {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Pill("Budgets", selected = true, modifier = Modifier.weight(1f))
            Pill("Recurring", selected = false, modifier = Modifier.weight(1f))
            Pill("Reminders", selected = false, modifier = Modifier.weight(1f))
        }
        for ((hue, used) in listOf(1 to 0.62f, 4 to 0.36f, 0 to 1f)) {
            val tone = paletteColor(hue)
            val bar = if (used >= 1f) scheme.error else tone.accent
            Column(
                Modifier.fillMaxWidth().background(scheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Bar(48.dp, 6.dp, strong = true)
                    Bar(26.dp, 5.dp)
                }
                Box(Modifier.fillMaxWidth().height(7.dp).background(scheme.onSurface.copy(alpha = 0.1f), CircleShape)) {
                    Box(Modifier.fillMaxWidth(used).height(7.dp).background(bar, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun BoxScope.InsightsArt() {
    val scheme = MaterialTheme.colorScheme
    val hues = listOf(1, 4, 6)
    MockScreen {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Pill("October", selected = true)
            Pill("Custom range", selected = false)
        }
        // The month, day by day.
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            for ((index, height) in listOf(20, 34, 26, 44, 30, 52, 38).withIndex()) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(height.dp)
                        .background(if (index == 5) scheme.primary else scheme.primary.copy(alpha = 0.28f), RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)),
                )
            }
        }
        // Where it went.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val colors = hues.map { paletteColor(it).accent }
            Canvas(Modifier.size(46.dp)) {
                val stroke = 10.dp.toPx()
                val inset = stroke / 2f
                var start = -90f
                for ((index, sweep) in listOf(150f, 110f, 86f).withIndex()) {
                    drawArc(
                        color = colors[index],
                        startAngle = start,
                        sweepAngle = sweep - 3f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                    start += sweep
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (index in hues.indices) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).background(colors[index], CircleShape))
                        Bar(listOf(54.dp, 42.dp, 34.dp)[index], 5.dp, strong = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.WalletArt(hue: Int) {
    val scheme = MaterialTheme.colorScheme
    val front = paletteColor(hue).accent
    val back = paletteColor(2).accent
    Box(Modifier.align(Alignment.Center)) {
        MiniCard(back, Modifier.size(width = 176.dp, height = 108.dp).offset(x = (-22).dp, y = (-20).dp).rotate(-7f), digits = false)
        MiniCard(front, Modifier.size(width = 176.dp, height = 108.dp).offset(x = 14.dp, y = 14.dp).rotate(3f))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = 24.dp, y = 30.dp)) {
            RoundIcon(Icons.Outlined.Lock, scheme.surface, scheme.onSurface, size = 34.dp)
        }
    }
}

@Composable
private fun BoxScope.CardExchangeArt() {
    val scheme = MaterialTheme.colorScheme
    val tone = paletteColor(0)
    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MiniPhone(Modifier.rotate(-4f)) {
            MiniCard(tone.accent, Modifier.align(Alignment.Center).size(width = 62.dp, height = 40.dp), digits = false)
        }
        Box(Modifier.size(64.dp).background(tone.accent.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(42.dp).background(scheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Nfc, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(24.dp))
            }
        }
        MiniPhone(Modifier.rotate(4f)) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 62.dp, height = 40.dp)
                    .border(2.dp, tone.accent, RoundedCornerShape(10.dp)),
            )
        }
    }
}

@Composable
private fun BoxScope.WidgetsArt() {
    val scheme = MaterialTheme.colorScheme
    val tone = paletteColor(2)
    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // The clock: a face of rings, the hour in the middle and the minute in a pill on the ring.
        Box(Modifier.size(100.dp).background(scheme.surface, CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(88.dp)) {
                drawCircle(tone.accent.copy(alpha = 0.35f), radius = size.minDimension / 2f - 2.dp.toPx(), style = Stroke(3.dp.toPx()))
                drawCircle(tone.accent.copy(alpha = 0.18f), radius = size.minDimension / 3f, style = Stroke(2.dp.toPx()))
            }
            Text("10", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Box(
                Modifier.offset { IntOffset(26.dp.roundToPx(), (-26).dp.roundToPx()) }.background(tone.accent, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("09", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (tone.accent.luminance() > 0.5f) Color.Black else Color.White)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Recent transactions.
            Column(
                Modifier.width(150.dp).background(scheme.surface, RoundedCornerShape(18.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text("Recent", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                TxnRow(hue = 1, amount = 26.dp)
                TxnRow(hue = 4, amount = 20.dp)
            }
            // Add a transaction.
            Row(
                Modifier.width(150.dp).background(scheme.surface, CircleShape).padding(6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIcon(Icons.Rounded.Add, scheme.primary, scheme.onPrimary)
                RoundIcon(Icons.AutoMirrored.Rounded.CallReceived, scheme.secondaryContainer, scheme.onSecondaryContainer)
                RoundIcon(Icons.Rounded.SwapHoriz, scheme.tertiaryContainer, scheme.onTertiaryContainer)
            }
        }
    }
}

@Composable
private fun BoxScope.LookAndFeelArt() {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDark
    val accents = remember { listOf(AccentColor.Pure, AccentColor.Volt, AccentColor.Blue, AccentColor.Orange, AccentColor.Purple) }
    Column(
        Modifier.align(Alignment.Center).width(262.dp).background(scheme.surface, RoundedCornerShape(24.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Pill("Light", selected = !dark, modifier = Modifier.weight(1f))
            Pill("Dark", selected = dark, modifier = Modifier.weight(1f))
            Pill("System", selected = false, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((index, accent) in accents.withIndex()) {
                val swatch = remember(accent, dark) { AppTheme.paletteSwatch(AppTheme.accentScheme(accent, dark)) }
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .background(scheme.surfaceContainerHighest, RoundedCornerShape(14.dp))
                        .then(if (index == 0) Modifier.border(2.dp, scheme.primary, RoundedCornerShape(14.dp)) else Modifier)
                        .padding(5.dp),
                ) { SwatchCircle(swatch, Modifier.fillMaxWidth().aspectRatio(1f), edge = scheme.onSurface.copy(alpha = 0.16f)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Bar(70.dp, 6.dp, strong = true)
            Bar(40.dp, 6.dp)
        }
    }
}

@Composable
private fun BoxScope.BackupArt() {
    val scheme = MaterialTheme.colorScheme
    val tone = paletteColor(5)
    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.size(width = 84.dp, height = 100.dp).background(scheme.surface, RoundedCornerShape(16.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.FolderZip, contentDescription = null, tint = tone.accent, modifier = Modifier.size(38.dp))
            Spacer(Modifier.height(4.dp))
            Text(".zip", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = tone.onContainer, modifier = Modifier.size(24.dp))
        MiniPhone {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Restore, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(30.dp))
                Bar(40.dp, 5.dp, strong = true)
                Bar(28.dp, 5.dp)
            }
        }
    }
}

@Composable
private fun BoxScope.PrivacyArt() {
    val scheme = MaterialTheme.colorScheme
    val tone = paletteColor(0)
    Box(Modifier.align(Alignment.Center).size(width = 240.dp, height = 190.dp)) {
        Box(
            Modifier.align(Alignment.Center).size(104.dp).background(scheme.surface, CircleShape).border(3.dp, tone.accent.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Lock, contentDescription = null, tint = tone.accent, modifier = Modifier.size(48.dp)) }
        Box(Modifier.align(Alignment.TopStart).offset(x = 12.dp, y = 14.dp)) {
            RoundIcon(Icons.Rounded.Fingerprint, scheme.surface, scheme.onSurface, size = 44.dp)
        }
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = 22.dp)) {
            RoundIcon(Icons.Outlined.Shield, scheme.surface, scheme.onSurface, size = 40.dp)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).background(scheme.surface, CircleShape).padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("•••• •••• •••• 4242", fontSize = 11.sp, color = scheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        }
    }
}
