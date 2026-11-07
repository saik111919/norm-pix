package com.example.normpix.finance.ui

import com.example.normpix.ui.components.solidColorScheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.rememberCoroutineScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.OptionSheet
import com.example.normpix.ui.components.OptionSheetItem
import kotlinx.coroutines.launch
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import com.example.normpix.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.lerp
import com.example.normpix.banks.ui.AccountBadge
import com.example.normpix.banks.ui.BankBadge
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.emojiOf
import com.example.normpix.finance.domain.inTreeOrder
import com.example.normpix.finance.domain.pathName
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusColorRole
import com.example.normpix.ui.theme.isDark

/*
 * The editor's input vocabulary, in Material 3 Expressive terms: a connected choice
 * group whose selected segment changes shape, size and tone; one dropdown for long
 * lists; a hero amount; and tonal (filled, borderless) fields. Each is built once and
 * reused wherever the same kind of choice appears.
 */

/**
 * One choice of a [ConnectedChoiceRow] or [ChoiceDropdownField]. [glyph] replaces the icon with other artwork (a bank's logo)
 * on its badge in the dropdown. [avatar] replaces the badge altogether, drawn at the size it is given: for a category or a
 * person, whose mark is the same avatar here as in the lists ([CategoryAvatar], [PersonAvatar]) - in the shape the user chose.
 */
@Immutable
class ChoiceOption<K>(
    val key: K,
    val label: String,
    val icon: ImageVector,
    val glyph: (@Composable () -> Unit)? = null,
    val avatar: (@Composable (size: Dp) -> Unit)? = null,
    val supporting: String? = null,
)

/** Round-cornered tonal fields: filled, no outline, an indicator only for errors. */
@Composable
fun tonalFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return TextFieldDefaults.colors(
        focusedContainerColor = scheme.surfaceContainerHighest,
        unfocusedContainerColor = scheme.surfaceContainerHighest,
        errorContainerColor = scheme.surfaceContainerHighest,
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        focusedLabelColor = scheme.onSurfaceVariant,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        focusedLeadingIconColor = scheme.onSurfaceVariant,
        unfocusedLeadingIconColor = scheme.onSurfaceVariant,
        focusedTrailingIconColor = scheme.onSurfaceVariant,
        unfocusedTrailingIconColor = scheme.onSurfaceVariant,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = scheme.error,
    )
}

/**
 * For fields the user types into: outlined, with a soft border that turns primary while editing (and error-coloured when
 * invalid). The tonal fill ([tonalFieldColors]) is for fields that pick, so what can be typed in looks different.
 */
@Composable
fun typedFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.outlineVariant,
        errorBorderColor = scheme.error,
    )
}

/** Shape of a standalone tonal field. */
val TonalFieldShape = AppRadius.rLg

private val SegmentHeight = 48.dp
private val SegmentGap = 2.dp
private val SegmentTrackPadding = 4.dp

/**
 * A connected group of mutually exclusive choices (Material 3 Expressive "connected button group"), as one pill-shaped
 * track: the choices sit inside it 2dp apart, and the selected one is a pill of its own in [selectedColors] (the primary
 * container unless given) that grows a little and shows its icon, while the others are transparent with soft labels.
 * Each segment is at least 48dp tall and announced as a radio button.
 */
@Composable
fun <K> ConnectedChoiceRow(
    options: List<ChoiceOption<K>>,
    selected: K?,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    selectedColors: StatusColorRole? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = selectedColors ?: primaryTonalChoice()
    val haptics = rememberAppHaptics()
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(AppRadius.rFull)
            .background(scheme.surfaceContainerHigh)
            .padding(SegmentTrackPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        for (option in options) {
            val isSelected = option.key == selected
            ChoiceSegment(
                option = option,
                selected = isSelected,
                colors = colors,
                onClick = {
                    if (!isSelected) haptics.selection()
                    onSelect(option.key)
                },
            )
        }
    }
}

