package com.normpix.cashflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normpix.cashflow.LocalCashTokens
import com.normpix.ui.floatingnav.GlassBackdropState
import com.normpix.ui.floatingnav.glassPanel

/**
 * A destructive-action confirmation, rendered as a bottom-anchored glass sheet (same visual
 * language as [EditSheet]) rather than inline in scrolling content — so it's always reachable
 * without scrolling, regardless of where the triggering row sits on screen.
 */
@Composable
fun ConfirmSheet(
    visible: Boolean,
    glass: GlassBackdropState?,
    title: String,
    message: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    blurRadius: androidx.compose.ui.unit.Dp = 24.dp,
    opacity: Float = 0.92f,
) {
    val t = LocalCashTokens.current
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
                        glass, RectangleShape, blurRadius = blurRadius * (10f / 24f),
                        backgroundColor = Color.Black, backgroundOpacity = 0.32f, saturation = 1f,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onCancel() },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(320, easing = Emphasized)) { it },
            exit = slideOutVertically(tween(320, easing = Emphasized)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(
                        glass, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), blurRadius = blurRadius,
                        backgroundColor = t.hi, backgroundOpacity = opacity, saturation = 1.5f,
                    )
                    .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 28.dp)
                    .navigationBarsPadding(),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(t.out),
                )
                Text(
                    title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = t.err,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(message, fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, t.out, RoundedCornerShape(22.dp))
                            .clickable { onCancel() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onS, textAlign = TextAlign.Center)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(t.err)
                            .clickable { onConfirm() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            confirmLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = if (t.dark) Color(0xFF690005) else Color.White, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
