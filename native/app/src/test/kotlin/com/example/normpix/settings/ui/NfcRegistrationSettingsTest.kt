package com.example.normpix.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.NfcAvailability
import java.util.Locale
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

/**
 * Limiting the NFC reveal to the user's own tags, from Security & privacy: the switch, registering a tag (and being told
 * when a tag can't be recognised), the second tag, and taking them all away - which must also let any tag work again, so
 * a lost tag never locks anyone out of their cards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class NfcRegistrationSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val mine = byteArrayOf(0x04, 0x5A, 0x1C, 0x32, 0x7B, 0x66, 0x80.toByte())
    private val spare = byteArrayOf(0x04, 0x77, 0x01, 0x02, 0x03, 0x04, 0x05)
    private val phoneLikeId = byteArrayOf(0x08, 0x13, 0x37, 0x42)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("nfc_registration_settings")
    }

    @After
    fun tearDown() = env.close()

    private fun show() {
        compose.setContent { env.Content { SecuritySettingsPage() } }
        compose.waitForIdle()
    }

    private fun tap(id: ByteArray): Boolean {
        var accepted = false
        compose.runOnUiThread { accepted = env.nfc.tap(id) }
        compose.waitForIdle()
        return accepted
    }

    private val registeredOnly get() = env.preferences.state.value.nfcRevealRegisteredOnly

    @Test
    fun theOptionAppearsOnlyOnceTheTapToRevealGateIsOn() {
        show()
        compose.onNodeWithText("Tap an NFC tag to reveal").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Only my registered tags").fetchSemanticsNodes().size)

        compose.onNodeWithText("Tap an NFC tag to reveal").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Only my registered tags").assertIsDisplayed()
        compose.onNodeWithText("Register your own tag, and only it will work").assertIsDisplayed()
    }

    @Test
    fun turningItOnAsksForTheTagFirstAndOnlyThenLimitsTheReveal() {
        env.preferences.setNfcTagRevealEnabled(true)
        show()

        compose.onNodeWithText("Only my registered tags").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hold the tag you want to use").assertIsDisplayed()
        assertFalse(registeredOnly, "not limited until there is a tag to ask for")

        assertTrue(tap(mine))
        compose.waitForIdle()
        assertEquals(1, env.registeredTags.count.value)
        assertTrue(env.registeredTags.matches(mine))
        assertTrue(registeredOnly)
        compose.onNodeWithText("Only a tag you've registered will work").assertIsDisplayed()
    }

    @Test
    fun cancellingTheRegistrationLeavesTheRevealAsItWas() {
        env.preferences.setNfcTagRevealEnabled(true)
        show()

        compose.onNodeWithText("Only my registered tags").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()

        assertFalse(registeredOnly)
        assertEquals(0, env.registeredTags.count.value)
        assertFalse(env.nfc.listening)
    }

    @Test
    fun aTagThatChangesItsIdEveryTapIsExplainedAndTheSheetKeepsWaiting() {
        env.preferences.setNfcTagRevealEnabled(true)
        show()
        compose.onNodeWithText("Only my registered tags").performClick()
        compose.waitForIdle()

        assertFalse(tap(phoneLikeId), "it can't be recognised, so it isn't taken")
        compose.onNodeWithText("different ID every time", substring = true).assertIsDisplayed()
        assertEquals(0, env.registeredTags.count.value)
        assertTrue(env.nfc.listening, "and another tag can still be tried")

        assertTrue(tap(mine))
        assertEquals(1, env.registeredTags.count.value)
    }

    @Test
    fun aSecondTagCanBeAddedAndEitherWorks() {
        env.preferences.setNfcTagRevealEnabled(true)
        env.registeredTags.register(mine)
        env.preferences.setNfcRevealRegisteredOnly(true)
        show()

        compose.onNodeWithText("Register another tag").performClick()
        compose.waitForIdle()
        assertTrue(tap(spare))
        compose.waitForIdle()

        assertEquals(2, env.registeredTags.count.value)
        assertTrue(env.registeredTags.matches(mine) && env.registeredTags.matches(spare))
        compose.onNodeWithText("Remove registered tags").assertIsDisplayed()
    }

    @Test
    fun removingTheTagsAsksFirstAndLetsAnyTagWorkAgain() {
        env.preferences.setNfcTagRevealEnabled(true)
        env.registeredTags.register(mine)
        env.preferences.setNfcRevealRegisteredOnly(true)
        show()

        compose.onNodeWithText("Remove registered tags").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Remove your registered tags?").assertIsDisplayed()
        assertEquals(1, env.registeredTags.count.value, "nothing is removed before it is confirmed")

        compose.onNodeWithText("Remove").performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) { env.registeredTags.count.value == 0 }
        assertFalse(registeredOnly, "with no tag left, the limit goes too - otherwise nothing could ever reveal a card")
    }

    @Test
    fun theLimitCanBeTurnedOffWithoutTheTagSoALostTagNeverLocksYouOut() {
        env.preferences.setNfcTagRevealEnabled(true)
        env.registeredTags.register(mine)
        env.preferences.setNfcRevealRegisteredOnly(true)
        show()

        compose.onNodeWithText("Lost your tag?", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Only my registered tags").performClick()
        compose.waitForIdle()

        assertFalse(registeredOnly)
        assertEquals(1, env.registeredTags.count.value, "turning the limit off keeps the tag for later")
    }

    @Test
    fun withNfcSwitchedOffTheLimitCannotBeTurnedOnButCanStillBeTurnedOff() {
        env.preferences.setNfcTagRevealEnabled(true)
        env.nfc.state = NfcAvailability.Disabled
        show()
        compose.onNodeWithText("Only my registered tags").assertIsNotEnabled()

        env.registeredTags.register(mine)
        env.preferences.setNfcRevealRegisteredOnly(true)
        compose.waitForIdle()
        compose.onNodeWithText("Only my registered tags").assertIsEnabled()
    }
}
