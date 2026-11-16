package com.example.normpix.finance.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * Loans and EMIs (2026-11-15), pure: what a loan is, the maths of its instalments and the two entries one EMI makes.
 *
 * A loan is an account that is always owed - its balance is what is still to pay, below zero - plus its terms. Everything about
 * the terms is arithmetic on integer minor units (paise), done in BigDecimal and rounded half up to a paisa at each step, so the
 * numbers are identical on every device. Nothing here reads the ledger: the caller says what is outstanding.
 */

/** The most a loan's yearly interest can be, in basis points (1 bp = 0.01%): 100%. */
const val MAX_LOAN_RATE_BP = 10_000

/** The longest a loan can run, in months: 50 years. */
const val MAX_LOAN_MONTHS = 600

/** The ready-made expense category an EMI's interest is filed under (`CategorySuggestions`), by name: the ledger has no built-in mark. */
const val LOANS_CATEGORY = "Loans & EMI"

/**
 * What was borrowed and how it is paid back. All amounts are minor units. [rateBp] is the interest a year in basis points (1050 =
 * 10.50%). [emi] is the fixed monthly payment and [tenureMonths] how many of them the loan was meant to take; the two belong
 * together (one is worked out from the other when a loan is made, see [emiFor] and [monthsToPay]) but are kept apart because a
 * lender's own EMI is rounded and a loan is read from the EMI it really has. [start] is the day of the first EMI; the next ones fall
 * on the same day of each following month ([dateOf]).
 */
data class LoanTerms(
    val principal: Long,
    val rateBp: Int,
    val tenureMonths: Int,
    val emi: Long,
    val start: LocalDate,
) {
    /** The day of EMI number [index] (0 = the first): [start] and as many months on, the 31st becoming the month's last day where it is shorter. */
    fun dateOf(index: Int): LocalDate = start.plusMonths(index.toLong())
}

/** What is wrong with a loan's terms, in the words a form says it ([advice]) and a refused backup does ([reason]). */
enum class LoanFault(val advice: String, val reason: String) {
    Principal("Enter the amount borrowed, above zero.", "has a loan amount that is not above zero"),
    Rate("Enter an interest rate from 0% to 100% a year.", "has a loan interest rate outside 0% to 100% a year"),
    Tenure("Enter a number of months from 1 to $MAX_LOAN_MONTHS.", "has a loan length outside 1 to $MAX_LOAN_MONTHS months"),
    Emi("Enter an EMI above zero.", "has a loan EMI that is not above zero"),
}

/** The first thing wrong with these terms, or null when they hold: an amount above zero, a rate of 0-100%, 1-600 months, an EMI above zero. */
fun LoanTerms.fault(): LoanFault? = when {
    principal <= 0 -> LoanFault.Principal
    rateBp !in 0..MAX_LOAN_RATE_BP -> LoanFault.Rate
    tenureMonths !in 1..MAX_LOAN_MONTHS -> LoanFault.Tenure
    emi <= 0 -> LoanFault.Emi
    else -> null
}

// ---------------------------------------------------------------------------------------------------------------- the maths

private val PRECISION = MathContext(60)
private val DIVISOR = BigDecimal(12 * 10_000)

/**
 * A month's interest on [outstanding]: the yearly rate over twelve, rounded half up to a paisa. The rate in bp over 120,000 is the
 * monthly rate (bp / 10,000 / 12), so this is one exact division.
 */
fun interestOn(outstanding: Long, rateBp: Int): Long =
    BigDecimal.valueOf(outstanding).multiply(BigDecimal.valueOf(rateBp.toLong())).divide(DIVISOR, 0, RoundingMode.HALF_UP).toLong()

/**
 * The EMI that pays [principal] off in [months] at [rateBp] a year: P * i * (1 + i)^n / ((1 + i)^n - 1) with i the monthly rate, or
 * P / n with no interest, rounded half up to a paisa (never below one). Rs 10,00,000 at 9% for 240 months is Rs 8,997.26.
 */
