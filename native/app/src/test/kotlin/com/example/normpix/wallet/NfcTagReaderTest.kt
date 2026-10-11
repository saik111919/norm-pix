package com.example.normpix.wallet

import com.example.normpix.wallet.nfc.DetectedTag
import com.example.normpix.wallet.nfc.NfcCardOutletService
import com.example.normpix.wallet.nfc.NfcExchangeController
import com.example.normpix.wallet.nfc.NfcInletState
import com.example.normpix.wallet.nfc.NfcTagRead
import com.example.normpix.wallet.nfc.PaymentCardInfo
import com.example.normpix.wallet.nfc.PhoneNfcArea
import com.example.normpix.wallet.nfc.SELECT_PPSE
import com.example.normpix.wallet.nfc.TagChannel
import com.example.normpix.wallet.nfc.nfcAreaFrom
import com.example.normpix.wallet.nfc.parsePaymentDirectory
import com.example.normpix.wallet.nfc.readTag
import com.example.normpix.wallet.nfc.toNfcExchangePayload
import java.io.IOException
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the Inlet makes of each kind of tapped tag. Bank cards are the case that used to fail: they
 * never answer NormPix's own AID and carry no NDEF, so they must be recognised from their public
 * payment directory - and nothing beyond it may ever be asked for.
 */
class NfcTagReaderTest {
    private val card = StoredCard(
        id = "sender-id",
        bankName = "HDFC Bank",
        cardholderName = "Asha Rao",
        cardNumber = "4111111111111111",
        expiryMonth = "09",
        expiryYear = "29",
        cvv = "123",
        kind = CardKind.Debit,
        createdAt = LocalDateTime.of(2026, 1, 1, 9, 0),
    )

    /** A tag that answers APDUs with [apdu] (ISO-DEP) and/or holds [ndef] records. Records what was sent. */
    private class FakeTag(
        override val techs: Set<String>,
        private val ndef: List<ByteArray>? = null,
        private val apdu: ((ByteArray) -> ByteArray)? = null,
    ) : TagChannel {
        val sent = mutableListOf<ByteArray>()

        override fun <T> isoDep(block: ((ByteArray) -> ByteArray) -> T): T? {
            val answer = apdu ?: return null
            return block { command ->
                sent += command
                answer(command)
            }
        }

        override fun ndefPayloads(): List<ByteArray>? = ndef
    }

    private val ok = byteArrayOf(0x90.toByte(), 0x00)
    private val fileNotFound = byteArrayOf(0x6A, 0x82.toByte())

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun tlv(tag: Int, value: ByteArray): ByteArray {
        val tagBytes = if (tag > 0xFF) byteArrayOf((tag shr 8).toByte(), tag.toByte()) else byteArrayOf(tag.toByte())
        val length = if (value.size < 0x80) byteArrayOf(value.size.toByte()) else byteArrayOf(0x81.toByte(), value.size.toByte())
        return tagBytes + length + value
    }

    private fun app(aid: String, label: String? = null, priority: Int? = null): ByteArray =
        tlv(
            0x61,
            tlv(0x4F, hex(aid)) +
                (label?.let { tlv(0x50, it.toByteArray(Charsets.ISO_8859_1)) } ?: ByteArray(0)) +
                (priority?.let { tlv(0x87, byteArrayOf(it.toByte())) } ?: ByteArray(0)),
        )

    private fun directory(vararg apps: ByteArray): ByteArray =
        tlv(0x6F, tlv(0x84, "2PAY.SYS.DDF01".toByteArray()) + tlv(0xA5, tlv(0xBF0C, apps.fold(ByteArray(0)) { a, b -> a + b })))

    /** An EMV card: doesn't know NormPix's AID, answers the payment directory, refuses everything else. */
    private fun bankCard(fci: ByteArray) = FakeTag(setOf("IsoDep", "NfcA")) { command ->
        when {
            command.contentEquals(SELECT_PPSE) -> fci + ok
            else -> fileNotFound
        }
    }

    @Test
    fun aSharingPhone_givesTheWholeCard() {
        val payload = card.toNfcExchangePayload()
        val phone = FakeTag(setOf("IsoDep", "NfcA")) { command ->
            if (command.contentEquals(NfcCardOutletService.SELECT_AID_COMMAND)) payload.toByteArray() + ok else fileNotFound
        }
        assertEquals(NfcTagRead.NormPixPayload(payload), readTag(phone))
        assertEquals(1, phone.sent.size)
    }

