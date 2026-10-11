package com.example.normpix.finance.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.billPayment
import com.example.normpix.finance.domain.halfOf
import com.example.normpix.finance.domain.minorToInputText
import com.example.normpix.finance.domain.nextBillDue
import com.example.normpix.finance.domain.parseMinorUnits
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Pays a credit card's bill, shown as a bottom sheet. Paying a card is a *transfer* from another account onto the card - not
 * an expense - so the money is spent once, when it is charged to the card, and never again when the bill is settled.
 *
 * The amount starts at everything owed and can be changed (or set to half in one tap): a line under it works out what that
 * leaves - a bill still to pay, the card cleared, or, if more than the bill is paid, the part that stays on the card as
 * credit. Calls [onDone] with true once saved, or null when closed without paying.
 */
@Composable
fun PayBillSheet(card: Account, onDone: (Boolean?) -> Unit) {
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val snackbars = LocalSnackbars.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val money = finance.money
    val decimals = finance.currency.decimals

    val owed = maxOf(0L, -finance.balanceOf(card.id!!))
    val sources = finance.activeAccounts.filter { it.id != card.id }

    var amount by remember { mutableStateOf(if (owed > 0) minorToInputText(owed, decimals) else "") }
    var sourceId by remember {
        mutableStateOf((finance.defaultAccount?.takeIf { it.id != card.id } ?: sources.firstOrNull())?.id)
    }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    var pickingDate by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    val initialSnapshot = remember { listOf(amount, sourceId, date, note).joinToString("|") }
    DiscardChangesGuard(
        dirty = listOf(amount, sourceId, date, note).joinToString("|") != initialSnapshot,
        saving = saving,
        isEditing = false,
        keptMessage = "",
    )

    val parsed = parseMinorUnits(amount, decimals)?.takeIf { it > 0 }
    val outcome = parsed?.let { billPayment(owed, it) }
    val role = TransactionType.Transfer.role()
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { amountFocus.requestFocus() } }

    fun save() {
        formError = null
        amountError = if (parsed == null) "Enter an amount greater than zero" else null
        sourceError = if (sourceId == null) "Choose where the money comes from" else null
        if (parsed == null || sourceId == null) return
        saving = true
        val now = LocalDateTime.now()
        val draft = FinanceTransaction(
            type = TransactionType.Transfer,
            amount = parsed,
            accountId = sourceId!!,
            toAccountId = card.id,
            occurredAt = LocalDateTime.of(date, LocalTime.now().withSecond(0).withNano(0)),
            note = note.ifBlank { "Card bill payment" },
            createdAt = now,
            updatedAt = now,
        )
        scope.launch {
            try {
                controller.saveTransaction(draft)
                haptics.success()
                snackbars.show("Paid ${money.format(parsed)} to ${card.name}")
                onDone(true)
            } catch (e: FinanceValidationException) {
                formError = e.message
            } catch (e: FinanceConflictException) {
                formError = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                formError = "Couldn't save. Your other data is unchanged."
            } finally {
                saving = false
            }
        }
    }

    EditorSheet(
        title = "Pay ${card.name}",
        // What is owed, and - when the card has a due day - when it falls due.
        subtitle = if (owed > 0) {
            listOfNotNull("${money.format(owed)} owed", nextBillDue(card.dueDay, LocalDate.now())?.line()).joinToString(" · ")
        } else {
            "Nothing is owed on this card right now"
        },
        saveLabel = if (parsed != null) "Pay ${money.format(parsed)}" else "Pay bill",
        onSave = ::save,
        saving = saving,
        saveColors = ButtonDefaults.buttonColors(containerColor = role.color, contentColor = role.onColor),
    ) {
        HeroAmountField(
            value = amount,
            onValueChange = {
                amount = it
                amountError = null
            },
            currency = finance.currency,
            accent = role,
            errorText = amountError,
            label = "Amount to pay",
            modifier = Modifier.focusRequester(amountFocus),
        )
        if (owed > 0) {
            Spacer(Modifier.height(AppSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                val half = halfOf(owed)
                BillChip("Full ${money.format(owed, trimZeroFraction = true)}", parsed == owed) {
                    haptics.selection()
                    amount = minorToInputText(owed, decimals)
                    amountError = null
                }
                if (half in 1 until owed) {
                    BillChip("Half ${money.format(half, trimZeroFraction = true)}", parsed == half) {
                        haptics.selection()
                        amount = minorToInputText(half, decimals)
                        amountError = null
                    }
                }
            }
        }
        // What this payment leaves, worked out as the amount changes.
        if (outcome != null) {
            Spacer(Modifier.height(AppSpacing.md))
            val (text, color) = when {
                outcome.creditAfter > 0 ->
                    "${money.format(outcome.creditAfter)} more than the bill. It stays on the card as credit toward your next spending." to
                        MaterialTheme.colorScheme.tertiary
                outcome.settled -> "Paid in full. Nothing will be owed on ${card.name}." to StatusTone.Success.resolve().color
                else -> "${money.format(outcome.owedAfter)} will still be owed after this." to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.animateContentSize(AppMotion.spatial()))
        }
        Spacer(Modifier.height(AppSpacing.xl))
        if (sources.isEmpty()) {
            Text(
                "Add another account in Wallet to pay from.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            AccountSelector(
                label = "Pay from",
                accounts = sources,
                selected = sourceId,
                onSelect = {
                    sourceId = it
                    sourceError = null
                },
                errorText = sourceError,
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))
        TonalPickerField(
            label = "Date",
            value = shortDate(date.atStartOfDay()),
            icon = Icons.Outlined.CalendarToday,
            onClick = { pickingDate = true },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AppSpacing.md))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
            placeholder = { Text("Card bill payment") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
        formError?.let {
            Spacer(Modifier.height(AppSpacing.lg))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }

    if (pickingDate) {
        DatePickerDialogFor(
            initial = date,
            onPicked = {
                pickingDate = false
                date = it
            },
            onDismiss = { pickingDate = false },
        )
    }
}

@Composable
private fun BillChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
