package com.normpix.cashflow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.DonutSmall
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DonutSmall
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normpix.cashflow.ui.AddScreen
import com.normpix.cashflow.ui.DashboardScreen
import com.normpix.cashflow.ui.EditSheet
import com.normpix.cashflow.ui.Emphasized
import com.normpix.cashflow.ui.InboxScreen
import com.normpix.cashflow.ui.InsightsScreen
import com.normpix.cashflow.ui.SettingsScreen
import com.normpix.cashflow.ui.TransactionsScreen
import com.normpix.ui.floatingnav.FloatingGlassNavBar
import com.normpix.ui.floatingnav.FloatingNavItem
import com.normpix.ui.floatingnav.LocalGlassBackdropState
import com.normpix.ui.floatingnav.glassSource
import com.normpix.ui.floatingnav.rememberGlassBackdropState
import kotlinx.coroutines.delay

/** Root of the Cashflow app — theme, screens, glass nav bar, FAB, add flow, sheet, toast. */
@Composable
fun CashflowApp() {
    val state = rememberCashflowState()
    val sysDark = isSystemInDarkTheme()

    CashflowTheme(seed = state.seed, mode = state.mode) {
        val t = LocalCashTokens.current
        val glass = rememberGlassBackdropState()

        // System back: close overlays / sub-screens before leaving the app.
        BackHandler(enabled = state.sheet != null || state.addOpen || state.subScreen != null || state.tab != 0) {
            when {
                state.sheet != null -> state.closeSheet()
                state.addOpen -> state.addOpen = false
                state.subScreen != null -> state.subScreen = null
                else -> state.tab = 0
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(t.surf)) {
            // Screen content — recorded for the glass nav backdrop. The composition
            // local lets each screen's scroll state keep the blur in sync.
            CompositionLocalProvider(LocalGlassBackdropState provides glass) {
                Box(modifier = Modifier.fillMaxSize().glassSource(glass)) {
                    when (state.tab) {
                        0 -> DashboardScreen(state)
                        1 -> TransactionsScreen(state)
                        2 -> InboxScreen(state)
                        3 -> InsightsScreen(state)
                        else -> SettingsScreen(state, sysDark)
                    }
                }
            }

            // FAB (hidden while the add screen is open, per design)
            if (state.showFab) {
                val fabScale by animateFloatAsState(
                    if (state.addOpen) 0.6f else 1f, tween(320, easing = Emphasized), label = "fabScale",
                )
                val fabAlpha by animateFloatAsState(
                    if (state.addOpen) 0f else 1f, tween(240, easing = Emphasized), label = "fabAlpha",
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 20.dp, bottom = 96.dp)
                        .scale(fabScale)
                        .graphicsLayer { alpha = fabAlpha }
                        .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = t.p.copy(alpha = 0.4f))
                        .size(60.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(t.p)
                        .clickable(enabled = !state.addOpen) { state.addOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add", tint = t.onP, modifier = Modifier.size(28.dp))
                }
            }

            // Floating glass nav bar — positioned exactly like the reference demo so the
            // blurred backdrop slice stays in sync with the content behind it.
            FloatingGlassNavBar(
                items = listOf(
                    FloatingNavItem("Home", Icons.Outlined.Home, Icons.Rounded.Home),
                    FloatingNavItem("Activity", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Rounded.ReceiptLong),
                    FloatingNavItem("Inbox", Icons.Outlined.Inbox, Icons.Rounded.Inbox, badgeCount = state.inbox.size),
                    FloatingNavItem("Insights", Icons.Outlined.DonutSmall, Icons.Rounded.DonutSmall),
                    FloatingNavItem("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
                ),
                selectedIndex = state.tab,
                onItemSelected = { state.tab = it },
                glassState = glass,
                blurRadius = state.navBlur.dp,
                backgroundColor = t.surf,
                backgroundOpacity = state.navOpacity,
                activeContentColor = t.p,
                inactiveIconColor = t.onSV,
                saturation = 1.5f, // design: backdrop-filter saturate(1.5)
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )

            // Add screen (full-screen slide-up)
            AddScreen(state)

            // Edit bottom sheet
            EditSheet(state)

            // Toast
            LaunchedEffect(state.toastTick) {
                if (state.toast != null) {
                    delay(2200)
                    state.toast = null
                }
            }
            var lastToast by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            state.toast?.let { lastToast = it }
            AnimatedVisibility(
                visible = state.toast != null,
                enter = fadeIn(tween(200, easing = Emphasized)),
                exit = fadeOut(tween(200, easing = Emphasized)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp),
            ) {
                val dark = t.dark
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .shadow(6.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (dark) t.onS else Color(0xFF322F35))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        lastToast,
                        fontSize = 14.sp,
                        color = if (dark) t.surf else Color(0xFFF5EFF7),
                    )
                }
            }
        }
    }
}
