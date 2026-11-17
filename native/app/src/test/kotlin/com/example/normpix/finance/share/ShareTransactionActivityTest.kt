package com.example.normpix.finance.share

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.MainActivity
import com.example.normpix.NormPixApplication
import com.example.normpix.quickadd.QuickAddActivity
import com.example.normpix.quickadd.QuickAddRequest
import com.example.normpix.quickadd.quickAddRequestOf
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The share sheet's way in: found for shared text and pictures, and hands each share on - once - to the overlay that opens the form
 * over the sharing app. The main screen is not opened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareTransactionActivityTest {
    private val app: NormPixApplication = ApplicationProvider.getApplicationContext()

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
        // The overlay it hands on to is not a target of its own: only NormPix reaches it.
        assertTrue(QuickAddActivity::class.java.name !in targetsFor("text/plain"))
        assertTrue(QuickAddActivity::class.java.name !in targetsFor("image/png"))
        val info = app.packageManager.getActivityInfo(ComponentName(app, ShareTransactionActivity::class.java), 0)
        assertTrue(info.exported)
        assertTrue(!app.packageManager.getActivityInfo(ComponentName(app, QuickAddActivity::class.java), 0).exported)
    }

    @Test
    fun `a share hands its text on to the overlay, not to the main screen`() {
        val controller = Robolectric.buildActivity(ShareTransactionActivity::class.java, send()).create()
        val activity = controller.get()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(QuickAddActivity::class.java.name, started.component?.className)
        // Over the sharing app: the overlay's own manifest entry (single task, empty affinity) makes its task, not a flag here, and
        // nothing opens the main screen.
        assertTrue((started.flags and Intent.FLAG_ACTIVITY_NEW_TASK) == 0)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals("Sent Rs.250.00 To SWIGGY On 10/10/26", sharedTextOf(started))
        assertEquals(QuickAddRequest.Share::class, quickAddRequestOf(started, app.packageName)!!::class)
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
    fun `a shared picture is handed on with the permission to read it`() {
        val activity = Robolectric.buildActivity(ShareTransactionActivity::class.java, sendPicture()).create().get()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(QuickAddActivity::class.java.name, started.component?.className)
        val share = quickAddRequestOf(started, app.packageName) as QuickAddRequest.Share
        assertEquals(receipt, share.picture)
        assertEquals("", share.text)
        // The permission to read the picture would end with this activity: the overlay is given it.
        assertEquals(receipt, started.clipData?.getItemAt(0)?.uri)
        assertTrue((started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `a picture of NormPix's own is never handed on`() {
        val own = Uri.parse("content://${app.packageName}.scanfiles/scan/card.jpg")
        val activity = Robolectric.buildActivity(ShareTransactionActivity::class.java, sendPicture(own)).create().get()

        val share = quickAddRequestOf(shadowOf(activity).nextStartedActivity, app.packageName) as QuickAddRequest.Share
        assertNull(share.picture)
    }

    @Test
    fun `only another app's content pictures are taken`() {
        assertEquals(receipt, sharedPictureOf(sendPicture(), app.packageName))
        // A file path, or one of NormPix's own files, could be a way for another app to have NormPix read its private files.
        assertNull(sharedPictureOf(sendPicture(Uri.parse("file:///data/data/${app.packageName}/files/card_vault.bin")), app.packageName))
        assertNull(sharedPictureOf(sendPicture(Uri.parse("content://${app.packageName}.scanfiles/scan/card.jpg")), app.packageName))
        // Android reads `user@name` as the provider `name`: a user in front doesn't make NormPix's own provider another app's.
        assertNull(sharedPictureOf(sendPicture(Uri.parse("content://0@${app.packageName}.scanfiles/scan/card.jpg")), app.packageName))
        assertNull(sharedPictureOf(sendPicture(Uri.parse("content://10@${app.packageName}.scanfiles/receipts/r.png")), app.packageName))
        assertNull(sharedPictureOf(sendPicture(Uri.parse("content://x@0@${app.packageName}.scanfiles/scan/card.jpg")), app.packageName))
        // ... while a picture shared from a work profile, which arrives with its user in front, is another app's and is taken.
        val fromWorkProfile = Uri.parse("content://10@com.bank.files/receipt.png")
        assertEquals(fromWorkProfile, sharedPictureOf(sendPicture(fromWorkProfile), app.packageName))
        // Not a picture, or not a URI at all.
        assertNull(sharedPictureOf(sendPicture(type = "text/plain"), app.packageName))
        assertNull(sharedPictureOf(sendPicture(picture = "content://not/a/uri"), app.packageName))
        assertNull(sharedPictureOf(sendPicture(picture = null), app.packageName))
        assertNull(sharedPictureOf(null, app.packageName))
    }

    @Test
    fun `a recreated or reopened share is not handed on again`() {
        val recreated = Robolectric.buildActivity(ShareTransactionActivity::class.java, send()).create(Bundle()).get()
        assertNull(shadowOf(recreated).nextStartedActivity)
        val fromRecents = send().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        val reopened = Robolectric.buildActivity(ShareTransactionActivity::class.java, fromRecents).create().get()
        assertNull(shadowOf(reopened).nextStartedActivity)
        assertTrue(reopened.isFinishing)
    }

    @Test
    fun `a share without readable text still opens the overlay, which explains`() {
        val activity = Robolectric.buildActivity(ShareTransactionActivity::class.java, send(text = 42)).create().get()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(QuickAddActivity::class.java.name, started.component?.className)
        val share = quickAddRequestOf(started, app.packageName) as QuickAddRequest.Share
        assertEquals("", share.text)
        assertNull(share.picture)
    }

    @Test
    fun `a text far longer than a payment message is cut before it is handed on`() {
        val long = "x".repeat(1_000_000)
        val activity = Robolectric.buildActivity(ShareTransactionActivity::class.java, send(text = long)).create().get()
        val share = quickAddRequestOf(shadowOf(activity).nextStartedActivity, app.packageName) as QuickAddRequest.Share
        // Only enough is kept to tell it is too long.
        assertEquals(SharedTransactionParser.MAX_LENGTH + 1, share.text.length)
    }

    @Test
    fun `anything but a send does nothing`() {
        val view = Intent(Intent.ACTION_VIEW).setClass(app, ShareTransactionActivity::class.java)
        val activity = Robolectric.buildActivity(ShareTransactionActivity::class.java, view).create().get()
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
}
