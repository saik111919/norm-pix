package com.normpix.cashflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normpix.cashflow.CashflowState
import com.normpix.cashflow.LocalCashTokens
import com.normpix.cashflow.SheetTarget
import com.normpix.cashflow.fmt

@Composable
fun InboxScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberGlassSyncedScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = contentBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text("Inbox", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = t.onS)
            val n = state.inbox.size
            Text(
                if (n > 0) "$n detected message${if (n > 1) "s" else ""} waiting for review" else "All caught up",
                fontSize = 13.sp, color = t.onSV, modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (state.inbox.isNotEmpty()) {
            state.inbox.toList().forEach { m ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(t.cont)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Header: sender + badges
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Sms, 36.dp, bg = t.sc, tint = t.onSc, iconSize = 18.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(m.sender, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = t.onS)
                            Text(m.whenLabel, fontSize = 11.sp, color = t.onSV)
                        }
                        if (m.dup) WarnBadge("Possible duplicate")
                        if (m.conf == "low") WarnBadge("Check merchant")
                    }
                    // Raw SMS
                    Text(
                        m.sms, fontSize = 12.sp, lineHeight = 18.sp, color = t.onSV,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(t.hi)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                    // Parsed transaction
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(m.icon, 44.dp, bg = t.pc, tint = t.onPc, iconSize = 22.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(m.merchant, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
                            Text("${m.cat} · ${m.account}", fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 2.dp))
                        }
                        Text("−" + fmt(m.amount), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = t.onS)
                    }
                    // Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(t.p)
                                .clickable { state.confirmInbox(m) },
                            contentAlignment = Alignment.Center,
                        ) { Text("Confirm", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onP) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .border(1.dp, t.out, RoundedCornerShape(22.dp))
                                .clickable { state.openSheet(SheetTarget("inbox", inbox = m)) },
                            contentAlignment = Alignment.Center,
                        ) { Text("Edit", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.p) }
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .clickable { state.ignoreInbox(m) },
                            contentAlignment = Alignment.Center,
                        ) { Text("Ignore", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onSV) }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconBubble(Icons.Rounded.TaskAlt, 72.dp, bg = t.pc, tint = t.onPc, iconSize = 34.dp)
                Text(
                    "Inbox zero", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text("New bank messages will appear here", fontSize = 13.sp, color = t.onSV, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun WarnBadge(text: String) {
    val t = LocalCashTokens.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(t.warnBg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = t.warnFg)
    }
}
