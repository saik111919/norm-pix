package com.example.normpix.wallet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.normpix.banks.ui.BankChoice
import com.example.normpix.banks.ui.BankSelector
import com.example.normpix.finance.ui.ChoiceOption
import com.example.normpix.finance.ui.ConnectedChoiceRow
import com.example.normpix.finance.ui.TonalFieldShape
import com.example.normpix.finance.ui.connectedFieldShapes
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.finance.ui.tonalFieldColors
import com.example.normpix.finance.ui.typedFieldColors
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.ui.components.AppLoadingIndicator
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve
import com.example.normpix.wallet.nfc.NfcCardTapSheet
import com.example.normpix.wallet.nfc.NfcTapResult
import com.example.normpix.wallet.nfc.PaymentCardInfo
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Inserts a space after every four digits while keeping the cursor where the user expects it. */
private object CardNumberTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i > 0 && i % 4 == 0) out.append(' ')
            out.append(c)
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + (offset - 1).coerceAtLeast(0) / 4

            override fun transformedToOriginal(offset: Int) = offset - offset / 5
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}

private fun digitsOnly(input: String, max: Int): String = input.filter { it in '0'..'9' }.take(max)

/** What the form says after a bank card was tapped: what was found, and why the rest is typed. */
private fun tappedNotice(info: PaymentCardInfo, canScan: Boolean): String =
    "${info.title} detected. Banks lock the card number inside the chip, so no app can read it with a tap. " +
        if (canScan) "Type the details below, or use Scan card on the front." else "Type the details below."

/**
 * The add/edit card form. This is the single place a card is created or
 * edited anywhere in the app. Calls [onDone] with the saved [StoredCard], or
 * null if cancelled. Callers persist the result through the shared vault.
 *
 * Laid out like the transaction editor (see `EditorSheet`). A new card can be filled from a camera
 * scan or an NFC tap; [tapped] is a bank card identified by a tap before the form opened.
 */