@Composable
private fun <K> RowScope.ChoiceSegment(
    option: ChoiceOption<K>,
    selected: Boolean,
    colors: StatusColorRole,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val emphasis by animateFloatAsState(if (selected) 1f else 0f, AppMotion.spatial(), label = "choice_emphasis")
    // Only the opacity changes between chosen and not: a fill that fades in, never one that passes through another colour.
    val container by animateColorAsState(
        if (selected) colors.container else colors.container.copy(alpha = 0f),
        tween(AppMotion.SHORT_MS, easing = AppMotion.standard),
        label = "choice_container",
    )
    val content by animateColorAsState(
        if (selected) colors.onContainer else scheme.onSurfaceVariant,
        tween(AppMotion.SHORT_MS, easing = AppMotion.standard),
        label = "choice_content",
    )
    Row(
        Modifier
            .weight(1f + 0.25f * emphasis.coerceAtLeast(0f))
            .fillMaxHeight()
            .heightIn(min = SegmentHeight)
            .clip(AppRadius.rFull)
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            // Tight on purpose: three labels share the width, and large text needs every dp of it.
            .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The icon belongs to the chosen segment: it opens up beside the label, the others stay text-only.
        val fade = AppMotion.duration(AppMotion.SHORT_MS)
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(fade, easing = AppMotion.standard)) + expandHorizontally(tween(fade, easing = AppMotion.standard)),
            exit = fadeOut(tween(fade, easing = AppMotion.standard)) + shrinkHorizontally(tween(fade, easing = AppMotion.standard)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(option.icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(AppSpacing.xs + 2.dp))
            }
        }
        Text(
            option.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * The chosen choice of every [ConnectedChoiceRow]: a primary tonal fill with dark text on it - the primary tone itself on a
 * dark theme (light against the dark sheet), the primary container on a light one. Dynamic, so it follows the wallpaper.
 */
@Composable
fun primaryTonalChoice(): StatusColorRole {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.isDark) {
        StatusColorRole(scheme.primary, scheme.onPrimary, scheme.primary, scheme.onPrimary)
    } else {
        StatusColorRole(scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer)
    }
}

/**
 * Expense | Income | Transfer, in the same primary tonal pill as every other choice. The type's own colour stays where it
 * means something: the amount's symbol and outline and the Save button. A null [type] shows none chosen yet.
 */
@Composable
fun TransactionTypeSelector(type: TransactionType?, onSelect: (TransactionType) -> Unit, modifier: Modifier = Modifier) {
    val options = remember { TransactionType.entries.map { ChoiceOption(it, it.label, it.icon) } }
    ConnectedChoiceRow(options, selected = type, onSelect = onSelect, modifier = modifier)
}

private data class SheetChoice<K>(val value: K)

/**
 * One Material 3 exposed dropdown: the field shows the current choice (icon and label) and the
 * menu lists every option, the selected one marked. An optional last item ([createLabel]) creates a
 * new option without leaving the form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <K> ChoiceDropdownField(
    label: String,
    options: List<ChoiceOption<K>>,
    selected: K?,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Choose",
    errorText: String? = null,
    createLabel: String? = null,
    onCreate: (() -> Unit)? = null,
    /** The badge shown while nothing is chosen, so the card has its icon from the start. */
    emptyIcon: ImageVector? = null,
    /** Whether [emptyIcon] stands for a category or a person, and so is drawn as an avatar in the shape the user chose. */
    emptyAsAvatar: Boolean = false,
    /** The mark shown while nothing is chosen, when it is not an icon on a badge (an account's, in the shape the user chose). Wins over [emptyIcon]. */
    emptyAvatar: (@Composable (size: Dp) -> Unit)? = null,
) {
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val current = options.firstOrNull { option -> option.key == selected }

    val openSheet: () -> Unit = {
        scope.launch {
            val selectedSet = options.filter { it.key == selected }.map { SheetChoice(it.key) }.toSet()
            val picked = overlays.sheet<SheetChoice<K>> { dismiss ->
                OptionSheet(
                    title = label,
                    options = options.map { option ->
                        OptionSheetItem(
                            key = SheetChoice(option.key),
                            label = option.label,
                            supporting = option.supporting,
                            trailing = { ChoiceIcon(option, 32.dp) },
                        )
                    },
                    selected = selectedSet,
                    multiple = false,
                    onSelect = { pickedChoice ->
                        dismiss(pickedChoice)
                    },
                    actionLabel = createLabel,
                    actionIcon = if (createLabel != null) Icons.Rounded.Add else null,
                    onAction = if (onCreate != null && createLabel != null) {
                        {
                            dismiss(null)
                            onCreate()
                        }
                    } else null,
                )
            }
            if (picked != null) {
                if (picked.value != selected) haptics.selection()
                onSelect(picked.value)
            }
        }
    }

    ChoicePickerField(
        label = label,
        value = current?.label,
        placeholder = placeholder,
        onClick = openSheet,
        leading = when {
            current != null -> { { ChoiceIcon(current) } }
            emptyAvatar != null -> { { emptyAvatar(IconBadgeSize) } }
            emptyIcon != null -> { { if (emptyAsAvatar) NeutralAvatar(emptyIcon) else IconBadge(emptyIcon) } }
            else -> null
        },
        errorText = errorText,
        modifier = modifier,
    )
}

/** The size of the badge (or avatar) at the start of a selection field. */
private val IconBadgeSize = 40.dp

/**
 * An icon on a 40dp circular badge with a soft tonal background: the one icon container every selection field uses, for
 * anything but a category or a person - those are shown as the avatars the user chose the shape of (see [ChoiceOption.avatar]).
 */
