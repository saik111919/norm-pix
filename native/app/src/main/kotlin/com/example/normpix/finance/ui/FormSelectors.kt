package com.example.normpix.finance.ui

import com.example.normpix.ui.components.solidColorScheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.example.normpix.banks.ui.AccountMark
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.emojiOf
import com.example.normpix.finance.domain.inTreeOrder
import com.example.normpix.finance.domain.pathName
import com.example.normpix.finance.domain.TransactionType
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
 * One choice of a [ConnectedChoiceRow] or [ChoiceDropdownField]. [tint] gives a category-style tonal icon badge;
 * [glyph] replaces the icon with other artwork (a bank's logo) in the dropdown.
 */
@Immutable
class ChoiceOption<K>(
    val key: K,
    val label: String,
    val icon: ImageVector,
    val tint: PaletteColors? = null,
    val glyph: (@Composable () -> Unit)? = null,
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
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.key == selected }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        TextField(
            value = current?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            leadingIcon = when {
                current != null -> { { ChoiceIcon(current) } }
                emptyIcon != null -> { { IconBadge(emptyIcon) } }
                else -> null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = errorText != null,
            supportingText = errorText?.let { { Text(it) } },
            colors = tonalFieldColors(),
            shape = TonalFieldShape,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // A popup over the sheet: nothing behind it is blurred, so it keeps the solid tone.
            containerColor = solidColorScheme.surfaceContainer,
            shape = AppRadius.rLg,
        ) {
            for (option in options) {
                val isSelected = option.key == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = { ChoiceIcon(option) },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.primary) }
                    } else {
                        null
                    },
                    onClick = {
                        if (!isSelected) haptics.selection()
                        onSelect(option.key)
                        expanded = false
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .then(if (isSelected) Modifier.background(scheme.secondaryContainer.copy(alpha = 0.5f)) else Modifier)
                        .semantics { this.selected = isSelected },
                )
            }
            if (onCreate != null && createLabel != null) {
                HorizontalDivider(Modifier.padding(vertical = AppSpacing.xs))
                DropdownMenuItem(
                    text = { Text(createLabel, color = scheme.primary) },
                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, tint = scheme.primary) },
                    onClick = {
                        expanded = false
                        onCreate()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

/** An icon on a 40dp circular badge with a soft tonal background: the one icon container every selection field uses. */
@Composable
fun IconBadge(
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    glyph: (@Composable () -> Unit)? = null,
) {
    Box(modifier.size(40.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
        if (glyph != null) {
            glyph()
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        }
    }
}

/** A choice's icon as a [IconBadge]: in its own palette (a category), or the neutral tonal one. */
@Composable
private fun <K> ChoiceIcon(option: ChoiceOption<K>) {
    val scheme = MaterialTheme.colorScheme
    val tint = option.tint
    IconBadge(
        option.icon,
        container = tint?.container ?: scheme.secondaryContainer,
        content = tint?.onContainer ?: scheme.onSecondaryContainer,
        glyph = option.glyph,
    )
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
    val markTint = MaterialTheme.colorScheme.onSurfaceVariant
    val options = accounts.filter { it.id != exclude }.map { account ->
        ChoiceOption(account.id!!, account.name, account.kind.icon, glyph = { AccountMark(account, markTint, 24.dp) })
    }
    ChoiceDropdownField(
        label = label,
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        placeholder = "Choose an account",
        errorText = errorText,
        emptyIcon = Icons.Outlined.AccountBalanceWallet,
    )
}

/**
 * The amount as the editor's focal point: the currency symbol and big tabular figures (Display Medium), centred and floating
 * free of any box or underline. While it is typed in a soft outline appears in the type's [accent] colour, and in the error
 * colour when the amount is invalid. A tap anywhere in it starts typing.
 *
 * The whole line - symbol, grouped digits, decimals - is one text the width of the field, so it is centred as one and shrinks
 * as one: a long amount gets a smaller font, never a sideways scroll. The digits are grouped as they are typed
 * ("12,34,567.50") while the text being edited stays plain digits.
 */
@Composable
fun HeroAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: CurrencySpec,
    accent: StatusColorRole,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    label: String = "Amount",
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current

    val typing = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val outline by animateColorAsState(
        when {
            errorText != null -> scheme.error
            focused -> accent.color
            else -> Color.Transparent
        },
        tween(AppMotion.SHORT_MS, easing = AppMotion.standard),
        label = "amount_outline",
    )

    Column(modifier.fillMaxWidth()) {
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
            Text(label, style = type.labelLarge, color = if (focused) accent.color else scheme.onSurfaceVariant)
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = AppSpacing.xs)) {
                val transformation = remember(currency, accent.color) {
                    AmountTransformation(
                        prefix = currency.symbol.trim().let { if (it.isEmpty()) "" else "$it " },
                        indianGrouping = currency.indianGrouping,
                        prefixColor = accent.color,
                    )
                }
                val placeholder = amountPlaceholder(transformation.prefix, currency.decimals)
                val shown = if (value.isEmpty()) placeholder else transformation.filter(AnnotatedString(value)).text.text
                // The cursor and a few dp of air beyond the digits, so the text never touches the field's edge.
                val availablePx = constraints.maxWidth - with(density) { AmountEdgeAir.toPx() }
                val base = type.displayMedium.copy(
                    fontFeatureSettings = TABULAR_FIGURES,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = scheme.onSurface,
                )
                val style = rememberFittedAmountStyle(shown, base, availablePx)

                BasicTextField(
                    value = value,
                    onValueChange = { onValueChange(filterAmountInput(value, it, currency.decimals)) },
                    textStyle = style,
                    singleLine = true,
                    cursorBrush = SolidColor(accent.color),
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (currency.decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = imeAction,
                    ),
                    decorationBox = { field ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (value.isEmpty()) {
                                Text(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(color = accent.color.copy(alpha = 0.45f))) { append(transformation.prefix) }
                                        append(placeholder.removePrefix(transformation.prefix))
                                    },
                                    style = style.copy(color = scheme.onSurfaceVariant.copy(alpha = 0.4f)),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            field()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(typing)
                        .onFocusChanged { focused = it.isFocused }
                        .semantics { contentDescription = "$label in ${currency.code}" },
                )
            }
        }
        FieldError(errorText)
    }
}