    @Test
    fun aPhoneWithNormPixThatIsNotSharing_isNamedAsSuch() {
        val phone = FakeTag(setOf("IsoDep", "NfcA")) { byteArrayOf(0x6F, 0x00) }
        assertEquals(NfcTagRead.Unsupported(DetectedTag.PhoneNotSharing), readTag(phone))
    }

    @Test
    fun aBankCard_isIdentifiedFromItsPublicDirectory_andNothingElseIsAskedFor() {
        val tag = bankCard(directory(app("A0000000031010", "VISA DEBIT", 1)))

        val read = assertIs<NfcTagRead.PaymentCard>(readTag(tag))

        assertEquals(PaymentCardInfo(networkName = "Visa", network = CardNetwork.Visa, kind = CardKind.Debit, label = "VISA DEBIT"), read.info)
        assertEquals("Visa debit card", read.info.title)
        // Exactly two commands, both SELECT (INS A4): NormPix's AID, then the directory. Never
        // GET PROCESSING OPTIONS (80 A8) or READ RECORD (00 B2), which is where card data lives.
        assertEquals(2, tag.sent.size)
        assertTrue(tag.sent.all { it[1] == 0xA4.toByte() })
        assertContentEquals(NfcCardOutletService.SELECT_AID_COMMAND, tag.sent[0])
        assertContentEquals(SELECT_PPSE, tag.sent[1])
    }

    @Test
    fun theCardsOwnPreferredApplication_decides() {
        val tag = bankCard(directory(app("A0000000041010", "MASTERCARD", 2), app("A0000000043060", "MAESTRO", 1)))
        val info = assertIs<NfcTagRead.PaymentCard>(readTag(tag)).info
        assertEquals("Mastercard", info.networkName)
        // Maestro only exists on debit cards.
        assertEquals(CardKind.Debit, info.kind)
        assertEquals("MAESTRO", info.label)
    }

    @Test
    fun aRupayCardWithoutALabel_stillNamesItsNetwork_andGuessesNoKind() {
        val info = assertIs<NfcTagRead.PaymentCard>(readTag(bankCard(directory(app("A0000005241010"))))).info
        assertEquals(CardNetwork.Rupay, info.network)
        assertNull(info.kind)
        assertEquals("RuPay card", info.title)
    }

    @Test
    fun aCreditLabel_marksACreditCard() {
        val info = parsePaymentDirectory(directory(app("A0000000041010", "Mastercard Credit")))!!
        assertEquals(CardKind.Credit, info.kind)
    }

    @Test
    fun aDirectoryListingNoApplications_isStillAPaymentCard() {
        val info = assertIs<NfcTagRead.PaymentCard>(readTag(bankCard(directory()))).info
        assertEquals("Contactless card", info.title)
    }

    @Test
    fun labelsFromTheCard_areCleanedAndBounded() {
        val info = parsePaymentDirectory(directory(app("A0000000031010", "VISA\u0007\u0000 CLASSIC PLATINUM EXTRA")))!!
        // Control characters dropped, then cut to the 16 characters a label may have.
        assertEquals("VISA CLASSIC PLA", info.label)
    }

    @Test
    fun aSmartCardThatIsNoPaymentCard_isDetectedButUnsupported() {
        val idCard = FakeTag(setOf("IsoDep", "NfcB")) { fileNotFound }
        assertEquals(NfcTagRead.Unsupported(DetectedTag.OtherSmartCard), readTag(idCard))

        val garbled = bankCard(byteArrayOf(0x6F, 0x7F, 0x01))
        assertEquals(NfcTagRead.Unsupported(DetectedTag.OtherSmartCard), readTag(garbled))
    }

    @Test
    fun aCardPulledAwayMidRead_isReportedAsLost() {
        val moving = FakeTag(setOf("IsoDep", "NfcA")) { throw IOException("Tag was lost.") }
        assertEquals(NfcTagRead.Lost, readTag(moving))
    }

    @Test
    fun anNdefTagWithANormPixRecord_isRead_evenWithATextRecordPrefix() {
        val payload = card.toNfcExchangePayload()
        val tag = FakeTag(setOf("NfcA", "MifareUltralight", "Ndef"), ndef = listOf(("\u0002en" + payload).toByteArray()))
        val read = assertIs<NfcTagRead.NormPixPayload>(readTag(tag))
        assertTrue(read.payload.endsWith(payload))
    }

