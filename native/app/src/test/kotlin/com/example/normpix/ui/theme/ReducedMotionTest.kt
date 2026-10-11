package com.example.normpix.ui.theme

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Remove animations" is read again when the user comes back from system settings, so changing it needs no restart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReducedMotionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun setAnimatorScale(scale: Float) {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun leaveAndReturn() {
        val scenario = compose.activityRule.scenario
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
    }

    @Test
    fun theSettingIsReadAtStart_andAgainOnReturningToTheApp() {
        var reduced = false
        compose.setContent { reduced = rememberSystemReducedMotion() }
        compose.waitForIdle()
        assertFalse(reduced, "animations are on")

        setAnimatorScale(0f)
        leaveAndReturn()
        assertTrue(reduced, "the user turned them off in system settings and came back")

        setAnimatorScale(1f)
        leaveAndReturn()
        assertFalse(reduced, "and on again")
    }

    @Test
    fun everythingInTheAppSeesTheOneValue_unlessATestForcesIt() {
        var seen = emptyList<Boolean>()
        compose.setContent {
            CompositionLocalProvider(LocalSystemReducedMotion provides true) {
                val inApp = rememberReducedMotion()
                CompositionLocalProvider(LocalReducedMotionOverride provides false) {
                    seen = listOf(inApp, rememberReducedMotion())
                }
            }
        }
        compose.waitForIdle()
        assertEquals(listOf(true, false), seen, "the system value inside the app; a test's override beats it")
    }
}
