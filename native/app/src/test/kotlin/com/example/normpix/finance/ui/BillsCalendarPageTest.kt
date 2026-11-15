package com.example.normpix.finance.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.BillKind
import com.example.normpix.finance.domain.CardBill
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.DayMark
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.reminders.Reminder
import com.example.normpix.reminders.ReminderCategory
import com.example.normpix.reminders.ReminderLink
import com.example.normpix.reminders.RepeatKind
import com.example.normpix.reminders.RepeatRule
import com.example.normpix.screens.DueBill
import com.example.normpix.screens.HomeScreen
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 * The Bills calendar page: a month grid with dots on the days that have something (each day reads its date and how many things are due),
 * the chosen day's things listed under it - a card's bill, repeating payments, an entry dated ahead, reminders - and a tap on one opening
 * what it is about; the summary banner; changing month inside the range; amounts hidden; and the two places that open it. The page is shown
 * "today" two months on from the real day (the 10th), so every rule, entry and reminder it holds is still ahead of the real clock - nothing
 * is recorded by itself - whatever day the tests run.
 *
 * The month: Rent 24,000 on the 12th, Employer 50,000 on the 28th, four payments on the 25th (Netflix 649, Gym 2,000, Water 500, Maid
 * 4,500), a SIP of 5,000 into savings on the 6th (before "today", so it is marked and listed but not in this month's summary), a payment of 10,000 onto the ICICI card on the 19th, an entry Croma 3,500 on the 18th, the
 * card's bill of 12,000 on the 20th (with a reminder about the card that day, folded into it), a reminder on the 22nd, and one about Rent on the 14th.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h4000dp")
@OptIn(ExperimentalTestApi::class)
class BillsCalendarPageTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now()
    private val today = LocalDate.now().plusMonths(2).withDayOfMonth(10)
    private val month = YearMonth.from(today)
    private lateinit var card: Account

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("bills_calendar_page")
    }

    @After
    fun tearDown() = env.close()

    private fun on(day: Int, of: YearMonth = month): LocalDate = of.atDay(day)

    private fun rule(type: TransactionType, amount: Long, day: Int, payee: String, account: Long, to: Long? = null, category: Long? = null) =
        RecurringRule(
            type = type,
            amount = amount,
            accountId = account,
            toAccountId = to,
            categoryId = category,
            payee = payee,
            frequency = RecurrenceFrequency.Monthly,
            startDate = on(day).atTime(9, 0),
            nextDate = on(day).atTime(9, 0),
            createdAt = now,
            updatedAt = now,
        )

    private fun reminder(title: String, due: LocalDateTime, link: ReminderLink? = null) = runBlocking {
        env.container.reminders.addReminder(
            Reminder(
                title = title,
                category = ReminderCategory.Payments,
                dueAt = due,
                repeatRule = RepeatRule(RepeatKind.Once),
                link = link,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** The scenario in the class comment; returns the card's bill the way Home reads it. */
    private fun seed(): List<DueBill> = runBlocking {
        val finance = env.container.finance
        val bank = finance.saveAccount(Account(name = "HDFC", kind = AccountKind.Bank, openingBalance = 10_000_000, createdAt = now, updatedAt = now))
        val jar = finance.saveAccount(Account(name = "Savings jar", kind = AccountKind.Savings, createdAt = now, updatedAt = now))
        card = finance.saveAccount(Account(name = "ICICI card", kind = AccountKind.Card, statementDay = 5, dueDay = 20, createdAt = now, updatedAt = now))
        val expense = finance.state.value.categories.first { it.type == CategoryType.Expense }.id
        val income = finance.state.value.categories.first { it.type == CategoryType.Income }.id
        val rent = finance.saveRecurringRule(rule(TransactionType.Expense, 2_400_000, 12, "Rent", bank.id!!, category = expense))
        finance.saveRecurringRule(rule(TransactionType.Income, 5_000_000, 28, "Employer", bank.id!!, category = income))
        finance.saveRecurringRule(rule(TransactionType.Expense, 64_900, 25, "Netflix", bank.id!!, category = expense))
        finance.saveRecurringRule(rule(TransactionType.Expense, 200_000, 25, "Gym", bank.id!!, category = expense))
        finance.saveRecurringRule(rule(TransactionType.Expense, 50_000, 25, "Water", bank.id!!, category = expense))
        finance.saveRecurringRule(rule(TransactionType.Expense, 450_000, 25, "Maid", bank.id!!, category = expense))
        finance.saveRecurringRule(rule(TransactionType.Transfer, 500_000, 6, "SIP", bank.id!!, to = jar.id))
        finance.saveRecurringRule(rule(TransactionType.Transfer, 1_000_000, 19, "HDFC to card", bank.id!!, to = card.id))
        finance.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense,
                amount = 350_000,
                accountId = bank.id!!,
                categoryId = expense,
                occurredAt = on(18).atTime(12, 0),
                payee = "Croma",
                createdAt = now,
                updatedAt = now,
            ),
        )
        reminder("Renew insurance", on(22).atTime(9, 0))
        reminder("Pay ICICI", on(20).atTime(10, 0), ReminderLink.account(card.id!!))
        reminder("Rent is coming", on(14).atTime(9, 0), ReminderLink.recurring(rent.id!!))
        val due = on(20)
        listOf(DueBill(card, CardBill(BillKind.Statement, 1_200_000, 1_500_000, due, ChronoUnit.DAYS.between(today, due).toInt(), on(5), 0.5f)))
    }

    private fun show(bills: List<DueBill> = seed(), reduced: Boolean? = null) {
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides reduced) {
                env.Content { BillsCalendarPage(today = today, dueBills = bills) }
            }
        }
        waitFor("Legend") { compose.onAllNodesWithText("Money out", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.waitForIdle()
                condition()
            }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for $what: " + compose.onRoot().printToString(), e)
        }
    }

    private fun waitForText(text: String) = waitFor("\"$text\"") { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** What a day says to a screen reader: its date. */
    private fun dateOf(day: LocalDate) = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.US).format(day)

    private fun cell(day: LocalDate) = compose.onNode(hasContentDescription(dateOf(day), substring = true))

    private fun said(day: LocalDate): String =
        cell(day).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()

    private fun heading(day: LocalDate, withYear: Boolean = false) =
        "${DateTimeFormatter.ofPattern("EEEE", Locale.US).format(day)}, ${DateTimeFormatter.ofPattern(if (withYear) "d MMM yyyy" else "d MMM", Locale.US).format(day)}"

    private fun monthTitle(of: YearMonth) = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US).format(of)

    private fun monthName(of: YearMonth) =
        DateTimeFormatter.ofPattern(if (of.year == today.year) "MMMM" else "MMMM yyyy", Locale.US).format(of)

    private fun banner(spoken: String) = compose.onNode(hasContentDescription(spoken))

    // ----------------------------------------------------------------------------------------------- the page

    @Test
    fun itOpensOnTheCurrentMonthWithTodayChosen() {
        show()
        compose.onNodeWithText(monthTitle(month)).assertIsDisplayed()
        compose.onNodeWithText("Today, ${DateTimeFormatter.ofPattern("d MMM", Locale.US).format(today)}").assertIsDisplayed()
        compose.onNodeWithText("Nothing due on this day").assertIsDisplayed()
        assertTrue(said(today).let { it.contains("chosen") && it.contains("today") })
        cell(today).assertIsSelected()
    }

    @Test
    fun theSummaryAddsUpWhatGoesOutAndWhatComesInFromToday() {
        show()
        // Out: Rent 24,000, the card payment 10,000, the bill's 2,000 that is left, the four on the 25th 7,649, Croma 3,500 - not the SIP, which
        // was due on the 6th, before today.
        banner("Bills calendar, ${monthName(month)}. Due this month ₹47,149.00, from today. Coming in ₹50,000.00.").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Due this month", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("Coming in", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun eachDayReadsItsDateAndHowManyThingsAreDue() {
        show()
        assertTrue(said(on(12)).endsWith("1 thing due"), said(on(12)))
        assertTrue(said(on(25)).endsWith("4 things due"), said(on(25)))
        // The bill, with the reminder about the card folded into it.
        assertTrue(said(on(20)).endsWith("1 thing due"), said(on(20)))
        assertTrue(said(on(18)).endsWith("1 thing due"))
        assertTrue(said(on(22)).endsWith("1 thing due"))
        assertTrue(said(on(14)).endsWith("1 thing due"), "the reminder about Rent, on a day the rent is not")
        assertTrue(said(on(11)).endsWith("nothing due"), said(on(11)))
        assertTrue(said(on(1)).endsWith("nothing due"))
    }

    @Test
    fun theGridIsOneGridOfWeeksAndEveryDayAButtonInIt() {
        show()
        // The page's own list is a collection too (of one column): the month's grid is the one of seven.
        val grid = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)).fetchSemanticsNodes()
            .filter { it.config[SemanticsProperties.CollectionInfo].columnCount == 7 }
        assertEquals(1, grid.size)
        val info = grid.single().config[SemanticsProperties.CollectionInfo]
        assertEquals(7, info.columnCount)
        assertTrue(info.rowCount in 4..6, "as many weeks as the month has: ${info.rowCount}")
        val days = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionItemInfo)).fetchSemanticsNodes()
        assertEquals(month.lengthOfMonth(), days.size)
        assertTrue(days.all { it.config[SemanticsProperties.CollectionItemInfo].columnIndex in 0..6 })
    }

    @Test
    fun thePickerCalendarHasNoMarkersAndNoGrid() {
        compose.setContent { env.Content { DateSheet(initial = today, latest = today.plusYears(1), onPick = {}, today = today) } }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)).fetchSemanticsNodes().size)
        compose.onAllNodes(hasContentDescription("thing due", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("nothing due", substring = true)).assertCountEquals(0)
    }

    @Test
    fun moreThingsThanDotsIsAPlus_andTheDotsAreTheFirstThree() {
        val marks = DayMarks(listOf(DayMark.Out, DayMark.Out, DayMark.In), count = 5)
        assertTrue(marks.more)
        assertEquals("5 things due", marks.spoken)
        assertFalse(DayMarks(listOf(DayMark.Reminder), 1).more)
        assertEquals("1 thing due", DayMarks(listOf(DayMark.Reminder), 1).spoken)
        assertEquals("nothing due", DayMarks.None.spoken)
        assertEquals(MARKS_SHOWN, 3)
    }

    // ----------------------------------------------------------------------------------------------- the chosen day

    @Test
    fun aDayTouchedListsItsThingsInPlaceOfTodays() {
        show()
        cell(on(12)).performClick()
        waitForText(heading(on(12)))
        cell(on(12)).assertIsSelected()
        compose.onNodeWithText("Rent").assertIsDisplayed()
        compose.onNodeWithText("Repeating payment · HDFC").assertIsDisplayed()
        compose.onNodeWithText("Nothing due on this day").assertDoesNotExist()
        // The kind is in words as well as the dot, and the amount is on the row.
        assertTrue(compose.onAllNodes(hasText("24,000.00", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun theCardsBillIsListedOnceWithItsReminderFoldedIn() {
        show()
        cell(on(20)).performClick()
        waitForText("ICICI card bill")
        compose.onNodeWithText("Card bill · With a reminder").assertIsDisplayed()
        assertEquals(0, count("Pay ICICI"), "the reminder about the card is part of the bill, not a second row")
    }

    @Test
    fun aPaymentOntoACardSaysSo() {
        show()
        cell(on(19)).performClick()
        waitForText("HDFC to card")
        compose.onNodeWithText("Repeating card payment · HDFC → ICICI card").assertIsDisplayed()
    }

    @Test
    fun anEntryDatedAheadIsUpcomingAndAReminderAboutSomethingSaysWhatItIsAbout() {
        show()
        cell(on(18)).performClick()
        waitForText("Croma")
        compose.onNodeWithText("Upcoming payment · HDFC").assertIsDisplayed()
        cell(on(14)).performClick()
        waitForText("Rent is coming")
        // "Reminder · when · what it is about": the repeating payment it is tied to, with how often and how much.
        compose.onNode(hasText("Reminder", substring = true) and hasText("Rent · Every month · ₹24,000.00", substring = true)).assertIsDisplayed()
    }

    @Test
    fun aReminderIsAReminderWithNoAmount() {
        show()
        cell(on(22)).performClick()
        waitForText("Renew insurance")
        // Where an amount would be, the row says "Reminder"; no figure is on it.
        assertEquals(1, count("Reminder"))
        assertEquals(0, compose.onAllNodes(hasText("Renew insurance") and hasText("₹", substring = true)).fetchSemanticsNodes().size)
    }

    // ----------------------------------------------------------------------------------------------- opening what a tap is about

    @Test
    fun aRepeatingPaymentOpensItsEditor() {
        show()
        cell(on(12)).performClick()
        waitForText("Rent")
        compose.onNodeWithText("Rent").performClick()
        waitForText("Edit recurring expense")
    }

    @Test
    fun theCardsBillOpensThePayBillSheetAtWhatIsDue() {
        show()
        cell(on(20)).performClick()
        waitForText("ICICI card bill")
        compose.onNodeWithText("ICICI card bill").performClick()
        waitForText("Pay ICICI card")
    }

    @Test
    fun anEntryDatedAheadOpensItsDetails() {
        show()
        cell(on(18)).performClick()
        waitForText("Croma")
        compose.onNodeWithText("Croma").performClick()
        // The details sheet's own buttons.
        waitForText("Delete")
        assertTrue(count("Edit") >= 1)
    }

    @Test
    fun aReminderOpensItsSheet() {
        show()
        cell(on(22)).performClick()
        waitForText("Renew insurance")
        compose.onNodeWithText("Renew insurance").performClick()
        waitForText("Edit reminder")
    }

    // ----------------------------------------------------------------------------------------------- months

    @Test
    fun theNextMonthOpensOnItsFirstDayWithSomethingAndSaysItsOwnTotals() {
        show()
        val next = month.plusMonths(1)
        compose.onNodeWithContentDescription("Next month").performClick()
        waitForText(monthTitle(next))
        // The SIP of the 6th is the first thing of the month; the whole month counts: no bill, the rent, the SIP, the card payment and the
        // four on the 25th, and the salary (the entry and the bill are this month's).
        waitForText(heading(on(6, next), withYear = next.year != today.year))
        cell(on(6, next)).assertIsSelected()
        banner("Bills calendar, ${monthName(next)}. Due this month ₹46,649.00, in ${monthName(next)}. Coming in ₹50,000.00.").assertIsDisplayed()
        // Back to this month: today again.
        compose.onNodeWithContentDescription("Previous month").performClick()
        waitForText("Today, ${DateTimeFormatter.ofPattern("d MMM", Locale.US).format(today)}")
        cell(today).assertIsSelected()
    }

    @Test
    fun itGoesOneMonthBackAndTwelveAhead() {
        show()
        compose.onNodeWithContentDescription("Previous month").performClick()
        waitForText(monthTitle(month.minusMonths(1)))
        compose.onNodeWithContentDescription("Previous month").assertIsNotEnabled()
        repeat(13) { compose.onNodeWithContentDescription("Next month").performClick() }
        waitForText(monthTitle(month.plusMonths(12)))
        compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
    }

    @Test
    fun aMonthChangeIsAnimated() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        // The month that is leaving is still there beside the one coming (the test that says "not with reduced motion" can fail).
        assertEquals(1, compose.onAllNodes(hasContentDescription(dateOf(on(12)), substring = true)).fetchSemanticsNodes().size)
        assertTrue(compose.onAllNodes(hasContentDescription(dateOf(on(12, month.plusMonths(1))), substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun withReducedMotionAMonthChangeIsInstant() {
        show(reduced = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0, compose.onAllNodes(hasContentDescription(dateOf(on(12)), substring = true)).fetchSemanticsNodes().size, "no month is left sliding out")
        assertTrue(compose.onAllNodes(hasContentDescription(dateOf(on(12, month.plusMonths(1))), substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    // ----------------------------------------------------------------------------------------------- hidden amounts, empty

    @Test
    fun withAmountsHiddenNoFigureIsShownOrSaid() {
        env.preferences.setAmountsHidden(true)
        show()
        banner("Bills calendar, ${monthName(month)}. Due this month ₹••••, from today. Coming in ₹••••.").assertIsDisplayed()
        cell(on(12)).performClick()
        waitForText(heading(on(12)))
        assertEquals(0, compose.onAllNodes(hasText("24,000", substring = true)).fetchSemanticsNodes().size)
        assertTrue(compose.onAllNodes(hasText("••••", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aCalendarWithNothingOnItStillOpens() {
        show(bills = emptyList())
        compose.onNodeWithText("Nothing due on this day").assertIsDisplayed()
        banner("Bills calendar, ${monthName(month)}. Due this month ₹0.00, from today. Coming in ₹0.00.").assertIsDisplayed()
    }

    // ----------------------------------------------------------------------------------------------- the two doors

    @Test
    fun theBillsCalendarIsOnRecurringBesideTheForecast() {
        compose.setContent { env.Content { RecurringView() } }
        waitForText("Bills calendar")
        compose.onNodeWithText("Card bills, repeating payments and reminders by day").assertIsDisplayed()
        assertEquals(0, env.navigator.billsCalendarOpened)
        compose.onNodeWithText("Bills calendar").performClick()
        assertEquals(1, env.navigator.billsCalendarOpened)
        assertEquals(0, env.navigator.forecastOpened)
    }

    @Test
    fun homesNextSevenDaysHasACalendarActionBesidePlans() {
        reminder("Pay the milkman", LocalDateTime.now().plusDays(2))
        compose.setContent { env.Content { HomeScreen() } }
        waitForText("Next 7 days")
        assertTrue(count("Plans") >= 1)
        assertEquals(0, env.navigator.billsCalendarOpened)
        compose.onNodeWithText("Calendar").performClick()
        assertEquals(1, env.navigator.billsCalendarOpened)
        assertTrue(env.navigator.plansSections.isEmpty(), "the calendar action is not the Plans one")
    }
}
