package com.example.normpix.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.CardsLayout
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
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
 * The Wallet's Stack layout, filed like folders (owner, 2026-11-02, with a reference): every card behind the front one shows its
 * strip, the front one is printed in full, tapping a strip brings that card to the front and tapping the front card opens it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class CardFolderStackTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment("card_folder_stack")
    }

    @After
    fun tearDown() = env.close()

    private fun card(id: String, bank: String, number: String, kind: CardKind = CardKind.Credit) =
        StoredCard(id, bank, "ANANYA RAO", number, "08", "29", "", kind, LocalDateTime.of(2026, 1, 1, 9, 0))

    // Published test numbers only.
    private val cards = listOf(
        card("a", "HDFC Bank", "4012888888881881"),
        card("b", "ICICI Bank", "5555555555554444"),
        card("c", "State Bank of India", "4000056655665556", CardKind.Debit),
        card("d", "Axis Bank", "4111111111111111"),
    )

    private val opened = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            env.Content {
                Box(Modifier.testTag("page").background(MaterialTheme.colorScheme.surface).padding(16.dp).width(379.dp)) {
                    CardFolderStack(cards, onOpen = { opened += it.id })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun node(bank: String, last4: String) = compose.onNodeWithContentDescription("$bank ", substring = true, useUnmergedTree = false).let {
        compose.onNodeWithContentDescription("ending in $last4", substring = true)
    }

    private fun top(last4: String) = node("", last4).getUnclippedBoundsInRoot().top


    @Test
    fun `the last card is in front and the others are filed behind it, a strip apart`() {
        show()
        // Behind ones in their order, a strip apart; the front one (the last) at the foot.
        val tops = listOf("1881", "4444", "5556", "1111").map { top(it).value }
        for (i in 1 until tops.size) assertEquals(FolderStep.value, tops[i] - tops[i - 1], 0.5f, "a strip apart")
        compose.onNodeWithContentDescription("ending in 1111, expires 08/29, in front", substring = true).assertExists()
        compose.onNodeWithContentDescription("ending in 1881, expires 08/29, card 1 of 4, behind", substring = true).assertExists()
    }

    @Test
    fun `tapping a card behind brings it to the front, and tapping the front card opens it`() {
        show()
        node("", "4444").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("ending in 4444, expires 08/29, in front", substring = true).assertExists()
        assertTrue(top("4444") > top("1111"), "the tapped card is now at the foot")
        assertTrue(opened.isEmpty(), "bringing a card forward opens nothing")
        node("", "4444").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("b"), opened)
    }

    @Test
    fun `swiping the front card brings the next one forward`() {
        show()
        val frontNode = compose.onNodeWithContentDescription("ending in 1111, expires 08/29, in front", substring = true)
        frontNode.assertExists()
        frontNode.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("ending in 1881, expires 08/29, in front", substring = true).assertExists()
    }
}
