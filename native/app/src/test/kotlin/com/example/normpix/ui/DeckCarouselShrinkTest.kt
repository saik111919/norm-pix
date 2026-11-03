package com.example.normpix.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.normpix.ui.components.DeckCarousel
import com.example.normpix.ui.theme.NormPixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A deck whose list gets shorter while it is on screen (a card deleted, a bank filtered, the accounts read again after a change)
 * must keep going. Its pager asks for the key of a page by its place in the list, and for a moment that place can be past the
 * end of the shorter list: on a phone this closed the app with "Index 1 out of bounds for length 1".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DeckCarouselShrinkTest {
    @get:Rule
    val compose = createComposeRule()

    private var items by mutableStateOf(listOf("alpha", "beta", "gamma"))

    private fun show() {
        compose.setContent {
            NormPixTheme {
                DeckCarousel(
                    items = items,
                    itemKey = { it },
                    tint = { null },
                    sideDescription = { item, index, count -> "side $item ${index + 1} of $count" },
                    card = { item, _, _ -> Text("card $item") },
                    details = { item -> Text("details $item") },
                    footer = { front -> Text("footer $front") },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theDeckKeepsGoingWhenItsListShrinksToOne() {
        show()
        items = listOf("alpha")
        compose.waitForIdle()
        compose.onNodeWithText("details alpha").assertIsDisplayed()
    }

    @Test
    fun theDeckKeepsGoingWhenItsListShrinksBySome() {
        show()
        items = listOf("alpha", "beta")
        compose.waitForIdle()
        compose.onNodeWithText("details alpha").assertIsDisplayed()
        items = listOf("beta")
        compose.waitForIdle()
        compose.onNodeWithText("details beta").assertIsDisplayed()
    }

    @Test
    fun theDeckKeepsGoingWhenTheFrontCardGoesAndThenTheListGrowsAgain() {
        show()
        items = listOf("beta", "gamma")
        compose.waitForIdle()
        items = listOf("gamma")
        compose.waitForIdle()
        items = listOf("gamma", "delta", "epsilon")
        compose.waitForIdle()
        compose.onNodeWithText("details gamma").assertIsDisplayed()
    }
}
