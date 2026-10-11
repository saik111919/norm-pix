package com.example.normpix.wallet

import com.example.normpix.finance.ui.FinanceController
import kotlinx.coroutines.CancellationException

/**
 * Gives every card a bank from the shared catalog (the one accounts use too).
 *
 * Cards saved before banks were shared, cards received over NFC, and cards whose bank is no longer in the catalog (after
 * the finance data was reset) carry only the bank name they were typed with. Their name is matched to a known bank the
 * way the logo is ("hdfc", "HDFC Bank Ltd"); a name that matches none becomes a bank of that name, added once however many
 * cards share it. Only the link is added: the card keeps its name, number and everything else exactly as stored, and a
 * card with no bank name at all is left alone.
 */
class CardBankLinker(private val cards: CardsController, private val finance: FinanceController) {
    /** Cards that could not be linked this session, so a failing write is not retried in a loop. */
    private val failed = mutableSetOf<String>()

    /** Links whatever needs it. Returns how many cards were linked. */
    suspend fun link(): Int {
        var linked = 0
        val catalog = finance.state.value.catalog
        val pending = cards.cards.filter { it.bankName.isNotBlank() && catalog.byKey(it.bankKey) == null && it.id !in failed }
        for (card in pending) {
            val key = try {
                catalog.resolve(card.bankName)?.key ?: finance.bankKeyFor(card.bankName)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed += card.id
                continue
            }
            // The card may have been edited or deleted while the bank was being found: link only the card that was matched.
            val current = cards.cards.firstOrNull { it.id == card.id } ?: continue
            if (current.bankName != card.bankName || finance.state.value.catalog.byKey(current.bankKey) != null) continue
            if (cards.update(current.copy(bankKey = key))) linked++ else failed += card.id
        }
        return linked
    }
}
