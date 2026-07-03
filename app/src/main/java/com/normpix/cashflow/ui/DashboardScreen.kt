package com.normpix.cashflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normpix.cashflow.CashflowState
import com.normpix.cashflow.LocalCashTokens
import com.normpix.cashflow.SheetTarget
import com.normpix.cashflow.fmt

const val SPENT_MONTH = 42318.0
const val BUDGET = 60000.0
const val BUDGET_PCT = 71

@Composable
fun DashboardScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberGlassSyncedScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = contentBottomPadding(state.showFab)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("July", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = t.onS)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircleIconButton(Icons.Rounded.Search, onClick = { state.tab = 1 })
                CircleIconButton(Icons.Rounded.Person, onClick = { state.tab = 4 }, bg = t.pc, tint = t.onPc)
            }
        }

        // Hero cards — equal heights, like the design's stretched flex row
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 32.dp))
                    .background(t.pc)
                    .padding(20.dp),
            ) {
                Text("Spent this month", fontSize = 13.sp, color = t.onPc)
                Text(
                    state.fmt(SPENT_MONTH), fontSize = 38.sp, fontWeight = FontWeight.SemiBold,
                    color = t.onPc, letterSpacing = (-0.5).sp, maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text("₹1,240 today", fontSize = 12.sp, color = t.onPc.copy(alpha = 0.8f), modifier = Modifier.padding(top = 6.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 32.dp, bottomEnd = 32.dp, bottomStart = 12.dp))
                    .background(t.sc)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                ProgressRing(
                    pct = BUDGET_PCT, size = 72.dp, thickness = 9.dp,
                    ringColor = t.p, trackColor = t.p.copy(alpha = 0.18f),
                ) {
                    Text("$BUDGET_PCT%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = t.onSc)
                }
                Text("of ₹60k budget", fontSize = 12.sp, color = t.onSc)
            }
        }

        // Inbox nudge
        if (state.inbox.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(t.p)
                    .clickable { state.tab = 2 }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.MarkEmailUnread, null, tint = t.onP, modifier = Modifier.size(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val n = state.inbox.size
                    Text(
                        "$n message${if (n > 1) "s" else ""} to review",
                        fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onP,
                    )
                    Text(
                        "Auto-detected from bank SMS", fontSize = 12.sp,
                        color = t.onP.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = t.onP, modifier = Modifier.size(22.dp))
            }
        }

        // Mini category bars
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(t.cont)
                .clickable { state.tab = 3 }
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Where did my money go?", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
                Icon(Icons.Rounded.ChevronRight, null, tint = t.onSV, modifier = Modifier.size(20.dp))
            }
            // Design: bars 80/60/44/30 at rgba(p, 0.2+0.8·op), radius 10, gap 14, labels 6 below,
            // all columns bottom-aligned so labels share one baseline and nothing overflows.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                listOf(
                    Triple("Food", 80, 1f), Triple("Shop", 60, 0.7f),
                    Triple("Travel", 44, 0.45f), Triple("Bills", 30, 0.25f),
                ).forEach { (label, barH, op) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barH.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(t.p.copy(alpha = 0.2f + 0.8f * op)),
                        )
                        Text(label, fontSize = 11.sp, color = t.onSV)
                    }
                }
            }
        }

        SectionTitle("Recent · auto-added", "See all", onTrailingClick = { state.tab = 1 })
        GroupCard {
            state.txns.take(4).forEach { tx ->
                TxRow(tx, state) { state.openSheet(SheetTarget("txn", txn = tx)) }
            }
        }
    }
}
