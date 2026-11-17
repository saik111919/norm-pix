package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.settings.AmountKeypad
import com.example.normpix.settings.PrefValue
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.theme.StatusColorRole
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The amount field's own keypad and calculator on screen (2026-11-18): the keypad opens under the amount while it is typed in and
 * goes with Done; a sum typed on it hands the form its total and says it under the amount; the sum becomes its total when the amount
 * is left; one that can't be an amount says why and hands the form nothing; and Settings' "Phone keyboard" brings no keypad at all,
 * the keyboard's own signs still adding up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class AmountKeypadTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private var amount by mutableStateOf("")

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("amount_keypad_test")
    }

    @After
    fun tearDown() = env.close()

    private fun show() {
        compose.setContent {
            env.Content {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    HeroAmountField(value = amount, onValueChange = { amount = it }, currency = CurrencySpec.INR, accent = accent)
                    Text("Next field")
                }
            }
        }
        compose.waitForIdle()
    }

    private val accent = StatusColorRole(
        androidx.compose.ui.graphics.Color.Red,
        androidx.compose.ui.graphics.Color.White,
        androidx.compose.ui.graphics.Color.Yellow,
        androidx.compose.ui.graphics.Color.Black,
    )

    private fun field() = compose.onNodeWithContentDescription("Amount in INR")

    private fun key(name: String) = compose.onNodeWithTag(amountKeyTag(name))

    private fun keys(vararg names: String) {
        for (name in names) key(name).performClick()
        compose.waitForIdle()
    }

    private fun keypadShown() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag).and(
        SemanticsMatcher("an amount key") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("amount_key_") == true },
    )).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the keypad opens under the amount as it is typed in, and Done puts it away`() {
        show()
        assertTrue(!keypadShown(), "nothing until the amount is typed in")
        field().performClick()
        compose.waitForIdle()
        key("digit_7").assertIsDisplayed()
        // TalkBack hears what each key does.
        compose.onNodeWithContentDescription("Divide").assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete").assertIsDisplayed()

        keys("digit_4", "digit_8", "digit_0")
        assertEquals("480", amount)
        field().assert(isFocused())

        keys("done")
        assertTrue(!keypadShown(), "Done puts the keypad away")
        assertEquals("480", amount)

        // A tap on the amount brings it back, the cursor after the digits typed.
        field().performClick()
        compose.waitForIdle()
        key("digit_7").assertIsDisplayed()
        keys("digit_5")
        assertEquals("4805", amount)
    }

    @Test
    fun `a sum hands the form its total, says it under the amount, and becomes it when the amount is left`() {
        show()
        field().performClick()
        compose.waitForIdle()
        keys("digit_1", "digit_2", "digit_0", "plus", "digit_4", "digit_5", "plus", "digit_3", "digit_0")
        assertEquals("195", amount, "the form holds the total as it is typed")
        compose.onNodeWithText("= ₹195").assertIsDisplayed()
        field().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Comes to ₹195"))

        // A sign after a sign takes its place; times before plus.
        keys("plus", "multiply", "digit_2")
        assertEquals("225", amount, "120 + 45 + 30 × 2")

        keys("done")
        assertEquals("225", amount)
        compose.onNodeWithText("₹ 225", useUnmergedTree = true).assertExists()
        assertEquals(0, compose.onAllNodes(SemanticsMatcher("total line") { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text.startsWith("= ") } == true }).fetchSemanticsNodes().size)
    }

    @Test
    fun `a sum that can't be an amount says why and hands the form nothing, and delete puts it right`() {
        show()
        field().performClick()
        compose.waitForIdle()
        keys("digit_5", "divide", "digit_0")
        assertEquals("", amount, "nothing to save")
        compose.onNodeWithText("Can't divide by zero").assertIsDisplayed()
        field().assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Can't divide by zero"))

        keys("delete", "digit_2")
        assertEquals("2.50", amount, "5 ÷ 2, as an amount is written to be edited")
        assertEquals(0, compose.onAllNodes(SemanticsMatcher("an error") { it.config.contains(SemanticsProperties.Error) }).fetchSemanticsNodes().size)
    }

    @Test
    fun `the point, double zero and holding delete`() {
        show()
        field().performClick()
        compose.waitForIdle()
        keys("point", "digit_5")
        assertEquals("0.5", amount, "a point first is 0.")
        keys("clear", "digit_1", "double_zero")
        assertEquals("100", amount)
        keys("equals")
        assertEquals("100", amount, "nothing to work out: refused, unchanged")
    }

    @Test
    fun `with the phone keyboard chosen there is no keypad, and the keyboard's own signs still add up`() {
        env.container.preferences.setAmountKeypad(AmountKeypad.Phone)
        show()
        field().performClick()
        compose.waitForIdle()
        assertTrue(!keypadShown(), "the phone's keyboard, not the app's keypad")
        field().performTextInput("200-50")
        compose.waitForIdle()
        assertEquals("150", amount)
        compose.onNodeWithText("= ₹150").assertIsDisplayed()
    }

    @Test
    fun `the choice is kept and travels with a backup`() {
        assertEquals(AmountKeypad.App, env.container.preferences.state.value.amountKeypad, "the app's keypad unless the user picks otherwise")
        env.container.preferences.setAmountKeypad(AmountKeypad.Phone)
        assertEquals(AmountKeypad.Phone, env.container.preferences.state.value.amountKeypad)
        assertEquals(PrefValue.StringPref("Phone"), env.container.preferences.exportAll()["amount_keypad"])
    }
}
