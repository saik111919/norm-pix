package com.example.normpix.finance.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.SavingsGoal
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
 * Savings goals on screen (2026-11-15): Plans > Goals with its banner ("Saved" / "Still to go") and a row for each goal - its picture, name,
 * a bar, "Rs X of Rs Y" and what a month must put by, or that it is reached or its date has passed; the goal's sheet (Edit, Delete asking
 * first, Add money and Take out only for a goal on its own); the add form for both kinds of goal with its messages; Add money and Take out
 * with the message that no money moves between accounts; and amounts kept off the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
@OptIn(ExperimentalTestApi::class)
class GoalsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now().withSecond(0).withNano(0)
    private val today = LocalDate.now()
    private val finance get() = env.container.finance

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("goals_ui_test")
    }

    @After
    fun tearDown() = env.close()

    private fun rupees(amount: Long) = amount * 100

    private fun monthYear(date: LocalDate) = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US).format(date)

    private fun savings(name: String = "SBI Savings", opening: Long = rupees(45_000)): Account = runBlocking {
        finance.saveAccount(Account(name = name, kind = AccountKind.Savings, openingBalance = opening, createdAt = now, updatedAt = now))
    }

    private fun goal(
        name: String,
        target: Long,
        saved: Long = 0,
        account: Long? = null,
        date: LocalDate? = null,
        emoji: String? = "🌴",
    ): SavingsGoal = runBlocking {
        finance.saveGoal(SavingsGoal(name = name, target = target, targetDate = date, emoji = emoji, accountId = account, saved = saved, createdAt = now, updatedAt = now))
    }

    private fun stored(): List<SavingsGoal> = runBlocking { finance.repository.goals() }

    private fun showGoals() {
        compose.setContent { env.Content { GoalsView() } }
        compose.waitForIdle()
    }

    private fun showForm(existing: SavingsGoal? = null, onDone: (SavingsGoal?) -> Unit = {}) {
        compose.setContent { env.Content { GoalFormSheet(existing, onDone) } }
        compose.waitForIdle()
    }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun printed(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun waitForText(text: String) = compose.waitUntil(timeoutMillis = 10_000) {
        compose.waitForIdle()
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitUntilGone(text: String) = compose.waitUntil(timeoutMillis = 10_000) {
        compose.waitForIdle()
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    /** The goal's row, found by what a screen reader says of it. */
    private fun row(startsWith: String) = compose.onNode(
        SemanticsMatcher("row starting $startsWith") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith(startsWith) } == true
        },
    )

    // =================================================================================================== the section

    @Test
    fun `with no goals it says what a goal is and offers to add one`() {
        showGoals()
        compose.onNodeWithText("No goals yet").assertIsDisplayed()
        compose.onNodeWithText("Save for a trip, a gadget or a rainy day.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("It never moves money between your accounts.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Add goal").assertIsDisplayed()
        assertEquals(0, count("Saved"), "no banner without goals")
    }

    @Test
    fun `the banner says what is saved and still to go, and each goal has its row`() {
        val account = savings()
        val date = today.plusMonths(5)
        goal("Trip to Goa", rupees(40_000), saved = rupees(18_000), date = date)
        goal("Emergency fund", rupees(3_00_000), account = account.id)
        showGoals()

        // Saved counts each goal up to its target: 18,000 + 45,000 of 3,40,000 in all.
        compose.onNodeWithContentDescription("Saved ₹63,000 of ₹3,40,000 across 2 goals, ₹2,77,000 still to go.").assertIsDisplayed()
        assertEquals(1, printed("Saved"))
        assertEquals(1, printed("Still to go"))
        assertEquals(1, printed("₹63,000"))
        assertEquals(1, printed("₹2,77,000"))
        assertEquals(1, printed("across 2 goals"))

        // The goal on its own: name, amounts, share, and what a month must put by.
        val status = "₹4,400 a month to reach it by ${monthYear(date)}"
        row("Trip to Goa, ₹18,000 of ₹40,000, 45%, $status").assertIsDisplayed()
        assertEquals(1, printed("Trip to Goa"))
        assertEquals(1, printed("₹18,000 of ₹40,000"))
        assertEquals(1, printed("45%"))
        assertEquals(1, printed(status))
        // The goal that counts an account: its balance, no date so no month needed.
        row("Emergency fund, ₹45,000 of ₹3,00,000, 15%").assertIsDisplayed()
        assertEquals(1, printed("₹45,000 of ₹3,00,000"))
        assertEquals(1, compose.onAllNodesWithText("a month to reach it", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size, "only the first has a month needed")
        compose.onNodeWithText("Add goal").assertIsDisplayed()
    }

    @Test
    fun `a reached goal says Reached and a goal whose date has gone says The date has passed`() {
        goal("Laptop", rupees(80_000), saved = rupees(80_000), date = today.minusMonths(1))
        goal("Bike", rupees(60_000), saved = rupees(10_000), date = today.minusDays(3))
        showGoals()

        row("Laptop, ₹80,000 of ₹80,000, 100%, Reached").assertIsDisplayed()
        row("Bike, ₹10,000 of ₹60,000, 17%, The date has passed").assertIsDisplayed()
        assertEquals(1, printed("Reached"))
        assertEquals(1, printed("The date has passed"))
        // Reached goals go after the ones still being saved for.
        compose.onNodeWithContentDescription("Saved ₹90,000 of ₹1,40,000 across 2 goals, ₹50,000 still to go, 1 reached.").assertIsDisplayed()
    }

    @Test
    fun `a goal over its target is reached, the bar full and the left nothing`() {
        goal("Fund", rupees(1_000), account = savings(opening = rupees(5_000)).id)
        showGoals()
        row("Fund, ₹5,000 of ₹1,000, 100%, Reached").assertIsDisplayed()
        // The summary counts it as its target, not the account's balance.
        compose.onNodeWithContentDescription("Saved ₹1,000 of ₹1,000 across 1 goal, ₹0 still to go, 1 reached.").assertIsDisplayed()
    }

    @Test
    fun `with amounts hidden no figure of a goal shows - the share and the words stay`() {
        val account = savings()
        goal("Trip to Goa", rupees(40_000), saved = rupees(18_000), date = today.plusMonths(5))
        goal("Emergency fund", rupees(3_00_000), account = account.id)
        env.preferences.setAmountsHidden(true)
        showGoals()

        compose.onNodeWithContentDescription("Saved ₹•••• of ₹•••• across 2 goals, ₹•••• still to go.").assertIsDisplayed()
        row("Trip to Goa, ₹•••• of ₹••••, 45%, ₹•••• a month to reach it by ${monthYear(today.plusMonths(5))}").assertIsDisplayed()
        for (figure in listOf("18,000", "40,000", "45,000", "3,00,000", "4,400", "63,000")) {
            assertEquals(0, compose.onAllNodesWithText(figure, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size, figure)
            assertEquals(0, compose.onAllNodesWithContentDescription(figure, substring = true).fetchSemanticsNodes().size, figure)
        }
    }

    // ============================================================================================== a goal's sheet

    @Test
    fun `a goal on its own opens a sheet with Add money, Take out, Edit and Delete - and says what it counts`() {
        goal("Trip to Goa", rupees(40_000), saved = rupees(18_000), date = today.plusMonths(5))
        showGoals()
        row("Trip to Goa").performClick()
        waitForText("Add money")

        compose.onNodeWithText("₹18,000 of ₹40,000 · 45%").assertIsDisplayed()
        compose.onNodeWithText("₹22,000 left to save").assertIsDisplayed()
        compose.onNodeWithText("Saved on the goal itself. This doesn't move money between accounts.").assertIsDisplayed()
        compose.onNodeWithText("Take out").assertIsDisplayed()
        compose.onNodeWithText("Edit").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun `a goal that counts an account has no Add money or Take out, and names the account`() {
        goal("Emergency fund", rupees(3_00_000), account = savings().id)
        showGoals()
        row("Emergency fund").performClick()
        waitForText("Edit")

        compose.onNodeWithText("Counts the balance of SBI Savings, as of today.").assertIsDisplayed()
        assertEquals(0, count("Add money"))
        assertEquals(0, count("Take out"))
        compose.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun `Take out is offered only while there is something saved`() {
        goal("Empty", rupees(1_000))
        showGoals()
        row("Empty").performClick()
        waitForText("Add money")
        assertEquals(0, count("Take out"))
    }

    @Test
    fun `Delete asks first, saying nothing moves, and a no keeps the goal`() {
        goal("Trip to Goa", rupees(40_000), saved = rupees(18_000))
        showGoals()
        row("Trip to Goa").performClick()
        waitForText("Delete")
        compose.onNodeWithText("Delete").performClick()
        waitForText("Delete Trip to Goa?")

        compose.onNodeWithText("The goal and the ₹18,000 counted on it are removed.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("That money isn't in any account, so nothing moves.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        waitUntilGone("Delete Trip to Goa?")
        assertEquals(1, stored().size, "a no keeps it")
    }

    @Test
    fun `Delete removes the goal and no account changes`() {
        val account = savings()
        goal("Emergency fund", rupees(3_00_000), account = account.id)
        goal("Trip to Goa", rupees(40_000), saved = rupees(18_000))
        showGoals()
        val balances = runBlocking { finance.repository.accountBalances() }

        row("Emergency fund").performClick()
        waitForText("Delete")
        compose.onNodeWithText("Delete").performClick()
        waitForText("Delete Emergency fund?")
        compose.onNodeWithText("The goal is removed. No account or balance changes.", substring = true).assertIsDisplayed()
        compose.onNode(hasText("Delete") and hasClickAction()).performClick()
        compose.waitUntil(10_000) { compose.waitForIdle(); stored().size == 1 }
        assertEquals(listOf("Trip to Goa"), stored().map { it.name })
        assertEquals(balances, runBlocking { finance.repository.accountBalances() })
    }

    // ================================================================================================== add money, take out

    private fun showMoney(goal: SavingsGoal, adding: Boolean, onDone: (GoalMoneyResult?) -> Unit = {}) {
        compose.setContent { env.Content { GoalMoneySheet(goal, adding, onDone) } }
        compose.waitForIdle()
    }

    @Test
    fun `Add money adds to what is saved, and says that no money moves between accounts`() {
        val goal = goal("Trip to Goa", rupees(40_000), saved = rupees(18_000))
        var result: GoalMoneyResult? = null
        showMoney(goal, adding = true) { result = it }
        compose.onNodeWithText("This doesn't move money between accounts. It only changes what is counted on this goal.").assertIsDisplayed()
        compose.onNodeWithText("Trip to Goa · ₹18,000 saved").assertIsDisplayed()

        compose.onNodeWithContentDescription("Amount to add in INR").performTextInput("2500")
        compose.onNode(hasText("Add money") and hasClickAction()).performClick()
        compose.waitUntil(10_000) { compose.waitForIdle(); result != null }
        assertEquals(rupees(20_500), stored().single().saved)
        assertEquals(rupees(2_500), result!!.amount)
        assertTrue(result!!.adding)
        assertEquals(rupees(20_500), result!!.goal.saved)
    }

    @Test
    fun `an amount that isn't there is asked for under the field, in the sheet`() {
        val goal = goal("Trip to Goa", rupees(40_000), saved = rupees(18_000))
        showMoney(goal, adding = true)
        compose.onNode(hasText("Add money") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Enter an amount").assertIsDisplayed()
        assertEquals(rupees(18_000), stored().single().saved)
        compose.onNodeWithContentDescription("Amount to add in INR").performTextInput("0")
        compose.onNode(hasText("Add money") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Enter an amount above zero").assertIsDisplayed()
    }

    @Test
    fun `Take out takes from what is saved, and more than is saved is refused in the sheet`() {
        val goal = goal("Trip to Goa", rupees(40_000), saved = rupees(1_000))
        var result: GoalMoneyResult? = null
        showMoney(goal, adding = false) { result = it }
        compose.onNodeWithText("Trip to Goa · ₹1,000 saved").assertIsDisplayed()

        compose.onNodeWithContentDescription("Amount to take out in INR").performTextInput("1000.01")
        compose.onNode(hasText("Take out") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("You only have ₹1,000 saved here").assertIsDisplayed()
        assertEquals(rupees(1_000), stored().single().saved, "nothing was taken")
        assertNull(result)

        compose.onNodeWithContentDescription("Amount to take out in INR").performTextClearance()
        compose.onNodeWithContentDescription("Amount to take out in INR").performTextInput("400")
        compose.onNode(hasText("Take out") and hasClickAction()).performClick()
        compose.waitUntil(10_000) { compose.waitForIdle(); result != null }
        assertEquals(rupees(600), stored().single().saved)
        assertEquals(false, result!!.adding)
        assertEquals(rupees(400), result!!.amount)
    }

    @Test
    fun `the whole flow from a goal's sheet - Add money, then it shows in the list`() {
        goal("Trip to Goa", rupees(40_000), saved = rupees(18_000))
        showGoals()
        row("Trip to Goa").performClick()
        waitForText("Add money")
        compose.onNodeWithText("Add money").performClick()
        waitForText("This doesn't move money between accounts. It only changes what is counted on this goal.")
        compose.onNodeWithContentDescription("Amount to add in INR").performTextInput("2000")
        compose.onNode(hasText("Add money") and hasClickAction()).performClick()
        compose.waitUntil(10_000) { compose.waitForIdle(); stored().single().saved == rupees(20_000) }
        waitForText("Added ₹2,000 to Trip to Goa")
        row("Trip to Goa, ₹20,000 of ₹40,000, 50%").assertIsDisplayed()
    }

    // ========================================================================================================= the form

    @Test
    fun `a new goal asks for a name and a target, under their fields`() {
        showForm()
        compose.onNodeWithText("New goal").assertIsDisplayed()
        compose.onNodeWithText("Save goal").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Give the goal a name").assertIsDisplayed()
        compose.onNodeWithText("Enter the amount you want to save").assertIsDisplayed()
        assertTrue(stored().isEmpty())

        compose.onNodeWithText("Name").performTextInput("Trip to Goa")
        compose.onNodeWithContentDescription("Target in INR").performTextInput("0")
        compose.onNodeWithText("Save goal").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Enter a target above zero").assertIsDisplayed()
        assertEquals(0, count("Give the goal a name"), "the name is fine now")
    }

    @Test
    fun `a name of more than 40 characters is refused`() {
        showForm()
        compose.onNodeWithText("Name").performTextInput("x".repeat(45))
        compose.onNodeWithContentDescription("Target in INR").performTextInput("1000")
        compose.onNodeWithText("Save goal").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Keep the goal's name to 40 characters.").assertIsDisplayed()
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `a goal on its own is saved with what is saved so far, and starts there when there is no savings account`() {
        var saved: SavingsGoal? = null
        showForm { saved = it }
        // No Savings account: it starts On its own, and says what that is.
        compose.onNode(hasText("On its own") and radio).assertIsSelected()
        compose.onNodeWithText("Add money and Take out change this. This doesn't move money between accounts.").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Name").performTextInput("  Trip   to Goa ")
        compose.onNodeWithContentDescription("Target in INR").performTextInput("40000")
        compose.onNodeWithText("Saved so far").performScrollTo().performTextInput("18000")
        compose.onNodeWithText("Save goal").performClick()
        compose.waitUntil(10_000) { compose.waitForIdle(); saved != null }

        val goal = stored().single()
        assertEquals("Trip to Goa", goal.name)
        assertEquals(rupees(40_000), goal.target)
        assertEquals(rupees(18_000), goal.saved)
        assertNull(goal.accountId)
        assertNull(goal.targetDate)
        assertEquals("🎯", goal.symbol, "the default emoji")
        assertEquals(goal.id, saved?.id, "the sheet gives back the saved goal")
    }

    @Test
    fun `a goal that counts an account is saved against it - with a savings account it starts on that choice`() {
        val account = savings()
        var saved: SavingsGoal? = null
        showForm { saved = it }
        compose.onNode(hasText("Linked account") and radio).assertIsSelected()
        compose.onNodeWithText("The account's balance, as of today, counts as saved. Nothing else to keep up.").performScrollTo().assertIsDisplayed()
        assertEquals(0, count("Saved so far"))

        compose.onNodeWithText("Name").performTextInput("Emergency fund")
        compose.onNodeWithContentDescription("Target in INR").performTextInput("300000")
        compose.onNodeWithText("Save goal").performClick()
        compose.waitUntil(10_000) { compose.waitForIdle(); saved != null }

        val goal = stored().single()
        assertEquals(account.id, goal.accountId)
        assertEquals(0L, goal.saved)
        assertEquals(rupees(3_00_000), goal.target)
    }

    @Test
    fun `choosing Linked account with no account chosen asks for one, under the field`() {
        showForm()
        compose.onNodeWithText("Name").performTextInput("Fund")
        compose.onNodeWithContentDescription("Target in INR").performTextInput("1000")
        compose.onNode(hasText("Linked account") and radio).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Save goal").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Choose an account").performScrollTo().assertIsDisplayed()
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `saved so far must be an amount`() {
        showForm()
        compose.onNodeWithText("Name").performTextInput("Fund")
        compose.onNodeWithContentDescription("Target in INR").performTextInput("1000")
        compose.onNodeWithText("Saved so far").performScrollTo().performTextInput("1.2.3")
        compose.onNodeWithText("Save goal").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Enter a valid amount").performScrollTo().assertIsDisplayed()
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `editing a goal shows what it has, and a date can be taken off`() {
        val date = today.plusMonths(8)
        val existing = goal("Trip to Goa", rupees(40_000), saved = rupees(18_000), date = date, emoji = "🌴")
        var saved: SavingsGoal? = null
        showForm(existing) { saved = it }
        compose.onNodeWithText("Edit goal").assertIsDisplayed()
        compose.onNodeWithText("Save changes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Target date (optional), ${shortDate(date.atStartOfDay())}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Saved so far").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("On its own") and radio).assertIsSelected()

        compose.onNodeWithText("Remove date").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Target date (optional), No date").assertIsDisplayed()
        compose.onNodeWithText("Name").performTextClearance()
        compose.onNodeWithText("Name").performTextInput("Goa trip")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { compose.waitForIdle(); saved != null }

        val goal = stored().single()
        assertEquals("Goa trip", goal.name)
        assertNull(goal.targetDate)
        assertEquals(existing.id, goal.id)
        assertEquals(rupees(18_000), goal.saved, "what was saved is as it was")
        assertEquals("🌴", goal.emoji)
    }
}
