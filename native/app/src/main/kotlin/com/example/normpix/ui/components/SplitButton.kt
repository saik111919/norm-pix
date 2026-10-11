package com.example.normpix.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius

/** The two sizes of Material 3's split button this app uses (the spec's small and medium). */
enum class SplitButtonSize(
    val height: Dp,
    /** Space before and after the leading button's content. */
    val leadingStart: Dp,
    val leadingEnd: Dp,
    val chevron: Dp,
    /** Space either side of the chevron. */
    val trailingPadding: Dp,
    val icon: Dp,
) {
    Small(height = 40.dp, leadingStart = 16.dp, leadingEnd = 12.dp, chevron = 22.dp, trailingPadding = 13.dp, icon = 20.dp),
    Medium(height = 56.dp, leadingStart = 24.dp, leadingEnd = 24.dp, chevron = 26.dp, trailingPadding = 15.dp, icon = 24.dp),
}

/** The container and content colours of a split button. */
class SplitButtonColors(val container: Color, val content: Color) {
    companion object {
        @Composable
        fun tonal() = MaterialTheme.colorScheme.let { SplitButtonColors(it.secondaryContainer, it.onSecondaryContainer) }

        @Composable
        fun filled() = MaterialTheme.colorScheme.let { SplitButtonColors(it.primary, it.onPrimary) }
    }
}

/** Between the two buttons, and the corner where they meet (the spec's "between space" and extra-small corner). */
internal val SplitGap = 2.dp
internal val InnerCorner = 4.dp

/** The corner a pressed button's inner edge opens to (the spec's medium corner). */
internal val PressedInnerCorner = 12.dp

/**
 * Material 3's split button: a main action, and beside it - a hair apart - a button that opens a menu of related ones. The
 * outer ends are fully round; the corners where the two meet are small, open up a little under a finger, and the menu
 * button goes fully round while its menu is open, its chevron turned over.
 *
 * @param leading the main button's content (an icon, and a label if it has one).
 * @param menu the items of the menu the chevron opens.
 */
@Composable
fun AppSplitButton(
    onClick: () -> Unit,
    leadingDescription: String,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: SplitButtonSize = SplitButtonSize.Medium,
    colors: SplitButtonColors = SplitButtonColors.tonal(),
    menuDescription: String = "More options",
    leading: @Composable RowScope.() -> Unit,
    menu: @Composable ColumnScope.() -> Unit,
) {
    val haptics = rememberAppHaptics()
    val leadingPress = remember { MutableInteractionSource() }
    val trailingPress = remember { MutableInteractionSource() }
    val leadingPressed by leadingPress.collectIsPressedAsState()
    val trailingPressed by trailingPress.collectIsPressedAsState()
    val outer = CornerSize(50)
    val spec = AppMotion.spatial<Dp>()
    val leadingInner by animateDpAsState(if (leadingPressed) PressedInnerCorner else InnerCorner, spec, label = "split_leading_corner")
    // Open, the menu button is as round as it can be: a circle (or a pill, where it is wider than tall).
    val trailingInner by animateDpAsState(
        when {
            menuExpanded -> size.height / 2
            trailingPressed -> PressedInnerCorner
            else -> InnerCorner
        },
        spec,
        label = "split_trailing_corner",
    )
    val chevronTurn by animateFloatAsState(
        if (menuExpanded) 180f else 0f,
        tween(AppMotion.duration(AppMotion.MEDIUM_MS), easing = AppMotion.standard),
        label = "split_chevron",
    )

    Row(modifier.height(size.height), horizontalArrangement = Arrangement.spacedBy(SplitGap), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = {
                haptics.selection()
                onClick()
            },
            modifier = Modifier.height(size.height).semantics {
                role = Role.Button
                contentDescription = leadingDescription
            },
            shape = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = CornerSize(leadingInner), bottomEnd = CornerSize(leadingInner)),
            color = colors.container,
            contentColor = colors.content,
            interactionSource = leadingPress,
        ) {
            Row(
                Modifier.padding(start = size.leadingStart, end = size.leadingEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                content = leading,
            )
        }
        Box {
            Surface(
                onClick = {
                    haptics.selection()
                    onMenuExpandedChange(!menuExpanded)
                },
                modifier = Modifier.height(size.height).semantics {
                    role = Role.Button
                    contentDescription = menuDescription
                    stateDescription = if (menuExpanded) "Expanded" else "Collapsed"
                },
                shape = RoundedCornerShape(topStart = CornerSize(trailingInner), bottomStart = CornerSize(trailingInner), topEnd = outer, bottomEnd = outer),
                color = colors.container,
                contentColor = colors.content,
                interactionSource = trailingPress,
            ) {
                Box(Modifier.padding(horizontal = size.trailingPadding).height(size.height), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(size.chevron).graphicsLayer { rotationZ = chevronTurn },
                    )
                }
            }
            // The app's menu look: solid tone (nothing behind a popup is blurred) and the large radius.
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) },
                containerColor = solidColorScheme.surfaceContainer,
                shape = AppRadius.rLg,
                content = menu,
            )
        }
    }
}
