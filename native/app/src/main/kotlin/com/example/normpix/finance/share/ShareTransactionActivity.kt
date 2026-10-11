package com.example.normpix.finance.share

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import com.example.normpix.MainActivity
import com.example.normpix.NormPixApplication

/**
 * Where Android's share sheet delivers what is shared with NormPix (Share in a payment or bank app, then NormPix): text,
 * or a picture such as a payment app's receipt. It shows nothing: it hands the share to the app's [SharedTextInbox] and
 * brings NormPix's own task to the front, where it is read into a draft the user checks and saves.
 *
 * A share target opens inside the sharing app's task. Doing the work here, instead of in [MainActivity], keeps NormPix to
 * a single main screen - with its lock, its NFC reader and its biometric prompt - rather than a second copy of it inside
 * the other app. Text goes through memory, never through the intent that opens the app (the system keeps a task's intent
 * with its recents).
 */
class ShareTransactionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated, or reopened from recents: this share was handed over the first time.
        val fresh = savedInstanceState == null && ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
        if (fresh && intent?.action == Intent.ACTION_SEND) {
            val picture = sharedPictureOf(intent, packageName)
            (application as NormPixApplication).container.sharedText.offer(sharedTextOf(intent), picture)
            val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (picture == null) {
                startActivity(open)
            } else {
                // The permission to read the picture belongs to this activity and ends with it: passing it on to the main
                // screen keeps the picture readable while the app is open (behind its lock, too), without copying it.
                try {
                    startActivity(
                        Intent(open).apply {
                            clipData = ClipData.newRawUri("", picture)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                    )
                } catch (_: SecurityException) {
                    // The sharing app gave no permission to pass on; the picture then turns out unreadable and the app says so.
                    startActivity(open)
                }
            }
        }
        finish()
    }
}
