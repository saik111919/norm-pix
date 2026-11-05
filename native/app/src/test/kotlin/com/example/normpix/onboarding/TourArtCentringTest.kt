package com.example.normpix.onboarding

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A sticker's picture is drawn centred on its pad, so the artwork inside its box has to be centred too. Some of the 3D pictures are
 * drawn off the middle of theirs (the credit card low in its box), which left the welcome step's card pushed down in its blob; this
 * measures where each picture really is opaque and holds the nudge in `pictureOffset` to it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TourArtCentringTest {
    private fun decode(emoji: String): Bitmap {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return context.assets.open("fluent3d/$emoji.webp").use { BitmapFactory.decodeStream(it) }
    }

    /** The middle of the opaque part, as shares of the box measured from its middle: (x to the right, y downwards). */
    private fun artworkMiddle(bitmap: Bitmap): Pair<Float, Float> {
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        var left = bitmap.width
        var right = -1
        var top = bitmap.height
        var bottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if ((pixels[y * bitmap.width + x] ushr 24) > 24) {
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
            }
        }
        assertTrue(right >= left && bottom >= top, "the picture has something in it")
        val cx = (left + right) / 2f - (bitmap.width - 1) / 2f
        val cy = (top + bottom) / 2f - (bitmap.height - 1) / 2f
        return cx / bitmap.width to cy / bitmap.height
    }

    @Test
    fun everyPictureOnAPadIsCentredOnceItsNudgeIsApplied() {
        assertTrue(paddedPictures.size >= 8, "the tour's pads were found: $paddedPictures")
        for (emoji in paddedPictures) {
            val (x, y) = artworkMiddle(decode(emoji))
            val (dx, dy) = pictureOffset(emoji)
            // Within 2.5 px of the 128 px box: about a dp at the size these are drawn.
            assertEquals(0f, x - dx, 2.5f / 128f, "$emoji is off to the side by ${x - dx} of its box")
            assertEquals(0f, y - dy, 2.5f / 128f, "$emoji is off up or down by ${y - dy} of its box")
        }
    }

    @Test
    fun theCreditCardOfTheWelcomeStepIsTheOneThatWasLow() {
        val (_, y) = artworkMiddle(decode("1f4b3"))
        assertTrue(y > 0.1f, "its artwork sits low in its box (by $y), which is why it is nudged")
        assertTrue(abs(pictureOffset("1f4b3").second - y) < 2.5f / 128f)
    }
}
