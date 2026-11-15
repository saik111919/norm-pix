package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * The cash-flow forecast (2026-11-14): the money that can be spent, day by day from today to a date the user picks - what is left
 * after the salary comes, the rent goes and the cards are paid. Pure and exact (integer minor units); nothing is stored.
 *
 * It follows the money rules. Today's balances are "as of today": what is dated after today is upcoming and is added here on its own
 * day, never before. A credit card's bill is paid by a transfer, which is not spending - and a card's expenses are not paid on the
 * day they are made but through its bill - so on the card side the forecast counts bills, not the card's expenses themselves.
 *
 * What it knows is what is scheduled: entries already dated ahead, the repeating payments that are active, the cards' bills. What
 * it does not know is the everyday spending of the days to come, so that is an estimate - what was spent over the last 90 days
 * outside the repeating payments, a day's share of it taken off every day - and a switch on the page, so the assumption is in view.
 */

/** Where a dated item came from. */
enum class ForecastSource { Entered, Repeating, CardBill }

/**
 * One dated thing that moves the money that can be spent: its [effect] (plus is money in, minus is money out) on [date]. What it
 * is - a payee, a category, an account - is kept as ids and the payee as written, for the screen to say in words. [type] is null
 * for a card's bill.
 */
class ForecastItem(
    val date: LocalDate,
    val effect: Long,
    val source: ForecastSource,
    val type: TransactionType?,
    val payee: String?,
    val categoryId: Long?,
    val accountId: Long,
    val toAccountId: Long?,
)

/** The money that can be spent at the end of [date]. */
class ForecastPoint(val date: LocalDate, val balance: Long)

/** A credit card as the forecast needs it: its bill as Home shows it (null when there is none), and everything owed on it now. */
class CardFacts(val account: Account, val bill: CardBill?, val owedNow: Long)

/** What the forecast is worked out from: gathered by the repository, so the working-out itself can stay pure. */
class ForecastFacts(
    val today: LocalDate,
    val accounts: List<Account>,
    /** Every account's balance as of the end of [today]. */
    val balances: Map<Long, Long>,
    /** What is dated after [today]. */
    val upcoming: List<FinanceTransaction>,
    val rules: List<RecurringRule>,
    /** The active credit cards that have a due day. */
    val cards: List<CardFacts>,
    /** What was spent over the last [everydayDays] days outside the repeating payments, on every account. */
    val everydayTotal: Long,
    val everydayDays: Int = EVERYDAY_DAYS,
) {
    /** A day's share of that, to the nearest minor unit (a half up). */
    val everydayPerDay: Long get() = (2 * everydayTotal + everydayDays) / (2L * everydayDays)

    companion object {
        /** How many days of everyday spending the estimate is made from. */
        const val EVERYDAY_DAYS = 90
    }
}

/** Where a forecast ends: some days on, the next payday, or a day the user picked. */
sealed interface ForecastEnd {
    data class Days(val days: Int) : ForecastEnd

    data object Payday : ForecastEnd

    data class Date(val date: LocalDate) : ForecastEnd
}

/** The furthest ahead a forecast looks: past a year the everyday estimate outweighs what is scheduled. */
fun forecastLimit(today: LocalDate): LocalDate = today.plusMonths(12)

/**
 * The last day of a forecast to [this]. [payday] is the next one ([nextPayday]); with none, "until payday" is a month. A picked day
 * is kept between tomorrow and [forecastLimit].
 */
fun ForecastEnd.endDate(today: LocalDate, payday: LocalDate?): LocalDate = when (this) {
    is ForecastEnd.Days -> today.plusDays(days.toLong())
    ForecastEnd.Payday -> payday ?: today.plusDays(30)
    is ForecastEnd.Date -> {
        val first = today.plusDays(1)
        val last = forecastLimit(today)
        if (date.isBefore(first)) first else if (date.isAfter(last)) last else date
    }
}

/**
 * The next payday: the next occurrence of the active income that is the largest (the earlier one when two are alike), after today
 * and within [forecastLimit]; null with no income repeating, or none left before the limit. There is no payday flag to set - the
 * biggest income is the one the month turns on.
 */
fun nextPayday(rules: List<RecurringRule>, today: LocalDate): LocalDate? {
    val salary = rules
        .filter { it.active && !it.isFinished && it.type == TransactionType.Income }
        .sortedWith(compareByDescending<RecurringRule> { it.amount }.thenBy { it.nextDate }.thenBy { it.id ?: Long.MAX_VALUE })
        .firstOrNull() ?: return null
    return occurrencesUntil(salary, forecastLimit(today)).firstOrNull { it.toLocalDate().isAfter(today) }?.toLocalDate()
}

