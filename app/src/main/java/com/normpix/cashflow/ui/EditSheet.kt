package com.normpix.cashflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normpix.cashflow.CashflowState
import com.normpix.cashflow.LocalCashTokens
import com.normpix.cashflow.catIcons
import com.normpix.ui.floatingnav.glassPanel
import kotlin.math.roundToInt

/** Edit/confirm bottom sheet — custom overlay matching the design (28dp top radius, scrim, chips). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSheet(state: CashflowState, glass: com.normpix.ui.floatingnav.GlassBackdropState?) {
    val t = LocalCashTokens.current
    val target = state.sheet
    val visible = target != null

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(280, easing = Emphasized)),
            exit = fadeOut(tween(280, easing = Emphasized)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassPanel(
                        glass, RectangleShape, blurRadius = (state.sheetBlur * 10f / 24f).dp,
                        backgroundColor = Color.Black, backgroundOpacity = 0.32f, saturation = 1f,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { state.closeSheet() },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(320, easing = Emphasized)) { it },
            exit = slideOutVertically(tween(320, easing = Emphasized)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val isInbox = target?.type == "inbox"

            // Drag-to-expand/dismiss (design's startSheetDrag): dragging up past 50dp expands
            // to fullscreen; while expanded, dragging down past 50dp collapses back; while
            // collapsed, dragging down past 110dp dismisses. Visual offset while dragging:
            // clamped to only the "active" direction (design's max(0,·)/min(0,·)*0.4 formula).
            var expanded by remember { mutableStateOf(false) }
            LaunchedEffect(target) { expanded = false }
            var dragging by remember { mutableStateOf(false) }
            var dragDeltaPx by remember { mutableFloatStateOf(0f) }
            val density = LocalDensity.current
            val rawOffsetPx = if (dragging) {
                if (expanded) dragDeltaPx.coerceAtLeast(0f) else dragDeltaPx.coerceAtMost(0f) * 0.4f
            } else 0f
            val animatedOffsetPx by animateFloatAsState(
                targetValue = rawOffsetPx,
                animationSpec = if (dragging) snap() else tween(280, easing = Emphasized),
                label = "sheetDragOffset",
            )
            val screenHeight = LocalConfiguration.current.screenHeightDp.dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (expanded) Modifier.fillMaxHeight() else Modifier.heightIn(max = screenHeight * 0.92f))
                    .offset { IntOffset(0, animatedOffsetPx.roundToInt()) }
                    .glassPanel(
                        glass,
                        if (expanded) RectangleShape else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        blurRadius = state.sheetBlur.dp, backgroundColor = t.hi, backgroundOpacity = state.sheetOpacity, saturation = 1.5f,
                    )
                    .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 28.dp)
                    .navigationBarsPadding(),
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 9.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { dragging = true; dragDeltaPx = 0f },
                                onDragEnd = {
                                    val delta = dragDeltaPx
                                    val collapseThresholdPx = with(density) { 50.dp.toPx() }
                                    val closeThresholdPx = with(density) { 110.dp.toPx() }
                                    when {
                                        !expanded && delta < -collapseThresholdPx -> expanded = true
                                        expanded && delta > collapseThresholdPx -> expanded = false
                                        !expanded && delta > closeThresholdPx -> state.closeSheet()
                                    }
                                    dragging = false
                                },
                                onDragCancel = { dragging = false },
                            ) { change, dragAmount ->
                                change.consume()
                                dragDeltaPx += dragAmount
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(t.out),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (isInbox) "Edit & confirm" else "Edit transaction",
                        fontSize = 18.sp, fontWeight = FontWeight.Medium, color = t.onS,
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(t.cont)
                            .clickable { state.closeSheet() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, null, tint = t.onSV, modifier = Modifier.size(18.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text("Merchant", fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                        CashTextField(
                            value = state.editMerchant, onValueChange = { state.editMerchant = it },
                            placeholder = "", modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp,
                        )
                    }
                    Column {
                        Text("Amount (₹)", fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                        CashTextField(
                            value = state.editAmount, onValueChange = { state.editAmount = it },
                            placeholder = "", modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp,
                            keyboardType = KeyboardType.Number,
                        )
                    }
                    Column {
                        Text("Category", fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("Food", "Shopping", "Transport", "Bills", "Income").forEach { c ->
                                val sel = state.editCat == c
                                Row(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(if (sel) t.sc else Color.Transparent)
                                        .border(1.dp, if (sel) t.sc else t.out, RoundedCornerShape(17.dp))
                                        .clickable { state.editCat = c }
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        if (sel) Icons.Rounded.Check else catIcons.getValue(c),
                                        null, tint = if (sel) t.onSc else t.onSV, modifier = Modifier.size(16.dp),
                                    )
                                    Text(c, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (sel) t.onSc else t.onSV)
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, t.out, RoundedCornerShape(24.dp))
                            .clickable { state.closeSheet() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.p, textAlign = TextAlign.Center)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(t.p)
                            .clickable { state.saveSheet() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isInbox) "Confirm" else "Save",
                            fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onP, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
