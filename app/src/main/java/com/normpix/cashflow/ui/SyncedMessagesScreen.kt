package com.normpix.cashflow.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normpix.cashflow.CashflowState
import com.normpix.cashflow.InboxMsg
import com.normpix.cashflow.LocalCashTokens
import com.normpix.cashflow.SyncHistoryEntry
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun StatusBadge(status: String) {
    val t = LocalCashTokens.current
    val (label, color) = when (status) {
        "added" -> "Added" to t.p
        "ignored" -> "Ignored" to t.onSV
        else -> "Pending review" to t.p
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = if (t.dark) 0.25f else 0.12f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun SyncedMessageCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    merchant: String,
    sender: String,
    whenLabel: String,
    amount: Double?,
    sms: String,
    status: String,
    state: CashflowState,
    onClick: (() -> Unit)? = null,
) {
    val t = LocalCashTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(t.hi)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, 38.dp, bg = t.sc, tint = t.onSc, iconSize = 18.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(merchant, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onS, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$sender · $whenLabel", fontSize = 11.sp, color = t.onSV, modifier = Modifier.padding(top = 2.dp))
            }
            if (amount != null) {
                Text("−${state.fmt(amount)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = t.onS)
            }
            StatusBadge(status)
        }
        Text(
            sms, fontSize = 11.sp, lineHeight = 16.sp, color = t.onSV, fontFamily = FontFamily.Monospace,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.cont).padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun SyncedMessagesSubScreen(state: CashflowState) {
    val t = LocalCashTokens.current

    LaunchedEffect(state.syncTick) {
        if (state.smsSyncing) {
            delay(900)
            state.finishSync()
        }
    }

    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "Synced messages", "${state.inbox.size + state.syncHistory.size} messages detected on this device")

        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(t.cont).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(t.sc), contentAlignment = Alignment.Center) {
                if (state.smsSyncing) {
                    val infinite = rememberInfiniteTransition(label = "syncSpin")
                    val angle by infinite.animateFloat(
                        initialValue = 0f, targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
                        label = "angle",
                    )
                    Icon(Icons.Rounded.Sync, null, tint = t.onSc, modifier = Modifier.size(20.dp).rotate(angle))
                } else {
                    Icon(Icons.Rounded.Sync, null, tint = t.onSc, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (state.smsSyncing) "Syncing…" else "Check for new messages",
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onS,
                )
                Text(
                    state.smsLastSynced?.let { "Last synced ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it))}" } ?: "Not synced yet",
                    fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(t.p)
                    .clickable { state.syncSms() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Sync now", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.onP)
            }
        }

        if (state.inbox.isNotEmpty()) {
            SectionTitle("Pending review", "${state.inbox.size}")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.inbox.forEach { m: InboxMsg ->
                    SyncedMessageCard(
                        icon = m.icon, merchant = m.merchant, sender = m.sender, whenLabel = m.whenLabel,
                        amount = m.amount, sms = m.sms, status = "pending", state = state,
                        onClick = { state.tab = 2 },
                    )
                }
            }
        }
        if (state.syncHistory.isNotEmpty()) {
            SectionTitle("History")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.syncHistory.forEach { m: SyncHistoryEntry ->
                    SyncedMessageCard(
                        icon = m.icon, merchant = m.merchant, sender = m.sender, whenLabel = m.whenLabel,
                        amount = m.amount, sms = m.sms, status = m.status, state = state,
                    )
                }
            }
        }
        if (state.inbox.isEmpty() && state.syncHistory.isEmpty()) {
            Text(
                "No messages synced yet", fontSize = 14.sp, color = t.onSV, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            )
        }
    }
}
