package com.example.normpix.finance.ui

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
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.SharedStatus
import com.example.normpix.finance.share.SharedTextReading
import com.example.normpix.finance.share.SharedTransactionImport
import com.example.normpix.finance.share.SharedTransactionParser
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
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDateTime
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
) {
    private val state get() = finance.state.value

    /** The new-transaction sheet; [accountId], when given, is the account it starts on (a card's "Add expense"). */
    fun addTransaction(type: TransactionType = TransactionType.Expense, accountId: Long? = null, personId: Long? = null) {
        scope.launch {
            overlays.sheet<Boolean> { dismiss ->
                TransactionEditorSheet(type, existingId = null, onDone = dismiss, initialAccountId = accountId, initialPersonId = personId)
            }
        }
    }

    /**
     * A transaction from what another app shared - a payment confirmation, a bank SMS, or a receipt picture whose text
     * [readPicture] reads on this device: read into a draft, then the new-transaction sheet opens with what could be read
     * and says what couldn't. Nothing is saved until the user saves. A share with nothing to read says so; a payment the
     * message says failed, or a request, asks first.
     */
    fun addFromShare(text: String, readPicture: (suspend () -> String?)? = null) {
        scope.launch {
            val fromPicture = readPicture != null
            val pictureText = readPicture?.let {
                snackbars.show("Reading the picture…")
                it()
            }
            if (fromPicture && pictureText.isNullOrBlank() && text.isBlank()) {
                val enter = overlays.confirm(
                    title = "Couldn't read the picture",
                    message = "NormPix found no text in it that it could read. You can still enter the transaction yourself.",
                    confirmLabel = "Enter it myself",
                )
                if (enter) openSharedDraft(SharedTransactionPrefill.nothingRead(fromPicture = true))
                return@launch
            }
            // Words that came with a picture are read together with the picture's own.
            val shared = listOf(text, pictureText.orEmpty()).filter { it.isNotBlank() }.joinToString("\n\n")
            val now = LocalDateTime.now()
            when (val reading = SharedTransactionParser.read(shared, state.currency, now.toLocalDate())) {
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
                    if (enter) openSharedDraft(SharedTransactionPrefill.nothingRead(fromPicture))
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
                        openSharedDraft(SharedTransactionImport.prefill(draft, shared, state, finance.repository, now).copy(fromPicture = fromPicture))
                    }
                }
            }
        }
    }

    private suspend fun openSharedDraft(prefill: SharedTransactionPrefill) {
        overlays.sheet<Boolean> { dismiss ->
            TransactionEditorSheet(prefill.type ?: TransactionType.Expense, existingId = null, onDone = dismiss, prefill = prefill)
        }
    }

    /** Pays a credit card's bill: a transfer from another account onto the card (see [PayBillSheet]). */
    fun payBill(card: Account) {
        scope.launch { overlays.sheet<Boolean> { dismiss -> PayBillSheet(card, onDone = dismiss) } }
    }

    fun editTransaction(transaction: FinanceTransaction) {
        scope.launch {
            overlays.sheet<Boolean> { dismiss -> TransactionEditorSheet(transaction.type, existingId = transaction.id, onDone = dismiss) }
        }
    }

    fun showDetails(transaction: FinanceTransaction) {
        scope.launch {
            val canRemind = reminders != null
            when (
                overlays.sheet<DetailAction>(expandable = true) { dismiss ->
                    TransactionDetailsSheet(transaction, canRemind = canRemind, onAction = dismiss)
                }
            ) {
                DetailAction.Edit -> editTransaction(transaction)
                DetailAction.Delete -> deleteTransactionNow(transaction)
                DetailAction.Remind -> reminders?.about(transaction)
                null -> Unit
            }
        }
    }

    fun deleteTransaction(transaction: FinanceTransaction) {
        scope.launch { deleteTransactionNow(transaction) }
    }

    private suspend fun deleteTransactionNow(transaction: FinanceTransaction) {
        val confirmed = overlays.confirm(
            title = "Delete this ${transaction.type.label.lowercase()}?",
            message = "${signedAmount(state.money, transaction.type, transaction.amount)} will be removed, and balances, " +
                "totals and budgets will update. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            icon = Icons.Outlined.DeleteOutline,
        )
        if (!confirmed) return
        finance.deleteTransaction(transaction.id!!)
        haptics.success()
        snackbars.show("Transaction deleted")
    }

    // --------------------------------------------------------------- recurring

    fun addRecurring() {
        scope.launch { overlays.sheet<Boolean> { dismiss -> RecurringEditorSheet(existingId = null, onDone = dismiss) } }
    }

    fun editRecurring(id: Long) {
        scope.launch {
            overlays.sheet<Boolean> { dismiss ->
                RecurringEditorSheet(existingId = id, onDone = dismiss, onRemind = reminders?.let { launcher -> { rule -> launcher.about(rule) } })
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
                    subtitle = state.money.format(state.balanceOf(account.id!!)),
                    actions = actions,
                    onPick = dismiss,
                )
            } ?: return@launch
            when (action) {
                "pay" -> payBill(account)
                "spend" -> addTransaction(TransactionType.Expense, accountId = account.id)
                "edit" -> overlays.sheet<Account> { dismiss -> AccountFormSheet(existing = account, onDone = dismiss) }
                "remind" -> reminders?.about(account)
                "default" -> {
                    finance.setDefaultAccountId(account.id)
                    snackbars.show("${account.name} is now the default")
                }
                "archive", "restore" -> {
                    val archive = action == "archive"
                    finance.setAccountArchived(account.id!!, archive)
                    snackbars.show(if (archive) "${account.name} archived. Its history is kept." else "${account.name} restored")
                }
                "delete" -> {
                    val confirmed = overlays.confirm(
                        title = "Delete ${account.name}?",
                        message = "Only accounts without any transactions can be deleted. " +
                            "Archive it instead to hide it and keep its history.",
                        confirmLabel = "Delete",
                        destructive = true,
                    )
                    if (!confirmed) return@launch
                    try {
                        finance.deleteAccount(account.id!!)
                        snackbars.show("Account deleted")
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
                if (showOpen) add(SheetAction("open", Icons.Outlined.ReceiptLong, "See their expenses"))
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
                            "${person.name} has no expenses. This can't be undone."
                        } else {
                            "${person.name}'s $count ${if (count == 1) "expense" else "expenses"} (${state.money.format(spent!!.amount)}) " +
                                "stay in your history and in every balance, total and budget - they just won't be for anyone " +
                                "any more. To keep them attached and only hide ${person.name}, archive instead. This can't be undone."
                        },
                        confirmLabel = "Delete",
                        destructive = true,
                        icon = Icons.Outlined.DeleteOutline,
                    )
                    if (!confirmed) return@launch
                    val kept = finance.deletePerson(id)
                    haptics.success()
                    snackbars.show(
                        if (kept == 0) "${person.name} deleted" else "${person.name} deleted. $kept ${if (kept == 1) "expense" else "expenses"} kept.",
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
            snackbars.show(
                if (added == 0) "You already have all the suggested sub-categories" else "Added $added sub-categor${if (added == 1) "y" else "ies"}",
            )
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
                        finance.setCategoryArchived(category.id!!, archive)
                        snackbars.show(if (archive) "${category.name} archived. Past transactions keep it." else "${category.name} restored")
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
                        finance.deleteCategory(category.id!!)
                        snackbars.show("Category deleted")
                    } catch (e: FinanceConflictException) {
                        overlays.alert("Can't delete", e.message)
                    }
                }
            }
        }
    }
}

@Composable
fun rememberFinanceActions(): FinanceActions {
    val finance = LocalFinance.current
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val navigator = LocalNavigator.current
    val haptics = rememberAppHaptics()
    val scope = LocalAppScope.current
    val reminders = LocalReminderLauncher.current
    return remember(finance, overlays, snackbars, navigator, haptics, scope, reminders) {
        FinanceActions(finance, overlays, snackbars, navigator, haptics, scope, reminders)
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
