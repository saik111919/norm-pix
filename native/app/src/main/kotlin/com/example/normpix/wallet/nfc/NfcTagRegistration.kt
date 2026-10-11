package com.example.normpix.wallet.nfc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.normpix.AppContainer
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.wallet.TagRegistration

/**
 * Asks the user to tap the tag they want to use for revealing cards, and registers it. A tag that can't be recognised
 * is explained and the sheet keeps waiting, so another can be tried. Null when it was cancelled.
 */
suspend fun registerNfcTag(container: AppContainer, overlays: AppOverlays): TagRegistration? {
    val result = overlays.sheet<TagRegistration> { dismiss ->
        RegisterTagSheet(container, onResult = { dismiss(it) }, onCancel = { dismiss(null) })
    }
    container.nfc.cancel()
    return result
}

@Composable
private fun RegisterTagSheet(container: AppContainer, onResult: (TagRegistration) -> Unit, onCancel: () -> Unit) {
    var notice by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        container.nfc.waitForTag { id ->
            val outcome = container.registeredTags.register(id)
            if (outcome == TagRegistration.ChangesEveryTap) {
                notice = "That tag shows a different ID every time it's tapped (like a phone or a payment card), " +
                    "so NormPix can't recognise it. Use a sticker or key-fob tag instead."
                false
            } else {
                onResult(outcome)
                true
            }
        }
        onDispose { container.nfc.cancel() }
    }
    NfcTapSheet(
        title = "Hold the tag you want to use",
        message = "NormPix remembers which tag it is, not what's on it, and asks for it before revealing a card.",
        notice = notice,
        onCancel = onCancel,
    )
}
