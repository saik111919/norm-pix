package com.example.normpix.wallet

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.normpix.settings.AppLockScope
import com.example.normpix.settings.AppLockTimeout
import com.example.normpix.settings.AppPrefsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Asks the user to prove it is them (biometrics, or the device credential as a fallback). */
interface Authenticator {
    suspend fun isSupported(): Boolean

    suspend fun authenticate(reason: String): Boolean
}

/** [Authenticator] backed by the system `BiometricPrompt`, hosted by [activity]. */
class BiometricAuthenticator(private val activity: FragmentActivity) : Authenticator {
    private val allowed: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

    override suspend fun isSupported(): Boolean =
        BiometricManager.from(activity).canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun authenticate(reason: String): Boolean = suspendCancellableCoroutine { continuation ->
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(false)
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(reason)
                .setAllowedAuthenticators(allowed)
                .build(),
        )
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
/**
 * Manages app and wallet lock state, authentication, and timeouts. Pure
 * session state in memory: a process restart always starts locked.
 *
 * Timeouts count from when the user *left* (switched away from the Wallet
 * tab, or put NormPix in the background), never from when they unlocked, so
 * someone actively using the wallet is never locked out mid-use.
 *
 * [version] changes whenever a lock state may have changed, so screens that
 * read [isAppLocked] / [isWalletLocked] re-evaluate.
 */
class AppLockService(private val now: () -> Long = System::currentTimeMillis) {
    /** Set by the activity; null while no activity can show a prompt. */
    @Volatile
    var authenticator: Authenticator? = null

    /** True while the system prompt is up, so lifecycle events it causes don't re-lock. */
    @Volatile
    var isAuthenticating: Boolean = false
        private set

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private var appUnlocked = false
    private var walletUnlocked = false

    /** When the user switched away from the Wallet tab while it was unlocked (wallet-only scope). */
    private var walletLeftAt: Long? = null
    private var backgroundedAt: Long? = null

    /** Set while NormPix itself sent the user to another app's screen (camera, file picker). */
    @Volatile
    private var externalSince: Long? = null

    private fun changed() = _version.update { it + 1 }

    suspend fun isDeviceSupported(): Boolean = try {
        authenticator?.isSupported() ?: false
    } catch (_: Exception) {
        false
    }

    /** Whether the whole app is currently locked. */
    fun isAppLocked(prefs: AppPrefsState): Boolean =
        prefs.walletLockEnabled && prefs.appLockScope == AppLockScope.EntireApp && !appUnlocked

    /** Whether the wallet tab is currently locked. */
    fun isWalletLocked(prefs: AppPrefsState): Boolean {
        if (!prefs.walletLockEnabled) return false
        // With whole-app scope the app gate protects the wallet too.
        if (prefs.appLockScope == AppLockScope.EntireApp) return isAppLocked(prefs)
        if (!walletUnlocked) return true
        val left = walletLeftAt ?: return false
        return awayTooLong(prefs.appLockTimeout, now() - left)
    }

    private fun awayTooLong(timeout: AppLockTimeout, awayMs: Long): Boolean {
        if (timeout == AppLockTimeout.Immediate) return true
        val limit = timeout.millis ?: return false // session: stays unlocked until the process dies
        return awayMs > limit
    }

    /** Called when the user leaves the Wallet tab for another tab. */
    fun onLeftWalletTab(prefs: AppPrefsState) {
        if (prefs.appLockScope == AppLockScope.EntireApp || !walletUnlocked) return
        if (prefs.appLockTimeout == AppLockTimeout.Immediate) walletUnlocked = false else walletLeftAt = now()
        changed()
    }

    /** Called when the user switches to the Wallet tab: settles a pending timeout. */
    fun onEnteredWalletTab(prefs: AppPrefsState) {
        if (walletUnlocked && isWalletLocked(prefs)) walletUnlocked = false
        walletLeftAt = null
        changed()
    }

    /**
     * Call right before opening another app's screen on the user's behalf (the
     * camera for a card scan, the system file picker for a backup), so coming
     * back from it doesn't count as leaving NormPix. Only covers a short trip:
     * after [EXTERNAL_GRACE_MS] away the normal rules apply again.
     */
    fun expectExternalActivity() {
        externalSince = now()
    }

    /** Called when the app is paused / backgrounded. */
    fun onAppPaused(prefs: AppPrefsState) {
        if (isAuthenticating) return
        backgroundedAt = now()
        if (externalSince != null) return
        // Lock right away (not on return), so nothing sensitive is on screen when NormPix comes back.
        if (prefs.appLockTimeout == AppLockTimeout.Immediate) lockAll()
    }

    /** Called when the app resumes from background. */
    fun onAppResumed(prefs: AppPrefsState) {
        val since = backgroundedAt
        if (isAuthenticating || since == null) return
        val away = now() - since
        backgroundedAt = null
        val external = externalSince != null
        externalSince = null
        val expired = if (external) away > EXTERNAL_GRACE_MS && awayTooLong(prefs.appLockTimeout, away)
        else awayTooLong(prefs.appLockTimeout, away)
        if (expired) lockAll() else changed()
    }

    /** Authenticates; unlocks both the app and the wallet for the session on success. */
    suspend fun authenticate(reason: String = "Unlock with biometrics"): Boolean {
        val auth = authenticator ?: return false
        isAuthenticating = true
        return try {
            val ok = auth.authenticate(reason)
            if (ok) unlockAll()
            ok
        } catch (_: Exception) {
            false
        } finally {
            isAuthenticating = false
        }
    }

    /** Marks both app and wallet as unlocked for the current session. */
    fun unlockAll() {
        appUnlocked = true
        walletUnlocked = true
        walletLeftAt = null
        changed()
    }

    /** Locks both app and wallet. */
    fun lockAll() {
        appUnlocked = false
        walletUnlocked = false
        walletLeftAt = null
        changed()
    }

    companion object {
        /** How long a trip to the camera or file picker may take before NormPix re-locks anyway. */
        const val EXTERNAL_GRACE_MS = 120_000L
    }
}
