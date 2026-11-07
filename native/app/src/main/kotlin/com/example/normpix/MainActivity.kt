package com.example.normpix

import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.window.SplashScreenView
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.normpix.ui.components.LocalLiveMotion
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.example.normpix.navigation.CookieMotion
import com.example.normpix.navigation.LaunchScreen
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.reminders.ReminderNotifications
import com.example.normpix.reminders.ReminderOverlay
import com.example.normpix.settings.LaunchAnimation
import com.example.normpix.updates.SystemUpdateNotifier
import com.example.normpix.wallet.BiometricAuthenticator
import com.example.normpix.wallet.expiry.CardExpiryNotifications
import com.example.normpix.widgets.QuickAddShortcuts
import com.example.normpix.widgets.WidgetIntents
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** How long Android's own launch screen takes to fade once the app lets it go (when it is not the cookie's): short, so a splash is not hidden behind it. */
private const val SYSTEM_SPLASH_FADE_MS = 90L

/** The cookie's intro in the launch screen's icon (`avd_splash.xml`) has nothing visible left to move this long after Android starts it. */
private const val LAUNCH_INTRO_MS = CookieMotion.LAUNCH_DELAY_MS + CookieMotion.INTRO_SETTLED_MS.toLong()

/** How long the launch screen takes to fade once the splash lets it go (see [CookieMotion.RELEASE_FADE_MS]). */
private const val LAUNCH_SCREEN_FADE_MS = CookieMotion.RELEASE_FADE_MS

/** The most the app keeps a held launch screen if nothing takes it away (the splash that should have is gone, or never came). */
private const val LAUNCH_SCREEN_FAILSAFE_MS = 4_000L

// BiometricPrompt needs a FragmentActivity host.
class MainActivity : FragmentActivity() {
    private val container get() = (application as NormPixApplication).container

    private val launchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Android 12 and later draw their own launch screen over every cold start, from the first moment - before this process, this
     * activity or a first frame exist - and hand it to the app once it has drawn. The cookie's intro is played *there* (the icon is
     * an animated vector, `avd_splash.xml`), so there is no empty screen while the app starts and nothing the app is busy with can
     * make it stutter; the app keeps that screen up, draws the same settled cookie under it, and takes it away when it is ready to
     * open onto the app (see [LaunchScreen] and CookieSplash). Earlier versions have no such screen: the app draws the cookie itself.
     */
    private var launchScreen by mutableStateOf<LaunchScreen>(
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) LaunchScreen.None else LaunchScreen.Pending,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Add expense, Add income and Transfer on the app's icon (press and hold); each opens the app on that sheet, once unlocked.
        if (savedInstanceState == null) QuickAddShortcuts.publish(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { view -> onLaunchScreen(view, coldStart = savedInstanceState == null) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) keepLaunchScreenThemeInStep()
        }
        if (BuildConfig.SECURE_WINDOW) {
            // Card numbers, CVVs and cardholder names render on screen: block
            // screenshots and screen recording, and blank the recents thumbnail
            // so a revealed card never ends up in the app switcher.
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Deprecated in Android 15 (API 35); still what stops the
            // system putting a scrim behind the status bar on Android 10 to 14.
            @Suppress("DEPRECATION")
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        container.host.activity = this
        container.lock.authenticator = BiometricAuthenticator(this)
        // After a configuration change or process restore the launch intent was already handled.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            // The real app, on a screen: the one place endless motion (the hero's weather) may run - see LocalLiveMotion.
            CompositionLocalProvider(LocalLiveMotion provides true) {
                NormPixRoot(
                    container = container,
                    // A fresh start writes the app's name out first; coming back to a restored activity doesn't.
                    showSplash = savedInstanceState == null,
                    launchScreen = launchScreen,
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
    }

    /**
     * Android hands over its launch screen. When it is the cookie's - a cold start, the Cookie launch choice, animations on - it is
     * kept over the app, and the splash is told when its intro ends and how to take it away. Otherwise (the name's splash, a start
     * that is not a cold one, animations off) it fades away at once, as it always did.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun onLaunchScreen(view: SplashScreenView, coldStart: Boolean) {
        val animatedIcon = (view.iconAnimationDuration?.toMillis() ?: 0L) > 0L
        val animationsOn = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        val cookie = coldStart && animatedIcon && animationsOn &&
            container.preferences.state.value.launchAnimation == LaunchAnimation.Cookie
        if (!cookie) {
            launchScreen = LaunchScreen.None
            view.animate().alpha(0f).setDuration(SYSTEM_SPLASH_FADE_MS).withEndAction { view.remove() }.start()
            return
        }
        // How much of the intro is left to play: none when the app was slower than it is long.
        val played = view.iconAnimationStart?.let { Duration.between(it, Instant.now()).toMillis() } ?: LAUNCH_INTRO_MS
        val left = (LAUNCH_INTRO_MS - played).coerceAtLeast(0L)
        launchScreen = LaunchScreen.Held(System.nanoTime() + left * 1_000_000L) {
            view.animate().alpha(0f).setDuration(LAUNCH_SCREEN_FADE_MS).withEndAction { view.remove() }.start()
        }
        view.postDelayed({ view.remove() }, LAUNCH_SCREEN_FAILSAFE_MS)
    }

    /**
     * Tells Android which launch screen to use for the next start (from Android 13; it keeps the choice): the cookie's is the
     * manifest's own, so for it the choice is cleared, and the name's splash gets an empty one, as it writes the name itself.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun keepLaunchScreenThemeInStep() {
        val screen = splashScreen
        launchScope.launch {
            container.preferences.state.map { it.launchAnimation }.distinctUntilChanged().collect { animation ->
                val theme = if (animation == LaunchAnimation.Cookie) Resources.ID_NULL else R.style.Theme_NormPix_LaunchBlank
                // A call into the system, kept off the main thread.
                runCatching { screen.setSplashScreenTheme(theme) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * A tapped reminder notification asks for that reminder's alert screen; a tapped card-expiry one for Wallet; a tapped
     * update one for the update page (and its Install button for the update to be installed); a widget's tap for what it
     * offers (a new transaction, one transaction, Activity).
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
        // A home-screen widget's button or row: the UI does it once the app is unlocked.
        WidgetIntents.requestOf(intent)?.let { request ->
            container.widgetRequests.value = request
            intent?.let(WidgetIntents::clear)
        }
    }

    override fun onStart() {
        super.onStart()
        container.host.visible = true
        // NormPix is in front: the reminder is its own alert screen's now, so a floating card still up over it (the notification's
        // "Open", say, brought us here) would only say the same thing twice.
        ReminderOverlay.dismiss()
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
        container.host.visible = false
        container.lock.onAppPaused(container.preferences.state.value)
    }

    override fun onDestroy() {
        launchScope.cancel()
        if (container.host.activity === this) {
            container.host.activity = null
            container.lock.authenticator = null
        }
        super.onDestroy()
    }
}
