package com.example.normpix.finance.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.PictureText
import com.example.normpix.finance.share.ReceiveSharedTransactions
import com.example.normpix.finance.share.SharedPictureReader
import com.example.normpix.testing.TestEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A picture attached to a transaction, through the real form: chosen in the form (and read to fill in what's empty),
 * shared from another app, kept when the transaction is saved, seen whole from its details, and removed.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PictureAttachmentFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Stands in for the on-device recognizer: what it "reads" in any picture, once [gate] (when there is one) opens. */
    private class FakeReader(var text: String?) : SharedPictureReader {
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun read(picture: Uri): PictureText? {
            gate?.await()
            return text?.let { PictureText(it) }
        }
    }

    // Illustrative: Google Pay's paid screen, as the recognizer might read it.
    private val reader = FakeReader("To SWIGGY\n₹250\nCompleted\n10 Oct 2026, 12:34 pm\nUPI transaction ID\n428312345678")
    private val env = TestEnvironment(prefsName = "picture_flow_test", pictures = reader)
    private val finance = env.container.finance
    private val store = finance.pictures!!
    private val kept = File(env.context.filesDir, "pictures")

    @After
    fun tearDown() {
        env.close()
        kept.deleteRecursively()
        File(env.context.cacheDir, "picture-drafts").deleteRecursively()
    }

    private fun waitFor(text: String, substring: Boolean = false) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun waitGone(text: String) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun stored(): List<FinanceTransaction> = runBlocking { finance.repository.transactions(TransactionQuery(), limit = 10).items }

    private fun keptNames(): Set<String> = kept.listFiles().orEmpty().map { it.name }.toSet()

    private fun amountField() = compose.onNodeWithContentDescription("Amount in INR")

    private var served = 0

    /** A picture in another app (the gallery, a payment app), as the picker or a share hands it over. */
    private fun galleryPicture(): Uri {
        val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val uri = Uri.parse("content://com.example.gallery/picture-${served++}")
        shadowOf(env.context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
        return uri
    }

    private fun open(action: FinanceActions.() -> Unit) {
        compose.setContent {
            env.Content {
                val actions = rememberFinanceActions()
                LaunchedEffect(Unit) { actions.action() }
            }
        }
        compose.waitForIdle()
    }

    /** Chooses [picture] in the form, as if picked in the system's photo picker. */
    private fun choose(picture: Uri) {
        compose.onNodeWithContentDescription("Picture, Choose one").performScrollTo().performClick()
        compose.waitForIdle()
        val activity = shadowOf(compose.activity)
        val started = assertNotNull(activity.nextStartedActivityForResult, "the photo picker was opened")
        activity.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(picture))
        compose.waitForIdle()
    }

    private fun saved(): FinanceTransaction {
        compose.waitUntil(timeoutMillis = 10_000) { stored().isNotEmpty() }
        return stored().single()
    }

    @Test
    fun `a picture chosen in a new form fills in what's empty and is kept with the transaction`() {
        open { addTransaction() }
        waitFor("New expense")
        choose(galleryPicture())
        // What it filled in is said at the top of the form, in view, not in a snackbar under the sheet.
        waitFor("Filled in from the picture")
        compose.onNodeWithText("Filled in from the picture").assertIsDisplayed()
        compose.onNodeWithText("Filled in the amount", substring = true).assertExists()
        compose.onNodeWithText("Attached").assertExists()
        amountField().assert(hasText("250"))
        compose.onNodeWithText("SWIGGY").assertExists()
        compose.onNodeWithText("Food & dining › Delivery apps").assertExists()
        assertTrue(stored().isEmpty())

        compose.onNodeWithText("Save expense").performClick()
        val saved = saved()
        assertEquals(25_000L, saved.amount)
        assertEquals("SWIGGY", saved.payee)
        assertEquals(LocalDateTime.of(2026, 10, 10, 12, 34), saved.occurredAt)
        assertEquals("Ref 428312345678", saved.note)
        assertEquals(setOf(assertNotNull(saved.picture)), keptNames())
    }

    @Test
    fun `what the user entered stays as it is`() {
        open { addTransaction() }
        waitFor("New expense")
        amountField().performTextInput("300")
        choose(galleryPicture())
        waitFor("Filled in from the picture")
        compose.onNodeWithText("Anything already filled in stays as it was.").assertExists()
        amountField().assert(hasText("300"))
        // The rest was empty, so it is filled in.
        compose.onNodeWithText("SWIGGY").assertExists()
    }

    @Test
    fun `money received in a picture makes a blank new form income`() {
        reader.text = "From Rahul Sharma\n₹500\nCompleted\n9 Oct 2026, 8:15 pm"
        open { addTransaction() }
        waitFor("New expense")
        choose(galleryPicture())
        waitFor("New income")
        amountField().assert(hasText("500"))
        compose.onNodeWithText("Rahul Sharma").assertExists()
    }

    @Test
    fun `a picture with no text is still attached`() {
        reader.text = null
        open { addTransaction() }
        waitFor("New expense")
        choose(galleryPicture())
        waitFor("No text could be read from it, so nothing was filled in.")
        compose.onNodeWithText("Attached").assertExists()
        amountField().assert(hasText(""))
    }

    @Test
    fun `a picture can be removed from a saved transaction, and goes with it`() {
        val transaction = runBlocking {
            val draft = assertNotNull(store.stage(galleryPicture()))
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = 25_000,
                    accountId = finance.state.value.defaultAccount!!.id!!,
                    categoryId = finance.state.value.categories.first { it.name == "Food & dining" }.id,
                    occurredAt = LocalDateTime.of(2026, 10, 10, 12, 34),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                ),
                PictureEdit.Attach(draft),
            )
        }
        assertEquals(1, keptNames().size)
        open { editTransaction(transaction) }
        waitFor("Attached")
        compose.onNodeWithContentDescription("Remove picture").performScrollTo().performClick()
        waitGone("Attached")
        compose.onNodeWithText("Save changes").performClick()
        // The file goes once the row no longer names it.
        compose.waitUntil(timeoutMillis = 10_000) { stored().single().picture == null && keptNames().isEmpty() }
    }

    @Test
    fun `a saved transaction's picture can be read again, and what it says replaces what was read wrongly`() {
        // Saved as an earlier reading had it: ₹710 from a misread ₹, and no payee.
        val transaction = runBlocking {
            val draft = assertNotNull(store.stage(galleryPicture()))
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = 71_000,
                    accountId = finance.state.value.defaultAccount!!.id!!,
                    categoryId = finance.state.value.categories.first { it.name == "Food & dining" }.id,
                    occurredAt = LocalDateTime.of(2026, 10, 10, 12, 34),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                ),
                PictureEdit.Attach(draft),
            )
        }
        open { editTransaction(transaction) }
        waitFor("Attached")
        amountField().assert(hasText("710"))

        compose.onNodeWithContentDescription("Read the picture again").performScrollTo().performClick()
        waitFor("Changed the amount, payee and reference.")
        compose.onNodeWithText("Read again").assertIsDisplayed()
        amountField().assert(hasText("250"))
        compose.onNodeWithText("SWIGGY").assertExists()
        // Nothing is saved until the form is.
        assertEquals(71_000L, stored().single().amount)

        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { stored().single().amount == 25_000L }
        val saved = stored().single()
        assertEquals("SWIGGY", saved.payee)
        assertEquals("Food & dining", finance.state.value.category(saved.categoryId)?.name)
        assertEquals(transaction.picture, saved.picture)
    }

    @Test
    fun `reading a picture again that says nothing changes nothing`() {
        reader.text = null
        val transaction = runBlocking {
            val draft = assertNotNull(store.stage(galleryPicture()))
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = 71_000,
                    accountId = finance.state.value.defaultAccount!!.id!!,
                    categoryId = finance.state.value.categories.first { it.name == "Food & dining" }.id,
                    occurredAt = LocalDateTime.of(2026, 10, 10, 12, 34),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                ),
                PictureEdit.Attach(draft),
            )
        }
        open { editTransaction(transaction) }
        waitFor("Attached")
        compose.onNodeWithContentDescription("Read the picture again").performScrollTo().performClick()
        waitFor("No text could be read from it, so nothing changed.")
        amountField().assert(hasText("710"))
    }

    @Test
    fun `a transaction's picture is in its editor, not its receipt, and opens whole`() {
        val transaction = runBlocking {
            val draft = assertNotNull(store.stage(galleryPicture()))
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = 25_000,
                    accountId = finance.state.value.defaultAccount!!.id!!,
                    categoryId = finance.state.value.categories.first { it.name == "Food & dining" }.id,
                    occurredAt = LocalDateTime.of(2026, 10, 10, 12, 34),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                ),
                PictureEdit.Attach(draft),
            )
        }
        open { showDetails(transaction) }
        waitFor("Share receipt")
        assertTrue(compose.onAllNodesWithText("Attached").fetchSemanticsNodes().isEmpty(), "the receipt doesn't show the picture")
        compose.onNodeWithText("Edit").performClick()
        waitFor("Attached")
        compose.onNodeWithText("Attached").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("Close").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("Close").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `a shared picture comes attached, and is kept when saved`() {
        compose.setContent {
            env.Content { ReceiveSharedTransactions(env.container.sharedText, reader, appLocked = false, sampleData = false) }
        }
        compose.waitForIdle()
        compose.runOnUiThread { env.container.sharedText.offer(text = null, picture = galleryPicture()) }
        waitFor("Filled in from a shared picture")
        compose.onNodeWithText("Attached").performScrollTo().assertExists()
        compose.onNodeWithText("Save expense").performClick()
        val saved = saved()
        assertEquals(setOf(assertNotNull(saved.picture)), keptNames())
        // The draft went with the form.
        compose.waitUntil(timeoutMillis = 10_000) {
            File(env.context.cacheDir, "picture-drafts").listFiles().orEmpty().isEmpty()
        }
    }

    @Test
    fun `a shared picture the user doesn't add leaves nothing behind`() {
        compose.setContent {
            env.Content { ReceiveSharedTransactions(env.container.sharedText, reader, appLocked = false, sampleData = false) }
        }
        compose.waitForIdle()
        compose.runOnUiThread { env.container.sharedText.offer(text = null, picture = galleryPicture()) }
        waitFor("Filled in from a shared picture")
        compose.onNodeWithContentDescription("Drag handle").performSemanticsAction(SemanticsActions.Dismiss)
        // Leaving a filled-in draft asks first.
        waitFor("Discard this entry?")
        compose.onNodeWithText("Discard").performClick()
        waitGone("Filled in from a shared picture")
        compose.waitUntil(timeoutMillis = 10_000) {
            File(env.context.cacheDir, "picture-drafts").listFiles().orEmpty().isEmpty()
        }
        assertTrue(stored().isEmpty())
        assertTrue(keptNames().isEmpty())
    }

    @Test
    fun `a shared picture can be replaced from the menu, and what the new one says takes the notice's place`() {
        compose.setContent {
            env.Content { ReceiveSharedTransactions(env.container.sharedText, reader, appLocked = false, sampleData = false) }
        }
        compose.waitForIdle()
        compose.runOnUiThread { env.container.sharedText.offer(text = null, picture = galleryPicture()) }
        waitFor("Filled in from a shared picture")
        val shared = File(env.context.cacheDir, "picture-drafts").listFiles().orEmpty().map { it.name }.toSet()

        compose.onNodeWithContentDescription("Replace picture").performScrollTo().performClick()
        waitFor("Choose a picture")
        compose.onNodeWithText("Take a photo").assertExists()
        compose.onNodeWithText("Choose a picture").performClick()
        compose.waitForIdle()
        val activity = shadowOf(compose.activity)
        val started = assertNotNull(activity.nextStartedActivityForResult, "the photo picker was opened")
        activity.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(galleryPicture()))

        // The same payment again: everything is already filled in, and the form says so where the share's notice was.
        waitFor("What it shows is already filled in.")
        waitGone("Filled in from a shared picture")
        compose.onNodeWithText("Picture attached").assertIsDisplayed()
        // The shared picture's draft went when it was replaced.
        compose.waitUntil(timeoutMillis = 10_000) {
            File(env.context.cacheDir, "picture-drafts").listFiles().orEmpty().none { it.name in shared }
        }
    }

    @Test
    fun `a picture that can't be opened is said under the field, and nothing is attached`() {
        open { addTransaction() }
        waitFor("New expense")
        val notAPicture = Uri.parse("content://com.example.gallery/not-a-picture")
        shadowOf(env.context.contentResolver).registerInputStreamSupplier(notAPicture) { ByteArrayInputStream("Paid ₹250".toByteArray()) }
        choose(notAPicture)
        waitFor("Couldn't open that picture. Try another one.")
        compose.onNodeWithContentDescription("Picture, Choose one").assertExists()
        assertTrue(compose.onAllNodesWithText("Attached").fetchSemanticsNodes().isEmpty())
    }

    // ------------------------------------------------- the loading sheet

    private fun drafts(): List<File> = File(env.context.cacheDir, "picture-drafts").listFiles().orEmpty().toList()

    private fun until(condition: () -> Boolean) = compose.waitUntil(timeoutMillis = 10_000) {
        compose.waitForIdle()
        condition()
    }

    private fun receiveShares() {
        compose.setContent {
            env.Content { ReceiveSharedTransactions(env.container.sharedText, reader, appLocked = false, sampleData = false) }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a picture chosen in the form is read under a loading sheet, and the form fills in once it has gone`() {
        val gate = CompletableDeferred<Unit>().also { reader.gate = it }
        open { addTransaction() }
        waitFor("New expense")
        choose(galleryPicture())

        // A sheet says what is going on - not a line under the field, nor a snackbar under the form.
        waitFor(READING_PICTURE)
        compose.onNodeWithText(READING_PICTURE).assertIsDisplayed()
        compose.onNodeWithText(READING_PICTURE_MESSAGE).assertIsDisplayed()
        // Nothing on the form changes while it reads.
        amountField().assert(hasText(""))
        assertTrue(compose.onAllNodesWithText("Attached").fetchSemanticsNodes().isEmpty())

        gate.complete(Unit)
        waitGone(READING_PICTURE)
        waitFor("Filled in from the picture")
        amountField().assert(hasText("250"))
        compose.onNodeWithText("Attached").assertExists()
    }

    @Test
    fun `cancelling the reading leaves the form as it was, and no copy of the picture behind`() {
        reader.gate = CompletableDeferred()
        open { addTransaction() }
        waitFor("New expense")
        choose(galleryPicture())
        waitFor(READING_PICTURE)
        // The copy is made before it is read.
        until { drafts().isNotEmpty() }

        compose.onNodeWithText("Cancel").performClick()
        waitGone(READING_PICTURE)
        // Back on the form, as it was.
        compose.onNodeWithText("New expense").assertIsDisplayed()
        compose.onNodeWithContentDescription("Picture, Choose one").assertExists()
        assertTrue(compose.onAllNodesWithText("Attached").fetchSemanticsNodes().isEmpty())
        amountField().assert(hasText(""))
        until { drafts().isEmpty() }
    }

    @Test
    fun `a shared picture is read under a loading sheet before the form opens`() {
        val gate = CompletableDeferred<Unit>().also { reader.gate = it }
        receiveShares()
        compose.runOnUiThread { env.container.sharedText.offer(text = null, picture = galleryPicture()) }
        waitFor(READING_PICTURE)
        assertTrue(compose.onAllNodesWithText("New expense").fetchSemanticsNodes().isEmpty())

        gate.complete(Unit)
        waitFor("Filled in from a shared picture")
        assertTrue(compose.onAllNodesWithText(READING_PICTURE).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Attached").performScrollTo().assertExists()
    }

    @Test
    fun `cancelling the reading of a shared picture opens nothing and leaves nothing behind`() {
        reader.gate = CompletableDeferred()
        receiveShares()
        compose.runOnUiThread { env.container.sharedText.offer(text = null, picture = galleryPicture()) }
        waitFor(READING_PICTURE)
        until { drafts().isNotEmpty() }

        compose.onNodeWithText("Cancel").performClick()
        waitGone(READING_PICTURE)
        until { drafts().isEmpty() }
        assertTrue(compose.onAllNodesWithText("New expense").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Couldn't read the picture").fetchSemanticsNodes().isEmpty())
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `what saving says shows once the form has gone, not under it`() {
        val transaction = runBlocking {
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = 25_000,
                    accountId = finance.state.value.defaultAccount!!.id!!,
                    categoryId = finance.state.value.categories.first { it.name == "Food & dining" }.id,
                    occurredAt = LocalDateTime.of(2026, 10, 10, 12, 34),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                ),
            )
        }
        open { editTransaction(transaction) }
        waitFor("Save changes")
        amountField().performTextInput("0")
        compose.onNodeWithText("Save changes").performClick()
        waitFor("Changes saved")
        // By the time it shows, the form has gone: it isn't dimmed under the sheet.
        assertTrue(compose.onAllNodesWithText("Save changes").fetchSemanticsNodes().isEmpty())
    }

    // ------------------------------------------------- the number a picture shows

    private fun account(name: String, ending: String?) = runBlocking {
        val at = LocalDateTime.of(2026, 10, 12, 9, 0)
        finance.saveAccount(
            com.example.normpix.finance.domain.Account(
                name = name, kind = com.example.normpix.finance.domain.AccountKind.Bank, bankKey = "hdfc", numberEnding = ending,
                createdAt = at, updatedAt = at,
            ),
        )
    }

    private val cardReceipt = "To SWIGGY\n₹250\nCompleted\n10 Oct 2026, 12:34 pm\nHDFC Bank\n652850XXXXXXXX55\nUPI transaction ID\n428312345678"

    @Test
    fun `a picture that shows an account number nothing matches leaves the account to choose`() {
        account("HDFC Savings", ending = "1111")
        reader.text = cardReceipt
        open { addTransaction() }
        waitFor("New expense")
        // The form starts on the default account, Cash.
        compose.onNodeWithText("Cash").assertExists()
        choose(galleryPicture())

        waitFor("Filled in from the picture")
        waitFor("The message shows a number starting 652850 and ending 55, but none of your accounts has it.", substring = true)
        // Nothing was guessed: the account field is empty, and saving asks for one.
        assertTrue(compose.onAllNodesWithText("Cash").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Save expense").performClick()
        waitFor("Choose an account")
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `an account the user already chose stays when the picture shows another number`() {
        account("HDFC Savings", ending = "1111")
        reader.text = cardReceipt
        open { addTransaction() }
        waitFor("New expense")
        compose.onNodeWithText("Cash").performScrollTo().performClick()
        compose.onNodeWithText("HDFC Savings").performClick()
        compose.waitForIdle()
        choose(galleryPicture())

        waitFor("Filled in from the picture")
        compose.onNodeWithText("HDFC Savings").assertExists()
        assertTrue(compose.onAllNodesWithText("none of your accounts has it", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
