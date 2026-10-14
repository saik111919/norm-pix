package com.example.normpix.wallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.normpix.AppContainer
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.ui.components.AppHaptics
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.wallet.clipboard.CardField
import com.example.normpix.wallet.nfc.NfcTapSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Which cards are showing their number and CVV, and the step that guards showing (or copying) them: with "tap an NFC tag
 * to reveal" on, a tag has to be tapped first. Each place that shows a card holds its own, so it starts with every card
 * hidden - and the Wallet tab and the card sheet on Home go through this one gate.
 */
class CardReveal internal constructor(
    /** The ids of the cards whose number and CVV are on screen. */
    val revealed: MutableList<String>,
    private val container: AppContainer,
    private val overlays: AppOverlays,
    private val snackbars: AppSnackbars,
    private val haptics: AppHaptics,
    private val flows: CardFlows,
    private val scope: CoroutineScope,
) {
    private val prefs get() = container.preferences.state.value

    /**
     * The tap-an-NFC-tag step that guards revealing (and copying) card details, when it is turned on. True once passed:
     * by any tag, or - when the user limited it to their own - by one of the tags they registered.
     */
    private suspend fun passedNfcGate(): Boolean = when (container.nfc.availability()) {
        NfcAvailability.Available -> {
            val registeredOnly = prefs.nfcRevealRegisteredOnly
            if (registeredOnly && container.registeredTags.count.value == 0) {
                cantReveal("No tag registered", "No tag is registered yet - register one in Settings > Security & privacy")
                false
            } else {
                val tagged = overlays.sheet<Boolean> { dismiss ->
                    NfcRevealSheet(registeredOnly = registeredOnly, onCancel = { dismiss(false) }, onTag = { dismiss(true) })
                }
                container.nfc.cancel()
                (tagged == true).also { if (it) haptics.strong() }
            }
        }
        NfcAvailability.Disabled -> {
            cantReveal("NFC is off", "Turn on NFC in system settings to reveal cards")
            false
        }
        NfcAvailability.Unsupported -> {
            cantReveal("No NFC", "NFC isn't available on this device")
            false
        }
    }

    /**
     * Says why a card can't be revealed: a snackbar on a page, or - over a sheet (the card's own, on Home), where a
     * snackbar waits until the sheet has gone - a notice that comes up over it.
     */
    private suspend fun cantReveal(title: String, message: String) {
        if (overlays.isOpen) overlays.alert(title, "$message.") else snackbars.show(message, SnackbarKind.Warning)
    }

    /** Shows or hides [id]'s number and CVV; showing goes through the NFC step when that is on. */
    fun toggle(id: String) {
        val revealing = id !in revealed
        if (revealing && prefs.nfcTagRevealEnabled) {
            scope.launch { if (passedNfcGate()) revealed += id }
            return
        }
        haptics.selection()
        if (!revealed.remove(id)) revealed += id
    }

    /**
     * Copying a detail is as much a reveal as showing it, so it goes through the same gate - without showing the card.
     * [say] gets the confirmation (a snackbar when it is null).
     */
    fun copy(card: StoredCard, field: CardField?, say: ((String) -> Unit)? = null) {
        scope.launch {
            if (prefs.nfcTagRevealEnabled && card.id !in revealed && !passedNfcGate()) return@launch
            if (field == null) flows.copyDetails(card) else flows.copyFieldAndSay(card, field, say)
        }
    }
}

@Composable
fun rememberCardReveal(flows: CardFlows = rememberCardFlows()): CardReveal {
    val container = LocalAppContainer.current
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val revealed = remember { mutableStateListOf<String>() }
    return remember(container, overlays, snackbars, haptics, flows, scope) {
        CardReveal(revealed, container, overlays, snackbars, haptics, flows, scope)
    }
}

/**
 * Shown while NFC waits for a tap. By default, any tag (or NFC-enabled device) will do and its contents are never
 * read - discovery itself is the reveal gesture; with [registeredOnly] only a tag the user registered is accepted, and
 * a different one is told so while the sheet keeps waiting.
 */
@Composable
private fun NfcRevealSheet(registeredOnly: Boolean, onCancel: () -> Unit, onTag: () -> Unit) {
    val container = LocalAppContainer.current
    val haptics = rememberAppHaptics()
    var refused by remember { mutableStateOf(false) }
    DisposableEffect(registeredOnly) {
        container.nfc.waitForTag { id ->
            val accepted = !registeredOnly || container.registeredTags.matches(id)
            if (accepted) onTag() else refused = true
            accepted
        }
        onDispose { container.nfc.cancel() }
    }
    LaunchedEffect(refused) { if (refused) haptics.strong() }
    NfcTapSheet(
        title = if (registeredOnly) "Hold your registered tag near your phone" else "Hold an NFC tag near your phone",
        message = if (registeredOnly) {
            "Only a tag you registered in NormPix will reveal this card."
        } else {
            "Tap any NFC tag or NFC-enabled device to reveal this card."
        },
        notice = if (refused) "That isn't one of your registered tags. Try your own." else null,
        onCancel = onCancel,
    )
}
