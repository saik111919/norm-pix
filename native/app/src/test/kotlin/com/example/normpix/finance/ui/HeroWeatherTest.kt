package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.BudgetHealth
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.LocalLiveMotion
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home's month card wears the month's weather (owner, 2026-11-01: "animation on the home hero section, similar to Overcast"):
 * which weather a month gets, and that it moves only where it may - the real app, the setting on - and is still everywhere else.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class HeroWeatherTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment("hero_weather")
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun `the month's weather follows what was spent and the budget`() {
        assertEquals(HeroWeather.Snow, heroWeatherFor(0, BudgetHealth.Exceeded, null), "nothing spent yet: snow, whatever else")
        assertEquals(HeroWeather.Clear, heroWeatherFor(50_000, BudgetHealth.Healthy, 2.0), "on track with a budget: sun")
        assertEquals(HeroWeather.Heat, heroWeatherFor(50_000, BudgetHealth.Approaching, -0.5), "nearly used: heat")
        assertEquals(HeroWeather.Rain, heroWeatherFor(50_000, BudgetHealth.Exceeded, -0.5), "over budget: rain")
        // With no budget to go by, the month is hot only when it is well up on last month.
        assertEquals(HeroWeather.Heat, heroWeatherFor(50_000, null, HEAT_CHANGE))
        assertEquals(HeroWeather.Clear, heroWeatherFor(50_000, null, HEAT_CHANGE - 0.01))
        assertEquals(HeroWeather.Clear, heroWeatherFor(50_000, null, null))
    }

    private fun show(live: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLiveMotion provides live) {
                env.Content {
                    Box(Modifier.testTag("hero").width(379.dp)) {
                        Hero(weather = HeroWeather.Rain) { HeroAmount("₹1,384.00") }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
    }

    private fun shot(): ImageBitmap = compose.onNodeWithTag("hero").captureToImage()

    private fun same(a: ImageBitmap, b: ImageBitmap): Boolean = a.asAndroidBitmap().sameAs(b.asAndroidBitmap())

    @Test
    fun `in the real app the weather moves`() {
        show(live = true)
        val first = shot()
        compose.mainClock.advanceTimeBy(400)
        assertFalse(same(first, shot()), "the rain has fallen on")
    }

    @Test
    fun `anywhere else it is a still picture, so a screen can settle`() {
        show(live = false)
        val first = shot()
        compose.mainClock.advanceTimeBy(400)
        assertTrue(same(first, shot()), "nothing moves outside the real app (tests, previews)")
        // And with no endless motion, the screen settles: this returns.
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    @Test
    fun `turned off in Settings the card is still and plain`() {
        env.preferences.setHeroWeather(false)
        show(live = true)
        val first = shot()
        compose.mainClock.advanceTimeBy(400)
        assertTrue(same(first, shot()), "Hero weather off: nothing moves")
        assertFalse(env.preferences.state.value.heroWeather)
    }
}
