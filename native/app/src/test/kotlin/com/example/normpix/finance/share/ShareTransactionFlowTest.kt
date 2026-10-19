package com.example.normpix.finance.share

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.shortDate
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Text shared from another app, through the real app: it waits in the inbox, opens as a new-transaction draft once the
 * app is up and unlocked, can be edited, and is saved - through the same editor and repository as any entry - only when
 * the user saves it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ShareTransactionFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "share_flow_test")
    private val inbox get() = env.container.sharedText

    private val hdfc = "Sent Rs.250.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 10/10/26\nRef 428312345678\n" +
        "Not You?\nCall 18002586161/SMS BLOCK UPI to 7308080808"

    @After
    fun tearDown() = env.close()

    private fun launch() {
        compose.setContent { NormPixRoot(container = env.container) }
        compose.waitForIdle()
    }

    private fun share(text: String?) {
        compose.runOnUiThread { inbox.offer(text) }
        compose.waitForIdle()
    }

    private fun waitFor(text: String) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitGone(text: String) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun shown(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun stored(): List<FinanceTransaction> =
        runBlocking { env.container.finance.repository.transactions(TransactionQuery(), limit = 10).items }

    private fun click(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun pickCategory(name: String) {
        click("Category")
        compose.onNodeWithText(name).performClick()
        compose.waitForIdle()
    }

    private fun amountField() = compose.onNodeWithContentDescription("Amount in INR")

    private fun categoryId(name: String): Long? = env.container.finance.state.value.categories.first { it.name == name }.id

    @Test
    fun `a shared payment opens filled in and is saved only when the user saves it`() {
        launch()
        share(hdfc)
        waitFor("Filled in from shared text")
        compose.onNodeWithText("New expense").assertIsDisplayed()
        amountField().assert(hasText("250"))
        compose.onNodeWithText("SWIGGY").assertExists()
        compose.onNodeWithText("Ref 428312345678").assertExists()
        // A merchant the app knows comes with its category.
        compose.onNodeWithText("Food & dining › Delivery apps").assertExists()
        // Opening a draft records nothing.
        assertTrue(stored().isEmpty())

        compose.onNodeWithText("Save expense").performClick()
        waitGone("New expense")

        val saved = stored().single()
        assertEquals(TransactionType.Expense, saved.type)
        assertEquals(25_000L, saved.amount)
        assertEquals("SWIGGY", saved.payee)
        assertEquals(categoryId("Delivery apps"), saved.categoryId)
        assertEquals("Ref 428312345678", saved.note)
        assertEquals(LocalDate.of(2026, 10, 10), saved.occurredAt.toLocalDate())
        assertEquals(env.container.finance.state.value.defaultAccount?.id, saved.accountId)
        assertTrue(inbox.waiting.value.isEmpty())
    }

    @Test
    fun `every field can be changed before saving`() {
        launch()
        share(hdfc)
        waitFor("Filled in from shared text")
        amountField().performTextClearance()
        amountField().performTextInput("300")
        compose.onNodeWithText("SWIGGY").performScrollTo().performTextClearance()
        compose.onNodeWithText("Paid to").performTextInput("Swiggy Instamart")
        compose.waitForIdle()
        // The message said money went out; the user says it came in.
        compose.onNode(hasText("Income") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        compose.waitForIdle()
        pickCategory("Freelance")
        compose.onNodeWithText("Save income").performClick()
        waitGone("New income")

        val saved = stored().single()
        assertEquals(TransactionType.Income, saved.type)
        assertEquals(30_000L, saved.amount)
        assertEquals("Swiggy Instamart", saved.payee)
    }

    @Test
    fun `a share that arrives before the app is up opens once, and not again after it is dropped`() {
        share(hdfc)
        launch()
        waitFor("Filled in from shared text")
        assertEquals(1, compose.onAllNodesWithText("New expense").fetchSemanticsNodes().size)

        // A draft from shared text asks before it is thrown away, even untouched.
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss)
        waitFor("Discard this entry?")
        compose.onNodeWithText("Discard").performClick()
        waitGone("New expense")
        compose.waitForIdle()
        assertFalse(shown("Filled in from shared text"))
        assertTrue(stored().isEmpty())
        assertTrue(inbox.waiting.value.isEmpty())
    }

    @Test
    fun `a missing amount has to be entered`() {
        launch()
        share("UPI payment to Swiggy successful. Ref 428312345678")
        waitFor("No amount found in the message. Enter it.")
        pickCategory("Food & dining")
        compose.onNodeWithText("Save expense").performClick()
        waitFor("Enter an amount greater than zero")
        assertTrue(stored().isEmpty())

        amountField().performTextInput("120")
        compose.onNodeWithText("Save expense").performClick()
        waitGone("New expense")
        assertEquals(12_000L, stored().single().amount)
    }

    @Test
    fun `a direction the message doesn't give has to be chosen`() {
        launch()
        share("Rs 500 on 10-10-26 Ref 428312345678")
        waitFor("New transaction")
        compose.onNodeWithText("Save transaction").performClick()
        waitFor("Choose expense or income")
        assertTrue(stored().isEmpty())

        compose.onNode(hasText("Income") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        compose.waitForIdle()
        pickCategory("Salary")
        compose.onNodeWithText("Save income").performClick()
        waitGone("New income")
        val saved = stored().single()
        assertEquals(TransactionType.Income, saved.type)
        assertEquals(50_000L, saved.amount)
    }

    @Test
    fun `a date that couldn't be read has to be chosen`() {
        launch()
        share("Rs 250 debited on 31-02-26 to Swiggy")
        waitFor("The message's date couldn't be read. Choose the date.")
        pickCategory("Food & dining")
        compose.onNodeWithText("Save expense").performClick()
        waitFor("Choose the date")
        assertTrue(stored().isEmpty())

        compose.onNodeWithContentDescription("Date, Choose").performScrollTo().performClick()
        // The date sheet opens on today; its button uses the day shown.
        val useToday = "Use ${shortDate(LocalDate.now().atStartOfDay())}"
        waitFor(useToday)
        compose.onNodeWithText(useToday).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Save expense").performClick()
        waitGone("New expense")
        assertEquals(LocalDate.now(), stored().single().occurredAt.toLocalDate())
    }

    @Test
    fun `a payment already recorded is pointed out, and saving it again is the user's choice`() {
        val finance = env.container.finance
        val food = finance.state.value.categories.first { it.name == "Food & dining" }
        val at = LocalDateTime.of(2026, 10, 10, 9, 0)
        runBlocking {
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense, amount = 25_000, accountId = finance.state.value.defaultAccount!!.id!!,
                    categoryId = food.id, occurredAt = at, payee = "SWIGGY", note = "Ref 428312345678", createdAt = at, updatedAt = at,
                ),
            )
        }
        launch()
        share(hdfc)
        waitFor("You may have recorded this already: an expense of ₹250.00 to SWIGGY on 10 Oct at 9:00 AM.")
        // The payee was filed under Food & dining before, so the draft starts there.
        compose.onNodeWithText("Save expense").performClick()
        waitFor("Already recorded?")
        compose.onNodeWithText("Go back").performClick()
        waitGone("Already recorded?")
        assertEquals(1, stored().size)
        compose.onNodeWithText("New expense").assertExists()

        compose.onNodeWithText("Save expense").performClick()
        waitFor("Already recorded?")
        compose.onNodeWithText("Save anyway").performClick()
        waitGone("New expense")
        assertEquals(2, stored().size)
    }

    @Test
    fun `text that isn't a transaction says so and can still be entered by hand`() {
        launch()
        share("See you at 7 tomorrow!")
        waitFor("No transaction found")
        compose.onNodeWithText("Cancel").performClick()
        waitGone("No transaction found")
        assertFalse(shown("Filled in from shared text"))

        share("Check out this link https://example.com/page")
        waitFor("No transaction found")
        compose.onNodeWithText("Enter it myself").performClick()
        waitFor("Nothing could be read from the shared text. Enter the details yourself.")
        compose.onNodeWithText("New transaction").assertExists()
    }

    @Test
    fun `a failed payment, a request and an empty share ask or explain first`() {
        launch()
        share("Your payment of ₹250 to Swiggy failed. Any amount debited will be refunded within 3-5 working days.")
        waitFor("This payment failed")
        compose.onNodeWithText("Cancel").performClick()
        waitGone("This payment failed")
        assertFalse(shown("Filled in from shared text"))

        share("Rahul Sharma has requested ₹500 from you on Google Pay")
        waitFor("This is a payment request")
        compose.onNodeWithText("Add anyway").performClick()
        waitFor("This is a payment request: nothing has been paid yet.")
        compose.onNodeWithText("New transaction").assertExists()
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `an empty share is explained`() {
        launch()
        share(null)
        waitFor("Nothing to add")
        compose.onNodeWithText("OK").performClick()
        waitGone("Nothing to add")
        assertFalse(shown("New expense"))
    }

    // -------------------------------------------------------------- pictures

    /** Stands in for the on-device recognizer (ML Kit doesn't run under Robolectric): what it "reads", and what it was asked. */
    private class FakePictures(private val text: String?, private val headline: String? = null) : SharedPictureReader {
        val asked = mutableListOf<Uri>()

        override suspend fun read(picture: Uri): PictureText? {
            asked += picture
            return text?.let { PictureText(it, headline) }
        }
    }

    private val receipt = Uri.parse("content://com.example.payments.files/receipts/receipt.png")

    private fun receiveWith(pictures: SharedPictureReader) {
        compose.setContent { env.Content { ReceiveSharedTransactions(inbox, pictures, appLocked = false, sampleData = false) } }
        compose.waitForIdle()
    }

    @Test
    fun `a shared receipt picture is read on the phone and opens filled in`() {
        // Illustrative: how a payment app's receipt screenshot might come out of the recognizer, one line per text block.
        val pictures = FakePictures("₹250\nPaid to Swiggy\nCompleted\n10 Oct 2026, 12:34 pm\nUPI transaction ID\n428312345678")
        receiveWith(pictures)
        compose.runOnUiThread { inbox.offer(text = null, picture = receipt) }
        waitFor("Filled in from a shared picture")
        assertEquals(listOf(receipt), pictures.asked)
        amountField().assert(hasText("250"))
        compose.onNodeWithText("Swiggy").assertExists()
        compose.onNodeWithText("Food & dining › Delivery apps").assertExists()
        assertTrue(stored().isEmpty())

        compose.onNodeWithText("Save expense").performClick()
        waitGone("New expense")
        val saved = stored().single()
        assertEquals(25_000L, saved.amount)
        assertEquals("Swiggy", saved.payee)
        assertEquals("Ref 428312345678", saved.note)
        assertEquals(LocalDateTime.of(2026, 10, 10, 12, 34), saved.occurredAt)
        assertEquals(categoryId("Delivery apps"), saved.categoryId)
    }

    @Test
    fun `a receipt whose rupee sign was misread still gets its amount and direction`() {
        // Google Pay's paid screen says no payment word at all: "To <payee>" over the amount, which the recognizer often
        // reads without its rupee sign. The figure printed largest is the amount; the title says the money went out.
        receiveWith(
            FakePictures(
                "To ZOMATO\nZ1,499\nCompleted\n10 Oct 2026, 8:05 pm\nUPI transaction ID\n428312345678\nTo: ZOMATO\nFrom: SAI KUMAR (HDFC Bank)",
                headline = "Z1,499",
            ),
        )
        compose.runOnUiThread { inbox.offer(text = null, picture = receipt) }
        waitFor("Filled in from a shared picture")
        compose.onNodeWithText("New expense").assertExists()
        amountField().assert(hasText("1499"))
        compose.onNodeWithText("ZOMATO").assertExists()
        compose.onNodeWithText("Food & dining › Delivery apps").assertExists()
    }

    @Test
    fun `the text read from a picture can be opened to check it`() {
        val read = "₹250\nPaid to Swiggy\nUPI transaction ID\n428312345678"
        receiveWith(FakePictures(read))
        compose.runOnUiThread { inbox.offer(text = null, picture = receipt) }
        waitFor("Show the text it read")
        assertFalse(shown(read))
        click("Show the text it read")
        waitFor("Hide the text it read")
        compose.onNodeWithText(read).assertExists()
    }

    @Test
    fun `words shared with a picture are read with it`() {
        receiveWith(FakePictures("UPI transaction ID\n428312345678"))
        compose.runOnUiThread { inbox.offer(text = "Paid ₹250 to Swiggy", picture = receipt) }
        waitFor("Filled in from a shared picture")
        amountField().assert(hasText("250"))
        compose.onNodeWithText("Ref 428312345678").assertExists()
    }

    @Test
    fun `a picture with no readable text says so`() {
        receiveWith(FakePictures(null))
        compose.runOnUiThread { inbox.offer(text = null, picture = receipt) }
        waitFor("Couldn't read the picture")
        compose.onNodeWithText("Enter it myself").performClick()
        waitFor("Nothing could be read from the shared picture. Enter the details yourself.")
        compose.onNodeWithText("New transaction").assertExists()
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `a share waits while the app is locked`() {
        var locked by mutableStateOf(true)
        compose.setContent { env.Content { ReceiveSharedTransactions(inbox, NoSharedPictureReader, appLocked = locked, sampleData = false) } }
        share(hdfc)
        compose.waitForIdle()
        assertFalse(shown("Filled in from shared text"))
        assertEquals(1, inbox.waiting.value.size)

        compose.runOnUiThread { locked = false }
        waitFor("Filled in from shared text")
        assertTrue(inbox.waiting.value.isEmpty())
    }

    @Test
    fun `a share never goes into the sample data`() {
        env.preferences.setDummyDataEnabled(true)
        compose.setContent { env.Content { ReceiveSharedTransactions(inbox, NoSharedPictureReader, appLocked = false, sampleData = true) } }
        share(hdfc)
        waitFor("Sample data is on")
        compose.onNodeWithText("Turn off sample data").performClick()
        waitGone("Sample data is on")
        // Sample data is off and the share still waits, for the user's own data to open it.
        assertFalse(env.preferences.state.value.dummyDataEnabled)
        assertEquals(1, inbox.waiting.value.size)
        assertFalse(shown("Filled in from shared text"))
    }

    @Test
    fun `declining to leave the sample data drops the share`() {
        env.preferences.setDummyDataEnabled(true)
        compose.setContent { env.Content { ReceiveSharedTransactions(inbox, NoSharedPictureReader, appLocked = false, sampleData = true) } }
        share(hdfc)
        waitFor("Sample data is on")
        compose.onNodeWithText("Cancel").performClick()
        waitGone("Sample data is on")
        assertTrue(env.preferences.state.value.dummyDataEnabled)
        assertTrue(inbox.waiting.value.isEmpty())
        assertFalse(shown("Filled in from shared text"))
    }

    // ------------------------------------------------- the number a receipt shows

    private val phonePe = "Paid to\nTransaction Successful\n11:34 am on 05 Oct 2026\nSuman Sharma\nQ505076279 @ybl\nPayment Details\n" +
        "PhonePe Transaction ID\nT2610051134004403220212\nDebited from\nXXXXXXXXXX3588\nUTR: 225197484310\nPowered by\nUPI AXIS BANK\nZ10\n10"

    private fun account(name: String, bankKey: String, ending: String?): Account = runBlocking {
        val at = LocalDateTime.of(2026, 10, 12, 9, 0)
        env.container.finance.saveAccount(
            Account(name = name, kind = AccountKind.Bank, bankKey = bankKey, numberEnding = ending, createdAt = at, updatedAt = at),
        )
    }

    @Test
    fun `a receipt that shows an account number nothing matches leaves the account to choose`() {
        val salary = account("HDFC Salary", "hdfc", "1111")
        account("ICICI Savings", "icici", "2222")
        launch()
        share(phonePe)
        waitFor("Filled in from shared text")

        // What the receipt says is read right: the amount is the figure alone on a line, the payee is below "Paid to".
        amountField().assert(hasText("10"))
        compose.onNodeWithText("Suman Sharma").assertExists()
        compose.onNodeWithText("Ref 225197484310").assertExists()
        assertTrue(shown("The message shows a number ending 3588, but none of your accounts has it.", substring = true))

        // No account was chosen for the user: saving asks for one.
        pickCategory("Food & dining")
        compose.onNodeWithText("Save expense").performClick()
        waitFor("Choose an account")
        assertTrue(stored().isEmpty())

        click("Paid from")
        compose.onNodeWithText("HDFC Salary").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Save expense").performClick()
        waitGone("New expense")
        assertEquals(salary.id, stored().single().accountId)
    }

    @Test
    fun `a receipt that shows an account number picks the account that has it`() {
        val salary = account("HDFC Salary", "hdfc", "3588")
        account("ICICI Savings", "icici", "2222")
        launch()
        share(phonePe)
        waitFor("Filled in from shared text")

        assertTrue(shown("Account from the number in the message: HDFC Salary."))
        pickCategory("Food & dining")
        compose.onNodeWithText("Save expense").performClick()
        waitGone("New expense")
        assertEquals(salary.id, stored().single().accountId)
    }
}