/** The forecast: the money that can be spent on each day, where it is lowest, where it ends, and what makes it up. */
class Forecast(
    val today: LocalDate,
    val end: LocalDate,
    /** What can be spent today. */
    val startBalance: Long,
    /** One point a day, today first and [end] last. */
    val points: List<ForecastPoint>,
    /** The dated things that move it, oldest first. The everyday estimate is not one of them. */
    val items: List<ForecastItem>,
    val everyday: Boolean,
    val everydayPerDay: Long,
) {
    /** The lowest the money gets (the earliest day when several are as low). */
    val lowest: ForecastPoint = points.minWith(compareBy<ForecastPoint> { it.balance }.thenBy { it.date })

    /** Where the money stands on the last day. */
    val last: ForecastPoint get() = points.last()

    /** The money that can be spent runs out at some point. */
    val goesBelowZero: Boolean get() = lowest.balance < 0

    /** What the everyday estimate takes off over the whole forecast. */
    val everydayTotal: Long get() = if (everyday) everydayPerDay * ChronoUnit.DAYS.between(today, end) else 0L
}

/** Cash, a bank, a wallet or something else that holds money to spend - not a card (spent through its bill) or savings (put aside). */
private val SPENDABLE_KINDS = setOf(AccountKind.Cash, AccountKind.Bank, AccountKind.Wallet, AccountKind.Other)

/**
 * The accounts whose money counts as money to spend: not archived, of a kind that holds it, and not owed. "Owed" is the account
 * form's switch, which keeps the opening balance below zero (`Forms.kt`): such an account is a loan or a debt, and what is put on it
 * or taken from it is a payment, not spending. An account that is merely overdrawn today still counts - its shortfall is money that
 * isn't there to spend, and leaving it out would make the forecast look better than it is (main agent's review, 2026-11-15).
 * A loan ([Account.isLoan]) is left out whatever its opening balance says (an EMI moves money onto it: a payment, not spending).
 * [balances] is no longer read for this; it stays for the callers.
 */
@Suppress("UNUSED_PARAMETER")
fun spendableAccounts(accounts: List<Account>, balances: Map<Long, Long>): Set<Long> =
    accounts
        .filter { it.id != null && !it.archived && it.kind in SPENDABLE_KINDS && it.openingBalance >= 0L && !it.isLoan }
        .mapNotNull { it.id }
        .toSet()

/**
 * When what is charged to a card on [date] must be paid: the due date of the statement that charge falls in (a charge on the
 * statement day is in that statement). A card with no statement day has none to go by, so it is the next due date.
 */
fun dueDateFor(statementDay: Int?, dueDay: Int, date: LocalDate): LocalDate =
    if (statementDay == null) nextOnDay(dueDay, date) else nextOnDay(dueDay, nextOnDay(statementDay, date).plusDays(1))

/** One thing dated in the forecast, before it is known what it does to the money that can be spent. */
private class Entry(
    val date: LocalDate,
    val type: TransactionType,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long?,
    val payee: String?,
    val categoryId: Long?,
    val source: ForecastSource,
)

/**
 * Works the forecast out from [facts] to [end] (clamped to today at the earliest), with the everyday estimate taken off each day
 * after today when [everyday] is on.
 *
 * - Start: today's balances of the accounts that count ([spendableAccounts]).
 * - Entries dated after today and the occurrences of the active repeating payments up to [end]: an income into such an account
 *   adds; an expense from one takes away; a transfer takes away what leaves them for a card, savings or an owed account, adds what
 *   comes in from one, and is nothing between two of them. An expense on a card is not an outflow now - it goes into the card's bill
 *   (a card with no due day has no bill, so its expense is counted on its day).
 * - Cards' bills: what is due now (the bill Home shows, on its due date; a late one is counted today), what was charged since the
 *   last statement (due after the next one), and what the entries and repeats to come charge, each on the due date of the statement
 *   it falls in. A transfer onto the card dated by the due date pays that much of the bill, so the bill is not counted twice with
 *   the transfer that pays it.
 */
