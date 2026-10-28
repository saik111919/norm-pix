package com.example.normpix.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.ThemeMode
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import com.example.normpix.ui.theme.NormPixTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How the "+" menu moves, judged from pixels on a manual clock: the picture a few frames after the tap, against the picture
 * before it and the one the menu settles into. With animations off the menu is the settled picture almost at once; with them on
 * it has left the closed picture but not yet reached the settled one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ActionMenuMotionTest {

    @get:Rule
    val compose = createComposeRule()

    private var open by mutableStateOf(false)

    private fun show(reducedMotion: Boolean) {
        val actions = listOf("Expense", "Income", "Recurring", "Reminder", "Card", "Account", "Categories", "Budgets", "People", "Search", "Settings")
            .mapIndexed { i, label -> FooterAction(label, Icons.Rounded.Add, isPrimary = i == 0, onClick = {}) }
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides reducedMotion) {
                NormPixTheme(Appearance(ThemeMode.Dark, ColorSource.Custom, AccentColor.Orange)) {
                    // Full screen, so every picture is the same size whatever is open.
                    Box(Modifier.fillMaxSize()) {
                        FloatingNavigationRow(
                            destinations = AppDestination.entries,
                            current = AppDestination.Home,
                            overdueCount = 0,
                            onDestinationSelected = {},
                            actions = actions,
                            isMoreOpen = open,
                            onMoreToggle = { open = !open },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }

    private fun picture(): ImageBitmap = compose.onRoot().captureToImage()

    /** The picture before the tap, 12 frames (about 190 ms) after it, and once everything has settled. */
    private class Frames(val closed: ImageBitmap, val early: ImageBitmap, val settled: ImageBitmap)

    private fun framesAroundOpening(): Frames {
        compose.waitForIdle()
        val closed = picture()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            open = true
            // A manual clock does not deliver the write to the composition by itself; make it.
            Snapshot.sendApplyNotifications()
        }
        repeat(12) { compose.mainClock.advanceTimeByFrame() }
        val early = picture()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        return Frames(closed, early, picture())
    }

    /** Where two pictures differ, as "N pixels in rows a..b, columns c..d" - or null when they are the same picture. */
    private fun difference(first: ImageBitmap, second: ImageBitmap): String? {
        if (first.width != second.width || first.height != second.height) {
            return "sizes ${first.width}x${first.height} and ${second.width}x${second.height}"
        }
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        var count = 0
        var rows = Int.MAX_VALUE to Int.MIN_VALUE
        var cols = Int.MAX_VALUE to Int.MIN_VALUE
        for (y in 0 until a.height) for (x in 0 until a.width) {
            if (a[x, y] != b[x, y]) {
                count++
                rows = minOf(rows.first, y) to maxOf(rows.second, y)
                cols = minOf(cols.first, x) to maxOf(cols.second, x)
            }
        }
        return if (count == 0) null else "$count pixels in rows ${rows.first}..${rows.second}, columns ${cols.first}..${cols.second}"
    }

    @Test
    fun with_animations_off_the_menu_is_already_the_settled_picture_a_few_frames_in() {
        show(reducedMotion = true)
        val frames = framesAroundOpening()
        assertTrue("the menu should have opened: the picture is still the closed one", difference(frames.closed, frames.early) != null)
        val moving = difference(frames.early, frames.settled)
        assertTrue("with animations off the menu should have jumped straight to open, but it differs: $moving", moving == null)
    }

    @Test
    fun with_animations_on_the_menu_is_on_its_way_a_few_frames_in() {
        show(reducedMotion = false)
        val frames = framesAroundOpening()
        assertTrue("the menu should have started: the picture is still the closed one", difference(frames.closed, frames.early) != null)
        assertTrue("the menu should not have arrived yet: the picture is already the settled one", difference(frames.early, frames.settled) != null)
    }
}
