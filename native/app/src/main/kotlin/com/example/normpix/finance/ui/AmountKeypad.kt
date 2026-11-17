package com.example.normpix.finance.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.AmountExpression
import com.example.normpix.finance.domain.AmountKey
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusColorRole

/**
 * The app's own keypad for an amount (Settings > Finance > Typing an amount; owner, 2026-11-18), under the amount it types into in
 * place of the phone's keyboard. A calculator's layout - the signs down the right, Clear and Delete on top, Done where Enter would
 * be - so a sum ("120 + 45 + 30") is typed as on any calculator, and the field saves what it comes to.
 *
 * Digits sit on the form's own tonal fill, the signs on the type's colour (red for an expense, green for income) and Done in it,
 * as the Save button is. Each key is a pill that squares off a little while it is pressed (Material 3 Expressive's shape morph,
 * still with reduced motion), 52 dp tall, and taps the phone lightly - or buzzes "no" for a key that can't be used there (a second
 * point, a sign first). Holding Delete clears the amount. TalkBack reads each key by name ("Divide", "Delete").
 *
 * [onKey] does the key and says whether it could be used; [onDone] puts the keypad away.
 */
@Composable
internal fun AmountKeypadPanel(
    accent: StatusColorRole,
    decimals: Int,
    onKey: (AmountKey) -> Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val press: (AmountKey) -> Unit = { key ->
        haptics.performHapticFeedback(if (onKey(key)) HapticFeedbackType.KeyboardTap else HapticFeedbackType.Reject)
    }
    val digit = KeyTone(scheme.surfaceContainerHighest, scheme.onSurface)
    val tool = KeyTone(scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
    val sign = KeyTone(accent.container, accent.onContainer)
    val done = KeyTone(accent.color, accent.onColor)

    @Composable
    fun RowScope.Digit(n: Int) = TextKey(n.toString(), n.toString(), "digit_$n", digit) { press(AmountKey.Digit(n)) }

    @Composable
    fun RowScope.Sign(c: Char, spoken: String, tag: String) = TextKey(c.toString(), spoken, tag, sign, large = true) { press(AmountKey.Sign(c)) }

    Column(
        modifier.fillMaxWidth().semantics { isTraversalGroup = true },
        verticalArrangement = Arrangement.spacedBy(KeyGap),
    ) {
        KeyRow {
            TextKey("C", "Clear", "clear", tool) { press(AmountKey.Clear) }
            Sign(AmountExpression.DIVIDE, "Divide", "divide")
            Sign(AmountExpression.TIMES, "Multiply", "multiply")
            IconKey(
                Icons.AutoMirrored.Outlined.Backspace,
                spoken = "Delete",
                tag = "delete",
                tone = tool,
                onLongClick = { press(AmountKey.Clear) },
                longClickLabel = "Clear",
            ) { press(AmountKey.Backspace) }
        }
        KeyRow {
            Digit(7)
            Digit(8)
            Digit(9)
            Sign(AmountExpression.MINUS, "Minus", "minus")
        }
        KeyRow {
            Digit(4)
            Digit(5)
            Digit(6)
            Sign(AmountExpression.PLUS, "Plus", "plus")
        }
        KeyRow {
            Digit(1)
            Digit(2)
            Digit(3)
            TextKey("=", "Equals", "equals", sign, large = true) { press(AmountKey.Equals) }
        }
        KeyRow {
            TextKey(".", "Point", "point", digit, enabled = decimals > 0) { press(AmountKey.Point) }
            Digit(0)
            TextKey("00", "Double zero", "double_zero", digit) { press(AmountKey.DoubleZero) }
            IconKey(Icons.Rounded.Check, spoken = "Done", tag = "done", tone = done) {
                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                onDone()
            }
        }
    }
}

/** The test tag of the keypad key named [name] ("digit_7", "plus", "delete", "done"...). */
internal fun amountKeyTag(name: String) = "amount_key_$name"

private class KeyTone(val container: Color, val content: Color)

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap), content = content)
}

@Composable
private fun RowScope.TextKey(
    label: String,
    spoken: String,
    tag: String,
    tone: KeyTone,
    large: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    KeySurface(spoken, tag, tone, enabled, onClick = onClick) {
        val type = MaterialTheme.typography
        Text(
            label,
            style = (if (large) type.headlineMedium else type.headlineSmall).copy(fontFeatureSettings = TABULAR_FIGURES),
            fontWeight = FontWeight.Medium,
            color = tone.content,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun RowScope.IconKey(
    icon: ImageVector,
    spoken: String,
    tag: String,
    tone: KeyTone,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    onClick: () -> Unit,
) {
    KeySurface(spoken, tag, tone, enabled = true, onLongClick = onLongClick, longClickLabel = longClickLabel, onClick = onClick) {
        Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(24.dp))
    }
}

/** One key: a pill on [tone] that squares off a little while pressed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.KeySurface(
    spoken: String,
    tag: String,
    tone: KeyTone,
    enabled: Boolean,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) KeyPressedCorner else KeyHeight / 2, AppMotion.spatial<Dp>(), label = "key_corner")
    Box(
        Modifier
            .weight(1f)
            .height(KeyHeight)
            .clip(RoundedCornerShape(corner))
            .background(if (enabled) tone.container else tone.container.copy(alpha = 0.38f))
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics { contentDescription = spoken }
            .testTag(amountKeyTag(tag)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A key's height: above the 48 dp a target needs, and five rows still leave the form's next field in sight on a phone. */
private val KeyHeight = 52.dp
private val KeyGap = 8.dp

/** How round a key's corners are while it is held: from a full pill to a soft square. */
private val KeyPressedCorner = AppSpacing.md