fun buildForecast(facts: ForecastFacts, end: LocalDate, everyday: Boolean): Forecast {
    val today = facts.today
    val last = if (end.isAfter(today)) end else today
    val spendable = spendableAccounts(facts.accounts, facts.balances)
    val billed = facts.cards.mapNotNull { it.account.id }.toSet()
    val unbilledCards = facts.accounts.filter { it.kind == AccountKind.Card && !it.archived && it.id != null && it.id !in billed }
        .mapNotNull { it.id }.toSet()

    val entries = mutableListOf<Entry>()
    for (t in facts.upcoming) {
        val date = t.occurredAt.toLocalDate()
        if (date.isAfter(today) && !date.isAfter(last)) {
            entries += Entry(date, t.type, t.amount, t.accountId, t.toAccountId, t.payee, t.categoryId, ForecastSource.Entered)
        }
    }
    for (rule in facts.rules) {
        if (!rule.active || rule.isFinished) continue
        for (occurrence in occurrencesUntil(rule, last)) {
            // One that is due but not yet recorded (later today, or before the next refresh) is today's.
            val date = occurrence.toLocalDate().let { if (it.isBefore(today)) today else it }
            entries += Entry(date, rule.type, rule.amount, rule.accountId, rule.toAccountId, rule.payee, rule.categoryId, ForecastSource.Repeating)
        }
    }

    val items = mutableListOf<ForecastItem>()
    val charges = HashMap<Long, MutableList<Pair<LocalDate, Long>>>()
    val payments = HashMap<Long, MutableList<Pair<LocalDate, Long>>>()
    for (e in entries) {
        if (e.type == TransactionType.Transfer && e.toAccountId != null && e.toAccountId in billed) {
            payments.getOrPut(e.toAccountId) { mutableListOf() } += e.date to e.amount
        }
        val effect = when (e.type) {
            TransactionType.Income -> if (e.accountId in spendable) e.amount else 0L
            TransactionType.Expense -> when (e.accountId) {
                in spendable -> -e.amount
                in billed -> {
                    charges.getOrPut(e.accountId) { mutableListOf() } += e.date to e.amount
                    0L
                }
                in unbilledCards -> -e.amount
                else -> 0L
            }
            TransactionType.Transfer -> {
                val from = e.accountId in spendable
                val to = e.toAccountId != null && e.toAccountId in spendable
                when {
                    from && !to -> -e.amount
                    !from && to -> e.amount
                    else -> 0L
                }
            }
        }
        if (effect != 0L) {
            items += ForecastItem(e.date, effect, e.source, e.type, e.payee, e.categoryId, e.accountId, e.toAccountId)
        }
    }

    for (card in facts.cards) {
        val id = card.account.id ?: continue
        val dueDay = card.account.dueDay ?: continue
        val statementDay = card.account.statementDay
        val bills = sortedMapOf<LocalDate, Long>()
        fun owe(due: LocalDate, amount: Long) {
            if (amount > 0) bills.merge(due, amount, Long::plus)
        }
        // What is due now: the last statement's bill, on its due date - or today, for one that is late.
        card.bill?.let { owe(if (it.dueDate.isBefore(today)) today else it.dueDate, it.amount) }
        // What was charged since that statement is on the next one.
        if (statementDay != null) {
            val sinceStatement = (card.owedNow - (card.bill?.amount ?: 0L)).coerceAtLeast(0L)
            owe(dueDateFor(statementDay, dueDay, previousOnDay(statementDay, today).plusDays(1)), sinceStatement)
        }
        // What the entries and repeats to come charge to it.
        for ((date, amount) in charges[id].orEmpty()) owe(dueDateFor(statementDay, dueDay, date), amount)

        val paid = payments[id].orEmpty().sortedBy { it.first }
        var applied = 0L
        for ((due, amount) in bills) {
            // A transfer onto the card by its due date pays that much of what it asks for; none of it is counted twice.
            val available = paid.filter { !it.first.isAfter(due) }.sumOf { it.second } - applied
            val toPay = minOf(amount, available.coerceAtLeast(0L))
            applied += toPay
            val net = amount - toPay
            if (net > 0 && !due.isAfter(last)) {
                items += ForecastItem(due, -net, ForecastSource.CardBill, null, card.account.name, null, id, null)
            }
        }
    }

    val days = ChronoUnit.DAYS.between(today, last).toInt()
    val change = LongArray(days + 1)
    for (item in items) change[ChronoUnit.DAYS.between(today, item.date).toInt().coerceIn(0, days)] += item.effect
    val start = spendable.sumOf { facts.balances[it] ?: 0L }
    val perDay = facts.everydayPerDay
    var balance = start
    val points = (0..days).map { day ->
        balance += change[day]
        if (everyday && day > 0) balance -= perDay
        ForecastPoint(today.plusDays(day.toLong()), balance)
    }
    return Forecast(
        today = today,
        end = last,
        startBalance = start,
        points = points,
        items = items.sortedWith(compareBy<ForecastItem> { it.date }.thenBy { it.effect }),
        everyday = everyday,
        everydayPerDay = perDay,
    )
}
