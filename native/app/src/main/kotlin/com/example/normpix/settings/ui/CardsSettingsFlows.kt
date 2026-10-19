package com.example.normpix.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import com.example.normpix.AppContainer
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.wallet.CardsState
import com.example.normpix.wallet.CardsWipe
import com.example.normpix.wallet.eraseCards
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The two switches that decide what the card vault may hold (Settings > Wallet & Cards): "Save CVV" and "Store cards". Turning
 * either one off deletes what it covers, so each asks first - and the switch only moves once the deletion has really
 * happened: say no, or let the deletion fail, and it stays on. Nothing here logs or shows card data.
 */
internal class CardsSettingsFlows(
    private val container: AppContainer,
    private val overlays: AppOverlays,
    private val snackbars: AppSnackbars,
) {
    private val preferences get() = container.preferences

    /** While Wallet lock is on, deleting cards takes the same unlock exporting them does. True when no lock stands in the way. */
    private suspend fun unlocked(reason: String): Boolean =
        !preferences.state.value.walletLockEnabled || container.lock.authenticate(reason)

    /** The stored cards, or null when the vault can't be read right now. */
    private suspend fun storedCards() = container.cards.let { controller ->
        if (controller.state.value !is CardsState.Ready) controller.load()
        (controller.state.value as? CardsState.Ready)?.cards
    }

    // ------------------------------------------------------------------ Save CVV

    /**
     * "Save CVV". On is just on. Off asks whether to delete the CVVs already saved (the switch stays on if the answer is no),
     * deletes them from every stored card, and only then turns it off. With no CVV saved there is nothing to ask about.
     */
    suspend fun setCvvSaved(on: Boolean) {
        if (on) {
            preferences.setCardCvvEnabled(true)
            return
        }
        val cards = storedCards()
        if (cards == null) {
            // Not knowing whether CVVs are saved, they can't be promised gone.
            snackbars.show("Your cards can't be read right now, so saved CVVs can't be deleted. Save CVV stays on.", SnackbarKind.Error)
            return
        }
        val withCvv = cards.count { it.hasCvv }
        if (withCvv > 0) {
            val sure = overlays.confirm(
                title = "Delete saved CVVs?",
                message = "Turning off Save CVV deletes the CVV saved with ${cardsPhrase(withCvv)}. Cards you add or import from now on " +
                    "are kept without one, and the CVV field leaves the card form. Everything else on your cards stays.",
                confirmLabel = "Delete CVVs",
                cancelLabel = "Keep saving",
                destructive = true,
                icon = Icons.Outlined.DeleteOutline,
            )
            if (!sure) return
            if (!unlocked("Unlock to delete your saved CVVs")) {
                snackbars.show("Couldn't unlock, so the CVVs were kept and Save CVV stays on.", SnackbarKind.Error)
                return
            }
            // Started, it finishes: leaving the page mid-way must not leave some cards with a CVV and some without.
            val removed = withContext(NonCancellable) { container.cards.removeCvvs() }
            if (!removed) {
                overlays.alert(
                    title = "Couldn't delete the CVVs",
                    message = "The card vault couldn't be saved, so nothing was changed. Your cards keep their CVVs and Save CVV stays on. Try again.",
                    icon = Icons.Outlined.ErrorOutline,
                )
                return
            }
        }
        preferences.setCardCvvEnabled(false)
        snackbars.show(
            if (withCvv > 0) "Saved CVVs deleted. New cards are kept without one." else "New cards are kept without a CVV.",
            if (withCvv > 0) SnackbarKind.Deleted else SnackbarKind.Done,
        )
    }

    // --------------------------------------------------------------- Store cards

    /**
     * "Store cards". On is just on: the Wallet shows an empty cards section again. Off explains what is deleted and asks, then
     * deletes it all ([eraseCards]) and only then turns it off. If the vault can't be erased nothing at all is changed; if the
     * cards are gone but something around them couldn't be cleaned up, it says exactly what, and the switch stays on so
     * turning it off again finishes the job.
     */
    suspend fun setCardsStored(on: Boolean) {
        if (on) {
            preferences.setWalletCardsEnabled(true)
            // Whatever the vault holds now is what the Wallet shows.
            container.cards.load()
            snackbars.show("Cards are stored again. Add one from the Wallet.", SnackbarKind.Done)
            return
        }
        val cards = storedCards()
        val sure = overlays.confirm(
            title = "Stop storing cards?",
            message = "This deletes, from this device, everything NormPix keeps about cards: " +
                (cards?.takeIf { it.isNotEmpty() }?.let { "${cardsPhrase(it.size)} with the numbers, CVVs and expiry dates, " } ?: "every saved card with its number, CVV and expiry date, ") +
                "the NFC tags you registered for revealing cards, the card-expiry reminders, and the links from your accounts to cards. " +
                "Your accounts and their transactions stay, and the Wallet shows accounts only. This can't be undone.",
            confirmLabel = "Delete cards",
            cancelLabel = "Keep cards",
            destructive = true,
            icon = Icons.Outlined.DeleteOutline,
        )
        if (!sure) return
        if (!unlocked("Unlock to delete your cards")) {
            snackbars.show("Couldn't unlock, so your cards were kept and Store cards stays on.", SnackbarKind.Error)
            return
        }
        // Started, it finishes: leaving the page mid-way must not leave half the cleanup undone.
        when (val result = withContext(NonCancellable) { eraseCards(container) }) {
            CardsWipe.VaultFailed -> overlays.alert(
                title = "Couldn't delete your cards",
                message = "The card vault couldn't be erased, so nothing was deleted. Your cards, reminders and links are as they were, " +
                    "and Store cards stays on. Try again.",
                icon = Icons.Outlined.ErrorOutline,
            )
            is CardsWipe.Erased -> if (result.complete) {
                preferences.setWalletCardsEnabled(false)
                snackbars.show("Cards deleted. The Wallet now shows accounts only.", SnackbarKind.Deleted)
            } else {
                overlays.alert(
                    title = "Cards deleted, but not everything",
                    message = "Every saved card is gone, but ${result.leftOver.joinToString(", ")} couldn't be removed. " +
                        "Store cards stays on: turn it off again to finish.",
                    icon = Icons.Outlined.ErrorOutline,
                )
            }
        }
    }

    private fun cardsPhrase(n: Int) = if (n == 1) "1 card" else "$n cards"
}
