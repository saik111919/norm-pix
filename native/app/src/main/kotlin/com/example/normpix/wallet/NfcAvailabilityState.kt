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
import com.example.normpix.navigation.LocalAppContainer

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
