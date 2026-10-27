package com.example.normpix.wallet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import com.example.normpix.wallet.motion.CardTiltState
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

/** An account drawn as a card leans with the phone as a saved card does, and lies flat without a tilt (or with none to give). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h400dp-xhdpi")
class AccountCardTiltTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment("account_card_tilt")
    }

    @After
    fun tearDown() = env.close()

    private val account = AccountCardData(
        id = 1, name = "Salary", kind = AccountKind.Bank, bankName = "HDFC Bank", balance = "₹12,500.00", standing = null,
        isDefault = true, archived = false, cards = 1,
    )

    private var tilt by mutableStateOf<CardTiltState?>(null)

    private fun show(compact: Boolean) {
        compose.setContent {
            env.Content {
                CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                    Box(Modifier.padding(24.dp).width(320.dp)) {
                        AccountCardView(account, onOpen = {}, onManage = {}, compact = compact, tilt = tilt)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun look(): ImageBitmap {
        compose.waitForIdle()
        return compose.onRoot().captureToImage()
    }

    private fun differs(a: ImageBitmap, b: ImageBitmap): Boolean {
        val x = a.toPixelMap()
        val y = b.toPixelMap()
        if (x.width != y.width || x.height != y.height) return true
        for (px in 0 until x.width step 3) for (py in 0 until x.height step 3) if (x[px, py] != y[px, py]) return true
        return false
    }

    private fun leanOf(pitch: Float, roll: Float) = CardTiltState().apply { this.pitch = pitch; this.roll = roll }

    @Test
    fun aLeaningPhoneTurnsTheCard_andAFlatOneDoesNot() {
        show(compact = false)
        val flat = look()
        compose.runOnIdle { tilt = CardTiltState() }
        assertFalse(differs(flat, look()), "a tilt at rest draws what no tilt draws")
        compose.runOnIdle { tilt = leanOf(pitch = 4f, roll = 5f) }
        assertTrue(differs(flat, look()), "a leaning card is drawn turned, with the sheen across it")
        compose.runOnIdle { tilt = null }
        assertFalse(differs(flat, look()), "and back to flat when the tilt is taken away")
    }

    @Test
    fun theCarouselsCompactCardLeansToo() {
        show(compact = true)
        val flat = look()
        compose.runOnIdle { tilt = leanOf(pitch = -3f, roll = -4f) }
        assertTrue(differs(flat, look()))
    }
}
