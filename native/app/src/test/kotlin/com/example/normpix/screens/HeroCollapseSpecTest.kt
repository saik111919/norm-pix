package com.example.normpix.screens

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.normpix.ui.components.FloatingTopControls
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The measures Home's number collapse is given on a real window. The pure rule (HeroCollapseTest) takes them as numbers, so a wrong one
 * would pass there: the chip's fade was 40 dp while the page's own colour fades out over the list across 12 dp, and the chip showed as a
 * patch on the hero card behind the number from the first pixel of scroll.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class HeroCollapseSpecTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theChipComesInOverTheStretchTheHeadersColourFadesOutOver() {
        var spec: HeroCollapseSpec? = null
        var pageFade = 0f
        compose.setContent {
            spec = rememberHeroCollapseSpec(null)
            pageFade = with(LocalDensity.current) { FloatingTopControls.fade.toPx() }
        }
        compose.waitForIdle()
        assertEquals(pageFade, spec!!.fade, 0.01f, "the chip fades in across the page colour's own fade")
        assertEquals(24f, spec!!.fade, 0.01f, "which is 12 dp, 24 px at 2x")
        // The floating header's colour is whole down to the start of that fade, so that is where the chip is solid.
        assertEquals(spec!!.headerBottom - spec!!.fade, spec!!.chipSolidAt, 0.01f, "solid where the colour is whole")
    }
}
