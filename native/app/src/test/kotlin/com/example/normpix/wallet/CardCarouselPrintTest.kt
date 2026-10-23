package com.example.normpix.wallet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.example.normpix.settings.BankIconStyle
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
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
 * The print on the carousel's card, measured with real text (native graphics: the legacy text the other layout tests run on is
 * shorter than a phone's, and would not show the print not fitting). The deck's card is narrower than a card of the list, and
 * its print once needed more height than the card had: the chip sat against the logo and the cardholder row ran down under the
 * panel's edge, cut off.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CardCarouselPrintTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    private fun card(id: String, bank: String, number: String, kind: CardKind) = StoredCard(
        id = id, bankName = bank, cardholderName = "Ananya Rao", cardNumber = number, expiryMonth = "11", expiryYear = "29",
        cvv = "123", kind = kind, createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
    )

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("carousel_print")
        val cards = listOf(card("a", "HDFC Bank", "4111111111114242", CardKind.Debit), card("b", "ICICI Bank", "5555555555554444", CardKind.Credit))
        compose.setContent {
            env.Content {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.fillMaxSize().padding(top = 24.dp)) {
                        CardCarousel(
                            cards = cards, revealed = emptyList(), nfcGated = false, bankIconStyle = BankIconStyle.Full, tilt = null,
                            onToggleReveal = {}, onShareNfc = {}, onEdit = {}, onDelete = {}, onCopy = { _, _ -> },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @After
    fun tearDown() = env.close()

    /** A node's box in the window's own dp: the print is drawn in a scaled density, which the node's own bounds would be read in. */
    private fun SemanticsNodeInteraction.realBounds(): DpRect {
        val box = fetchSemanticsNode().boundsInRoot
        return with(compose.density) { DpRect(box.left.toDp(), box.top.toDp(), box.right.toDp(), box.bottom.toDp()) }
    }

    /**
     * Measured on a 272dp card (every phone's, from 360dp up): before the print was drawn to fit, the last line of print was 3.1dp
     * above the panel's edge, and the badge row to the number 55.5dp (the chip had no room above it); now 9.3dp and 71dp. The
     * bars are between the two.
     */
    @Test
    fun theLastLineOfPrintIsClearOfThePanel_andTheChipHasRoomAboveTheNumber() {
        val card = compose.onNodeWithContentDescription("HDFC Bank debit card, ending in", substring = true).realBounds()
        // Text on the card is inside the card's own (cleared) semantics: the unmerged tree has it.
        val holder = compose.onAllNodesWithText("ANANYA RAO", useUnmergedTree = true).onFirst().realBounds()
        val badge = compose.onAllNodesWithText("DEBIT", useUnmergedTree = true).onFirst().realBounds()
        val last4 = compose.onAllNodesWithText("4242", useUnmergedTree = true).onFirst().realBounds()
        val panelEdge = card.bottom - 10.dp // the panel stands over the card's last 10dp
        assertTrue(panelEdge - holder.bottom >= 6.dp, "the last line of print is clear of the panel: ${panelEdge - holder.bottom}")
        assertTrue(last4.top - badge.bottom >= 63.dp, "room for the chip between the badge row and the number: ${last4.top - badge.bottom}")
    }

    /**
     * The panel's bar is narrow: four icon buttons, 48dp each by their touch targets, leave Reveal about 92dp. A tonal button with its
     * usual padding left the word no room - it was drawn as "." - and the text's own semantics still said "Reveal", so only the
     * layout of the text can say whether it is all there.
     */
    @Test
    fun theRevealButtonShowsItsWholeLabel() {
        val label = compose.onNode(hasText("Reveal"), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        label.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        // (Not hasVisualOverflow: it also says true for text that fits, when the room it was given is wider than the text.)
        assertFalse(layout.isLineEllipsized(0), "\"Reveal\" is ellipsized: ${layout.getLineEnd(0, visibleEnd = true)} of 6 letters show in ${layout.layoutInput.constraints}")
        assertTrue(layout.getLineEnd(0, visibleEnd = true) == "Reveal".length, "all of \"Reveal\" shows")
        // Room to spare, not just enough: a wider shape of the interface font (Settings > Appearance) is a few dp wider.
        val room = layout.layoutInput.constraints.maxWidth - layout.size.width
        assertTrue(room >= with(compose.density) { 4.dp.toPx() }, "slack beside the word: $room px")
    }
}
