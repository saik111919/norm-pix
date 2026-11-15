package com.example.normpix.finance.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.SplitPart
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.settings.ui.DataSettingsPage
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
 * Splitting a payment on screen (2026-11-15): the form's Split field (offered for an expense and an income, not a transfer) opening the
 * split editor; the running line - what is left to share, what is over, shared exactly - with Save only once the parts add up and each has
 * a category and an amount; the details of a part showing the whole payment with Edit split, Unsplit and Delete; Delete saying it removes
 * every part, with Undo; Unsplit asking which category; a part's row marked Split; amounts hidden; and Check and recalculate offering to
 * end a split that no longer holds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class SplitUiTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now().withSecond(0).withNano(0)
    private val at = now.minusDays(1).withHour(18).withMinute(30)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("split_ui_test")
        runBlocking { env.container.finance.load() }
    }

    @After
    fun tearDown() = env.close()

    private val finance get() = env.container.finance

    private fun cash(): Account = runBlocking { finance.repository.accounts().first { it.kind == AccountKind.Cash } }

    private fun bank(): Account = runBlocking {
        finance.repository.accounts().firstOrNull { it.name == "HDFC Savings" }
            ?: finance.saveAccount(Account(name = "HDFC Savings", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
    }

    private fun category(name: String): Long = runBlocking { finance.repository.categories().first { it.name == name }.id!! }

    private fun payment(total: Long, type: TransactionType = TransactionType.Expense) = FinanceTransaction(
        type = type, amount = total, accountId = cash().id!!, occurredAt = at, payee = "DMart", createdAt = now, updatedAt = now,
    )

    /** A saved split of 1,120 rupees: 820 in Groceries ("Rice and dal") and 300 in Shopping ("Detergent"). */
    private fun aSplit(): List<FinanceTransaction> = runBlocking {
        val saved = finance.repository.saveSplit(
            payment(112_000),
            listOf(SplitPart(category("Groceries"), 82_000, "Rice and dal"), SplitPart(category("Shopping"), 30_000, "Detergent")),
        )
        finance.load()
        saved
    }

    private fun expense(amount: Long, payee: String, categoryName: String = "Health"): FinanceTransaction = runBlocking {
        finance.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense, amount = amount, accountId = cash().id!!, categoryId = category(categoryName), occurredAt = at,
                payee = payee, createdAt = now, updatedAt = now,
            ),
        )
    }

    private fun rows(): List<FinanceTransaction> = runBlocking { finance.repository.transactions(TransactionQuery(), limit = 50).items }

    private fun count(text: String, substring: Boolean = false) = compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private fun waitFor(text: String) = compose.waitUntil(15_000) { compose.waitForIdle(); count(text) > 0 }

    private fun waitUntilGone(text: String) = compose.waitUntil(15_000) { compose.waitForIdle(); count(text) == 0 }

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun sql(statement: String) {
        val field = FinanceRepository::class.java.getDeclaredField("database").also { it.isAccessible = true }
        (field.get(finance.repository) as FinanceDatabase).writableDatabase.execSQL(statement)
    }

    private fun showEditor(type: TransactionType = TransactionType.Expense, existingId: Long? = null, onDone: (Boolean?) -> Unit = {}) {
        compose.setContent { env.Content { TransactionEditorSheet(type, existingId = existingId, onDone = onDone) } }
        compose.waitForIdle()
    }

    private fun showSplitEditor(parts: List<SplitPart>, total: Long = parts.sumOf { it.amount }, onDone: (Boolean?) -> Unit = {}) {
        compose.setContent { env.Content { SplitEditorSheet(payment(total), null, parts, onDone) } }
        compose.waitForIdle()
    }

    private fun status() = compose.onNodeWithTag(SPLIT_STATUS_TAG)

    // =================================================================================== the form's Split field

    @Test
    fun `the form offers Split for an expense and an income, and not for a transfer`() {
        showEditor(TransactionType.Expense)
        compose.onNodeWithTag(SPLIT_FIELD_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Split, Share between categories").assertIsDisplayed()

        compose.onNode(hasText("Transfer") and radio).performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag(SPLIT_FIELD_TAG).fetchSemanticsNodes().size, "a transfer has no category to share")

        compose.onNode(hasText("Income") and radio).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SPLIT_FIELD_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Split needs an amount to share first, and says so in the form`() {
        showEditor(TransactionType.Expense)
        compose.onNodeWithTag(SPLIT_FIELD_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Enter the amount to split").assertIsDisplayed()
        assertEquals(0, count("Split payment"), "no split editor opened")
    }

    @Test
    fun `Split from an existing transaction opens the split editor on it - its category and amount first, a second part empty`() {
        val t = expense(45_000, "Apollo")
        var done: Boolean? = null
        showEditor(existingId = t.id) { done = it }
        compose.onNodeWithTag(SPLIT_FIELD_TAG).performScrollTo().performClick()
        waitFor("Split payment")

        compose.onNodeWithText("Share one expense between categories").assertIsDisplayed()
        compose.onNodeWithText("Part 1").assertIsDisplayed()
        compose.onNodeWithText("Part 2").assertIsDisplayed()
        compose.onNodeWithTag(splitAmountTag(0)).assertTextContains("450")
        compose.onNodeWithText("Give every part a category and an amount").assertIsDisplayed()
        compose.onNodeWithText("Save split").assertIsNotEnabled()

        // 450 becomes 300 + 150: what is left is said, and the last part takes it with one tap.
        compose.onNodeWithTag(splitAmountTag(0)).performTextClearance()
        compose.onNodeWithTag(splitAmountTag(0)).performTextInput("300")
        compose.waitForIdle()
        compose.onNodeWithText("₹150 left to share").assertIsDisplayed()
        compose.onNodeWithTag(splitCategoryTag(1)).performScrollTo().performClick()
        waitFor("Groceries")
        compose.onNodeWithText("Groceries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Add the ₹150 left").performScrollTo().performClick()
        compose.waitForIdle()
        status().assertTextEquals("Shared exactly")
        compose.onNodeWithText("Save split").assertIsEnabled()
        compose.onNodeWithText("Save split").performClick()
        compose.waitUntil(15_000) { compose.waitForIdle(); rows().size == 2 }

        val saved = rows().sortedBy { it.id }
        assertEquals(t.id, saved[0].id, "the transaction is the first part")
        assertEquals(listOf(30_000L, 15_000L), saved.map { it.amount })
        assertEquals(listOf(category("Health"), category("Groceries")), saved.map { it.categoryId })
        assertEquals(1, saved.map { it.splitGroup }.toSet().size)
        assertTrue(saved.all { it.splitGroup != null })
        assertEquals(listOf("Apollo", "Apollo"), saved.map { it.payee })
        compose.waitUntil(15_000) { compose.waitForIdle(); done == true }
        assertEquals(0, count("Save split"), "the split editor closed, and the form was told it is done (a test hosts the form itself)")
    }

    // ================================================================================================== the split editor

    @Test
    fun `the running line says what is left, what is over and when it is shared exactly, and Save waits for the parts to add up`() {
        val groceries = category("Groceries")
        val shopping = category("Shopping")
        showSplitEditor(listOf(SplitPart(groceries, 112_000), SplitPart(shopping, 0)))
        // The first part has it all and the second nothing: it adds up, but the second part has no amount yet.
        status().assertTextEquals("Give every part a category and an amount")
        compose.onNodeWithText("Save split").assertIsNotEnabled()

        compose.onNodeWithTag(splitAmountTag(0)).performTextClearance()
        compose.onNodeWithTag(splitAmountTag(0)).performTextInput("820")
        compose.waitForIdle()
        status().assertTextEquals("₹300 left to share")
        compose.onNodeWithText("Save split").assertIsNotEnabled()

        compose.onNodeWithTag(splitAmountTag(1)).performTextInput("900")
        compose.waitForIdle()
        status().assertTextEquals("₹600 over the total")
        compose.onNodeWithText("Save split").assertIsNotEnabled()
        assertEquals(0, count("Add the", substring = true), "nothing is left to put into a part")

        compose.onNodeWithTag(splitAmountTag(1)).performTextClearance()
        compose.onNodeWithTag(splitAmountTag(1)).performTextInput("300")
        compose.waitForIdle()
        status().assertTextEquals("Shared exactly")
        compose.onNodeWithText("Save split").assertIsEnabled()

        // The total can be changed too: 1,000 is now 120 less than the parts add up to.
        compose.onNodeWithContentDescription("Total in INR").performTextClearance()
        compose.onNodeWithContentDescription("Total in INR").performTextInput("1000")
        compose.waitForIdle()
        status().assertTextEquals("₹120 over the total")
        compose.onNodeWithText("Save split").assertIsNotEnabled()
    }

    @Test
    fun `saving writes the parts as rows of one payment, with the shared fields on each`() {
        val groceries = category("Groceries")
        val shopping = category("Shopping")
        var done: Boolean? = null
        showSplitEditor(listOf(SplitPart(groceries, 82_000), SplitPart(shopping, 30_000)), onDone = { done = it })
        compose.onNodeWithTag(splitNoteTag(0)).performTextInput("Rice and dal")
        compose.onNodeWithTag(splitNoteTag(1)).performScrollTo().performTextInput("Detergent")
        status().assertTextEquals("Shared exactly")
        compose.onNodeWithText("Save split").performClick()
        compose.waitUntil(15_000) { compose.waitForIdle(); done == true }

        val saved = rows().sortedBy { it.id }
        assertEquals(listOf(82_000L, 30_000L), saved.map { it.amount })
        assertEquals(listOf("Rice and dal", "Detergent"), saved.map { it.note })
        assertEquals(setOf("DMart"), saved.map { it.payee }.toSet())
        assertEquals(setOf(at), saved.map { it.occurredAt }.toSet())
        assertEquals(1, saved.map { it.splitGroup }.toSet().size)
    }

    @Test
    fun `parts can be added up to ten, and taken out while more than two are left`() {
        val groceries = category("Groceries")
        showSplitEditor(listOf(SplitPart(groceries, 1_000), SplitPart(groceries, 1_000)))
        assertEquals(0, compose.onAllNodesWithContentDescription("Remove part 1", substring = true).fetchSemanticsNodes().size, "two parts is the least")

        repeat(8) {
            compose.onNodeWithTag(SPLIT_ADD_PART_TAG).performScrollTo().performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText("Part 10").performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag(SPLIT_ADD_PART_TAG).fetchSemanticsNodes().size)
        compose.onNodeWithText("10 parts is the most").performScrollTo().assertIsDisplayed()

        compose.onNodeWithContentDescription("Remove part 10").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(0, count("Part 10"))
        compose.onNodeWithTag(SPLIT_ADD_PART_TAG).performScrollTo().assertIsDisplayed()
        // Nine parts, seven of them empty.
        status().assertTextEquals("Give every part a category and an amount")
    }

    @Test
    fun `with amounts hidden the running line shows no figure`() {
        env.preferences.setAmountsHidden(true)
        val groceries = category("Groceries")
        val shopping = category("Shopping")
        showSplitEditor(listOf(SplitPart(groceries, 82_000), SplitPart(shopping, 30_000)))
        compose.onNodeWithTag(splitAmountTag(1)).performTextClearance()
        compose.onNodeWithTag(splitAmountTag(1)).performTextInput("100")
        compose.waitForIdle()
        status().assertTextEquals("₹•••• left to share")
    }

    // ===================================================================================== a part's details and its row

    @Test
    fun `a part's details show the whole payment - its total and every part - with Edit split, Unsplit and Delete`() {
        val parts = aSplit()
        var action: DetailAction? = null
        compose.setContent { env.Content { TransactionDetailsSheet(parts[1], parts = parts, onAction = { action = it }) } }
        compose.waitForIdle()

        val money = finance.state.value.money
        compose.onNodeWithContentDescription("Expense ${money.format(112_000)}").assertIsDisplayed()
        compose.onNodeWithText("Split into").assertIsDisplayed()
        compose.onNodeWithText("2 parts").assertIsDisplayed()
        compose.onNodeWithText("Groceries").assertIsDisplayed()
        compose.onNodeWithText(money.format(82_000)).assertIsDisplayed()
        compose.onNodeWithText("Rice and dal").assertIsDisplayed()
        compose.onNodeWithText("Shopping").assertIsDisplayed()
        compose.onNodeWithText(money.format(30_000)).assertIsDisplayed()
        compose.onNodeWithText("Detergent").assertIsDisplayed()
        compose.onNodeWithText("DMart").assertIsDisplayed()

        for (label in listOf("Edit split", "Unsplit", "Delete")) compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        assertEquals(0, count("Edit"), "the plain Edit is the split's own")
        compose.onNodeWithText("Edit split").performClick()
        assertEquals(DetailAction.EditSplit, action)
        compose.onNodeWithText("Unsplit").performClick()
        assertEquals(DetailAction.Unsplit, action)
        compose.onNodeWithText("Delete").performClick()
        assertEquals(DetailAction.Delete, action)
    }

    @Test
    fun `a payment that is not split keeps its own details and actions`() {
        val t = expense(45_000, "Apollo")
        compose.setContent { env.Content { TransactionDetailsSheet(t, onAction = {}) } }
        compose.waitForIdle()
        compose.onNodeWithText("Edit").performScrollTo().assertIsDisplayed()
        assertEquals(0, count("Edit split"))
        assertEquals(0, count("Unsplit"))
        assertEquals(0, count("Split into"))
    }

    @Test
    fun `with amounts hidden the details of a split show no figure`() {
        val parts = aSplit()
        env.preferences.setAmountsHidden(true)
        compose.setContent { env.Content { TransactionDetailsSheet(parts[0], parts = parts, onAction = {}) } }
        compose.waitForIdle()
        for (figure in listOf("1,120", "820", "300")) {
            assertEquals(0, compose.onAllNodesWithText(figure, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size, figure)
            assertEquals(0, compose.onAllNodesWithContentDescription(figure, substring = true).fetchSemanticsNodes().size, figure)
        }
        compose.onNodeWithText("Groceries").assertIsDisplayed()
    }

    @Test
    fun `each part is its own row, marked Split and read as a part of a split payment`() {
        val parts = aSplit()
        val plain = expense(700, "Apollo")
        compose.setContent {
            env.Content {
                androidx.compose.foundation.layout.Column {
                    TransactionTile(parts[0])
                    TransactionTile(parts[1])
                    TransactionTile(plain)
                }
            }
        }
        compose.waitForIdle()
        assertEquals(2, compose.onAllNodesWithText("Split", useUnmergedTree = true).fetchSemanticsNodes().size, "one chip a part, none on the other row")
        assertEquals(2, compose.onAllNodesWithContentDescription("part of a split payment", substring = true).fetchSemanticsNodes().size)
        // Each says its own category and amount, so a category filter and a glance both find it.
        val money = finance.state.value.money
        compose.onNodeWithContentDescription("${money.format(30_000)}, DMart, Shopping", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("${money.format(82_000)}, DMart, Groceries", substring = true).assertIsDisplayed()
    }

    // ===================================================================================== delete, unsplit, edit split

    private fun openDetails(of: FinanceTransaction) {
        compose.setContent {
            env.Content {
                val actions = rememberFinanceActions()
                LaunchedEffect(Unit) { actions.showDetails(of) }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `Delete asks first, saying every part goes, and an Undo puts the whole payment back`() {
        val parts = aSplit()
        val before = rows()
        openDetails(parts[1])
        waitFor("Delete")
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        waitFor("Delete this split payment?")
        compose.onNodeWithText("The ₹1,120.00 payment and all its 2 parts", substring = true).assertIsDisplayed()
        compose.onNode(hasText("Delete") and hasClickAction()).performClick()
        compose.waitUntil(15_000) { compose.waitForIdle(); rows().isEmpty() }

        waitFor("Split payment deleted")
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(15_000) { compose.waitForIdle(); rows().size == 2 }
        assertEquals(before.map { it.id!! }.sorted(), rows().map { it.id!! }.sorted())
        assertEquals(setOf(parts[0].splitGroup), rows().map { it.splitGroup }.toSet(), "back as one split, parts and all")
    }

    @Test
    fun `a no on Delete keeps the payment`() {
        val parts = aSplit()
        openDetails(parts[0])
        waitFor("Delete")
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        waitFor("Delete this split payment?")
        compose.onNodeWithText("Cancel").performClick()
        waitUntilGone("Delete this split payment?")
        assertEquals(2, rows().size)
    }

    @Test
    fun `Unsplit asks which category, starting on the largest part's, and makes one payment`() {
        val parts = aSplit()
        openDetails(parts[1])
        waitFor("Unsplit")
        compose.onNodeWithText("Unsplit").performScrollTo().performClick()
        waitFor("Unsplit this payment?")
        compose.onNodeWithText("The 2 parts become one expense of ₹1,120", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Groceries").assertIsDisplayed()
        // Another category can be picked before it is done.
        compose.onNodeWithText("Groceries").performClick()
        waitFor("Health")
        compose.onNodeWithText("Health").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Unsplit") and hasClickAction()).performClick()
        compose.waitUntil(15_000) { compose.waitForIdle(); rows().size == 1 }

        val whole = rows().single()
        assertEquals(112_000L, whole.amount)
        assertEquals(category("Health"), whole.categoryId)
        assertEquals("Rice and dal · Detergent", whole.note)
        assertNull(whole.splitGroup)
        waitFor("Payment unsplit")
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(15_000) { compose.waitForIdle(); rows().size == 2 }
        assertEquals(1, rows().map { it.splitGroup }.toSet().size)
    }

    @Test
    fun `Edit split opens the parts as they are saved`() {
        val parts = aSplit()
        openDetails(parts[0])
        waitFor("Edit split")
        compose.onNodeWithText("Edit split").performScrollTo().performClick()
        waitFor("Share one expense between categories")
        compose.onNodeWithTag(splitAmountTag(0)).assertTextContains("820")
        compose.onNodeWithTag(splitAmountTag(1)).assertTextContains("300")
        compose.onNodeWithTag(splitNoteTag(0)).assertTextContains("Rice and dal")
        status().assertTextEquals("Shared exactly")
        compose.onNodeWithText("Save split").assertIsEnabled()

        // Moving 100 from the second part to the first, and saving, keeps the same two rows.
        compose.onNodeWithTag(splitAmountTag(0)).performTextClearance()
        compose.onNodeWithTag(splitAmountTag(0)).performTextInput("920")
        compose.onNodeWithTag(splitAmountTag(1)).performTextClearance()
        compose.onNodeWithTag(splitAmountTag(1)).performTextInput("200")
        compose.waitForIdle()
        compose.onNodeWithText("Save split").performClick()
        compose.waitUntil(15_000) { compose.waitForIdle(); rows().sortedBy { it.id }.map { it.amount } == listOf(92_000L, 20_000L) }
        assertEquals(parts.map { it.id }, rows().sortedBy { it.id }.map { it.id }, "the same rows")
    }

    @Test
    fun `the form of a part says how many parts there are, and opens them`() {
        val parts = aSplit()
        showEditor(existingId = parts[1].id)
        compose.waitUntil(15_000) { compose.waitForIdle(); compose.onAllNodesWithContentDescription("Split, 2 parts · Edit split").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Split, 2 parts · Edit split").performScrollTo().performClick()
        waitFor("Edit split")
        compose.onNodeWithText("Part 2").assertIsDisplayed()
        compose.onNodeWithTag(splitAmountTag(1)).assertTextContains("300")
        compose.onNodeWithTag(splitAmountTag(0)).assertTextContains("820")
        status().assertTextEquals("Shared exactly")
    }

    // ====================================================================================== check and recalculate

    @Test
    fun `check and recalculate offers to unsplit a split that no longer holds, and does it when asked`() {
        val parts = aSplit()
        // Nothing the app writes does this: the parts left on two accounts.
        sql("UPDATE transactions SET account_id = ${bank().id} WHERE id = ${parts[1].id}")
        compose.setContent { env.Content { DataSettingsPage() } }
        compose.waitForIdle()
        compose.onNodeWithText("Check and recalculate").performScrollTo().performClick()
        waitFor("Split payments that don't add up")
        compose.onNodeWithText("1 thing to look at").assertIsDisplayed()
        compose.onNodeWithText("This split payment's 2 parts are on different accounts").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Unsplit").performScrollTo().performClick()
        waitFor("Unsplit this payment?")
        compose.onNode(hasText("Unsplit") and hasClickAction()).performClick()
        compose.waitUntil(15_000) { compose.waitForIdle(); rows().all { it.splitGroup == null } }
        assertEquals(setOf(82_000L, 30_000L), rows().map { it.amount }.toSet(), "every amount is as it was")
        assertEquals(2, rows().size)
    }
}
