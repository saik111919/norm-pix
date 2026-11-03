package com.example.normpix.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import com.example.normpix.settings.ui.ProfilePage
import com.example.normpix.testing.TestEnvironment
import java.io.File
import java.util.Locale
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
 * Picking an avatar on the Profile page does not make the page jump (owner, 2026-11-01: "when changing avatar why is the UI
 * jumping"): while the picture is being kept, and once it is, what is under the profile card stays where it was - followed frame
 * by frame through the change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileAvatarSteadyTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var env: TestEnvironment
    private val photoDir get() = File(env.context.filesDir, ProfilePhotos.DIR)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("profile_avatar_steady")
        photoDir.deleteRecursively()
    }

    @After
    fun tearDown() {
        env.close()
        photoDir.deleteRecursively()
    }

    /** Where the name field's top is: everything under the profile card moves with it. */
    private fun nameFieldTop(): Dp = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot().top

    /** Taps [avatar] and follows the name field for a second, frame by frame; the tops it was seen at. */
    private fun pickAndFollow(avatar: String): List<Dp> {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Wear the $avatar avatar").performClick()
        val seen = mutableListOf<Dp>()
        repeat(60) {
            compose.mainClock.advanceTimeBy(16)
            seen += nameFieldTop()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        seen += nameFieldTop()
        return seen
    }

    private fun assertSteady(before: Dp, seen: List<Dp>, what: String) {
        val moved = seen.map { (it - before).value }
        assertTrue(moved.all { kotlin.math.abs(it) < 1f }, "$what: the name field moved by ${moved.distinct()} dp")
    }

    @Test
    fun `wearing an avatar for the first time does not move the page`() {
        compose.setContent { env.Content { ProfilePage() } }
        compose.waitForIdle()
        val before = nameFieldTop()
        assertSteady(before, pickAndFollow("Fox with goggles"), "first avatar")
    }

    @Test
    fun `changing from one avatar to another does not move the page`() {
        compose.setContent { env.Content { ProfilePage() } }
        compose.waitForIdle()
        pickAndFollow("Fox with goggles")
        val before = nameFieldTop()
        assertSteady(before, pickAndFollow("Little robot"), "another avatar")
    }
}
