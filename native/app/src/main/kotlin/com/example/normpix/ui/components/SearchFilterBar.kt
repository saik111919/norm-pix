package com.example.normpix.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius

private val BarHeight = 56.dp
private val CountSize = 24.dp

/**
 * A search field and the control that filters what it searches, as one piece: the field is the main part of Material 3's
 * split button - a pill, round at its outer end - and beside it, a hair apart, the filter button: a filter icon (or, while
 * something is filtered, how many) and a chevron, which opens [menu], the quick filters. The button is filled while it has
 * a count, so a list that has been narrowed never looks whole, and it is the same width either way: the field beside it
 * never resizes under the finger.
 *
 * @param filterCount how many filters are on; 0 for none.
 */
@Composable
fun SearchFilterBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    filterCount: Int,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    menu: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val focus = LocalFocusManager.current
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val filtering = filterCount > 0
    val colors = if (filtering) SplitButtonColors.filled() else SplitButtonColors.tonal()
    val outer = CornerSize(50)
    // As the split button's menu half: small where the halves meet, open under a finger, fully round while the menu is open.
    val innerCorner by animateDpAsState(
        when {
            menuExpanded -> BarHeight / 2
            pressed -> PressedInnerCorner
            else -> InnerCorner
        },
        AppMotion.spatial<Dp>(),
        label = "search_filter_corner",
    )
    val chevronTurn by animateFloatAsState(
        if (menuExpanded) 180f else 0f,
        tween(AppMotion.duration(AppMotion.MEDIUM_MS), easing = AppMotion.standard),
        label = "search_filter_chevron",
    )

    // At least the split button's height, and taller as the text grows: the filter segment takes the field's height, not its own.
    Row(
        modifier.heightIn(min = BarHeight).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(SplitGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            // Short, so it fits whole in the room the filter button leaves, even on a narrow phone with filters on.
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (value.isNotEmpty()) {
                {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            } else {
                null
            },
            singleLine = true,
            shape = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = CornerSize(InnerCorner), bottomEnd = CornerSize(InnerCorner)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = TextFieldDefaults.colors(
                // A step brighter while typing: the field has no outline, so this is its only sign of focus besides the cursor.
                focusedContainerColor = scheme.surfaceContainerHighest,
                unfocusedContainerColor = scheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Box {
            Surface(
                onClick = {
                    haptics.selection()
                    onMenuExpandedChange(!menuExpanded)
                },
                modifier = Modifier.fillMaxHeight().heightIn(min = BarHeight).semantics {
                    role = Role.Button
                    contentDescription = if (filtering) "Filters, $filterCount active" else "Filters"
                    stateDescription = if (menuExpanded) "Expanded" else "Collapsed"
                },
                shape = RoundedCornerShape(topStart = CornerSize(innerCorner), bottomStart = CornerSize(innerCorner), topEnd = outer, bottomEnd = outer),
                color = colors.container,
                contentColor = colors.content,
                interactionSource = press,
            ) {
                Row(
                    Modifier.padding(start = 18.dp, end = 12.dp).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (filtering) {
                        // The count takes the icon's place; the button's own description says how many.
                        Box(
                            Modifier.size(CountSize).background(scheme.onPrimary, CircleShape).clearAndSetSemantics { },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$filterCount", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                        }
                    } else {
                        Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(CountSize))
                    }
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = chevronTurn },
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
