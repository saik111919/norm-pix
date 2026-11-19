package com.example.normpix.finance.ui

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.DebtEntry
import com.example.normpix.finance.domain.DebtKind
import com.example.normpix.finance.domain.Person
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
 * Money lent and borrowed on screen (2026-11-19): a person's card says who owes whom in words, offers Settle up only while something is
 * owed, and lists the records; the form records an amount typed on the keypad - with its money moved through an account (an expense in
 * "Lent to someone") or on its own; Settle up's form starts on the whole balance; the People page adds it up and says it on each row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class DebtsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now().withSecond(0).withNano(0)
    private var closedWith: DebtEntry? = null
    private var closed = false

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("debts_ui_test")
    }

    @After
    fun tearDown() = env.close()

    private val finance get() = env.container.finance

    private fun ravi(): Person = runBlocking { finance.savePerson(Person(name = "Ravi", createdAt = now, updatedAt = now)) }

    private fun record(person: Person, kind: DebtKind, amount: Long, note: String? = null) = runBlocking {
        finance.saveDebt(DebtEntry(personId = person.id!!, kind = kind, amount = amount, occurredAt = now.minusDays(2), note = note, createdAt = now, updatedAt = now))
    }

    private fun count(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    /** Whether one node is described for a screen reader with every one of [parts]. */
    private fun described(vararg parts: String) = compose.onAllNodes(
        androidx.compose.ui.test.SemanticsMatcher("described as ${parts.joinToString()}") { node ->
            val words = node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
            parts.all { it in words }
        },
    ).fetchSemanticsNodes().isNotEmpty()

    private fun keys(vararg names: String) {
        for (name in names) compose.onNodeWithTag(amountKeyTag(name)).performClick()
        compose.waitForIdle()
    }

    private fun form(person: Person, kind: DebtKind, amount: Long? = null) {
        compose.setContent {
            env.Content {
                DebtFormSheet(person, kind, amount, onDone = {
                    closedWith = it
                    closed = true
                })
            }
        }
        compose.waitForIdle()
    }

    private fun waitClosed() = compose.waitUntil(5_000) {
        compose.waitForIdle()
        closed
    }

    @Test
    fun `a person's card says who owes whom, offers Settle up while something is owed, and lists the records`() {
        val ravi = ravi()
        record(ravi, DebtKind.Lent, 50_000, note = "Dinner")
        record(ravi, DebtKind.RepaidToMe, 20_000)
        val person = finance.state.value.person(ravi.id)!!
        compose.setContent { env.Content { PersonDebtsCard(person) } }
        compose.waitForIdle()

        compose.onNodeWithText("Money between you").assertIsDisplayed()
        compose.onNodeWithText("Ravi owes you ₹300.00").assertIsDisplayed()
        compose.onNodeWithText("Settle up").assertIsDisplayed()
        compose.onNodeWithText("You lent").assertIsDisplayed()
        compose.onNodeWithText("You borrowed").assertIsDisplayed()
        // Each record is one sentence for a screen reader: what happened, how much, when and its note.
        assertTrue(described("You lent Ravi ₹500.00", "Dinner"), "the lending, with its note")
        assertTrue(described("Ravi paid you back ₹200.00"))
    }

    @Test
    fun `with nothing owed the card says so and offers no Settle up`() {
        val ravi = ravi()
        val person = finance.state.value.person(ravi.id)!!
        compose.setContent { env.Content { PersonDebtsCard(person) } }
        compose.waitForIdle()
        compose.onNodeWithText("Nothing owed between you").assertIsDisplayed()
        assertEquals(0, count("Settle up"))
    }

    @Test
    fun `lending records the amount typed on the keypad, with an expense from the default account in Lent to someone`() {
        val ravi = ravi()
        form(ravi, DebtKind.Lent)
        compose.onNodeWithText("You lent Ravi").assertIsDisplayed()
        keys("digit_5", "digit_0", "digit_0")
        compose.onNodeWithText("Save").performClick()
        waitClosed()

        val saved = assertNotNull(closedWith)
        assertEquals(50_000L, saved.amount)
        assertEquals(DebtKind.Lent, saved.kind)
        val tx = runBlocking { finance.repository.transactionById(assertNotNull(saved.transactionId)) }!!
        assertEquals(TransactionType.Expense, tx.type)
        assertEquals("Lent to someone", finance.state.value.category(tx.categoryId)?.name)
        assertEquals(AccountKind.Cash, finance.state.value.account(tx.accountId)?.kind)
        assertEquals("Ravi", tx.payee)
        assertEquals(50_000L, finance.state.value.debtBalances[ravi.id])
    }

    @Test
    fun `with the account switch off only what is owed is kept`() {
        val ravi = ravi()
        form(ravi, DebtKind.Borrowed)
        keys("digit_2", "digit_0", "digit_0")
        compose.onNodeWithText("Received into an account").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Save").performClick()
        waitClosed()

        val saved = assertNotNull(closedWith)
        assertNull(saved.transactionId)
        assertEquals(-20_000L, finance.state.value.debtBalances[ravi.id])
        assertTrue(runBlocking { finance.repository.transactionsBetween(now.minusYears(1), now.plusDays(1)) }.isEmpty())
    }

    @Test
    fun `settling up starts on the whole balance and leaves the two of them settled`() {
        val ravi = ravi()
        record(ravi, DebtKind.Lent, 30_000)
        form(ravi, DebtKind.RepaidToMe, 30_000)
        compose.onNodeWithText("Ravi paid you back").assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        waitClosed()
        assertEquals(30_000L, closedWith?.amount)
        assertTrue(finance.state.value.debtBalances.isEmpty(), "all settled")
        val tx = runBlocking { finance.repository.transactionById(closedWith!!.transactionId!!) }!!
        assertEquals(TransactionType.Income, tx.type)
        assertEquals("Loan repaid to me", finance.state.value.category(tx.categoryId)?.name)
    }

    @Test
    fun `the People page adds up what is owed and says it on each person's row`() {
        val ravi = ravi()
        val asha = runBlocking { finance.savePerson(Person(name = "Asha", createdAt = now, updatedAt = now)) }
        record(ravi, DebtKind.Lent, 50_000)
        record(asha, DebtKind.Borrowed, 20_000)
        compose.setContent { env.Content { PeoplePage() } }
        compose.waitForIdle()

        // A row is said as one sentence, so its printed words are in the unmerged tree; the sentence carries the line too.
        compose.onNodeWithText("Owes you ₹500.00", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("You owe them ₹200.00", useUnmergedTree = true).assertExists()
        assertTrue(described("Ravi", "Owes you ₹500.00"))
        assertTrue(described("Asha", "You owe them ₹200.00"))
        assertTrue(described("Owed to you ₹500.00. You owe ₹200.00."), "the banner")
    }
}
