package com.example.normpix.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home's number collapsing into the header as the page is scrolled (after Weather's temperature): the rule that places the two lines
 * and the chip at each scroll position. It is a plain function of the scroll, so it runs the same forwards and back.
 */
class HeroCollapseTest {
    // A 411 dp phone at 2x: the card's lines start low on the page, the header's chip is 42 dp tall at 8 dp below a 24 dp status bar.
    private val rest = HeroCollapseRest(
        eyebrow = Offset(96f, 360f),
        eyebrowSize = Size(260f, 48f),
        amount = Offset(96f, 408f),
        amountSize = Size(420f, 128f),
    )
    private val spec = HeroCollapseSpec(
        chipLeft = 32f,
        chipTop = 48f + 16f,
        padHorizontal = 24f,
        padVertical = 4f,
        headerBottom = 48f + 116f,
        fade = 80f,
        listTop = 0f,
        columnLeft = 0f,
    )

    private val steps = (0..1_000 step 8).map { it.toFloat() }

    @Test
    fun atRestThePageIsAsItWas() {
        val frame = heroCollapseFrame(0f, rest, spec)
        assertEquals(rest.eyebrow, frame.eyebrowOffset)
        assertEquals(rest.amount, frame.amountOffset)
        assertEquals(1f, frame.eyebrowScale)
        assertEquals(1f, frame.amountScale)
        assertEquals(0f, frame.progress)
        assertEquals(0f, frame.chipAlpha, "no chip is drawn over the card")
        assertEquals(1f, frame.titleAlpha, "and the page's title is all there")
    }

    @Test
    fun theFirstPixelsOfScrollMoveTheLinesWithThePage() {
        // Where the lines are handed from the card's copies to the overlay's, they must move exactly as the page does.
        val frame = heroCollapseFrame(2f, rest, spec)
        assertEquals(rest.amount.y - 2f, frame.amountOffset.y, 0.5f)
        assertEquals(rest.eyebrow.y - 2f, frame.eyebrowOffset.y, 0.5f)
        assertTrue(frame.amountScale > 0.99f, "and are as big as they were")
    }

    @Test
    fun theyShrinkAndRiseAndNeverTurnBack() {
        var last = heroCollapseFrame(0f, rest, spec)
        for (scroll in steps.drop(1)) {
            val frame = heroCollapseFrame(scroll, rest, spec)
            assertTrue(frame.amountOffset.y <= last.amountOffset.y + 0.01f, "rising: $scroll")
            assertTrue(frame.amountScale <= last.amountScale + 0.0001f, "shrinking: $scroll")
            assertTrue(frame.progress >= last.progress, "progress: $scroll")
            assertTrue(frame.chipAlpha >= last.chipAlpha - 0.0001f, "the chip only comes in: $scroll")
            last = frame
        }
    }

    @Test
    fun theyArriveWithoutAJoltWhereTheyStop() {
        // The step from one frame to the next shrinks to nothing as they settle (no sudden stop), and is never larger than the page's own.
        val travel = rest.amount.y - (spec.chipTop + spec.padVertical + rest.eyebrowSize.height * EYEBROW_PINNED_SCALE)
        var previous = heroCollapseFrame(travel - 40f, rest, spec).amountOffset.y
        var lastStep = Float.MAX_VALUE
        for (scroll in (travel - 32f).toInt()..travel.toInt() step 8) {
            val y = heroCollapseFrame(scroll.toFloat(), rest, spec).amountOffset.y
            val step = abs(y - previous)
            assertTrue(step <= 8.01f, "no faster than the page: $step at $scroll")
            assertTrue(step <= lastStep + 0.01f, "slowing as it arrives: $step after $lastStep")
            lastStep = step
            previous = y
        }
        assertTrue(lastStep < 2f, "all but stopped where it stops ($lastStep)")
    }

    @Test
    fun onceThePageHasScrolledPastTheyStayPinnedInTheChip() {
        val pinned = heroCollapseFrame(10_000f, rest, spec)
        assertEquals(spec.chipLeft + spec.padHorizontal, pinned.amountOffset.x, 0.01f)
        assertEquals(spec.chipTop + spec.padVertical, pinned.eyebrowOffset.y, 0.01f)
        assertEquals(AMOUNT_PINNED_SCALE, pinned.amountScale, 0.0001f)
        assertEquals(EYEBROW_PINNED_SCALE, pinned.eyebrowScale, 0.0001f)
        assertEquals(1f, pinned.progress)
        assertEquals(1f, pinned.chipAlpha, "the chip is solid, over the page's colour")
        assertEquals(0f, pinned.titleAlpha, "and the title has given way to it")
        // The chip holds both lines, with its padding, and no taller than the header's own controls (42 dp = 84 px).
        assertTrue(pinned.chip.left <= pinned.eyebrowOffset.x - spec.padHorizontal + 0.5f)
        assertTrue(pinned.chip.bottom >= pinned.amountOffset.y + rest.amountSize.height * AMOUNT_PINNED_SCALE)
        assertTrue(pinned.chip.height <= 84f + 1f, "the chip fits the header row: ${pinned.chip.height}")
        // The same frame whatever way the scroll got there.
        val again = heroCollapseFrame(10_000f, rest, spec)
        assertEquals(pinned.chip, again.chip)
    }

    @Test
    fun theChipComesInBeforeTheLinesReachThePagesColour_andTheTitleGivesWayAsTheyArrive() {
        var alphaAtEntry = -1f
        for (scroll in steps) {
            val frame = heroCollapseFrame(scroll, rest, spec)
            if (frame.eyebrowOffset.y <= spec.headerBottom + spec.fade && alphaAtEntry < 0f) alphaAtEntry = frame.chipAlpha
            // Where the lines are over the header's solid colour the chip is fully there, so they are never dark on dark.
            if (frame.eyebrowOffset.y <= spec.headerBottom) assertEquals(1f, frame.chipAlpha, 0.0001f, "scroll $scroll")
            // The title is still whole until the lines are at the header, and gone by the time they are in it.
            if (frame.eyebrowOffset.y >= spec.headerBottom) assertEquals(1f, frame.titleAlpha, 0.0001f, "scroll $scroll")
        }
        assertTrue(alphaAtEntry in 0f..0.15f, "it starts to come in from nothing ($alphaAtEntry)")
    }

    @Test
    fun theTitleOnlyFadesOutAsThePageScrollsOn_andIsNeverGoneBeforeTheLinesAreNear() {
        var last = 1f
        for (scroll in steps) {
            val frame = heroCollapseFrame(scroll, rest, spec)
            assertTrue(frame.titleAlpha <= last + 0.0001f, "scroll $scroll")
            if (frame.eyebrowOffset.y > spec.headerBottom + spec.fade) assertEquals(1f, frame.titleAlpha, 0.0001f, "scroll $scroll")
            last = frame.titleAlpha
        }
        assertEquals(0f, last, 0.0001f)
    }

    @Test
    fun aShortPageThatCannotScrollDrawsNothing() {
        // The state returns no frame at scroll 0 (see HeroCollapseState.frame); the function itself is at rest there.
        assertEquals(0f, heroCollapseFrame(0f, rest, spec).progress)
    }
}
