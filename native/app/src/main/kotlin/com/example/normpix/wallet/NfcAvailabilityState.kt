package com.example.normpix.wallet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.normpix.availability.AvailabilityRule
import com.example.normpix.availability.ScheduledFeature
import com.example.normpix.availability.availabilityAt
import com.example.normpix.availability.rememberAvailability
import com.example.normpix.navigation.LocalAppContainer
import java.time.LocalDateTime

/**
 * Whether NFC can be used right now: read again whenever the screen comes back (NFC switched on in system settings) and
 * whenever NFC is switched on or off while it is on screen (quick settings), so what offers NFC shows and goes with it.
 */
@Composable
fun rememberNfcAvailability(): NfcAvailability {
    val nfc = LocalAppContainer.current.nfc
    val context = LocalContext.current
    var availability by remember(nfc) { mutableStateOf(nfc.availability()) }
    LifecycleResumeEffect(nfc) {
        availability = nfc.availability()
        onPauseOrDispose {}
    }
    DisposableEffect(nfc, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)) {
                    NfcAdapter.STATE_ON -> availability = NfcAvailability.Available
                    NfcAdapter.STATE_OFF -> availability = NfcAvailability.Disabled
                    else -> Unit // turning on or off: what it was until it has
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return availability
}

/**
 * Whether anything in the app offers the NFC card exchange: the phone's NFC has to be on, and so does the app's own switch for
 * it (Settings > Availability & Schedules, off until the user turns it on - and, on a schedule, only inside its hours). Either one
 * off and the Wallet's NFC button, its "Tap to add" rows and every card's Share button are gone, not greyed out.
 */
fun nfcExchangeOffered(phone: NfcAvailability, rule: AvailabilityRule, now: LocalDateTime): Boolean =
    phone == NfcAvailability.Available && rule.availabilityAt(now).isOpen

/** [nfcExchangeOffered], kept current: it follows the phone's NFC switch, the rule being edited, and a schedule opening or closing. */
@Composable
fun rememberNfcExchangeOffered(): Boolean {
    val phoneOn = rememberNfcAvailability() == NfcAvailability.Available
    val appOn = rememberAvailability(ScheduledFeature.NfcExchange).isOpen
    return phoneOn && appOn
}
