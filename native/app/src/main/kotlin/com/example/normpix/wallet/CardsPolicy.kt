package com.example.normpix.wallet

import com.example.normpix.settings.AppPrefsState

/**
 * What the user's card settings (Settings > Wallet & Cards) let the vault keep: [storeCards] is "Store cards" and [keepCvv] is
 * "Save CVV". Every way a card gets into the vault - the add form, an NFC tap, a cards file, an app backup - ends in
 * [CardsController], which asks for this at each write, so no path can keep what the user said not to.
 */
data class CardsPolicy(val storeCards: Boolean = true, val keepCvv: Boolean = true) {
    /** [card] as it may be kept: without its CVV when CVVs are not kept. */
    fun shape(card: StoredCard): StoredCard = if (keepCvv || !card.hasCvv) card else card.copy(cvv = "")

    companion object {
        /** Everything may be kept: the settings' defaults, and what a controller does until it is told to follow the settings. */
        val Unrestricted = CardsPolicy()
    }
}

/** The card settings as the vault's rules. */
val AppPrefsState.cardsPolicy: CardsPolicy get() = CardsPolicy(storeCards = walletCardsEnabled, keepCvv = cardCvvEnabled)
