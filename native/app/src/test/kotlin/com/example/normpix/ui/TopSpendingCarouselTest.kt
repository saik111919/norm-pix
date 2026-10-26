package com.example.normpix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.ui.CategorySpend
import com.example.normpix.screens.TopSpendingStack
import com.example.normpix.settings.TopSpendingLayout
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings > Appearance > Layouts > Top spending: Home's biggest categories as a carousel, its panel saying what each came to. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class TopSpendingCarouselTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val month = MonthKey.of(LocalDateTime.now())

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("top_spending_carousel_test")
    }

    @After
    fun tearDown() = env.close()

    private fun spends(): List<CategorySpend> = runBlocking {
        env.container.finance.load()
        val expense = env.container.finance.state.value.categories.filter { it.type == CategoryType.Expense && it.parentId == null }
        listOf(
            CategorySpend(expense[0].id!!, expense[0], month, amount = 2_002_00, count = 2, share = 0.65, previous = 1_000_00, budget = null),
            CategorySpend(expense[1].id!!, expense[1], month, amount = 1_078_00, count = 1, share = 0.35, previous = 0, budget = null),
        )
    }

    @Test
    fun `the carousel is the stack's other look, and its transactions button opens the category's month`() {
        val spends = spends()
        env.preferences.setTopSpendingLayout(TopSpendingLayout.Carousel)
        compose.setContent { env.Content { TopSpendingStack(spends, onSeeAll = {}) } }
        compose.waitForIdle()

        assertTrue(compose.onAllNodesWithText("2 transactions").fetchSemanticsNodes().isNotEmpty(), "the panel's button, with the count")
        assertTrue(compose.onAllNodesWithText("100% more", substring = true).fetchSemanticsNodes().isNotEmpty(), "against last month")
        // Short labels, kept whole: "Vs this time last month", not "Compared with this time / last month".
        assertTrue(compose.onAllNodesWithText("VS THIS TIME LAST MONTH").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("OF SPENDING").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("2 transactions").performClick()
        compose.waitForIdle()
        assertEquals(listOf(spends[0].categoryId to month), env.navigator.categoryHistories)
    }

    private fun lines(text: String): Int {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().lineCount
    }

    @Test
    fun `the panel's labels are whole, each on one line, and its two facts share a row`() {
        val spends = spends()
        env.preferences.setTopSpendingLayout(TopSpendingLayout.Carousel)
        // As wide as it is on Home, inside the page's margins.
        compose.setContent { env.Content { Column(Modifier.padding(horizontal = 16.dp)) { TopSpendingStack(spends, onSeeAll = {}) } } }
        compose.waitForIdle()

        for (label in listOf("VS THIS TIME LAST MONTH", "OF SPENDING", "BUDGET LEFT")) assertEquals(1, lines(label), "$label on one line")
        val share = compose.onNodeWithText("OF SPENDING").getUnclippedBoundsInRoot()
        val budget = compose.onNodeWithText("BUDGET LEFT").getUnclippedBoundsInRoot()
        assertEquals(share.top.value, budget.top.value, 1f, "one row")
        assertTrue(budget.left > share.left)
    }

    @Test
    fun `the stack stays the default`() {
        val spends = spends()
        compose.setContent { env.Content { TopSpendingStack(spends, onSeeAll = {}) } }
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("2 transactions").fetchSemanticsNodes().isEmpty(), "no carousel panel")
        assertEquals(TopSpendingLayout.Stack, env.preferences.state.value.topSpendingLayout)
    }
}
