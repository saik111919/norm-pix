package com.example.normpix.settings.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.statement.StatementFixtures
import com.example.normpix.testing.TestEnvironment
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Settings > Data & backup > Import a bank statement, as the user goes through it: the row, the system's picker (answered by a
 * stand-in that hands over a made-up file), the sheet that guesses the columns, the preview with a tick box on every row, and the
 * result - with the real sheets, the real ledger (in memory) and the page's own picker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class StatementImportFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Android's document picker, as the user using it: answers each launch at once with [answer] (null: backed out). */
    private class FakePicker(var answer: Uri?) : ActivityResultRegistryOwner {
        val intents = mutableListOf<Intent>()
        val contracts = mutableListOf<ActivityResultContract<*, *>>()

        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                contracts += contract
                intents += contract.createIntent(ApplicationProvider.getApplicationContext(), input)
                @Suppress("UNCHECKED_CAST")
                dispatchResult(requestCode, answer as O)
            }
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val statement = Uri.parse("content://statements.example/statement.csv")
    private val picker = FakePicker(statement)
    private lateinit var env: TestEnvironment
    private val finance get() = env.container.finance
    private val now = LocalDateTime.of(2026, 11, 15, 10, 0)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("statement_import_flow_test")
    }

    @After
    fun tearDown() = env.close()

    private fun file(text: String) = file(text.toByteArray(Charsets.UTF_8))

    private fun file(bytes: ByteArray) {
        shadowOf(context.contentResolver).registerInputStreamSupplier(statement) { ByteArrayInputStream(bytes) }
    }

    private fun addAccount(name: String, kind: AccountKind = AccountKind.Bank) = runBlocking {
        finance.saveAccount(Account(name = name, kind = kind, createdAt = now, updatedAt = now))
    }

    private fun open() {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                env.Content { DataSettingsPage() }
            }
        }
        compose.waitForIdle()
    }

    // Texts inside a picker field are under its own description (clearAndSetSemantics): they are only in the unmerged tree.
    private fun shown(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun field(label: String) = compose.onNodeWithText(label, useUnmergedTree = true)

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(20_000) { shown(text, substring) }

    private fun tapRow() {
        compose.onNodeWithText("Import a bank statement").performScrollTo().performClick()
    }

    private fun savedIn(name: String) = runBlocking {
        val id = finance.state.value.accounts.first { it.name == name }.id
        finance.repository.snapshot().transactions.filter { it.accountId == id }
    }

    private fun pickAccount(name: String) {
        field("Which account is this for?").performClick()
        waitFor(name)
        compose.onAllNodesWithText(name, useUnmergedTree = true).onFirst().performClick()
        compose.waitForIdle()
    }

    // ------------------------------------------------------------------- the row

    @Test
    fun `the Data page offers it, and says what file it takes and what it keeps`() {
        open()
        compose.onNodeWithText("Import a bank statement").performScrollTo()
        assertTrue(shown("A CSV file from your bank's website or app"))
        assertTrue(shown("keeps nothing of it", substring = true))
        assertTrue(shown("can be imported again safely", substring = true))
    }

    @Test
    fun `with sample data on the row says so and opens nothing`() {
        env.preferences.setDummyDataEnabled(true)
        open()
        compose.onNodeWithText("Import a bank statement").performScrollTo()
        assertTrue(shown("Sample data is on. Turn it off to import into your own accounts."))
        compose.onNodeWithText("Import a bank statement").performClick()
        compose.waitForIdle()
        assertTrue(picker.intents.isEmpty())
    }

    @Test
    fun `the picker is asked for one openable file of the types a CSV is filed under`() {
        picker.answer = null
        open()
        tapRow()
        compose.waitUntil(20_000) { compose.waitForIdle(); picker.intents.isNotEmpty() }
        assertTrue(picker.contracts.single() is OpenStatement)
        val intent = picker.intents.single()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(Intent.CATEGORY_OPENABLE in intent.categories.orEmpty())
        assertEquals(BackupFiles.STATEMENT_TYPES.toList(), intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.toList())
        for (type in listOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "application/vnd.ms-excel")) {
            assertTrue(type in BackupFiles.STATEMENT_TYPES, type)
        }
        assertFalse(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, true))
        // Backed out: nothing opens.
        compose.waitForIdle()
        assertFalse(shown("Which account is this for?"))
    }

    // ----------------------------------------------------------- the files it refuses

    @Test
    fun `a file over two megabytes is refused and says so`() {
        file(ByteArray(2 * 1024 * 1024 + 10) { 'a'.code.toByte() })
        open()
        tapRow()
        waitFor("That file is too large")
        assertTrue(shown("up to 2 MB", substring = true))
        assertFalse(shown("Which account is this for?"))
    }

    @Test
    fun `a pdf and a file with no table are refused in words`() {
        file("%PDF-1.7 not a statement".toByteArray())
        open()
        tapRow()
        waitFor("NormPix can't read a PDF")
        assertTrue(shown("Download the statement as a CSV", substring = true))
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()

        file("Name,City\nSomeone,Somewhere\n")
        tapRow()
        waitFor("No transactions found")
        assertFalse(shown("Which account is this for?"))
    }

    // ------------------------------------------------------------------ the columns

    @Test
    fun `the sheet shows the columns it guessed, and what it found`() {
        file(StatementFixtures.withWithdrawals)
        open()
        tapRow()
        waitFor("Which account is this for?")
        // The guesses, by the file's own headings.
        for (guess in listOf("Narration", "Withdrawal Amt.", "Deposit Amt.", "Chq./Ref.No.", "dd/MM/yy")) {
            assertTrue(shown(guess), guess)
        }
        // Date: the first "Date" column, not "Value Dt"; and the money is two columns, so no single amount is chosen.
        assertTrue(shown("3 transactions found, 4 lines skipped"))
        assertTrue(shown("Amount (one column)"))
    }

    @Test
    fun `each field's sheet lists the columns with their first values, and picking one changes the field`() {
        file(StatementFixtures.withWithdrawals)
        open()
        tapRow()
        waitFor("Which account is this for?")
        field("Reference").performClick()
        waitFor("The bank's reference, used to find what is in NormPix already")
        // Every column is offered with what is in it, and "None" to leave the field out.
        assertTrue(shown("Leave this out"))
        assertTrue(shown("01/10/26 · 05/10/26 · 09/10/26"))
        field("Value Dt").performClick()
        compose.waitForIdle()
        assertFalse(shown("The bank's reference, used to find what is in NormPix already"))
        assertTrue(shown("Value Dt"))
    }

    @Test
    fun `dates that read both ways ask, with two of them read both ways, and Review waits for the answer`() {
        file("Date,Narration,Debit,Credit\n04/03/2026,Tea,40.00,\n05/03/2026,Coffee,60.00,\n06/03/2026,Pay,,100.00\n")
        open()
        tapRow()
        waitFor("Which account is this for?")
        assertTrue(shown("Choose how the dates are written"))
        assertTrue(shown("04/03/2026 is 4 Mar 2026 (dd/MM) or 3 Apr 2026 (MM/dd).", substring = true))
        assertTrue(shown("05/03/2026 is 5 Mar 2026 (dd/MM) or 3 May 2026 (MM/dd).", substring = true))
        compose.onNodeWithText("Review").performClick()
        waitFor("Choose how the dates are written.")
        assertFalse(shown("Review the transactions"))

        field("Date format").performClick()
        waitFor("How the dates in this file are written")
        field("dd/MM/yyyy").performClick()
        waitFor("3 transactions found")
        compose.onNodeWithText("Review").performClick()
        waitFor("Review the transactions")
        assertTrue(shown("Import 3 of 3"))
    }

    @Test
    fun `Review asks for the account first when there is more than one`() {
        addAccount("HDFC Savings")
        file(StatementFixtures.withDebitCredit)
        open()
        tapRow()
        waitFor("Which account is this for?")
        compose.onNodeWithText("Review").performClick()
        waitFor("Choose the account this statement is for.")
        assertFalse(shown("Review the transactions"))
    }

    // ------------------------------------------------------------------ the preview

    @Test
    fun `the preview has a tick box on every row, a summary, and the account it goes into`() {
        addAccount("HDFC Savings")
        file(StatementFixtures.withWithdrawals)
        open()
        tapRow()
        waitFor("Which account is this for?")
        pickAccount("HDFC Savings")
        compose.onNodeWithText("Review").performClick()
        waitFor("Review the transactions")

        assertTrue(shown("They go into HDFC Savings.", substring = true))
        assertTrue(shown("Import 3 of 3 (4 lines skipped)"))
        for (payee in listOf("Sample Cafe", "Example Employer Ltd", "Example Store")) compose.onNodeWithText(payee).assertIsOn()
        // The narration as the bank wrote it is under the short payee.
        assertTrue(shown("UPI-SAMPLE CAFE-samplecafe@bank-BANK0000001-428312345678-UPI"))
        assertTrue(shown("Import 3"))

        // Unticking a row changes the summary and the button.
        compose.onNodeWithText("Example Store").performClick()
        compose.onNodeWithText("Example Store").assertIsOff()
        assertTrue(shown("Import 2 of 3 (4 lines skipped)"))
        compose.onNodeWithText("Select all").performClick()
        assertTrue(shown("Import 3 of 3 (4 lines skipped)"))
        compose.onNodeWithText("Clear all").performClick()
        assertTrue(shown("Import 0 of 3 (4 lines skipped)"))
        compose.onNodeWithText("Select all").performClick()
        assertTrue(shown("Import 3 of 3 (4 lines skipped)"))
    }

    @Test
    fun `an entry that is in NormPix already is unticked and says so`() {
        val cash = finance.state.value.defaultAccount!!
        addAccount("HDFC Savings")
        runBlocking {
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = 49_900,
                    accountId = cash.id!!,
                    categoryId = finance.state.value.categories.first { it.name == "Other" && it.parentId == null }.id,
                    occurredAt = LocalDateTime.of(2026, 10, 9, 18, 40),
                    payee = "Store near home",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        file(StatementFixtures.withWithdrawals)
        open()
        tapRow()
        waitFor("Which account is this for?")
        pickAccount("HDFC Savings")
        compose.onNodeWithText("Review").performClick()
        waitFor("Review the transactions")

        assertTrue(shown("Import 2 of 3 (1 already in NormPix, 4 lines skipped)"))
        assertTrue(shown("Already in NormPix, in ${cash.name}"))
        compose.onNodeWithText("Example Store").assertIsOff()
        compose.onNodeWithText("Sample Cafe").assertIsOn()
        // It can still be ticked.
        compose.onNodeWithText("Example Store").performClick()
        assertTrue(shown("Import 3 of 3 (1 already in NormPix, 4 lines skipped)"))
    }

    @Test
    fun `the arrow goes back to the columns with the account kept`() {
        addAccount("HDFC Savings")
        file(StatementFixtures.withDebitCredit)
        open()
        tapRow()
        waitFor("Which account is this for?")
        pickAccount("HDFC Savings")
        compose.onNodeWithText("Review").performClick()
        waitFor("Review the transactions")
        compose.onNodeWithContentDescription("Change the columns").performClick()
        waitFor("Which account is this for?")
        compose.waitUntil(20_000) { compose.waitForIdle(); !shown("Review the transactions") }
        assertTrue(shown("HDFC Savings"))
    }

    // ------------------------------------------------------------------- the import

    @Test
    fun `importing saves the ticked rows into the account, with the references in the notes, and says so`() {
        addAccount("HDFC Savings")
        file(StatementFixtures.withWithdrawals)
        open()
        tapRow()
        waitFor("Which account is this for?")
        pickAccount("HDFC Savings")
        compose.onNodeWithText("Review").performClick()
        waitFor("Review the transactions")
        compose.onNodeWithText("Example Store").performClick()
        compose.onNodeWithText("Import 2").performClick()

        waitFor("2 transactions imported into HDFC Savings")
        val saved = savedIn("HDFC Savings").sortedBy { it.occurredAt }
        assertEquals(2, saved.size)
        assertEquals(listOf("Sample Cafe", "Example Employer Ltd"), saved.map { it.payee })
        assertEquals(listOf(125_000L, 8_500_050L), saved.map { it.amount })
        assertEquals(listOf(TransactionType.Expense, TransactionType.Income), saved.map { it.type })
        assertTrue(saved[0].note!!.startsWith("Ref 428312345678 · "))
        assertTrue(saved[1].note!!.startsWith("Ref N123456789012 · "))
        assertTrue(savedIn("Cash").isEmpty())
        // The sheets are gone; the page shows the result.
        assertFalse(shown("Review the transactions"))
    }

    @Test
    fun `importing the same statement again finds every row already there`() {
        addAccount("HDFC Savings")
        file(StatementFixtures.withDebitCredit)
        open()
        tapRow()
        waitFor("Which account is this for?")
        pickAccount("HDFC Savings")
        compose.onNodeWithText("Review").performClick()
        waitFor("Review the transactions")
        assertTrue(shown("Import 2 of 2"))
        compose.onNodeWithText("Import 2").performClick()
        waitFor("2 transactions imported into HDFC Savings")
        compose.waitUntil(20_000) { compose.waitForIdle(); !shown("Review the transactions") }
        assertEquals(2, savedIn("HDFC Savings").size)

        // The same file, once more: nothing is ticked, and both rows say why.
        tapRow()
        waitFor("Which account is this for?")
        pickAccount("HDFC Savings")
        compose.onNodeWithText("Review").performClick()
        waitFor("Review the transactions")
        assertTrue(shown("Import 0 of 2 (2 already in NormPix)"))
        assertEquals(2, compose.onAllNodesWithText("Already in NormPix", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(2, savedIn("HDFC Savings").size)
    }
}