fun emiFor(principal: Long, rateBp: Int, months: Int): Long {
    require(principal > 0 && months > 0) { "A loan needs an amount and a length." }
    val p = BigDecimal.valueOf(principal)
    val emi = if (rateBp == 0) {
        p.divide(BigDecimal(months), PRECISION)
    } else {
        val i = BigDecimal.valueOf(rateBp.toLong()).divide(DIVISOR, PRECISION)
        val growth = BigDecimal.ONE.add(i).pow(months, PRECISION)
        p.multiply(i).multiply(growth).divide(growth.subtract(BigDecimal.ONE), PRECISION)
    }
    return emi.setScale(0, RoundingMode.HALF_UP).toLong().coerceAtLeast(1L)
}

/** How a last instalment may differ from the EMI: it is what is left, so rounding along the way makes it a little more or less. */
private fun slack(emi: Long): Long = emi / 20

/** The longest a schedule is followed before it is called endless. */
private const val ENDLESS_MONTHS = 1_200

private class Step(val principal: Long, val interest: Long, val outstandingAfter: Long)

/**
 * The steps of paying [outstanding] off with [emi] a month: each month's interest is [interestOn] what is still owed, the rest of
 * the EMI is principal. The last month pays exactly what is left - which is a little more or less than the EMI, never a month of a
 * few paise of its own: it is the month in which what is owed with its interest is no more than the EMI and 5% over. Null when it
 * never ends: an EMI that does not cover a month's interest, or one that takes over [ENDLESS_MONTHS] months.
 */
private fun amortise(outstanding: Long, rateBp: Int, emi: Long): List<Step>? {
    if (outstanding <= 0) return emptyList()
    if (emi <= 0) return null
    val steps = ArrayList<Step>()
    var left = outstanding
    while (left > 0) {
        if (steps.size >= ENDLESS_MONTHS) return null
        val interest = interestOn(left, rateBp)
        val last = left + interest <= emi + slack(emi)
        if (!last && emi <= interest) return null
        val principal = if (last) left else emi - interest
        left -= principal
        steps += Step(principal, interest, left)
    }
    return steps
}

/** One month of a loan: its [date], what of the payment is [principal] and what is [interest], and what is still owed after it. */
data class LoanInstalment(
    /** 1 for the next one to pay, counting up. */
    val number: Int,
    val date: LocalDate,
    val principal: Long,
    val interest: Long,
    val outstandingAfter: Long,
) {
    /** What is paid that month: the principal and the interest. */
    val payment: Long get() = principal + interest
}

/**
 * The months still to pay for a loan that has [outstanding] left, paid with [emi]: for each, its date ([dateOf] says the day of the
 * n-th, counting from 0), the split of the payment and what is owed after it. Empty once nothing is owed; null when it never ends.
 */
fun loanSchedule(outstanding: Long, rateBp: Int, emi: Long, dateOf: (Int) -> LocalDate): List<LoanInstalment>? =
    amortise(outstanding, rateBp, emi)?.mapIndexed { row, step ->
        LoanInstalment(row + 1, dateOf(row), step.principal, step.interest, step.outstandingAfter)
    }

/** How many months an EMI takes to pay a loan off ([monthsToPay]). */
sealed interface PayoffMonths {
    /** It is paid off in [count] months. */
    data class Months(val count: Int) : PayoffMonths

    /** The EMI does not even cover the first month's [interest], so the loan would never get smaller. */
    data class BelowInterest(val interest: Long) : PayoffMonths

    /** It would take longer than a loan can run ([MAX_LOAN_MONTHS]). */
    data object TooLong : PayoffMonths
}

/** The months it takes [emi] a month to pay [principal] off at [rateBp] a year, or why it cannot. */
fun monthsToPay(principal: Long, rateBp: Int, emi: Long): PayoffMonths {
    val first = interestOn(principal, rateBp)
    if (emi <= first && principal + first > emi + slack(emi)) return PayoffMonths.BelowInterest(first)
    val steps = amortise(principal, rateBp, emi)?.size ?: return PayoffMonths.TooLong
    return if (steps > MAX_LOAN_MONTHS) PayoffMonths.TooLong else PayoffMonths.Months(steps)
}

