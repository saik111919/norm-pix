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
import androidx.compose.material3.Icon
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
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.CardsState
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
    val matches = remember(catalog, usedKeys, choice.typed) { catalog.matching(choice.typed, usedKeys) }
    var open by remember { mutableStateOf(false) }

    fun pick(bank: Bank?) {
        haptics.selection()
        onChoice(BankChoice(bank?.key))
        open = false
        if (bank != null) onPicked()
    }

    fun addNew() {
        open = false
        scope.launch {
            val key = overlays.sheet<String> { dismiss -> BankFormSheet(initialName = choice.typed, onDone = dismiss) }
            if (key != null) {
                onChoice(BankChoice(key))
                onPicked()
            }
        }
    }

    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = choice.text(catalog),
            onValueChange = { text ->
                open = true
                onChoice(BankChoice(catalog.exact(text)?.key, text))
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                // Once the user moves on, a name that was a bank shows as the bank's own name.
                .onFocusChanged { if (!it.isFocused && choice.key != null && choice.typed.isNotEmpty()) onChoice(BankChoice(choice.key)) },
            label = { Text(if (optional) "$label (optional)" else label) },
            placeholder = { Text("Search or choose a bank") },
            leadingIcon = catalog.byKey(choice.key)?.let { bank ->
                { BankGlyph(bank, tint = scheme.onSurfaceVariant, size = 24.dp) }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            isError = errorText != null,
            supportingText = errorText?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
        ExposedDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 320.dp),
            // A popup over the sheet: nothing behind it is blurred, so it keeps the solid tone.
            containerColor = solidColorScheme.surfaceContainer,
            shape = AppRadius.rLg,
        ) {
            if (optional && choice.key != null) {
                DropdownMenuItem(
                    text = { Text("No bank") },
                    leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(24.dp)) },
                    onClick = { pick(null) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            for (bank in matches) {
                val isSelected = bank.key == choice.key
                DropdownMenuItem(
                    text = { Text(bank.name, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                    leadingIcon = { BankGlyph(bank, tint = scheme.onSurfaceVariant, size = 28.dp, modifier = Modifier.padding(2.dp)) },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.primary) }
                    } else {
                        null
                    },
                    onClick = { pick(bank) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .then(if (isSelected) Modifier.background(scheme.secondaryContainer.copy(alpha = 0.5f)) else Modifier)
                        .semantics { this.selected = isSelected },
                )
            }
            if (matches.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = AppSpacing.xs))
            DropdownMenuItem(
                text = { Text("Add new bank", color = scheme.primary) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, tint = scheme.primary) },
                onClick = ::addNew,
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
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
