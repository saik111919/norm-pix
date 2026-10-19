package com.example.normpix.wallet

import com.example.normpix.AppContainer
import kotlinx.coroutines.CancellationException

/** How deleting every card went (Settings > Wallet & Cards > Store cards turned off); see [eraseCards]. */
sealed interface CardsWipe {
    /**
     * The vault couldn't be erased, so nothing at all was changed: the cards, their expiry reminders, the registered NFC tags
     * and the accounts' links to cards are all as they were.
     */
    data object VaultFailed : CardsWipe

    /** Every card is gone from the vault. [leftOver] names what else couldn't be cleaned up, in words; it is empty when all of it was. */
    data class Erased(val leftOver: List<String>) : CardsWipe {
        val complete: Boolean get() = leftOver.isEmpty()
    }
}

/**
 * Deletes everything NormPix keeps about cards, in an order that never leaves a half-deleted state unannounced:
 *
 *  1. the vault is erased first - every number, expiry and CVV (see [CardsController.deleteAll]). If that fails, nothing
 *     else is touched and the result is [CardsWipe.VaultFailed];
 *  2. then the card-expiry reminders (their alarms, notifications and records) are cancelled, the copied card detail still on
 *     the clipboard is taken off, the NFC tags registered to reveal cards are removed (and the reveal gate goes back to
 *     "any tag", since no tag of the user's is left), and every account's link to a Wallet card is cleared. The accounts
 *     themselves stay, with any card digits their own forms hold.
 *
 * Each step in 2 is tried whatever happened to the others, and what failed comes back in [CardsWipe.Erased.leftOver] so the
 * caller can say so; every step can be run again, so asking again finishes the job. Nothing here logs or returns card data.
 */
suspend fun eraseCards(container: AppContainer): CardsWipe {
    if (!container.cards.deleteAll()) return CardsWipe.VaultFailed
    val leftOver = mutableListOf<String>()

    if (!attempt { container.expiry.clear() }) leftOver += "the card-expiry reminders"
    // A failed clear is not worth reporting: the copy clears itself on its own timer anyway.
    attempt { container.clipboard.clear() }
    val tagsRemoved = attempt {
        container.registeredTags.clear()
        container.preferences.setNfcRevealRegisteredOnly(false)
    }
    if (!tagsRemoved) leftOver += "the NFC tags registered for revealing cards"

    val failedLinks = try {
        val finance = container.finance
        var failed = 0
        for (account in finance.repository.accounts().filter { it.cardId != null }) {
            if (!attempt { finance.saveAccount(account.copy(cardId = null)) }) failed++
        }
        failed
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        -1
    }
    if (failedLinks != 0) leftOver += "the links from accounts to cards"

    return CardsWipe.Erased(leftOver)
}

/** Runs [step]; false if it threw (cancellation excepted). */
private inline fun attempt(step: () -> Unit): Boolean = try {
    step()
    true
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    false
}
