package com.example.normpix.quickadd

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.EXTRA_PICTURE_WITHOUT_GRANT
import com.example.normpix.finance.share.SharedTransactionParser
import com.example.normpix.widgets.WidgetIntents
import com.example.normpix.widgets.WidgetRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the overlay is asked for: read from an intent, kept in order, never twice for one double tap, never printed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickAddRequestsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun share(text: String?, picture: Uri? = null): Intent =
        Intent(Intent.ACTION_SEND).setClass(context, QuickAddActivity::class.java).putExtra(EXTRA_HANDED_OVER, true).apply {
            if (text != null) putExtra(Intent.EXTRA_TEXT, text)
            if (picture == null) setType("text/plain") else setType("image/png").putExtra(Intent.EXTRA_STREAM, picture)
        }

    @Test
    fun `a picture handed over without the right to read it is still the share's picture, on the same terms`() {
        // ShareTransactionActivity's fallback when the sharing app gave no grant to pass on: not under EXTRA_STREAM.
        val other = Uri.parse("content://com.other.app.files/receipt.png")
        val without = Intent(Intent.ACTION_SEND).setClass(context, QuickAddActivity::class.java).setType("image/png")
            .putExtra(EXTRA_HANDED_OVER, true).putExtra(EXTRA_PICTURE_WITHOUT_GRANT, other)
        val request = quickAddRequestOf(without, context.packageName)
        assertTrue(request is QuickAddRequest.Share && request.picture == other, "the overlay tries it and says it couldn't read it")
        // Never one of NormPix's own files, never a file path - as for a picture that came with its grant.
        val own = Uri.parse("content://${context.packageName}.fileprovider/receipts/x.png")
        val ownShare = quickAddRequestOf(Intent(without).putExtra(EXTRA_PICTURE_WITHOUT_GRANT, own), context.packageName)
        assertTrue(ownShare is QuickAddRequest.Share && ownShare.picture == null)
        val file = quickAddRequestOf(Intent(without).putExtra(EXTRA_PICTURE_WITHOUT_GRANT, Uri.parse("file:///sdcard/x.png")), context.packageName)
        assertTrue(file is QuickAddRequest.Share && file.picture == null)
    }

    @Test
    fun `a share without the hand-over mark - a task brought back with its intent emptied - is no request at all`() {
        // As Android restarts the overlay after NormPix's process went (an update): its first intent, extras gone.
        val emptied = Intent(Intent.ACTION_SEND).setClass(context, QuickAddActivity::class.java).setType("text/plain")
        assertNull(quickAddRequestOf(emptied, context.packageName))
        // The mark alone is a real share with nothing in it, which the overlay says so of.
        val nothing = quickAddRequestOf(share(null), context.packageName)
        assertTrue(nothing is QuickAddRequest.Share && nothing.text.isEmpty() && nothing.picture == null)
    }

    @Test
    fun `a shortcut's, a tile's or a widget's add intent is an add of its type, and nothing else it carries is`() {
        for (type in TransactionType.entries) {
            val intent = WidgetIntents.intent(context, WidgetRequest.Add(type))
            assertEquals(QuickAddRequest.Add(type), quickAddRequestOf(intent, context.packageName))
        }
        // What opens the app itself is not the overlay's.
        assertNull(quickAddRequestOf(WidgetIntents.intent(context, WidgetRequest.Show(7)), context.packageName))
        assertNull(quickAddRequestOf(WidgetIntents.intent(context, WidgetRequest.Activity), context.packageName))
        assertNull(quickAddRequestOf(Intent(context, QuickAddActivity::class.java), context.packageName))
        assertNull(quickAddRequestOf(null, context.packageName))
    }

    @Test
    fun `reopened from Recents, nothing is asked again`() {
        val add = WidgetIntents.intent(context, WidgetRequest.Add(TransactionType.Expense)).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(quickAddRequestOf(add, context.packageName))
        assertNull(quickAddRequestOf(share("Rs 5").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY), context.packageName))
    }

    @Test
    fun `a share is read straight from the intent, its text and a picture of another app's only`() {
        val text = quickAddRequestOf(share("Sent Rs.250.00 To SWIGGY"), context.packageName) as QuickAddRequest.Share
        assertEquals("Sent Rs.250.00 To SWIGGY", text.text)
        assertNull(text.picture)

        val receipt = Uri.parse("content://com.example.payments.files/receipts/receipt.png")
        val picture = quickAddRequestOf(share(null, receipt), context.packageName) as QuickAddRequest.Share
        assertEquals("", picture.text)
        assertEquals(receipt, picture.picture)

        // NormPix's own files are never read back into a form, however they got on the intent.
        val own = Uri.parse("content://${context.packageName}.scanfiles/scan/card.jpg")
        assertNull((quickAddRequestOf(share(null, own), context.packageName) as QuickAddRequest.Share).picture)
        assertNull((quickAddRequestOf(share(null, Uri.parse("file:///data/data/x/card.jpg")), context.packageName) as QuickAddRequest.Share).picture)

        // A share with nothing in it is still a share: the overlay says there was nothing to add.
        val empty = quickAddRequestOf(share(null), context.packageName) as QuickAddRequest.Share
        assertEquals("", empty.text)
    }

    @Test
    fun `a text far longer than a payment message is kept only long enough to tell`() {
        val share = quickAddRequestOf(share("x".repeat(1_000_000)), context.packageName) as QuickAddRequest.Share
        assertEquals(SharedTransactionParser.MAX_LENGTH + 1, share.text.length)
    }

    @Test
    fun `what was shared is never in a log or a message`() {
        val share = QuickAddRequest.Share("Paid Rs 250 to Swiggy", Uri.parse("content://com.example.payments.files/receipt.png"))
        assertFalse("Swiggy" in share.toString() || "receipt" in share.toString())
    }

    @Test
    fun `requests are done in the order they came, a few at most`() {
        val queue = QuickAddRequests()
        assertTrue(queue.isEmpty)
        assertNull(queue.takeNext())

        val first = QuickAddRequest.Share("a")
        val second = QuickAddRequest.Add(TransactionType.Income)
        queue.offer(first)
        queue.offer(second)
        assertFalse(queue.isEmpty)
        assertEquals(listOf(first, second), queue.waiting.value)
        assertEquals(first, queue.takeNext())
        assertEquals(second, queue.takeNext())
        assertNull(queue.takeNext())

        repeat(QuickAddRequests.MAX_WAITING + 2) { queue.offer(QuickAddRequest.Share("share $it")) }
        assertEquals(QuickAddRequests.MAX_WAITING, queue.waiting.value.size)
        assertEquals("share 2", (queue.waiting.value.first() as QuickAddRequest.Share).text)
    }

    @Test
    fun `the same add asked for twice, as a double tap does, is done once`() {
        val queue = QuickAddRequests()
        val expense = QuickAddRequest.Add(TransactionType.Expense)
        queue.offer(expense)
        queue.offer(expense)
        assertEquals(1, queue.waiting.value.size)

        // Open now: asked for again meanwhile, it is not queued behind itself ...
        assertEquals(expense, queue.takeNext())
        queue.offer(expense)
        assertTrue(queue.waiting.value.isEmpty())
        assertFalse(queue.isEmpty, "still open")
        // ... but once it is over, asking again is a new request.
        queue.finished()
        assertTrue(queue.isEmpty)
        queue.offer(expense)
        assertEquals(1, queue.waiting.value.size)

        // Two shares are never taken for one.
        val shares = QuickAddRequests()
        shares.offer(QuickAddRequest.Share("same"))
        shares.offer(QuickAddRequest.Share("same"))
        assertEquals(2, shares.waiting.value.size)
    }
}
