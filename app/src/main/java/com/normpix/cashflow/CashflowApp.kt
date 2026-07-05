package com.normpix.cashflow

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.normpix.cashflow.ui.AddScreen
import com.normpix.cashflow.ui.AlarmFiringOverlay
import com.normpix.cashflow.ui.CategoryDetailScreen
import com.normpix.cashflow.ui.ConfirmSheet
import com.normpix.cashflow.ui.DashboardScreen
import com.normpix.cashflow.ui.EditSheet
import com.normpix.cashflow.ui.Emphasized
import com.normpix.cashflow.ui.InboxMenuOverlay
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

        // Ask once per session so transaction-captured notifications have a chance to show.
        RequestNotificationPermissionOnce()

        // Re-check SMS permission on resume — covers the user granting (or revoking) it
        // from system Settings while the app was backgrounded — and pick up anything the
        // SMS receiver queued or that arrived while the app wasn't running.
        val context = LocalContext.current
        DisposableEffect(context) {
            val activity = context as? ComponentActivity
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    state.refreshSmsPermission()
                    state.refreshSmsInbox()
                    state.refreshExactAlarmPermission()
                }
            }
            activity?.lifecycle?.addObserver(observer)
            onDispose { activity?.lifecycle?.removeObserver(observer) }
        }
        LaunchedEffect(Unit) { state.refreshSmsInbox() }

        // System back: close overlays / sub-screens before leaving the app.
        BackHandler(
            enabled = state.alarmFiring != null || state.inboxMenuOpen || state.sheet != null || state.addOpen ||
                state.catDetail != null || state.subScreen != null || state.tab != 0,
        ) {
            when {
                state.alarmFiring != null -> state.dismissAlarm()
                state.inboxMenuOpen -> state.inboxMenuOpen = false
                state.sheet != null -> state.closeSheet()
                state.addOpen -> state.addOpen = false
                state.catDetail != null -> state.catDetail = null
                state.subScreen != null -> state.subScreen = null
                else -> state.tab = 0
            }
        }

        // Design places the bar 16px from the very bottom of the screen. Use whichever
        // is larger: the design's 16dp or the system gesture inset — instead of stacking
        // both, which floated the bar (and FAB/toast) visibly higher than the design.
        val navBottomPadding = maxOf(
            16.dp,
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        )

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
                        // Design: right 20, bottom 96 (= 16 above the 64dp bar at bottom 16)
                        .padding(end = 20.dp, bottom = navBottomPadding + 80.dp)
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

            // Floating glass nav — design renders this as TWO separate pills side by side
            // (a labeled 4-tab main pill, plus a small icon-only Settings pill), not one
            // unified 5-item bar.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navBottomPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FloatingGlassNavBar(
                    items = listOf(
                        FloatingNavItem("Home", Icons.Outlined.Home, Icons.Rounded.Home),
                        FloatingNavItem("Activity", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Rounded.ReceiptLong),
                        FloatingNavItem(
                            "Inbox", Icons.Outlined.Inbox, Icons.Rounded.Inbox, badgeCount = state.inbox.size,
                            onLongClick = { state.inboxMenuOpen = true },
                        ),
                        FloatingNavItem("Insights", Icons.Outlined.DonutSmall, Icons.Rounded.DonutSmall),
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
                    borderColor = if (t.dark) t.hi else Color.White, // design border color
                )
                FloatingGlassNavBar(
                    items = listOf(
                        FloatingNavItem("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
                    ),
                    selectedIndex = if (state.tab == 4) 0 else -1,
                    onItemSelected = { state.tab = 4 },
                    glassState = glass,
                    blurRadius = state.navBlur.dp,
                    backgroundColor = t.surf,
                    backgroundOpacity = state.navOpacity,
                    activeContentColor = t.p,
                    inactiveIconColor = t.onSV,
                    saturation = 1.5f,
                    borderColor = if (t.dark) t.hi else Color.White,
                    showLabels = false,
                )
            }

            // Category detail drill-down (full-screen slide-in from the right)
            AnimatedVisibility(
                visible = state.catDetail != null,
                enter = slideInHorizontally(tween(360, easing = Emphasized)) { it },
                exit = slideOutHorizontally(tween(360, easing = Emphasized)) { it },
            ) {
                CategoryDetailScreen(state)
            }

            // Add screen (full-screen slide-up)
            AddScreen(state, glass)

            // Edit bottom sheet
            EditSheet(state, glass)

            // Erase-all-data confirmation — a fast-to-reach bottom sheet rather than an
            // inline warning buried in the Privacy sub-screen's scroll position.
            ConfirmSheet(
                visible = state.eraseConfirming,
                glass = glass,
                title = "Erase all ${state.txns.size} transactions, accounts and rules?",
                message = "This clears local data on this device. It cannot be undone.",
                confirmLabel = "Erase everything",
                onCancel = { state.cancelErase() },
                onConfirm = { state.confirmErase() },
                blurRadius = state.sheetBlur.dp,
                opacity = state.sheetOpacity,
            )

            // Long-press menu popped from the floating nav's Inbox tab
            InboxMenuOverlay(state, glass)

            // Full-screen reminder alarm (on top of everything else)
            AlarmFiringOverlay(state)

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
                    .padding(bottom = navBottomPadding + 80.dp),
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
