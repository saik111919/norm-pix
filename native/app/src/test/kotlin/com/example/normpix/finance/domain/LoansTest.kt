package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The loan maths (`Loans.kt`): the EMI, the schedule, what is left from a real outstanding, the EMI that is too small, which EMI is
 * next, the rate as typed and the two entries one EMI makes.
 *
 * The expected figures were worked out apart from the code, in Python with the decimal module at 60 digits and each step rounded half up
 * to a paisa the way the code says (not read back from it): Rs 10,00,000 at 9% for 240 months is an EMI of 899,725.9558... paise, so
 * Rs 8,997.26 (899,726 paise), and clears with 240 months of which the first is 149,726 principal + 750,000 interest and the last
 * 893,000 + 6,698.
 */
class LoansTest {
    private val tenLakh = 100_000_000L

    private fun terms(
        principal: Long = tenLakh,
        rateBp: Int = 900,
        months: Int = 240,
        emi: Long = emiFor(principal, rateBp, months),
        start: LocalDate = LocalDate.of(2026, 12, 5),
    ) = LoanTerms(principal, rateBp, months, emi, start)

    // ------------------------------------------------------------------------------------------------------- the EMI

    @Test
    fun anEmiOfTenLakhAtNinePercentForTwentyYearsIsRs8997_26() {
        assertEquals(899_726L, emiFor(tenLakh, 900, 240))
    }

    @Test
    fun otherLoansWorkedOutApart() {
        assertEquals(879_159L, emiFor(10_000_000L, 1000, 12), "Rs 1,00,000 at 10% for a year")
        assertEquals(601_138L, emiFor(30_000_000L, 750, 60), "Rs 3,00,000 at 7.5% for 5 years")
        assertEquals(162_512L, emiFor(5_000_000L, 1050, 36), "Rs 50,000 at 10.5% for 3 years")
        assertEquals(999_449L, emiFor(tenLakh, 875, 180), "Rs 10,00,000 at 8.75% for 15 years")
    }

    @Test
    fun withNoInterestTheEmiIsTheAmountOverTheMonths() {
        assertEquals(8_333L, emiFor(100_000L, 0, 12), "Rs 1,000 over a year: 83.33, rounded half up")
        assertEquals(1_000L, emiFor(12_000L, 0, 12))
        assertEquals(1L, emiFor(1L, 0, 3), "never below a paisa")
    }

    @Test
    fun aMonthsInterestIsTheYearlyRateOverTwelveRoundedHalfUp() {
        assertEquals(750_000L, interestOn(tenLakh, 900), "10,00,000 x 0.75%")
        assertEquals(0L, interestOn(tenLakh, 0))
        assertEquals(1L, interestOn(100L, 600), "100 paise at 0.5% a month is half a paisa, up")
        assertEquals(0L, interestOn(99L, 600), "49.5 hundredths of a paisa, down")
    }

    @Test
    fun anEmiNeedsAnAmountAndAMonth() {
        assertFailsWith<IllegalArgumentException> { emiFor(0, 900, 12) }
        assertFailsWith<IllegalArgumentException> { emiFor(1000, 900, 0) }
    }

    // ------------------------------------------------------------------------------------------------- the schedule

    private fun schedule(outstanding: Long, rateBp: Int, emi: Long): List<LoanInstalment> =
        assertNotNull(loanSchedule(outstanding, rateBp, emi) { LocalDate.of(2026, 12, 5).plusMonths(it.toLong()) })

    @Test
    fun theScheduleOfAWholeLoanAddsUp() {
        val rows = schedule(tenLakh, 900, 899_726)
        assertEquals(240, rows.size, "as many months as the loan was made for")
        assertEquals(tenLakh, rows.sumOf { it.principal }, "the principal parts add up to what was borrowed")
        assertEquals(0L, rows.last().outstandingAfter)
        assertEquals(115_934_212L, rows.sumOf { it.interest }, "Rs 11,59,342.12 of interest")
        assertEquals(1, rows.first().number)
        assertEquals(LocalDate.of(2026, 12, 5), rows.first().date)
        assertEquals(LocalDate.of(2046, 11, 5), rows.last().date)
        // The first month: interest on all of it, the rest of the EMI is principal.
        assertEquals(750_000L, rows[0].interest)
        assertEquals(149_726L, rows[0].principal)
        assertEquals(tenLakh - 149_726L, rows[0].outstandingAfter)
    }

