package com.normpix.cashflow.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normpix.cashflow.CashflowState
import com.normpix.cashflow.LocalCashTokens
import com.normpix.cashflow.catDisplayName
import com.normpix.cashflow.catIcons

private data class CatBudget(val name: String, val key: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val spent: Double, val cap: Double)

/** Default display order for the built-in budgeted categories; custom budgets append after. */
private val defaultCatOrder = listOf("Food", "Shopping", "Transport", "Bills")

@Composable
fun InsightsScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    val orderedKeys = defaultCatOrder.filter { it in state.budgetCaps } +
        state.budgetCaps.keys.filter { it !in defaultCatOrder }
    val cats = orderedKeys.map { key ->
        CatBudget(
            catDisplayName[key] ?: key, key, catIcons[key] ?: Icons.Rounded.Receipt,
            state.categorySpent(key), state.budgetCaps.getValue(key),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberGlassSyncedScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = contentBottomPadding(state.showFab)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Budgets & insights", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = t.onS,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Budget ring card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(t.pc)
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(
                pct = state.budgetPct, size = 96.dp, thickness = 12.dp,
                ringColor = t.p, trackColor = t.p.copy(alpha = 0.18f),
            ) {
                Text("${state.budgetPct}%", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = t.onPc)
            }
            Column {
                Text("July budget", fontSize = 13.sp, color = t.onPc)
                Text(
                    "${state.fmt(state.spentMonth)} / ${state.fmt(state.monthlyBudget)}",
                    fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = t.onPc,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "${state.fmt((state.monthlyBudget - state.spentMonth).coerceAtLeast(0.0))} left this month",
                    fontSize = 12.sp,
                    color = t.onPc.copy(alpha = 0.8f), modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        SectionTitle("Category budgets")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cats.forEach { c ->
                val p2 = ((c.spent / c.cap) * 100).toInt()
                val over = p2 > 100
                val warn = p2 > 85 && !over
                val barCol = if (over) t.err else if (warn) t.warnFg else t.p
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(t.cont)
                        .clickable { state.catDetail = c.key }
                        .padding(16.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(c.icon, null, tint = t.onSV, modifier = Modifier.size(20.dp))
                        Text(
                            c.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onS,
                            modifier = Modifier.weight(1f),
                        )
                        if (over) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(t.err.copy(alpha = if (t.dark) 0.25f else 0.12f))
                                    .padding(horizontal = 9.dp, vertical = 3.dp),
                            ) {
                                Text("Over by ${state.fmt(c.spent - c.cap)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = t.err)
                            }
                        }
                        Text("${state.fmt(c.spent)} / ${state.fmt(c.cap)}", fontSize = 13.sp, color = t.onSV)
                    }
                    val frac by animateFloatAsState(
                        (p2.coerceAtMost(100)) / 100f,
                        tween(600, easing = Emphasized), label = "budgetBar",
                    )
                    // Design: 8dp track at rgba(p, .15), 4dp radius, solid status-colored fill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(t.p.copy(alpha = 0.15f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(frac)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(barCol),
                        )
                    }
                }
            }
        }

        SectionTitle("Subscriptions", "${state.fmt(state.subsList.sumOf { it.amt })}/mo")
        GroupCard {
            state.subsList.forEach { sb ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(t.hi)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBubble(sb.icon, 44.dp, bg = t.sc, tint = t.onSc, iconSize = 22.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sb.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
                        Text(
                            "${state.fmt(sb.amt)} · ${sb.cycle} · ${sb.due}", fontSize = 12.sp, color = t.onSV,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Icon(Icons.Rounded.Notifications, null, tint = t.onSV, modifier = Modifier.size(20.dp))
                }
            }
        }

        // 6-month trend
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(t.cont)
                .padding(18.dp),
        ) {
            Text("Last 6 months", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
            // Design: 6 bars (52/66/48/74/58/71), gap 12, radius 8, current month solid p,
            // others p @ 30%, labels 6 below on a shared baseline.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val months = listOf("Feb" to 52, "Mar" to 66, "Apr" to 48, "May" to 74, "Jun" to 58, "Jul" to 71)
                months.forEachIndexed { i, (m2, v) ->
                    val current = i == months.lastIndex
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(v.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (current) t.p else t.p.copy(alpha = 0.3f)),
                        )
                        Text(
                            m2, fontSize = 11.sp,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (current) t.p else t.onSV,
                        )
                    }
                }
            }
        }
    }
}
