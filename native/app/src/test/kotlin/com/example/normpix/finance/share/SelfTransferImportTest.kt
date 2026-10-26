package com.example.normpix.finance.share

import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.PhoneReceipts.line
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A shared self transfer, from what was read to the form's starting point, against a real (in-memory) ledger. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelfTransferImportTest {
    private val env = TestEnvironment(prefsName = "self_transfer_import_test")
    private val finance = env.container.finance
    private val repository = finance.repository
    private val now = LocalDateTime.of(2026, 10, 25, 10, 0)

    @After
    fun tearDown() = env.close()

    private fun account(name: String, bankKey: String?, ending: String? = null, kind: AccountKind = AccountKind.Bank): Account = runBlocking {
        finance.saveAccount(Account(name = name, kind = kind, bankKey = bankKey, numberEnding = ending, createdAt = now, updatedAt = now))
    }

    private fun parse(text: String) =
        assertIs<SharedTextReading.Draft>(SharedTransactionParser.read(text, today = now.toLocalDate())).draft

    private fun prefillOf(text: String) =
        runBlocking { SharedTransactionImport.prefill(parse(text), text, finance.state.value, repository, now) }

    private fun prefillOf(lines: List<PictureLine>): SharedTransactionPrefill {
        val picture = PhoneReceipts.picture(lines)
        val draft = assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read("", picture, CurrencySpec.INR, now.toLocalDate())).draft
        return runBlocking { SharedTransactionImport.prefill(draft, picture.text, finance.state.value, repository, now) }
    }

    private val sentOut = "Rs.5000.00 debited from A/c XX1234 to A/c XX5678 on 24-10-26. IMPS Ref No 612345678901"

    // ----------------------------------------------------------- by number

    @Test
    fun `money sent to another of the user's accounts is a transfer between the two`() {
        val hdfc = account("HDFC Salary", "hdfc", ending = "1234")
        val sbi = account("SBI Savings", "sbi", ending = "5678")
        val prefill = prefillOf(sentOut)

        assertEquals(TransactionType.Transfer, prefill.type)
        assertEquals(hdfc.id, prefill.accountId)
        assertEquals(sbi.id, prefill.toAccountId)
        assertFalse(prefill.accountNeedsChoice)
        assertFalse(prefill.toAccountNeedsChoice)
        assertNull(prefill.categoryId)
        assertEquals(500_000L, prefill.amount)
        assertEquals("Self transfer · Ref 612345678901", prefill.note)
        assertEquals(
            listOf(
                ShareNotice(
                    "The account it went to is one of yours, by the number the message shows, so it's recorded as a transfer between " +
                        "your accounts: not spending, and not income. Switch to Expense if it wasn't between your accounts.",
                ),
                ShareNotice("From: HDFC Salary, by the number in the message."),
                ShareNotice("To: SBI Savings, by the number in the message."),
            ),
            prefill.notices.filterNot { it.text.startsWith("No date") },
        )
        assertEquals(prefill.notices[0], prefill.transferNotice)
    }

    @Test
    fun `the receiving bank's message of the same transfer has it the other way round`() {
        val hdfc = account("HDFC Salary", "hdfc", ending = "1234")
        val sbi = account("SBI Savings", "sbi", ending = "5678")
        val prefill = prefillOf("Rs.5000.00 credited to your A/c XX5678 from A/c XX1234 on 24-10-26. IMPS Ref No 612345678901")

        assertEquals(TransactionType.Transfer, prefill.type)
        assertEquals(hdfc.id, prefill.accountId)
        assertEquals(sbi.id, prefill.toAccountId)
        assertTrue(prefill.notices.any { it.text.startsWith("The account it came from is one of yours") && "Switch to Income" in it.text })
    }

    @Test
    fun `the same transfer shared twice is found the second time`() {
        val hdfc = account("HDFC Salary", "hdfc", ending = "1234")
        val sbi = account("SBI Savings", "sbi", ending = "5678")
        runBlocking {
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Transfer, amount = 500_000, accountId = hdfc.id!!, toAccountId = sbi.id, categoryId = null,
                    occurredAt = LocalDateTime.of(2026, 10, 24, 9, 0), note = "Self transfer · Ref 612345678901", createdAt = now, updatedAt = now,
                ),
            )
        }
        val first = prefillOf(sentOut).notices.first()
        assertTrue(first.attention)
        assertEquals("You may have recorded this already: a transfer of ₹5,000.00 on 24 Oct at 9:00 AM.", first.text)
    }

    // ------------------------------------------------------------- receipts

    @Test
    fun `PhonePe's receipt of a self transfer, by the number under the user's name`() {
        val hdfc = account("HDFC Salary", "hdfc", ending = "1234")
        val sbi = account("SBI Savings", "sbi", ending = "5678")
        val prefill = prefillOf(SelfTransfersTest.phonePeSelf)

        assertEquals(TransactionType.Transfer, prefill.type)
        assertEquals(hdfc.id, prefill.accountId)
        assertEquals(sbi.id, prefill.toAccountId)
        // Kept for a switch back to Expense; a transfer saves no payee.
        assertEquals("Rahul Mehta", prefill.payee)
        assertEquals("Self transfer · Ref 523456789012", prefill.note)
    }

    @Test
    fun `PhonePe's "Transfer to" an account of the user's own is a transfer between the two`() {
        val kotak = account("Kotak Savings", "kotak", ending = "1234")
        val hdfc = account("HDFC Salary", "hdfc", ending = "5678")
        val lines = listOf(
            line("Transaction Successful", 0, 10, 100, 20),
            line("12:27 pm on 22 Aug 2026", 0, 30, 120, 40),
            line("Transfer to", 0, 50, 80, 60),
            line("XXXXXXXXXX1234 ₹1,000", 0, 70, 150, 80),
            line("Kotak Mahindra Bank", 0, 90, 80, 100),
            line("Banking Name : Kusumanchi Sai", 0, 110, 150, 120),
            line("Transfer Details", 0, 130, 100, 140),
            line("PhonePe Transaction ID T2608221227467285779046", 0, 150, 250, 160),
            line("Debited from", 0, 170, 80, 180),
            line("XXXXXXXX5678 ₹1,000", 0, 190, 150, 200),
            line("UTR: 179259041561", 0, 210, 150, 220),
        )
        val prefill = prefillOf(lines)

        assertEquals(TransactionType.Transfer, prefill.type)
        assertEquals(hdfc.id, prefill.accountId)
        assertEquals(kotak.id, prefill.toAccountId)
        assertEquals(100_000L, prefill.amount)
    }

    @Test
    fun `PhonePe's "Transfer to" someone else's account stays a payment to them`() {
        account("HDFC Salary", "hdfc", ending = "5678")
        account("Kotak Savings", "kotak", ending = "9999")
        val lines = listOf(
            line("Transaction Successful", 0, 10, 100, 20),
            line("12:27 pm on 22 Aug 2026", 0, 30, 120, 40),
            line("Transfer to", 0, 50, 80, 60),
            line("XXXXXXXXXX1234 ₹1,000", 0, 70, 150, 80),
            line("Kotak Mahindra Bank", 0, 90, 80, 100),
            line("Banking Name : Ravi Kumar", 0, 110, 150, 120),
            line("Transfer Details", 0, 130, 100, 140),
            line("Debited from", 0, 170, 80, 180),
            line("XXXXXXXX5678 ₹1,000", 0, 190, 150, 200),
            line("UTR: 179259041562", 0, 210, 150, 220),
        )
        val prefill = prefillOf(lines)

        assertEquals(TransactionType.Expense, prefill.type)
        assertEquals("Ravi Kumar", prefill.payee)
        assertNull(prefill.toAccountId)
    }

    @Test
    fun `Google Pay's receipt with the user's name on both sides finds the other account by its bank`() {
        val hdfc = account("HDFC Salary", "hdfc", ending = "1234")
        val sbi = account("SBI Savings", "sbi")
        val prefill = prefillOf(SelfTransfersTest.googlePaySelf)

        assertEquals(TransactionType.Transfer, prefill.type)
        assertEquals(hdfc.id, prefill.accountId)
        assertEquals(sbi.id, prefill.toAccountId)
        assertTrue(prefill.notices.any { it.text.startsWith("The same name is on both sides of the payment") })
        assertTrue(ShareNotice("To: SBI Savings, by the bank in the message.") in prefill.notices)
    }

    @Test
    fun `Paytm's receipt of a self transfer`() {
        val hdfc = account("HDFC Salary", "hdfc", ending = "1234")
        val sbi = account("SBI Savings", "sbi", ending = "5678")
        val prefill = prefillOf(SelfTransfersTest.paytmSelf)

        assertEquals(TransactionType.Transfer, prefill.type)
        assertEquals(hdfc.id, prefill.accountId)
        assertEquals(sbi.id, prefill.toAccountId)
        assertEquals(200_000L, prefill.amount)
    }

    // -------------------------------------------------------- left to choose

    @Test
    fun `a self transfer to an account that can't be told leaves it to the user`() {
        val hdfc = account("HDFC Salary", "hdfc", ending = "1234")
        account("SBI Savings", "sbi")
        account("ICICI Savings", "icici")
        val prefill = prefillOf("Rs.2,000.00 sent to self from HDFC Bank A/c XX1234 on 24-10-26. UPI Ref 612345678902")

        assertEquals(TransactionType.Transfer, prefill.type)
        assertEquals(hdfc.id, prefill.accountId)
        assertNull(prefill.toAccountId)
        assertTrue(prefill.toAccountNeedsChoice)
        val notice = assertNotNull(prefill.toAccountNotice)
        assertEquals(ShareNotice("Choose the account it went to.", attention = true), notice)
        // What needs choosing comes first.
        assertEquals(notice, prefill.notices.first())
        assertTrue(prefill.notices.any { it.text.startsWith("The message says it's a self transfer") })
    }

    @Test
    fun `a self transfer from a number no account has leaves the account it came from to the user`() {
        account("HDFC Salary", "hdfc", ending = "1111")
        val sbi = account("SBI Savings", "sbi", ending = "5678")
        val prefill = prefillOf(SelfTransfersTest.phonePeSelf)

        assertEquals(TransactionType.Transfer, prefill.type)
        assertNull(prefill.accountId)
        assertTrue(prefill.accountNeedsChoice)
        assertEquals(sbi.id, prefill.toAccountId)
        val notice = assertNotNull(prefill.accountNotice)
        assertTrue(notice.attention)
        assertTrue("ending 1234" in notice.text && "none of your accounts has it" in notice.text, notice.text)
    }

    @Test
    fun `the other side's number no account has, without words, leaves it an expense`() {
        account("HDFC Salary", "hdfc", ending = "1234")
        account("SBI Savings", "sbi", ending = "9999")
        val prefill = prefillOf(SelfTransfersTest.phonePeSelf)
        assertEquals(TransactionType.Expense, prefill.type)
        assertNull(prefill.toAccountId)
        assertFalse(prefill.toAccountNeedsChoice)
    }

    // ------------------------------------------------------- not a transfer

    @Test
    fun `paying someone who banks where the user does is still a payment`() {
        account("HDFC Salary", "hdfc", ending = "1234")
        account("SBI Savings", "sbi")
        // Google Pay names the other side's bank only for the user's own account; a payee at SBI is still a payee.
        val toFriend = SelfTransfersTest.googlePaySelf.map {
            when (it.text) {
                "To RAHUL MEHTA" -> line("To ASHA VERMA", it.left, it.top, it.right, it.bottom)
                "To: RAHUL MEHTA (State Bank of India)" -> line("To: ASHA VERMA (State Bank of India)", it.left, it.top, it.right, it.bottom)
                else -> it
            }
        }
        val prefill = prefillOf(toFriend)
        assertEquals(TransactionType.Expense, prefill.type)
        assertEquals("ASHA VERMA", prefill.payee)
        assertNull(prefill.toAccountId)
        assertTrue(prefill.notices.none { "transfer" in it.text })
    }

    @Test
    fun `an ordinary receipt stays what it was`() {
        account("HDFC Salary", "hdfc", ending = "1234")
        account("SBI Savings", "sbi", ending = "0000")
        val prefill = prefillOf(PhoneReceipts.phonePeTen)
        assertEquals(TransactionType.Expense, prefill.type)
        assertNull(prefill.toAccountId)
        assertEquals("Asha Verma", prefill.payee)
    }

    @Test
    fun `with one account a self transfer can't be one yet, and the form says why`() {
        // Only the Cash account every ledger starts with.
        val prefill = prefillOf("Rs.2,000.00 sent to self from HDFC Bank A/c XX1234 on 24-10-26. UPI Ref 612345678902")
        assertEquals(TransactionType.Expense, prefill.type)
        assertNull(prefill.toAccountId)
        assertTrue(
            ShareNotice(
                "The message says it's a self transfer, but you have only one account in NormPix. Add the other one to record it as a " +
                    "transfer between them.",
            ) in prefill.notices,
        )
        assertEquals("Ref 612345678902", prefill.note)
    }

    @Test
    fun `a card's bill is still the card's bill`() {
        account("SBI Savings", "sbi", ending = "5678")
        val card = account("HDFC Credit Card", "hdfc", kind = AccountKind.Card)
        val draft = ParsedTransactionDraft(
            amount = 567_930, type = TransactionType.Expense, counterparty = "HDFC Bank Credit Card", selfTransfer = SelfTransferCue.Words,
        )
        val prefill = runBlocking { SharedTransactionImport.prefill(draft, "Paid to HDFC Bank Credit Card", finance.state.value, repository, now) }
        assertEquals(TransactionType.Transfer, prefill.type)
        assertEquals(card.id, prefill.toAccountId)
        assertTrue(prefill.note!!.startsWith("Card bill payment"))
    }
}
