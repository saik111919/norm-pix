package com.example.normpix.widgets

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.R
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.settings.AppLockScope
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Recent widget lists what Home's Recent does - the latest transactions as of today, newest first - written as the app
 * writes them; hides amounts while the user hides them; and shows nothing of the ledger while NormPix locks as a whole.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now().withSecond(0).withNano(0)

    @BeforeTest
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("recent_widget_test")
    }

    @AfterTest
    fun tearDown() = env.close()

    /** Rs 250 at Swiggy this morning, Rs 1,200 at Uber yesterday, a salary the day before, and rent dated ahead. */
    private fun seed() = runBlocking {
        val finance = env.container.finance
        finance.load()
        val bank = finance.saveAccount(Account(name = "HDFC Bank", kind = AccountKind.Bank, openingBalance = 10_000_00, createdAt = now, updatedAt = now))
        val expense = finance.state.value.categories.first { it.type == CategoryType.Expense && it.parentId == null }
        val income = finance.state.value.categories.first { it.type == CategoryType.Income && it.parentId == null }
        fun record(type: TransactionType, amount: Long, payee: String, at: LocalDateTime, category: Long?) = runBlocking {
            finance.saveTransaction(
                FinanceTransaction(
                    type = type, amount = amount, accountId = bank.id!!, categoryId = category, payee = payee,
                    occurredAt = at, createdAt = now, updatedAt = now,
                ),
            )
        }
        val today = LocalDate.now()
        record(TransactionType.Expense, 250_00, "Swiggy", today.atTime(0, 5), expense.id)
        record(TransactionType.Expense, 1_200_00, "Uber", today.minusDays(1).atTime(9, 0), expense.id)
        record(TransactionType.Income, 85_000_00, "Salary", today.minusDays(2).atTime(9, 0), income.id)
        record(TransactionType.Expense, 5_000_00, "Rent", today.plusDays(3).atTime(9, 0), expense.id)
    }

    private fun load() = runBlocking { RecentWidgetData.load(env.container, RecentWidget.MAX_ROWS) }

    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test
    fun `the latest transactions as of today, newest first, written as the app writes them`() {
        seed()
        val rows = assertIs<RecentContent.Rows>(load()).rows
        assertEquals(listOf("Swiggy", "Uber", "Salary"), rows.map { it.title }, "the rent dated ahead waits for its day")
        assertEquals("−₹250.00", rows[0].amount)
        assertEquals("+₹85,000.00", rows[2].amount)
        assertTrue(rows[0].detail.endsWith("· Today"), rows[0].detail)
        assertTrue(rows[1].detail.endsWith("· Yesterday"), rows[1].detail)
        assertTrue(rows.all { it.emoji.isNotBlank() })
        assertTrue(rows[0].spoken.startsWith("Expense, −₹250.00, Swiggy"), rows[0].spoken)
    }

    @Test
    fun `hidden amounts stay hidden on the home screen`() {
        seed()
        env.preferences.setAmountsHidden(true)
        val rows = assertIs<RecentContent.Rows>(load()).rows
        assertTrue(rows.none { "250" in it.amount || "85,000" in it.amount }, rows.map { it.amount }.toString())
    }

    @Test
    fun `while NormPix locks as a whole the widget shows none of the ledger`() {
        seed()
        env.preferences.setWalletLockEnabled(true)
        env.preferences.setAppLockScope(AppLockScope.EntireApp)
        assertEquals(RecentContent.Locked, load())

        val shown = RecentWidgetViews.build(context, RecentContent.Locked, rows = 4).apply(context, FrameLayout(context))
        assertEquals(View.GONE, shown.findViewById<View>(R.id.widget_recent_rows).visibility)
        assertTrue("NormPix is locked" in texts(shown))
        assertFalse(texts(shown).any { "Swiggy" in it })
        // Locking Wallet alone keeps the ledger on show.
        env.preferences.setAppLockScope(AppLockScope.WalletOnly)
        assertIs<RecentContent.Rows>(load())
    }

    @Test
    fun `as many rows as fit, and the way to add one when there are none`() {
        seed()
        val content = load()
        val one = RecentWidgetViews.build(context, content, rows = 1).apply(context, FrameLayout(context))
        assertEquals(1, one.findViewById<ViewGroup>(R.id.widget_recent_rows).childCount)
        assertTrue("Swiggy" in texts(one))
        val all = RecentWidgetViews.build(context, content, rows = 8).apply(context, FrameLayout(context))
        assertEquals(3, all.findViewById<ViewGroup>(R.id.widget_recent_rows).childCount)
        assertTrue("+₹85,000.00" in texts(all))

        val empty = RecentWidgetViews.build(context, RecentContent.Rows(emptyList()), rows = 4).apply(context, FrameLayout(context))
        assertTrue("No transactions yet" in texts(empty))

        assertEquals(1, RecentWidgetViews.rowsFor(110f))
        assertEquals(3, RecentWidgetViews.rowsFor(250f))
        assertEquals(RecentWidget.MAX_ROWS, RecentWidgetViews.rowsFor(2000f))
    }
}
