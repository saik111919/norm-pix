package com.example.normpix.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.normpix.settings.AccentColor
import com.materialkolor.ktx.toHct
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The pure half of the artwork colours: which seeds are worth a scheme, what the scheme is made of, what it must keep from the
 * app's own, and that its text pairs read for any seed - black, white and grey included, and in Pure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtworkColorsTest {
    private val seeds = mapOf(
        "black" to Color(0xFF000000),
        "white" to Color(0xFFFFFFFF),
        "grey" to Color(0xFF808080),
        "red" to Color(0xFFE53935),
        "yellow" to Color(0xFFFDD835),
        "green" to Color(0xFF43A047),
        "blue" to Color(0xFF1E88E5),
        "pastel" to Color(0xFFF8BBD0),
    )

    private val themes = listOf(
        Triple("Light", AppTheme.accentScheme(AccentColor.Green, isDark = false), false),
        Triple("Dark", AppTheme.accentScheme(AccentColor.Green, isDark = true), false),
        Triple("Pure light", AppTheme.pureScheme(isDark = false), true),
        Triple("Pure dark", AppTheme.pureScheme(isDark = true), true),
        Triple("Volt dark", AppTheme.accentScheme(AccentColor.Volt, isDark = true), true),
    )

    private fun ratio(a: Color, b: Color): Double {
        val x = a.luminance().toDouble() + 0.05
        val y = b.luminance().toDouble() + 0.05
        return max(x, y) / min(x, y)
    }

    @Test
    fun everyTextPairReadsForEverySeedInEveryTheme() {
        val failures = mutableListOf<String>()
        for ((themeName, base, keepSurfaces) in themes) {
            for ((seedName, seed) in seeds) {
                val s = ArtworkColors.scheme(base, seed, keepSurfaces)
                val pairs = mapOf(
                    "onPrimary/primary" to (s.onPrimary to s.primary),
                    "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
                    "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
                    "onTertiaryContainer/tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
                    "onSurface/surface" to (s.onSurface to s.surface),
                    "onSurfaceVariant/surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant),
                )
                for ((pair, colors) in pairs) {
                    val r = ratio(colors.first, colors.second)
                    if (r < 4.5) failures += "$themeName / $seedName / $pair: ${"%.2f".format(r)}:1"
                }
            }
        }
        assertEquals(emptyList(), failures, "text pairs under 4.5:1")
    }

    @Test
    fun anArtworkSchemeTakesTheSeedsHue_andKeepsTheAppsErrorAndScrim() {
        val base = AppTheme.accentScheme(AccentColor.Green, isDark = false)
        val s = ArtworkColors.scheme(base, seeds.getValue("red"), keepSurfaces = false)
        assertNotEquals(base.primary, s.primary, "the primary follows the picture")
        assertNotEquals(base.surface, s.surface, "so does the surface, softly")
        assertEquals(base.error, s.error)
        assertEquals(base.onError, s.onError)
        assertEquals(base.errorContainer, s.errorContainer)
        assertEquals(base.onErrorContainer, s.onErrorContainer)
        assertEquals(base.scrim, s.scrim)
    }

    @Test
    fun inPureAndVoltOnlyTheAccentFamiliesFollow_andEverySurfaceStays() {
        for ((name, base, keepSurfaces) in themes.filter { it.third }) {
            val s = ArtworkColors.scheme(base, seeds.getValue("blue"), keepSurfaces = true)
            assertNotEquals(base.primary, s.primary, "$name: the primary follows")
            assertNotEquals(base.secondaryContainer, s.secondaryContainer, "$name: so do the containers")
            assertEquals(base.background, s.background, "$name background")
            assertEquals(base.surface, s.surface, "$name surface")
            assertEquals(base.surfaceVariant, s.surfaceVariant, "$name surfaceVariant")
            assertEquals(base.surfaceContainerLowest, s.surfaceContainerLowest, "$name surfaceContainerLowest")
            assertEquals(base.surfaceContainerHighest, s.surfaceContainerHighest, "$name surfaceContainerHighest")
            assertEquals(base.onSurface, s.onSurface, "$name onSurface")
            assertEquals(base.outline, s.outline, "$name outline")
            assertEquals(base.error, s.error, "$name error")
            assertTrue(keepSurfaces)
        }
        // Pure black stays black.
        val pureDark = ArtworkColors.scheme(AppTheme.pureScheme(isDark = true), seeds.getValue("green"), keepSurfaces = true)
        assertEquals(AppTheme.pureScheme(isDark = true).background, pureDark.background)
    }

    @Test
    fun aGreyHasNoColourToMakeAThemeFrom() {
        for (name in listOf("black", "white", "grey")) assertFalse(ArtworkColors.usable(seeds.getValue(name)), name)
        for (name in listOf("red", "yellow", "green", "blue")) assertTrue(ArtworkColors.usable(seeds.getValue(name)), name)
        assertFalse(ArtworkColors.usable(Color.Transparent))
        // A black card's colour is not red, though a scheme made from it would be: its seed is none.
        assertFalse(ArtworkColors.usable(Color(0xFF2B2B2B)))
    }

    @Test
    fun twoSeedsOfTheSameHueAreTheSame_andTheirNoneIsNotOneOfThem() {
        val red = seeds.getValue("red")
        assertTrue(ArtworkColors.isSame(red, red))
        assertTrue(ArtworkColors.isSame(null, null))
        assertFalse(ArtworkColors.isSame(red, null))
        assertFalse(ArtworkColors.isSame(null, red))
        assertFalse(ArtworkColors.isSame(red, seeds.getValue("blue")))
        // A shade lighter or darker of one hue makes the same scheme (the style takes the hue and not the tone).
        val hue = red.toHct().hue
        assertTrue(ArtworkColors.isSame(red, Color(com.materialkolor.hct.Hct.from(hue, 60.0, 40.0).toInt())))
    }

    private fun solid(argb: Int, count: Int = 64 * 64) = IntArray(count) { argb }

    @Test
    fun aRedPictureSaysRed() {
        val seed = assertNotNull(ArtworkColors.seedFromPixels(solid(0xFFD32F2F.toInt())))
        val hue = seed.toHct().hue
        assertTrue(hue < 30.0 || hue > 340.0, "hue $hue is red")
    }

    @Test
    fun aGreyPictureSaysNothing_andSoDoesAWhiteOne() {
        assertNull(ArtworkColors.seedFromPixels(solid(0xFF808080.toInt())), "grey")
        assertNull(ArtworkColors.seedFromPixels(solid(0xFFFFFFFF.toInt())), "white")
        assertNull(ArtworkColors.seedFromPixels(solid(0xFF101010.toInt())), "near black")
    }

    @Test
    fun anEmptyOrTransparentOrOnePixelPictureIsHandled() {
        assertNull(ArtworkColors.seedFromPixels(IntArray(0)), "empty")
        assertNull(ArtworkColors.seedFromPixels(solid(0x00000000, 32 * 32)), "fully transparent")
        val one = ArtworkColors.seedFromPixels(intArrayOf(0xFF1E88E5.toInt()))
        // One pixel is a picture too, though a quantizer may not find a share worth keeping in it: it must not throw.
        if (one != null) assertTrue(ArtworkColors.usable(one))
    }

    @Test
    fun theMostProminentColourWins_overABorderOfAnother() {
        val pixels = IntArray(100 * 100) { i -> if (i % 100 < 8) 0xFF2E7D32.toInt() else 0xFF1565C0.toInt() }
        val hue = assertNotNull(ArtworkColors.seedFromPixels(pixels)).toHct().hue
        assertTrue(hue in 200.0..280.0, "hue $hue is the blue that fills the picture, not the green edge")
    }

    @Test
    fun aLargeNoisyPictureIsReadQuickly_atTheSizeItIsDecodedAt() {
        val random = Random(7)
        val pixels = IntArray(128 * 128) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
        val started = System.nanoTime()
        ArtworkColors.seedFromPixels(pixels)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue(ms < 3000, "$ms ms")
    }
}
