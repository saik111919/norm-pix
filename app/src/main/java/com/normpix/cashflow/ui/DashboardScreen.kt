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
import androidx.compose.material.icons.rounded.Add
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
import com.normpix.cashflow.fmtCompact

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
            Column {
                Text("NormPix", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = t.onS)
                Text("July", fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 2.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.showFab) {
                    CircleIconButton(Icons.Rounded.Add, onClick = { state.addOpen = true }, bg = t.p, tint = t.onP)
                }
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
                    state.fmt(state.spentMonth), fontSize = 38.sp, fontWeight = FontWeight.SemiBold,
                    color = t.onPc, letterSpacing = (-0.5).sp, maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "${state.fmt(state.todaySpent)} today", fontSize = 12.sp,
                    color = t.onPc.copy(alpha = 0.8f), modifier = Modifier.padding(top = 6.dp),
                )
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
                    pct = state.budgetPct, size = 72.dp, thickness = 9.dp,
                    ringColor = t.p, trackColor = t.p.copy(alpha = 0.18f),
                ) {
                    Text("${state.budgetPct}%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = t.onSc)
                }
                Text("of ${fmtCompact(state.monthlyBudget, state.currency)} budget", fontSize = 12.sp, color = t.onSc)
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
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { state.tab = 3 },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Where did my money go?", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
                Icon(Icons.Rounded.ChevronRight, null, tint = t.onSV, modifier = Modifier.size(20.dp))
            }
            // Bar height + opacity scale with each category's live spend relative to the
            // highest-spending one (design: max bar 80dp, height floor 6dp, opacity floor 0.25
            // so empty categories still render a visible sliver rather than disappearing).
            val barCats = listOf("Food" to "Food", "Shopping" to "Shop", "Transport" to "Travel", "Bills" to "Bills")
            val spentByCat = barCats.map { (cat, _) -> cat to state.categorySpent(cat) }
            val maxSpent = spentByCat.maxOf { it.second }.coerceAtLeast(1.0)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                barCats.forEachIndexed { i, (cat, label) ->
                    val ratio = (spentByCat[i].second / maxSpent).toFloat()
                    val op = ratio.coerceAtLeast(0.25f)
                    val barH = (ratio * 80f).coerceAtLeast(6f).toInt()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { state.catDetail = cat },
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
