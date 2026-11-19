package com.example.normpix.wallet.passes

import android.app.Activity
import android.content.ContextWrapper
import android.net.Uri
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.example.normpix.finance.domain.minorToInputText
import com.example.normpix.finance.domain.parseMinorUnits
import com.example.normpix.finance.ui.AmountField
import com.example.normpix.finance.ui.ChoiceDropdownField
import com.example.normpix.finance.ui.ChoiceOption
import com.example.normpix.finance.ui.DatePickerDialogFor
import com.example.normpix.finance.ui.PickerField
import com.example.normpix.finance.ui.PickerFieldRow
import com.example.normpix.finance.ui.TonalFieldShape
import com.example.normpix.finance.ui.paletteColor
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.finance.ui.shortDate
import com.example.normpix.finance.ui.typedFieldColors
import com.example.normpix.ui.components.ConnectedButtonGroup
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.GroupButton
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.wallet.SectionAddButton
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.WalletSectionHeader
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.floor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * Loyalty, membership and gift cards on screen (2026-11-19): the Wallet's section of them, a pass at the counter (its code large on white,
 * the screen at full brightness), and the form - typed or scanned from a photo or a screenshot. The model, the vault and the codes are
 * in this package; the flows (open, add, edit, delete with Undo) are `PassFlows`.
 */

/** The test tag of the Wallet's passes section and of the code drawn on a pass. */
internal const val PASSES_SECTION_TAG = "passes_section"
internal const val PASS_CODE_TAG = "pass_code"

/** A pass kind's mark, from icons that have a Lucide drawing too. */
internal val PassKind.icon
    get() = when (this) {
        PassKind.Loyalty -> Icons.Outlined.ConfirmationNumber
        PassKind.Membership -> Icons.Outlined.CardMembership
        PassKind.GiftCard -> Icons.Outlined.CardGiftcard
        PassKind.Other -> Icons.Outlined.Badge
    }

/** The Wallet's heading for the passes: what they are, how many, and Add. */
@Composable
fun PassesHeader(count: Int?, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    WalletSectionHeader(
        title = "Loyalty & gift cards",
        summary = when (count) {
            null -> null
            0 -> "Scan a card's code once, show it at the counter"
            else -> "$count saved, encrypted and kept on this device"
        },
        modifier = modifier,
        action = { SectionAddButton("Add", onAdd) },
    )
}

