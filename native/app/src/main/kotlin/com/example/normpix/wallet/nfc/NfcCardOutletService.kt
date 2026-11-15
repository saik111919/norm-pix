package com.example.normpix.wallet.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Host Card Emulation (HCE) service for NFC Outlet: the sharing phone's end of the card exchange.
 *
 * Android routes a reader's commands for NormPix's AID here, on the main thread, and only while the phone is unlocked
 * (`apduservice.xml` requireDeviceUnlock). Every command goes to the one [responder], which runs the encrypted exchange (version 2:
 * key agreement, a code both phones show, Match, then the card sealed for that phone alone - see [NfcShareResponder]).
 */
class NfcCardOutletService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return NfcShareResponder.statusBytes(SW_NOTHING_SHARED)
        return responder.answer(commandApdu)
    }

    /** The reader went away, or selected another AID: whatever session was under way ends (a new one starts with a new SELECT). */
    override fun onDeactivated(reason: Int) {
        responder.linkLost()
    }

    companion object {
        val AID: ByteArray get() = NfcShareResponder.AID

        /** SELECT by name of [AID]: what a NormPix reader sends first. */
        val SELECT_AID_COMMAND: ByteArray get() = NfcShareResponder.SELECT_AID_COMMAND

        /** What a reader sees when this service answers but has no card to send (Outlet not active, or not allowed now). */
        internal const val SW_NOTHING_SHARED = NfcShareResponder.SW_NOTHING_SHARED

        /** Whether sharing is allowed right now (the NFC schedule); the app wires it to the user's availability rule. */
        @Volatile
        var isAllowed: () -> Boolean = { true }

        /** The one sharing session for the app: the service answers through it, the sheet arms it and confirms or declines on it. */
        val responder = NfcShareResponder(isAllowed = { isAllowed() })

        /**
         * Arms [payload] (the card's plain JSON) to be shared, sealed, with the next reader whose code the user matches. [events]
         * tell the sheet about the code, a code withdrawn, and the card gone.
         */
        fun setOutletCard(payload: String, events: NfcShareResponder.Events) {
            val bytes = payload.toByteArray(Charsets.UTF_8)
            responder.arm(bytes, events)
            // The responder keeps its own copy, zeroed when the card is given or disarmed.
            bytes.fill(0)
        }

        /** Nothing is shared any more (the sheet closed, or Share left). */
        fun clearOutlet() {
            responder.disarm()
        }

        /** Whether a card is waiting to be shared. */
        val isArmed: Boolean get() = responder.isArmed
    }
}
