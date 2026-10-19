package com.example.normpix.wallet

import androidx.compose.runtime.Immutable
import com.example.normpix.settings.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The card list as the UI sees it: still loading, ready, or the vault could not be read. */
@Immutable
sealed interface CardsState {
    data object Loading : CardsState

    data class Ready(val cards: List<StoredCard>) : CardsState

    data class Failed(val error: Throwable) : CardsState
}

/**
 * The one shared card list (Wallet and Reminders both use it), backed by the
 * encrypted [CardVault]. Every change is written to the vault; if writing
 * fails, the list is restored to what is actually stored.
 *
 * What may be kept is the user's to say (Settings > Wallet & Cards, see [CardsPolicy]): with "Store cards" off nothing is
 * added, and with "Save CVV" off a card is kept without its CVV, whichever way it arrived.
 */
class CardsController(
    private val vault: CardVault,
    /** What the settings let the vault keep; asked afresh at every write, because the user can change it at any time. */
    @Volatile private var policy: () -> CardsPolicy = { CardsPolicy.Unrestricted },
) {
    private val _state = MutableStateFlow<CardsState>(CardsState.Loading)
    val state: StateFlow<CardsState> = _state.asStateFlow()

    val cards: List<StoredCard> get() = (_state.value as? CardsState.Ready)?.cards.orEmpty()

    /** Serializes read-modify-write cycles, so two quick edits can't overwrite each other. */
    private val mutex = Mutex()

    suspend fun load() {
        _state.value = try {
            CardsState.Ready(vault.loadCards())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CardsState.Failed(e)
        }
    }

    /** Makes this controller follow the card settings in [preferences] from now on (the app's container does this for its own). */
    fun followSettings(preferences: AppPreferences) {
        policy = { preferences.state.value.cardsPolicy }
    }

    /** False when cards are not being stored, or the vault couldn't be read or written; nothing was added then. */
    suspend fun add(card: StoredCard): Boolean {
        val rules = policy()
        if (!rules.storeCards) return false
        return mutate { it + rules.shape(card) }
    }

    suspend fun update(card: StoredCard): Boolean {
        val kept = policy().shape(card)
        return mutate { list -> list.map { if (it.id == card.id) kept else it } }
    }

    suspend fun delete(id: String): Boolean = mutate { list -> list.filter { it.id != id } }

    /** Puts a deleted [card] back where it was ([index], or at the end), unless one with its id is there already. */
    suspend fun restore(card: StoredCard, index: Int): Boolean {
        val rules = policy()
        if (!rules.storeCards) return false
        val kept = rules.shape(card)
        return mutate { list ->
            if (list.any { it.id == kept.id }) list else list.toMutableList().apply { add(index.coerceIn(0, size), kept) }
        }
    }

    /**
     * Takes the CVV off every stored card (Save CVV turned off). False when the vault couldn't be read or written, in which
     * case nothing was changed and the list still shows what is stored.
     */
    suspend fun removeCvvs(): Boolean = mutate { list -> list.map { if (it.hasCvv) it.copy(cvv = "") else it } }

    /**
     * Erases the vault: every card, with its number, expiry and CVV (Store cards turned off). Nothing is read first, so a vault
     * that can't be opened is erased all the same. False when it couldn't be erased, in which case the list is read again and
     * shows what is still stored.
     */
    suspend fun deleteAll(): Boolean = mutex.withLock {
        try {
            vault.clear()
            _state.value = CardsState.Ready(emptyList())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            load()
            false
        }
    }

    /**
     * Adds the cards read from a cards file or an app backup, skipping any already saved (see [CardsTransfer.merge]), under
     * the card settings: with "Store cards" off nothing is added and the result says so ([CardsTransfer.Merge.refused]), and
     * with "Save CVV" off the cards are added without their CVVs. Null when the vault couldn't be read or written, in which
     * case nothing was added.
     */
    suspend fun addAll(incoming: List<StoredCard>): CardsTransfer.Merge? = mutex.withLock {
        val rules = policy()
        // Refused before the vault is touched.
        if (!rules.storeCards) return@withLock CardsTransfer.merge(emptyList(), incoming, rules)
        if (_state.value !is CardsState.Ready) load()
        val stored = (_state.value as? CardsState.Ready)?.cards ?: return@withLock null
        val merge = CardsTransfer.merge(stored, incoming, rules)
        when {
            merge.added.isEmpty() -> merge
            persist(merge.applyTo(stored)) -> merge
            else -> null
        }
    }

    /**
     * Applies [change] to the cards actually stored and writes the result.
     *
     * Always starts from a successfully read vault: if the list hasn't loaded
     * yet (a card added from the reminder form before Wallet was ever opened)
     * it is read first, and if the vault can't be read nothing is written -
     * saving `[] + newCard` over an unreadable vault would erase every card in it.
     */
    private suspend fun mutate(change: (List<StoredCard>) -> List<StoredCard>): Boolean = mutex.withLock {
        if (_state.value !is CardsState.Ready) load()
        val stored = (_state.value as? CardsState.Ready)?.cards ?: return@withLock false
        persist(change(stored))
    }

    /** Shows [next] immediately, writes it, and on failure goes back to the stored list. */
    private suspend fun persist(next: List<StoredCard>): Boolean {
        _state.value = CardsState.Ready(next)
        return try {
            vault.saveCards(next)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            load()
            false
        }
    }
}
