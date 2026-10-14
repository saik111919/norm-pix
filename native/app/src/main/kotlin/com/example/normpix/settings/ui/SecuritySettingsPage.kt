package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ScreenshotMonitor
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.normpix.BuildConfig
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.settings.AppLockScope
import com.example.normpix.settings.AppLockTimeout
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.StatusSurface
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.wallet.NfcAvailability
import com.example.normpix.wallet.TagRegistration
import com.example.normpix.wallet.nfc.registerNfcTag
import kotlinx.coroutines.launch

/**
 * Settings > Security & privacy: biometric lock (what it covers and when it
 * re-locks), NFC tap-to-reveal, and a plain statement of where data lives.
 *
 * Weakening the lock (turning it off, narrowing it, lengthening the timeout)
 * needs the same proof as unlocking whenever the wallet is currently locked,
 * so Settings can't be used to get around it. Turning it on always asks once,
 * which also proves the device can unlock before anything is locked behind it.
 */
@Composable
fun SecuritySettingsPage() {
    val navigator = LocalNavigator.current
    val container = LocalAppContainer.current
    val preferences = container.preferences
    val lock = container.lock
    val prefs by preferences.state.collectAsState()
    val lockVersion by lock.version.collectAsState()
    val snackbars = LocalSnackbars.current
    val scope = LocalAppScope.current
    val haptics = rememberAppHaptics()

    // Re-check when coming back from system settings (NFC switched on, a fingerprint enrolled).
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose {}
    }
    val biometrics by produceState<Boolean?>(null, resumes) { value = lock.isDeviceSupported() }
    val nfc = remember(resumes) { container.nfc.availability() }
    val overlays = LocalOverlays.current
    val tagCount by container.registeredTags.count.collectAsState()
    val locked = prefs.walletLockEnabled
    @Suppress("UNUSED_EXPRESSION")
    lockVersion

    /** Runs [change] once the user has shown it's them (only asked when the wallet is locked right now). */
    fun withIdentity(reason: String, always: Boolean = false, change: () -> Unit) {
        scope.launch {
            val current = preferences.state.value
            val ok = (!always && !lock.isWalletLocked(current)) || lock.authenticate(reason)
            if (ok) {
                haptics.selection()
                change()
            } else {
                snackbars.show("Not changed - couldn't confirm it's you", SnackbarKind.Error)
            }
        }
    }

    /** Asks for a tap and registers the tag; [then] runs once a tag is registered (or already was). */
    fun registerTag(then: () -> Unit = {}) {
        scope.launch {
            if (container.nfc.availability() != NfcAvailability.Available) {
                snackbars.show("Turn on NFC in system settings to register a tag", SnackbarKind.Warning)
                return@launch
            }
            when (registerNfcTag(container, overlays)) {
                TagRegistration.Added -> {
                    haptics.success()
                    snackbars.show("Tag registered")
                    then()
                }
                TagRegistration.AlreadyRegistered -> {
                    snackbars.show("That tag is already registered", SnackbarKind.Info)
                    then()
                }
                TagRegistration.LimitReached -> snackbars.show("You can register up to ${container.registeredTags.limit} tags", SnackbarKind.Warning)
                TagRegistration.Unavailable -> snackbars.show("Couldn't save the tag securely on this device", SnackbarKind.Error)
                TagRegistration.ChangesEveryTap, null -> Unit
            }
        }
    }

    AppSubPage(title = "Security & privacy", onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                StatusSurface(
                    tone = if (locked) StatusTone.Success else StatusTone.Info,
                    icon = if (locked) Icons.Outlined.VerifiedUser else Icons.Outlined.Shield,
                    title = if (locked) {
                        "${if (prefs.appLockScope == AppLockScope.EntireApp) "NormPix" else "Wallet"} is locked with biometrics"
                    } else {
                        "Biometric lock is off"
                    },
                    message = if (locked) {
                        prefs.appLockTimeout.description
                    } else {
                        "Anyone with your unlocked phone can open NormPix and see your cards and finances."
                    },
                )
                ListSection(
                    title = "Biometric lock",
                    footer = if (biometrics == false) {
                        "This device doesn't have a fingerprint, face or screen lock set up."
                    } else {
                        null
                    },
                ) {
                    item {
                        SwitchRow(
                            headline = "Require unlock",
                            supporting = "Fingerprint, face or screen lock",
                            leading = { Icon(Icons.Rounded.Fingerprint, contentDescription = null) },
                            checked = locked,
                            // Turning it off stays possible even if biometrics later disappear.
                            enabled = biometrics == true || locked,
                            onCheckedChange = { on ->
                                if (on) {
                                    withIdentity("Confirm it's you to turn on the lock", always = true) {
                                        preferences.setWalletLockEnabled(true)
                                    }
                                } else {
                                    withIdentity("Confirm it's you to turn off the lock") {
                                        preferences.setWalletLockEnabled(false)
                                    }
                                }
                            },
                        )
                    }
                }
                if (locked) {
                    ListSection(title = "Lock scope", modifier = Modifier.selectableGroup()) {
                        for (lockScope in AppLockScope.entries) {
                            item {
                                RadioRow(
                                    headline = lockScope.label,
                                    supporting = lockScope.description,
                                    selected = prefs.appLockScope == lockScope,
                                    onClick = {
                                        if (prefs.appLockScope != lockScope) {
                                            withIdentity("Confirm it's you to change the lock") { preferences.setAppLockScope(lockScope) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    ListSection(title = "Auto-lock", modifier = Modifier.selectableGroup()) {
                        for (timeout in AppLockTimeout.entries) {
                            item {
                                RadioRow(
                                    headline = timeout.label,
                                    supporting = timeout.description,
                                    selected = prefs.appLockTimeout == timeout,
                                    onClick = {
                                        if (prefs.appLockTimeout != timeout) {
                                            withIdentity("Confirm it's you to change the lock") { preferences.setAppLockTimeout(timeout) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                ListSection(
                    title = "Cards",
                    footer = if (prefs.nfcTagRevealEnabled && prefs.nfcRevealRegisteredOnly) {
                        "Lost your tag? Turn \"Only my registered tags\" off here - you'll be asked to confirm it's you."
                    } else {
                        null
                    },
                ) {
                    item {
                        SwitchRow(
                            headline = "Tap an NFC tag to reveal",
                            supporting = when (nfc) {
                                NfcAvailability.Available -> if (prefs.nfcRevealRegisteredOnly) {
                                    "Only a tag you've registered will work"
                                } else {
                                    "Hold any NFC tag near your phone to show a card number"
                                }
                                NfcAvailability.Disabled -> "Turn on NFC in system settings to use this"
                                NfcAvailability.Unsupported -> "Not available on this device"
                            },
                            leading = { Icon(Icons.Rounded.Nfc, contentDescription = null) },
                            checked = prefs.nfcTagRevealEnabled,
                            enabled = nfc != NfcAvailability.Unsupported,
                            onCheckedChange = { on ->
                                haptics.selection()
                                preferences.setNfcTagRevealEnabled(on)
                            },
                        )
                    }
                    if (prefs.nfcTagRevealEnabled) {
                        item {
                            SwitchRow(
                                headline = "Only my registered tags",
                                supporting = when {
                                    prefs.nfcRevealRegisteredOnly -> "Other tags and phones won't reveal a card"
                                    tagCount == 0 -> "Register your own tag, and only it will work"
                                    else -> "Only your $tagCount registered ${if (tagCount == 1) "tag" else "tags"} will work"
                                },
                                leading = { Icon(Icons.Outlined.Key, contentDescription = null) },
                                checked = prefs.nfcRevealRegisteredOnly,
                                // Turning it off stays possible whatever NFC is doing, so a lost tag never locks you out.
                                enabled = nfc == NfcAvailability.Available || prefs.nfcRevealRegisteredOnly,
                                onCheckedChange = { on ->
                                    when {
                                        // Tightening needs nothing; with no tag yet there is nothing to ask for, so register one first.
                                        on && tagCount > 0 -> {
                                            haptics.selection()
                                            preferences.setNfcRevealRegisteredOnly(true)
                                        }
                                        on -> withIdentity("Confirm it's you to register a tag") {
                                            registerTag { preferences.setNfcRevealRegisteredOnly(true) }
                                        }
                                        else -> withIdentity("Confirm it's you to allow any NFC tag") {
                                            preferences.setNfcRevealRegisteredOnly(false)
                                        }
                                    }
                                },
                            )
                        }
                        if (tagCount > 0) {
                            if (tagCount < container.registeredTags.limit) {
                                item {
                                    ListRow(
                                        headline = "Register another tag",
                                        supporting = "Up to ${container.registeredTags.limit}; any one of them will do",
                                        leading = { Icon(Icons.Outlined.AddCircleOutline, contentDescription = null) },
                                        onClick = { withIdentity("Confirm it's you to register a tag") { registerTag() } },
                                    )
                                }
                            }
                            item {
                                ListRow(
                                    headline = "Remove registered tags",
                                    supporting = "$tagCount registered. Any NFC tag will work again until you register one",
                                    leading = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                    supportingMaxLines = 3,
                                    onClick = {
                                        scope.launch {
                                            val sure = overlays.confirm(
                                                title = "Remove your registered tags?",
                                                message = "Any NFC tag will reveal a card again until you register one.",
                                                confirmLabel = "Remove",
                                                destructive = true,
                                            )
                                            if (sure) {
                                                withIdentity("Confirm it's you to remove your tags") {
                                                    container.registeredTags.clear()
                                                    preferences.setNfcRevealRegisteredOnly(false)
                                                    snackbars.show("Registered tags removed", SnackbarKind.Deleted)
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                ListSection(title = "Your data") {
                    item {
                        ListRow(
                            headline = "Everything stays on this device",
                            supporting = "No account and no sign-in, and nothing is sent anywhere. " +
                                "NormPix never reads SMS or connects to your bank.",
                            leading = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
                            supportingMaxLines = 4,
                        )
                    }
                    item {
                        ListRow(
                            headline = "Cards are encrypted",
                            supporting = "Card details are encrypted with AES-256-GCM, with the key kept in the Android Keystore.",
                            leading = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            supportingMaxLines = 4,
                        )
                    }
                    if (BuildConfig.SECURE_WINDOW) {
                        item {
                            ListRow(
                                headline = "Screen is protected",
                                supporting = "Screenshots and screen recording are blocked, and the app is hidden in Recents.",
                                leading = { Icon(Icons.Outlined.ScreenshotMonitor, contentDescription = null) },
                                supportingMaxLines = 4,
                            )
                        }
                    }
                }
            }
        }
    }
}