@Composable
fun IconBadge(
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    size: Dp = IconBadgeSize,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    glyph: (@Composable () -> Unit)? = null,
) {
    Box(modifier.size(size).background(container, CircleShape), contentAlignment = Alignment.Center) {
        if (glyph != null) {
            glyph()
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/** A choice's mark: its own [ChoiceOption.avatar] (a category, a person), or its icon on the neutral tonal [IconBadge]. */
@Composable
private fun <K> ChoiceIcon(option: ChoiceOption<K>, size: Dp = IconBadgeSize) {
    val avatar = option.avatar
    if (avatar != null) avatar(size) else IconBadge(option.icon, size = size, glyph = option.glyph)
}

/**
 * Picks an account: a tonal selection card like the category's - the account's mark on its badge, its name, and the
 * whole list one tap away. [exclude] hides the other side of a transfer.
 */
@Composable
fun AccountSelector(
    label: String,
    accounts: List<Account>,
    selected: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    exclude: Long? = null,
    errorText: String? = null,
) {
    // An account's mark is on a badge in the shape the user chose for icon backgrounds, as a category's and a person's are.
    val options = accounts.filter { it.id != exclude }.map { account ->
        ChoiceOption(
            key = account.id!!,
            label = account.name,
            icon = account.kind.icon,
            avatar = { size -> AccountBadge(account, size = size) },
            supporting = account.kind.label,
        )
    }
    ChoiceDropdownField(
        label = label,
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        placeholder = "Choose an account",
        errorText = errorText,
        emptyAvatar = { size -> BankBadge(bank = null, fallback = Icons.Outlined.AccountBalanceWallet, size = size) },
    )
}

/**
 * The amount as the editor's focal point: big tabular figures (Display Large, as Home's hero figure) after a smaller currency
 * symbol in the type's [accent] colour, centred and floating free of any box or underline. While it is typed in a soft outline
 * appears in the accent, and the outline, the label and the message under it turn the error colour when the amount is invalid.
 * A tap anywhere in it starts typing, after the digits already there.
 *
 * The whole line - symbol, grouped digits, decimals - is one text the width of the field, so it is centred as one and shrinks
 * as one: a long amount gets a smaller font, never a sideways scroll. The digits are grouped as they are typed
 * ("12,34,567.50") while the text being edited stays plain digits. With nothing typed the line is the symbol and a faint "0"
 * with the cursor in front of it, and a typed point brings the decimals still to come in faintly, so what is drawn is always
 * the amount that will be saved. The keyboard's action is Done by default: it puts the keyboard away to show the rest of the
 * form, since what follows the amount is picked, not typed.
 */
@Composable
fun HeroAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: CurrencySpec,
    accent: StatusColorRole,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    label: String = "Amount",
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val typing = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val colorMotion = tween<Color>(AppMotion.SHORT_MS, easing = AppMotion.standard)
    // The type's colour follows the Expense / Income / Transfer choice instead of snapping to it.
    val tint by animateColorAsState(accent.color, colorMotion, label = "amount_tint")
    val outline by animateColorAsState(
        when {
            errorText != null -> scheme.error
            focused -> accent.color
            else -> Color.Transparent
        },
        colorMotion,
        label = "amount_outline",
    )

    // What is edited, with the cursor. It starts after the digits, so focusing the field by its label, around the figure or
    // on opening goes on typing at the end (a plain String field starts its cursor in front of the first digit); a value set
    // from outside (a bill chip, a picture read) puts the cursor after the new digits.
    var editing by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val current = if (editing.text == value) editing else TextFieldValue(value, TextRange(value.length))

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(2.dp, outline, AppRadius.rXxl)
                .pointerInput(Unit) {
                    detectTapGestures {
                        typing.requestFocus()
                        keyboard?.show()
                    }
                }
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                style = type.labelLarge,
                color = when {
                    errorText != null -> scheme.error
                    focused -> tint
                    else -> scheme.onSurfaceVariant
                },
            )
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = AppSpacing.xs)) {
                val hint = scheme.onSurfaceVariant.copy(alpha = 0.4f)
                val transformation = remember(currency, tint, hint) {
                    AmountTransformation(
                        prefix = currency.symbol.trim().let { if (it.isEmpty()) "" else "$it " },
                        indianGrouping = currency.indianGrouping,
                        prefixColor = tint,
                        prefixSize = AmountSymbolSize,
                        hintColor = hint,
                        decimals = currency.decimals,
                    )
                }
                val shown = transformation.filter(AnnotatedString(value)).text
                // The cursor and a few dp of air beyond the digits, so the text never touches the field's edge.
                val availablePx = constraints.maxWidth - with(density) { AmountEdgeAir.toPx() }
                val base = type.displayLarge.copy(
                    fontFeatureSettings = TABULAR_FIGURES,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = scheme.onSurface,
                )
                val style = rememberFittedAmountStyle(shown, base, availablePx)

                BasicTextField(
                    value = current,
                    onValueChange = { next ->
                        val kept = filterAmountInput(value, next.text, currency.decimals)
                        editing = when (kept) {
                            next.text -> next
                            value -> current // refused: stays as it was, cursor and all
                            else -> TextFieldValue(kept, TextRange(kept.length)) // tidied (a pasted "1,250")
                        }
                        if (kept != value) onValueChange(kept)
                    },
                    textStyle = style,
                    singleLine = true,
                    cursorBrush = SolidColor(tint),
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (currency.decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = imeAction,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(typing)
                        .onFocusChanged { focused = it.isFocused }
                        .semantics {
                            contentDescription = "$label in ${currency.code}"
                            if (errorText != null) error(errorText)
                        },
                )
            }
        }
        FieldError(errorText, textAlign = TextAlign.Center)
    }
}

