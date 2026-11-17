package com.example.normpix.finance.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.Tag
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Add again" and "Frequent" (2026-11-18): a transaction's details offer Add again (not for a split payment), which opens a new
 * transaction filled in as a copy, dated now, and saves as a new one; a new expense shows what is entered again and again, and a tap
 * fills the category, payee, account and - while none is typed - the amount; an edit, a copy and a transfer show no picks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class AddAgainAndFrequentTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now().withSecond(0).withNano(0)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("add_again_frequent_test")
    }

    @After
    fun tearDown() = env.close()

    private val finance get() = env.container.finance

    private fun categoryId(name: String): Long = finance.state.value.categories.first { it.name == name }.id!!

    private fun cashId(): Long = runBlocking { finance.repository.accounts().first { it.kind == AccountKind.Cash }.id!! }

    private fun expense(amount: Long, payee: String?, category: String = "Health", note: String? = null, tags: Set<Long> = emptySet(), daysAgo: Long = 1) =
        runBlocking {
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense, amount = amount, accountId = cashId(), categoryId = categoryId(category),
                    occurredAt = now.minusDays(daysAgo), payee = payee, note = note, createdAt = now, updatedAt = now, tagIds = tags,
                ),
            )
        }

    private fun all(): List<FinanceTransaction> = runBlocking { finance.repository.transactionsBetween(now.minusYears(1), now.plusDays(1)) }

    private fun amountField() = compose.onNodeWithContentDescription("Amount in INR")

    // The form is drawn here without a sheet around it: saved, it says so through onDone.
    private var closedWith: Boolean? = null

    private fun waitSaved() = compose.waitUntil(5_000) {
        compose.waitForIdle()
        closedWith == true
    }

    private fun count(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    // ============================================================================================ Add again

    @Test
    fun `a transaction's details offer Add again, and a split payment's don't`() {
        val t = expense(45_000, "Apollo")
        var action: DetailAction? = null
        compose.setContent { env.Content { TransactionDetailsSheet(t, onAction = { action = it }) } }
        compose.waitForIdle()
        compose.onNodeWithText("Add again").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Share receipt").assertIsDisplayed()
        compose.onNodeWithText("Add again").performClick()
        assertEquals(DetailAction.AddAgain, action)
    }

    @Test
    fun `Add again opens a new transaction filled in as a copy, dated now, and saves it as a new one`() {
        val tag = runBlocking { finance.saveTag(Tag(name = "Reimbursable", createdAt = now)) }
        val original = expense(45_000, "Apollo", note = "Medicines", tags = setOf(tag.id!!), daysAgo = 10)
        compose.setContent { env.Content { TransactionEditorSheet(TransactionType.Expense, existingId = null, onDone = { closedWith = it }, copyOf = original) } }
        compose.waitForIdle()

        compose.onNodeWithText("New expense").assertIsDisplayed()
        amountField().assert(hasText("450"))
        compose.onNodeWithText("Apollo").assertExists()
        compose.onNodeWithText("Medicines").assertExists()
        compose.onNodeWithText("Health").assertExists()
        assertEquals(0, count("Frequent"), "a copy has its picks already")

        compose.onNodeWithText("Save expense").performClick()
        waitSaved()

        val saved = all().filter { it.id != original.id }
        assertEquals(1, saved.size, "a new transaction; the original is untouched")
        val copy = saved.single()
        assertNotEquals(original.id, copy.id)
        assertEquals(45_000L, copy.amount)
        assertEquals("Apollo", copy.payee)
        assertEquals("Medicines", copy.note)
        assertEquals(original.categoryId, copy.categoryId)
        assertEquals(original.accountId, copy.accountId)
        assertEquals(setOf(tag.id), runBlocking { finance.repository.transactionById(copy.id!!) }!!.tagIds)
        assertEquals(LocalDate.now(), copy.occurredAt.toLocalDate(), "dated now, not when the original was")
        assertEquals(now.minusDays(10), runBlocking { finance.repository.transactionById(original.id!!) }!!.occurredAt)
    }

    // ============================================================================================ Frequent

    @Test
    fun `a new expense shows what is entered again and again, and a tap fills it in`() {
        expense(30_000, "Apollo", daysAgo = 9)
        expense(45_000, "apollo ", daysAgo = 2)
        expense(9_900, "Once only", category = "Shopping")
        compose.setContent { env.Content { TransactionEditorSheet(TransactionType.Expense, existingId = null, onDone = { closedWith = it }) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Frequent")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag(FREQUENT_ROW_TAG).assertIsDisplayed()
        // One chip, said as one: the payee as last written, the amount it had then, the category.
        val chip = compose.onNodeWithContentDescription("apollo, ₹450, Health")
        chip.assertIsDisplayed()
        assertEquals(0, count("Once only"), "once is not frequent")

        chip.performClick()
        compose.waitForIdle()
        amountField().assert(hasText("450"))
        compose.onNodeWithText("apollo").assertExists()

        compose.onNodeWithText("Save expense").performClick()
        waitSaved()
        val saved = all().maxBy { it.id!! }
        assertEquals(45_000L, saved.amount)
        assertEquals("apollo", saved.payee)
        assertEquals(categoryId("Health"), saved.categoryId)
        assertEquals(cashId(), saved.accountId)
    }

    @Test
    fun `an amount already typed is kept when a pick is tapped`() {
        expense(30_000, "Apollo")
        expense(45_000, "Apollo")
        compose.setContent { env.Content { TransactionEditorSheet(TransactionType.Expense, existingId = null, onDone = {}) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Frequent")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(amountKeyTag("digit_9")).performClick()
        compose.onNodeWithTag(amountKeyTag("digit_9")).performClick()
        compose.onNodeWithContentDescription("Apollo, ₹450, Health").performClick()
        compose.waitForIdle()
        amountField().assert(hasText("99"))
        compose.onNodeWithText("Apollo").assertExists()
    }

    @Test
    fun `an edit shows no picks`() {
        expense(30_000, "Apollo")
        val t = expense(45_000, "Apollo")
        compose.setContent { env.Content { TransactionEditorSheet(TransactionType.Expense, existingId = t.id, onDone = {}) } }
        compose.waitUntil(5_000) { count("Edit expense") > 0 }
        compose.waitForIdle()
        assertEquals(0, count("Frequent"))
    }

    @Test
    fun `a transfer shows no picks`() {
        expense(30_000, "Apollo")
        expense(45_000, "Apollo")
        compose.setContent { env.Content { TransactionEditorSheet(TransactionType.Transfer, existingId = null, onDone = {}) } }
        compose.waitForIdle()
        assertTrue(count("New transfer") > 0)
        assertEquals(0, count("Frequent"))
    }
}
