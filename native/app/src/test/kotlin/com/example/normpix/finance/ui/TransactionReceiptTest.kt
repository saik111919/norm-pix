package com.example.normpix.finance.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.RecordingReceiptSharer
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.SeeThroughColors
import com.example.normpix.ui.components.solidColorScheme
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A transaction's details as a receipt - badge, who it was with and the amount above a torn line, the facts below it - and
 * what is done with it from there: Share (as an image, which works; a PDF, still to be built), Edit, Delete.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class TransactionReceiptTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now().withNano(0)
    private val finance get() = env.container.finance
    private val actions = mutableListOf<DetailAction?>()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("transaction_receipt_test")
    }

    @After
    fun tearDown() = env.close()

    private fun seed(payee: String? = "Amazon", note: String? = null, type: TransactionType = TransactionType.Expense): FinanceTransaction = runBlocking {
        finance.load()
        val bank = finance.saveAccount(Account(name = "HDFC Savings", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        val savings = finance.saveAccount(Account(name = "Rainy day", kind = AccountKind.Savings, createdAt = now, updatedAt = now))
        val shopping = finance.state.value.categories.first { it.name == "Shopping" }
        finance.saveTransaction(
            FinanceTransaction(
                type = type,
                amount = 1_402_00,
                accountId = bank.id!!,
                toAccountId = if (type == TransactionType.Transfer) savings.id else null,
                categoryId = if (type == TransactionType.Transfer) null else shopping.id,
                payee = payee,
                note = note,
                occurredAt = now.minusMinutes(5),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun show(transaction: FinanceTransaction) {
        compose.setContent {
            env.Content {
                // No GPU here to read the receipt back from: stand in a plain picture of its size.
                TransactionDetailsSheet(transaction, snapshot = { android.graphics.Bitmap.createBitmap(600, 900, android.graphics.Bitmap.Config.ARGB_8888) }) { actions += it }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theReceiptNamesWhoItWasWith_theAmount_andTheFactsInOrder() {
        val transaction = seed(note = "Birthday gift")
        show(transaction)

        compose.onNode(hasText("Amazon") and isHeading()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Expense ₹1,402.00").assertExists()
        for (label in listOf("Date", "Time", "Category", "Paid from", "Note", "Reference")) compose.onNodeWithText(label).assertExists()
        compose.onNodeWithText("Shopping").assertExists()
        compose.onNodeWithText("HDFC Savings").assertExists()
        compose.onNodeWithText("Birthday gift").assertExists()
        compose.onNodeWithText(receiptReference(transaction.id!!)).assertExists()
    }

    @Test
    fun withoutAPayeeTheCategoryIsTheTitle_andWithoutANoteThereIsNoNoteRow() {
        show(seed(payee = null))

        compose.onNode(hasText("Shopping") and isHeading()).assertExists()
        compose.onAllNodesWithText("Note").assertCountEquals(0)
    }

    @Test
    fun aTransferShowsBothAccounts() {
        show(seed(payee = null, type = TransactionType.Transfer))

        compose.onNode(hasText("Transfer") and isHeading()).assertExists()
        compose.onNodeWithText("From").assertExists()
        compose.onNodeWithText("To").assertExists()
        compose.onNodeWithText("HDFC Savings").assertExists()
        compose.onNodeWithText("Rainy day").assertExists()
        compose.onAllNodesWithText("Category").assertCountEquals(0)
    }

    @Test
    fun editAndDeleteAnswerTheSheet() {
        show(seed())

        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Delete").performClick()

        assertEquals(listOf(DetailAction.Edit, DetailAction.Delete), actions.toList())
    }

    @Test
    fun shareOffersImageAndPdf_onlyTheImageWorks_theOtherSaysItIsComingSoon_andSendsNothing() {
        show(seed())

        compose.onNodeWithText("Share receipt").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Share as Image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Share as PDF").assertIsDisplayed()
        compose.onAllNodesWithText("Coming soon").assertCountEquals(1)
        compose.onAllNodesWithText("isn't available yet", substring = true).assertCountEquals(0)

        compose.onNodeWithText("Share as PDF").performClick()
        compose.onNodeWithText("Sharing as PDF isn't available yet", substring = true).assertIsDisplayed()

        assertTrue(env.receipts.shared.isEmpty(), "nothing was sent")
        assertTrue(actions.isEmpty(), "choosing a format does not edit or delete the transaction")
    }

    @Test
    fun shareAsImage_offersTheReceiptAsAPng_andClosesTheOptions() {
        val transaction = seed()
        show(transaction)

        compose.onNodeWithText("Share receipt").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Share as Image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Share as Image").performClick()
        compose.waitUntil(20_000) { env.receipts.shared.isNotEmpty() }

        val shared = env.receipts.shared.single()
        assertEquals("NormPix-receipt-${receiptReference(transaction.id!!)}.png", shared.fileName)
        val picture = android.graphics.BitmapFactory.decodeByteArray(shared.png, 0, shared.png.size)
        assertTrue(picture != null && picture.width > 200 && picture.height > picture.width / 2, "a real picture of the receipt")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Share as Image").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun theSharedPictureIsSolid_evenWhenItIsSharedFromASheetThatLetsThePageShowThrough() {
        val transaction = seed()
        var ground = 0
        compose.setContent {
            env.Content {
                val solid = solidColorScheme
                ground = solid.surfaceContainerLow.toArgb()
                // As on a phone with blur: the sheet's tones are tints with a little alpha, not colours.
                SeeThroughColors(enabled = true, ground = solid.surfaceContainerLow) {
                    TransactionDetailsSheet(transaction, snapshot = { android.graphics.Bitmap.createBitmap(600, 900, android.graphics.Bitmap.Config.ARGB_8888) }) { }
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Share receipt").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Share as Image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Share as Image").performClick()
        compose.waitUntil(20_000) { env.receipts.shared.isNotEmpty() }

        val shared = env.receipts.shared.single()
        val picture = android.graphics.BitmapFactory.decodeByteArray(shared.png, 0, shared.png.size)
        assertEquals(255, android.graphics.Color.alpha(picture.getPixel(2, 2)), "nothing round the receipt is see-through")
        assertEquals(ground, picture.getPixel(2, 2), "the picture is laid on the sheet's own solid tone")
    }

    @Test
    fun ifTheImageCannotBeOffered_theOptionsStayAndSayWhy() {
        env.close()
        env = TestEnvironment("transaction_receipt_test_b", receipts = RecordingReceiptSharer(works = false))
        show(seed())

        compose.onNodeWithText("Share receipt").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Share as Image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Share as Image").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Couldn't prepare the image", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Share as Image").assertIsDisplayed()
    }

    @Test
    fun theFramedPictureIsTheReceiptOnItsBackground_withRoomAllRound() {
        val receipt = android.graphics.Bitmap.createBitmap(100, 80, android.graphics.Bitmap.Config.ARGB_8888)
        receipt.eraseColor(android.graphics.Color.RED)
        receipt.setPixel(0, 0, android.graphics.Color.TRANSPARENT)

        val png = framedReceiptPng(receipt, background = android.graphics.Color.BLUE, margin = 10)
        val framed = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size)

        assertEquals(120, framed.width)
        assertEquals(100, framed.height)
        assertEquals(android.graphics.Color.BLUE, framed.getPixel(0, 0), "the margin is the background")
        assertEquals(android.graphics.Color.RED, framed.getPixel(60, 50), "the receipt sits inside it")
        assertEquals(android.graphics.Color.BLUE, framed.getPixel(10, 10), "and what is see-through on it shows the background")
    }

    @Test
    fun theFramedPictureIsNeverSeeThrough_whateverBackgroundItIsGiven() {
        val receipt = android.graphics.Bitmap.createBitmap(100, 80, android.graphics.Bitmap.Config.ARGB_8888)
        receipt.eraseColor(android.graphics.Color.RED)
        // The sheet's tones are tints with a little alpha; laid on as they are, the margin of the picture came out clear.
        val tint = android.graphics.Color.argb(19, 0x1B, 0x1B, 0x20)

        val png = framedReceiptPng(receipt, background = tint, margin = 10)
        val framed = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size)

        assertEquals(255, android.graphics.Color.alpha(framed.getPixel(0, 0)), "the margin is solid")
        assertEquals(android.graphics.Color.rgb(0x1B, 0x1B, 0x20), framed.getPixel(0, 0), "in the background's own colour")
    }
}