    @Test
    fun theLastMonthPaysExactlyWhatIsLeft() {
        val rows = schedule(tenLakh, 900, 899_726)
        val last = rows.last()
        assertEquals(893_000L, last.principal)
        assertEquals(6_698L, last.interest)
        assertEquals(rows[rows.size - 2].outstandingAfter, last.principal, "all that was left")
        // Every month before it pays the EMI to the paisa.
        assertTrue(rows.dropLast(1).all { it.payment == 899_726L })
    }

    @Test
    fun aRoundedDownEmiDoesNotLeaveAMonthOfAFewPaise() {
        // Rs 1,000 over 12 months with no interest: 12 x 8,333 is 4 paise short, and those 4 are in the last month, not in a 13th.
        val rows = schedule(100_000L, 0, 8_333)
        assertEquals(12, rows.size)
        assertEquals(8_337L, rows.last().payment)
        assertEquals(100_000L, rows.sumOf { it.principal })
    }

    @Test
    fun aScheduleOfLoansFromElsewhereAddsUpToo() {
        for ((principal, rateBp, months) in listOf(
            Triple(10_000_000L, 1000, 12),
            Triple(30_000_000L, 750, 60),
            Triple(5_000_000L, 1050, 36),
            Triple(tenLakh, 875, 180),
            Triple(2_500_000L, 1999, 7),
            Triple(800_000_000L, 1125, 600),
        )) {
            val rows = schedule(principal, rateBp, emiFor(principal, rateBp, months))
            assertEquals(months, rows.size, "$principal at $rateBp bp for $months months")
            assertEquals(principal, rows.sumOf { it.principal })
            assertEquals(0L, rows.last().outstandingAfter)
            assertTrue(rows.all { it.principal > 0 && it.interest >= 0 })
        }
    }

    @Test
    fun nothingOwedIsNoSchedule() {
        assertEquals(emptyList(), loanSchedule(0, 900, 899_726) { LocalDate.EPOCH })
        assertEquals(emptyList(), loanSchedule(-5, 900, 899_726) { LocalDate.EPOCH })
    }

    // ------------------------------------------------------------------------------------- from what is really owed

    @Test
    fun whatIsLeftFromWhatIsReallyOwed() {
        // Rs 5,00,000 still owed at the Rs 8,997.26 EMI and 9%: worked out apart, 73 more months; the first is 524,726 paise (Rs 5,247.26)
        // of principal and 375,000 (Rs 3,750) of interest, the last 147,345 + 1,105, and Rs 1,49,287.22 of interest in all.
        val loan = terms(start = LocalDate.of(2026, 6, 5))
        val outlook = assertNotNull(loanOutlook(loan, 50_000_000L, nextIndex = 6))
        assertEquals(73, outlook.monthsLeft)
        assertEquals(LocalDate.of(2032, 12, 5), outlook.payoff, "73 EMIs from 5 Dec 2026")
        assertEquals(14_928_722L, outlook.interestLeft)
        val next = assertNotNull(outlook.next)
        assertEquals(LocalDate.of(2026, 12, 5), next.date, "the first month's day is the next EMI's")
        assertEquals(375_000L, next.interest, "the interest is what is owed x the monthly rate")
        assertEquals(524_726L, next.principal, "the principal is the EMI less that")
        assertEquals(49_475_274L, next.outstandingAfter)
        assertTrue(!outlook.paidOff)
    }

    @Test
    fun paidOffLeavesNothingToSay() {
        val outlook = assertNotNull(loanOutlook(terms(), 0, nextIndex = 0))
        assertTrue(outlook.paidOff)
        assertEquals(0, outlook.monthsLeft)
        assertNull(outlook.payoff)
        assertNull(outlook.next)
        assertEquals(0L, outlook.interestLeft)
    }