    @Test
    fun otherTags_areDetectedButUnsupported() {
        val sticker = FakeTag(setOf("NfcA", "Ndef"), ndef = listOf("https://example.com".toByteArray()))
        assertEquals(NfcTagRead.Unsupported(DetectedTag.TagWithoutCard), readTag(sticker))

        val transitPass = FakeTag(setOf("NfcA", "MifareClassic"))
        assertEquals(NfcTagRead.Unsupported(DetectedTag.OtherTag), readTag(transitPass))

        val label = FakeTag(setOf("NfcV"))
        assertEquals(NfcTagRead.Unsupported(DetectedTag.OtherTag), readTag(label))
    }

    @Test
    fun theDirectoryParser_neverCrashesOnJunk() {
        val random = Random(7)
        repeat(5_000) {
            val bytes = random.nextBytes(random.nextInt(0, 64))
            parsePaymentDirectory(bytes)
            parsePaymentDirectory(byteArrayOf(0x6F) + bytes)
        }
    }

    // ------------------------------------------------------------------ antenna position

    @Test
    fun anAntennaTheMakerDidNotReport_staysUnknown() {
        assertEquals(PhoneNfcArea.Unknown, nfcAreaFrom(0, 0, foldable = false, antennas = emptyList()))
        assertEquals(PhoneNfcArea.Unknown, nfcAreaFrom(70, 150, foldable = false, antennas = emptyList()))
        assertEquals(PhoneNfcArea.Unknown, nfcAreaFrom(70, 150, foldable = false, antennas = listOf(200 to 20)))
    }

    @Test
    fun aFoldablesUnfoldedMeasurements_areNotShownOnTheFoldedPhone() {
        assertEquals(PhoneNfcArea.Unknown, nfcAreaFrom(150, 150, foldable = true, antennas = listOf(40 to 30)))
    }

    @Test
    fun aReportedAntenna_isMirroredForTheBackOfThePhone() {
        // 14mm from the left edge, seen from the screen, is 56mm from the left seen from the back.
        val area = assertIs<PhoneNfcArea.Reported>(nfcAreaFrom(70, 150, foldable = false, antennas = listOf(14 to 30)))
        assertEquals(listOf(PhoneNfcArea.Spot(x = 0.8f, y = 0.2f)), area.spots)
    }

    // ------------------------------------------------------------------ the Inlet's response

    private fun controller() = NfcExchangeController(ActivityHost())

    @Test
    fun aReceivedCard_isHandedOverOnce() {
        val inlet = controller()
        val received = mutableListOf<StoredCard>()
        inlet.onTagRead(NfcTagRead.NormPixPayload(card.toNfcExchangePayload()), isAlreadySaved = { false }) { received += it }
        assertEquals(1, received.size)
        assertEquals(card.cardNumber, received.single().cardNumber)
        assertIs<NfcInletState.Success>(inlet.inletState.value)
    }

    @Test
    fun aCardAlreadyInTheWallet_isNotHandedOverAgain() {
        val inlet = controller()
        val received = mutableListOf<StoredCard>()
        inlet.onTagRead(NfcTagRead.NormPixPayload(card.toNfcExchangePayload()), isAlreadySaved = { true }) { received += it }
        assertTrue(received.isEmpty())
        assertEquals(true, assertIs<NfcInletState.Success>(inlet.inletState.value).alreadySaved)
    }

    @Test
    fun eachKindOfTap_landsInItsOwnState() {
        val inlet = controller()
        val info = PaymentCardInfo("Visa", CardNetwork.Visa, CardKind.Debit, "VISA DEBIT")
        inlet.onTagRead(NfcTagRead.PaymentCard(info), { false }) { error("no card expected") }
        assertEquals(NfcInletState.PaymentCard(info), inlet.inletState.value)

        inlet.onTagRead(NfcTagRead.Unsupported(DetectedTag.OtherTag), { false }) { error("no card expected") }
        assertEquals(NfcInletState.Unsupported(DetectedTag.OtherTag), inlet.inletState.value)

        inlet.onTagRead(NfcTagRead.Lost, { false }) { error("no card expected") }
        assertEquals(NfcInletState.Lost, inlet.inletState.value)

        inlet.onTagRead(NfcTagRead.NormPixPayload("{\"type\":\"normpix_card\"}"), { false }) { error("no card expected") }
        assertIs<NfcInletState.Error>(inlet.inletState.value)
    }
}
