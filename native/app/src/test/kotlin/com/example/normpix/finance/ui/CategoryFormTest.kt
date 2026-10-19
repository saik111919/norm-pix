package com.example.normpix.finance.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.emojiIcon
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The category form and the Categories page with emoji icons and sub-categories: an emoji from the catalog or pasted from
 * anywhere becomes the icon; a sub-category is made under a parent and takes its color; a category with sub-categories of its
 * own cannot be put under another; the page shows each parent with its sub-categories under it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CategoryFormTest {
    @get:Rule
    val compose = createComposeRule()

    private val env = TestEnvironment("category_form_test")
    private val now = LocalDateTime.now().withNano(0)
    private val finance get() = env.container.finance

    private val pizza = "🍕"
    private val unicorn = "🦄"

    @After
    fun tearDown() = env.close()

    private val loaded by lazy { runBlocking { finance.load() } }

    private fun category(name: String, parent: Category? = null, color: Int = 3): Category = runBlocking {
        loaded
        finance.saveCategory(
            Category(name = name, type = CategoryType.Expense, icon = "restaurant", color = color, parentId = parent?.id, createdAt = now, updatedAt = now),
        )
    }

    private fun showForm(existing: Category? = null, parent: Category? = null, onDone: (Category?) -> Unit = {}) {
        loaded
        compose.setContent { env.Content { CategoryFormSheet(type = CategoryType.Expense, existing = existing, parent = parent, onDone = onDone) } }
        compose.waitForIdle()
    }

    /** The form's icon and color are folded until their row is tapped. */
    private fun open(part: String) {
        compose.onNodeWithText(part).performClick()
        compose.waitForIdle()
    }

    private fun state(part: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, part)

    @Test
    fun theIconAndColorAreFoldedAtFirst_eachShowingWhatIsChosen() {
        showForm()
        // Folded: the row says what is chosen, and none of the picker is there.
        compose.onNodeWithText("Icon").assert(state("Collapsed"))
        compose.onNodeWithText("Color").assert(state("Collapsed"))
        compose.onNodeWithText("Other").assertExists()
        compose.onNodeWithText("Coral").assertExists()
        compose.onAllNodesWithText("Emoji").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Blue").assertCountEquals(0)
        // The name stays as it was.
        compose.onNodeWithText("Name").assertExists()
    }

    @Test
    fun aRowOpensInPlace_andFoldsAgain() {
        showForm()
        open("Icon")
        compose.onNodeWithText("Icon").assert(state("Expanded"))
        compose.onNodeWithText("Emoji").assertExists()
        compose.onNodeWithText("Color").assert(state("Collapsed"))
        compose.onAllNodesWithContentDescription("Blue").assertCountEquals(0)

        open("Color")
        compose.onNodeWithContentDescription("Blue").assertExists()
        compose.onNodeWithText("Emoji").assertExists()

        open("Icon")
        compose.onAllNodesWithText("Emoji").assertCountEquals(0)
        compose.onNodeWithContentDescription("Blue").assertExists()
    }

    @Test
    fun aColorPickedFromTheOpenedRow_isTheOneSaved_andNamedOnTheRow() {
        var saved: Category? = null
        showForm { saved = it }
        compose.onNodeWithText("Name").performTextInput("Gym")
        open("Color")
        compose.onNodeWithContentDescription("Blue").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Blue").assertExists()
        compose.onNodeWithText("Save category").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertEquals(2, assertNotNull(saved).color)
    }

    @Test
    fun theRowsShowTheIconAndColorACategoryHas() {
        val tea = runBlocking {
            loaded
            finance.saveCategory(
                Category(name = "Tea", type = CategoryType.Expense, icon = "coffee", color = 5, createdAt = now, updatedAt = now),
            )
        }
        showForm(existing = tea)
        compose.onNodeWithText("Coffee").assertExists()
        compose.onNodeWithText("Teal").assertExists()
    }

    @Test
    fun theEmojiTab_offersTheCatalog_andPickingOneSavesItAsTheIcon() {
        var saved: Category? = null
        showForm { saved = it }
        compose.onNodeWithText("Name").performTextInput("Pizza night")
        open("Icon")
        compose.onNodeWithText("Emoji").performClick()
        compose.onNodeWithContentDescription("pizza").performClick()
        compose.onNodeWithText("Save category").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertEquals(emojiIcon(pizza), assertNotNull(saved).icon)
    }

    @Test
    fun anEmojiPastedIn_evenOneTheCatalogLacks_isChosen() {
        assertTrue(emojiCatalog.none { group -> group.emojis.any { it.glyph == unicorn } }, "this test needs an emoji the catalog does not have")
        var saved: Category? = null
        showForm { saved = it }
        compose.onNodeWithText("Name").performTextInput("Magic")
        open("Icon")
        compose.onNodeWithText("Emoji").performClick()
        compose.onNodeWithText("Search or paste an emoji").performTextInput("my $unicorn thing")
        compose.onNodeWithText("Save category").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertEquals(emojiIcon(unicorn), assertNotNull(saved).icon)
    }

    @Test
    fun searchingNarrowsTheCatalog_andSaysWhenNothingMatches() {
        showForm()
        open("Icon")
        compose.onNodeWithText("Emoji").performClick()
        compose.onNodeWithContentDescription("sushi").assertExists()

        compose.onNodeWithText("Search or paste an emoji").performTextInput("pizza")
        compose.onNodeWithContentDescription("pizza").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("sushi").assertCountEquals(0)

        compose.onNodeWithText("Search or paste an emoji").performTextReplacement("qqqqq")
        compose.onNodeWithText("No emoji matches", substring = true).assertIsDisplayed()
        compose.onAllNodesWithContentDescription("pizza").assertCountEquals(0)
    }

    @Test
    fun aSubCategoryIsMadeUnderItsParent_andTakesItsColor() {
        val food = category("Eating out", color = 3)
        var saved: Category? = null
        showForm(parent = food) { saved = it }

        compose.onNodeWithText("New sub-category").assertExists()
        compose.onNodeWithText("Expense category in Eating out").assertExists()
        // Its color is its parent's, so there is none to choose: the row says so, and opened it explains.
        compose.onNodeWithText("Same as Eating out").assertExists()
        open("Color")
        compose.onNodeWithText("Takes the color of Eating out", substring = true).assertExists()
        compose.onAllNodesWithContentDescription("Coral").assertCountEquals(0)

        compose.onNodeWithText("Name").performTextInput("Pizzeria")
        compose.onNodeWithText("Save category").performClick()
        compose.waitUntil(10_000) { saved != null }

        val result = assertNotNull(saved)
        assertEquals(food.id, result.parentId)
        assertEquals(3, result.color)
    }

    @Test
    fun aCategoryWithSubCategoriesCannotBePutUnderAnother_butASubCategoryCanBeMoved() {
        val food = category("Eating out")
        val pizzaCategory = category("Pizzeria", parent = food)
        category("Travel")

        showForm(existing = food)
        compose.onAllNodesWithText("Under").assertCountEquals(0)
    }

    @Test
    fun aSubCategoryCanChooseAnotherParent() {
        val food = category("Eating out")
        val pizzaCategory = category("Pizzeria", parent = food)
        category("Travel")

        showForm(existing = pizzaCategory)
        compose.onNodeWithText("Under").assertExists()
        compose.onNodeWithText("Eating out").assertExists()
    }

    /** A family on the Categories page is folded until it is tapped (the page opens on a long, ready-made tree). */
    /**
     * Opens [parent]'s family, and brings its [last] sub-category into view: the test's family comes after all the categories
     * a new install starts with, so it can open below the bottom of the screen.
     */
    private fun openFamily(parent: String, last: String) {
        compose.onNodeWithText(parent, useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(last))
        compose.waitForIdle()
    }

    @Test
    fun theCategoriesPage_showsEachParentWithItsSubCategoriesUnderIt() {
        val food = category("Eating out")
        category("Pizzeria", parent = food)
        category("Burgers", parent = food)
        loaded
        compose.setContent { env.Content { CategoriesPage() } }
        compose.waitForIdle()

        compose.onNodeWithText("2 sub-categories").assertExists()
        openFamily("Eating out", last = "Burgers")
        // Opened, its sub-categories sit in a card of their own just below it, in the same text column.
        val parent = compose.onNodeWithText("Eating out", useUnmergedTree = true).getBoundsInRoot()
        val pizzeria = compose.onNodeWithText("Pizzeria", useUnmergedTree = true).getBoundsInRoot()
        val burgers = compose.onNodeWithText("Burgers", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(pizzeria.top > parent.bottom, "the sub-category is under its parent: ${pizzeria.top} vs ${parent.bottom}")
        assertTrue(burgers.top > pizzeria.top, "and the next one under that")
    }

    @Test
    fun onlyAParentOffersAddSubCategory() {
        val food = category("Eating out")
        category("Pizzeria", parent = food)
        loaded
        compose.setContent { env.Content { CategoriesPage() } }
        compose.waitForIdle()

        openFamily("Eating out", last = "Pizzeria")
        // The open family has an "Add sub-category" row of its own; what is asked is that the sub-category's options add no second one.
        val onThePage = compose.onAllNodesWithText("Add sub-category").fetchSemanticsNodes().size
        compose.onNodeWithContentDescription("Options for Pizzeria").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Archive").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Add sub-category").assertCountEquals(onThePage)
    }

    @Test
    fun aParentOffersAddSubCategory_whichOpensTheFormUnderIt() {
        val food = category("Eating out")
        loaded
        compose.setContent { env.Content { CategoriesPage() } }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Options for ${food.name}").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add sub-category").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add sub-category").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("New sub-category").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Expense category in Eating out").assertExists()
    }
}
