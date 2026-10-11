package com.example.normpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.ui.ChoiceOption
import com.example.normpix.finance.ui.ConnectedChoiceRow
import com.example.normpix.finance.ui.HeroAmountField
import com.example.normpix.finance.ui.IconBadge
import com.example.normpix.finance.ui.connectedFieldShapes
import com.example.normpix.settings.ThemeMode
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import com.example.normpix.ui.theme.NormPixTheme
import com.example.normpix.ui.theme.StatusColorRole
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The editor's input vocabulary in its Material You form: a unified pill for choices, a centred display-size amount
 * with no underline, 40dp circular icon badges, equal-width split chips, and a 56dp pill Save.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class EditorStyleTest {
    @get:Rule
    val compose = createComposeRule()

    private val density = 3f // xxhdpi
    private val inr = CurrencySpec("INR", "₹", "Indian rupee", 2, indianGrouping = true)
    private val accent = StatusColorRole(Color(0xFFD97B3A), Color.Black, Color(0xFFFFB68A), Color(0xFF4A2200))

    private fun themed(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            NormPixTheme(Appearance(mode = ThemeMode.Dark)) {
                CompositionLocalProvider(LocalReducedMotionOverride provides true) { content() }
            }
        }
        compose.waitForIdle()
    }

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    // --- 40dp circular badges ---------------------------------------------------------------------------------

    @Test
    fun anIconBadgeIsA40dpCircle() {
        themed {
            Box(Modifier.background(Color.White).padding(10.dp)) {
                IconBadge(Icons.Outlined.Edit, Modifier.testTag("badge"), container = Color.Red, content = Color.White)
            }
        }
        val bounds = compose.onNodeWithTag("badge").getUnclippedBoundsInRoot()
        assertEquals(40f, (bounds.right - bounds.left).value, 0.5f)
        assertEquals(40f, (bounds.bottom - bounds.top).value, 0.5f)

        val px = compose.onRoot().captureToImage().toPixelMap()
        val left = (bounds.left.value * density).toInt()
        val top = (bounds.top.value * density).toInt()
        val centreY = top + (20 * density).toInt()
        val corner = px[left + 2, top + 2]
        val edgeOfCircle = px[left + 4, centreY]
        assertTrue(corner.red > 0.95f && corner.green > 0.95f, "the square's corner is outside the circle: $corner")
        assertTrue(edgeOfCircle.red > 0.9f && edgeOfCircle.green < 0.1f, "the circle is filled with the badge colour: $edgeOfCircle")
    }

    // --- split chips -------------------------------------------------------------------------------------------

    @Test
    fun theSplitChipsAreFullPillsOutsideAndSquareWhereTheyMeet() {
        val (start, end) = connectedFieldShapes()
        val size = Size(300f, 192f) // a 64dp-high chip at 3x
        val d = Density(density)
        assertEquals(96f, start.topStart.toPx(size, d), 0.5f, "outer corner is half the height: a pill")
        assertEquals(96f, end.bottomEnd.toPx(size, d), 0.5f)
        assertEquals(12f, start.topEnd.toPx(size, d), 0.5f, "4dp where the chips meet")
        assertEquals(12f, end.topStart.toPx(size, d), 0.5f)
        assertTrue(start.topStart is CornerSize)
    }

    // --- the unified selector --------------------------------------------------------------------------------

    @Test
    fun theSelectorIsOnePillWithTheChosenChoiceFilledAndTheOthersTransparent() {
        var trackColor = Color.Unspecified
        themed {
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            val options = listOf("Expense", "Income", "Transfer").map { ChoiceOption(it, it, Icons.Outlined.Edit) }
            Box(Modifier.testTag("row").width(360.dp)) {
                ConnectedChoiceRow(options, selected = "Expense", onSelect = {}, selectedColors = accent)
            }
        }
        compose.onNode(hasText("Expense") and radio).assertIsSelected()
        assertEquals(false, compose.onNode(hasText("Income") and radio).fetchSemanticsNode().config[SemanticsProperties.Selected])

        val px = compose.onRoot().captureToImage().toPixelMap()
        val chosen = compose.onNode(hasText("Expense") and radio).getUnclippedBoundsInRoot()
        val other = compose.onNode(hasText("Income") and radio).getUnclippedBoundsInRoot()
        // Just inside the top of each segment's pill, above its label.
        fun at(b: androidx.compose.ui.unit.DpRect) = px[((b.left.value + b.right.value) / 2 * density).toInt(), (b.top.value * density).toInt() + 6]
        val chosenPx = at(chosen)
        val otherPx = at(other)
        assertEquals(accent.container.red, chosenPx.red, 0.02f, "the chosen choice is filled in the given container colour")
        assertEquals(accent.container.blue, chosenPx.blue, 0.02f)
        assertEquals(trackColor.red, otherPx.red, 0.02f, "the others are transparent: the track shows")
        assertEquals(trackColor.blue, otherPx.blue, 0.02f)
    }

    // --- the amount ------------------------------------------------------------------------------------------

    /** The text as the amount field lays it out (what is drawn, with the symbol and grouping), to check it against the field. */
    private fun amountLayout(): androidx.compose.ui.text.TextLayoutResult {
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        val node = compose.onNodeWithContentDescription("Amount in INR").fetchSemanticsNode()
        node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }

    @Test
    fun theAmountIsCentredAndATapAnywhereInItStartsTyping() {
        themed {
            HeroAmountField(value = "", onValueChange = {}, currency = inr, accent = accent, modifier = Modifier.testTag("hero"))
        }
        val hero = compose.onNodeWithTag("hero").getUnclippedBoundsInRoot()
        val field = compose.onNodeWithContentDescription("Amount in INR").getUnclippedBoundsInRoot()
        val heroCentre = (hero.left.value + hero.right.value) / 2
        val fieldCentre = (field.left.value + field.right.value) / 2
        // The symbol is part of the same centred line as the figures, so the field is centred in the card.
        assertEquals(heroCentre, fieldCentre, 1f, "the amount is centred: hero $heroCentre field $fieldCentre")

        // Tapping the label (not the text field itself) focuses the field.
        compose.onNodeWithText("Amount").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Amount in INR").assertIsFocused()
    }

    @Test
    fun theAmountHasNoUnderlineBar() {
        themed {
            Box(Modifier.background(Color.Black)) {
                HeroAmountField(value = "", onValueChange = {}, currency = inr, accent = accent, modifier = Modifier.testTag("hero"))
            }
        }
        val hero = compose.onNodeWithTag("hero").getUnclippedBoundsInRoot()
        val px = compose.onRoot().captureToImage().toPixelMap()
        // The old design drew a 3dp accent bar under the figures, full width. Look along the bottom of the field for
        // anything of the accent's colour.
        val y = (hero.bottom.value * density).toInt() - 4
        val bar = (0 until px.width step 8).count { x ->
            val p = px[x, y]
            p.red > 0.5f && p.green < 0.6f && p.blue < 0.4f
        }
        assertEquals(0, bar, "no accent-coloured bar along the bottom of the amount")
    }

    @Test
    fun largeAmountFitsWithinCardWithoutOverlappingSymbolOrClipping() {
        themed {
            HeroAmountField(value = "3749494979", onValueChange = {}, currency = inr, accent = accent, modifier = Modifier.testTag("hero"))
        }
        val hero = compose.onNodeWithTag("hero").getUnclippedBoundsInRoot()
        val field = compose.onNodeWithContentDescription("Amount in INR").getUnclippedBoundsInRoot()
        assertTrue(field.left >= hero.left, "field does not overflow left: field.left ${field.left} >= hero.left ${hero.left}")
        assertTrue(field.right <= hero.right, "field does not overflow right: field.right ${field.right} <= hero.right ${hero.right}")
    }

    @Test
    fun theLongestAmountIsShownWholeSoThereIsNothingToScrollTo() {
        // The widest thing that can be typed - 12 digits and the decimals - in a narrow sheet. The line must lie wholly inside
        // the field: a single-line field with any part of its text outside it can be dragged sideways to find the rest.
        themed {
            Box(Modifier.width(300.dp)) {
                HeroAmountField(value = "999999999999.99", onValueChange = {}, currency = inr, accent = accent)
            }
        }
        val fieldWidthPx = compose.onNodeWithContentDescription("Amount in INR").fetchSemanticsNode().size.width
        val layout = amountLayout()
        assertEquals("₹ 9,99,99,99,99,999.99", layout.layoutInput.text.text, "grouped the Indian way as it is typed")
        assertTrue(layout.size.width <= fieldWidthPx, "the laid-out text (${layout.size.width}px) is no wider than its field (${fieldWidthPx}px)")
        assertTrue(layout.getLineLeft(0) >= 0f, "nothing off the left edge: ${layout.getLineLeft(0)}")
        assertTrue(layout.getLineRight(0) <= fieldWidthPx, "nothing off the right edge: ${layout.getLineRight(0)} of $fieldWidthPx")
        assertEquals(1, layout.lineCount, "stays on one line")
    }

    @Test
    fun aShortAmountKeepsTheFullSizeAndALongOneShrinksToFit() {
        var typed by androidx.compose.runtime.mutableStateOf("250")
        themed {
            Box(Modifier.width(300.dp)) {
                HeroAmountField(value = typed, onValueChange = {}, currency = inr, accent = accent)
            }
        }
        val short = amountLayout().layoutInput.style.fontSize.value

        typed = "123456789012.50"
        compose.waitForIdle()
        val long = amountLayout().layoutInput.style.fontSize.value
        assertTrue(long < short, "a 12-digit amount is drawn smaller ($long) than a 3-digit one ($short)")
        assertTrue(long > short * 0.3f, "but never unreadably small: $long of $short")
    }

    @Test
    fun theTypedDigitsAreGroupedButWhatIsEditedStaysPlain() {
        var typed = "1250000"
        themed {
            HeroAmountField(value = typed, onValueChange = { typed = it }, currency = inr, accent = accent)
        }
        assertEquals("₹ 12,50,000", amountLayout().layoutInput.text.text)
        assertEquals("1250000", typed, "the value itself carries no separators")
    }

    @Test
    fun anInvalidAmountSaysSo() {
        themed {
            HeroAmountField(value = "", onValueChange = {}, currency = inr, accent = accent, errorText = "Enter an amount greater than zero")
        }
        compose.onNodeWithText("Enter an amount greater than zero").assertExists()
    }

    // --- the save button -------------------------------------------------------------------------------------

    @Test
    fun saveIsAFullWidth56dpButton() {
        themed {
            Box(Modifier.testTag("sheet")) {
                EditorSheet(title = "New expense", saveLabel = "Save expense", onSave = {}) { Text("body") }
            }
        }
        val button = compose.onNode(hasText("Save expense") and hasClickAction()).getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertEquals(56f, (button.bottom - button.top).value, 0.5f)
        // The sheet's 24dp gutters either side.
        assertEquals((root.right - root.left).value - 48f, (button.right - button.left).value, 1f)
    }
}
