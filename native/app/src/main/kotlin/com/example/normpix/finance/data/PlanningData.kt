package com.example.normpix.finance.data

import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CardFacts
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.ForecastFacts
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.RepeatDetection
import com.example.normpix.finance.domain.RepeatSuggestion
import com.example.normpix.finance.domain.calendarLastMonth
import com.example.normpix.finance.domain.cardBill
import com.example.normpix.finance.domain.forecastLimit
import com.example.normpix.finance.domain.payeeKey
import com.example.normpix.finance.domain.suggestRepeats
import java.time.LocalDate

/*
 * What the planning screens read from the ledger (2026-11-14): the facts the cash-flow forecast is worked out from, and the past
 * expenses the detector of repeating payments reads. The working-out itself is pure (finance/domain); this only gathers.
 */

/**
 * Everything the forecast needs as of the end of [today]: every account's balance, what is dated ahead (as far as a forecast may look),
 * the repeating payments, each active credit card with a due day and its bill as Home shows it (`cardBill`), and what was spent over the
 * last 90 days outside the repeating payments. Read once for any end date or switch the page offers.
 */
suspend fun FinanceRepository.forecastFacts(today: LocalDate): ForecastFacts {
    val cutoff = today.plusDays(1).atStartOfDay()
    val accounts = accounts()
    val balances = accountBalances(asOf = cutoff)
    val cards = accounts.filter { it.kind == AccountKind.Card && !it.archived && it.id != null && it.dueDay != null }
    val figures = cardStatementFigures(cards, today)
    return ForecastFacts(
        today = today,
        accounts = accounts,
        balances = balances,
        upcoming = transactionsBetween(cutoff, forecastLimit(today).plusDays(1).atStartOfDay()),
        rules = recurringRules(),
        cards = cards.map { card ->
            val id = card.id!!
            val balance = balances[id] ?: 0L
            CardFacts(card, cardBill(card.statementDay, card.dueDay, balance, figures[id], today), owedNow = maxOf(0L, -balance))
        },
        everydayTotal = spentOutsideRepeats(today.minusDays(ForecastFacts.EVERYDAY_DAYS - 1L).atStartOfDay(), cutoff),
    )
}

/** What the Bills calendar reads from the ledger: the repeating payments, and what is already entered with a date after today. */
class CalendarLedger(val rules: List<RecurringRule>, val upcoming: List<FinanceTransaction>)

/**
 * The ledger's part of the Bills calendar as of [today]: every repeating payment, and the entries dated after today as far ahead as the
 * calendar looks (the end of [calendarLastMonth]). The cards' bills and the reminders come from where Home reads them.
 */
suspend fun FinanceRepository.calendarLedger(today: LocalDate): CalendarLedger = CalendarLedger(
    rules = recurringRules(),
    upcoming = transactionsBetween(today.plusDays(1).atStartOfDay(), calendarLastMonth(today).atEndOfMonth().plusDays(1).atStartOfDay()),
)

/** The payees that look like a repeating payment as of [today]: what [suggestRepeats] makes of the past expenses, the rules and what was dismissed. */
suspend fun FinanceRepository.repeatSuggestions(today: LocalDate): List<RepeatSuggestion> {
    val from = today.minusMonths(RepeatDetection.YEARLY_LOOKBACK_MONTHS).atStartOfDay()
    return suggestRepeats(
        transactions = expensesOutsideRepeats(from, today.plusDays(1).atStartOfDay()),
        rules = recurringRules(),
        dismissed = dismissedRepeatPayees(),
        today = today,
        loanPayees = accounts().filter { it.isLoan }.mapNotNull { payeeKey(it.name) }.toSet(),
    )
}
