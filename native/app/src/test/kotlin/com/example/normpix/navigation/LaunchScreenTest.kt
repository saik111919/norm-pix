package com.example.normpix.navigation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What Android 12 and later draw over a cold start, before the app exists: the window's own colour with, in it, the cookie's intro
 * (an animated vector the system plays itself, so there is no empty screen while the app starts and nothing the app is busy with
 * can make it stutter). The Name splash, which writes the name itself, is given an empty one instead (Android 13 and later are told
 * to use it). Seen in a recording of a phone: a blank launch screen for more than a second, then the cookie, then a long wait.
 */
@RunWith(RobolectricTestRunner::class)
// Native graphics, or a vector draws nothing at all and the transparency test would pass for the wrong reason.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LaunchScreenTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The launch-screen attributes a window theme gives, as Android 12 and later read them. */
    private class LaunchLook(val background: Int, val iconResource: Int, val durationMs: Int)

    private fun launchScreen(style: Int = R.style.Theme_NormPix): LaunchLook {
        val theme = context.resources.newTheme().apply { applyStyle(style, true) }
        val attrs = intArrayOf(
            android.R.attr.windowSplashScreenBackground,
            android.R.attr.windowSplashScreenAnimatedIcon,
            android.R.attr.windowSplashScreenAnimationDuration,
        )
        val array = theme.obtainStyledAttributes(attrs)
        try {
            return LaunchLook(array.getColor(0, 0), array.getResourceId(1, 0), array.getInt(2, 0))
        } finally {
            array.recycle()
        }
    }

    private fun windowColor() = context.getColor(R.color.window_background)

    @Test
    @Config(qualifiers = "notnight")
    fun inTheDayTheLaunchScreenIsTheWindowsColourWithTheCookiesIntroInIt() {
        val screen = launchScreen()
        assertEquals(windowColor(), screen.background, "the window's own colour, which the cookie splash draws on")
        // Android 14 and later: the system's own surface colour, which is the app's page for the wallpaper palette, so the launch
        // screen, the window and the page are one colour from the first frame (recorded on a phone: a greenish ground, a pinkish page).
        assertEquals(context.getColor(android.R.color.system_surface_light), screen.background)
        assertEquals(R.drawable.avd_splash, screen.iconResource, "the cookie's intro, played by the system")
        assertEquals(CookieMotion.INTRO_MS.toInt(), screen.durationMs, "as long as the intro it plays (Android 12 reads it)")
    }

    @Test
    @Config(qualifiers = "night")
    fun inTheDarkItIsTheSystemsDarkSurfaceWithTheSameIntro() {
        val screen = launchScreen()
        assertEquals(windowColor(), screen.background)
        assertEquals(context.getColor(android.R.color.system_surface_dark), screen.background)
        assertEquals(R.drawable.avd_splash, screen.iconResource)
    }

    @Test
    @Config(sdk = [33], qualifiers = "notnight")
    fun beforeAndroid14TheWindowColourIsAFixedStandInForTheDaySurface() {
        // No system surface colour to take: a close fixed one, and the splash blends from it into the palette's surface.
        val screen = launchScreen()
        assertEquals(0xFFFBFDF8.toInt(), screen.background)
        assertEquals(windowColor(), screen.background)
        assertEquals(R.drawable.avd_splash, screen.iconResource)
    }

    @Test
    @Config(sdk = [33], qualifiers = "night")
    fun beforeAndroid14TheDarkWindowColourIsAFixedStandIn() {
        val screen = launchScreen()
        assertEquals(0xFF101410.toInt(), screen.background)
        assertEquals(windowColor(), screen.background)
        assertEquals(R.drawable.avd_splash, screen.iconResource)
    }

    @Test
    @Config(qualifiers = "night")
    fun theNameSplashesLaunchScreenIsEmpty_inTheSameWindowColour() {
        val screen = launchScreen(R.style.Theme_NormPix_LaunchBlank)
        assertEquals(windowColor(), screen.background, "the colour is the window's, taken from the theme it is built on")
        assertEquals(R.drawable.splash_icon_empty, screen.iconResource, "no cookie to play before a name is written")
        assertNotEquals(R.drawable.avd_splash, screen.iconResource)
    }

    @Test
    fun theEmptyIconIsFullyTransparent_soNothingIsDrawnButTheColour() {
        val icon = context.getDrawable(R.drawable.splash_icon_empty)!!
        val side = 96
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, side, side)
        icon.draw(Canvas(bitmap))
        val pixels = IntArray(side * side).also { bitmap.getPixels(it, 0, side, 0, 0, side, side) }
        assertTrue(pixels.all { (it ushr 24) == 0 }, "not one visible pixel")
    }
}
