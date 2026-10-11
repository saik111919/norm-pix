package com.example.normpix.testing

import com.example.normpix.wallet.NfcAvailability
import com.example.normpix.wallet.NfcTagReveal

/** An NFC adapter a test can tap: it holds the session the app opened and hands it whichever tag ID the test chooses. */
class FakeNfcTagReveal(var state: NfcAvailability = NfcAvailability.Available) : NfcTagReveal {
    private var listener: ((ByteArray) -> Boolean)? = null

    /** Whether the app is waiting for a tag right now. */
    val listening: Boolean get() = listener != null

    override fun availability(): NfcAvailability = state

    override fun waitForTag(onTag: (id: ByteArray) -> Boolean) {
        listener = onTag
    }

    override fun cancel() {
        listener = null
    }

    /** Holds a tag with this ID to the phone (call it on the UI thread). True when the app accepted it, which ends the session. */
    fun tap(id: ByteArray): Boolean {
        val current = listener ?: return false
        val accepted = current(id)
        if (accepted) listener = null
        return accepted
    }
}