    @Test
    fun theNextEmisDayFollowsTheEmiDayEachMonth() {
        val loan = terms(start = LocalDate.of(2027, 1, 31))
        assertEquals(LocalDate.of(2027, 1, 31), loan.dateOf(0))
        assertEquals(LocalDate.of(2027, 2, 28), loan.dateOf(1), "the 31st is the month's last day where it is shorter")
        assertEquals(LocalDate.of(2027, 3, 31), loan.dateOf(2), "and the 31st again: counted from the first, not from February")
    }

    // ------------------------------------------------------------------------------------------- an EMI too small

    @Test
    fun anEmiBelowTheFirstMonthsInterestIsRefusedWithThatInterest() {
        // Interest on Rs 10,00,000 at 9% is Rs 7,500 a month.
        assertEquals(PayoffMonths.BelowInterest(750_000L), monthsToPay(tenLakh, 900, 700_000L))
        assertEquals(PayoffMonths.BelowInterest(750_000L), monthsToPay(tenLakh, 900, 750_000L), "just the interest never gets it smaller")
        assertNull(loanSchedule(tenLakh, 900, 700_000L) { LocalDate.EPOCH })
        assertNull(loanOutlook(terms(emi = 700_000L), tenLakh, 0), "the outlook says it can't")
    }

    @Test
    fun anEmiThatTakesLongerThanALoanCanRunIsRefused() {
        // Just above the interest: a paisa-sized dent a month for centuries.
        assertEquals(PayoffMonths.TooLong, monthsToPay(tenLakh, 900, 750_100L))
    }

    @Test
    fun anEmiThatCoversItTakesAsManyMonthsAsItTakes() {
        assertEquals(PayoffMonths.Months(240), monthsToPay(tenLakh, 900, 899_726L))
        // Rounded to the rupee, as a lender gives it: 8,997 is 26 paise short a month and the last month still is the 240th.
        assertEquals(PayoffMonths.Months(240), monthsToPay(tenLakh, 900, 899_700L))
        // A bigger EMI takes fewer months.
        val bigger = monthsToPay(tenLakh, 900, 1_200_000L) as PayoffMonths.Months
        assertTrue(bigger.count < 240, "${bigger.count}")
        assertEquals(PayoffMonths.Months(1), monthsToPay(1_000L, 900, 5_000L), "a small loan is paid in one month")
    }

    @Test
    fun theMonthsAnEmiTakesAreTheMonthsOfItsSchedule() {
        for (emi in listOf(899_726L, 950_000L, 1_000_000L, 1_500_000L)) {
            val months = (monthsToPay(tenLakh, 900, emi) as PayoffMonths.Months).count
            assertEquals(months, schedule(tenLakh, 900, emi).size, "at $emi paise a month")
        }
    }

    // ------------------------------------------------------------------------------------------------ the terms

    @Test
    fun termsThatHold() {
        assertNull(terms().fault())
        assertNull(terms(rateBp = 0).fault(), "no interest is fine")
        assertNull(terms(rateBp = MAX_LOAN_RATE_BP).fault())
        assertNull(terms(months = MAX_LOAN_MONTHS).fault())
        assertNull(terms(months = 1).fault())
    }

    @Test
    fun eachTermIsCheckedInTheOrderTheFormAsksForThem() {
        assertEquals(LoanFault.Principal, LoanTerms(0, 900, 240, 1_000, LocalDate.EPOCH).fault())
        assertEquals(LoanFault.Principal, LoanTerms(-5, 900, 240, 1_000, LocalDate.EPOCH).fault())
        assertEquals(LoanFault.Rate, LoanTerms(1_000, -1, 240, 1_000, LocalDate.EPOCH).fault())
        assertEquals(LoanFault.Rate, LoanTerms(1_000, 10_001, 240, 1_000, LocalDate.EPOCH).fault())
        assertEquals(LoanFault.Tenure, LoanTerms(1_000, 900, 0, 1_000, LocalDate.EPOCH).fault())
        assertEquals(LoanFault.Tenure, LoanTerms(1_000, 900, 601, 1_000, LocalDate.EPOCH).fault())
        assertEquals(LoanFault.Emi, LoanTerms(1_000, 900, 240, 0, LocalDate.EPOCH).fault())
        assertEquals(LoanFault.Emi, LoanTerms(1_000, 900, 240, -1, LocalDate.EPOCH).fault())
    }

