package com.example.normpix.finance.share

import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** From a parsed draft to the form's starting point, against a real (in-memory) ledger. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedTransactionImportTest {
    private val env = TestEnvironment(prefsName = "shared_import_test")
    private val finance = env.container.finance
    private val repository = finance.repository
    private val now = LocalDateTime.of(2026, 10, 11, 9, 30)

    @After
    fun tearDown() = env.close()

    private fun account(name: String, kind: AccountKind, bankKey: String?): Account = runBlocking {
        finance.saveAccount(Account(name = name, kind = kind, bankKey = bankKey, createdAt = now, updatedAt = now))
    }

    private fun category(name: String): Category = finance.state.value.categories.first { it.name == name }

    private fun expense(amount: Long, at: LocalDateTime, payee: String?, note: String? = null, categoryId: Long = category("Food & dining").id!!) =
        runBlocking {
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = amount,
                    accountId = finance.state.value.defaultAccount!!.id!!,
                    categoryId = categoryId,
                    occurredAt = at,
                    payee = payee,
                    note = note,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

    private fun parse(text: String) =
        assertIs<SharedTextReading.Draft>(SharedTransactionParser.read(text, today = now.toLocalDate())).draft

    // ------------------------------------------------------------ category

    @Test
    fun `category comes from the last entry with the same payee`() {
        val shopping = category("Shopping")
        expense(100, now.minusDays(9), "Swiggy", categoryId = category("Food & dining").id!!)
        expense(100, now.minusDays(2), "SWIGGY", categoryId = shopping.id!!)
        expense(100, now.minusDays(1), "Swiggy Instamart", categoryId = category("Groceries").id!!)
        runBlocking {
            assertEquals(shopping.id, repository.lastCategoryForPayee(" swiggy ", TransactionType.Expense))
            assertNull(repository.lastCategoryForPayee("Swiggy", TransactionType.Income))
            assertNull(repository.lastCategoryForPayee("Zomato", TransactionType.Expense))
            finance.setCategoryArchived(shopping.id!!, true)
            assertNull(repository.lastCategoryForPayee("Swiggy", TransactionType.Expense))
        }
    }

    // ---------------------------------------------------------- duplicates

    @Test
    fun `a payment with the same reference is a possible duplicate whatever else differs`() {
        val saved = expense(25_000, now.minusDays(5), "Somewhere else", note = "Ref 428312345678")
        val found = runBlocking {
            repository.possibleDuplicates(TransactionType.Income, 999, now, "Swiggy", "428312345678")
        }
        assertEquals(listOf(saved.id), found.map { it.id })
    }

    @Test
    fun `same amount on the same day matches unless the payees differ`() {
        val day = LocalDateTime.of(2026, 10, 10, 13, 0)
        val swiggy = expense(25_000, day, "Swiggy")
        expense(25_000, day.minusDays(1), "Swiggy")
        expense(25_100, day, "Swiggy")
        runBlocking {
            assertEquals(listOf(swiggy.id), repository.possibleDuplicates(TransactionType.Expense, 25_000, day.withHour(20), "swiggy", null).map { it.id })
            // No payee on the new one: amount and day are enough to ask.
            assertEquals(listOf(swiggy.id), repository.possibleDuplicates(TransactionType.Expense, 25_000, day, null, null).map { it.id })
            // Two different shops for the same money on the same day are two payments.
            assertTrue(repository.possibleDuplicates(TransactionType.Expense, 25_000, day, "Zomato", null).isEmpty())
            assertTrue(repository.possibleDuplicates(TransactionType.Income, 25_000, day, "Swiggy", null).isEmpty())
            // Without a type or an amount there is nothing to compare but a reference.
            assertTrue(repository.possibleDuplicates(null, 25_000, day, "Swiggy", null).isEmpty())
        }
    }

    // ------------------------------------------------------------- account

    @Test
    fun `account is the one the message's bank and kind point to`() {
        val savings = account("HDFC Savings", AccountKind.Bank, "hdfc")
        val card = account("HDFC Credit Card", AccountKind.Card, "hdfc")
        val accounts = finance.state.value.activeAccounts
        val catalog = finance.state.value.catalog
        fun match(text: String) = parse(text).let { SharedTransactionImport.matchAccount(text, it.counterparty, it.instrument, accounts, catalog) }

        assertEquals(savings.id, match("Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 10/10/26")?.id)
        assertEquals(card.id, match("Rs 1,250 spent on your HDFC Bank Credit Card at AMAZON on 10-10-26")?.id)
        // "Card" alone could be either.
        assertNull(match("Rs 1,250 spent on HDFC Bank Card x1234 at AMAZON on 10-10-26"))
        // No bank named, or one the user has no account with.
        assertNull(match("₹250 paid to Swiggy"))
        assertNull(match("Rs 250 debited from your ICICI Bank account"))
    }

    @Test
    fun `the payee's name is not taken for a bank`() {
        account("Airtel Bank", AccountKind.Bank, "airtel")
        val text = "₹499 paid to Airtel"
        val draft = parse(text)
        val state = finance.state.value
        assertNull(SharedTransactionImport.matchAccount(text, draft.counterparty, draft.instrument, state.activeAccounts, state.catalog))
    }

    // ------------------------------------------------------------- prefill

    @Test
    fun `prefill carries what was read and says where the rest came from`() {
        val savings = account("HDFC Savings", AccountKind.Bank, "hdfc")
        val food = category("Food & dining")
        expense(9_900, now.minusDays(20), "SWIGGY", categoryId = food.id!!)
        val text = "Sent Rs.250.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 10/10/26\nRef 428312345678"
        val prefill = runBlocking { SharedTransactionImport.prefill(parse(text), text, finance.state.value, repository, now) }

        assertEquals(TransactionType.Expense, prefill.type)
        assertEquals(25_000L, prefill.amount)
        assertEquals("SWIGGY", prefill.payee)
        assertEquals("Ref 428312345678", prefill.note)
        // The message has no time: the date is its, the time of day is now's - as when a date is picked in the form.
        assertEquals(LocalDateTime.of(LocalDate.of(2026, 10, 10), now.toLocalTime()), prefill.occurredAt)
        assertEquals(savings.id, prefill.accountId)
        assertEquals(food.id, prefill.categoryId)
        assertEquals("428312345678", prefill.reference)
        assertEquals(
            listOf(
                ShareNotice("Category from your last payment to SWIGGY."),
                ShareNotice("Account from the bank in the message: HDFC Savings."),
            ),
            prefill.notices,
        )
    }

    @Test
    fun `a merchant the user hasn't filed yet gets the category the app knows for it`() {
        val text = "₹250 paid to Swiggy on 10-10-26"
        val prefill = runBlocking { SharedTransactionImport.prefill(parse(text), text, finance.state.value, repository, now) }
        assertEquals(category("Delivery apps").id, prefill.categoryId)
        assertTrue(ShareNotice("Category picked for Swiggy. Change it if it doesn't fit.") in prefill.notices)

        // The user's own filing of the payee wins over the app's table.
        expense(9_900, now.minusDays(3), "Swiggy", categoryId = category("Shopping").id!!)
        val again = runBlocking { SharedTransactionImport.prefill(parse(text), text, finance.state.value, repository, now) }
        assertEquals(category("Shopping").id, again.categoryId)
        assertTrue(ShareNotice("Category from your last payment to Swiggy.") in again.notices)
    }

    @Test
    fun `money received gets no category from the table`() {
        val text = "Swiggy sent you ₹250 on 10-10-26"
        val prefill = runBlocking { SharedTransactionImport.prefill(parse(text), text, finance.state.value, repository, now) }
        assertEquals(TransactionType.Income, prefill.type)
        assertNull(prefill.categoryId)
    }

    @Test
    fun `prefill warns about a payment already recorded`() {
        expense(25_000, LocalDateTime.of(2026, 10, 10, 9, 0), "SWIGGY", note = "Ref 428312345678")
        val text = "Sent Rs.250.00 To SWIGGY On 10/10/26 Ref 428312345678"
        val prefill = runBlocking { SharedTransactionImport.prefill(parse(text), text, finance.state.value, repository, now) }
        val first = prefill.notices.first()
        assertTrue(first.attention)
        assertEquals("You may have recorded this already: an expense of ₹250.00 to SWIGGY on 10 Oct.", first.text)
    }

    @Test
    fun `notices name what is missing first`() {
        val money = finance.state.value.money
        fun notices(text: String) = SharedTransactionImport.notices(parse(text), money, now, null, category = null, duplicate = null)

        assertEquals(
            listOf(
                ShareNotice("No amount found in the message. Enter it.", attention = true),
                ShareNotice("Couldn't tell whether money went out or came in. Choose Expense or Income.", attention = true),
                ShareNotice("No date in the message, so it's set to now."),
            ),
            notices("Ref 428312345678"),
        )
        assertEquals(
            ShareNotice("The message mentions ₹100.00 and ₹200.00. Enter the one that moved.", attention = true),
            notices("Rs 100 and Rs 200 debited from your account").first(),
        )
        assertEquals(
            ShareNotice("The amount is in $. NormPix doesn't convert it, so enter it in INR.", attention = true),
            notices("Paid $25.00 to Netflix on 10-10-26").first(),
        )
        assertEquals(
            ShareNotice("The message's date couldn't be read. Choose the date.", attention = true),
            notices("Rs 250 debited on 31-02-26").single(),
        )
        assertEquals(
            ShareNotice("The message says this payment failed. A failed payment usually moves no money.", attention = true),
            notices("Your payment of ₹250 to Swiggy failed on 10-10-26").first(),
        )
        assertTrue(ShareNotice("A refund is money coming back, so it's recorded as income.") in notices("Refund of Rs 499 credited on 10-10-26"))
    }
}
