package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.CardsState
import com.example.normpix.wallet.CardsTransfer
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val cardsFileDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * Exporting and importing saved cards on their own (Settings > Cards). The file is always locked with a password the user
 * picks (see [CardsTransfer]); exporting asks for the biometric unlock first while Wallet lock is on, since the file holds
 * every card in full. Importing only ever adds cards: one already saved is skipped, nothing is replaced.
 */
internal class CardsTransferFlows(
    private val container: AppContainer,
    private val overlays: AppOverlays,
    private val snackbars: AppSnackbars,
    private val files: BackupFiles,
) {
    suspend fun export() {
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
        val password = overlays.sheet<String> { dismiss -> CardsPasswordSheet(forExport = true, cardCount = cards.size, onDone = dismiss) } ?: return
        val now = LocalDateTime.now()
        val text = overlays.loading("Locking the file") {
            withContext(Dispatchers.Default) { CardsTransfer.encode(cards, password.toCharArray(), exportedAt = now) }
        } ?: return
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
        // Asked again, with a word on why, until the password opens it or the user gives up.
        var error: String? = null
        var opened: CardsTransfer.Result.Cards? = null
        while (opened == null) {
            val shownError = error
            val password = overlays.sheet<String> { dismiss ->
                CardsPasswordSheet(forExport = false, cardCount = 0, onDone = dismiss, error = shownError)
            } ?: return
            val result = overlays.loading("Opening the file") {
                withContext(Dispatchers.Default) { CardsTransfer.decode(text, password.toCharArray()) }
            } ?: return
            when (result) {
                is CardsTransfer.Result.Cards -> opened = result
                CardsTransfer.Result.WrongPassword -> error = "That password doesn't open this file. Try again."
                is CardsTransfer.Result.Invalid -> {
                    problem(result.reason)
                    return
                }
            }
        }
        val found = opened ?: return
        if (found.cards.isEmpty()) {
            snackbars.show("The file has no cards in it.")
            return
        }
        val merge = container.cards.addAll(found.cards)
        if (merge == null) {
            snackbars.show("The cards couldn't be saved, so nothing was added.", SnackbarKind.Error)
            return
        }
        snackbars.show(
            when {
                merge.added.isEmpty() -> "Every card in the file is already in Wallet"
                merge.alreadyThere == 0 -> "${count(merge.added.size)} added to Wallet"
                else -> "${count(merge.added.size)} added. ${merge.alreadyThere} already in Wallet, kept as they were"
            },
        )
    }

    private suspend fun problem(reason: String) {
        overlays.alert("Can't use this file", "Nothing was changed.\n\n$reason", icon = Icons.Outlined.ErrorOutline)
    }

    private fun count(n: Int) = "$n ${if (n == 1) "card" else "cards"}"

    private companion object {
        const val NOT_A_CARDS_FILE = "This file isn't a NormPix cards file. Cards files are made with Export cards, in Settings > Cards."
    }
}

/**
 * Asks for the cards file's password. Exporting sets one - at least [CardsTransfer.MIN_PASSWORD] characters, typed twice -
 * and says it can't be recovered; importing asks for the one it was locked with. Calls [onDone] with it, or null if closed.
 */
@Composable
internal fun CardsPasswordSheet(forExport: Boolean, cardCount: Int, onDone: (String?) -> Unit, error: String? = null) {
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
            "${cardCount} ${if (cardCount == 1) "card" else "cards"}, with their full numbers, expiry and CVV"
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
