package com.example.normpix.finance.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.Tag
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
 * Tags on screen (2026-11-15): the transaction form's Tags field with its chips (and none for a transfer); the picker - the two
 * ready-made tags while there are none, typing a new one, ticking, applying, saving; Edit tags renaming and deleting (asking first,
 * saying how many transactions it is on); the filter by tag; and a tag chip in a transaction's details opening that tag's transactions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class TagsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now().withSecond(0).withNano(0)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("tags_ui_test")
    }

    @After
    fun tearDown() = env.close()

    private val finance get() = env.container.finance

    private fun tag(name: String): Tag = runBlocking { finance.saveTag(Tag(name = name, createdAt = now)) }

    private fun expense(amount: Long, payee: String, tags: Set<Long> = emptySet(), at: LocalDateTime = now.minusHours(1)): FinanceTransaction = runBlocking {
        val cash = finance.repository.accounts().first { it.kind == AccountKind.Cash }
        val health = finance.repository.categories().first { it.name == "Health" }
        finance.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense, amount = amount, accountId = cash.id!!, categoryId = health.id, occurredAt = at, payee = payee,
                createdAt = now, updatedAt = now, tagIds = tags,
            ),
        )
    }

    private fun stored(id: Long): FinanceTransaction = runBlocking { finance.repository.transactionById(id)!! }

    private fun count(text: String, substring: Boolean = false) = compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    /** Rows say themselves to a screen reader in one sentence, so their printed words are in the unmerged tree. */
    private fun printed(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    private val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun tagField() = compose.onNodeWithContentDescription("Tags,", substring = true)

    // ================================================================================================== the form's field

    @Test
    fun `the form's Tags field shows the transaction's tags, and a transfer has no field`() {
        val tax = tag("Tax-deductible")
        val trip = tag("Goa trip")
        val t = expense(45_000, "Apollo", setOf(tax.id!!, trip.id!!))
        compose.setContent { env.Content { TransactionEditorSheet(TransactionType.Expense, existingId = t.id, onDone = {}) } }
        compose.waitForIdle()

        tagField().performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Tags, Goa trip, Tax-deductible").assertIsDisplayed()
        // The chips are printed in the field (its words are said once, by the field).
        assertEquals(1, compose.onAllNodesWithText("Goa trip", useUnmergedTree = true).fetchSemanticsNodes().size)

        compose.onNode(hasText("Transfer") and radio).performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(SemanticsMatcher("tags field") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith("Tags,") } == true
        }).fetchSemanticsNodes().size, "a transfer has no Tags field")
    }

    @Test
    fun `with no tags yet the picker offers the two ready-made ones, and picking one makes it, ticks it, and saves with the transaction`() {
        val t = expense(45_000, "Apollo")
        compose.setContent { env.Content { TransactionEditorSheet(TransactionType.Expense, existingId = t.id, onDone = {}) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Tags, none").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Ready-made").assertIsDisplayed()
        compose.onNodeWithText("Tax-deductible").assertIsDisplayed()
        compose.onNodeWithText("Reimbursable").assertIsDisplayed()
        compose.onNodeWithText("What you can claim against your tax").assertIsDisplayed()
        assertTrue(runBlocking { finance.repository.tags() }.isEmpty(), "offered, not made")

        compose.onNodeWithText("Tax-deductible").performClick()
        compose.waitForIdle()
        val made = runBlocking { finance.repository.tags() }
        assertEquals(listOf("Tax-deductible"), made.map { it.name }, "made when picked, and only that one")
        // Now there is a tag: no more ready-made ones, the new one ticked.
        assertEquals(0, count("Ready-made"))
        assertEquals(0, count("Reimbursable"))
        compose.onNode(hasText("Tax-deductible") and checkbox).assertIsOn()
        compose.onNodeWithText("1 selected").assertIsDisplayed()

        compose.onNodeWithContentDescription("Apply (1)").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Tags, Tax-deductible").assertIsDisplayed()

        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { compose.waitForIdle(); stored(t.id!!).tagIds.isNotEmpty() }
        assertEquals(setOf(made.single().id), stored(t.id!!).tagIds)
    }

    @Test
    fun `typing a new name offers to add it, an existing one is found and not doubled, and a long one is refused in the sheet`() {
        val trip = tag("Goa trip")
        var result: Set<Long>? = null
        compose.setContent { env.Content { TagPickerSheet(selected = emptySet(), onDone = { result = it }) } }
        compose.waitForIdle()
        assertEquals(0, count("Ready-made"), "the ready-made ones are only for a list with none")
        compose.onNode(hasText("Goa trip") and checkbox).assertIsOff()

        val search = compose.onNodeWithTag(TAG_PICKER_SEARCH_TAG)
        search.performTextInput("GOA TRIP")
        compose.waitForIdle()
        assertEquals(0, count("Add “", substring = true), "the same name in another case is the tag there is")
        compose.onNode(hasText("Goa trip") and checkbox).assertIsDisplayed()

        search.performTextClearance()
        search.performTextInput("  Office   lunch ")
        compose.waitForIdle()
        compose.onNodeWithText("Add “Office lunch”").performClick()
        compose.waitForIdle()
        val office = assertNotNull(runBlocking { finance.repository.tags() }.firstOrNull { it.name == "Office lunch" })
        compose.onNode(hasText("Office lunch") and checkbox).assertIsOn()

        compose.onNodeWithTag(TAG_PICKER_SEARCH_TAG).performTextInput("x".repeat(31))
        compose.waitForIdle()
        compose.onNodeWithText("Up to 30 characters").assertIsDisplayed()
        compose.onNodeWithText("Add “${"x".repeat(31)}”").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Keep the tag's name to 30 characters.").assertIsDisplayed()
        assertEquals(2, runBlocking { finance.repository.tags() }.size, "nothing more was made")

        compose.onNodeWithTag(TAG_PICKER_SEARCH_TAG).performTextClearance()
        compose.waitForIdle()
        compose.onNode(hasText("Goa trip") and checkbox).performClick()
        compose.onNodeWithContentDescription("Apply (2)").performClick()
        compose.waitForIdle()
        assertEquals(setOf(trip.id!!, office.id!!), result)
    }

    @Test
    fun `Edit tags renames a tag, and deletes one only after saying how many transactions it is on`() {
        val tax = tag("Tax")
        tag("Goa trip")
        val a = expense(100, "Apollo", setOf(tax.id!!))
        val b = expense(200, "Chemist", setOf(tax.id!!))
        compose.setContent { env.Content { TagManagerSheet(onDone = {}) } }
        compose.waitForIdle()
        compose.onNodeWithText("On 2 transactions").assertIsDisplayed()
        compose.onNodeWithText("Not on any transaction").assertIsDisplayed()

        compose.onNodeWithContentDescription("Rename Tax").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Rename tag").assertIsDisplayed()
        // The one field there is: the tag's name.
        val name = compose.onNode(hasSetTextAction())
        compose.onNode(hasText("Tax") and hasSetTextAction()).assertIsDisplayed()
        name.performTextClearance()
        name.performTextInput("goa trip")
        compose.onNodeWithText("Save name").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("You already have a tag called Goa trip.").assertIsDisplayed()
        name.performTextClearance()
        name.performTextInput("Tax-deductible")
        compose.onNodeWithText("Save name").performClick()
        compose.waitUntil(10_000) { compose.waitForIdle(); runBlocking { finance.repository.tags() }.any { it.name == "Tax-deductible" } }
        assertEquals(tax.id, runBlocking { finance.repository.tags() }.first { it.name == "Tax-deductible" }.id, "renamed, the same tag")

        // Asked first, saying how many it is on; "Cancel" keeps it.
        compose.onNodeWithContentDescription("Delete Tax-deductible").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Delete “Tax-deductible”?").assertIsDisplayed()
        compose.onNodeWithText("It's on 2 transactions. The tag comes off them; the transactions stay as they are.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(2, runBlocking { finance.repository.tags() }.size)

        compose.onNodeWithContentDescription("Delete Tax-deductible").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(10_000) { compose.waitForIdle(); runBlocking { finance.repository.tags() }.size == 1 }
        assertEquals(listOf("Goa trip"), runBlocking { finance.repository.tags() }.map { it.name })
        assertEquals(emptySet(), stored(a.id!!).tagIds)
        assertEquals(emptySet(), stored(b.id!!).tagIds)
        assertEquals(100L, stored(a.id!!).amount, "the transactions are kept")
    }

    // ================================================================================================== the filter

    @Test
    fun `the filter sheet has Tags once there are tags, and gives back the ones ticked`() {
        var result: TransactionQuery? = null
        compose.setContent { env.Content { TransactionFilterSheet(current = TransactionQuery(), showAccounts = true, onDone = { result = it }) } }
        compose.waitForIdle()
        assertEquals(0, count("Any tag"), "no tags yet: no Tags row")

        // The first tags arrive while it is open: the row comes with them.
        val trip = tag("Goa trip")
        tag("Tax-deductible")
        compose.waitForIdle()
        compose.onNodeWithText("Any tag").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Goa trip") and checkbox).performClick()
        compose.onNodeWithContentDescription("Apply (1)").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Goa trip").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show results").performClick()
        compose.waitForIdle()
        assertEquals(setOf(trip.id!!), result?.tagIds)
    }

    @Test
    fun `a tag's history lists its transactions only, all time, with the tag as a filter chip`() {
        val trip = tag("Goa trip")
        expense(100, "Beach shack", setOf(trip.id!!), at = now.minusMonths(3))
        expense(200, "Ferry", setOf(trip.id!!))
        expense(300, "Groceries")
        compose.setContent { env.Content { TagHistoryPage(trip.id!!) } }
        compose.waitForIdle()
        compose.waitUntil(10_000) { printed("Ferry") > 0 }
        assertEquals(1, printed("Beach shack"), "all time: three months back too")
        assertEquals(0, printed("Groceries"))
        compose.onNodeWithContentDescription("Remove tag filter").assertIsDisplayed()
        compose.onNodeWithContentDescription("Year by tag").performClick()
        assertEquals(1, env.navigator.yearByTagOpened)
    }

    // ================================================================================================== the details

    @Test
    fun `a transaction's details show its tags, and a tag opens that tag's transactions`() {
        val tax = tag("Tax-deductible")
        val t = expense(45_000, "Apollo", setOf(tax.id!!))
        compose.setContent {
            env.Content {
                val actions = rememberFinanceActions()
                LaunchedEffect(Unit) { actions.showDetails(t) }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Tag Tax-deductible, show its transactions").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf<Pair<Long, DateRange?>>(tax.id!! to null), env.navigator.tagHistories)
        assertEquals(0, count("Share receipt"), "the details closed on the way")
    }

    @Test
    fun `a transaction with no tags shows no tag chips`() {
        val t = expense(45_000, "Apollo")
        compose.setContent { env.Content { TransactionDetailsSheet(t, onAction = {}) } }
        compose.waitForIdle()
        compose.onNodeWithText("Share receipt").assertIsDisplayed()
        assertNull(compose.onAllNodes(SemanticsMatcher("a tag chip") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith("Tag ") } == true
        }).fetchSemanticsNodes().firstOrNull())
    }
}