/**
 * Where a loan stands: what is [outstanding] and the [schedule] of the months still to pay from the next EMI, at the loan's EMI. Months
 * left, the payoff day, the interest still to come and the next EMI's split are all read off it.
 */
class LoanOutlook(val outstanding: Long, val schedule: List<LoanInstalment>) {
    /** Nothing is owed any more. */
    val paidOff: Boolean get() = schedule.isEmpty()

    val monthsLeft: Int get() = schedule.size

    /** The day of the last EMI, or null once paid off. */
    val payoff: LocalDate? get() = schedule.lastOrNull()?.date

    /** The interest that will still be paid if every EMI is paid on time. */
    val interestLeft: Long get() = schedule.sumOf { it.interest }

    /** The next EMI: its day, and how much of it is principal and how much interest; null once paid off. */
    val next: LoanInstalment? get() = schedule.firstOrNull()
}

/**
 * The loan's outlook from [outstanding] - the loan account's balance as the ledger has it, below zero turned round - at the loan's
 * own EMI and rate, the next EMI being number [nextIndex] of its own days ([nextEmiIndex]). Null when the EMI can never pay it off.
 */
fun loanOutlook(terms: LoanTerms, outstanding: Long, nextIndex: Int): LoanOutlook? =
    loanSchedule(outstanding, terms.rateBp, terms.emi) { terms.dateOf(nextIndex + it) }?.let { LoanOutlook(outstanding, it) }

/**
 * Which EMI (counting from 0 at [LoanTerms.start]) is the next to pay. With none recorded yet, the first on or after [today] (or the
 * first one, when it is still ahead). Once one is recorded, the one after the day it covered: an EMI paid up to half a month before its
 * day, or after it, is that day's, so paying early does not leave this month's showing as next. [lastPaid] is the day of the latest
 * payment onto the loan.
 */
fun nextEmiIndex(terms: LoanTerms, lastPaid: LocalDate?, today: LocalDate): Int {
    if (lastPaid == null) {
        if (!terms.start.isBefore(today)) return 0
        var index = ChronoUnit.MONTHS.between(terms.start, today).toInt()
        while (terms.dateOf(index).isBefore(today)) index++
        return index
    }
    return (coveredIndex(terms, lastPaid) + 1).coerceAtLeast(0)
}

/** The EMI whose day [paid] is nearest to (the earlier of two equally near), or -1 when it is more than half a month before the first. */
private fun coveredIndex(terms: LoanTerms, paid: LocalDate): Int {
    if (paid.isBefore(terms.start)) return if (ChronoUnit.DAYS.between(paid, terms.start) <= HALF_MONTH_DAYS) 0 else -1
    val before = ChronoUnit.MONTHS.between(terms.start, paid).toInt()
    var earlier = before
    while (terms.dateOf(earlier + 1) <= paid) earlier++
    val toEarlier = ChronoUnit.DAYS.between(terms.dateOf(earlier), paid)
    val toLater = ChronoUnit.DAYS.between(paid, terms.dateOf(earlier + 1))
    return if (toLater < toEarlier) earlier + 1 else earlier
}

private const val HALF_MONTH_DAYS = 15L

// ----------------------------------------------------------------------------------------------------------- the rate as typed

private val RATE_SHAPE = Regex("""^\d{0,6}(\.\d{0,2})?$""")

/**
 * A yearly rate typed as a percentage ("9", "10.5", "8.75", an optional "%") in basis points, or null when it is not a plain number
 * with at most two decimals - it is never rounded. Whether it is in range is [LoanTerms.fault]'s question.
 */
fun parseRateBp(input: String): Int? {
    val text = input.trim().removeSuffix("%").trim()
    if (text.isEmpty() || text == "." || !RATE_SHAPE.matches(text)) return null
    return BigDecimal(text).movePointRight(2).toInt()
}

/** [rateBp] as a percentage as one would type it: 900 is "9", 1050 "10.5", 875 "8.75". */
fun rateText(rateBp: Int): String = BigDecimal.valueOf(rateBp.toLong(), 2).stripTrailingZeros().toPlainString()

// ----------------------------------------------------------------------------------------------------------- recording an EMI

