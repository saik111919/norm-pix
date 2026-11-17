package com.example.normpix.quickadd

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.example.normpix.AppContainer
import com.example.normpix.BuildConfig
import com.example.normpix.NormPixApplication
import com.example.normpix.wallet.Authenticator
import com.example.normpix.wallet.BiometricAuthenticator

/**
 * The new-transaction sheet over whatever app the user is in, without opening NormPix itself: what an app's **Share** to NormPix
 * ([com.example.normpix.finance.share.ShareTransactionActivity] hands over to it), the app icon's shortcuts, the Quick Settings tile
 * and the Add widget's buttons open. It is a see-through screen holding only the app's own sheet - the same form, filled in from a
 * share or started on a type - and it goes when the sheet is closed and the snackbar a save says has gone, or at once on Back or a
 * touch outside the sheet. See [QuickAddHost] for what it does and how the lock holds it.
 *
 * It is not the main screen, but it does what the main screen does for the lock and the services that need an activity: it takes
 * [com.example.normpix.wallet.ActivityHost]'s activity and the lock's prompt while it is in front and gives the main screen's back
 * when it goes, and it tells the lock when it is left and returned to (so the timeout counts from the user leaving NormPix, whichever
 * of the two they left). Its window is secure in a release build, as the main screen's is.
 *
 * One at a time (`singleTask`, in a task of its own - an affinity of its own, `<applicationId>.quickadd` - so it never brings the main screen's task forward): a second
 * request while it is open ([onNewIntent]) waits behind the first, in [requests].
 */
class QuickAddActivity : FragmentActivity() {
    private val container: AppContainer get() = containerOf(application)

    /** What it has been asked to do and has not done yet. Not kept over a recreation: a recreated overlay has nothing to show and goes. */
    internal val requests = QuickAddRequests()

    // The main screen's, if it holds them: given back when this goes.
    private var previousActivity: FragmentActivity? = null
    private var previousAuthenticator: Authenticator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.SECURE_WINDOW) {
            // The ledger is on screen: no screenshots, no screen recording, and a blank thumbnail in the app switcher.
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // As the main screen: Android 10 to 14 would put a scrim behind the bars otherwise.
            @Suppress("DEPRECATION")
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        takeOver()
        // Recreated (the system restored it after the process went): the request was done the first time, and what was open is gone.
        if (savedInstanceState == null) offer(intent)
        hostIfAsked()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Queued behind what is open: the user's draft is not thrown away for it.
        offer(intent)
        hostIfAsked()
    }

    /**
     * Whether it has nothing to do is only known here, not in [onCreate]: a task Android brings back after NormPix's process went (an
     * update, say) restarts this with its old, emptied intent, and the request that brought it back arrives through [onNewIntent]
     * just before this. Nothing asked by now: it goes without showing anything.
     */
    override fun onResume() {
        super.onResume()
        if (!hosting) finish()
    }

    // Whether the overlay's content is set: once, on the first request.
    private var hosting = false

    private fun hostIfAsked() {
        if (hosting || requests.isEmpty) return
        hosting = true
        setContent { QuickAddHost(container, requests, onFinished = ::finish) }
    }

    private fun offer(intent: Intent?) {
        quickAddRequestOf(intent, packageName)?.let(requests::offer)
    }

    // `container.host.visible` is left alone on purpose (2026-11-16 review): it says the MAIN screen is in front, and the NFC card
    // outlet hands over a card readied in the main screen's sheet only while it is. The overlay holds no card, so with it in front a
    // card left ready behind it must not go to a reader nobody is looking at.
    override fun onStart() {
        super.onStart()
        container.lock.onAppResumed(container.preferences.state.value)
    }

    override fun onStop() {
        super.onStop()
        container.lock.onAppPaused(container.preferences.state.value)
    }

    override fun onDestroy() {
        giveBack()
        super.onDestroy()
    }

    /** Takes the activity slot and the lock's prompt, remembering the main screen's. */
    private fun takeOver() {
        val host = container.host
        previousActivity = host.activity?.takeIf { it !== this }
        previousAuthenticator = container.lock.authenticator
        host.activity = this
        container.lock.authenticator = authenticatorOf(this)
    }

    /**
     * Gives them back, only if they are still this one's: a main screen opened meanwhile has taken them itself and keeps them, and one
     * that has gone since is not given back to.
     */
    private fun giveBack() {
        val host = container.host
        if (host.activity !== this) return
        val back = previousActivity?.takeUnless { it.isFinishing || it.isDestroyed }
        host.activity = back
        container.lock.authenticator = if (back != null) previousAuthenticator else null
    }

    companion object {
        /** Where the activity gets the app's services; tests give it theirs. */
        internal var containerOf: (Application) -> AppContainer = { (it as NormPixApplication).container }

        /** What asks the user to prove it is them; tests give it one that answers. */
        internal var authenticatorOf: (FragmentActivity) -> Authenticator = { BiometricAuthenticator(it) }
    }
}
