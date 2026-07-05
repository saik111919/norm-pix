package com.normpix.cashflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normpix.cashflow.CashflowState
import com.normpix.cashflow.LocalCashTokens
import com.normpix.cashflow.Reminder
import com.normpix.ui.floatingnav.GlassBackdropState
import com.normpix.ui.floatingnav.glassPanel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full-screen alert shown while [CashflowState.alarmFiring] is set — a foreground "preview" of the real alarm. */
@Composable
fun AlarmFiringOverlay(state: CashflowState) {
    var lastAlarm by remember { mutableStateOf<Reminder?>(null) }
    state.alarmFiring?.let { lastAlarm = it }

    AnimatedVisibility(
        visible = state.alarmFiring != null,
        enter = fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.94f),
        exit = fadeOut(tween(200)),
    ) {
        val r = lastAlarm
        if (r != null) {
            AlarmScreenContent(
                icon = r.icon, title = r.title, priority = r.priority, body = alarmBody(state, r),
                onSnooze = { state.snoozeAlarm() }, onDismiss = { state.dismissAlarm() },
            )
        }
    }
}

/**
 * The alarm's visuals — shared by the in-app preview ([AlarmFiringOverlay]) and the real
 * full-screen alarm ([com.normpix.cashflow.AlarmActivity], which fires from a background
 * [android.app.AlarmManager] alarm even when the app process isn't running).
 */
@Composable
fun AlarmScreenContent(
    icon: ImageVector,
    title: String,
    priority: String,
    body: String,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalCashTokens.current
    val bg = if (t.dark) Color(0xFF1C1B1F) else t.p
    val fg = if (t.dark) t.onS else t.onP
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            if (priority == "urgent") "URGENT REMINDER" else "REMINDER",
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(alpha = 0.75f), letterSpacing = 1.sp,
        )
        Text(
            remember { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) },
            fontSize = 44.sp, fontWeight = FontWeight.Light, color = fg, modifier = Modifier.padding(top = 6.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PulsingAlarmIcon(icon, fg)
            Text(
                title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = fg, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                body, fontSize = 14.sp, color = fg.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp).widthIn(max = 300.dp),
            )
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(fg.copy(alpha = 0.16f))
                    .clickable { onSnooze() },
                contentAlignment = Alignment.Center,
            ) { Text("Snooze 10 min", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (t.dark) t.onS else Color.White)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) { Text("Dismiss", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (t.dark) t.surf else t.p) }
        }
    }
}

@Composable
private fun PulsingAlarmIcon(icon: ImageVector, color: Color) {
    val infinite = rememberInfiniteTransition(label = "alarmPulse")
    val scale by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        listOf(0, 650, 1300).forEach { delayMs ->
            val progress by infinite.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(delayMs),
                ),
                label = "ring$delayMs",
            )
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(1f + progress * 0.6f)
                    .clip(CircleShape)
                    .border(2.dp, color.copy(alpha = (1f - progress) * 0.7f), CircleShape),
            )
        }
        Box(
            modifier = Modifier.size(96.dp).scale(scale).clip(CircleShape).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(44.dp))
        }
    }
}

/** Popup menu from long-pressing the floating nav's Inbox tab (design's `inboxMenuNode`). */
@Composable
fun InboxMenuOverlay(state: CashflowState, glass: GlassBackdropState?) {
    val t = LocalCashTokens.current
    if (!state.inboxMenuOpen) return
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { state.inboxMenuOpen = false },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 92.dp)
                .widthIn(min = 232.dp)
                .glassPanel(
                    glass, RoundedCornerShape(20.dp), blurRadius = 24.dp,
                    backgroundColor = t.surf, backgroundOpacity = 0.94f, saturation = 1.5f,
                )
                .padding(6.dp),
        ) {
            InboxMenuItem(
                Icons.Rounded.Inbox, "View synced messages",
                if (state.inbox.isNotEmpty()) "${state.inbox.size} waiting for review" else "Nothing waiting right now",
            ) { state.inboxMenuOpen = false; state.tab = 4; state.subScreen = "syncedMessages" }
            InboxMenuItem(Icons.Rounded.Sync, "Sync SMS", "View all synced messages") {
                state.inboxMenuOpen = false; state.tab = 4; state.subScreen = "syncedMessages"
            }
        }
    }
}

@Composable
private fun InboxMenuItem(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val t = LocalCashTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, 36.dp, bg = t.sc, tint = t.onSc, iconSize = 18.dp)
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onS)
            Text(sub, fontSize = 11.sp, color = t.onSV, modifier = Modifier.padding(top = 1.dp))
        }
    }
}
