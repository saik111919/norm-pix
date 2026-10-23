package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.normpix.AppContainer
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.ConfirmSheetContent
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.CardsState
import com.example.normpix.wallet.CardsTransfer
import com.example.normpix.wallet.StoredCard
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val cardsFileDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** What an app backup does with the saved cards (see [CardsTransferFlows.forBackup]). */
internal sealed interface CardsForBackup {
    /** They are not in the backup: there are none to keep, Wallet keeps no cards, or the user chose to leave them out. */
    data object LeftOut : CardsForBackup

    /** They are in the backup, locked with the password just set: the cards file's text (see [CardsTransfer.encode]) and how many cards it holds. */
    class Locked(val file: String, val count: Int) : CardsForBackup
}

/**
 * Exporting and importing saved cards on their own (Settings > Wallet & Cards). The file is always locked with a password the user
 * picks (see [CardsTransfer]); exporting asks for the biometric unlock first while Wallet lock is on, since the file holds
 * every card in full. Importing only ever adds cards: one already saved is skipped, nothing is replaced. Both follow the card
 * settings: nothing is exported or imported while Store cards is off, and an import leaves CVVs out while Save CVV is off.
 */
internal class CardsTransferFlows(
    private val container: AppContainer,
    private val overlays: AppOverlays,
    private val snackbars: AppSnackbars,
    private val files: BackupFiles,
) {
    suspend fun export() {
        if (!container.preferences.state.value.walletCardsEnabled) {
            snackbars.show("NormPix isn't storing cards, so there are none to export.", SnackbarKind.Warning)
            return
        }
        val cardsController = container.cards
        if (cardsController.state.value !is CardsState.Ready) cardsController.load()
        val state = cardsController.state.value
        if (state !is CardsState.Ready) {
            snackbars.show("Your cards can't be read right now, so nothing was exported.", SnackbarKind.Error)
            return
        }
        val cards = state.cards
        if (cards.isEmpty()) {
            snackbars.show("There are no saved cards to export.")
            return
        }
        if (container.preferences.state.value.walletLockEnabled && !container.lock.authenticate("Unlock to export your cards")) {
            snackbars.show("Couldn't unlock, so nothing was exported.", SnackbarKind.Error)
            return
        }
        val now = LocalDateTime.now()
        val text = lock(cards, now) ?: return
        val saved = try {
            files.save("normpix-cards-${cardsFileDate.format(now)}.json", "application/json", text.toByteArray(Charsets.UTF_8))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            snackbars.show("Couldn't save the file there. Try another location.", SnackbarKind.Error)
            return
        }
        if (saved) snackbars.show("${count(cards.size)} exported, locked with your password")
    }

    suspend fun import() {
        if (container.preferences.state.value.dummyDataEnabled) {
            overlays.alert(
                "Sample data is on",
                "Cards cannot be imported while sample data is showing. Turn it off in Settings > Data & backup first.",
                icon = Icons.Outlined.ErrorOutline,
            )
            return
        }
        // Refused before the file is even asked for: with cards not stored there is nowhere to put them.
        if (!container.preferences.state.value.walletCardsEnabled) {
            overlays.alert(
                "NormPix isn't storing cards",
                "Nothing was imported. Turn on Store cards in Settings > Wallet & Cards first, then import the file.",
                icon = Icons.Outlined.ErrorOutline,
            )
            return
        }
        val bytes = try {
            files.open() ?: return
        } catch (e: CancellationException) {
            throw e
        } catch (_: BackupTooLargeException) {
            problem("This file is too large to be a NormPix cards file.")
            return
        } catch (_: Exception) {
            problem("The file couldn't be read.")
            return
        }
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .removePrefix("\uFEFF")
        } catch (_: CharacterCodingException) {
            problem(NOT_A_CARDS_FILE)
            return
        }
        if (!CardsTransfer.isCardFile(text)) {
            problem(NOT_A_CARDS_FILE)
            return
        }
        val found = unlock(text) ?: return
        if (found.cards.isEmpty()) {
            snackbars.show("The file has no cards in it.")
            return
        }
        // The controller holds the import to the card settings: refused while Store cards is off (it may have been turned off
        // while the password was being typed), and CVVs left out while Save CVV is off.
        val merge = container.cards.addAll(found.cards)
        if (merge == null) {
            snackbars.show("The cards couldn't be saved, so nothing was added.", SnackbarKind.Error)
            return
        }
        if (merge.refused) {
            snackbars.show("NormPix isn't storing cards, so nothing was added.", SnackbarKind.Warning)
            return
        }
        val cvvLeftOut = merge.added.isNotEmpty() && !container.preferences.state.value.cardCvvEnabled && found.cards.any { it.hasCvv }
        val said = when {
            merge.added.isEmpty() -> "Every card in the file is already in Wallet"
            merge.alreadyThere == 0 -> "${count(merge.added.size)} added to Wallet"
            else -> "${count(merge.added.size)} added. ${merge.alreadyThere} already in Wallet, kept as they were"
        }
        snackbars.show(if (cvvLeftOut) "$said. CVVs weren't kept, because Save CVV is off" else said)
    }

    /**
     * Asks for a password and locks [cards] with it: the text of a cards file. Null if the user closed the sheet. Building the
     * key takes a moment, so a sheet says so meanwhile.
     */
    private suspend fun lock(cards: List<StoredCard>, now: LocalDateTime): String? {
        val password = overlays.sheet<String> { dismiss ->
            // Worked out in the sheet, not before it: a Boolean held by the sheet's lambda trips the build's Compose stack
            // trace mapping ("Failed to tokenize ... lock").
            CardsPasswordSheet(forExport = true, cardCount = cards.size, onDone = dismiss, withCvv = cards.any { it.hasCvv })
        } ?: return null
        return overlays.loading("Locking the file") {
            withContext(Dispatchers.Default) { CardsTransfer.encode(cards, password.toCharArray(), exportedAt = now) }
        }
    }

    /**
     * Opens the cards file [text] with the password the user enters, asked again - with a word on why - until it opens or they give
     * up. Null when they gave up, or when the file turned out to be unusable (which is said). [alone]: the file is being imported
     * by itself, so "Nothing was changed" is true to say of it.
     */
    suspend fun unlock(text: String, alone: Boolean = true): CardsTransfer.Result.Cards? {
        var error: String? = null
        while (true) {
            val shownError = error
            val password = overlays.sheet<String> { dismiss ->
                CardsPasswordSheet(forExport = false, cardCount = 0, onDone = dismiss, error = shownError)
            } ?: return null
            val result = overlays.loading("Opening the file") {
                withContext(Dispatchers.Default) { CardsTransfer.decode(text, password.toCharArray()) }
            } ?: return null
            when (result) {
                is CardsTransfer.Result.Cards -> return result
                CardsTransfer.Result.WrongPassword -> error = "That password doesn't open this file. Try again."
                is CardsTransfer.Result.Invalid -> {
                    problem(result.reason, nothingChanged = alone)
                    return null
                }
            }
        }
    }

    /**
     * Settles what an app backup does with the saved cards, before anything is written: asks "Include saved cards?" (cards are
     * a lot to put in a file, so they are only there when the user says so), unlocks with the biometric prompt while Wallet lock
     * is on, and has the user set the password they are locked with. Nothing is asked where there is nothing to ask - Wallet keeps
     * no cards, or has none. Null if the user backed out of the whole backup (closed a sheet, or couldn't unlock).
     */
    suspend fun forBackup(now: LocalDateTime): CardsForBackup? {
        if (!container.preferences.state.value.walletCardsEnabled) return CardsForBackup.LeftOut
        val cardsController = container.cards
        if (cardsController.state.value !is CardsState.Ready) cardsController.load()
        val state = cardsController.state.value
        if (state !is CardsState.Ready) {
            val goOn = overlays.confirm(
                title = "Back up without your cards?",
                message = "Your saved cards can't be read right now, so they can't go into the backup. Everything else can.",
                confirmLabel = "Back up without them",
                icon = Icons.Outlined.CreditCard,
            )
            return if (goOn) CardsForBackup.LeftOut else null
        }
        val cards = state.cards
        if (cards.isEmpty()) return CardsForBackup.LeftOut
        val include = overlays.sheet<Boolean> { dismiss ->
            ConfirmSheetContent(
                title = "Include saved cards?",
                message = "${count(cards.size)} with their full numbers, expiry and CVV where one is kept. They are locked with a " +
                    "password you set next, and nothing else in the backup is.",
                confirmLabel = "Include",
                cancelLabel = "Leave out",
                destructive = false,
                icon = Icons.Outlined.CreditCard,
                onConfirm = { dismiss(true) },
                onCancel = { dismiss(false) },
            )
        } ?: return null
        if (!include) return CardsForBackup.LeftOut
        if (container.preferences.state.value.walletLockEnabled && !container.lock.authenticate("Unlock to back up your cards")) {
            snackbars.show("Couldn't unlock, so nothing was backed up.", SnackbarKind.Error)
            return null
        }
        val file = lock(cards, now) ?: return null
        return CardsForBackup.Locked(file, cards.size)
    }

    private suspend fun problem(reason: String, nothingChanged: Boolean = true) {
        if (nothingChanged) {
            overlays.alert("Can't use this file", "Nothing was changed.\n\n$reason", icon = Icons.Outlined.ErrorOutline)
        } else {
            overlays.alert("Can't open the saved cards", reason, icon = Icons.Outlined.ErrorOutline)
        }
    }

    private fun count(n: Int) = "$n ${if (n == 1) "card" else "cards"}"

    private companion object {
        const val NOT_A_CARDS_FILE = "This file isn't a NormPix cards file. Cards files are made with Export cards, in Settings > Wallet & Cards."
    }
}