/** Room kept clear at the end of the amount for the cursor, so the text never has to be scrolled to reveal it. */
private val AmountEdgeAir = 12.dp

/** A read-only tonal field that opens a picker (date, time); [shape] lets a pair read as one connected unit. */
@Composable
fun TonalPickerField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = TonalFieldShape,
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

/**
 * Who an expense was spent on: one dropdown (their avatar and name), with "No one in particular" first - the usual answer, and
 * the one an expense has until it is given someone - and "New person" at the end of the menu (when [onCreate] is given).
 * [people] should hold the people that can be picked, plus the one already chosen if they have since been archived.
 */
@Composable
fun PersonSelector(
    people: List<Person>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    onCreate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val options = listOf(ChoiceOption(NO_PERSON, "No one in particular", Icons.Outlined.Person)) + people.map { person ->
        val tone = paletteColor(person.color)
        ChoiceOption(
            person.id!!,
            if (person.archived) "${person.name} (archived)" else person.name,
            Icons.Outlined.Person,
            tone,
            glyph = { PersonGlyph(person, tone.onContainer, 22.dp) },
        )
    }
    ChoiceDropdownField(
        label = "For",
        options = options,
        selected = selected ?: NO_PERSON,
        onSelect = { onSelect(if (it == NO_PERSON) null else it) },
        modifier = modifier,
        placeholder = "No one in particular",
        createLabel = "New person",
        onCreate = onCreate,
        emptyIcon = Icons.Outlined.Person,
    )
}
