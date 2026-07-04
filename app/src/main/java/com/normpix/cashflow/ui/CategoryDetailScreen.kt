package com.normpix.cashflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
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
import com.normpix.cashflow.catMeta

/** Category drill-down (design section 9): slides in from the right over the current tab. */
@Composable
fun CategoryDetailScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    val cat = state.catDetail ?: return
    val meta = catMeta[cat] ?: return
    val cap = state.budgetCaps[cat]
    val spent = state.categorySpent(cat)
    val entries = state.txns.filter { it.cat == cat }
    val days = listOf("Today", "Yesterday", "Earlier")
        .map { day -> day to entries.filter { it.day == day } }
        .filter { it.second.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.surf)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onClick = { state.catDetail = null })
            IconBubble(meta.icon, 44.dp, bg = t.pc, tint = t.onPc, iconSize = 22.dp)
            Column {
                Text(meta.title, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = t.onS)
                Text("July · ${entries.size} entries", fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 2.dp))
            }
        }

        if (cap != null) {
            val over = spent > cap
            val frac = (spent / cap).toFloat().coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(t.cont)
                    .padding(18.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Spent this month", fontSize = 12.sp, color = t.onSV)
                        Text(
                            state.fmt(spent), fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                            color = t.onS, letterSpacing = (-0.5).sp, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        if (over) "Over by ${state.fmt(spent - cap)}" else "${state.fmt(cap - spent)} left of ${state.fmt(cap)}",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = if (over) t.err else t.onSV,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(t.p.copy(alpha = 0.15f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (over) t.err else t.p),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (days.isNotEmpty()) {
                days.forEach { (day, rows) ->
                    Text(day, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.onSV)
                    GroupCard {
                        rows.forEach { tx -> TxRow(tx, state) { state.openSheet(SheetTarget("txn", txn = tx)) } }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null, tint = t.onSV, modifier = Modifier.size(40.dp))
                    Text(
                        "No entries in this category yet", fontSize = 14.sp, color = t.onSV,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