/**
 * One EMI as the user records it: [principal] and [interest] paid from [fromAccountId] at [at] onto the loan [loanId]. Either may be
 * zero (an interest-free loan has no interest) but not both. The interest is spending, so it needs [interestCategoryId]; [payee] (the
 * loan's name) is what the interest expense is paid to.
 */
data class LoanEmi(
    val loanId: Long,
    val fromAccountId: Long,
    val at: LocalDateTime,
    val principal: Long,
    val interest: Long,
    val interestCategoryId: Long? = null,
    val payee: String? = null,
)

private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

/** "EMI Nov 2026 - principal": the note both entries of an EMI carry, [part] saying which. */
fun loanEmiNote(at: LocalDateTime, part: String): String = "EMI ${MONTH_YEAR.format(at)} - $part"

/**
 * The entries one EMI makes, to be saved together or not at all: a Transfer of the principal from the paying account to the loan - what
 * is owed goes down, and no money is spent - and an Expense of the interest from the same account in [LoanEmi.interestCategoryId],
 * which is spending. A part that is zero makes no entry. Throws [FinanceValidationException] when the EMI makes no sense.
 */
fun loanEmiEntries(emi: LoanEmi, now: LocalDateTime): List<FinanceTransaction> {
    if (emi.principal < 0 || emi.interest < 0) throw FinanceValidationException("The principal and the interest can't be negative.")
    if (emi.principal == 0L && emi.interest == 0L) throw FinanceValidationException("Enter the principal, the interest, or both.")
    if (emi.fromAccountId == emi.loanId) throw FinanceValidationException("Pay the EMI from another account.")
    if (emi.interest > 0 && emi.interestCategoryId == null) throw FinanceValidationException("Choose a category for the interest.")
    return buildList {
        if (emi.principal > 0) {
            add(
                FinanceTransaction(
                    type = TransactionType.Transfer,
                    amount = emi.principal,
                    accountId = emi.fromAccountId,
                    toAccountId = emi.loanId,
                    occurredAt = emi.at,
                    note = loanEmiNote(emi.at, "principal"),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        if (emi.interest > 0) {
            add(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = emi.interest,
                    accountId = emi.fromAccountId,
                    categoryId = emi.interestCategoryId,
                    occurredAt = emi.at,
                    payee = emi.payee,
                    note = loanEmiNote(emi.at, "interest"),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }
}

/** The category an EMI's interest goes in by default: the ready-made top-level expense category [LOANS_CATEGORY], when the user still has it. */
fun emiInterestCategory(categories: List<Category>): Category? =
    categories.firstOrNull {
        it.type == CategoryType.Expense && it.parentId == null && !it.archived && it.name.trim().equals(LOANS_CATEGORY, ignoreCase = true)
    }

// ------------------------------------------------------------------------------------------------ EMIs recorded each month

/*
 * EMIs recorded each month (owner, 2026-11-16: "If I entered EMI amount it can be given on monthly based amount right"): a loan's
 * EMI can be recorded on its own day without Record EMI. It is a repeating payment - a monthly transfer of the EMI from the paying
 * account onto the loan ([emiRule]) - so Plans > Recurring lists it, the Forecast and the Bills calendar count it as money going out,
 * and it can be paused like any other. When one falls due it is not recorded as one transfer: it is split as Record EMI splits it
 * ([emiSplit], from what is owed that day), the principal a transfer onto the loan and the interest an expense, both marked as the
 * repeating payment's. An EMI already recorded by hand is not recorded again ([emiCovered]), and once nothing is owed it ends.
 */

/** How one payment onto a loan splits: [principal] lowers what is owed, [interest] is spending. */
data class EmiSplit(val principal: Long, val interest: Long) {
    val total: Long get() = principal + interest
}

/**
 * How a payment of [payment] splits when [owed] is still owed at [rateBp] a year: a month's interest on what is owed ([interestOn]) and
 * the rest principal - or, in the month that pays the loan off (what is owed and its interest come to no more than the payment and 5%
 * over, as in [loanSchedule]), exactly what is left and its interest. A payment that does not cover the month's interest is all interest.
 * Null once nothing is owed, or for no payment.
 */
fun emiSplit(owed: Long, rateBp: Int, payment: Long): EmiSplit? {
    if (owed <= 0 || payment <= 0) return null
    val interest = interestOn(owed, rateBp)
    if (owed + interest <= payment + slack(payment)) return EmiSplit(owed, interest)
    if (payment <= interest) return EmiSplit(0, payment)
    return EmiSplit(payment - interest, interest)
}

/** What a note of an EMI's entries starts with ([loanEmiNote]): how a payment onto a loan made with Record EMI is known as an EMI. */
const val EMI_NOTE_PREFIX = "EMI "

/** The time of day an EMI recorded each month is dated at. */
val EMI_TIME: LocalTime = LocalTime.of(9, 0)

/** Whether this repeating payment records the EMIs of one of [loanIds]: a transfer onto it. */
fun RecurringRule.recordsEmiOf(loanIds: Set<Long>): Boolean =
    type == TransactionType.Transfer && toAccountId != null && toAccountId in loanIds

/**
 * Whether an EMI paid by hand on [paid] already covers the EMI due on [due]: [due] is the EMI day nearest to it - within half a month,
 * the earlier of two equally near ([nextEmiIndex] reads a payment the same way), so paying a few days early or late is that month's EMI.
 */
fun emiCovered(due: LocalDate, paid: LocalDate): Boolean = ChronoUnit.DAYS.between(due, paid) in (1 - HALF_MONTH_DAYS)..HALF_MONTH_DAYS

/**
 * Which EMI (counting from 0 at [LoanTerms.start]) the first one recorded each month would be when it is turned on [today]: the next to
 * pay ([nextEmiIndex] after the [lastPaid] day), or - when EMIs before today were never recorded - the first on or after today. Ones
 * already missed are not recorded all at once; Record EMI is there for them.
 */
fun firstAutoEmiIndex(terms: LoanTerms, lastPaid: LocalDate?, today: LocalDate): Int {
    var index = nextEmiIndex(terms, lastPaid, today)
    while (terms.dateOf(index).isBefore(today)) index++
    return index
}

/**
 * The repeating payment that records [loan]'s EMIs from [fromAccountId]: a monthly transfer of the EMI onto the loan at [EMI_TIME], the
 * first on the day of EMI number [firstIndex] ([firstAutoEmiIndex]) and the last on [lastDay] - the day the schedule pays it off, or
 * none when the EMI can never pay it off (it then ends when nothing is owed). Its payee is the loan's name and its note "EMI".
 */
fun emiRule(loan: Account, fromAccountId: Long, firstIndex: Int, lastDay: LocalDate?, now: LocalDateTime): RecurringRule {
    val terms = requireNotNull(loan.loan) { "Only a loan has EMIs." }
    val loanId = requireNotNull(loan.id) { "The loan is saved first." }
    val first = terms.dateOf(firstIndex).atTime(EMI_TIME)
    return RecurringRule(
        type = TransactionType.Transfer,
        amount = terms.emi,
        accountId = fromAccountId,
        toAccountId = loanId,
        payee = loan.name,
        note = "EMI",
        frequency = RecurrenceFrequency.Monthly,
        interval = 1,
        startDate = first,
        endDate = lastDay?.let { day -> maxOf(day, first.toLocalDate()).atTime(LocalTime.MAX.withNano(0)) },
        nextDate = first,
        active = true,
        createdAt = now,
        updatedAt = now,
    )
}

/**
 * The expense category an EMI recorded each month files its interest under: [emiInterestCategory], else an expense category called
 * Other, else the first expense category there is - an EMI's interest is never left unrecorded because Loans & EMI was deleted. Null
 * only when there is no expense category at all.
 */
fun emiInterestCategoryOrFallback(categories: List<Category>): Category? {
    val expenses = categories.filter { it.type == CategoryType.Expense && !it.archived }
    return emiInterestCategory(categories)
        ?: expenses.firstOrNull { it.parentId == null && it.name.trim().equals("Other", ignoreCase = true) }
        ?: expenses.firstOrNull { it.parentId == null }
        ?: expenses.firstOrNull()
}
