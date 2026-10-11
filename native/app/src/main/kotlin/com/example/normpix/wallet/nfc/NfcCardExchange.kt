package com.example.normpix.wallet.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import com.example.normpix.wallet.ActivityHost
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.StoredCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.util.UUID

/** Lifecycle state for the NFC Inlet (card receiver) mode. */
sealed class NfcInletState {
    object Idle : NfcInletState()

    /** Reader on, waiting for a tap. */
    object Active : NfcInletState()

    /** Something touched the phone and is being read. */
    object Detected : NfcInletState()
    object Receiving : NfcInletState()

    /** A card was received. [alreadySaved]: the wallet already holds it, so nothing was added. */
    data class Success(val card: StoredCard, val alreadySaved: Boolean = false) : NfcInletState()

    /** A contactless bank card (or phone wallet) was identified; its details stay locked in the chip. */
    data class PaymentCard(val info: PaymentCardInfo) : NfcInletState()

    /** Something was tapped that NormPix can't take a card from. The reader keeps listening. */
    data class Unsupported(val tag: DetectedTag) : NfcInletState()

    /** It moved away before it could be read. The reader keeps listening. */
    object Lost : NfcInletState()

    /** NFC is switched off in system settings. */
    object NfcOff : NfcInletState()

    /** This phone has no NFC. */
    object NoNfc : NfcInletState()

    data class Error(val message: String) : NfcInletState()
}

/** Lifecycle state for the NFC Outlet (card sender) mode. */
sealed class NfcOutletState {
    data class Active(val card: StoredCard) : NfcOutletState()
    data class Transferred(val card: StoredCard) : NfcOutletState()
}

/**
 * Serializes a [StoredCard] into an NFC exchange payload.
 */
fun StoredCard.toNfcExchangePayload(): String = buildJsonObject {
    put("type", "normpix_card")
    put("version", 1)
    put("bankName", bankName)
    put("cardholderName", cardholderName)
    put("cardNumber", cardNumber)
    put("expiryMonth", expiryMonth)
    put("expiryYear", expiryYear)
    put("cvv", cvv)
    put("kind", kind.key)
}.toString()

/**
 * Parses a raw NFC payload into a newly created [StoredCard], or null when it is not a NormPix card
 * or is malformed (so a garbled or foreign tag is reported, never stored).
 *
 * The JSON is taken from the first `{` to the last `}`: an NDEF Text record puts a status byte and a
 * language code in front of it, which must not stop a tag written that way from being read.
 */
