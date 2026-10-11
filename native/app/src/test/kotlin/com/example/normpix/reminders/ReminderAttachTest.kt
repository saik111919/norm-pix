package com.example.normpix.reminders

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.DetailAction
import com.example.normpix.finance.ui.TransactionDetailsSheet
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
 * A reminder about something: the form's "Attached to" field, the sheet that fills it (a transaction, a repeating payment, an
 * account or a card), a draft that opens already attached, and what a reminder says when what it was about is gone.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ReminderAttachTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now()
    private var saved: Reminder? = null

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("reminder_attach")
    }

    @After
    fun tearDown() = env.close()

    private fun showForm(initial: Reminder? = null, existing: Reminder? = null) {
        compose.setContent {
            env.Content {
                AddReminderSheet(existing = existing, cards = emptyList(), onAddCard = { null }, onDone = { saved = it }, initial = initial)
            }
        }
        compose.waitForIdle()
    }

    private fun account(name: String, kind: AccountKind = AccountKind.Bank): Account = runBlocking {
        env.container.finance.saveAccount(Account(name = name, kind = kind, createdAt = now, updatedAt = now))
    }

    private fun expense(account: Account, payee: String, amount: Long = 64_900): FinanceTransaction = runBlocking {
        val category = env.container.finance.state.value.categories.first { it.name == "Bills & utilities" }
        env.container.finance.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense,
                amount = amount,
                accountId = account.id!!,
                categoryId = category.id,
                occurredAt = now.minusDays(1),
                payee = payee,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun reminder(link: ReminderLink?) = Reminder(
        title = "Follow up",
        category = ReminderCategory.Personal,
        dueAt = now.plusDays(1),
        repeatRule = RepeatRule.Once,
        link = link,
        createdAt = now,
        updatedAt = now,
    )

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun openAttach() {
        compose.onNodeWithText("Attached to").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("What is this reminder about?").assertIsDisplayed()
    }

    private fun save() {
        compose.onNodeWithText("Save reminder").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `a new reminder is attached to nothing, and says so`() {
        showForm()
        compose.onNodeWithText("Attached to").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nothing attached").assertExists()
    }

    @Test
    fun `an account can be attached, and the field names it`() {
        val bank = account("HDFC Savings")
        showForm()
        compose.onNodeWithText("What do you want to remember?").performTextInput("Check the balance")
        openAttach()
        compose.onNode(hasText("Account") and radio).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("HDFC Savings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("HDFC Savings · Bank account").performScrollTo().assertIsDisplayed()
        save()
        assertEquals(ReminderLink.account(bank.id!!), saved?.link)
    }

    @Test
    fun `any transaction can be found by search and attached`() {
        val bank = account("HDFC Savings")
        val netflix = expense(bank, "Netflix")
        expense(bank, "Electricity", amount = 120_000)
        showForm()
        compose.onNodeWithText("What do you want to remember?").performTextInput("Cancel Netflix")
        openAttach()
        // The transactions tab is the first, and lists the recent ones; typing narrows them.
        compose.onNodeWithText("Search transactions").performTextInput("netflix")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription("Electricity", substring = true)).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodes(hasContentDescription("Netflix", substring = true) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasContentDescription("Netflix", substring = true) and hasClickAction()).performClick()
        compose.waitForIdle()
        // The field says what it is: the name, then what it was.
        compose.onNode(hasText("Netflix · Expense", substring = true)).performScrollTo().assertIsDisplayed()
        save()
        assertEquals(ReminderLink.transaction(netflix.id!!), saved?.link)
        assertEquals("Cancel Netflix", saved?.title)
    }

    @Test
    fun `a search that finds nothing says so`() {
        account("HDFC Savings")
        showForm()
        openAttach()
        compose.onNodeWithText("Search transactions").performTextInput("zzzz")
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("No transaction matches “zzzz”.").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a draft opens already filled in and attached, and is a new reminder`() {
        val bank = account("HDFC Savings")
        val netflix = expense(bank, "Netflix")
        val draft = ReminderDrafts.aboutTransaction(netflix, "Netflix", "Expense of ₹649 on 10 Oct from HDFC Savings", now)
        showForm(initial = draft)
        compose.onNodeWithText("Netflix").assertExists()
        // What it is attached to is looked up on the database thread, which the test clock does not wait for.
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodes(hasText("Netflix · Expense", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("Netflix · Expense", substring = true)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("New reminder").assertIsDisplayed()
        save()
        val reminder = assertNotNull(saved)
        assertNull(reminder.id, "it is a new reminder")
        assertEquals(ReminderLink.transaction(netflix.id!!), reminder.link)
        assertEquals(ReminderCategory.Expenses, reminder.category)
    }

    @Test
    fun `the attachment can be taken away`() {
        val bank = account("HDFC Savings")
        showForm(existing = reminder(ReminderLink.account(bank.id!!)))
        compose.onNodeWithText("HDFC Savings · Bank account").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove attached to").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Nothing attached").assertExists()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitForIdle()
        assertNull(saved?.link)
    }

    @Test
    fun `what a reminder was about is read fresh, and says so when it is gone`() {
        showForm(existing = reminder(ReminderLink.transaction(9_999)))
        // The link is looked up on the database thread, which the test clock does not wait for.
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("This transaction no longer exists").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("This transaction no longer exists").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the reminders list shows what each reminder is attached to`() {
        val bank = account("HDFC Savings")
        runBlocking { env.container.reminders.addReminder(reminder(ReminderLink.account(bank.id!!)).copy(title = "Check the balance")) }
        runBlocking { env.container.reminders.load() }
        compose.setContent { env.Content { RemindersView() } }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("Check the balance").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("HDFC Savings · Bank account")).fetchSemanticsNodes().isNotEmpty() }
    }

    // --- from the transaction's own details ---------------------------------------------------------------------------

    @Test
    fun `a transaction's details offer to remind me about it, when reminders are there`() {
        val bank = account("HDFC Savings")
        val t = expense(bank, "Netflix")
        var action: DetailAction? = null
        compose.setContent { env.Content { TransactionDetailsSheet(t, canRemind = true, onAction = { action = it }) } }
        compose.waitForIdle()
        compose.onNodeWithText("Remind").performScrollTo().performClick()
        assertEquals(DetailAction.Remind, action)
    }

    @Test
    fun `without reminders the details do not offer one`() {
        val bank = account("HDFC Savings")
        val t = expense(bank, "Netflix")
        compose.setContent { env.Content { TransactionDetailsSheet(t, onAction = {}) } }
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Remind").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Edit").assertExists()
    }
}
