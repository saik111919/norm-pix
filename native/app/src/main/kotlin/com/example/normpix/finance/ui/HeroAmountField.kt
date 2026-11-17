package com.example.normpix.finance.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.normpix.finance.domain.AmountEdit
import com.example.normpix.finance.domain.AmountExpression
import com.example.normpix.finance.domain.AmountKey
import com.example.normpix.finance.domain.AmountOutcome
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.applyAmountKey
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.settings.AmountKeypad
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.ui.components.politeLiveRegion
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusColorRole
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

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
 * the amount that will be saved.
 *
 * **A calculator** (2026-11-18): the amount can be typed as a sum - "120 + 45 + 30", "1500 ÷ 3" - drawn with its signs in the
 * accent, and what it comes to is said under it ("= ₹195"); the form is handed that total as it is typed, and the sum itself turns
 * into it when the amount is left, Done or = is pressed. A sum that can't be an amount (÷ 0, below zero) says why, in place of a total,
 * and hands the form nothing, so it can't be saved as it is.
 *
 * **What it is typed with** follows Settings > Finance > Typing an amount ([AmountKeypad]): the app's own keypad
 * ([AmountKeypadPanel], the default) opens under the amount while it is being typed in, and the phone's keyboard does not come up
 * for it at all ([InterceptPlatformTextInput]: the field keeps its cursor and focus, and only the request for the keyboard is held
 * back); Done or Back puts the keypad away. With the phone's keyboard chosen, the keyboard's own number pad comes up as before, its
 * action is Done ([imeAction]) - it puts the keyboard away to show the rest of the form, since what follows the amount is picked,
 * not typed - and the signs it has (most number pads have - at least) work as the keypad's do.
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
    val decimals = currency.decimals
    val ownKeypad = (LocalAppPreferences.current?.state?.collectAsState()?.value?.amountKeypad ?: AmountKeypad.App) == AmountKeypad.App

    val typing = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    // The keypad is up from the amount being typed in until it is put away (Done, Back, leaving the amount) and up again at a tap on
    // the amount. Not the focus alone: clearing focus can hand it straight back to the first field on the screen - this one - which
    // must not bring the keypad back the moment Done put it away (seen in a test, 2026-11-18).
    var keypadUp by remember { mutableStateOf(false) }
    var putAwayAt by remember { mutableLongStateOf(Long.MIN_VALUE / 2) }
    // Counts the taps that opened the keypad: each puts the cursor after the digits once it has been handled.
    var cursorToEnd by remember { mutableIntStateOf(0) }

    // What is edited, with the cursor: the amount, or a sum that comes to it. It starts after the digits, so focusing the field by its
    // label, around the figure or on opening goes on typing at the end (a plain String field starts its cursor in front of the first
    // digit). The form holds what the text comes to, so the text is this field's own while it still comes to that; anything else is a
    // value set from outside (a bill chip, a picture read), which puts the cursor after its digits.
    var editing by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val current = if (AmountExpression.formValue(editing.text, decimals) == value) editing else TextFieldValue(value, TextRange(value.length))
    val outcome = if (AmountExpression.isSum(current.text)) AmountExpression.evaluate(current.text, decimals) else null
    val problem = (outcome as? AmountOutcome.Problem)?.message
    val shownError = problem ?: errorText
    val money = remember(currency) { MoneyFormatter(currency) }
    // The line under a sum: what it comes to, or why it can't be an amount.
    val sumLine = when (outcome) {
        is AmountOutcome.Value -> "= ${money.format(outcome.minor, trimZeroFraction = true)}"
        is AmountOutcome.Problem -> outcome.message
        else -> null
    }

    val colorMotion = tween<Color>(AppMotion.SHORT_MS, easing = AppMotion.standard)
    // The type's colour follows the Expense / Income / Transfer choice instead of snapping to it.
    val tint by animateColorAsState(accent.color, colorMotion, label = "amount_tint")
    val outline by animateColorAsState(
        when {
            shownError != null -> scheme.error
            focused -> accent.color
            else -> Color.Transparent
        },
        colorMotion,
        label = "amount_outline",
    )

    fun take(next: TextFieldValue) {
        editing = next
        val handed = AmountExpression.formValue(next.text, decimals)
        if (handed != value) onValueChange(handed)
    }

    fun press(key: AmountKey): Boolean {
        val edit = applyAmountKey(key, AmountEdit(current.text, current.selection.start, current.selection.end), decimals) ?: return false
        take(TextFieldValue(edit.text, TextRange(edit.start, edit.end)))
        return true
    }

    // A sum becomes what it comes to once the amount is left; one that can't be an amount stays as typed, with its reason, to be put right.
    fun settle() {
        if (AmountExpression.isSum(current.text)) press(AmountKey.Equals)
    }

    fun done() {
        settle()
        keypadUp = false
        putAwayAt = SystemClock.uptimeMillis()
        focusManager.clearFocus()
    }

    val showKeypad = ownKeypad && focused && keypadUp
    // Back puts the keypad away first, as it does the phone's keyboard; the form's own Back (asking before it closes) is next.
    BackHandler(enabled = showKeypad) { done() }
    // The keyboard's request is held back while the app's keypad is the choice: the field keeps its focus and cursor all the same.
    val interceptor = remember(ownKeypad) {
        PlatformTextInputInterceptor { request, nextHandler ->
            if (ownKeypad) awaitCancellation() else nextHandler.startInputMethod(request)
        }
    }
    // After the tap that opened the keypad has been handled (the text field puts the cursor where the finger was on the way up), the
    // cursor goes after the digits.
    val latest by rememberUpdatedState(current)
    LaunchedEffect(cursorToEnd) {
        if (cursorToEnd == 0) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val now = latest
        editing = now.copy(selection = TextRange(now.text.length))
    }
    // The amount and the keypad under it are brought into sight together when the keypad opens.
    val inSight = remember { BringIntoViewRequester() }
    LaunchedEffect(showKeypad) {
        if (showKeypad) {
            // The phone's keyboard may still be up from a field typed in before (the payee): the keypad takes its place.
            keyboard?.hide()
            delay(AppMotion.SHORT_MS.toLong())
            inSight.bringIntoView()
        }
    }

    Column(modifier.fillMaxWidth().bringIntoViewRequester(inSight), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(2.dp, outline, AppRadius.rXxl)
                // A tap anywhere on the amount - the digits too, which take the tap to place the cursor - brings the keypad up, and one
                // that brings it up goes on typing after the digits: the figures are centred, so a tap lands mid-number and the next
                // digit went in the middle ("5480" for 480 then 5 - seen in a test, 2026-11-18). Taps while it is up place the cursor.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        if (awaitTap(viewConfiguration.touchSlop) && !keypadUp) {
                            keypadUp = true
                            cursorToEnd++
                        }
                    }
                }
                .pointerInput(ownKeypad) {
                    detectTapGestures {
                        typing.requestFocus()
                        if (!ownKeypad) keyboard?.show()
                    }
                }
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                style = type.labelLarge,
                color = when {
                    shownError != null -> scheme.error
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
                        decimals = decimals,
                    )
                }
                val shown = transformation.filter(AnnotatedString(current.text)).text
                // The cursor and a few dp of air beyond the digits, so the text never touches the field's edge.
                val availablePx = constraints.maxWidth - with(density) { AmountEdgeAir.toPx() }
                val base = type.displayLarge.copy(
                    fontFeatureSettings = TABULAR_FIGURES,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = scheme.onSurface,
                )
                val style = rememberFittedAmountStyle(shown, base, availablePx)
                // The line keeps its full-size height as a long amount or sum is drawn smaller, so nothing under it - the keypad's keys -
                // moves while it is typed (a long sum moved them 20 px on the emulator, 2026-11-18); the smaller figures sit in its middle.
                val measurer = rememberTextMeasurer()
                val fullLineHeight = remember(base, measurer) {
                    with(density) { measurer.measure(AnnotatedString("0"), base, maxLines = 1).size.height.toDp() }
                }

                InterceptPlatformTextInput(interceptor) {
                    BasicTextField(
                        value = current,
                        onValueChange = { next ->
                            val kept = AmountExpression.filter(current.text, next.text, decimals)
                            take(
                                when (kept) {
                                    next.text -> next
                                    current.text -> current // refused: stays as it was, cursor and all
                                    else -> TextFieldValue(kept, TextRange(kept.length)) // tidied (a pasted "1,250", a keyboard's "-")
                                },
                            )
                        },
                        textStyle = style,
                        singleLine = true,
                        cursorBrush = SolidColor(tint),
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
                            imeAction = imeAction,
                        ),
                        keyboardActions = KeyboardActions(onDone = { done() }),
                        decorationBox = { inner -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { inner() } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = fullLineHeight)
                            .focusRequester(typing)
                            .onFocusChanged {
                                if (focused && !it.isFocused) {
                                    settle()
                                    keypadUp = false
                                }
                                // Typed in (a tap, the form asking for the amount): the keypad comes up - unless this is the focus
                                // coming straight back as Done cleared it.
                                if (!focused && it.isFocused && SystemClock.uptimeMillis() - putAwayAt > REFOCUS_AFTER_DONE_MS) keypadUp = true
                                focused = it.isFocused
                            }
                            .semantics {
                                contentDescription = "$label in ${currency.code}"
                                if (outcome is AmountOutcome.Value) stateDescription = "Comes to ${money.format(outcome.minor, trimZeroFraction = true)}"
                                if (shownError != null) error(shownError)
                            },
                    )
                }
            }
            // What a sum comes to, or why it can't be an amount, under it. While the keypad is up the line keeps its place shown or not,
            // so the keys never move under a finger: the total coming in with the first sign pushed the whole keypad down a line, and
            // the next tap landed between two keys (seen on the emulator, 2026-11-18).
            val line: @Composable (String, Modifier) -> Unit = { text, lineModifier ->
                Text(
                    text,
                    style = type.titleMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = if (problem != null) scheme.error else tint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = lineModifier.fillMaxWidth().padding(top = AppSpacing.xs),
                )
            }
            if (showKeypad) {
                line(
                    sumLine ?: " ",
                    if (sumLine != null) Modifier.politeLiveRegion() else Modifier.alpha(0f).clearAndSetSemantics { },
                )
            } else {
                RevealVisibility(sumLine != null) { line(sumLine.orEmpty(), Modifier.politeLiveRegion()) }
            }
        }
        // The form's own message (a sum's is on the line above, inside the field).
        FieldError(errorText.takeIf { problem == null }, textAlign = TextAlign.Center)
        RevealVisibility(showKeypad) {
            Column {
                Spacer(Modifier.height(AppSpacing.lg))
                AmountKeypadPanel(accent = accent, decimals = decimals, onKey = ::press, onDone = ::done)
            }
        }
    }
}

/** How long after Done the focus coming back to the amount is taken as the platform handing it back, not as the user typing in. */
private const val REFOCUS_AFTER_DONE_MS = 400L

/**
 * Watches one gesture without taking it (the initial pass, before the text field places its cursor): true when it was a tap - let go
 * where it went down, within [slop] - and false for a drag (the form being scrolled) or a gesture cancelled.
 */
private suspend fun AwaitPointerEventScope.awaitTap(slop: Float): Boolean {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return false
        if ((change.position - down.position).getDistance() > slop) return false
        if (!change.pressed) return true
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