fun parseCardFromNfcPayload(raw: String): StoredCard? = runCatching {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val json = Json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
    val type = json["type"]?.jsonPrimitive?.content
    if (type != "normpix_card") return null

    val bankName = json["bankName"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.take(MAX_TEXT) ?: "Card"
    val cardholderName = json["cardholderName"]?.jsonPrimitive?.content?.take(MAX_TEXT) ?: ""
    val cardNumber = json["cardNumber"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
    // A card number is digits (spaces and dashes are only grouping): anything else is not a card.
    val digits = cardNumber.filter { it.isDigit() }
    if (cardNumber.any { !it.isDigit() && it != ' ' && it != '-' } || digits.length !in MIN_DIGITS..MAX_DIGITS) return null
    val expiryMonth = json["expiryMonth"]?.jsonPrimitive?.content ?: "12"
    val expiryYear = json["expiryYear"]?.jsonPrimitive?.content ?: "30"
    val cvv = json["cvv"]?.jsonPrimitive?.content ?: ""
    val kindKey = json["kind"]?.jsonPrimitive?.content ?: "credit"
    val kind = CardKind.entries.firstOrNull { it.key == kindKey } ?: CardKind.Credit

    StoredCard(
        id = UUID.randomUUID().toString(),
        bankName = bankName,
        cardholderName = cardholderName,
        cardNumber = cardNumber,
        expiryMonth = expiryMonth,
        expiryYear = expiryYear,
        cvv = cvv,
        kind = kind,
        createdAt = LocalDateTime.now(),
    )
}.getOrNull()

private const val MAX_TEXT = 64
private const val MIN_DIGITS = 12
private const val MAX_DIGITS = 19

/** Same physical card: same number (ignoring grouping) and expiry. Used so one tap never stores a card twice. */
fun StoredCard.sameCardAs(other: StoredCard): Boolean =
    cardNumber.filter { it.isDigit() } == other.cardNumber.filter { it.isDigit() } &&
        expiryMonth.trim().trimStart('0') == other.expiryMonth.trim().trimStart('0') &&
        expiryYear.trim().takeLast(2) == other.expiryYear.trim().takeLast(2)

/**
 * Controller for two-way NFC card exchange (Inlet and Outlet).
 *
 * The Inlet runs Android reader mode on the current activity. The platform itself pauses reader
 * mode when the activity leaves the foreground and restores it on return, so only leaving the
 * screen ([stopInlet]) has to turn it off. Every start opens a new session: a tag read that
 * finishes after its session ended (screen left, scanning restarted) is dropped, so one tap can
 * never be reported twice.
 */
class NfcExchangeController(private val host: ActivityHost) {
    private val _inletState = MutableStateFlow<NfcInletState>(NfcInletState.Idle)
    val inletState: StateFlow<NfcInletState> = _inletState.asStateFlow()

    private val _outletState = MutableStateFlow<NfcOutletState?>(null)
    val outletState: StateFlow<NfcOutletState?> = _outletState.asStateFlow()

    private var activeActivity: Activity? = null

    @Volatile
    private var session = 0

    /** Starts NFC Outlet mode to broadcast [card] via HCE. */
    fun startOutlet(card: StoredCard) {
        stopInlet()
        _outletState.value = NfcOutletState.Active(card)
        NfcCardOutletService.setOutletCard(card.toNfcExchangePayload()) {
            _outletState.value = NfcOutletState.Transferred(card)
        }
    }

    /** Stops NFC Outlet mode. */
    fun stopOutlet() {
        _outletState.value = null
        NfcCardOutletService.clearOutlet()
    }

    /** Where to hold a card on this phone; [PhoneNfcArea.Unknown] unless the phone reports it. */
    fun phoneArea(): PhoneNfcArea {
        val activity = host.activity ?: return PhoneNfcArea.Unknown
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return PhoneNfcArea.Unknown
        return phoneNfcArea(adapter)
    }

    /**
     * Starts NFC Inlet mode: listens for a phone sharing a card, a NormPix tag, or a contactless
     * bank card. Calling it again starts a clean session.
     *
     * One tap receives one card: after a successful read the reader is released (so the phones staying
     * in contact, or a second tap, cannot store it again) until [startInlet] is called again. A card for
     * which [isAlreadySaved] is true is reported as such and [onCardReceived] is not called for it;
     * otherwise [onCardReceived] is invoked once with the new card. A bank card also ends the session
     * ([NfcInletState.PaymentCard]); anything unreadable leaves the reader listening for another tap.
     */
    fun startInlet(isAlreadySaved: (StoredCard) -> Boolean = { false }, onCardReceived: (StoredCard) -> Unit) {
        stopOutlet()
        releaseReader()
        val activity = host.activity ?: run {
            _inletState.value = NfcInletState.Error("NFC reader is unavailable")
            return
        }
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: run {
            _inletState.value = NfcInletState.NoNfc
            return
        }
        if (!adapter.isEnabled) {
            _inletState.value = NfcInletState.NfcOff
            return
        }

        val current = session
        activeActivity = activity
        _inletState.value = NfcInletState.Active

        adapter.enableReaderMode(
            activity,
            { tag: Tag ->
                activity.runOnUiThread {
                    if (session == current) _inletState.value = NfcInletState.Detected
                }
                val channel = AndroidTagChannel(tag)
                val read = readTag(channel)
                logTagRead(channel.techs, read)
                activity.runOnUiThread {
                    if (session == current) onTagRead(read, isAlreadySaved, onCardReceived)
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

    /** Applies one finished read to the Inlet. Main thread. */
    internal fun onTagRead(read: NfcTagRead, isAlreadySaved: (StoredCard) -> Boolean, onCardReceived: (StoredCard) -> Unit) {
        when (read) {
            is NfcTagRead.NormPixPayload -> {
                _inletState.value = NfcInletState.Receiving
                val card = parseCardFromNfcPayload(read.payload)
                if (card == null) {
                    _inletState.value = NfcInletState.Error("The card data that arrived was incomplete or damaged. Tap again.")
                    return
                }
                val known = isAlreadySaved(card)
                releaseReader()
                _inletState.value = NfcInletState.Success(card, alreadySaved = known)
                if (!known) onCardReceived(card)
            }
            is NfcTagRead.PaymentCard -> {
                releaseReader()
                _inletState.value = NfcInletState.PaymentCard(read.info)
            }
            is NfcTagRead.Unsupported -> _inletState.value = NfcInletState.Unsupported(read.tag)
            NfcTagRead.Lost -> _inletState.value = NfcInletState.Lost
        }
    }

    /** Stops listening in NFC Inlet mode. */
    fun stopInlet() {
        releaseReader()
        _inletState.value = NfcInletState.Idle
    }

    /** Turns the NFC reader off without touching [inletState], so a result stays on screen. Ends the session. */
    private fun releaseReader() {
        session++
        val activity = activeActivity
        activeActivity = null
        if (activity != null) {
            runCatching {
                NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity)
            }
        }
    }
}