    // -------------------------------------------------------------------------------------------- which EMI is next

    private val june = terms(start = LocalDate.of(2026, 6, 5))

    @Test
    fun withNoEmiRecordedTheNextIsTheFirstOnOrAfterToday() {
        assertEquals(0, nextEmiIndex(terms(start = LocalDate.of(2026, 12, 5)), null, LocalDate.of(2026, 11, 15)), "the first is still ahead")
        assertEquals(0, nextEmiIndex(terms(start = LocalDate.of(2026, 11, 15)), null, LocalDate.of(2026, 11, 15)), "the first is today")
        assertEquals(6, nextEmiIndex(june, null, LocalDate.of(2026, 11, 15)), "5 Dec is the first on or after 15 Nov")
        assertEquals(5, nextEmiIndex(june, null, LocalDate.of(2026, 11, 5)), "5 Nov is today")
        assertEquals(5, nextEmiIndex(june, null, LocalDate.of(2026, 11, 4)))
    }

    @Test
    fun anEmiPaidCoversTheDayItIsNearest_soPayingEarlyDoesNotLeaveThisMonthNext() {
        val today = LocalDate.of(2026, 11, 15)
        assertEquals(6, nextEmiIndex(june, LocalDate.of(2026, 11, 5), today), "paid on the day")
        assertEquals(6, nextEmiIndex(june, LocalDate.of(2026, 11, 1), today), "paid four days early")
        assertEquals(6, nextEmiIndex(june, LocalDate.of(2026, 10, 22), today), "14 days before 5 Nov, 17 after 5 Oct")
        assertEquals(5, nextEmiIndex(june, LocalDate.of(2026, 10, 18), today), "the 5 Oct one, 13 days late: 5 Nov is still to pay")
        assertEquals(6, nextEmiIndex(june, LocalDate.of(2026, 11, 9), today), "paid four days late")
    }

