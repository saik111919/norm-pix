package com.example.normpix.finance.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.ui.components.DialogBackdropBlur
import com.example.normpix.ui.components.SeeThroughColors
import com.example.normpix.ui.components.rememberDialogSurface

import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.RepeatSchedule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.creditRoom
import com.example.normpix.finance.domain.minorToInputText
import com.example.normpix.finance.domain.parseMinorUnits
import com.example.normpix.finance.share.PaymentAppReceipts
import com.example.normpix.finance.share.ShareNotice
import com.example.normpix.finance.share.SharedDraftNotice
import com.example.normpix.finance.share.SharedTextReading
import com.example.normpix.finance.share.SharedTransactionImport
import com.example.normpix.finance.share.SharedTransactionPrefill
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.ui.components.politeLiveRegion
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The add / edit transaction editor, shown as a bottom sheet (see `AppOverlays.sheet`): pull the
 * handle down, tap the scrim or press back to leave - with a question first when there are unsaved
 * changes. Calls [onDone] with true once saved, or null when closed without saving.
 *
 * Ordered by what matters for fast entry - type, amount, category, account, date - with payee and
 * note tucked behind a disclosure until wanted. When [existingId] is set, the transaction is loaded
 * first and edited in place (same id and creation time).
 */
@Composable
fun TransactionEditorSheet(
    initialType: TransactionType,
    existingId: Long?,
    onDone: (Boolean?) -> Unit,
    /** The account a new transaction starts on, instead of the default one (a card's "Add expense"). */
    initialAccountId: Long? = null,
    /** The person a new expense starts on (a person's "Add expense"). */
    initialPersonId: Long? = null,
    /** What a new transaction starts with when it comes from text shared by another app (see `SharedTransactionImport`). */
    prefill: SharedTransactionPrefill? = null,
) {
    if (existingId == null) {
        TransactionEditor(
            initialType = initialType,
            existing = null,
            onDone = onDone,
            initialAccountId = initialAccountId,
            initialPersonId = initialPersonId,
            prefill = prefill,
        )
        return
    }
    val controller = LocalFinance.current
    val loaded by produceState<AsyncValue<FinanceTransaction?>>(AsyncValue.Loading, existingId) {
        value = try {
            AsyncValue.Data(controller.repository.transactionById(existingId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AsyncValue.Failure(e)
        }
    }
    when (val state = loaded) {
        is AsyncValue.Data -> {
            val existing = state.value
            if (existing == null) {
                LaunchedEffect(Unit) { onDone(null) }
            } else {
                TransactionEditor(initialType = existing.type, existing = existing, onDone = onDone)
            }
        }
        else -> AppLoadingState(Modifier.fillMaxWidth().height(280.dp))
    }
}

@Composable
private fun TransactionEditor(
    initialType: TransactionType,
    existing: FinanceTransaction?,
    onDone: (Boolean?) -> Unit,
    initialAccountId: Long? = null,
    initialPersonId: Long? = null,
    prefill: SharedTransactionPrefill? = null,
) {
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val isEditing = existing != null

    var type by remember { mutableStateOf(prefill?.type ?: initialType) }
    // A shared message that doesn't say which way the money went leaves the type unchosen: the user picks it.
    var typeChosen by remember { mutableStateOf(prefill == null || prefill.type != null) }
    var amount by remember {
        mutableStateOf(
            (existing?.amount ?: prefill?.amount)?.let { minorToInputText(it, finance.currency.decimals) }.orEmpty(),
        )
    }
    var payee by remember { mutableStateOf((existing?.payee ?: prefill?.payee).orEmpty()) }
    var note by remember { mutableStateOf((existing?.note ?: prefill?.note).orEmpty()) }
    var categoryId by remember {
        mutableStateOf(existing?.categoryId ?: prefill?.categoryId?.takeIf { id -> finance.category(id)?.archived == false })
    }
    // Who an expense was spent on; an archived person stays on an expense that already has them, but is not offered to a new one.
    var personId by remember {
        mutableStateOf(
            existing?.personId
                ?: initialPersonId?.takeIf { id -> initialType == TransactionType.Expense && finance.activePeople.any { it.id == id } },
        )
    }
    // A shared receipt that shows an account's number, which none of the user's accounts is known by, starts with no account
    // chosen: the default one would be a guess about which account the money moved through.
    val startAccountId = existing?.accountId
        ?: (prefill?.accountId ?: initialAccountId)?.takeIf { id -> finance.activeAccounts.any { it.id == id } }
        ?: finance.defaultAccount?.id?.takeIf { prefill?.accountNeedsChoice != true }
    var accountId by remember { mutableStateOf(startAccountId) }
    val walletCards = LocalWalletCards.current
    var toAccountId by remember {
        mutableStateOf(
            existing?.toAccountId
                ?: if (initialType == TransactionType.Transfer) {
                    finance.activeAccounts.firstOrNull { it.id != startAccountId }?.id
                } else {
                    null
                },
        )
    }
    var occurredAt by remember {
        mutableStateOf(existing?.occurredAt ?: prefill?.occurredAt ?: LocalDateTime.now().withSecond(0).withNano(0))
    }
    // A shared message's date that couldn't be read is never quietly replaced by today: it has to be picked.
    var dateUnset by remember { mutableStateOf(prefill?.dateNeedsChoice == true) }
    // How it repeats, if it does: saving then records it as the first of a repeating payment. Not offered to one that already belongs to one.
    var repeat by remember { mutableStateOf<RepeatSchedule?>(null) }
    var showDetails by remember {
        mutableStateOf(existing?.payee != null || existing?.note != null || prefill?.payee != null || prefill?.note != null)
    }
    var saving by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    // The picture: the one saved on the transaction (until it is removed or replaced), and a new one not saved yet.
    val pictureStore = controller.pictures
    val pictureReader = LocalAppContainer.current.sharedPictures
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    var keptPicture by remember { mutableStateOf(existing?.picture) }
    var draftPicture by remember { mutableStateOf(prefill?.picture) }
    val keptFile = remember(keptPicture) { pictureStore?.file(keptPicture) }
    val shownPicture = draftPicture ?: keptFile
    var viewingPicture by remember { mutableStateOf(false) }
    // A payment read from an attached picture is looked for among those already recorded, as a shared one is.
    var readFromPicture by remember { mutableStateOf(false) }
    var pictureReference by remember { mutableStateOf<String?>(null) }
    // What reading a picture attached here came to, said at the top of the form (kept while it hides, to ease it out),
    // and what went wrong getting one, said under the picture field: a snackbar would sit under this sheet.
    var pictureNote by remember { mutableStateOf<PictureNote?>(null) }
    var pictureNoteShown by remember { mutableStateOf(false) }
    var picturesRead by remember { mutableIntStateOf(0) }
    var pictureProblem by remember { mutableStateOf<String?>(null) }
    // What the user set themselves, which a picture read later never changes.
    var typeTouched by remember { mutableStateOf(false) }
    var dateTouched by remember { mutableStateOf(false) }
    var accountTouched by remember { mutableStateOf(false) }
    // A draft the form didn't save goes with it (a save files its own copy).
    DisposableEffect(Unit) { onDispose { pictureStore?.discard(draftPicture) } }

    var amountError by remember { mutableStateOf<String?>(null) }
    var typeError by remember { mutableStateOf<String?>(null) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var accountError by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    val isTransfer = type == TransactionType.Transfer
    val role = type.role()

    fun snapshot() = listOf(
        type, amount, payee, note, categoryId, accountId, toAccountId, occurredAt, personId, repeat, keptPicture, draftPicture?.path,
    ).joinToString("|")
    val initialSnapshot = remember { snapshot() }
    // A draft filled in from shared text is the user's to keep or throw away, so leaving it asks first, edited or not.
    val dirty = snapshot() != initialSnapshot || prefill != null

    DiscardChangesGuard(dirty = dirty, saving = saving, isEditing = isEditing, keptMessage = "The transaction stays as it was.")

    fun pickDefaultDestination() {
        toAccountId = finance.activeAccounts.firstOrNull { it.id != accountId }?.id
    }

    fun setType(next: TransactionType) {
        type = next
        categoryError = null
        accountError = null
        // Only an expense is spent on someone.
        if (next != TransactionType.Expense) personId = null
        if (next == TransactionType.Transfer) {
            categoryId = null
            if (toAccountId == null || toAccountId == accountId) pickDefaultDestination()
        } else {
            toAccountId = null
            if (finance.category(categoryId)?.type != CategoryType.forTransaction(next)) categoryId = null
        }
    }

    fun newCategory() {
        scope.launch {
            val created = overlays.sheet<Category> { dismiss ->
                CategoryFormSheet(type = CategoryType.forTransaction(type), existing = null, onDone = dismiss)
            }
            if (created != null) {
                categoryId = created.id
                categoryError = null
            }
        }
    }

    fun newPerson() {
        scope.launch {
            val created = overlays.sheet<Person> { dismiss -> PersonFormSheet(existing = null, onDone = dismiss) }
            if (created != null) personId = created.id
        }
    }

    /**
     * False when the user would rather not save what looks like a payment already recorded. Only asked for a new one read
     * from a share or a picture: those are the ones that may well have been entered before.
     */
    suspend fun notADuplicate(draft: FinanceTransaction): Boolean {
        if (isEditing || (prefill == null && !readFromPicture)) return true
        val reference = prefill?.reference ?: pictureReference
        val match = try {
            controller.repository.possibleDuplicates(draft.type, draft.amount, draft.occurredAt, draft.payee, reference, limit = 1).firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return true
        return overlays.confirm(
            title = "Already recorded?",
            message = "${SharedTransactionImport.describe(match, finance.money, LocalDateTime.now(), is24Hour).replaceFirstChar { it.uppercase() }} " +
                "looks like the same payment. Save this one as well?",
            confirmLabel = "Save anyway",
            cancelLabel = "Go back",
        )
    }

    fun save() {
        if (saving) return
        val parsed = parseMinorUnits(amount, finance.currency.decimals)
        formError = null
        amountError = if (parsed == null || parsed <= 0) "Enter an amount greater than zero" else null
        typeError = if (!typeChosen) "Choose expense or income" else null
        categoryError = if (typeChosen && !isTransfer && categoryId == null) "Choose a category" else null
        accountError = when {
            accountId == null -> "Choose an account"
            isTransfer && toAccountId == null -> "Choose where the money goes"
            isTransfer && toAccountId == accountId -> "Choose two different accounts"
            else -> null
        }
        dateError = if (dateUnset) "Choose the date" else null
        if (amountError != null || typeError != null || categoryError != null || accountError != null || dateError != null || parsed == null) return

        saving = true
        val now = LocalDateTime.now()
        val draft = FinanceTransaction(
            id = existing?.id,
            type = type,
            amount = parsed,
            accountId = accountId!!,
            toAccountId = if (isTransfer) toAccountId else null,
            categoryId = if (isTransfer) null else categoryId,
            occurredAt = occurredAt,
            payee = if (isTransfer) null else payee,
            note = note,
            recurringId = existing?.recurringId,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            personId = if (type == TransactionType.Expense) personId else null,
            picture = existing?.picture,
        )
        val pictureEdit = draftPicture?.let { PictureEdit.Attach(it) }
            ?: if (keptPicture != existing?.picture) PictureEdit.Remove else PictureEdit.Unchanged
        scope.launch {
            try {
                if (!notADuplicate(draft)) return@launch
                val schedule = repeat?.takeIf { existing?.recurringId == null }
                val saved = if (schedule != null) {
                    controller.saveTransactionAndRepeat(draft, schedule, pictureEdit)
                } else {
                    controller.saveTransaction(draft, pictureEdit)
                }
                haptics.success()
                val repeating = schedule?.let { " · ${describeRepeat(it).replaceFirstChar { c -> c.lowercase() }}" }.orEmpty()
                snackbars.show(
                    if (isEditing) "Changes saved$repeating" else "${saved.type.label} of ${finance.money.format(saved.amount)} saved$repeating",
                )
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

    /** Says at the top of the form what reading a picture came to, in place of what the last picture said, if anything. */
    fun sayAboutPicture(note: PictureNote) {
        pictureNote = note
        pictureNoteShown = true
        picturesRead++
    }

    /**
     * Fills in from what a picture says ([found], read from [readText]) whatever the form doesn't have yet, and says what
     * it filled in.
     */
    fun fillFromPicture(found: SharedTransactionPrefill, readText: String) {
        val fill = pictureFill(
            found,
            FormGaps(
                type = type,
                typeOpen = !typeChosen ||
                    (!isEditing && !typeTouched && amount.isBlank() && type != TransactionType.Transfer),
                amountEmpty = amount.isBlank(),
                payeeEmpty = payee.isBlank(),
                noteEmpty = note.isBlank(),
                categoryEmpty = categoryId == null,
                dateOpen = dateUnset || (!isEditing && !dateTouched && prefill?.occurredAt == null),
                accountOpen = !isEditing && !accountTouched && initialAccountId == null && prefill?.accountId == null,
            ),
        )
        fill.type?.let {
            typeChosen = true
            typeError = null
            setType(it)
        }
        fill.amount?.let {
            amount = minorToInputText(it, finance.currency.decimals)
            amountError = null
        }
        fill.payee?.let {
            payee = it
            showDetails = true
        }
        fill.note?.let {
            note = it
            showDetails = true
        }
        fill.categoryId?.let {
            categoryId = it
            categoryError = null
        }
        fill.occurredAt?.let {
            occurredAt = it
            dateUnset = false
            dateError = null
        }
        fill.accountId?.let {
            accountId = it
            accountError = null
        }
        // A number nothing matches: the account the form started with is a guess; it is the user's to choose.
        if (fill.chooseAccount) {
            accountId = null
            accountError = null
        }
        readFromPicture = true
        pictureReference = found.reference
        val summary = fill.summary
        val accountNotice = found.accountNotice.takeIf { fill.chooseAccount }
        sayAboutPicture(
            if (summary != null) {
                PictureNote(
                    "Filled in from the picture",
                    "Read on this phone${found.app?.let { " as a $it receipt" }.orEmpty()}, which can get things wrong. " +
                        "Check each detail, then save.",
                    notices = listOfNotNull(
                        ShareNotice("Filled in $summary."),
                        ShareNotice("Anything already filled in stays as it was.").takeIf { fill.kept },
                        accountNotice,
                    ),
                    readText = readText,
                )
            } else {
                PictureNote(
                    "Picture attached",
                    "What it shows is already filled in.",
                    notices = listOfNotNull(accountNotice),
                    readText = readText,
                )
            },
        )
    }

    /**
     * Reads [draft] while the loading sheet is up ([rememberPictureInput]) and gives back what attaching it does: put it in
     * place of any picture, then fill in from what it says whatever the form doesn't have yet. The form doesn't change until
     * then, so a cancelled reading leaves it as it was. The text is read from the [original] picture when there is one: the
     * draft is scaled down, and small print reads worse.
     */
    suspend fun readPicture(draft: File, original: Uri?): () -> Unit {
        val read = original?.let { pictureReader.read(it) } ?: pictureReader.read(Uri.fromFile(draft))
        val now = LocalDateTime.now()
        // What it says as a payment, read the way a shared picture is; null when it doesn't look like one.
        val found = read?.let {
            val reading = PaymentAppReceipts.read("", it, finance.currency, now.toLocalDate())
            if (reading is SharedTextReading.Draft) {
                SharedTransactionImport.prefill(reading.draft, it.text, finance, controller.repository, now, walletCards?.cards.orEmpty())
            } else {
                null
            }
        }
        return {
            pictureStore?.discard(draftPicture)
            draftPicture = draft
            pictureProblem = null
            when {
                read == null -> sayAboutPicture(PictureNote("Picture attached", "No text could be read from it, so nothing was filled in."))
                found == null -> sayAboutPicture(
                    PictureNote("Picture attached", "It doesn't look like a payment, so nothing was filled in.", readText = read.text),
                )
                else -> fillFromPicture(found, read.text)
            }
        }
    }

    val noun = if (typeChosen) type.label.lowercase() else "transaction"
    val accounts = finance.accounts.filter { !it.archived || it.id == accountId || it.id == toAccountId }
    val categories = finance.categories.filter {
        it.type == CategoryType.forTransaction(type) && (!it.archived || it.id == categoryId)
    }
    // How far this expense would take a credit card past its limit (null when it would not, or it is not a card with one).
    val chosenAccount = finance.account(accountId)
    val liveAmount = parseMinorUnits(amount, finance.currency.decimals)?.takeIf { it > 0 }
    val overLimitBy = if (type == TransactionType.Expense && liveAmount != null && chosenAccount?.id != null) {
        creditRoom(chosenAccount.creditLimit, balanceWithout(existing, chosenAccount.id, finance.balanceOf(chosenAccount.id)) - liveAmount)
            ?.takeIf { it.overLimit }?.let { -it.available }
    } else {
        null
    }
    val amountFocus = remember { FocusRequester() }
    // A shared draft that already has its amount opens for reading, without the keyboard covering it.
    LaunchedEffect(Unit) { if (!isEditing && prefill?.amount == null) runCatching { amountFocus.requestFocus() } }
    val topOfForm = remember { BringIntoViewRequester() }
    LaunchedEffect(picturesRead) { if (picturesRead > 0) topOfForm.bringIntoView() }
    val (dateShape, timeShape) = remember { connectedFieldShapes() }
    val reduced = rememberReducedMotion()

    EditorSheet(
        title = "${if (isEditing) "Edit" else "New"} $noun",
        saveLabel = if (isEditing) "Save changes" else "Save $noun",
        onSave = ::save,
        saving = saving,
        saveColors = ButtonDefaults.buttonColors(containerColor = role.color, contentColor = role.onColor),
    ) {
        // The form's notices sit at its top: a share's, or - in its place - what a picture attached here was read to say,
        // brought into view as it comes, with the top of the form it filled in.
        Column(Modifier.bringIntoViewRequester(topOfForm)) {
            prefill?.let {
                RevealVisibility(!pictureNoteShown) {
                    Column {
                        SharedDraftNotice(it.notices, fromPicture = it.fromPicture, readText = it.readText)
                        Spacer(Modifier.height(AppSpacing.lg))
                    }
                }
            }
            pictureNote?.let { note ->
                RevealVisibility(pictureNoteShown) {
                    Column {
                        SharedDraftNotice(
                            note.notices,
                            Modifier.politeLiveRegion(),
                            readText = note.readText,
                            title = note.title,
                            body = note.body,
                            icon = Icons.Outlined.Image,
                        )
                        Spacer(Modifier.height(AppSpacing.lg))
                    }
                }
            }
            TransactionTypeSelector(
                type = if (typeChosen) type else null,
                onSelect = {
                    typeChosen = true
                    typeTouched = true
                    typeError = null
                    setType(it)
                },
            )
            FieldError(typeError)
        }
        Spacer(Modifier.height(AppSpacing.xxl))
        HeroAmountField(
            value = amount,
            onValueChange = {
                amount = it
                amountError = null
            },
            currency = finance.currency,
            accent = role,
            errorText = amountError,
            modifier = Modifier.focusRequester(amountFocus),
        )
        Spacer(Modifier.height(AppSpacing.xxl))
        // What changes with the type fades in place rather than snapping.
        AnimatedContent(
            targetState = isTransfer,
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(AppMotion.SHORT_MS, delayMillis = 60, easing = AppMotion.enter)) togetherWith
                        fadeOut(tween(90))
                }
            },
            modifier = Modifier.animateContentSize(AppMotion.spatial()),
            label = "type_fields",
        ) { transfer ->
            Column {
                if (transfer) {
                    AccountSelector(
                        label = "From",
                        accounts = accounts,
                        selected = accountId,
                        onSelect = {
                            accountId = it
                            accountTouched = true
                            if (toAccountId == it) toAccountId = null
                            accountError = null
                        },
                    )
                    Spacer(Modifier.height(AppSpacing.lg))
                    AccountSelector(
                        label = "To",
                        accounts = accounts,
                        selected = toAccountId,
                        exclude = accountId,
                        onSelect = {
                            toAccountId = it
                            accountError = null
                        },
                        errorText = accountError,
                    )
                } else {
                    TransactionCategoryField(
                        categories = categories,
                        selected = categoryId,
                        onSelect = {
                            categoryId = it
                            categoryError = null
                        },
                        onCreate = ::newCategory,
                        errorText = categoryError,
                    )
                    Spacer(Modifier.height(AppSpacing.lg))
                    if (accounts.isEmpty()) {
                        Text(
                            "Add an account in Wallet first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        AccountSelector(
                            label = if (type == TransactionType.Income) "Into" else "Paid from",
                            accounts = accounts,
                            selected = accountId,
                            onSelect = {
                                accountId = it
                                accountTouched = true
                                accountError = null
                            },
                            errorText = accountError,
                        )
                        // A card has a limit: say so, before the expense is saved, when this one would go past it.
                        overLimitBy?.let { over ->
                            Spacer(Modifier.height(AppSpacing.sm))
                            Text(
                                "This puts ${chosenAccount?.name.orEmpty()} ${finance.money.format(over)} over its " +
                                    "${finance.money.format(chosenAccount?.creditLimit ?: 0, trimZeroFraction = true)} limit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        // Who it was spent on: only an expense has someone, so the field comes and goes with the type.
                        RevealVisibility(visible = type == TransactionType.Expense) {
                            Column {
                                Spacer(Modifier.height(AppSpacing.lg))
                                PersonSelector(
                                    people = finance.people.filter { !it.archived || it.id == personId },
                                    selected = personId,
                                    onSelect = { personId = it },
                                    onCreate = ::newPerson,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TonalPickerField(
                label = "Date",
                value = if (dateUnset) "Choose" else shortDate(occurredAt),
                icon = Icons.Outlined.CalendarToday,
                onClick = { pickingDate = true },
                modifier = Modifier.weight(1f),
                shape = dateShape,
            )
            TonalPickerField(
                label = "Time",
                value = timeLabel(occurredAt, is24Hour),
                icon = Icons.Outlined.Schedule,
                onClick = { pickingTime = true },
                modifier = Modifier.weight(1f),
                shape = timeShape,
            )
        }
        FieldError(dateError)
        Spacer(Modifier.height(AppSpacing.lg))
        RepeatField(repeat = repeat, onChange = { repeat = it }, from = occurredAt, ruleId = existing?.recurringId)
        Spacer(Modifier.height(AppSpacing.md))
        if (!showDetails) {
            TextButton(onClick = { showDetails = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.size(AppSpacing.sm))
                Text(if (isTransfer) "Add a note" else "Add payee or note")
            }
        } else {
            if (!isTransfer) {
                OutlinedTextField(
                    value = payee,
                    onValueChange = { payee = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (type == TransactionType.Income) "Received from" else "Paid to") },
                    placeholder = { Text(if (type == TransactionType.Income) "e.g. Employer" else "e.g. Store or merchant") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    colors = typedFieldColors(),
                    shape = TonalFieldShape,
                )
                Spacer(Modifier.height(AppSpacing.sm))
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note") },
                minLines = 1,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = typedFieldColors(),
                shape = TonalFieldShape,
            )
        }
        // Where pictures can be kept (the sample data keeps its own): a bill, a receipt or a payment screenshot, read to fill in.
        if (pictureStore != null) {
            Spacer(Modifier.height(AppSpacing.lg))
            TransactionPictureField(
                picture = shownPicture,
                input = rememberPictureInput(
                    pictureStore,
                    onPicked = ::readPicture,
                    onProblem = { pictureProblem = it },
                ),
                onView = { viewingPicture = true },
                onRemove = {
                    pictureStore.discard(draftPicture)
                    draftPicture = null
                    keptPicture = null
                    pictureNoteShown = false
                    pictureProblem = null
                },
                errorText = pictureProblem,
            )
        }
        formError?.let {
            Spacer(Modifier.height(AppSpacing.lg))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }

    if (viewingPicture) {
        shownPicture?.let { PictureViewer(it, onDismiss = { viewingPicture = false }) }
    }
    if (pickingDate) {
        DatePickerDialogFor(
            initial = occurredAt.toLocalDate(),
            onPicked = { date ->
                pickingDate = false
                occurredAt = LocalDateTime.of(date, occurredAt.toLocalTime())
                dateUnset = false
                dateTouched = true
                dateError = null
            },
            onDismiss = { pickingDate = false },
        )
    }
    if (pickingTime) {
        TimePickerDialogFor(
            initial = occurredAt,
            onPicked = { hour, minute ->
                pickingTime = false
                occurredAt = occurredAt.withHour(hour).withMinute(minute)
                dateTouched = true
            },
            onDismiss = { pickingTime = false },
        )
    }
}

/** [balance] of [accountId] as it was before [existing] (the transaction being edited, if any) happened. */
private fun balanceWithout(existing: FinanceTransaction?, accountId: Long, balance: Long): Long {
    if (existing == null) return balance
    val effect = when {
        existing.accountId == accountId -> if (existing.type == TransactionType.Income) existing.amount else -existing.amount
        existing.toAccountId == accountId -> existing.amount
        else -> 0L
    }
    return balance - effect
}

/** A Material date picker dialog for plain calendar dates with frosted styling. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogFor(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit, yearsAhead: Int = 10) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        yearRange = 2000..(LocalDate.now().year + yearsAhead),
    )
    val preferences = LocalAppPreferences.current
    val prefsState = preferences?.state?.collectAsState()
    val sheetBlurEnabled = prefsState?.value?.sheetBlurEnabled ?: true
    val surface = rememberDialogSurface(enabled = sheetBlurEnabled)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = surface.container),
        shape = surface.shape,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onPicked(java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DialogBackdropBlur(surface.blurBehind)
        SeeThroughColors(surface.blurBehind, surface.ground) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogFor(initial: LocalDateTime, onPicked: (hour: Int, minute: Int) -> Unit, onDismiss: () -> Unit) {
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour)
    val preferences = LocalAppPreferences.current
    val prefsState = preferences?.state?.collectAsState()
    val sheetBlurEnabled = prefsState?.value?.sheetBlurEnabled ?: true
    val surface = rememberDialogSurface(enabled = sheetBlurEnabled)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surface.container,
        shape = surface.shape,
        confirmButton = { TextButton(onClick = { onPicked(state.hour, state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            DialogBackdropBlur(surface.blurBehind)
            SeeThroughColors(surface.blurBehind, surface.ground) { TimePicker(state = state) }
        },
    )
}
