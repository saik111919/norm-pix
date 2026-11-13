package com.example.normpix.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.BillKind
import com.example.normpix.finance.domain.CardBill
import com.example.normpix.finance.domain.billDateText
import com.example.normpix.finance.domain.cardBill
import com.example.normpix.finance.ui.AsyncValue
import com.example.normpix.finance.ui.FinanceStatus
import com.example.normpix.finance.ui.rememberFinanceActions
import com.example.normpix.finance.ui.rememberFinanceQuery
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.BannerProgress
import com.example.normpix.ui.components.DeckStack
import com.example.normpix.ui.components.DeckStackState
import com.example.normpix.ui.components.SectionHeader
import com.example.normpix.ui.components.SplitStatBanner
import com.example.normpix.ui.components.StackButton
import com.example.normpix.ui.components.rememberDeckStackState
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.StatusTone
import java.time.LocalDate

/*
 * The credit-card bills that are to be paid, as banners (the two-panel summary, SplitStatBanner): when each falls due and how far
 * through its month it is on the left, what is to pay on the right. Home shows them under what needs attention, the Wallet above
 * the accounts. What is to pay is the unpaid part of the card's last statement (see `cardBill`), not everything on the card: what
 * was charged since is for the next statement. A bill that is past its due date is said to be late, in the error colour. A card
 * nothing is due on - paid, or never billed - and a card with no due day have none; with no such card, nothing at all.
 */

/** A card that has a bill to pay, and where that bill stands. */
internal class DueBill(val account: Account, val bill: CardBill)

/** Every active credit card with a bill to pay, the soonest due first (the late ones before any that are not). */
@Composable
internal fun rememberDueBills(): List<DueBill> {
    val finance = rememberFinanceState()
    val today = LocalDate.now()
    val cards = finance.activeAccounts.filter { it.kind == AccountKind.Card && it.id != null && it.dueDay != null }
    // Read from the ledger again whenever it changes: what was owed at the statement, and what was paid in since.
    val figures by rememberFinanceQuery(cards.map { it.id to it.statementDay } to today) { cardStatementFigures(cards, today) }
    if (finance.status != FinanceStatus.Ready || cards.isEmpty()) return emptyList()
    val known = (figures as? AsyncValue.Data)?.value ?: return emptyList()
    return cards
        .mapNotNull { card ->
            val id = card.id ?: return@mapNotNull null
            cardBill(card.statementDay, card.dueDay, finance.balanceOf(id), known[id], today)?.let { DueBill(card, it) }
        }
        .sortedBy { it.bill.dueDate }
}

/** "Bills due" on Home: its heading, then the bills (see [BillBanners]). Nothing when no card has one. */
@Composable
internal fun BillsDue(padding: PaddingValues = HomeSectionPadding) {
    val bills = rememberDueBills()
    val deck = rememberDeckStackState()
    if (bills.isEmpty()) return
    // The heading's button gathers the bills again once they have been laid out (it is there only then).
    SectionHeader(title = if (bills.size == 1) "Bill due" else "Bills due", padding = padding, trailing = { StackButton(deck) })
    BillBanners(bills, deck)
}

/** What one bill's banner says: its two panels, the sentence a screen reader hears, and its colour. */
private class BillFace(val progress: BannerProgress, val figure: BannerFigure, val spoken: String, val tone: StatusTone?)

private fun billFace(due: DueBill, total: String): BillFace {
    val bill = due.bill
    val name = due.account.name
    val dueOn = billDateText(bill.dueDate)
    val overdue = bill.kind == BillKind.Overdue
    val words = when {
        overdue -> if (bill.daysAway == -1) "1 day late" else "${-bill.daysAway} days late"
        bill.daysAway == 0 -> "Due today"
        bill.daysAway == 1 -> "1 day left"
        else -> "${bill.daysAway} days left"
    }
    val figureTitle = when (bill.kind) {
        BillKind.Statement -> "Total due"
        BillKind.Open -> "Owed so far"
        BillKind.Overdue -> "Unpaid"
    }
    val dueLine = when {
        overdue -> "was due $dueOn, ${bill.inWords}"
        bill.daysAway <= 1 -> "due ${bill.inWords}"
        else -> "due $dueOn, ${bill.inWords}"
    }
    return BillFace(
        progress = BannerProgress(
            title = if (overdue) "Overdue" else "Due date",
            subtitle = if (overdue) "Was due $dueOn" else bill.inWords.replaceFirstChar(Char::uppercase),
            fraction = bill.fraction,
            label = words,
            from = billDateText(bill.start),
            to = dueOn,
        ),
        figure = BannerFigure(title = figureTitle, figure = total, note = name),
        spoken = "$name bill, $dueLine. $figureTitle $total. $words",
        // A bill that is late is an error; one days away is a warning; a distant one is just the card's month.
        tone = when {
            overdue -> StatusTone.Error
            bill.soon -> StatusTone.Warning
            else -> null
        },
    )
}

/** How much darker each bill behind the front one is: the banner is a pale tone, so a hint (as the status banners take). */
private const val BILL_BEHIND_DIM = 0.06f

/**
 * The [bills] as banners. One bill is one banner. Several are a stack, as the cards and the top spending are (owner, 2026-11-11):
 * the soonest due in front, the others a hair of colour behind it; a swipe brings the next one forward, pressing and holding lays
 * them all out, and the section's Stack button ([deck]) gathers them again. Tapping one opens the sheet that pays it, starting at
 * what is due.
 */
@Composable
internal fun BillBanners(bills: List<DueBill>, deck: DeckStackState) {
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val pay: (DueBill) -> Unit = { due -> actions.payBill(due.account, suggested = due.bill.amount) }
    if (bills.size == 1) {
        val due = bills.first()
        val face = billFace(due, finance.money.format(due.bill.amount))
        SplitStatBanner(face.progress, face.figure, face.spoken, tone = face.tone, onClickLabel = "Pay this bill", onClick = { pay(due) })
        return
    }
    DeckStack(
        items = bills,
        itemKey = { it.account.id ?: it.account.name },
        shape = BillShape,
        description = { due, index, count -> "${billFace(due, finance.money.format(due.bill.amount)).spoken}, ${index + 1} of $count" },
        onOpen = pay,
        state = deck,
        // As wide as one bill's banner is on its own (a stack of cards is kept to a card's width; a banner fills its column).
        maxWidth = Dp.Infinity,
        behindDim = BILL_BEHIND_DIM,
        // The stack is the button (tap to pay, swipe for the next, hold to lay them out), so the banner in it is not one of its own.
        plate = { due ->
            val face = billFace(due, finance.money.format(due.bill.amount))
            SplitStatBanner(face.progress, face.figure, face.spoken, tone = face.tone, plate = true)
        },
        face = { due ->
            val face = billFace(due, finance.money.format(due.bill.amount))
            SplitStatBanner(face.progress, face.figure, face.spoken, tone = face.tone)
        },
    )
}

/** The outline of a bill's banner, for the shade over the ones behind and the ring of a held one. */
private val BillShape = RoundedCornerShape(AppRadius.xxl)
