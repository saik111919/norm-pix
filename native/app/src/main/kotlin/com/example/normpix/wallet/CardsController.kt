package com.example.normpix.wallet

import androidx.compose.runtime.Immutable
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
 */
class CardsController(private val vault: CardVault) {
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

    suspend fun add(card: StoredCard): Boolean = mutate { it + card }

    suspend fun update(card: StoredCard): Boolean = mutate { list -> list.map { if (it.id == card.id) card else it } }

    suspend fun delete(id: String): Boolean = mutate { list -> list.filter { it.id != id } }

    /** Puts a deleted [card] back where it was ([index], or at the end), unless one with its id is there already. */
    suspend fun restore(card: StoredCard, index: Int): Boolean = mutate { list ->
        if (list.any { it.id == card.id }) list else list.toMutableList().apply { add(index.coerceIn(0, size), card) }
    }

    /**
     * Adds the cards read from a cards file, skipping any already saved (see [CardsTransfer.merge]). Null when the vault
     * couldn't be read or written, in which case nothing was added.
     */
    suspend fun addAll(incoming: List<StoredCard>): CardsTransfer.Merge? = mutex.withLock {
        if (_state.value !is CardsState.Ready) load()
        val stored = (_state.value as? CardsState.Ready)?.cards ?: return@withLock null
        val merge = CardsTransfer.merge(stored, incoming)
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