    @Test
    fun anEmiPaidBeforeTheFirstDayCoversItOnlyWhenItIsNear() {
        val loan = terms(start = LocalDate.of(2026, 12, 5))
        assertEquals(1, nextEmiIndex(loan, LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 26)), "ten days before the first: it is the first")
        assertEquals(0, nextEmiIndex(loan, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 26)), "forty days before: not an EMI")
    }

    @Test
    fun theDayCountingHoldsAtTheEndOfAMonth() {
        val loan = terms(start = LocalDate.of(2027, 1, 31))
        assertEquals(2, nextEmiIndex(loan, LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 1)), "28 Feb is the 28 Feb EMI")
        assertEquals(1, nextEmiIndex(loan, LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 1)))
    }

    // --------------------------------------------------------------------------------------------------- the rate

    @Test
    fun aRateTypedIsInBasisPoints() {
        assertEquals(900, parseRateBp("9"))
        assertEquals(1050, parseRateBp("10.5"))
        assertEquals(875, parseRateBp("8.75"))
        assertEquals(875, parseRateBp(" 8.75% "))
        assertEquals(0, parseRateBp("0"))
        assertEquals(50, parseRateBp(".5"))
        assertEquals(500, parseRateBp("5."))
        assertEquals(10_000, parseRateBp("100"))
    }

    @Test
    fun aRateIsNeverRounded() {
        assertNull(parseRateBp("8.755"), "three decimals")
        assertNull(parseRateBp(""))
        assertNull(parseRateBp("."))
        assertNull(parseRateBp("abc"))
        assertNull(parseRateBp("-3"))
        assertNull(parseRateBp("1e2"))
    }

    @Test
    fun aRateAsOneWouldTypeIt() {
        assertEquals("9", rateText(900))
        assertEquals("10.5", rateText(1050))
        assertEquals("8.75", rateText(875))
        assertEquals("0", rateText(0))
        assertEquals("100", rateText(10_000))
        for (bp in listOf(0, 1, 50, 875, 900, 1050, 9999, 10_000)) assertEquals(bp, parseRateBp(rateText(bp)), "$bp bp comes back")
    }

    // ------------------------------------------------------------------------------------------------ an EMI recorded

    private val at = LocalDateTime.of(2026, 11, 5, 10, 15)

    @Test
    fun anEmiIsATransferOfThePrincipalAndAnExpenseOfTheInterest() {
        val entries = loanEmiEntries(LoanEmi(loanId = 9, fromAccountId = 1, at = at, principal = 149_726, interest = 750_000, interestCategoryId = 7, payee = "Home loan"), at)
        assertEquals(2, entries.size)
        val transfer = entries[0]
        assertEquals(TransactionType.Transfer, transfer.type)
        assertEquals(149_726L, transfer.amount)
        assertEquals(1L, transfer.accountId)
        assertEquals(9L, transfer.toAccountId)
        assertNull(transfer.categoryId)
        assertEquals("EMI Nov 2026 - principal", transfer.note)
        val expense = entries[1]
        assertEquals(TransactionType.Expense, expense.type)
        assertEquals(750_000L, expense.amount)
        assertEquals(1L, expense.accountId)
        assertNull(expense.toAccountId)
        assertEquals(7L, expense.categoryId)
        assertEquals("Home loan", expense.payee)
        assertEquals("EMI Nov 2026 - interest", expense.note)
        assertTrue(entries.all { it.occurredAt == at })
    }

    @Test
    fun aPartOfZeroMakesNoEntry() {
        val interestFree = loanEmiEntries(LoanEmi(9, 1, at, principal = 8_333, interest = 0), at)
        assertEquals(listOf(TransactionType.Transfer), interestFree.map { it.type }, "no interest, no expense - and no category needed")
        val onlyInterest = loanEmiEntries(LoanEmi(9, 1, at, principal = 0, interest = 5_000, interestCategoryId = 7), at)
        assertEquals(listOf(TransactionType.Expense), onlyInterest.map { it.type })
    }

    @Test
    fun anEmiThatMakesNoSenseIsRefusedInPlainWords() {
        val none = assertFailsWith<FinanceValidationException> { loanEmiEntries(LoanEmi(9, 1, at, 0, 0, 7), at) }
        assertEquals("Enter the principal, the interest, or both.", none.message)
        val negative = assertFailsWith<FinanceValidationException> { loanEmiEntries(LoanEmi(9, 1, at, -1, 5, 7), at) }
        assertEquals("The principal and the interest can't be negative.", negative.message)
        val same = assertFailsWith<FinanceValidationException> { loanEmiEntries(LoanEmi(9, 9, at, 5, 5, 7), at) }
        assertEquals("Pay the EMI from another account.", same.message)
        val noCategory = assertFailsWith<FinanceValidationException> { loanEmiEntries(LoanEmi(9, 1, at, 5, 5, null), at) }
        assertEquals("Choose a category for the interest.", noCategory.message)
    }

    @Test
    fun theInterestGoesInLoansAndEmiWhenTheUserStillHasIt() {
        val loans = Category(id = 5, name = "Loans & EMI", type = CategoryType.Expense, icon = "loan", createdAt = at, updatedAt = at)
        val sub = loans.copy(id = 6, name = "Home loan EMI", parentId = 5)
        val income = loans.copy(id = 7, type = CategoryType.Income)
        assertEquals(5L, emiInterestCategory(listOf(sub, loans))?.id)
        assertEquals(5L, emiInterestCategory(listOf(loans.copy(name = "  loans & emi ")))?.id, "whatever its case")
        assertNull(emiInterestCategory(listOf(loans.copy(archived = true))), "not one that was archived")
        assertNull(emiInterestCategory(listOf(income, sub)), "not an income category or a sub-category")
        assertNull(emiInterestCategory(emptyList()))
    }
}
