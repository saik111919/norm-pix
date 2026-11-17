package com.example.normpix.finance.share

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import com.example.normpix.quickadd.EXTRA_HANDED_OVER
import com.example.normpix.quickadd.QuickAddActivity

/**
 * Where Android's share sheet delivers what is shared with NormPix (Share in a payment or bank app, then NormPix): text,
 * or a picture such as a payment app's receipt. It shows nothing itself: it hands the share on to [QuickAddActivity], the
 * see-through screen that opens the new-transaction sheet over the sharing app - filled in from the share, with the picture
 * attached - without opening NormPix.
 *
 * This class stays the share target (its name is the component the share sheet and other apps know), and the one that gets
 * the share and the permission to read a shared picture; what is passed on is only what [sharedTextOf] and [sharedPictureOf]
 * took from it - a picture of NormPix's own is never passed on - and the permission to read the picture goes with it, as it
 * would end with this activity.
 */
class ShareTransactionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated, or reopened from recents: this share was handed over the first time.
        val fresh = savedInstanceState == null && ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
        if (fresh && intent?.action == Intent.ACTION_SEND) {
            val picture = sharedPictureOf(intent, packageName)
            // The same cut a share's text gets in the overlay: a message far longer than a payment's is only kept long enough to say so.
            val text = sharedTextOf(intent)?.take(SharedTransactionParser.MAX_LENGTH + 1)
            val hand = Intent(this, QuickAddActivity::class.java).setAction(Intent.ACTION_SEND).putExtra(EXTRA_HANDED_OVER, true)
            if (text != null) hand.putExtra(Intent.EXTRA_TEXT, text)
            if (picture == null) {
                startActivity(hand.setType("text/plain"))
            } else {
                hand.setType(intent.type).putExtra(Intent.EXTRA_STREAM, picture)
                try {
                    startActivity(
                        Intent(hand).apply {
                            clipData = ClipData.newRawUri("", picture)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                    )
                } catch (_: SecurityException) {
                    // The sharing app gave no permission to pass on; the picture then turns out unreadable and the overlay says so. Not
                    // under EXTRA_STREAM: Android adds the same grant to any ACTION_SEND carrying one, refuses it again, and the share
                    // target would crash (seen on the emulator, 2026-11-16).
                    try {
                        startActivity(
                            Intent(hand).apply {
                                removeExtra(Intent.EXTRA_STREAM)
                                putExtra(EXTRA_PICTURE_WITHOUT_GRANT, picture)
                            },
                        )
                    } catch (_: RuntimeException) {
                        // Nothing more to try: the share is dropped rather than NormPix crashing in the sharing app's face.
                    }
                }
            }
        }
        finish()
    }
}
