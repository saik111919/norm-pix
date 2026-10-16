package com.example.normpix

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.reminders.ReminderNotifications
import com.example.normpix.updates.SystemUpdateNotifier
import com.example.normpix.wallet.BiometricAuthenticator
import com.example.normpix.wallet.expiry.CardExpiryNotifications

// BiometricPrompt needs a FragmentActivity host.
class MainActivity : FragmentActivity() {
    private val container get() = (application as NormPixApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.SECURE_WINDOW) {
            // Card numbers, CVVs and cardholder names render on screen: block
            // screenshots and screen recording, and blank the recents thumbnail
            // so a revealed card never ends up in the app switcher.
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        container.host.activity = this
        container.lock.authenticator = BiometricAuthenticator(this)
        // After a configuration change or process restore the launch intent was already handled.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            NormPixRoot(
                container = container,
                // A fresh start writes the app's name out first; coming back to a restored activity doesn't.
                showSplash = savedInstanceState == null,
                onDarkChanged = { dark ->
                    // The app's own light/dark choice can differ from the system's.
                    val transparent = android.graphics.Color.TRANSPARENT
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(transparent, transparent) { dark },
                        navigationBarStyle = SystemBarStyle.auto(transparent, transparent) { dark },
                    )
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * A tapped reminder notification asks for that reminder's alert screen; a tapped card-expiry one for Wallet; a tapped
     * update one for the update page (and its Install button for the update to be installed).
     */
    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1) ?: -1
        if (id >= 0) {
            container.reminderAlerts.value = id
            intent?.removeExtra(ReminderNotifications.EXTRA_REMINDER_ID)
        }
        if (intent?.getBooleanExtra(CardExpiryNotifications.EXTRA_OPEN_WALLET, false) == true) {
            container.walletRequests.value = true
            intent.removeExtra(CardExpiryNotifications.EXTRA_OPEN_WALLET)
        }
        if (intent?.getBooleanExtra(SystemUpdateNotifier.EXTRA_OPEN_UPDATE, false) == true) {
            // Install first, so the page that opens for the request knows to install.
            if (intent.getBooleanExtra(SystemUpdateNotifier.EXTRA_INSTALL_UPDATE, false)) container.updateInstallRequests.value = true
            container.updateRequests.value = true
            intent.removeExtra(SystemUpdateNotifier.EXTRA_OPEN_UPDATE)
            intent.removeExtra(SystemUpdateNotifier.EXTRA_INSTALL_UPDATE)
        }
    }

    override fun onStart() {
        super.onStart()
        // Back in front: expiry reminders are re-checked against today's date (a day may have passed, or the clock changed).
        container.expiry.rearmAll()
        container.lock.onAppResumed(container.preferences.state.value)
    }

    /**
     * Back from Android's installer or its "Install unknown apps" setting (both cover this activity without always
     * stopping it): an update waiting on the user carries on.
     */
    override fun onResume() {
        super.onResume()
        container.updates?.onForeground()
    }

    override fun onPause() {
        container.updates?.onBackground()
        super.onPause()
    }

    /**
     * A copied card detail whose time ran out while the app was away is dealt with once the window has focus: only
     * the app in focus may look at the clipboard, so this is the moment it can tell whether the clip is still its own
     * or something newer that must be left alone.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) container.clipboard.reconcile()
    }

    override fun onStop() {
        super.onStop()
        container.lock.onAppPaused(container.preferences.state.value)
    }

    override fun onDestroy() {
        if (container.host.activity === this) {
            container.host.activity = null
            container.lock.authenticator = null
        }
        super.onDestroy()
    }
}
