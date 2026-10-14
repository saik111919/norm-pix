package com.example.normpix.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.normpix.navigation.TabMotion
import com.example.normpix.settings.PageAnimationType
import com.example.normpix.ui.theme.AppMotion

/**
 * Shows [content] by easing its height open (and fading it in), and the reverse on the way out: the one
 * "this option only matters now" reveal, with the app's standard duration and curve. Instant when the
 * user turned animations off.
 */
@Composable
fun RevealVisibility(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val ms = AppMotion.duration(AppMotion.SHORT_MS)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(tween(ms, easing = AppMotion.standard)) + fadeIn(tween(ms)),
        exit = shrinkVertically(tween(ms, easing = AppMotion.standard)) + fadeOut(tween(ms)),
    ) { content() }
}

/**
 * The transition between sibling sections of one screen (Plans' sections, the NFC Receive / Share modes), in the family of
 * the user's [style] - as the tabs are: the old one fades a short way off, and the new one either fades and slides in from
 * the side it sits on ([forward] meaning "to a later tab") or, in the styles that grow pages in place (a Linux window,
 * scale, fade through), grows in from where that style grows a page. The new section takes its height at once, so
 * nothing jumps. With [reduced] it is an instant swap.
 */
fun <S> AnimatedContentTransitionScope<S>.sectionTransition(
    forward: Boolean,
    reduced: Boolean,
    shiftPx: Int,
    style: PageAnimationType = PageAnimationType.SharedAxis,
): ContentTransform {
    if (reduced) return EnterTransition.None togetherWith ExitTransition.None
    if (!TabMotion.slides(style)) {
        val arrive = tween<Float>(AppMotion.MEDIUM_MS, delayMillis = 60, easing = AppMotion.enter)
        val enter = fadeIn(arrive) + scaleIn(arrive, initialScale = TabMotion.scale(style, 0f), transformOrigin = TabMotion.origin(style))
        val exit = fadeOut(tween(SECTION_EXIT_MS, easing = LinearEasing))
        return (enter togetherWith exit) using SizeTransform(clip = false) { _, _ -> snap() }
    }
    val direction = if (forward) 1 else -1
    val enter = fadeIn(tween(AppMotion.SHORT_MS, delayMillis = 60, easing = AppMotion.enter)) +
        slideInHorizontally(tween(AppMotion.SHORT_MS, delayMillis = 60, easing = AppMotion.enter)) { direction * shiftPx }
    val exit = fadeOut(tween(SECTION_EXIT_MS, easing = LinearEasing)) +
        slideOutHorizontally(tween(SECTION_EXIT_MS, easing = LinearEasing)) { -direction * shiftPx }
    return (enter togetherWith exit) using SizeTransform(clip = false) { _, _ -> snap() }
}

private const val SECTION_EXIT_MS = 90
