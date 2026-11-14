package com.example.normpix.wallet

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.NormPixApplication
import com.example.normpix.R
import com.example.normpix.availability.AvailabilityMode
import com.example.normpix.availability.AvailabilityRule
import com.example.normpix.availability.ScheduledFeature
import com.example.normpix.wallet.nfc.NfcCardOutletService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * What the card-sharing service hands a reader is a card's number, expiry and CVV. So it answers one reader, once, and only while
 * the user is sharing: with NormPix in front, inside their schedule, on a phone that is unlocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NfcCardOutletServiceTest {
    private val app: NormPixApplication = ApplicationProvider.getApplicationContext()
    private val service = NfcCardOutletService()
    private val select = NfcCardOutletService.SELECT_AID_COMMAND
    private val nothing = byteArrayOf(0x6F, 0x00)
    private val allowedByTheApp = NfcCardOutletService.isAllowed

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
        var transfers = 0
        NfcCardOutletService.setOutletCard("card") { transfers++ }

        assertContentEquals("card".toByteArray() + byteArrayOf(0x90.toByte(), 0x00), service.processCommandApdu(select, null))
        assertEquals(1, transfers)
        // The sheet still says "Card Transferred!": a second reader held to the phone meanwhile gets nothing.
        assertNull(NfcCardOutletService.activePayload)
        assertContentEquals(nothing, service.processCommandApdu(select, null))
        assertEquals(1, transfers)
    }

    @Test
    fun `nothing is handed over when sharing is not allowed, and the card stays ready`() {
        NfcCardOutletService.isAllowed = { false }
        var transfers = 0
        NfcCardOutletService.setOutletCard("card") { transfers++ }

        assertContentEquals(nothing, service.processCommandApdu(select, null))
        assertEquals(0, transfers)
        assertEquals("card", NfcCardOutletService.activePayload)
    }

    @Test
    fun `only NormPix's own select command is answered`() {
        NfcCardOutletService.isAllowed = { true }
        NfcCardOutletService.setOutletCard("card")

        assertContentEquals(nothing, service.processCommandApdu(null, null))
        assertContentEquals(nothing, service.processCommandApdu(byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00), null))
        assertContentEquals(nothing, service.processCommandApdu(select.copyOf().also { it[5] = 0xA0.toByte() }, null))
        assertEquals("card", NfcCardOutletService.activePayload)
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