@Composable
fun AddCardSheet(initial: StoredCard?, onDone: (StoredCard?) -> Unit, tapped: PaymentCardInfo? = null) {
    val container = LocalAppContainer.current
    val scanner = container.scanner
    val overlays = LocalOverlays.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val isEditing = initial != null
    val catalog = rememberFinanceState().catalog

    // The bank is one of the banks the app knows - the same list accounts use - not free text. A card from before banks
    // were shared opens on the bank its name refers to, or on its old name to be turned into one.
    var bank by remember {
        mutableStateOf(
            initial?.let { card ->
                (catalog.byKey(card.bankKey) ?: catalog.resolve(card.bankName))?.let { BankChoice(it.key) }
                    ?: BankChoice(null, card.bankName)
            } ?: BankChoice(),
        )
    }
    var name by remember { mutableStateOf(initial?.cardholderName.orEmpty()) }
    var number by remember { mutableStateOf(initial?.cardNumber.orEmpty()) }
    var month by remember { mutableStateOf(initial?.expiryMonth.orEmpty()) }
    var year by remember { mutableStateOf(initial?.expiryYear.orEmpty()) }
    var cvv by remember { mutableStateOf(initial?.cvv.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind ?: tapped?.kind ?: CardKind.Credit) }
    var submitted by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scanMessage by remember {
        mutableStateOf<Pair<StatusTone, String>?>(tapped?.let { StatusTone.Info to tappedNotice(it, scanner != null) })
    }
    val focus = remember { FocusRequester() }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!isEditing) runCatching { focus.requestFocus() } }
    // Tapping fills a new card; a phone without NFC hardware doesn't offer it at all.
    val canTap = !isEditing && remember { container.nfc.availability() != NfcAvailability.Unsupported }

    fun snapshot() = listOf(bank.key, bank.typed, name, number, month, year, cvv, kind).joinToString("|")
    val initialSnapshot = remember { snapshot() }
    DiscardChangesGuard(dirty = snapshot() != initialSnapshot, isEditing = isEditing, keptMessage = "The card stays as it was.")

    // What the card says for the bank so far: the chosen bank's name, else whatever is typed.
    val bankName = catalog.byKey(bank.key)?.name ?: bank.typed.trim()

    /** Digit count required for the chosen bank, when it's a recognized Indian bank; null falls back to 12-19. */
    val expectedDigits = IndianBankCardLength.digitsFor(bankName)
    val maxDigits = expectedDigits ?: 19

    var photoTarget by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val photo = photoTarget
        photoTarget = null
        if (photo == null || scanner == null) return@rememberLauncherForActivityResult
        if (!taken) {
            // The user cancelled the camera - not an error, nothing to say.
            scanner.discard(photo)
            scanning = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                val scanned = scanner.read(photo)
                if (scanned.isEmpty) {
                    scanMessage = StatusTone.Warning to "Couldn't find card details in that photo. Enter them below."
                } else {
                    scanned.cardNumber?.let { number = digitsOnly(it, 19) }
                    scanned.cardholderName?.let { name = it }
                    scanned.expiryMonth?.let { month = it }
                    scanned.expiryYear?.let { year = it }
                    scanMessage = StatusTone.Success to "Filled from the scan. Check the details before saving."
                    haptics.success()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: CardScanException) {
                scanMessage = StatusTone.Error to (e.message ?: "Couldn't read that photo.")
            } finally {
                scanning = false
            }
        }
    }

    fun scan() {
        val s = scanner ?: return
        scanning = true
        scanMessage = null
        val (file, uri) = s.newPhotoTarget()
        photoTarget = file
        try {
            // A trip to the camera app isn't "leaving" NormPix: don't re-lock under the open form.
            container.lock.expectExternalActivity()
            camera.launch(uri)
        } catch (_: Exception) {
            s.discard(file)
            photoTarget = null
            scanning = false
            scanMessage = StatusTone.Error to "Could not open the camera on this device."
        }
    }

    /** Fills the form from an NFC tap: everything from a phone sharing a card, only what a bank card's chip publishes otherwise. */
    fun tap() {
        scope.launch {
            when (val result = overlays.sheet<NfcTapResult> { dismiss -> NfcCardTapSheet(onResult = dismiss) }) {
                is NfcTapResult.Shared -> {
                    val card = result.card
                    bank = catalog.resolve(card.bankName)?.let { BankChoice(it.key) } ?: BankChoice(null, card.bankName)
                    name = card.cardholderName
                    number = digitsOnly(card.cardNumber, 19)
                    month = card.expiryMonth
                    year = card.expiryYear.takeLast(2)
                    cvv = card.cvv
                    kind = card.kind
                    scanMessage = StatusTone.Success to "Filled from the other phone. Check the details before saving."
                }
                is NfcTapResult.Payment -> {
                    result.info.kind?.let { kind = it }
                    scanMessage = StatusTone.Info to tappedNotice(result.info, scanner != null)
                }
                null -> Unit
            }
        }
    }

    // Field errors, only surfaced once the user has tried to save.
    val bankError = when {
        !submitted || bank.key != null -> null
        bank.unmatched -> "Pick a bank from the list, or add it as a new bank"
        else -> "Choose a bank"
    }
    val nameError = if (submitted && name.isBlank()) "Enter the name on the card" else null
    val numberError = if (submitted) {
        when {
            expectedDigits != null -> if (number.length == expectedDigits) null else "Must be $expectedDigits digits"
            number.length !in 12..19 -> "Enter a valid card number"
            else -> null
        }
    } else {
        null
    }
    val monthError = if (submitted && (month.toIntOrNull() ?: 0) !in 1..12) "01–12" else null
    val yearError = if (submitted && year.length != 2) "2 digits" else null
    val cvvError = if (submitted && cvv.length < 3) "3–4 digits" else null

    fun submit() {
        submitted = true
        val picked = catalog.byKey(bank.key)
        val valid = name.isNotBlank() &&
            (if (expectedDigits != null) number.length == expectedDigits else number.length in 12..19) &&
            (month.toIntOrNull() ?: 0) in 1..12 && year.length == 2 && cvv.length >= 3
        if (picked == null || !valid) return
        onDone(
            StoredCard(
                id = initial?.id ?: "${System.currentTimeMillis()}${(0..999).random()}",
                bankName = picked.name,
                cardholderName = name.trim(),
                cardNumber = number,
                expiryMonth = month.padStart(2, '0'),
                expiryYear = year.padStart(2, '0'),
                cvv = cvv,
                kind = kind,
                createdAt = initial?.createdAt ?: LocalDateTime.now(),
                bankKey = picked.key,
            ),
        )
    }

    // Live card model reflecting the current form values.
    val liveCard = StoredCard(
        id = "preview",
        bankName = bankName.ifEmpty { "Select Bank" },
        cardholderName = name.trim().ifEmpty { "CARDHOLDER NAME" },
        cardNumber = number.ifEmpty { "••••••••••••••••" },
        expiryMonth = if (month.isEmpty()) "MM" else month.padStart(2, '0'),
        expiryYear = if (year.isEmpty()) "YY" else year.padStart(2, '0'),
        cvv = cvv.ifEmpty { "•••" },
        kind = kind,
        createdAt = LocalDateTime.now(),
    )
    val kinds = remember {
        listOf(ChoiceOption(CardKind.Credit, CardKind.Credit.label, Icons.Outlined.CreditCard), ChoiceOption(CardKind.Debit, CardKind.Debit.label, Icons.Outlined.Payments))
    }
    val (monthShape, yearShape) = remember { connectedFieldShapes() }

    EditorSheet(
        title = if (isEditing) "Edit card" else "Add card",
        subtitle = "Stored encrypted, on this device only",
        saveLabel = if (isEditing) "Save changes" else "Save card",
        onSave = ::submit,
    ) {
        if (scanner != null || canTap) {
            // Two ways to fill the form without typing it all.
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                if (scanner != null) {
                    FilledTonalButton(onClick = ::scan, enabled = !scanning, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        if (scanning) {
                            AppLoadingIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        }
                        Spacer(Modifier.width(AppSpacing.sm))
                        Text(if (scanning) "Scanning" else "Scan card")
                    }
                }
                if (canTap) {
                    FilledTonalButton(onClick = ::tap, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Contactless, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(AppSpacing.sm))
                        Text("Tap card")
                    }
                }
            }
            Spacer(Modifier.height(AppSpacing.md))
        }
        scanMessage?.let { (tone, message) ->
            InlineNotice(tone, message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Spacer(Modifier.height(AppSpacing.md))
        }
        WalletCardFacePreview(liveCard, revealed = true)
        Spacer(Modifier.height(AppSpacing.lg))
        ConnectedChoiceRow(kinds, selected = kind, onSelect = { kind = it })
        Spacer(Modifier.height(AppSpacing.lg))

        // The same searchable list of banks the account form uses (India's public, private, small-finance and
        // payments banks, plus any the user added), with "Add new bank" at its end.
        BankSelector(
            choice = bank,
            onChoice = { bank = it },
            errorText = bankError,
            focusRequester = focus,
            // Picking a bank answers that field: carry on to the next one.
            onPicked = { runCatching { nameFocus.requestFocus() } },
        )
        Spacer(Modifier.height(AppSpacing.md))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            label = { Text("Cardholder name") },
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
        Spacer(Modifier.height(AppSpacing.md))
        OutlinedTextField(
            value = number,
            onValueChange = { number = digitsOnly(it, maxDigits) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Card number") },
            placeholder = { Text("1234 5678 9012 3456") },
            isError = numberError != null,
            supportingText = (numberError ?: expectedDigits?.let { "$it digits for this bank" })?.let { { Text(it) } },
            singleLine = true,
            visualTransformation = CardNumberTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
        Spacer(Modifier.height(AppSpacing.md))
        Row {
            // Month and year are one expiry date: a connected pair, like the editor's date and time.
            Row(Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                OutlinedTextField(
                    value = month,
                    onValueChange = { month = digitsOnly(it, 2) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Month") },
                    placeholder = { Text("MM") },
                    isError = monthError != null,
                    supportingText = monthError?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    colors = typedFieldColors(),
                    shape = monthShape,
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = digitsOnly(it, 2) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Year") },
                    placeholder = { Text("YY") },
                    isError = yearError != null,
                    supportingText = yearError?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    colors = typedFieldColors(),
                    shape = yearShape,
                )
            }
            Spacer(Modifier.width(AppSpacing.md))
            OutlinedTextField(
                value = cvv,
                onValueChange = { cvv = digitsOnly(it, 4) },
                modifier = Modifier.weight(1f),
                label = { Text("CVV") },
                isError = cvvError != null,
                supportingText = cvvError?.let { { Text(it) } },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                colors = typedFieldColors(),
                shape = TonalFieldShape,
            )
        }
    }
}

/** A short tonal message inside a form (scan and tap results). */
@Composable
private fun InlineNotice(tone: StatusTone, message: String, modifier: Modifier = Modifier) {
    val role = tone.resolve()
    Row(
        modifier
            .fillMaxWidth()
            .background(role.container, AppRadius.rMd)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (tone) {
                StatusTone.Success -> Icons.Outlined.CheckCircle
                StatusTone.Error -> Icons.Outlined.ErrorOutline
                else -> Icons.Outlined.Info
            },
            contentDescription = null,
            tint = role.onContainer,
            modifier = Modifier.size(AppIconSizes.md),
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = role.onContainer)
    }
}
