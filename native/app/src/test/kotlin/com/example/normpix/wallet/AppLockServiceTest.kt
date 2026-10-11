package com.example.normpix.wallet

import com.example.normpix.settings.AppLockScope
import com.example.normpix.settings.AppLockTimeout
import com.example.normpix.settings.AppPrefsState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

private class FakeAuthenticator(var succeed: Boolean = true) : Authenticator {
    var prompts = 0

    override suspend fun isSupported() = true

    override suspend fun authenticate(reason: String): Boolean {
        prompts++
        return succeed
    }
}

class AppLockServiceTest {
    private var clock = 1_000_000L
    private val lock = AppLockService(now = { clock }).apply { authenticator = FakeAuthenticator() }

    private fun prefs(scope: AppLockScope = AppLockScope.WalletOnly, timeout: AppLockTimeout = AppLockTimeout.Immediate) =
        AppPrefsState(walletLockEnabled = true, appLockScope = scope, appLockTimeout = timeout)

    private fun unlock() = runBlocking { assertTrue(lock.authenticate()) }

    @Test
    fun nothingIsLockedWhenTheLockIsOff() {
        val off = AppPrefsState(walletLockEnabled = false)
        assertFalse(lock.isAppLocked(off))
        assertFalse(lock.isWalletLocked(off))
    }

    @Test
    fun startsLockedAndUnlocksForTheSession() {
        val p = prefs()
        assertTrue(lock.isWalletLocked(p))
        assertFalse(lock.isAppLocked(p), "wallet-only scope never locks the whole app")
        unlock()
        assertFalse(lock.isWalletLocked(p))
    }

    @Test
    fun failedAuthenticationKeepsItLocked() = runBlocking {
        (lock.authenticator as FakeAuthenticator).succeed = false
        assertFalse(lock.authenticate())
        assertTrue(lock.isWalletLocked(prefs()))
    }

    @Test
    fun immediateRelocksWhenLeavingTheWalletTab() {
        val p = prefs(timeout = AppLockTimeout.Immediate)
        unlock()
        lock.onLeftWalletTab(p)
        assertTrue(lock.isWalletLocked(p))
    }

    @Test
    fun timedLockCountsFromLeavingNotFromUnlocking() {
        val p = prefs(timeout = AppLockTimeout.Seconds30)
        unlock()
        // Ten minutes of continuous use on the Wallet tab: still unlocked.
        clock += 10 * 60_000
        assertFalse(lock.isWalletLocked(p))
        // Leave for 20 seconds and come back: still unlocked.
        lock.onLeftWalletTab(p)
        clock += 20_000
        assertFalse(lock.isWalletLocked(p))
        lock.onEnteredWalletTab(p)
        assertFalse(lock.isWalletLocked(p))
        // Leave for 31 seconds: locked.
        lock.onLeftWalletTab(p)
        clock += 31_000
        assertTrue(lock.isWalletLocked(p))
        lock.onEnteredWalletTab(p)
        assertTrue(lock.isWalletLocked(p))
    }

    @Test
    fun backgroundTimeoutAppliesOnReturn() {
        val p = prefs(scope = AppLockScope.EntireApp, timeout = AppLockTimeout.Minute1)
        unlock()
        lock.onAppPaused(p)
        clock += 30_000
        lock.onAppResumed(p)
        assertFalse(lock.isAppLocked(p))
        lock.onAppPaused(p)
        clock += 61_000
        lock.onAppResumed(p)
        assertTrue(lock.isAppLocked(p))
        assertTrue(lock.isWalletLocked(p), "the app gate covers the wallet too")
    }

    @Test
    fun immediateLocksTheMomentTheAppGoesToTheBackground() {
        val p = prefs(scope = AppLockScope.EntireApp)
        unlock()
        lock.onAppPaused(p)
        assertTrue(lock.isAppLocked(p))
    }

    @Test
    fun sessionStaysUnlockedUntilTheProcessEnds() {
        val p = prefs(timeout = AppLockTimeout.Session)
        unlock()
        lock.onLeftWalletTab(p)
        lock.onAppPaused(p)
        clock += 6 * 60 * 60_000
        lock.onAppResumed(p)
        assertFalse(lock.isWalletLocked(p))
    }

    @Test
    fun theBiometricPromptItselfDoesNotRelock() = runBlocking {
        val p = prefs(scope = AppLockScope.EntireApp)
        val prompting = object : Authenticator {
            override suspend fun isSupported() = true

            override suspend fun authenticate(reason: String): Boolean {
                // The device-credential screen covers the activity while it's up.
                lock.onAppPaused(p)
                lock.onAppResumed(p)
                return true
            }
        }
        lock.authenticator = prompting
        assertTrue(lock.authenticate())
        assertFalse(lock.isAppLocked(p))
    }

    @Test
    fun aShortTripToTheCameraKeepsTheSession() {
        val p = prefs(scope = AppLockScope.EntireApp)
        unlock()
        lock.expectExternalActivity()
        lock.onAppPaused(p)
        assertFalse(lock.isAppLocked(p), "the open card form must not be locked away mid-scan")
        clock += 20_000
        lock.onAppResumed(p)
        assertFalse(lock.isAppLocked(p))
    }

    @Test
    fun aLongTripToTheCameraStillLocks() {
        val p = prefs(scope = AppLockScope.EntireApp)
        unlock()
        lock.expectExternalActivity()
        lock.onAppPaused(p)
        clock += AppLockService.EXTERNAL_GRACE_MS + 1
        lock.onAppResumed(p)
        assertTrue(lock.isAppLocked(p))
        // The grace is one-shot: the next ordinary trip away locks immediately again.
        unlock()
        lock.onAppPaused(p)
        assertTrue(lock.isAppLocked(p))
    }
}
