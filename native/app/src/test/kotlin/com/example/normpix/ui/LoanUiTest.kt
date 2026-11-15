package com.example.normpix.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.LoanEmi
import com.example.normpix.finance.domain.LoanTerms
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.emiFor
import com.example.normpix.finance.domain.loanOutlook
import com.example.normpix.finance.domain.nextEmiIndex
import com.example.normpix.finance.ui.AccountFormSheet
import com.example.normpix.finance.ui.AccountHistoryPage
import com.example.normpix.finance.ui.RecordEmiSheet
import com.example.normpix.finance.ui.SCHEDULE_PREVIEW
import com.example.normpix.finance.ui.monthYear
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
 * Loans on screen (2026-11-15): the account form's Loan kind and its "This is a loan" switch with the loan details and their
 * messages; a loan's page with its summary, rows and schedule; the Record EMI sheet, pre-filled and saving both entries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class LoanUiTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now()
    private val today = LocalDate.now()
    private val tenLakh = 100_000_000L

    /** Rs 10,00,000 at 9% for 240 months, the first EMI a month from today: nothing is paid yet. */
    private val terms = LoanTerms(tenLakh, 900, 240, emiFor(tenLakh, 900, 240), today.plusMonths(1))

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("loan_ui_test")
    }

    @After
    fun tearDown() = env.close()

    private val finance get() = env.container.finance

    private fun saveAccount(name: String, kind: AccountKind, opening: Long = 0, loan: LoanTerms? = null): Account = runBlocking {
        finance.saveAccount(Account(name = name, kind = kind, openingBalance = opening, createdAt = now, updatedAt = now, loan = loan))
    }

    private fun homeLoan(opening: Long = -tenLakh) = saveAccount("Home loan", AccountKind.Loan, opening, terms)

    private fun accounts(): List<Account> = runBlocking { finance.repository.accounts() }

    private fun showForm(existing: Account?, onDone: (Account?) -> Unit = {}) {
        compose.setContent { env.Content { AccountFormSheet(existing, onDone) } }
        compose.waitForIdle()
    }

    private fun chooseType(label: String) {
        compose.onNodeWithText("Type").performClick()
        compose.waitForIdle()
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        compose.waitForIdle()
    }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun showsText(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun slideToSaveNewAccount() =
        compose.onNodeWithContentDescription("Slide to save account").performSemanticsAction(SemanticsActions.OnClick)

    // =========================================================================================== the account form

    @Test
    fun `the Loan kind is a choice of Type, and shows the loan details with owed turned on`() {
        showForm(null)
        assertEquals(0, count("Loan details"))
        compose.onAllNodesWithText("This is a loan").assertCountEquals(1)

        chooseType("Loan")

        compose.onNodeWithText("Loan details").assertIsDisplayed()
        compose.onNodeWithText("Amount borrowed").assertIsDisplayed()
        compose.onNodeWithText("Interest a year").assertIsDisplayed()
        compose.onNodeWithText("EMI").assertIsDisplayed()
        compose.onNodeWithText("Months").assertIsDisplayed()
        compose.onNodeWithText("EMI a month").assertIsDisplayed()
        compose.onNodeWithContentDescription("First EMI date", substring = true).assertIsDisplayed()
        // It is a loan already: no switch to turn it into one, and it is owed whatever the switch below says.
        compose.onAllNodesWithText("This is a loan").assertCountEquals(0)
        compose.onNodeWithText("A loan is always owed").assertIsDisplayed()
    }

    @Test
    fun `any account but a card can be made a loan with the switch, which turns owed on and says so`() {
        showForm(saveAccount("Old car loan", AccountKind.Bank, opening = 0))
        assertEquals(0, count("Loan details"))
        compose.onNodeWithText("Turn on for money you repay in EMIs. It is counted as owed.").assertIsDisplayed()

        compose.onNodeWithText("This is a loan").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Loan details").assertIsDisplayed()
        compose.onNodeWithText("A loan is always owed, so \"I owe this amount\" is on. Fill in its details below.").assertIsDisplayed()
        compose.onNodeWithText("A loan is always owed").assertIsDisplayed()

        compose.onNodeWithText("This is a loan").performClick()
        compose.waitForIdle()
        assertEquals(0, count("Loan details"), "turned off again, the details go")
    }

    @Test
    fun `a card has no loan switch, and cash does`() {
        showForm(saveAccount("HDFC Credit", AccountKind.Card))
        compose.onAllNodesWithText("This is a loan").assertCountEquals(0)
        assertEquals(0, count("Loan details"))
    }

    @Test
    fun `cash can be a loan too`() {
        showForm(accounts().first { it.kind == AccountKind.Cash })
        compose.onAllNodesWithText("This is a loan").assertCountEquals(1)
    }

    @Test
    fun `an empty loan says what is missing under each field`() {
        showForm(null)
        compose.onNodeWithText("Name").performTextInput("Home loan")
        chooseType("Loan")

        slideToSaveNewAccount()
        compose.waitForIdle()

        compose.onNodeWithText("Enter the amount you borrowed").assertIsDisplayed()
        compose.onNodeWithText("Enter the interest a year (0 if there is none)").assertIsDisplayed()
        compose.onNodeWithText("Enter the EMI").assertIsDisplayed()
        assertEquals(1, accounts().size, "nothing was saved: only Cash")
    }

    private fun fillLoanExceptRate(rate: String) {
        showForm(null)
        compose.onNodeWithText("Name").performTextInput("Home loan")
        chooseType("Loan")
        compose.onNodeWithText("Amount borrowed").performTextInput("1000000")
        compose.onNodeWithText("EMI a month").performTextInput("9000")
        compose.onNodeWithText("Interest a year").performTextInput(rate)
        slideToSaveNewAccount()
        compose.waitForIdle()
    }

    @Test
    fun `a rate that is no rate is refused`() {
        fillLoanExceptRate("8.755")
        compose.onNodeWithText("Enter a rate such as 9 or 10.5").assertIsDisplayed()
        assertEquals(1, accounts().size)
    }

    @Test
    fun `a rate above a hundred is refused`() {
        fillLoanExceptRate("101")
        compose.onNodeWithText("Enter a rate from 0 to 100").assertIsDisplayed()
        assertEquals(1, accounts().size)
    }

    @Test
    fun `an EMI that does not cover the first month's interest is refused, with that interest`() {
        showForm(null)
        compose.onNodeWithText("Name").performTextInput("Home loan")
        chooseType("Loan")
        compose.onNodeWithText("Amount borrowed").performTextInput("1000000")
        compose.onNodeWithText("Interest a year").performTextInput("9")
        compose.onNodeWithText("EMI a month").performTextInput("5000")
        compose.waitForIdle()

        val message = "That EMI is less than the first month's interest (₹7,500.00). Enter a bigger EMI."
        assertEquals(0, count(message), "said under the field once Save is tried, not while it is typed")
        slideToSaveNewAccount()
        compose.waitForIdle()
        compose.onNodeWithText(message).assertIsDisplayed()
        assertEquals(1, accounts().size)
    }

    @Test
    fun `the months an EMI takes are worked out as it is typed`() {
        showForm(null)
        chooseType("Loan")
        compose.onNodeWithText("Amount borrowed").performTextInput("1000000")
        compose.onNodeWithText("Interest a year").performTextInput("9")
        compose.onNodeWithText("EMI a month").performTextInput("8997.26")
        compose.waitForIdle()
        compose.onNodeWithText("Paid off in 240 months").assertIsDisplayed()
    }

    @Test
    fun `or the EMI is worked out from the months`() {
        showForm(null)
        chooseType("Loan")
        compose.onNodeWithText("Amount borrowed").performTextInput("1000000")
        compose.onNodeWithText("Interest a year").performTextInput("9")
        compose.onNodeWithText("Months").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Months to pay").performTextInput("240")
        compose.waitForIdle()
        compose.onNodeWithText("EMI ₹8,997.26 a month").assertIsDisplayed()
    }

    @Test
    fun `more than fifty years of months is refused`() {
        showForm(null)
        compose.onNodeWithText("Name").performTextInput("Home loan")
        chooseType("Loan")
        compose.onNodeWithText("Amount borrowed").performTextInput("1000000")
        compose.onNodeWithText("Interest a year").performTextInput("9")
        compose.onNodeWithText("Months").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Months to pay").performTextInput("700")
        slideToSaveNewAccount()
        compose.waitForIdle()
        compose.onNodeWithText("Enter a number of months from 1 to 600").assertIsDisplayed()
        assertEquals(1, accounts().size)
    }

    @Test
    fun `a new loan is saved with its terms, owed from the amount borrowed`() {
        var saved: Account? = null
        showForm(null) { saved = it }
        compose.onNodeWithText("Name").performTextInput("Home loan")
        chooseType("Loan")
        compose.onNodeWithText("Amount borrowed").performTextInput("1000000")
        compose.onNodeWithText("Interest a year").performTextInput("9")
        compose.onNodeWithText("EMI a month").performTextInput("8997.26")
        compose.waitForIdle()

        slideToSaveNewAccount()
        compose.waitUntil(10_000) { saved != null }

        val loan = assertNotNull(assertNotNull(saved).loan)
        assertEquals(AccountKind.Loan, saved!!.kind)
        assertEquals(tenLakh, loan.principal)
        assertEquals(900, loan.rateBp)
        assertEquals(240, loan.tenureMonths, "the months the EMI takes")
        assertEquals(899_726L, loan.emi)
        assertEquals(LocalDate.now().plusMonths(1), loan.start, "the first EMI a month from today, as the form starts")
        assertEquals(-tenLakh, saved!!.openingBalance, "owed what was borrowed, without typing the starting balance")
        val stored = accounts().first { it.name == "Home loan" }
        assertEquals(loan, stored.loan)
        assertEquals(-tenLakh, runBlocking { finance.repository.accountBalances() }[stored.id])
    }

    @Test
    fun `a loan made from the months saves the EMI they work out to`() {
        var saved: Account? = null
        showForm(null) { saved = it }
        compose.onNodeWithText("Name").performTextInput("Car loan")
        chooseType("Loan")
        compose.onNodeWithText("Amount borrowed").performTextInput("1000000")
        compose.onNodeWithText("Interest a year").performTextInput("9")
        compose.onNodeWithText("Months").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Months to pay").performTextInput("240")
        compose.waitForIdle()

        slideToSaveNewAccount()
        compose.waitUntil(10_000) { saved != null }

        assertEquals(LoanTerms(tenLakh, 900, 240, 899_726L, LocalDate.now().plusMonths(1)), saved!!.loan)
    }

    @Test
    fun `a typed starting balance is kept, not overwritten by the amount borrowed`() {
        var saved: Account? = null
        showForm(null) { saved = it }
        compose.onNodeWithText("Name").performTextInput("Home loan")
        chooseType("Loan")
        // An older loan: some of it is paid off already, so what is owed now is not what was borrowed.
        compose.onNodeWithContentDescription("Starting balance in INR").performTextInput("750000")
        compose.onNodeWithText("Amount borrowed").performTextInput("1000000")
        compose.onNodeWithText("Interest a year").performTextInput("9")
        compose.onNodeWithText("EMI a month").performTextInput("8997.26")
        compose.waitForIdle()

        slideToSaveNewAccount()
        compose.waitUntil(10_000) { saved != null }

        assertEquals(-75_000_000L, saved!!.openingBalance)
        assertEquals(tenLakh, saved!!.loan!!.principal)
    }

    @Test
    fun `an account made a loan with the switch keeps its kind and gets its terms`() {
        val old = saveAccount("Old car loan", AccountKind.Bank, opening = -30_000_000L)
        var saved: Account? = null
        showForm(old) { saved = it }
        compose.onNodeWithText("This is a loan").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Amount borrowed").performTextInput("500000")
        compose.onNodeWithText("Interest a year").performTextInput("10.5")
        compose.onNodeWithText("Months").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Months to pay").performTextInput("36")
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Slide to save changes").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { saved != null }

        assertEquals(AccountKind.Bank, saved!!.kind)
        val loan = assertNotNull(saved!!.loan)
        assertEquals(50_000_000L, loan.principal)
        assertEquals(1050, loan.rateBp)
        assertEquals(36, loan.tenureMonths)
        assertEquals(-30_000_000L, saved!!.openingBalance, "what was owed is what is owed: the balance is not reset to the amount borrowed")
    }

    @Test
    fun `editing a loan shows its terms, and nothing changed closes as it was`() {
        val loan = homeLoan()
        var saved: Account? = null
        showForm(loan) { saved = it }

        compose.onNodeWithText("Loan details").assertIsDisplayed()
        assertTrue(showsText("1000000"), "the amount borrowed")
        assertTrue(showsText("9"), "the rate")
        assertTrue(showsText("8997.26"), "the EMI")
        compose.onNodeWithText("Paid off in 240 months").assertIsDisplayed()

        compose.onNodeWithContentDescription("Slide to save changes").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { saved != null }

        assertEquals(terms, saved!!.loan)
        assertEquals(-tenLakh, saved!!.openingBalance)
    }

    // ========================================================================================== the loan's page

    private fun showPage(id: Long) {
        compose.setContent { env.Content { AccountHistoryPage(id) } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("still to pay", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    /** The banner's panels are drawn, not read one by one (one description, see `SplitStatBanner`): their words are in the unmerged tree. */
    private fun bannerText(text: String) = compose.onNodeWithText(text, useUnmergedTree = true)

    private fun bank(): Account = saveAccount("HDFC Savings", AccountKind.Bank, opening = 50_000_000L)

    private fun interestCategory() = runBlocking { finance.repository.categories() }.first { it.name == "Loans & EMI" && it.parentId == null }.id

    private fun recordEmi(loan: Account, from: Account, at: LocalDateTime, principal: Long, interest: Long) = runBlocking {
        finance.recordLoanEmi(LoanEmi(loan.id!!, from.id!!, at, principal, interest, interestCategory(), loan.name))
    }

    @Test
    fun `a loan's page opens with what is still to pay and the months left`() {
        val loan = homeLoan()
        showPage(loan.id!!)

        val outlook = assertNotNull(loanOutlook(terms, tenLakh, nextEmiIndex(terms, null, today)))
        bannerText("Still to pay").assertIsDisplayed()
        bannerText("₹10,00,000.00").assertIsDisplayed()
        bannerText("of ₹10,00,000 borrowed").assertIsDisplayed()
        bannerText("Months left").assertIsDisplayed()
        bannerText("240").assertIsDisplayed()
        bannerText("Paid off ${monthYear(outlook.payoff!!)}").assertIsDisplayed()
        compose.onNode(hasContentDescription("Home loan: ₹10,00,000.00 still to pay, 240 months left, Paid off ${monthYear(outlook.payoff!!)}.")).assertIsDisplayed()
    }

    @Test
    fun `the rows say the EMI, the rate, the interest still to pay and the next EMI as principal and interest`() {
        val loan = homeLoan()
        showPage(loan.id!!)

        val outlook = assertNotNull(loanOutlook(terms, tenLakh, 0))
        val next = outlook.next!!
        compose.onNodeWithText("EMI").assertIsDisplayed()
        compose.onNodeWithText("₹8,997.26 a month").assertIsDisplayed()
        compose.onNodeWithText("Interest a year").assertIsDisplayed()
        compose.onNodeWithText("9%").assertIsDisplayed()
        compose.onNodeWithText("Interest still to pay").assertIsDisplayed()
        compose.onNodeWithText("₹11,59,342.12").assertIsDisplayed()
        compose.onNodeWithText("Next EMI").assertIsDisplayed()
        compose.onNodeWithText("₹8,997.26").assertIsDisplayed()
        compose.onNodeWithText("₹1,497.26 principal + ₹7,500.00 interest, on ", substring = true).assertIsDisplayed()
        assertEquals(149_726L, next.principal)
        assertEquals(750_000L, next.interest)
    }

    @Test
    fun `the page has Record EMI and says to record each EMI there`() {
        val loan = homeLoan()
        showPage(loan.id!!)
        compose.onNodeWithText("Record EMI").assertIsDisplayed()
        compose.onNodeWithText("Record each EMI here when it's paid.").assertIsDisplayed()
    }

    @Test
    fun `the schedule shows a few months at first and all of them on Show all`() {
        val loan = homeLoan()
        showPage(loan.id!!)

        compose.onNodeWithText("Schedule").assertIsDisplayed()
        assertEquals(SCHEDULE_PREVIEW, count("left"), "the coming months, each with what is left after it")
        compose.onNodeWithText("₹1,497.26 principal\n₹7,500.00 interest").assertIsDisplayed()
        compose.onNodeWithText("Show all").performClick()
        compose.waitForIdle()
        assertEquals(240, count("left"))
        compose.onNodeWithText("Show fewer").performClick()
        compose.waitForIdle()
        assertEquals(SCHEDULE_PREVIEW, count("left"))
    }

    @Test
    fun `an EMI recorded brings the figures down by the principal`() {
        val loan = homeLoan()
        val from = bank()
        recordEmi(loan, from, LocalDateTime.of(today.minusDays(3), LocalTime.of(10, 0)), 149_726, 750_000)
        showPage(loan.id!!)

        bannerText("₹9,98,502.74").assertIsDisplayed()
        val last = LocalDateTime.of(today.minusDays(3), LocalTime.of(10, 0)).toLocalDate()
        val outlook = assertNotNull(loanOutlook(terms, tenLakh - 149_726, nextEmiIndex(terms, last, today)))
        bannerText("Paid off ${monthYear(outlook.payoff!!)}").assertIsDisplayed()
        bannerText("${outlook.monthsLeft}").assertIsDisplayed()
    }

    @Test
    fun `a paid off loan says so and has no Record EMI`() {
        val loan = homeLoan(opening = 0)
        showPage(loan.id!!)
        bannerText("Paid off").assertIsDisplayed()
        bannerText("0").assertIsDisplayed()
        compose.onAllNodesWithText("Record EMI").assertCountEquals(0)
        compose.onAllNodesWithText("Schedule").assertCountEquals(0)
    }

    @Test
    fun `an account that is not a loan has none of it`() {
        val plain = bank()
        compose.setContent { env.Content { AccountHistoryPage(plain.id!!) } }
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("still to pay", substring = true)).assertCountEquals(0)
        compose.onAllNodesWithText("Record EMI").assertCountEquals(0)
    }

    @Test
    fun `hidden amounts are hidden on a loan's page too`() {
        val loan = homeLoan()
        env.preferences.setAmountsHidden(true)
        showPage(loan.id!!)
        assertEquals(0, count("₹10,00,000.00"))
        assertEquals(0, count("₹8,997.26 a month"))
        bannerText("Still to pay").assertIsDisplayed()
    }

    @Test
    fun `a loan made of another kind has the same page`() {
        val old = saveAccount("Old car loan", AccountKind.Bank, opening = -5_000_000L, loan = terms.copy(principal = 5_000_000L, emi = 100_000L))
        showPage(old.id!!)
        bannerText("Still to pay").assertIsDisplayed()
        bannerText("₹50,000.00").assertIsDisplayed()
    }

    // ========================================================================================== the Record EMI sheet

    private fun showSheet(loan: Account, last: FinanceTransaction? = null, onDone: (Boolean?) -> Unit = {}) {
        compose.setContent { env.Content { RecordEmiSheet(loan, last, onDone) } }
        compose.waitForIdle()
    }

    @Test
    fun `the sheet starts from what the schedule says is next, and says what the two parts are`() {
        val loan = homeLoan()
        bank()
        showSheet(loan)

        compose.onNodeWithText("Record EMI").assertIsDisplayed()
        compose.onNodeWithText("The principal lowers what you owe; the interest is spending.").assertIsDisplayed()
        assertTrue(showsText("1497.26"), "the principal part, filled in")
        assertTrue(showsText("7500"), "the interest part, filled in")
        compose.onNodeWithText("That is the EMI, ₹8,997.26.").assertIsDisplayed()
        compose.onNodeWithText("Record ₹8,997.26").assertIsDisplayed()
        compose.onNodeWithText("Loans & EMI").assertIsDisplayed()
        compose.onNodeWithContentDescription("Date", substring = true).assertIsDisplayed()
    }

    @Test
    fun `it is paid from the default account when no EMI has been paid yet`() {
        val loan = homeLoan()
        bank()
        val other = saveAccount("SBI Savings", AccountKind.Bank, opening = 1_000_000L)
        runBlocking { finance.setDefaultAccountId(other.id) }
        showSheet(loan)
        compose.onNodeWithText("SBI Savings").assertIsDisplayed()
    }

    @Test
    fun `the last EMI's account is where it starts`() {
        val loan = homeLoan()
        val from = bank()
        val other = saveAccount("SBI Savings", AccountKind.Bank, opening = 1_000_000L)
        runBlocking { finance.setDefaultAccountId(other.id) }
        recordEmi(loan, from, LocalDateTime.of(today.minusDays(40), LocalTime.of(10, 0)), 149_726, 750_000)
        val last = runBlocking { finance.repository.lastLoanPayment(loan.id!!, LocalDateTime.now().plusDays(1)) }
        showSheet(loan, last)
        compose.onNodeWithText("HDFC Savings").assertIsDisplayed()
        assertEquals(0, count("SBI Savings"))
    }

    @Test
    fun `saving makes the transfer and the expense, and the sheet closes`() {
        val loan = homeLoan()
        val from = bank()
        runBlocking { finance.setDefaultAccountId(from.id) }
        var done: Boolean? = null
        showSheet(loan) { done = it }

        compose.onNodeWithText("Record ₹8,997.26").performClick()
        compose.waitUntil(10_000) { done != null }

        assertEquals(true, done)
        val all = runBlocking { finance.repository.transactions(TransactionQuery(), limit = 10).items }
        assertEquals(2, all.size)
        val transfer = all.single { it.type == TransactionType.Transfer }
        assertEquals(149_726L, transfer.amount)
        assertEquals(from.id, transfer.accountId)
        assertEquals(loan.id, transfer.toAccountId)
        assertEquals("EMI ${monthYearOf(transfer.occurredAt)} - principal", transfer.note)
        assertEquals(today, transfer.occurredAt.toLocalDate())
        val expense = all.single { it.type == TransactionType.Expense }
        assertEquals(750_000L, expense.amount)
        assertEquals(from.id, expense.accountId)
        assertEquals(interestCategory(), expense.categoryId)
        assertEquals("EMI ${monthYearOf(expense.occurredAt)} - interest", expense.note)
        // What is owed fell by the principal only.
        assertEquals(-tenLakh + 149_726L, runBlocking { finance.repository.accountBalances() }[loan.id])
    }

    private fun monthYearOf(at: LocalDateTime) = monthYear(at.toLocalDate())

    @Test
    fun `either part can be changed, and the sheet says how that stands against the EMI`() {
        val loan = homeLoan()
        bank()
        var done: Boolean? = null
        showSheet(loan) { done = it }

        compose.onNodeWithText("Principal").performTextClearance()
        compose.onNodeWithText("Principal").performTextInput("2000")
        compose.waitForIdle()
        compose.onNodeWithText("That is ₹502.74 more than the EMI of ₹8,997.26.").assertIsDisplayed()
        compose.onNodeWithText("Interest").performTextClearance()
        compose.onNodeWithText("Interest").performTextInput("6000")
        compose.waitForIdle()
        compose.onNodeWithText("That is ₹997.26 less than the EMI of ₹8,997.26.").assertIsDisplayed()
        compose.onNodeWithText("Record ₹8,000.00").performClick()
        compose.waitUntil(10_000) { done != null }

        val all = runBlocking { finance.repository.transactions(TransactionQuery(), limit = 10).items }
        assertEquals(2_000_00L, all.single { it.type == TransactionType.Transfer }.amount)
        assertEquals(6_000_00L, all.single { it.type == TransactionType.Expense }.amount)
    }

    @Test
    fun `nothing entered is refused in the sheet and nothing is saved`() {
        val loan = homeLoan()
        bank()
        var done: Boolean? = null
        showSheet(loan) { done = it }

        compose.onNodeWithText("Principal").performTextClearance()
        compose.onNodeWithText("Interest").performTextClearance()
        compose.waitForIdle()
        compose.onNode(hasText("Record EMI") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Enter the principal, the interest, or both.").assertIsDisplayed()
        assertNull(done)
        assertTrue(runBlocking { finance.repository.transactions(TransactionQuery(), limit = 10).items }.isEmpty())
    }

    @Test
    fun `a loan with no interest records only the principal and asks for no category`() {
        val loan = saveAccount("Friend loan", AccountKind.Loan, -1_000_000L, terms.copy(principal = 1_000_000L, rateBp = 0, tenureMonths = 10, emi = 100_000L))
        bank()
        var done: Boolean? = null
        showSheet(loan) { done = it }

        assertEquals(0, count("Category"), "no interest, no category")
        compose.onNodeWithText("Record ₹1,000.00").performClick()
        compose.waitUntil(10_000) { done != null }

        val all = runBlocking { finance.repository.transactions(TransactionQuery(), limit = 10).items }
        assertEquals(listOf(TransactionType.Transfer), all.map { it.type })
    }

    @Test
    fun `the page's Record EMI button opens the sheet`() {
        val loan = homeLoan()
        bank()
        showPage(loan.id!!)
        compose.onNodeWithText("Record EMI").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("The principal lowers what you owe; the interest is spending.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("The principal lowers what you owe; the interest is spending.").assertIsDisplayed()
    }

}
