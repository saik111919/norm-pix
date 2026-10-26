package com.example.normpix.widgets

import com.example.normpix.AppContainer
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.emojiOf
import com.example.normpix.finance.ui.categoryEmoji
import com.example.normpix.finance.ui.dayHeading
import com.example.normpix.finance.ui.label
import com.example.normpix.finance.ui.signedAmount
import com.example.normpix.settings.AppLockScope
import java.time.LocalDateTime

/** What the Recent widget shows. */
sealed interface RecentContent {
    /** NormPix locks as a whole: the home screen, which anyone holding the phone sees, shows none of it. */
    data object Locked : RecentContent

    /** The ledger couldn't be read just now. */
    data object Unavailable : RecentContent

    /** The latest transactions, newest first; none yet when empty. */
    data class Rows(val rows: List<RecentRow>) : RecentContent
}

/** One transaction as the widget writes it, as the app's own rows do. */
data class RecentRow(
    val id: Long,
    val type: TransactionType,
    /** The payee, or the category (a transfer: "Transfer"). */
    val title: String,
    /** The category or account it is in (a transfer: from → to), and its day. */
    val detail: String,
    /** Signed as in the app: "−₹250", "+₹85,000"; the currency and dots while amounts are hidden. */
    val amount: String,
    /** The category's emoji, drawn on its colour. */
    val emoji: String,
    /** Which of the eight category colours its disc takes. */
    val palette: Int,
    /** The whole row as one sentence, for a screen reader. */
    val spoken: String,
)

object RecentWidgetData {
    /**
     * The latest [limit] transactions as of today - an entry dated ahead waits for its day, as on Home - written as the app
     * writes them, amounts hidden while the user has them hidden. The user's own ledger, whatever the app shows (the sample
     * data is the app's, never the home screen's).
     */
    suspend fun load(container: AppContainer, limit: Int, now: LocalDateTime = LocalDateTime.now()): RecentContent {
        val prefs = container.preferences.state.value
        if (prefs.walletLockEnabled && prefs.appLockScope == AppLockScope.EntireApp) return RecentContent.Locked
        val repo = container.finance.repository
        val transactions = repo.transactions(TransactionQuery(before = repo.settledCutoff()), limit = limit).items
        if (transactions.isEmpty()) return RecentContent.Rows(emptyList())
        val categories = repo.categories().associateBy { it.id }
        val accounts = repo.accounts().associateBy { it.id }
        val money = MoneyFormatter(CurrencySpec.byCode(repo.currencyCode()), hidden = prefs.amountsHidden)
        return RecentContent.Rows(transactions.map { row(it, categories, accounts, money, now) })
    }

    fun row(
        t: FinanceTransaction,
        categories: Map<Long?, Category>,
        accounts: Map<Long?, Account>,
        money: MoneyFormatter,
        now: LocalDateTime,
    ): RecentRow {
        val transfer = t.type == TransactionType.Transfer
        val category = if (transfer) null else categories[t.categoryId]
        val title = t.payee ?: if (transfer) "Transfer" else category?.name ?: "Uncategorized"
        val where = when {
            transfer -> "${accounts[t.accountId]?.name ?: "Unknown"} → ${accounts[t.toAccountId]?.name ?: "Unknown"}"
            t.payee != null && category != null -> category.name
            else -> accounts[t.accountId]?.name ?: "Unknown account"
        }
        val day = dayHeading(t.occurredAt, now)
        val amount = signedAmount(money, t.type, t.amount)
        val icon = category?.icon ?: "other"
        return RecentRow(
            id = t.id ?: 0,
            type = t.type,
            title = title,
            detail = "$where · $day",
            amount = amount,
            emoji = emojiOf(icon) ?: categoryEmoji(icon),
            palette = Math.floorMod(category?.color ?: 0, CATEGORY_COLORS),
            spoken = "${t.type.label}, $amount, $title, $where, $day",
        )
    }

    /** The category colours the widget has a disc for: the app's eight. */
    const val CATEGORY_COLORS = 8
}
