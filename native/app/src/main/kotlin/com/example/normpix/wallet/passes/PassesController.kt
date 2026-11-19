package com.example.normpix.wallet.passes

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The passes as the Wallet sees them: still loading, ready, or the vault could not be read. */
@Immutable
sealed interface PassesState {
    data object Loading : PassesState

    data class Ready(val passes: List<StoredPass>) : PassesState

    data class Failed(val error: Throwable) : PassesState
}

/**
 * The one shared list of loyalty, membership and gift cards, backed by the encrypted [PassVault]: every change is written to it, and a
 * write that fails puts the list back to what is stored. Like the cards, a change always starts from a vault that was read: one that can't
 * be is never overwritten with a new list (that would erase every pass in it).
 */
class PassesController(private val vault: PassVault) {
    private val _state = MutableStateFlow<PassesState>(PassesState.Loading)
    val state: StateFlow<PassesState> = _state.asStateFlow()

    val passes: List<StoredPass> get() = (_state.value as? PassesState.Ready)?.passes.orEmpty()

    private val mutex = Mutex()

    suspend fun load() {
        _state.value = try {
            PassesState.Ready(vault.load())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PassesState.Failed(e)
        }
    }

    /** False when the vault couldn't be read or written; nothing was added then. */
    suspend fun add(pass: StoredPass): Boolean = mutate { it + pass }

    suspend fun update(pass: StoredPass): Boolean = mutate { list -> list.map { if (it.id == pass.id) pass else it } }

    suspend fun delete(id: String): Boolean = mutate { list -> list.filter { it.id != id } }

    /** Puts a deleted [pass] back where it was ([index]), unless one with its id is there already (Undo). */
    suspend fun restore(pass: StoredPass, index: Int): Boolean = mutate { list ->
        if (list.any { it.id == pass.id }) list else list.toMutableList().apply { add(index.coerceIn(0, size), pass) }
    }

    /**
     * Adds the passes read from a cards file or an app backup, skipping any already kept (see [mergePasses]). Gives how many were added
     * and how many were there already, or null when the vault couldn't be read or written (nothing was added).
     */
    suspend fun addAll(incoming: List<StoredPass>): Pair<Int, Int>? = mutex.withLock {
        if (_state.value !is PassesState.Ready) load()
        val stored = (_state.value as? PassesState.Ready)?.passes ?: return@withLock null
        val (added, already) = mergePasses(stored, incoming)
        when {
            added.isEmpty() -> 0 to already
            persist(stored + added) -> added.size to already
            else -> null
        }
    }

    private suspend fun mutate(change: (List<StoredPass>) -> List<StoredPass>): Boolean = mutex.withLock {
        if (_state.value !is PassesState.Ready) load()
        val stored = (_state.value as? PassesState.Ready)?.passes ?: return@withLock false
        persist(change(stored))
    }

    private suspend fun persist(next: List<StoredPass>): Boolean {
        _state.value = PassesState.Ready(next)
        return try {
            vault.save(next)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            load()
            false
        }
    }
}
