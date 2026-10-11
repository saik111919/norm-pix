package com.example.normpix.finance.share

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.MainActivity
import com.example.normpix.NormPixApplication
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The share sheet's way in: found for shared text, hands the text over once, opens NormPix's own task. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareTransactionActivityTest {
    private val app: NormPixApplication = ApplicationProvider.getApplicationContext()
    private val inbox get() = app.container.sharedText

    private fun send(text: Any? = "Sent Rs.250.00 To SWIGGY On 10/10/26", type: String = "text/plain") =
        Intent(Intent.ACTION_SEND).setClass(app, ShareTransactionActivity::class.java).setType(type).apply {
            when (text) {
                is String -> putExtra(Intent.EXTRA_TEXT, text)
                is Int -> putExtra(Intent.EXTRA_TEXT, text)
            }
        }

    private fun targetsFor(type: String): List<String> =
        app.packageManager.queryIntentActivities(Intent(Intent.ACTION_SEND).setType(type), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.name }

    @Test
    fun `NormPix is offered for shared text and pictures`() {
        assertTrue(ShareTransactionActivity::class.java.name in targetsFor("text/plain"))
        assertTrue(ShareTransactionActivity::class.java.name in targetsFor("image/png"))
        assertTrue(ShareTransactionActivity::class.java.name in targetsFor("image/jpeg"))
        assertTrue(ShareTransactionActivity::class.java.name !in targetsFor("application/pdf"))
        assertTrue(ShareTransactionActivity::class.java.name !in targetsFor("video/mp4"))
        // The main screen stays out of the share sheet.
        assertTrue(MainActivity::class.java.name !in targetsFor("text/plain"))
        assertTrue(MainActivity::class.java.name !in targetsFor("image/png"))
        val info = app.packageManager.getActivityInfo(ComponentName(app, ShareTransactionActivity::class.java), 0)
        assertTrue(info.exported)
    }

    @Test
    fun `a share hands its text over and opens the app's own task`() {
        val controller = Robolectric.buildActivity(ShareTransactionActivity::class.java, send()).create()
        val activity = controller.get()

        assertEquals(listOf("Sent Rs.250.00 To SWIGGY On 10/10/26"), inbox.waiting.value.map { it.text })
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertTrue((started.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        // The text travels in memory, not in the intent the system keeps with the task.
        assertNull(started.extras?.getCharSequence(Intent.EXTRA_TEXT))
        assertTrue(activity.isFinishing)
    }

    private val receipt = Uri.parse("content://com.example.payments.files/receipts/receipt.png")

    private fun sendPicture(picture: Any? = receipt, type: String = "image/png") =
        Intent(Intent.ACTION_SEND).setClass(app, ShareTransactionActivity::class.java).setType(type).apply {
            when (picture) {
                is Uri -> putExtra(Intent.EXTRA_STREAM, picture)
                is String -> putExtra(Intent.EXTRA_STREAM, picture)
            }
        }

    @Test
    fun `a shared picture is handed over with the permission to read it`() {
        val activity = Robolectric.buildActivity(ShareTransactionActivity::class.java, sendPicture()).create().get()

        val share = inbox.waiting.value.single()
        assertEquals(receipt, share.picture)
        assertEquals("", share.text)
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        // The permission to read the picture would end with this activity: the main screen is given it.
        assertEquals(receipt, started.clipData?.getItemAt(0)?.uri)
        assertTrue((started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `only another app's content pictures are taken`() {
        assertEquals(receipt, sharedPictureOf(sendPicture(), app.packageName))
        // A file path, or one of NormPix's own files, could be a way for another app to have NormPix read its private files.
        assertNull(sharedPictureOf(sendPicture(Uri.parse("file:///data/data/${app.packageName}/files/card_vault.bin")), app.packageName))
        assertNull(sharedPictureOf(sendPicture(Uri.parse("content://${app.packageName}.scanfiles/scan/card.jpg")), app.packageName))
        // Not a picture, or not a URI at all.
        assertNull(sharedPictureOf(sendPicture(type = "text/plain"), app.packageName))
        assertNull(sharedPictureOf(sendPicture(picture = "content://not/a/uri"), app.packageName))
        assertNull(sharedPictureOf(sendPicture(picture = null), app.packageName))
        assertNull(sharedPictureOf(null, app.packageName))
    }

    @Test
    fun `a recreated or reopened share is not handed over again`() {
        Robolectric.buildActivity(ShareTransactionActivity::class.java, send()).create(Bundle())
        val fromRecents = send().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        val reopened = Robolectric.buildActivity(ShareTransactionActivity::class.java, fromRecents).create().get()
        assertTrue(inbox.waiting.value.isEmpty())
        assertNull(shadowOf(reopened).nextStartedActivity)
        assertTrue(reopened.isFinishing)
    }

    @Test
    fun `a share without readable text still opens the app, which explains`() {
        val activity = Robolectric.buildActivity(ShareTransactionActivity::class.java, send(text = 42)).create().get()
        assertEquals(listOf(""), inbox.waiting.value.map { it.text })
        assertEquals(MainActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test
    fun `anything but a send does nothing`() {
        val view = Intent(Intent.ACTION_VIEW).setClass(app, ShareTransactionActivity::class.java)
        val activity = Robolectric.buildActivity(ShareTransactionActivity::class.java, view).create().get()
        assertTrue(inbox.waiting.value.isEmpty())
        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `reading a share's text never throws`() {
        assertNull(sharedTextOf(null))
        assertNull(sharedTextOf(Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_TEXT, "Rs 1 paid")))
        assertNull(sharedTextOf(send(text = null)))
        assertNull(sharedTextOf(send(text = 42)))
        assertEquals("Rs 1 paid", sharedTextOf(send(text = "Rs 1 paid")))
        val withSubject = send(text = "₹250 paid to Swiggy").putExtra(Intent.EXTRA_SUBJECT, "Payment receipt")
        assertEquals("Payment receipt\n₹250 paid to Swiggy", sharedTextOf(withSubject))
        // A subject the text already contains isn't repeated.
        val repeated = send(text = "Payment receipt: ₹250 paid").putExtra(Intent.EXTRA_SUBJECT, "Payment receipt")
        assertEquals("Payment receipt: ₹250 paid", sharedTextOf(repeated))
    }

    @Test
    fun `the inbox gives each share out once and keeps only a few`() {
        val box = SharedTextInbox()
        val first = box.offer("a")
        assertTrue(box.take(first))
        assertTrue(!box.take(first))
        repeat(SharedTextInbox.MAX_WAITING + 2) { box.offer("share $it") }
        assertEquals(SharedTextInbox.MAX_WAITING, box.waiting.value.size)
        assertEquals("share 2", box.waiting.value.first().text)
        // Far too long to be a payment message: only enough is kept to tell.
        assertEquals(SharedTransactionParser.MAX_LENGTH + 1, box.offer("x".repeat(1_000_000)).text.length)
    }
}
