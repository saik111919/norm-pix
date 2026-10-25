package com.example.normpix.finance.share

import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            finance.setCategoryArchived(shopping.id, true)
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
    fun `same amount near the same time matches unless the payees differ`() {
        val day = LocalDateTime.of(2026, 10, 10, 13, 0)
        val swiggy = expense(25_000, day, "Swiggy")
        expense(25_000, day.minusDays(1), "Swiggy")
        expense(25_100, day, "Swiggy")
        runBlocking {
            assertEquals(listOf(swiggy.id), repository.possibleDuplicates(TransactionType.Expense, 25_000, day.withHour(15), "swiggy", null).map { it.id })
            // No payee on the new one: amount and time are enough to ask.
            assertEquals(listOf(swiggy.id), repository.possibleDuplicates(TransactionType.Expense, 25_000, day, null, null).map { it.id })
            // Two different shops for the same money at the same time are two payments.
            assertTrue(repository.possibleDuplicates(TransactionType.Expense, 25_000, day, "Zomato", null).isEmpty())
            assertTrue(repository.possibleDuplicates(TransactionType.Income, 25_000, day, "Swiggy", null).isEmpty())
            // Without a type or an amount there is nothing to compare but a reference.
            assertTrue(repository.possibleDuplicates(null, 25_000, day, "Swiggy", null).isEmpty())
            // Hours apart on the same day: another order.
            assertTrue(repository.possibleDuplicates(TransactionType.Expense, 25_000, day.withHour(20), "Swiggy", null).isEmpty())
            // A message that gives only the day: anything that day may be it.
            assertEquals(
                listOf(swiggy.id),
                repository.possibleDuplicates(TransactionType.Expense, 25_000, day.withHour(20), "Swiggy", null, timeKnown = false).map { it.id },
            )
        }
    }

    @Test
    fun `the same ride twice in a day is two payments, each with its own reference`() {
        val morning = LocalDateTime.of(2026, 10, 12, 9, 5)
        expense(3_000, morning, "UBER INDIA SYSTEMS PRIVATE LIMITED", note = "Ref 131182715090")
        runBlocking {
            // The evening ride: same amount, same payee, same day - and its own reference.
            assertTrue(
                repository.possibleDuplicates(TransactionType.Expense, 3_000, morning.withHour(20), "UBER INDIA SYSTEMS PRIVATE LIMITED", "899354336109").isEmpty(),
            )
            // Even minutes apart: a different reference is a different payment.
            assertTrue(
                repository.possibleDuplicates(TransactionType.Expense, 3_000, morning.plusMinutes(10), "UBER INDIA SYSTEMS PRIVATE LIMITED", "899354336109").isEmpty(),
            )
            // The morning ride shared again is still found, whenever it says it was.
            assertEquals(1, repository.possibleDuplicates(TransactionType.Expense, 3_000, morning, "UBER INDIA SYSTEMS PRIVATE LIMITED", "131182715090").size)
        }
    }

    @Test
    fun `an app's receipt finds the account from its own part of it, and says which app it was read as`() {
        // PhonePe prints "Powered by UPI AXIS BANK" - its bank, not the user's - and the user's account only as a logo and digits.
        account("Axis Savings", AccountKind.Bank, "axis")
        val hdfc = runBlocking {
            finance.saveAccount(Account(name = "HDFC Salary", kind = AccountKind.Bank, bankKey = "hdfc", numberEnding = "1234", createdAt = now, updatedAt = now))
        }
        val picture = PhoneReceipts.picture(PhoneReceipts.phonePeTen)
        val draft = assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read("", picture, CurrencySpec.INR, now.toLocalDate())).draft
        val prefill = runBlocking { SharedTransactionImport.prefill(draft, picture.text, finance.state.value, repository, now) }
        assertEquals(hdfc.id, prefill.accountId)
        assertEquals("PhonePe", prefill.app)
        assertTrue(prefill.notices.any { it.text == "Read as a PhonePe receipt." && !it.attention })

        // Without the digits on any account, the Axis account is still not taken for it.
        runBlocking { finance.saveAccount(hdfc.copy(numberEnding = null)) }
        val unmatched = runBlocking { SharedTransactionImport.prefill(draft, picture.text, finance.state.value, repository, now) }
        assertNull(unmatched.accountId)
    }

    @Test
    fun `an entry typed in by hand is matched only around the time it was paid`() {
        // Typed in just after the morning ride: no reference to tell it apart by.
        val typed = expense(3_000, LocalDateTime.of(2026, 10, 12, 9, 20), "Uber")
        runBlocking {
            // The morning ride's receipt, shared later: probably that one.
            assertEquals(
                listOf(typed.id),
                repository.possibleDuplicates(TransactionType.Expense, 3_000, LocalDateTime.of(2026, 10, 12, 9, 5), "Uber", "131182715090").map { it.id },
            )
            // The evening ride's receipt: not that one.
            assertTrue(repository.possibleDuplicates(TransactionType.Expense, 3_000, LocalDateTime.of(2026, 10, 12, 19, 40), "Uber", "899354336109").isEmpty())
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

    // ------------------------------------------------------------ card bill

    @Test
    fun `a payment to the user's own credit card is recorded as that card's bill`() {
        account("SBI Savings", AccountKind.Bank, "sbi")
        val card = account("HDFC Credit Card", AccountKind.Card, "hdfc")
        val draft = ParsedTransactionDraft(
            amount = 567_930,
            type = TransactionType.Expense,
            counterparty = "HDFC Bank Credit Card",
            reference = "123456789012",
        )
        val prefill = runBlocking { SharedTransactionImport.prefill(draft, "Paid to HDFC Bank Credit Card", finance.state.value, repository, now) }

        assertEquals(TransactionType.Transfer, prefill.type)
        assertEquals(card.id, prefill.toAccountId)
        assertTrue(prefill.accountId != card.id, "the card is where it went, not where it came from")
        assertNull(prefill.categoryId)
        assertEquals("Card bill payment · Ref 123456789012", prefill.note)
        assertTrue(prefill.notices.any { "bill payment" in it.text && !it.attention })
    }

    @Test
    fun `without a card of the user's it stays an expense`() {
        account("SBI Savings", AccountKind.Bank, "sbi")
        val draft = ParsedTransactionDraft(amount = 567_930, type = TransactionType.Expense, counterparty = "HDFC Bank Credit Card")
        val prefill = runBlocking { SharedTransactionImport.prefill(draft, "Paid to HDFC Bank Credit Card", finance.state.value, repository, now) }

        assertEquals(TransactionType.Expense, prefill.type)
        assertNull(prefill.toAccountId)
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
        assertEquals("You may have recorded this already: an expense of ₹250.00 to SWIGGY on 10 Oct at 9:00 AM.", first.text)
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

    // ------------------------------------------------- the number a receipt shows

    private fun numbered(name: String, kind: AccountKind, bankKey: String?, ending: String? = null, cardId: String? = null): Account = runBlocking {
        finance.saveAccount(
            Account(name = name, kind = kind, bankKey = bankKey, numberEnding = ending, cardId = cardId, createdAt = now, updatedAt = now),
        )
    }

    private val phonePe = "Paid to\nTransaction Successful\n11:34 am on 05 Oct 2026\nSuman Sharma\nQ505076279 @ybl\nPayment Details\n" +
        "PhonePe Transaction ID\nT2610051134004403220212\nDebited from\nXXXXXXXXXX3588\nUTR: 225197484310\nPowered by\nUPI AXIS BANK\nZ10\n10"

    private fun prefillOf(text: String, cards: List<com.example.normpix.wallet.StoredCard> = emptyList()) =
        runBlocking { SharedTransactionImport.prefill(parse(text), text, finance.state.value, repository, now, cards) }

    @Test
    fun `the digits a receipt shows put it on the account that has them`() {
        val salary = numbered("HDFC Salary", AccountKind.Bank, "hdfc", ending = "3588")
        numbered("HDFC Spending", AccountKind.Bank, "hdfc", ending = "7001")
        val prefill = prefillOf(phonePe)

        assertEquals(salary.id, prefill.accountId)
        assertFalse(prefill.accountNeedsChoice)
        assertTrue(ShareNotice("Account from the number in the message: HDFC Salary.") in prefill.notices)
        // And what else the receipt says is read right, with no amount printed larger than the rest.
        assertEquals(1_000L, prefill.amount)
        assertEquals("Suman Sharma", prefill.payee)
        assertEquals("Ref 225197484310", prefill.note)
    }

    @Test
    fun `digits no account has leave the account to the user`() {
        numbered("HDFC Salary", AccountKind.Bank, "hdfc", ending = "1111")
        numbered("HDFC Spending", AccountKind.Bank, "hdfc")
        val prefill = prefillOf(phonePe)

        assertNull(prefill.accountId)
        assertTrue(prefill.accountNeedsChoice)
        val notice = prefill.notices.first { "ending 3588" in it.text }
        assertTrue(notice.attention)
        assertTrue("none of your accounts has it" in notice.text, notice.text)
        // The same words are there for the picture flow, which has no notice list of its own.
        assertEquals(notice, prefill.accountNotice)
    }

    @Test
    fun `digits that fit two accounts leave the account to the user, and say so`() {
        numbered("HDFC Salary", AccountKind.Bank, "hdfc", ending = "3588")
        numbered("ICICI Salary", AccountKind.Bank, "icici", ending = "3588")
        val prefill = prefillOf(phonePe)

        assertNull(prefill.accountId)
        assertTrue(prefill.accountNeedsChoice)
        assertTrue(prefill.notices.any { "fits more than one of your accounts" in it.text })
    }

    @Test
    fun `with one account there is nothing to choose between`() {
        // Only the Cash account every ledger starts with.
        val prefill = prefillOf(phonePe)
        assertNull(prefill.accountId)
        assertFalse(prefill.accountNeedsChoice)
    }

    @Test
    fun `a message that shows no number never asks for the account`() {
        numbered("HDFC Salary", AccountKind.Bank, "hdfc")
        numbered("ICICI Salary", AccountKind.Bank, "icici")
        val prefill = prefillOf("₹250 paid to Swiggy on 10-10-26")
        assertFalse(prefill.accountNeedsChoice)
        assertTrue(prefill.notices.none { "none of your accounts" in it.text })
    }

    @Test
    fun `a Wallet card's number puts a receipt on the account it was linked to`() {
        val cards = listOf(
            com.example.normpix.wallet.StoredCard("rupay", "HDFC Bank", "THE USER", "6528505555554455", "08", "29", "123", com.example.normpix.wallet.CardKind.Credit, now),
        )
        val rupay = numbered("HDFC RuPay", AccountKind.Card, "hdfc", cardId = "rupay")
        numbered("HDFC Savings", AccountKind.Bank, "hdfc")
        val googlePay = "To Mr AKBAR\n₹10\nCompleted\n10 Oct 2026, 10:07pm\nHDFC Bank\n652850XXXXXXXX55\nPayment of ₹10 completed\n" +
            "Receiver's bank has confirmed deposit of money to Mr AKBAR's bank account\nUPI transaction ID\n131074460564\nTo: Mr AKBAR\n" +
            "••••5486@ybl on PhonePe\nFrom: KUSUMANCHI SAI (HDFC Bank)\n••••10-5@okhdfcbank on Google Pay\nGoogle transaction ID\nCICAgLimuefacA"
        val prefill = prefillOf(googlePay, cards)

        assertEquals(rupay.id, prefill.accountId)
        assertEquals(TransactionType.Expense, prefill.type)
        assertEquals(1_000L, prefill.amount)
        assertEquals("Mr AKBAR", prefill.payee)
        assertEquals("Ref 131074460564", prefill.note)
        // Without the Wallet's cards, the same receipt cannot say which: two HDFC accounts, neither with digits.
        assertNull(prefillOf(googlePay).accountId)
    }

    @Test
    fun `no account fits, so the Wallet's cards are asked and the one account of that card's bank is chosen`() {
        val card = com.example.normpix.wallet.StoredCard(
            "millennia", "HDFC Bank", "THE USER", "4111111111113588", "08", "29", "123", com.example.normpix.wallet.CardKind.Credit, now,
        )
        val millennia = numbered("HDFC Millennia", AccountKind.Card, "hdfc")
        numbered("HDFC Savings", AccountKind.Bank, "hdfc")

        val prefill = prefillOf(phonePe, listOf(card))
        assertEquals(millennia.id, prefill.accountId)
        assertFalse(prefill.accountNeedsChoice)
        // Without the card the receipt could not say which, as before.
        assertNull(prefillOf(phonePe).accountId)
    }

    @Test
    fun `a Wallet card that has the digits but no account is named in the account notice`() {
        val card = com.example.normpix.wallet.StoredCard(
            "millennia", "HDFC Bank", "THE USER", "4111111111113588", "08", "29", "123", com.example.normpix.wallet.CardKind.Credit, now,
        )
        // Only a savings account of that bank: a credit card is not it, so the account is the user's to choose.
        numbered("HDFC Savings", AccountKind.Bank, "hdfc")

        val prefill = prefillOf(phonePe, listOf(card))
        assertNull(prefill.accountId)
        assertTrue(prefill.accountNeedsChoice)
        val notice = kotlin.test.assertNotNull(prefill.accountNotice)
        assertTrue(notice.attention)
        assertTrue("your HDFC Bank credit card ending 3588 in the Wallet" in notice.text, notice.text)
        assertTrue("none of your accounts is linked to it" in notice.text, notice.text)
        // The number itself is still said, and the picture flow's notice is the one in the list.
        assertTrue("ending 3588" in notice.text)
        assertTrue(notice in prefill.notices)
    }

    @Test
    fun `with no card either, the notice is the plain one`() {
        numbered("HDFC Savings", AccountKind.Bank, "hdfc")
        val notice = kotlin.test.assertNotNull(prefillOf(phonePe).accountNotice)
        assertTrue("none of your accounts has it" in notice.text, notice.text)
    }
    @Test
    fun `a self transfer prefills both from and to accounts`() {
        val hdfc = numbered("HDFC Savings", AccountKind.Bank, "hdfc", ending = "3588")
        val kotak = numbered("Kotak Account", AccountKind.Bank, "kotak", ending = "7890")
        val draft = ParsedTransactionDraft(
            amount = 480_000L,
            type = TransactionType.Transfer,
            kind = SharedKind.Transfer,
            status = SharedStatus.Completed,
            date = LocalDate.of(2026, 10, 25),
            reference = "131826136987",
            accountText = "HDFC Bank ••••3588",
            maskedNumbers = listOf(MaskedNumber("", "3588")),
            toAccountText = "Kotak Mahindra Bank ••••7890",
            toMaskedNumbers = listOf(MaskedNumber("", "7890")),
        )
        val prefill = runBlocking { SharedTransactionImport.prefill(draft, "", finance.state.value, repository, now) }
        assertEquals(TransactionType.Transfer, prefill.type)
        assertEquals(480_000L, prefill.amount)
        assertNull(prefill.payee)
        assertEquals(hdfc.id, prefill.accountId)
        assertEquals(kotak.id, prefill.toAccountId)
        assertTrue(prefill.notices.any { "Account from the number in the message: HDFC Savings" in it.text })
        assertTrue(prefill.notices.any { "Transfer destination from the number in the message: Kotak Account" in it.text })
    }
}