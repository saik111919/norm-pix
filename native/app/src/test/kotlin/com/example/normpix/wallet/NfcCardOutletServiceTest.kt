package com.example.normpix.wallet

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.NormPixApplication
import com.example.normpix.R
import com.example.normpix.availability.AvailabilityMode
import com.example.normpix.availability.AvailabilityRule
import com.example.normpix.availability.ScheduledFeature
import android.nfc.cardemulation.HostApduService
import com.example.normpix.wallet.nfc.NfcCardOutletService
import com.example.normpix.wallet.nfc.NfcPairing
import com.example.normpix.wallet.nfc.NfcShareInitiator
import com.example.normpix.wallet.nfc.NfcShareResponder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * What the card-sharing service hands a reader is a card's number, expiry and CVV, sealed for the phone whose code the user
 * matched (exchange version 2; the exchange itself is `NfcShareExchangeTest`). It answers one reader, once, and only while the
 * user is sharing: with NormPix in front, inside their schedule, on a phone that is unlocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NfcCardOutletServiceTest {
    private val app: NormPixApplication = ApplicationProvider.getApplicationContext()
    private val service = NfcCardOutletService()
    private val select = NfcCardOutletService.SELECT_AID_COMMAND
    private val nothing = byteArrayOf(0x6F, 0x00)
    private val allowedByTheApp = NfcCardOutletService.isAllowed
    private val payload = """{"type":"normpix_card","cardNumber":"4111111111111111","expiryMonth":"09","expiryYear":"29"}"""

    /** What the sheet is told; Match is tapped as soon as a code arrives when [matchAtOnce]. */
    private inner class Sheet(val matchAtOnce: Boolean = true) : NfcShareResponder.Events {
        var transfers = 0
        var withdrawn = 0
        val codes = mutableListOf<NfcShareResponder.Pairing>()
        override fun onCode(pairing: NfcShareResponder.Pairing) {
            codes += pairing
            if (matchAtOnce) NfcCardOutletService.responder.confirm(pairing.id)
        }
        override fun onCodeWithdrawn() {
            withdrawn++
        }
        override fun onTransferred() {
            transfers++
        }
    }

    /** A reader running the whole exchange against the service, as Android delivers it. */
    private fun readThroughTheService(): NfcShareInitiator.Result =
        NfcShareInitiator.run({ service.processCommandApdu(it, null) }, onCode = {}, sleep = {}, clock = { 0L })

    @BeforeTest
    fun setUp() {
        NfcCardOutletService.clearOutlet()
    }

    @AfterTest
    fun tearDown() {
        NfcCardOutletService.clearOutlet()
        NfcCardOutletService.isAllowed = allowedByTheApp
    }

    @Test
    fun `a card is handed to one reader, once`() {
        NfcCardOutletService.isAllowed = { true }
        val sheet = Sheet()
        NfcCardOutletService.setOutletCard(payload, sheet)

        assertEquals(NfcShareInitiator.Result.Card(payload), readThroughTheService())
        assertEquals(1, sheet.transfers)
        // The sheet still says "Card shared": a second reader held to the phone meanwhile gets nothing.
        assertFalse(NfcCardOutletService.isArmed)
        assertContentEquals(nothing, service.processCommandApdu(select, null))
        assertEquals(NfcShareInitiator.Result.NotSharing, readThroughTheService())
        assertEquals(1, sheet.transfers)
    }

    @Test
    fun `nothing is handed over when sharing is not allowed, and the card stays ready`() {
        NfcCardOutletService.isAllowed = { false }
        val sheet = Sheet()
        NfcCardOutletService.setOutletCard(payload, sheet)

        assertContentEquals(nothing, service.processCommandApdu(select, null))
        assertEquals(NfcShareInitiator.Result.NotSharing, readThroughTheService())
        assertEquals(0, sheet.transfers)
        assertTrue(NfcCardOutletService.isArmed)
    }

    @Test
    fun `only NormPix's own select command starts a share`() {
        NfcCardOutletService.isAllowed = { true }
        NfcCardOutletService.setOutletCard(payload, Sheet())

        assertContentEquals(nothing, service.processCommandApdu(null, null))
        for (other in listOf(byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00), select.copyOf().also { it[5] = 0xA0.toByte() })) {
            val answer = service.processCommandApdu(other, null)
            assertFalse(answer.size >= 2 && answer[answer.size - 2] == 0x90.toByte(), "no share starts from ${other.size} bytes")
        }
        // Nothing was started, so a key sent now is out of order.
        val key = byteArrayOf(0x80.toByte(), 0x10, 0x00, 0x00, 0x41) + NfcPairing.newKey().point + byteArrayOf(0x00)
        assertContentEquals(byteArrayOf(0x69, 0x85.toByte()), service.processCommandApdu(key, null))
        assertTrue(NfcCardOutletService.isArmed)
    }

    @Test
    fun `the reader going away ends the session, and the card stays ready for the next one`() {
        NfcCardOutletService.isAllowed = { true }
        val sheet = Sheet(matchAtOnce = false)
        NfcCardOutletService.setOutletCard(payload, sheet)
        assertContentEquals(NfcShareResponder.VERSION_MARKER + byteArrayOf(0x90.toByte(), 0x00), service.processCommandApdu(select, null))
        val key = byteArrayOf(0x80.toByte(), 0x10, 0x00, 0x00, 0x41) + NfcPairing.newKey().point + byteArrayOf(0x00)
        service.processCommandApdu(key, null)
        val nonce = byteArrayOf(0x80.toByte(), 0x12, 0x00, 0x00, 0x10) + NfcPairing.newNonce() + byteArrayOf(0x00)
        service.processCommandApdu(nonce, null)
        assertEquals(1, sheet.codes.size)

        service.onDeactivated(HostApduService.DEACTIVATION_LINK_LOSS)

        assertEquals(1, sheet.withdrawn)
        assertFalse(NfcCardOutletService.responder.confirm(sheet.codes.single().id), "Match on a code whose reader is gone sends nothing")
        assertContentEquals(byteArrayOf(0x69, 0x85.toByte()), service.processCommandApdu(byteArrayOf(0x80.toByte(), 0x14, 0, 0, 0), null))
        assertTrue(NfcCardOutletService.isArmed)
    }

    @Test
    fun `the app lets a card go only while NormPix is in front`() {
        // As the app wires it at launch: card sharing switched on (it is off until the user turns it on), and its own screen in front.
        app.container.host.visible = true
        assertFalse(allowedByTheApp(), "off until the user turns card sharing on")
        app.container.preferences.setAvailability(ScheduledFeature.NfcExchange, AvailabilityRule(AvailabilityMode.Always))
        assertTrue(allowedByTheApp())
        app.container.host.visible = false
        assertFalse(allowedByTheApp(), "behind another app, or with the screen off")
        app.container.host.visible = true
        assertTrue(allowedByTheApp())
    }

    @Test
    fun `Android routes a reader to the service only on an unlocked phone`() {
        val info = app.packageManager.getServiceInfo(ComponentName(app, NfcCardOutletService::class.java), PackageManager.GET_META_DATA)
        assertEquals("android.permission.BIND_NFC_SERVICE", info.permission)
        val parser = app.resources.getXml(R.xml.apduservice)
        var unlock: Boolean? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "host-apdu-service") {
                unlock = parser.getAttributeBooleanValue("http://schemas.android.com/apk/res/android", "requireDeviceUnlock", false)
            }
            parser.next()
        }
        assertEquals(true, unlock)
    }
}
