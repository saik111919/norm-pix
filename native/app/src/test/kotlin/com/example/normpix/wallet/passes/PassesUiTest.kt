package com.example.normpix.wallet.passes

import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Loyalty and gift cards on screen (2026-11-19): the form takes a typed number, guesses its code and draws it, and refuses a number its
 * code can't hold; a scanned picture fills the number and the code; a pass at the counter shows its code and number, and a gift card its
 * balance and - only on Show - its PIN; the Wallet's tiles say each card in one sentence, never the whole number.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class PassesUiTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
    private var result: StoredPass? = null
    private var action: PassAction? = null

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("passes_ui_test")
    }

    @After
    fun tearDown() = env.close()

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun form(existing: StoredPass? = null, reader: PassCodeReader? = null) {
        compose.setContent { env.Content { PassFormSheet(existing, reader, photoTarget = null, onDone = { result = it }) } }
        compose.waitForIdle()
    }

    private fun described(vararg parts: String) = compose.onAllNodes(
        SemanticsMatcher("described as ${parts.joinToString()}") { node ->
            val words = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
            parts.all { it in words }
        },
    ).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a typed number is drawn as the code it most likely is, and saved with it`() {
        form()
        field("Name").performTextInput("DMart")
        field("Number").performTextInput("5901234123457")
        compose.waitForIdle()
        compose.onNodeWithText("EAN-13").assertExists()
        compose.onNodeWithTag(PASS_CODE_TAG).assertExists()
        compose.onNodeWithText("Save card").performClick()
        compose.waitForIdle()
        val saved = assertNotNull(result)
        assertEquals("DMart", saved.name)
        assertEquals(PassCodeFormat.Ean13, saved.format)
        assertEquals(PassKind.Loyalty, saved.kind)
    }

    @Test
    fun `a number its code can't hold is refused, saying why`() {
        val existing = StoredPass(kind = PassKind.Loyalty, name = "Store", number = "5901234123457", format = PassCodeFormat.Ean13, createdAt = now, updatedAt = now)
        form(existing)
        // An EAN-13 with a letter in it.
        field("5901234123457").performTextInput("A")
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasTestTag(PASS_CODE_TAG)).fetchSemanticsNodes().size, "no code drawn")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitForIdle()
        assertNull(result)
        assertTrue(compose.onAllNodes(hasText("can't be drawn as EAN-13", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `with a reader the form offers a screenshot to read the code from, and without one it doesn't`() {
        form(reader = PassCodeReader { ScannedPassCode("MEMBER-77", PassCodeFormat.Qr) })
        compose.onNodeWithText("From a screenshot").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("Scan the card")).fetchSemanticsNodes().size, "no camera here: no Scan")
    }

    @Test
    fun `without a reader the number is typed`() {
        form(reader = null)
        assertEquals(0, compose.onAllNodes(hasText("From a screenshot")).fetchSemanticsNodes().size)
    }

    @Test
    fun `a gift card shows its code, number and balance, and its PIN only on Show`() {
        val gift = StoredPass(
            kind = PassKind.GiftCard, name = "Gift voucher", number = "6061220499183307", format = PassCodeFormat.Qr, pin = "482913",
            balance = 75_000, expiry = LocalDate.of(2027, 7, 1), createdAt = now, updatedAt = now,
        )
        compose.setContent { env.Content { PassSheet(gift, onAction = { action = it }) } }
        compose.waitForIdle()
        compose.onNodeWithText("Gift voucher").assertIsDisplayed()
        compose.onNodeWithTag(PASS_CODE_TAG).assertIsDisplayed()
        compose.onNodeWithText("6061 2204 9918 3307").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("Balance ₹750", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, compose.onAllNodes(hasText("482913", substring = true)).fetchSemanticsNodes().size, "the PIN is hidden")
        compose.onNodeWithText("Show").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("PIN 482913").assertIsDisplayed()
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        assertEquals(PassAction.Edit, action)
    }

    @Test
    fun `the Wallet's tiles say each card in one sentence, with the end of its number only`() {
        val passes = listOf(
            StoredPass(kind = PassKind.Loyalty, name = "Big Basket Star", number = "BB4417 2093 5521", format = PassCodeFormat.Code128, createdAt = now, updatedAt = now),
            StoredPass(kind = PassKind.Membership, name = "Cult.fit", number = "CF77", format = PassCodeFormat.Qr, createdAt = now, updatedAt = now),
        )
        var opened: StoredPass? = null
        compose.setContent { env.Content { PassesGrid(passes, onOpen = { opened = it }) } }
        compose.waitForIdle()
        assertTrue(described("Big Basket Star, Loyalty card, ending 5521"))
        assertTrue(described("Cult.fit, Membership, ending CF77"))
        assertEquals(0, compose.onAllNodes(hasText("BB4417", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size, "never the whole number")
        compose.onNode(SemanticsMatcher("Cult.fit tile") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.startsWith("Cult.fit") } == true })
            .performClick()
        assertEquals("Cult.fit", opened?.name)
    }
}
