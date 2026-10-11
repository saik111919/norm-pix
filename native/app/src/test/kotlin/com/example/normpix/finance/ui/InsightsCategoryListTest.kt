package com.example.normpix.finance.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where your money went is one list section: the answer as its intro, one row per category, a show-all row past five,
 * and the hint as its footer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
@OptIn(ExperimentalTestApi::class)
class InsightsCategoryListTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("insights_category_list")
    }

    @After
    fun tearDown() = env.close()

    /**
     * Seven of the seeded expense categories, one expense each this month, from Rs 7,000 down to Rs 1,000 - so the order
     * is known and the biggest is Food & dining at 25%.
     */
    private fun seedSevenCategories() = runBlocking {
        val finance = env.container.finance
        val account = finance.saveAccount(Account(name = "Main", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        val names = listOf(
            "Food & dining",
            "Groceries",
            "Transport",
            "Shopping",
            "Bills & utilities",
            "Rent & housing",
            "Entertainment",
        )
        names.forEachIndexed { index, name ->
            val category = finance.state.value.categories.first { it.name == name && it.type == CategoryType.Expense }
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = (7_000L - index * 1_000L) * 100,
                    accountId = account.id!!,
                    categoryId = category.id,
                    occurredAt = LocalDate.now().atStartOfDay().plusMinutes(index + 1L),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun show() {
        compose.setContent { env.Content { InsightsScreen() } }
        compose.waitForIdle()
        // The figures come from a query on the database thread, which the test clock does not wait for.
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Where your money went").fetchSemanticsNodes().isNotEmpty() }
    }

    /** The category rows: each one's description ends with the hint that a tap opens its transactions. */
    private fun categoryRows() = compose.onAllNodes(
        SemanticsMatcher("a category row") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.endsWith(". Show transactions") } == true
        },
    )

    @Test
    fun theCategoriesAreRowsOfOneListSection_withTheAnswerAboveAndTheHintBelow() {
        seedSevenCategories()
        show()
        compose.waitForIdle()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Where your money went"))
        compose.onNodeWithText("Where your money went").assertIsDisplayed()
        compose.onNodeWithText("Food & dining is your biggest spend: 25% of ${monthLabel(MonthKey.of(now))}.").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Tap a category to see its transactions."))
        compose.onNodeWithText("Tap a category to see its transactions.").assertIsDisplayed()
        categoryRows().assertCountEquals(5)
    }

    @Test
    fun showAllOpensTheRest_andShowFewerFoldsThemAgain() {
        seedSevenCategories()
        show()
        compose.waitForIdle()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Show all 7 categories"))
        compose.onNodeWithText("Show all 7 categories").assertIsDisplayed().assertHasClickAction()

        compose.onNodeWithText("Show all 7 categories").performClick()
        compose.waitForIdle()
        categoryRows().assertCountEquals(7)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Show fewer"))
        compose.onNodeWithText("Show fewer").assertIsDisplayed().assertHasClickAction()
        compose.onAllNodesWithText("Show all 7 categories").assertCountEquals(0)

        compose.onNodeWithText("Show fewer").performClick()
        compose.waitForIdle()
        categoryRows().assertCountEquals(5)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Show all 7 categories"))
        compose.onNodeWithText("Show all 7 categories").assertIsDisplayed().assertHasClickAction()
    }
}