/**
 * Asks for the cards file's password. Exporting sets one - at least [CardsTransfer.MIN_PASSWORD] characters, typed twice -
 * and says it can't be recovered; importing asks for the one it was locked with. Calls [onDone] with it, or null if closed.
 */
@Composable
internal fun CardsPasswordSheet(
    forExport: Boolean,
    cardCount: Int,
    onDone: (String?) -> Unit,
    error: String? = null,
    /** Whether any card being exported has a CVV, so the sheet says what the file holds. */
    withCvv: Boolean = true,
) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf(error) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun submit() {
        problem = when {
            password.isEmpty() -> "Enter the password"
            forExport && password.length < CardsTransfer.MIN_PASSWORD -> "Use at least ${CardsTransfer.MIN_PASSWORD} characters"
            forExport && again != password -> "The two passwords don't match"
            else -> null
        }
        if (problem == null) onDone(password)
    }

    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    val eye: @Composable () -> Unit = {
        IconButton(onClick = { visible = !visible }) {
            Icon(
                if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (visible) "Hide password" else "Show password",
            )
        }
    }
    EditorSheet(
        title = if (forExport) "Lock the cards file" else "Open the cards file",
        subtitle = if (forExport) {
            "${cardCount} ${if (cardCount == 1) "card" else "cards"}, with their full numbers and expiry" +
                if (withCvv) " and CVV where one is saved" else ""
        } else {
            "Enter the password it was locked with"
        },
        saveLabel = if (forExport) "Export" else "Open",
        onSave = ::submit,
    ) {
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                problem = null
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = transformation,
            trailingIcon = eye,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = if (forExport) ImeAction.Next else ImeAction.Done,
            ),
            isError = problem != null && !forExport,
        )
        if (forExport) {
            Spacer(Modifier.height(AppSpacing.md))
            OutlinedTextField(
                value = again,
                onValueChange = {
                    again = it
                    problem = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password again") },
                singleLine = true,
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                isError = problem != null,
            )
            Spacer(Modifier.height(AppSpacing.md))
            Text(
                "Anyone with the file and this password can see your cards in full. NormPix can't recover a forgotten password, " +
                    "so keep it somewhere safe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        problem?.let {
            Spacer(Modifier.height(AppSpacing.md))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}
