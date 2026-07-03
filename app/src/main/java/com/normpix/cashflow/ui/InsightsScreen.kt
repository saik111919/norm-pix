package com.normpix.cashflow.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normpix.cashflow.CashflowState
import com.normpix.cashflow.LocalCashTokens
import com.normpix.cashflow.fmt

private data class CatBudget(val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val spent: Double, val cap: Double)

@Composable
fun InsightsScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    val cats = listOf(
        CatBudget("Food & dining", com.normpix.cashflow.catList[0].icon, 13540.0, 15000.0),
        CatBudget("Shopping", com.normpix.cashflow.catList[1].icon, 10220.0, 10000.0),
        CatBudget("Transport", com.normpix.cashflow.catList[2].icon, 7610.0, 9000.0),
        CatBudget("Bills & utilities", com.normpix.cashflow.catList[3].icon, 5890.0, 8000.0),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberGlassSyncedScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = contentBottomPadding()),
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
                pct = BUDGET_PCT, size = 96.dp, thickness = 12.dp,
                ringColor = t.p, trackColor = t.p.copy(alpha = 0.18f),
            ) {
                Text("$BUDGET_PCT%", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = t.onPc)
            }
            Column {
                Text("July budget", fontSize = 13.sp, color = t.onPc)
                Text(
                    "${fmt(SPENT_MONTH)} / ${fmt(BUDGET)}",
                    fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = t.onPc,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "₹17,682 left · 29 days in, on track", fontSize = 12.sp,
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
                                Text("Over by ${fmt(c.spent - c.cap)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = t.err)
                            }
                        }
                        Text("${fmt(c.spent)} / ${fmt(c.cap)}", fontSize = 13.sp, color = t.onSV)
                    }
                    val frac by animateFloatAsState(
                        (p2.coerceAtMost(100)) / 100f,
                        tween(600, easing = Emphasized), label = "budgetBar",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(t.p.copy(alpha = 0.1f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(frac)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(barCol.copy(alpha = 0.7f), barCol)
                                    )
                                ),
                        )
                    }
                }
            }
        }

        SectionTitle("Subscriptions", "${fmt(state.subsList.sumOf { it.amt })}/mo")
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
                            "${fmt(sb.amt)} · ${sb.cycle} · ${sb.due}", fontSize = 12.sp, color = t.onSV,
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
            val trendData = listOf(52f, 66f, 48f, 74f, 58f, 71f)
            TrendChart(
                data = trendData,
                color = t.p,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(top = 16.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Feb", "Mar", "Apr", "May", "Jun", "Jul").forEach { m ->
                    Text(m, fontSize = 11.sp, color = t.onSV)
                }
            }
        }
    }
}