/** The passes as tiles, two to a row; a tap opens one at the counter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PassesGrid(passes: List<StoredPass>, onOpen: (StoredPass) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth().testTag(PASSES_SECTION_TAG),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        maxItemsInEachRow = 2,
    ) {
        for (pass in passes) PassTile(pass, onOpen = { onOpen(pass) }, modifier = Modifier.weight(1f))
        // An odd one out keeps its half of the row.
        if (passes.size % 2 == 1) Spacer(Modifier.weight(1f))
    }
}

/** One pass as a tile in its colour: its kind's mark, its name, its kind and the end of its number. */
@Composable
fun PassTile(pass: StoredPass, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = paletteColor(pass.colorIndex)
    val ending = pass.ending
    Surface(
        color = colors.container,
        contentColor = colors.onContainer,
        shape = AppRadius.rLg,
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(AppRadius.rLg)
            .clickable(onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = "${pass.name}, ${pass.kind.label}" + if (ending.isNotEmpty()) ", ending $ending" else ""
                role = Role.Button
                onClick(label = "Show") { onOpen(); true }
            },
    ) {
        Column(Modifier.padding(AppSpacing.md)) {
            Icon(pass.kind.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(AppSpacing.sm))
            Text(pass.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(pass.kind.label, ending.takeIf { it.isNotEmpty() }?.let { "•• $it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

/**
 * [number] drawn as [format]'s code, black on white whatever the theme (what a shop's scanner reads), with the quiet zone the code needs
 * round it and each module a whole number of pixels so its edges stay sharp. Nothing for a pass that keeps its number only, or one whose
 * number its format can't hold. [height] is a one-dimensional code's.
 */
@Composable
fun PassCodeImage(number: String, format: PassCodeFormat, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 120.dp) {
    val matrix = remember(number, format) { passCodeMatrix(number, format) } ?: return
    val bars = remember(matrix) { if (format.twoDimensional) emptyList() else barsOf(matrix) }
    // Quiet zone, in modules: ten either side of a barcode, four round a square code.
    val quiet = if (format.twoDimensional) 4 else 10
    val shape = if (format.twoDimensional) {
        Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio((matrix.width + 2f * quiet) / (matrix.height + 2f * quiet))
    } else {
        Modifier.fillMaxWidth().height(height)
    }
    Canvas(
        modifier
            .then(shape)
            .clip(AppRadius.rMd)
            .background(Color.White)
            .testTag(PASS_CODE_TAG)
            .semantics { contentDescription = "${format.label} of the card's number" },
    ) {
        val across = matrix.width + 2 * quiet
        val unit = floor(size.width / across).coerceAtLeast(1f)
        val left = (size.width - unit * matrix.width) / 2f
        if (format.twoDimensional) {
            val top = (size.height - unit * matrix.height) / 2f
            for (y in 0 until matrix.height) {
                var x = 0
                while (x < matrix.width) {
                    if (!matrix[x, y]) {
                        x++
                        continue
                    }
                    val start = x
                    while (x < matrix.width && matrix[x, y]) x++
                    drawRect(Color.Black, Offset(left + start * unit, top + y * unit), Size((x - start) * unit, unit))
                }
            }
        } else {
            val top = size.height * 0.08f
            for ((start, width) in bars) {
                drawRect(Color.Black, Offset(left + start * unit, top), Size(width * unit, size.height - 2 * top))
            }
        }
    }
}

/** The number as it is read out at a counter: in fours when it is all digits and spaces, else as it is. */
internal fun groupedNumber(number: String): String {
    val compact = number.filter { !it.isWhitespace() }
    return if (compact.isNotEmpty() && compact.all(Char::isDigit)) compact.chunked(4).joinToString(" ") else number
}

/** What a pass's sheet was closed with. */
enum class PassAction { Edit, Delete }

/**
 * One pass at the counter: its name and kind, its code large on white and its number under it - the screen at full brightness while it
 * is open, so a scanner reads it - and, for a gift card, what is left on it, until when, and its PIN behind Show. Edit and Delete below.
 */
@Composable
fun PassSheet(pass: StoredPass, onAction: (PassAction?) -> Unit) {
    val finance = rememberFinanceState()
    val scheme = MaterialTheme.colorScheme
    var showPin by remember { mutableStateOf(false) }
    FullBrightness()
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = AppSpacing.xl, end = AppSpacing.xl, bottom = AppSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(pass.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(pass.kind.label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(AppSpacing.lg))
        if (pass.format != PassCodeFormat.None) {
            PassCodeImage(pass.number, pass.format, height = 140.dp)
            Spacer(Modifier.height(AppSpacing.md))
        }
        Text(
            groupedNumber(pass.number),
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.semantics { contentDescription = "Number ${pass.number.toList().joinToString(" ")}" },
        )
        if (pass.kind == PassKind.GiftCard) {
            Spacer(Modifier.height(AppSpacing.lg))
            val facts = listOfNotNull(
                pass.balance?.let { "Balance ${finance.money.format(it, trimZeroFraction = true)}" },
                pass.expiry?.let { "Valid until ${shortDate(it.atStartOfDay())}" },
            )
            if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
            if (pass.hasPin) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showPin) "PIN ${pass.pin}" else "PIN ${"•".repeat(pass.pin.length.coerceAtMost(8))}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    )
                    TextButton(onClick = { showPin = !showPin }) {
                        Icon(if (showPin) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text(if (showPin) "Hide" else "Show")
                    }
                }
            }
        }
        if (pass.note.isNotBlank()) {
            Spacer(Modifier.height(AppSpacing.md))
            Text(pass.note, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(AppSpacing.xl))
        ConnectedButtonGroup(
            buttons = listOf(
                GroupButton("Edit", Icons.Outlined.Edit, scheme.surfaceContainerHighest, scheme.onSurface) { onAction(PassAction.Edit) },
                GroupButton("Delete", Icons.Outlined.DeleteOutline, scheme.surfaceContainerHighest, scheme.error) { onAction(PassAction.Delete) },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The window this is drawn in at full brightness while it is shown, as it was before after (a code on a dim screen may not scan). */
@Composable
private fun FullBrightness() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.windowOf()
        val before = window?.attributes?.screenBrightness
        window?.let { it.attributes = it.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL } }
        onDispose {
            window?.let { it.attributes = it.attributes.apply { screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        }
    }
}

private fun View.windowOf(): Window? {
    var v: View? = this
    while (v != null) {
        if (v is DialogWindowProvider) return v.window
        v = v.parent as? View
    }
    var context = context
    while (context is ContextWrapper) {
        if (context is DialogWindowProvider) return context.window
        if (context is Activity) return context.window
        context = context.baseContext
    }
    return null
}

/**
 * Adds or edits a pass: its name, what it is, its number - typed, or read from the card's code in a photo or a screenshot ([reader]; none
 * in tests) - and the type of code, with the code drawn as it will be shown; its colour; for a gift card the balance, the PIN and the day
 * it runs out; a note. Saved, it gives the pass back (not yet kept: the caller keeps it); closed, null.
 */
@Composable
fun PassFormSheet(
    existing: StoredPass?,
    reader: PassCodeReader?,
    /** A fresh file and its content URI for the camera app to write a photo to; null where there is no camera (tests). */
    photoTarget: (() -> Pair<File, Uri>)?,
    onDone: (StoredPass?) -> Unit,
) {
    val finance = rememberFinanceState()
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val decimals = finance.currency.decimals

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: PassKind.Loyalty) }
    var number by remember { mutableStateOf(existing?.number.orEmpty()) }
    var format by remember { mutableStateOf(existing?.format) }
    var colorIndex by remember { mutableStateOf(existing?.colorIndex ?: 0) }
    var pin by remember { mutableStateOf(existing?.pin.orEmpty()) }
    var balance by remember { mutableStateOf(existing?.balance?.let { minorToInputText(it, decimals) }.orEmpty()) }
    var expiry by remember { mutableStateOf(existing?.expiry) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var pickingDate by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scanNote by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // A typed number with no type chosen is drawn as what it most likely is (an EAN-13 for 13 digits with a right check digit...).
    val shownFormat = format ?: likelyFormatOf(number)
    val codeFault = passCodeFault(number, shownFormat)

    val initial = remember { listOf(name, kind, number, format, colorIndex, pin, balance, expiry, note).joinToString("|") }
    val dirty = listOf(name, kind, number, format, colorIndex, pin, balance, expiry, note).joinToString("|") != initial
    DiscardChangesGuard(dirty = dirty, saving = false, isEditing = existing != null, keptMessage = "The card stays as it was.")

    fun read(picture: Uri, photo: File?) {
        val scanner = reader ?: return
        scanning = true
        scanNote = null
        scope.launch {
            try {
                val found = scanner.read(picture)
                number = found.number
                format = found.format
                scanNote = "Read as ${found.format.label}. Check the number against the card."
                haptics.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: PassScanException) {
                scanNote = e.message
            } catch (_: Exception) {
                scanNote = "Couldn't read that picture. Try another, or type the number."
            } finally {
                photo?.delete()
                scanning = false
            }
        }
    }

    var pendingPhoto by remember { mutableStateOf<Pair<File, Uri>?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val target = pendingPhoto
        pendingPhoto = null
        if (target == null) return@rememberLauncherForActivityResult
        if (taken) read(target.second, target.first) else target.first.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) read(uri, null) }

    fun save() {
        val trimmedName = name.trim()
        val parsedBalance = if (kind == PassKind.GiftCard && balance.isNotBlank()) parseMinorUnits(balance, decimals) else null
        if (kind == PassKind.GiftCard && balance.isNotBlank() && parsedBalance == null) {
            error = "Enter the balance as an amount, or leave it empty."
            return
        }
        val now = LocalDateTime.now()
        val pass = StoredPass(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            kind = kind,
            name = trimmedName,
            number = number.trim(),
            format = shownFormat,
            colorIndex = colorIndex,
            pin = if (kind == PassKind.GiftCard) pin.trim() else "",
            balance = if (kind == PassKind.GiftCard) parsedBalance else null,
            expiry = if (kind == PassKind.GiftCard) expiry else null,
            note = note.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        error = pass.fault() ?: passCodeFault(pass.number, pass.format)
        if (error != null) return
        haptics.success()
        onDone(pass)
    }

    EditorSheet(
        title = if (existing == null) "New card" else "Edit card",
        saveLabel = if (existing == null) "Save card" else "Save changes",
        onSave = ::save,
        errorText = error,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= StoredPass.MAX_NAME) name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") },
            placeholder = { Text("e.g. DMart, Cult.fit, a gift voucher") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
        Spacer(Modifier.height(AppSpacing.lg))
        ChoiceDropdownField(
            label = "Kind",
            options = PassKind.entries.map { ChoiceOption(it, it.label, it.icon) },
            selected = kind,
            onSelect = { kind = it },
        )
        Spacer(Modifier.height(AppSpacing.lg))
        OutlinedTextField(
            value = number,
            onValueChange = { if (it.length <= StoredPass.MAX_NUMBER) number = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Number") },
            placeholder = { Text("As printed on the card") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
        if (reader != null) {
            Spacer(Modifier.height(AppSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                if (photoTarget != null) {
                    TextButton(
                        enabled = !scanning,
                        onClick = {
                            val target = photoTarget()
                            pendingPhoto = target
                            camera.launch(target.second)
                        },
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text("Scan the card")
                    }
                }
                TextButton(
                    enabled = !scanning,
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text("From a screenshot")
                }
            }
            scanNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(AppSpacing.lg))
        ChoiceDropdownField(
            label = "Code",
            options = PassCodeFormat.entries.map { ChoiceOption(it, it.label, Icons.Outlined.DocumentScanner) },
            selected = shownFormat,
            onSelect = { format = it },
        )
        // The code as it will be shown at the counter - or why it can't be drawn.
        if (number.isNotBlank() && shownFormat != PassCodeFormat.None) {
            Spacer(Modifier.height(AppSpacing.sm))
            if (codeFault == null) {
                PassCodeImage(number.trim(), shownFormat, height = 72.dp)
            } else {
                Text(codeFault, style = MaterialTheme.typography.bodySmall, color = scheme.error)
            }
        }
        Spacer(Modifier.height(AppSpacing.lg))
        Text("Colour", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(AppSpacing.sm))
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            for (i in 0 until PASS_COLORS) {
                val swatch = paletteColor(i)
                val chosen = i == colorIndex
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(swatch.container)
                        .border(if (chosen) 3.dp else 1.dp, if (chosen) swatch.accent else scheme.outlineVariant, CircleShape)
                        .selectable(selected = chosen, role = Role.RadioButton, onClick = { colorIndex = i })
                        .semantics { contentDescription = "Colour ${i + 1}" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (chosen) Icon(androidx.compose.material.icons.Icons.Rounded.Check, contentDescription = null, tint = swatch.onContainer, modifier = Modifier.size(AppIconSizes.md))
                }
            }
        }
        RevealVisibility(visible = kind == PassKind.GiftCard) {
            Column {
                Spacer(Modifier.height(AppSpacing.lg))
                AmountField(
                    value = balance,
                    onValueChange = { balance = it },
                    currency = finance.currency,
                    label = "Balance left (optional)",
                )
                Spacer(Modifier.height(AppSpacing.sm))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 16) pin = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PIN (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = typedFieldColors(),
                    shape = TonalFieldShape,
                )
                Spacer(Modifier.height(AppSpacing.sm))
                PickerFieldRow(first = PickerField("Valid until", expiry?.let { shortDate(it.atStartOfDay()) } ?: "No date") { pickingDate = true })
            }
        }
        Spacer(Modifier.height(AppSpacing.lg))
        OutlinedTextField(
            value = note,
            onValueChange = { if (it.length <= StoredPass.MAX_NOTE) note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
            minLines = 1,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
    }

    if (pickingDate) {
        DatePickerDialogFor(
            initial = expiry ?: LocalDate.now().plusYears(1),
            earliest = LocalDate.now().minusYears(1),
            onPicked = {
                pickingDate = false
                expiry = it
            },
            onDismiss = { pickingDate = false },
        )
    }
}

/** How many of the palette's colours a pass is offered. */
private const val PASS_COLORS = 6
