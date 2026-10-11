package com.example.normpix.wallet

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag

enum class NfcAvailability { Available, Disabled, Unsupported }

/**
 * Thin wrapper around the platform NFC reader mode for the wallet's "tap an NFC tag to reveal" gesture. The tag's
 * contents are never read - only its hardware ID, so the caller can tell *which* tag it was (see [RegisteredTags]).
 */
interface NfcTagReveal {
    fun availability(): NfcAvailability

    /**
     * Starts listening. [onTag] gets the ID of each tag that is tapped, on the main thread, and answers whether it
     * accepts it: true ends the session, false keeps listening for another tap. The ID identifies a tag the user owns,
     * so it is never logged or stored in the clear.
     */
    fun waitForTag(onTag: (id: ByteArray) -> Boolean)

    fun cancel()
}

class ReaderModeNfcTagReveal(private val host: ActivityHost) : NfcTagReveal {
    private var active: Activity? = null

    private fun adapter(activity: Activity): NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    override fun availability(): NfcAvailability {
        val activity = host.activity ?: return NfcAvailability.Unsupported
        val adapter = adapter(activity) ?: return NfcAvailability.Unsupported
        return if (adapter.isEnabled) NfcAvailability.Available else NfcAvailability.Disabled
    }

    override fun waitForTag(onTag: (id: ByteArray) -> Boolean) {
        val activity = host.activity ?: return
        val adapter = adapter(activity) ?: return
        cancel()
        active = activity
        adapter.enableReaderMode(
            activity,
            { tag: Tag ->
                val id = tag.id.copyOf()
                activity.runOnUiThread {
                    // A session that has been cancelled since this tap was detected must not act on it.
                    if (active === activity && onTag(id)) cancel()
                }
            },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null,
        )
    }

    override fun cancel() {
        val activity = active ?: return
        active = null
        runCatching { adapter(activity)?.disableReaderMode(activity) }
    }
}
