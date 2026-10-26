package com.example.normpix.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.DockFootprint
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberWindowSize
import kotlin.math.roundToInt

/*
 * Bottom controls (Settings > Appearance): what a page has at its top - the way back, its actions, its search and filters -
 * sits at the bottom, where a thumb reaches, beside the floating dock.
 *
 *  - A pushed page keeps the bottom dock's back row, which then also holds the page's actions (see [AppSubPage]).
 *  - A page with a search (Transactions, People, Settings...) holds it in a card just above that row - or, on a tab, above the
 *    dock - so typing and filtering never mean reaching for the top of the screen.
 *
 * It is a phone's layout: a wider window has a rail and room, and keeps its own layout whatever the setting says.
 */

/** Whether pages put their controls at the bottom here: the setting is on and the window is a phone's. */
@Composable
fun rememberBottomControls(): Boolean {
    val on = LocalAppPreferences.current?.state?.collectAsState()?.value?.bottomControls ?: false
    return on && rememberWindowSize().isCompact
}

/**
 * How far the shell's dock has stepped out of the way, 0 (in place) to 1 (gone): a tab's search card sits on the dock and goes
 * down into its place with it. A function, read where the card is laid out, so the dock's motion never recomposes a page.
 */
val LocalDockGone = staticCompositionLocalOf<() -> Float> { { 0f } }

/** The gap between a bottom search card and what it rests on (the dock, or the back row). */
internal val SearchCardGap = AppSpacing.sm

/**
 * The card a page's search and filters sit in at the bottom: a pill that holds the field, solid - on a tab it lives inside the
 * page the dock blurs, so glass would only blur itself - and lifted by a shadow over the list that scrolls under it.
 * [onHeightChange] says how tall it is, so the list can end above it.
 */
@Composable
fun BottomSearchCard(
    modifier: Modifier = Modifier,
    onHeightChange: (Int) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .padding(horizontal = AppSpacing.lg)
            .fillMaxWidth()
            .onSizeChanged { onHeightChange(it.height) },
        shape = AppRadius.rFull,
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.40f)),
    ) {
        Box(Modifier.padding(SearchCardInset)) { content() }
    }
}

/** The room round the field inside its card. */
private val SearchCardInset = 6.dp

/**
 * A tab's search card, over the foot of the page and above the dock the shell floats there. The dock leaves while the list
 * scrolls down ([LocalDockGone]) and the card goes down into its place; while the keyboard is up the dock is behind it, and the
 * card rests on the keyboard instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoxScope.DockedSearchCard(
    onHeightChange: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val dockGone = LocalDockGone.current
    val typing = WindowInsets.isImeVisible
    val lift: Dp by animateDpAsState(
        targetValue = if (typing) 0.dp else DockFootprint + SearchCardGap,
        animationSpec = AppMotion.spatial(),
        label = "search_card_lift",
    )
    BottomSearchCard(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .imePadding()
            // Up on the dock, less as the dock leaves; laid out, not recomposed, as the dock moves.
            .offset { IntOffset(0, -(lift.toPx() * (1f - dockGone())).roundToInt()) },
        onHeightChange = onHeightChange,
        content = content,
    )
}

/** What a list under a bottom search card keeps clear at its foot: the card, its lift over the dock and a little air. */
internal fun searchCardClearance(cardHeight: Dp, liftedOverDock: Boolean): Dp =
    cardHeight + (if (liftedOverDock) DockFootprint + SearchCardGap else 0.dp) + AppSpacing.lg