/** Room kept clear at the end of the amount for the cursor, so the text never has to be scrolled to reveal it. */
private val AmountEdgeAir = 12.dp

/**
 * The currency symbol beside the amount's figures, as a share of their size: there, but the figures lead. It sits on their
 * baseline, which holds in every interface font (a symbol lifted to the figures' tops is placed by the font's ascent, which
 * differs from font to font).
 */
private val AmountSymbolSize = 0.6.em

/** A read-only tonal field that opens a picker (date, time); [shape] lets a pair read as one connected unit. */
@Composable
fun TonalPickerField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = TonalFieldShape,
    /** The mark at the end of the field (the dropdown arrow the other fields of a form show); none for a field that acts rather than picks. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(scheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "$label, $value"
                role = Role.Button
                semanticsOnClick { onClick(); true }
            }
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
    }
}

/** Corner pair for two side-by-side fields that read as one split pill: fully rounded outside, small where they meet. */
fun connectedFieldShapes(): Pair<RoundedCornerShape, RoundedCornerShape> {
    val large = CornerSize(50)
    val small = CornerSize(AppRadius.xs)
    return RoundedCornerShape(large, small, small, large) to RoundedCornerShape(small, large, large, small)
}

private const val NO_PERSON = -1L

/** The most of a saved name the "For" field shows; a longer one is cut so that " (me)" is never the part that gets cut. */
private const val MY_NAME_LIMIT = 24

/**
 * What the "For" field calls the user (2026-11-08, owner): "Asha (me)" when a name is saved in the app (the welcome tour's
 * profile step, Settings > Profile), a bare "Me" when there is none. The name says who "me" is and tells the user apart from a
 * person added with the same first name. The field is one line, and a line is cut at its end, so a long name is shortened
 * here, before " (me)" is added. Only the label changes: the expense is still on nobody.
 */
internal fun myLabel(profileName: String): String {
    val name = profileName.trim().replace(Regex("\\s+"), " ")
    if (name.isEmpty()) return "Me"
    if (name.length <= MY_NAME_LIMIT) return "$name (me)"
    var cut = MY_NAME_LIMIT - 1
    if (Character.isHighSurrogate(name[cut - 1])) cut-- // never leave half of an emoji
    return name.take(cut).trimEnd() + "… (me)"
}

/**
 * Who an expense was spent on: one dropdown (their avatar and name), with the user first - the usual answer, and the one an
 * expense has until it is given someone (2026-11-08, owner: by default it should be me; before, "No one in particular"; nothing
 * stored changed: an expense on nobody is still one with no person) - labelled by [myLabel] ("Asha (me)", or "Me" with no saved
 * name) - and "New person" at the end of the menu (when [onCreate] is given). [people] should hold the people that can be picked,
 * plus the one already chosen if they have since been archived.
 */
@Composable
fun PersonSelector(
    people: List<Person>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    onCreate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val me = myLabel(LocalAppPreferences.current?.state?.collectAsState()?.value?.profileName.orEmpty())
    val options = listOf(
        ChoiceOption(NO_PERSON, me, Icons.Outlined.Person, avatar = { size -> PersonAvatar(null, size = size) }),
    ) + people.map { person ->
        ChoiceOption(
            person.id!!,
            if (person.archived) "${person.name} (archived)" else person.name,
            Icons.Outlined.Person,
            avatar = { size -> PersonAvatar(person, size = size) },
        )
    }
    ChoiceDropdownField(
        label = "For",
        options = options,
        selected = selected ?: NO_PERSON,
        onSelect = { onSelect(if (it == NO_PERSON) null else it) },
        modifier = modifier,
        placeholder = me,
        createLabel = "New person",
        onCreate = onCreate,
        emptyIcon = Icons.Outlined.Person,
        emptyAsAvatar = true,
    )
}
