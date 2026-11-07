package com.example.normpix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing

/**
 * One item in an [OptionSheet].
 */
data class OptionSheetItem<K>(
    val key: K,
    val label: String,
    val supporting: String? = null,
    val leading: (@Composable () -> Unit)? = null,
    val trailing: (@Composable () -> Unit)? = null,
    val enabled: Boolean = true,
)

/**
 * The standard "option sheet" for choices, filters, dropdowns and pickers:
 * - Header: optional leading icon (primary, 28 dp), title (headlineSmall, SemiBold, onSurface),
 *   and for multiple choice only, a 48 dp circular confirm button at the end.
 * - Optional subtitle (bodyMedium, onSurfaceVariant).
 * - Optional search field.
 * - Options grouped as rounded rows (outer 20 dp, inner 4 dp corners) separated by thin 4 dp gaps.
 *   Leading checkbox (multiple choice) or radio (single choice), label (selected: primary & SemiBold).
 * - Optional action pill at the bottom: 56 dp tall full-width tonal pill button.
 */
@Composable
fun <K> OptionSheet(
    title: String,
    options: List<OptionSheetItem<K>>,
    selected: Set<K>,
    modifier: Modifier = Modifier,
    onSelect: (K) -> Unit = {},
    subtitle: String? = null,
    headerIcon: ImageVector? = null,
    multiple: Boolean = false,
    onConfirm: ((Set<K>) -> Unit)? = null,
    confirmDescription: String? = null,
    searchPlaceholder: String? = null,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var searchQuery by remember { mutableStateOf("") }
    var currentSelected by remember(selected) { mutableStateOf(selected) }

    val filteredOptions = remember(options, searchQuery) {
        if (searchQuery.isBlank()) {
            options
        } else {
            val q = searchQuery.trim().lowercase()
            options.filter {
                it.label.lowercase().contains(q) || it.supporting?.lowercase()?.contains(q) == true
            }
        }
    }

    val containerColor = scheme.surfaceContainerHigh
    val selectedLabelColor = readableOn(containerColor, scheme.primary, scheme.onSurface)

    Column(modifier = modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppSpacing.xxl,
                    end = AppSpacing.xxl,
                    top = AppSpacing.lg,
                    bottom = if (searchPlaceholder != null) AppSpacing.sm else AppSpacing.md,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (headerIcon != null) {
                Icon(
                    headerIcon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier
                        .padding(end = AppSpacing.md)
                        .size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                val resolvedSubtitle = if (multiple && subtitle == null) {
                    "${currentSelected.size} selected"
                } else {
                    subtitle
                }
                if (resolvedSubtitle != null) {
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(
                        resolvedSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (multiple) {
                FilledIconButton(
                    onClick = { onConfirm?.invoke(currentSelected) },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                    ),
                    modifier = Modifier
                        .padding(start = AppSpacing.sm)
                        .size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = confirmDescription ?: "Apply (${currentSelected.size})",
                    )
                }
            }
        }

        // Optional search field
        if (searchPlaceholder != null) {
            SearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = searchPlaceholder,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.md),
            )
        }

        // Options list
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = AppSpacing.xxl,
                end = AppSpacing.xxl,
                bottom = if (actionLabel != null && onAction != null) AppSpacing.sm else AppSpacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            itemsIndexed(filteredOptions, key = { _, item -> item.key.toString() }) { index, item ->
                val isFirst = index == 0
                val isLast = index == filteredOptions.lastIndex
                val isSelected = item.key in currentSelected

                ListSegment(
                    isFirst = isFirst,
                    isLast = isLast,
                    color = containerColor,
                ) {
                    val dim = if (item.enabled) 1f else 0.38f
                    ListItem(
                        modifier = Modifier
                            .selectable(
                                selected = isSelected,
                                enabled = item.enabled,
                                role = if (multiple) Role.Checkbox else Role.RadioButton,
                                onClick = {
                                    if (multiple) {
                                        currentSelected = if (isSelected) currentSelected - item.key else currentSelected + item.key
                                    }
                                    onSelect(item.key)
                                },
                            ),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            ) {
                                if (multiple) {
                                    androidx.compose.material3.Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null,
                                        enabled = item.enabled,
                                    )
                                } else {
                                    androidx.compose.material3.RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        enabled = item.enabled,
                                    )
                                }
                                item.leading?.invoke()
                            }
                        },
                        headlineContent = {
                            Text(
                                item.label,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) selectedLabelColor else scheme.onSurface,
                                modifier = Modifier.alpha(dim),
                            )
                        },
                        supportingContent = item.supporting?.let {
                            { Text(it, modifier = Modifier.alpha(dim)) }
                        },
                        trailingContent = item.trailing,
                    )
                }
            }
        }

        // Optional bottom action pill
        if (actionLabel != null && onAction != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpacing.xxl, end = AppSpacing.xxl, top = AppSpacing.sm, bottom = AppSpacing.md),
            ) {
                FilledTonalButton(
                    onClick = onAction,
                    shape = AppRadius.rFull,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    if (actionIcon != null) {
                        Icon(actionIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(AppSpacing.sm))
                    }
                    Text(actionLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
