package com.example.normpix.finance.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Unarchive
import com.example.normpix.ui.components.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.RepeatSuggestion
import com.example.normpix.finance.domain.SavingsGoal
import com.example.normpix.finance.domain.SplitPart
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.billingCycleSummary
import com.example.normpix.finance.domain.firstOccurrenceAfter
import com.example.normpix.finance.domain.goalSaved
import com.example.normpix.finance.domain.settledCutoff
import com.example.normpix.finance.share.NoSharedPictureReader
import com.example.normpix.finance.share.PaymentAppReceipts
import com.example.normpix.finance.share.PictureText
import com.example.normpix.finance.share.SharedPictureReader
import com.example.normpix.finance.share.SharedStatus
import com.example.normpix.finance.share.SharedTextReading
import com.example.normpix.finance.share.SharedTransactionImport
import com.example.normpix.finance.share.SharedTransactionPrefill
import com.example.normpix.navigation.AppNavigator
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppHaptics
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.CardsController
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * User-facing finance flows (form/sheet/dialog -> controller -> feedback),
 * shared by every screen that offers the same action. They run on the app
 * scope, so a flow still finishes (and reports) when the row that started it
 * disappears - e.g. after a delete.
 */
class FinanceActions(
    private val finance: FinanceController,
    private val overlays: AppOverlays,
    private val snackbars: AppSnackbars,
    private val navigator: AppNavigator,
    private val haptics: AppHaptics,
    private val scope: CoroutineScope,
    /** Starts a reminder about something in the ledger; null where reminders are not available (then none is offered). */
    private val reminders: ReminderLauncher? = null,
    /** The Wallet's cards, whose numbers tell a shared receipt's account; null where there is no Wallet. */
    private val cards: CardsController? = null,
) {
    private val state get() = finance.state.value

    /**
     * What a sheet that holds an editor is drawn inside: with the reminder launcher in reach. Sheets are drawn by the overlay host,
     * which stands above where the launcher is provided, so an editor opened in one saw none - and a new repeating payment was
     * given no reminder of its own, nor an edited one's reminder kept in step (found by `RecurringReminderLinkTest`).
     */
    @Composable
    private fun WithReminders(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalReminderLauncher provides reminders, content = content)
    }

    /** The new-transaction sheet; [accountId], when given, is the account it starts on (a card's "Add expense"). */
    fun addTransaction(type: TransactionType = TransactionType.Expense, accountId: Long? = null, personId: Long? = null) {
        scope.launch {
            overlays.sheet<Boolean> { dismiss ->
                WithReminders { TransactionEditorSheet(type, existingId = null, onDone = dismiss, initialAccountId = accountId, initialPersonId = personId) }
            }
        }
    }

    /**
     * A transaction from what another app shared - a payment confirmation, a bank SMS, or a receipt [picture] whose text
     * [reader] reads on this device: read into a draft, then the new-transaction sheet opens with what could be read, says
     * what couldn't, and has the picture attached. Nothing is saved until the user saves. A share with nothing to read
     * says so; a payment the message says failed, or a request, asks first.
     */
    fun addFromShare(text: String, picture: Uri? = null, reader: SharedPictureReader = NoSharedPictureReader) {
        scope.launch {
            val fromPicture = picture != null
            // A copy made straight away, while the sharing app still lets NormPix read the picture: the form attaches it.
            // Whatever the form didn't keep is deleted once it closes (or here, when no form opens).
            var staged: File? = null
            try {
                // A sheet says it is being read, and the form (or a question) comes up once it goes. Cancelled: nothing opens.
                val read = if (picture == null) {
                    null
                } else {
                    val pictureRead = overlays.loading(READING_PICTURE, READING_PICTURE_MESSAGE) {
                        staged = finance.pictures?.stage(picture)
                        // Read from the original, which is as sharp as it gets (the copy is scaled and compressed).
                        PictureRead(reader.read(picture))
                    } ?: return@launch
                    pictureRead.text
                }
                val pictureText = read?.text
                if (fromPicture && pictureText.isNullOrBlank() && text.isBlank()) {
                    val enter = overlays.confirm(
                        title = "Couldn't read the picture",
                        message = "NormPix found no text in it that it could read. You can still enter the transaction yourself" +
                            if (staged != null) ", with the picture attached." else ".",
                        confirmLabel = "Enter it myself",
                    )
                    if (enter) openSharedDraft(SharedTransactionPrefill.nothingRead(fromPicture = true).copy(picture = staged))
                    return@launch
                }
                // Words that came with a picture are read together with the picture's own.
                val shared = listOf(text, pictureText.orEmpty()).filter { it.isNotBlank() }.joinToString("\n\n")
                val now = LocalDateTime.now()
                // A payment app's receipt is read by that app's own reader; anything else by the general rules.
                when (val reading = PaymentAppReceipts.read(text, read, state.currency, now.toLocalDate())) {
                    SharedTextReading.Empty -> overlays.alert(
                        title = "Nothing to add",
                        message = "NormPix can add a transaction from the message or the receipt picture a payment or bank app " +
                            "shares. This share had neither in it.",
                    )
                    SharedTextReading.TooLong -> overlays.alert(
                        title = "Too long to read",
                        message = "That's much longer than a payment message, so NormPix didn't read it. " +
                            "Share just the payment's message, or add the transaction yourself.",
                    )
                    SharedTextReading.NotATransaction -> {
                        val enter = overlays.confirm(
                            title = "No transaction found",
                            message = "The shared text has no amount or payment in it that NormPix can read. You can still enter it yourself.",
                            confirmLabel = "Enter it myself",
                        )
                        if (enter) {
                            openSharedDraft(SharedTransactionPrefill.nothingRead(fromPicture).copy(readText = pictureText, picture = staged))
                        }
                    }
                    is SharedTextReading.Draft -> {
                        val draft = reading.draft
                        val goAhead = when (draft.status) {
                            SharedStatus.Failed -> overlays.confirm(
                                title = "This payment failed",
                                message = "The message says it didn't go through, and a failed payment usually moves no money. " +
                                    "Add it only if money really left or reached your account.",
                                confirmLabel = "Add anyway",
                            )
                            SharedStatus.Requested -> overlays.confirm(
                                title = "This is a payment request",
                                message = "Nothing has been paid yet. Add it once the money has actually moved.",
                                confirmLabel = "Add anyway",
                            )
                            else -> true
                        }
                        if (goAhead) {
                            openSharedDraft(
                                SharedTransactionImport.prefill(draft, shared, state, finance.repository, now, cards?.cards.orEmpty())
                                    .copy(fromPicture = fromPicture, readText = pictureText, picture = staged),
                            )
                        }
                    }
                }
            } finally {
                // A save filed its own copy; the draft goes either way.
                finance.pictures?.discard(staged)
            }
        }
    }

    private suspend fun openSharedDraft(prefill: SharedTransactionPrefill) {
        overlays.sheet<Boolean> { dismiss ->
            WithReminders { TransactionEditorSheet(prefill.type ?: TransactionType.Expense, existingId = null, onDone = dismiss, prefill = prefill) }
        }
    }

    /**
     * Pays a credit card's bill: a transfer from another account onto the card (see [PayBillSheet]), starting at [suggested] - the
     * unpaid part of the last statement - when the caller knows it, else at everything owed.
     */
    fun payBill(card: Account, suggested: Long? = null) {
        scope.launch { overlays.sheet<Boolean> { dismiss -> PayBillSheet(card, suggested, onDone = dismiss) } }
    }

    /**
     * Records one EMI of the loan [loan]: the sheet starts from what the schedule says is next, and from the account the last EMI was
     * paid from (read first, so the sheet opens already knowing it). Saving makes the two entries in one transaction ([RecordEmiSheet]).
     */
    fun recordEmi(loan: Account) {
        scope.launch {
            val last = try {
                finance.repository.lastLoanPayment(loan.id!!, state.asOf ?: settledCutoff(LocalDateTime.now()))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            overlays.sheet<Boolean> { dismiss -> RecordEmiSheet(loan, last, onDone = dismiss) }
        }
    }

    /**
     * Turns on recording the loan [loan]'s EMIs each month ([AutoEmiSheet]: which account pays them, and when the first is), starting
     * from the account the last EMI came from, as Record EMI does.
     */
    fun startLoanEmis(loan: Account) {
        scope.launch {
            val last = try {
                finance.repository.lastLoanPayment(loan.id!!, state.asOf ?: settledCutoff(LocalDateTime.now()))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val started = overlays.sheet<Boolean> { dismiss -> AutoEmiSheet(loan, last, onDone = dismiss) }
            if (started == true) snackbars.show("${loan.name}'s EMIs are recorded each month")
        }
    }

    /**
     * Turns recording the loan [loan]'s EMIs each month off: the repeating payment that did it is deleted - what it recorded stays - with
     * Undo for a few seconds. Its reminders go once the Undo is let go.
     */
    fun stopLoanEmis(loan: Account) {
        scope.launch {
            try {
                var stopped: RecurringRule? = null
                val undo = finance.undoable {
                    stopped = stopLoanEmis(loan.id!!)
                    emptyList()
                }
                haptics.selection()
                val ruleId = stopped?.id
                snackbars.offerUndo(
                    "${loan.name}'s EMIs are no longer recorded each month",
                    finance,
                    undo,
                    scope,
                    onSettled = { if (ruleId != null) reminders?.forget(ruleId) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                snackbars.show("Couldn't change that. Your other data is unchanged.", SnackbarKind.Error)
            }
        }
    }

    fun editTransaction(transaction: FinanceTransaction) {
        scope.launch {
            overlays.sheet<Boolean> { dismiss -> WithReminders { TransactionEditorSheet(transaction.type, existingId = transaction.id, onDone = dismiss) } }
        }
    }

    fun showDetails(transaction: FinanceTransaction) {
        scope.launch {
            // The tag whose chip was tapped, if one was: the sheet closes, then its transactions open (an object, not a primitive, held
            // by the sheet's lambda - see below).
            val tagToOpen = arrayOfNulls<Long>(1)
            // A part of a split payment shows the whole payment: every part, read before the sheet opens.
            val parts = splitPartsOf(transaction)
            when (
                overlays.sheet<DetailAction>(expandable = true) { dismiss ->
                    // Asked in the sheet, not before it: a Boolean held by the sheet's lambda trips the build's Compose stack
                    // trace mapping ("Failed to tokenize ... invokeSuspend").
                    TransactionDetailsSheet(
                        transaction,
                        canRemind = reminders != null,
                        onTag = { id ->
                            tagToOpen[0] = id
                            dismiss(null)
                        },
                        parts = parts,
                        onAction = dismiss,
                    )
                }
            ) {
                DetailAction.Edit -> editTransaction(transaction)
                DetailAction.EditSplit -> editSplitNow(parts)
                DetailAction.Unsplit -> unsplitNow(parts)
                DetailAction.Delete -> deleteTransactionNow(transaction)
                DetailAction.Remind -> reminders?.about(transaction)
                null -> tagToOpen[0]?.let { navigator.openTagHistory(it) }
            }
        }
    }

    /**
     * Every part of the split payment [transaction] is one part of (this one among them), in the order they were entered; empty for a
     * payment that is not split - or whose group has lost all but one of its parts, which is then just a payment (Check and recalculate
     * offers to end such a group).
     */
    private suspend fun splitPartsOf(transaction: FinanceTransaction): List<FinanceTransaction> {
        val group = transaction.splitGroup ?: return emptyList()
        return try {
            finance.repository.splitParts(group).takeIf { it.size >= 2 }.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** The split editor over a split payment's own parts ([parts]: all of them, as stored): what the parts share stays as it is. */
    private suspend fun editSplitNow(parts: List<FinanceTransaction>) {
        val first = parts.firstOrNull() ?: return
        val payment = first.copy(amount = parts.sumOf { it.amount }, categoryId = null, note = null)
        overlays.sheet<Boolean> { dismiss ->
            SplitEditorSheet(payment, first.splitGroup, parts.map { SplitPart(it.categoryId, it.amount, it.note, it.id) }, onDone = dismiss)
        }
    }

    /** Asks which category the whole payment goes under, then makes the split payment one payment again, with Undo. */
    private suspend fun unsplitNow(parts: List<FinanceTransaction>) {
        val group = parts.firstOrNull()?.splitGroup ?: return
        val category = overlays.sheet<Long> { dismiss -> UnsplitSheet(parts, onDone = dismiss) } ?: return
        try {
            val undo = finance.unsplitUndoably(group, category)
            haptics.success()
            snackbars.offerUndo("Payment unsplit", finance, undo, scope)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            snackbars.show("Couldn't unsplit it. Nothing was changed.", SnackbarKind.Error)
        }
    }

    fun deleteTransaction(transaction: FinanceTransaction) {
        scope.launch { deleteTransactionNow(transaction) }
    }

    /**
     * Deleted as a swipe asks, the way Gmail does it: at once, with no question first, because the swipe already took a deliberate
     * pull to its end and the Undo that follows puts the entry back whole. Everywhere else (the details sheet, TalkBack's
     * action) [deleteTransaction] still asks.
     */
    fun deleteTransactionSwiped(transaction: FinanceTransaction) {
        scope.launch { removeTransaction(transaction) }
    }

    private suspend fun deleteTransactionNow(transaction: FinanceTransaction) {
        // One part of a split payment is not deleted alone: the whole payment goes, and the question says so.
        val parts = splitPartsOf(transaction)
        val confirmed = overlays.confirm(
            title = if (parts.isNotEmpty()) "Delete this split payment?" else "Delete this ${transaction.type.label.lowercase()}?",
            message = if (parts.isNotEmpty()) {
                "The ${state.money.format(parts.sumOf { it.amount })} payment and all its ${parts.size} parts" +
                    "${if (transaction.picture != null) ", with its picture," else ""} will be removed, and balances, " +
                    "totals and budgets will update. You can undo it for a few seconds."
            } else {
                "${signedAmount(state.money, transaction.type, transaction.amount)}" +
                    "${if (transaction.picture != null) " and its picture" else ""} will be removed, and balances, " +
                    "totals and budgets will update. You can undo it for a few seconds."
            },
            confirmLabel = "Delete",
            destructive = true,
            icon = Icons.Outlined.DeleteOutline,
        )
        if (!confirmed) return
        removeTransaction(transaction)
    }

    private suspend fun removeTransaction(transaction: FinanceTransaction) {
        val split = splitPartsOf(transaction).isNotEmpty()
        // The repository deletes every part of a split payment with the one that is named; its picture waits for Undo with them.
        val undo = finance.deleteTransactionUndoably(transaction.id!!)
        haptics.success()
        snackbars.offerUndo(if (split) "Split payment deleted" else "${transaction.type.label} deleted", finance, undo, scope)
    }

    // --------------------------------------------------------------- recurring

    fun addRecurring() {
        scope.launch { overlays.sheet<Boolean> { dismiss -> WithReminders { RecurringEditorSheet(existingId = null, onDone = dismiss) } } }
    }

    fun editRecurring(id: Long) {
        scope.launch {
            overlays.sheet<Boolean> { dismiss ->
                WithReminders {
                    RecurringEditorSheet(existingId = id, onDone = dismiss, onRemind = reminders?.let { launcher -> { rule -> launcher.about(rule) } })
                }
            }
        }
    }

    /** A new repeating payment that starts from [suggestion] - a payee that looks like it repeats - with what the payments so far say filled in. */
    fun addSuggestedRecurring(suggestion: RepeatSuggestion) {
        scope.launch {
            overlays.sheet<Boolean> { dismiss -> WithReminders { RecurringEditorSheet(existingId = null, onDone = dismiss, suggestion = suggestion) } }
        }
    }

    /** "Not this one": [suggestion]'s payee is not offered as a repeating payment again. */
    fun dismissRepeat(suggestion: RepeatSuggestion) {
        scope.launch {
            try {
                finance.dismissRepeatPayee(suggestion.key)
                snackbars.show("Won't suggest ${suggestion.payee} again", SnackbarKind.Info)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                snackbars.show("Couldn't save that. Your other data is unchanged.", SnackbarKind.Error)
            }
        }
    }

    /**
     * Pauses [rule], or resumes it: the rule's own switch, as the editor flips it, and nothing is deleted - what was recorded stays, and
     * so does the rule. A paused payment isn't recorded and its reminder goes; a resumed one carries on from its next day after today
     * (what was missed while it was paused is skipped, not back-filled), and gets its reminder back.
     */
    fun setRecurringPaused(rule: RecurringRule, paused: Boolean) {
        scope.launch {
            try {
                val resumed = rule.copy(active = true)
                val changed = if (paused) rule.copy(active = false) else resumed.copy(nextDate = firstOccurrenceAfter(resumed, LocalDateTime.now()))
                val saved = finance.saveRecurringRule(changed)
                reminders?.follow(saved, rule)
                haptics.selection()
                val name = rule.payee ?: "The payment"
                snackbars.show(if (paused) "$name paused" else "$name resumed")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                snackbars.show("Couldn't change that. Your other data is unchanged.", SnackbarKind.Error)
            }
        }
    }

    // ------------------------------------------------------------------- goals

    /** The new-goal sheet (Plans > Goals). */
    fun addGoal() {
        scope.launch {
            val saved = overlays.sheet<SavingsGoal> { dismiss -> GoalFormSheet(existing = null, onDone = dismiss) }
            if (saved != null) snackbars.show("${saved.name} added")
        }
    }

    /**
     * A goal opened: its sheet, then what was chosen in it - Add money or Take out (a goal on its own; no entry is made in the ledger),
     * Edit, or Delete, which asks first and can be undone for a few seconds. A goal only counts and moves no money, so deleting one
     * changes no account.
     */
    fun openGoal(goal: SavingsGoal) {
        scope.launch {
            val id = goal.id ?: return@launch
            val action = overlays.sheet<String>(expandable = true) { dismiss -> GoalSheet(goal, onPick = dismiss) } ?: return@launch
            val current = state.goal(id) ?: return@launch
            when (action) {
                "add", "take" -> {
                    val result = overlays.sheet<GoalMoneyResult> { dismiss -> GoalMoneySheet(current, adding = action == "add", onDone = dismiss) }
                    if (result != null) {
                        val amount = state.money.format(result.amount, trimZeroFraction = true)
                        snackbars.show(if (result.adding) "Added $amount to ${result.goal.name}" else "Took $amount out of ${result.goal.name}")
                    }
                }
                "edit" -> {
                    val edited = overlays.sheet<SavingsGoal> { dismiss -> GoalFormSheet(existing = current, onDone = dismiss) }
                    if (edited != null) snackbars.show("${edited.name} updated")
                }
                "delete" -> {
                    val counted = goalSaved(current, state.balances)
                    val confirmed = overlays.confirm(
                        title = "Delete ${current.name}?",
                        message = if (current.isLinked || counted == 0L) {
                            "The goal is removed. No account or balance changes. You can undo it for a few seconds."
                        } else {
                            "The goal and the ${state.money.format(counted, trimZeroFraction = true)} counted on it are removed. " +
                                "That money isn't in any account, so nothing moves. You can undo it for a few seconds."
                        },
                        confirmLabel = "Delete",
                        destructive = true,
                        icon = Icons.Outlined.DeleteOutline,
                    )
                    if (!confirmed) return@launch
                    try {
                        val undo = finance.undoable {
                            deleteGoal(id)
                            emptyList()
                        }
                        haptics.success()
                        snackbars.offerUndo("${current.name} deleted", finance, undo, scope)
                    } catch (e: FinanceConflictException) {
                        overlays.alert("Can't delete", e.message)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- accounts

    fun addAccount() {
        scope.launch {
            val saved = overlays.sheet<Account> { dismiss -> AccountFormSheet(existing = null, onDone = dismiss) }
            if (saved != null) snackbars.show("${saved.name} added")
        }
    }

    /** Account actions sheet: edit, make default, archive/restore, delete. */
    fun manageAccount(account: Account) {
        scope.launch {
            val isDefault = state.defaultAccountId == account.id
            val owed = (-state.balanceOf(account.id!!)).coerceAtLeast(0)
            val actions = buildList {
                // A loan is for paying off: its EMI comes first.
                if (account.isLoan && !account.archived) add(SheetAction("emi", Icons.Outlined.Payments, "Record EMI"))
                // A credit card is for spending on and paying off: those two come first.
                if (account.kind == AccountKind.Card && !account.archived) {
                    add(SheetAction("pay", Icons.Outlined.Payments, if (owed > 0) "Pay bill · ${state.money.format(owed)} owed" else "Pay bill"))
                    add(SheetAction("spend", Icons.Outlined.ShoppingCart, "Add expense on this card"))
                }
                add(SheetAction("edit", Icons.Outlined.Edit, "Edit"))
                // A card's bill, a balance to check: an account can be what a reminder is about.
                if (reminders != null && !account.archived) add(SheetAction("remind", Icons.Outlined.NotificationAdd, "Remind me"))
                if (!account.archived && !isDefault) add(SheetAction("default", Icons.Outlined.StarOutline, "Make default"))
                add(
                    if (account.archived) {
                        SheetAction("restore", Icons.Outlined.Unarchive, "Restore")
                    } else {
                        SheetAction("archive", Icons.Outlined.Archive, "Archive")
                    },
                )
                add(SheetAction("delete", Icons.Outlined.DeleteOutline, "Delete"))
            }
            val action = overlays.sheet<String> { dismiss ->
                ActionListSheet(
                    title = account.name,
                    subtitle = listOfNotNull(
                        state.money.format(state.balanceOf(account.id)),
                        billingCycleSummary(account.statementDay, account.dueDay),
                    ).joinToString(" · "),
                    actions = actions,
                    onPick = dismiss,
                )
            } ?: return@launch
            when (action) {
                "pay" -> payBill(account)
                "emi" -> recordEmi(account)
                "spend" -> addTransaction(TransactionType.Expense, accountId = account.id)
                "edit" -> {
                    val edited = overlays.sheet<Account> { dismiss -> AccountFormSheet(existing = account, onDone = dismiss) }
                    if (edited != null) snackbars.show("${edited.name} updated")
                }
                "remind" -> reminders?.about(account)
                "default" -> {
                    finance.setDefaultAccountId(account.id)
                    snackbars.show("${account.name} is now the default")
                }
                "archive", "restore" -> {
                    val archive = action == "archive"
                    val undo = finance.undoable {
                        setAccountArchived(account.id, archive)
                        emptyList()
                    }
                    snackbars.offerUndo(
                        if (archive) "${account.name} archived. Its history is kept." else "${account.name} restored",
                        finance,
                        undo,
                        scope,
                    )
                }
                "delete" -> {
                    // A goal that counts this account's balance would have nothing to count: say what becomes of it, where it is decided.
                    val counting = state.goals.count { it.accountId == account.id }
                    val confirmed = overlays.confirm(
                        title = "Delete ${account.name}?",
                        message = "Only accounts without any transactions can be deleted. " +
                            "Archive it instead to hide it and keep its history." +
                            if (counting == 0) {
                                ""
                            } else {
                                " ${if (counting == 1) "A savings goal counts" else "$counting savings goals count"} this account's balance: " +
                                    "${if (counting == 1) "it stays" else "they stay"}, on ${if (counting == 1) "its" else "their"} own, with nothing saved."
                            },
                        confirmLabel = "Delete",
                        destructive = true,
                    )
                    if (!confirmed) return@launch
                    try {
                        val undo = finance.undoable {
                            deleteAccount(account.id)
                            emptyList()
                        }
                        snackbars.offerUndo("${account.name} deleted", finance, undo, scope)
                    } catch (e: FinanceConflictException) {
                        overlays.alert("Can't delete", e.message)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ people

    /** The new-person sheet; [onAdded] gets the saved person. */
    fun addPerson(initialName: String = "", onAdded: (Person) -> Unit = {}) {
        scope.launch {
            val saved = overlays.sheet<Person> { dismiss -> PersonFormSheet(existing = null, onDone = dismiss, initialName = initialName) }
            if (saved != null) {
                snackbars.show("${saved.name} added")
                onAdded(saved)
            }
        }
    }

    /**
     * A person's actions sheet: add an expense for them, open their expenses ([showOpen], from the list), edit, archive or
     * restore, delete. Deleting asks first and says what happens to their expenses (they stay, linked to no one); archiving
     * is the way to hide someone and keep what was spent on them attached. [onDeleted] runs once they are gone.
     */
    fun managePerson(person: Person, onDeleted: () -> Unit = {}, showOpen: Boolean = true) {
        scope.launch {
            val id = person.id ?: return@launch
            val spent = runCatching { finance.repository.peopleSpending(null).of(id) }.getOrNull()
            val subtitle = if (spent == null || spent.count == 0) {
                "No expenses yet"
            } else {
                "${spent.count} ${if (spent.count == 1) "expense" else "expenses"} · ${state.money.format(spent.amount)} in all"
            }
            val actions = buildList {
                if (!person.archived) add(SheetAction("add", Icons.Outlined.Add, "Add expense for ${person.name}"))
                if (showOpen) add(SheetAction("open", Icons.AutoMirrored.Outlined.ReceiptLong, "See their expenses"))
                add(SheetAction("edit", Icons.Outlined.Edit, "Edit"))
                add(
                    if (person.archived) {
                        SheetAction("restore", Icons.Outlined.Unarchive, "Restore")
                    } else {
                        SheetAction("archive", Icons.Outlined.Archive, "Archive")
                    },
                )
                add(SheetAction("delete", Icons.Outlined.DeleteOutline, "Delete"))
            }
            val action = overlays.sheet<String> { dismiss ->
                ActionListSheet(title = person.name, subtitle = subtitle, actions = actions, onPick = dismiss)
            } ?: return@launch
            when (action) {
                "add" -> addTransaction(TransactionType.Expense, personId = id)
                "open" -> navigator.openPerson(id)
                "edit" -> overlays.sheet<Person> { dismiss -> PersonFormSheet(existing = person, onDone = dismiss) }
                "archive", "restore" -> {
                    val archive = action == "archive"
                    finance.setPersonArchived(id, archive)
                    snackbars.show(if (archive) "${person.name} archived. Their expenses keep them." else "${person.name} restored")
                }
                "delete" -> {
                    val count = spent?.count ?: 0
                    val confirmed = overlays.confirm(
                        title = "Delete ${person.name}?",
                        message = if (count == 0) {
                            "${person.name} has no expenses."
                        } else {
                            "${person.name}'s $count ${if (count == 1) "expense" else "expenses"} (${state.money.format(spent!!.amount)}) " +
                                "stay in your history and in every balance, total and budget - they just won't be for anyone " +
                                "any more. To keep them attached and only hide ${person.name}, archive instead."
                        },
                        confirmLabel = "Delete",
                        destructive = true,
                        icon = Icons.Outlined.DeleteOutline,
                    )
                    if (!confirmed) return@launch
                    var kept = 0
                    val undo = finance.undoable {
                        kept = deletePerson(id)
                        emptyList()
                    }
                    haptics.success()
                    snackbars.offerUndo(
                        if (kept == 0) "${person.name} deleted" else "${person.name} deleted. $kept ${if (kept == 1) "expense" else "expenses"} kept.",
                        finance,
                        undo,
                        scope,
                    )
                    onDeleted()
                }
            }
        }
    }

    // -------------------------------------------------------------- categories

    fun addCategory(type: CategoryType) {
        scope.launch {
            val saved = overlays.sheet<Category> { dismiss -> CategoryFormSheet(type = type, existing = null, onDone = dismiss) }
            if (saved != null) snackbars.show("${saved.name} added")
        }
    }

    /** A new category under [parent], which can be given any other parent (or none) in the form. */
    fun addSubCategory(parent: Category) {
        scope.launch {
            val saved = overlays.sheet<Category> { dismiss ->
                CategoryFormSheet(type = parent.type, existing = null, parent = parent, onDone = dismiss)
            }
            if (saved != null) snackbars.show("${saved.name} added under ${parent.name}")
        }
    }

    /** Adds the ready-made sub-categories that this ledger doesn't have yet, and says how many that was. */
    fun addSuggestedSubCategories() {
        scope.launch {
            val added = finance.addSuggestedSubCategories()
            haptics.success()
            if (added == 0) {
                snackbars.show("You already have all the suggested sub-categories", SnackbarKind.Info)
            } else {
                snackbars.show("Added $added sub-categor${if (added == 1) "y" else "ies"}")
            }
        }
    }

    fun manageCategory(category: Category) {
        scope.launch {
            val actions = buildList {
                add(SheetAction("edit", Icons.Outlined.Edit, "Edit"))
                // Two levels at most: only a top-level category, and not a hidden one, takes sub-categories.
                if (category.parentId == null && !category.archived) {
                    add(SheetAction("sub", Icons.Outlined.Add, "Add sub-category"))
                }
                add(
                    if (category.archived) {
                        SheetAction("restore", Icons.Outlined.Unarchive, "Restore")
                    } else {
                        SheetAction("archive", Icons.Outlined.Archive, "Archive")
                    },
                )
                add(SheetAction("delete", Icons.Outlined.DeleteOutline, "Delete"))
            }
            val action = overlays.sheet<String> { dismiss ->
                ActionListSheet(title = category.name, subtitle = null, actions = actions, onPick = dismiss)
            } ?: return@launch
            when (action) {
                "edit" -> overlays.sheet<Category> { dismiss ->
                    CategoryFormSheet(type = category.type, existing = category, onDone = dismiss)
                }
                "sub" -> addSubCategory(category)
                "archive", "restore" -> {
                    val archive = action == "archive"
                    try {
                        val undo = finance.undoable {
                            setCategoryArchived(category.id!!, archive)
                            emptyList()
                        }
                        snackbars.offerUndo(
                            if (archive) "${category.name} archived. Past transactions keep it." else "${category.name} restored",
                            finance,
                            undo,
                            scope,
                        )
                    } catch (e: FinanceConflictException) {
                        overlays.alert("Can't restore", e.message)
                    }
                }
                "delete" -> {
                    val confirmed = overlays.confirm(
                        title = "Delete ${category.name}?",
                        message = "Only categories without transactions can be deleted. " +
                            "Any budget for this category is removed with it.",
                        confirmLabel = "Delete",
                        destructive = true,
                    )
                    if (!confirmed) return@launch
                    try {
                        val undo = finance.undoable {
                            deleteCategory(category.id!!)
                            emptyList()
                        }
                        snackbars.offerUndo("${category.name} deleted", finance, undo, scope)
                    } catch (e: FinanceConflictException) {
                        overlays.alert("Can't delete", e.message)
                    }
                }
            }
        }
    }
}

/** The Wallet's cards, for the account form's card choice and for putting a shared receipt on the right account; null where there is no Wallet. */
val LocalWalletCards = staticCompositionLocalOf<CardsController?> { null }

@Composable
fun rememberFinanceActions(): FinanceActions {
    val finance = LocalFinance.current
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val navigator = LocalNavigator.current
    val haptics = rememberAppHaptics()
    val scope = LocalAppScope.current
    val reminders = LocalReminderLauncher.current
    val cards = LocalWalletCards.current
    return remember(finance, overlays, snackbars, navigator, haptics, scope, reminders, cards) {
        FinanceActions(finance, overlays, snackbars, navigator, haptics, scope, reminders, cards)
    }
}

class SheetAction(val key: String, val icon: ImageVector, val label: String)

/** Title + a short list of actions, returned by key. */
@Composable
fun ActionListSheet(title: String, subtitle: String?, actions: List<SheetAction>, onPick: (String) -> Unit) {
    Column {
        Column(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.sm)) {
            SheetHeader(title = title, subtitle = subtitle)
        }
        for (action in actions) {
            ListItem(
                modifier = Modifier.clickable { onPick(action.key) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(action.icon, contentDescription = null) },
                headlineContent = { Text(action.label) },
            )
        }
        Spacer(Modifier.height(AppSpacing.sm))
    }
}

/** What reading a shared picture came to, null when no text could be read: the loading sheet gives back null only when cancelled. */
private class PictureRead(val text: PictureText?)


/**
 * The snackbar for a change to the ledger that can be taken back for a few seconds ([FinanceController.undoable]): Undo puts
 * it back, and the snackbar going without it settles the change. If something else changed meanwhile, undoing would take
 * that away too, so it says so instead.
 */
internal fun AppSnackbars.offerUndo(
    message: String,
    finance: FinanceController,
    undo: FinanceUndo,
    scope: CoroutineScope,
    /** Called when the change is final - the snackbar went without an Undo, or the Undo could not be done. */
    onSettled: (() -> Unit)? = null,
) {
    showUndoable(
        message,
        undo = {
            scope.launch {
                val back = try {
                    finance.undo(undo)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
                if (back) {
                    show("Undone")
                } else {
                    finance.settle(undo)
                    onSettled?.invoke()
                    show("Couldn't undo, because something else changed since.", SnackbarKind.Warning)
                }
            }
        },
        settle = {
            scope.launch { finance.settle(undo) }
            onSettled?.invoke()
        },
    )
}
