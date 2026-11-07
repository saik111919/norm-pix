package com.example.normpix.banks.ui

import com.example.normpix.ui.components.solidColorScheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import com.example.normpix.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.normpix.banks.Bank
import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.ui.ChoicePickerField
import com.example.normpix.finance.ui.LocalFinance
import com.example.normpix.finance.ui.TonalFieldShape
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.finance.ui.tonalFieldColors
import com.example.normpix.finance.ui.typedFieldColors
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.ui.components.BankGlyph
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.OptionSheet
import com.example.normpix.ui.components.OptionSheetItem
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.CardsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * What a bank field holds: the [key] of the bank picked, and [typed], whatever the user has typed to find one.
 * Typing a name that is a known bank's picks it; anything else leaves [key] null until a bank is chosen or added, so
 * free text never becomes a bank on its own (that is how "HDFC" and "HDFC Bank" used to become two).
 */
@Immutable
data class BankChoice(val key: String? = null, val typed: String = "") {
    /** The text the field shows. */
    fun text(catalog: BankCatalog): String = typed.ifEmpty { catalog.byKey(key)?.name.orEmpty() }

    /** True when the user has typed something that isn't a bank (yet). */
    val unmatched: Boolean get() = key == null && typed.isNotBlank()
}

/**
 * The bank field of the account and card forms: one searchable list of every bank the app knows ([BankCatalog], the
 * built-in directory plus the banks the user added), the ones already in use first, with "Add new bank" at its end.
 * A bank added from either form is in the other's list at once, because both read the same catalog.
 *
 * [optional] fields (an account may have no bank) offer "No bank" once one is picked. [onPicked] runs after a bank is
 * picked from the list, so a form can carry on to its next field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSelector(
    choice: BankChoice,
    onChoice: (BankChoice) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Bank",
    optional: Boolean = false,
    errorText: String? = null,
    focusRequester: FocusRequester? = null,
    onPicked: () -> Unit = {},
) {
    val finance = rememberFinanceState()
    val container = LocalAppContainer.current
    val cardsState by container.cards.state.collectAsState()
    val overlays = LocalOverlays.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val catalog = finance.catalog

    // The banks already in use come first, so the user's own banks are one tap away.
    val usedKeys = remember(finance.accounts, cardsState) {
        buildSet {
            finance.accounts.forEach { it.bankKey?.let(::add) }
            (cardsState as? CardsState.Ready)?.cards?.forEach { it.bankKey?.let(::add) }
        }
    }

    fun pick(bank: Bank?) {
        haptics.selection()
        onChoice(BankChoice(bank?.key))
        if (bank != null) onPicked()
    }

    val openSheet: () -> Unit = {
        scope.launch {
            val totalCount = catalog.all.size
            val pickedKey = overlays.sheet<String> { dismiss ->
                val options = buildList {
                    if (optional && choice.key != null) {
                        add(
                            OptionSheetItem(
                                key = "",
                                label = "No bank",
                                leading = {
                                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(24.dp))
                                },
                            ),
                        )
                    }
                    for (bank in catalog.matching("", usedKeys)) {
                        add(
                            OptionSheetItem(
                                key = bank.key,
                                label = bank.name,
                                leading = {
                                    BankGlyph(bank, tint = scheme.onSurfaceVariant, size = 28.dp, modifier = Modifier.padding(2.dp))
                                },
                            ),
                        )
                    }
                }
                OptionSheet(
                    title = "Choose bank",
                    subtitle = "$totalCount banks",
                    searchPlaceholder = "Search banks",
                    options = options,
                    selected = if (choice.key != null) setOf(choice.key) else emptySet(),
                    multiple = false,
                    onSelect = { key ->
                        dismiss(key)
                    },
                    actionLabel = "Add new bank",
                    actionIcon = Icons.Rounded.Add,
                    onAction = {
                        dismiss("__add__")
                    },
                )
            }
            if (pickedKey != null) {
                if (pickedKey == "__add__") {
                    val newKey = overlays.sheet<String> { dismiss -> BankFormSheet(initialName = "", onDone = dismiss) }
                    if (newKey != null) {
                        onChoice(BankChoice(newKey))
                        onPicked()
                    }
                } else if (pickedKey.isEmpty()) {
                    pick(null)
                } else {
                    val bank = catalog.byKey(pickedKey)
                    pick(bank)
                }
            }
        }
    }

    val selectedBank = catalog.byKey(choice.key)
    ChoicePickerField(
        label = if (optional) "$label (optional)" else label,
        value = selectedBank?.name ?: choice.text(catalog).ifEmpty { null },
        placeholder = "Choose a bank",
        onClick = openSheet,
        leading = selectedBank?.let { bank ->
            { BankGlyph(bank, tint = scheme.onSurfaceVariant, size = 24.dp) }
        },
        onClear = if (optional && choice.key != null) {
            {
                pick(null)
            }
        } else null,
        errorText = errorText,
        modifier = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
    )
}

/**
 * Renames a bank the user added (a bottom sheet). Every account and card that uses it follows the new name, since they point at
 * its key. Calls [onDone] with true once renamed, or null if cancelled. A name that is another bank's is refused with a line
 * saying whose it is.
 */
@Composable
fun BankRenameSheet(bank: Bank, onDone: (Boolean?) -> Unit) {
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(bank.name) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val takenBy = finance.catalog.exact(name)?.takeIf { it.key != bank.key }
    DiscardChangesGuard(dirty = name != bank.name, saving = saving, isEditing = true, keptMessage = "The bank keeps its name.")

    fun save() {
        saving = true
        error = null
        scope.launch {
            try {
                controller.renameBank(bank.key, name)
                haptics.success()
                onDone(true)
            } catch (e: FinanceValidationException) {
                error = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't rename the bank. Nothing was changed."
            } finally {
                saving = false
            }
        }
    }

    EditorSheet(
        title = "Rename bank",
        subtitle = "Your accounts and cards that use it follow",
        saveLabel = "Rename",
        onSave = ::save,
        saving = saving,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                error = null
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("Bank name") },
            isError = error != null || takenBy != null,
            supportingText = (error ?: takenBy?.let { "${it.name} is already in your list." })?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
        Spacer(Modifier.height(AppSpacing.sm))
    }
}

/**
 * Adds a bank (shown in a bottom sheet over the form that asked). Calls [onDone] with the bank's key, or null if
 * cancelled. A name the app already knows ("hdfc bank ltd") is not added again: that bank is used, and the sheet says so.
 */
@Composable
fun BankFormSheet(initialName: String, onDone: (String?) -> Unit) {
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val existing = finance.catalog.exact(name)
    DiscardChangesGuard(dirty = name != initialName, saving = saving, isEditing = false, keptMessage = "The bank isn't added.")

    fun save() {
        saving = true
        error = null
        scope.launch {
            try {
                val key = controller.bankKeyFor(name)
                haptics.success()
                onDone(key)
            } catch (e: FinanceValidationException) {
                error = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't add the bank. Nothing was changed."
            } finally {
                saving = false
            }
        }
    }

    EditorSheet(
        title = "Add bank",
        subtitle = "Shared by your accounts and cards",
        saveLabel = if (existing != null) "Use ${existing.name}" else "Add bank",
        onSave = ::save,
        saving = saving,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                error = null
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("Bank name") },
            placeholder = { Text("e.g. Federal Bank, My Credit Union") },
            isError = error != null,
            supportingText = (error ?: existing?.let { "${it.name} is already in your list, so it will be used." })?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
        Spacer(Modifier.height(AppSpacing.sm))
    }
}
