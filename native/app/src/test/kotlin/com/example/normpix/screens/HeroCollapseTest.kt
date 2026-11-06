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
        chipSolidAt = 48f + 116f,
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
        // They take COLLAPSE_STRETCH times their way in scroll, so that is where they stop.
        val way = rest.amount.y - (spec.chipTop + spec.padVertical + rest.eyebrowSize.height * EYEBROW_PINNED_SCALE)
        val travel = way * COLLAPSE_STRETCH
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
            // The title is still whole until the chip starts to come in over it, and gone by the time the lines are in the header.
            if (frame.eyebrowOffset.y >= spec.headerBottom + spec.fade) assertEquals(1f, frame.titleAlpha, 0.0001f, "scroll $scroll")
            assertTrue(frame.titleAlpha <= 1f - frame.chipAlpha + 0.0001f, "the title fades with the chip: scroll $scroll")
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

    // A real phone (411 dp at 2x), numbers read from a render of Home: the page's colour ends 116 px down and fades out over 24 px, and the
    // card's lines start only 70 px below that - the case the rest of this class's round numbers do not have.
    private val phoneRest = HeroCollapseRest(
        eyebrow = Offset(96f, 186f),
        eyebrowSize = Size(300f, 44f),
        amount = Offset(96f, 232f),
        amountSize = Size(560f, 150f),
    )
    private val phoneSpec = HeroCollapseSpec(
        chipLeft = 32f,
        chipTop = 16f,
        padHorizontal = 24f,
        padVertical = 4f,
        headerBottom = 116f,
        chipSolidAt = 116f - 24f,
        fade = 24f,
        listTop = 0f,
        columnLeft = 0f,
    )
    private val phoneSteps = (4..800 step 4).map { it.toFloat() }

    @Test
    fun theLinesTrailTheCardAndNeverRaceIt() {
        // They used to get ahead of the page by up to a quarter of their way (the card still under them, the number already gone).
        var last = heroCollapseFrame(0f, phoneRest, phoneSpec)
        for (scroll in phoneSteps) {
            val frame = heroCollapseFrame(scroll, phoneRest, phoneSpec)
            assertTrue(frame.amountOffset.y >= phoneRest.amount.y - scroll - 0.01f, "the amount is ahead of the page at $scroll")
            assertTrue(frame.eyebrowOffset.y >= phoneRest.eyebrow.y - scroll - 0.01f, "the label is ahead of the page at $scroll")
            assertTrue(last.amountOffset.y - frame.amountOffset.y <= 4.01f, "faster than the page between ${scroll - 4} and $scroll")
            last = frame
        }
    }

    @Test
    fun theShrinkIsSpreadOverAWholeStretchOfScroll() {
        // The amount used to fall from full size to a third in about 36 dp of scroll (0.075 of its size in every 8 px at the steepest).
        var last = 1f
        var steepest = 0f
        for (scroll in 8..800 step 8) {
            val scale = heroCollapseFrame(scroll.toFloat(), phoneRest, phoneSpec).amountScale
            steepest = maxOf(steepest, last - scale)
            last = scale
        }
        assertTrue(steepest < 0.065f, "the steepest 8 px of scroll takes $steepest of the amount's size")
        assertEquals(AMOUNT_PINNED_SCALE, last, 0.0001f, "and it does get there")
    }

    @Test
    fun noChipIsDrawnWhileTheLinesAreStillOnTheCard() {
        // The chip used to be coming in from the first pixel of scroll (a patch on the card behind the number): on a phone the lines start
        // inside the stretch it fades in over. It now waits for the page colour's own fade.
        for (scroll in 0..30) {
            val frame = heroCollapseFrame(scroll.toFloat(), phoneRest, phoneSpec)
            assertEquals(0f, frame.chipAlpha, "a chip at $scroll px")
            assertEquals(1f, frame.titleAlpha, "the title is whole at $scroll px")
        }
        // On a floating header the page's colour is whole 12 dp above its foot (24 px here), so that is where the chip is solid; it has
        // not started at the foot's own height (the colour is nothing there).
        val atTheFoot = phoneSteps.first { heroCollapseFrame(it, phoneRest, phoneSpec).eyebrowOffset.y <= phoneSpec.headerBottom }
        assertEquals(0f, heroCollapseFrame(atTheFoot, phoneRest, phoneSpec).chipAlpha, 0.1f, "barely there at the foot of the header")
        val whereTheColourIsWhole = phoneSteps.first { heroCollapseFrame(it, phoneRest, phoneSpec).eyebrowOffset.y <= phoneSpec.chipSolidAt }
        assertEquals(1f, heroCollapseFrame(whereTheColourIsWhole, phoneRest, phoneSpec).chipAlpha, "solid where the page colour is whole")
    }

    @Test
    fun theChipIsACapsuleWithRoomForTheLinesAtEverySize() {
        // A box with square corners behind a big number was the patch. The chip round the lines is always as round as it is tall, and
        // has at least its own room round them.
        for (scroll in phoneSteps) {
            val frame = heroCollapseFrame(scroll, phoneRest, phoneSpec)
            assertEquals(frame.chip.height / 2f, frame.chipRadius, 0.01f, "round ends at $scroll")
            assertTrue(frame.eyebrowOffset.x - frame.chip.left >= phoneSpec.padHorizontal - 0.01f, "room at the left at $scroll")
            assertTrue(frame.chip.top <= frame.eyebrowOffset.y - phoneSpec.padVertical + 0.01f, "room above at $scroll")
            assertTrue(frame.chip.bottom >= frame.amountOffset.y + phoneRest.amountSize.height * frame.amountScale, "room below at $scroll")
        }
    }
}
