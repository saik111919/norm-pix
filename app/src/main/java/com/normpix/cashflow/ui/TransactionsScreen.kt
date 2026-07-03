package com.normpix.cashflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normpix.cashflow.CashflowState
import com.normpix.cashflow.LocalCashTokens
import com.normpix.cashflow.SheetTarget
import com.normpix.cashflow.fmt
import kotlin.math.abs

private val chipIcons: Map<String, ImageVector> = mapOf(
    "All" to Icons.Rounded.FilterList,
    "Food" to Icons.Rounded.LunchDining,
    "Shopping" to Icons.Rounded.ShoppingBag,
    "Transport" to Icons.Rounded.LocalTaxi,
    "Bills" to Icons.Rounded.Bolt,
    "Income" to Icons.Rounded.Savings,
)

@Composable
fun TransactionsScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    val chips = listOf("All", "Food", "Shopping", "Transport", "Bills", "Income")
    val q = state.search.trim().lowercase()
    val filtered = state.txns.filter { tx ->
        (state.chip == "All" || tx.cat == state.chip) &&
            (q.isEmpty() || tx.merchant.lowercase().contains(q) || tx.cat.lowercase().contains(q) ||
                abs(tx.amount).toLong().toString().contains(q))
    }
    val days = listOf("Today", "Yesterday", "Earlier")
        .map { day -> day to filtered.filter { it.day == day } }
        .filter { it.second.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberGlassSyncedScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = contentBottomPadding(state.showFab)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Transactions", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = t.onS,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(t.cont)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = t.onSV, modifier = Modifier.size(22.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (state.search.isEmpty()) {
                    Text("Search merchant, category, amount…", fontSize = 15.sp, color = t.onSV.copy(alpha = 0.7f), maxLines = 1)
                }
                BasicTextField(
                    value = state.search,
                    onValueChange = { state.search = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = t.onS),
                    cursorBrush = SolidColor(t.p),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.search.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Close, null, tint = t.onSV,
                    modifier = Modifier.size(20.dp).clickable { state.search = "" },
                )
            }
        }

        // Filter chips (12dp radius + count badges, per design)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEach { c ->
                val sel = state.chip == c
                val cnt = if (c == "All") state.txns.size else state.txns.count { it.cat == c }
                ChoiceChip(
                    label = c, selected = sel, onClick = { state.chip = c },
                    icon = chipIcons[c], cornerRadius = 12.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 18.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (sel) t.p.copy(alpha = 0.18f) else t.cont)
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$cnt", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = if (sel) t.onSc else t.onSV,
                        )
                    }
                }
            }
        }

        // Summary line
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${filtered.size} transaction${if (filtered.size == 1) "" else "s"}",
                fontSize = 12.sp, color = t.onSV,
            )
            val spent = filtered.filter { it.amount < 0 }.sumOf { abs(it.amount) }
            Text("Spent ${fmt(spent)}", fontSize = 12.sp, color = t.onSV)
        }

        if (days.isNotEmpty()) {
            days.forEach { (day, rows) ->
                Text(day, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.onSV, modifier = Modifier.padding(top = 2.dp))
                GroupCard {
                    rows.forEach { tx -> TxRow(tx) { state.openSheet(SheetTarget("txn", txn = tx)) } }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.SearchOff, null, tint = t.onSV, modifier = Modifier.size(40.dp))
                Text(
                    "No transactions match", fontSize = 14.sp, color = t.onSV,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
