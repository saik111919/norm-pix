package com.example.normpix.finance.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.families
import com.example.normpix.testing.TestEnvironment
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The category picker's families are closed until opened: each shows what is inside, a chevron opens and closes it, "Expand all"
 * opens every one, the family holding a picked sub-category starts open (and says so when closed), and tapping a family's row
 * still picks the category itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CategoryPickerCollapseTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("category_picker_collapse")
    }

    @After
    fun tearDown() = env.close()

    private fun expenseCategories() =
        env.container.finance.state.value.categories.filter { it.type == CategoryType.Expense && !it.archived }

    private fun food() = assertNotNull(expenseCategories().families().firstOrNull { it.category.name == "Food & dining" })

    private fun show(selected: Set<Long> = emptySet(), multiple: Boolean = false, onDone: (Set<Long>?) -> Unit = {}) {
        compose.setContent {
            env.Content { CategoryPickerSheet(categories = expenseCategories(), selected = selected, multiple = multiple, onDone = onDone) }
        }
        compose.waitForIdle()
    }

    private fun open(name: String) {
        compose.onNodeWithContentDescription("Show sub-categories of $name").performClick()
        compose.waitForIdle()
    }

    private fun close(name: String) {
        compose.onNodeWithContentDescription("Hide sub-categories of $name").performClick()
        compose.waitForIdle()
    }

    @Test
    fun familiesStartClosed_eachSayingWhatIsInside() {
        show()

        compose.onNodeWithText("Food & dining").assertIsDisplayed()
        compose.onAllNodesWithText("Pizza").assertCountEquals(0)
        val subs = food().subs
        val preview = subs.take(2).joinToString(", ") { it.name } + " +${subs.size - 2}"
        compose.onNodeWithText(preview).assertIsDisplayed()
        compose.onNodeWithContentDescription("Show sub-categories of Food & dining").assertIsDisplayed()
    }

    @Test
    fun theChevronOpensAFamily_andClosesItAgain() {
        show()

        open("Food & dining")
        compose.onNodeWithText("Pizza").assertIsDisplayed()
        compose.onNodeWithText("${food().subs.size} sub-categories").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide sub-categories of Food & dining").assertIsDisplayed()

        close("Food & dining")
        compose.onAllNodesWithText("Pizza").assertCountEquals(0)
        compose.onNodeWithContentDescription("Show sub-categories of Food & dining").assertIsDisplayed()
    }

    @Test
    fun expandAllOpensEveryFamily_andCollapseAllClosesThem() {
        show()

        compose.onNodeWithText("Expand all").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pizza").assertIsDisplayed()
        compose.onNodeWithText("Collapse all").assertIsDisplayed()

        compose.onNodeWithText("Collapse all").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Pizza").assertCountEquals(0)
        compose.onNodeWithText("Expand all").assertIsDisplayed()
    }

    @Test
    fun theFamilyHoldingAPickedSubCategoryStartsOpen_andSaysWhichWhenClosed() {
        val pizza = assertNotNull(expenseCategories().firstOrNull { it.name == "Pizza" })
        show(selected = setOf(assertNotNull(pizza.id)))

        // Open, so the choice is seen where it sits.
        compose.onNodeWithText("Pizza").assertIsDisplayed()

        // Closed again, the row still says what is picked inside.
        close("Food & dining")
        compose.onAllNodesWithText("Pizza").assertCountEquals(0)
        compose.onNodeWithText("Pizza selected").assertIsDisplayed()
    }

    @Test
    fun tappingAFamilysRowStillPicksTheCategoryItself() {
        var result: Set<Long>? = null
        show(onDone = { result = it })

        compose.onNodeWithText("Food & dining").performClick()
        compose.waitForIdle()

        assertEquals(setOf(assertNotNull(food().category.id)), result)
    }

    @Test
    fun aSubCategoryPickedFromAnOpenedFamilyIsPicked() {
        var result: Set<Long>? = null
        show(onDone = { result = it })

        open("Food & dining")
        compose.onNodeWithText("Pizza").performClick()
        compose.waitForIdle()

        val pizza = assertNotNull(expenseCategories().firstOrNull { it.name == "Pizza" })
        assertEquals(setOf(assertNotNull(pizza.id)), result)
    }

    @Test
    fun withSeveralChoicesAllowedTheFamiliesOpenAndCloseTheSameWay() {
        show(multiple = true)

        compose.onAllNodesWithText("Pizza").assertCountEquals(0)
        open("Food & dining")
        compose.onNodeWithText("Pizza").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Apply (1)").assertIsDisplayed()

        // Closed, the family still says one of its own is picked.
        close("Food & dining")
        compose.onNodeWithText("Pizza selected").assertIsDisplayed()
    }

    @Test
    fun searchFindsASubCategoryWithoutOpeningAnything() {
        show()

        compose.onNodeWithText("Search categories").performTextInput("food pizza")
        compose.waitForIdle()

        compose.onNodeWithText("Food & dining › Pizza").assertIsDisplayed()
        compose.onAllNodesWithText("Expand all").assertCountEquals(0)
    }
}
