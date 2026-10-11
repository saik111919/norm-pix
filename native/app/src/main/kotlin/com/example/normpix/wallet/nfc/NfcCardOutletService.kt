package com.example.normpix.wallet.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.nio.charset.StandardCharsets

/**
 * Host Card Emulation (HCE) service for NFC Outlet.
 *
 * When a user activates NFC Outlet mode for a card, this service responds to
 * incoming ISO-DEP APDU commands from an NFC Inlet reader with the card payload.
 */
class NfcCardOutletService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return STATUS_FAILED

        // Check if command is SELECT AID: 00 A4 04 00 07 F0 01 02 03 04 05 06 00
        if (isSelectAidCommand(commandApdu)) {
            // Even with a card armed, nothing is shared outside the user's schedule (the sheet normally tears it down first).
            if (!isAllowed()) return STATUS_FAILED
            val payload = activePayload
            if (payload != null) {
                val dataBytes = payload.toByteArray(StandardCharsets.UTF_8)
                val response = ByteArray(dataBytes.size + 2)
                System.arraycopy(dataBytes, 0, response, 0, dataBytes.size)
                response[response.size - 2] = 0x90.toByte()
                response[response.size - 1] = 0x00.toByte()

                onCardTransferredCallback?.invoke()
                return response
            }
        }

        return STATUS_FAILED
    }

    override fun onDeactivated(reason: Int) {
        // Session ended
    }

    private fun isSelectAidCommand(apdu: ByteArray): Boolean {
        if (apdu.size < SELECT_AID_HEADER.size) return false
        for (i in SELECT_AID_HEADER.indices) {
            if (apdu[i] != SELECT_AID_HEADER[i]) return false
        }
        return true
    }

    companion object {
        val AID = byteArrayOf(
            0xF0.toByte(), 0x01.toByte(), 0x02.toByte(),
            0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(),
        )

        val SELECT_AID_COMMAND = byteArrayOf(
            0x00.toByte(), // CLA
            0xA4.toByte(), // INS (Select)
            0x04.toByte(), // P1 (Select by DF name / AID)
            0x00.toByte(), // P2 (First or only occurrence)
            0x07.toByte(), // Lc (Length of AID = 7)
            0xF0.toByte(), 0x01.toByte(), 0x02.toByte(),
            0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(),
            0x00.toByte(), // Le (Expected response length)
        )

        private val SELECT_AID_HEADER = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), 0x07.toByte(),
            0xF0.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(),
        )

        private val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())

        /** What a reader sees when this service answers but has no card to send (Outlet not active). */
        internal const val SW_NOTHING_SHARED = 0x6F00

        @Volatile
        var activePayload: String? = null

        /** Whether sharing is allowed right now (the NFC schedule); the app wires it to the user's availability rule. */
        @Volatile
        var isAllowed: () -> Boolean = { true }

        @Volatile
        var onCardTransferredCallback: (() -> Unit)? = null

        fun setOutletCard(payload: String?, onTransferred: (() -> Unit)? = null) {
            activePayload = payload
            onCardTransferredCallback = onTransferred
        }

        fun clearOutlet() {
            activePayload = null
            onCardTransferredCallback = null
        }
    }
}
