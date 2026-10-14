package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.example.normpix.finance.domain.emojiIcon
import com.example.normpix.finance.domain.emojiOf
import com.example.normpix.ui.components.AppPrimaryTabs
import com.example.normpix.ui.components.AppTab
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing

/** The app's own icons, in the order the registry keeps them (the core ones first). */
private val appIconKeys: List<String> by lazy { categoryIcons.keys.filterNot(::isBrandIcon) }

/** Every brand, by name. */
private val brandIconKeys: List<String> by lazy {
    categoryIcons.keys.filter(::isBrandIcon).sortedBy { categoryIcons.getValue(it).label.lowercase() }
}

/**
 * Choosing a category's icon, in three tabs: the app's own icons (drawn in the user's icon style), brands (each by its logo
 * whatever the style - that is how they are told apart), and emoji. It opens on the tab [icon], the icon now chosen, is from.
 */
@Composable
internal fun CategoryIconPicker(icon: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var tab by remember {
        mutableIntStateOf(
            when {
                emojiOf(icon) != null -> EMOJI_TAB
                isBrandIcon(icon) -> BRANDS_TAB
                else -> ICONS_TAB
            },
        )
    }
    Column(modifier) {
        AppPrimaryTabs(
            tabs = remember { listOf(AppTab("Icons"), AppTab("Brands"), AppTab("Emoji")) },
            selectedIndex = tab,
            onSelect = { tab = it },
        )
        Spacer(Modifier.height(AppSpacing.md))
        when (tab) {
            ICONS_TAB -> KeyChoices(
                keys = appIconKeys,
                chosen = icon,
                noun = "icon",
                emptyHint = "Try another word, or pick an emoji.",
                onSelect = onSelect,
            ) { key ->
                val art = rememberCategoryArt(key)
                CategoryArtView(art, LocalContentColor.current, pickerArtSize(art))
            }
            BRANDS_TAB -> KeyChoices(
                keys = brandIconKeys,
                chosen = brandKey(icon) ?: icon,
                noun = "brand",
                emptyHint = "Try another spelling, or pick an icon or an emoji.",
                onSelect = onSelect,
            ) { key ->
                val art = remember(key) { brandPreviewArt(key) } ?: rememberCategoryArt(key)
                CategoryArtView(art, LocalContentColor.current, pickerArtSize(art))
            }
            else -> EmojiPicker(selected = emojiOf(icon), onSelect = { onSelect(emojiIcon(it)) })
        }
    }
}

/** A search field over [keys] (by label or key), and the matches in a grid, each drawn by [art]. */
@Composable
private fun KeyChoices(
    keys: List<String>,
    chosen: String,
    noun: String,
    emptyHint: String,
    onSelect: (String) -> Unit,
    art: @Composable (String) -> Unit,
) {
    val haptics = rememberAppHaptics()
    var text by remember { mutableStateOf("") }
    val query = text.trim()
    val matches = remember(keys, query) {
        if (query.isEmpty()) {
            keys
        } else {
            keys.filter { key -> key.contains(query, ignoreCase = true) || categoryIcons.getValue(key).label.contains(query, ignoreCase = true) }
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("${keys.size} ${noun}s") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        singleLine = true,
        shape = TonalFieldShape,
        colors = typedFieldColors(),
    )
    Spacer(Modifier.height(AppSpacing.sm))
    if (matches.isEmpty()) {
        Text(
            text = "No $noun is called \"$query\". $emptyHint",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    PickerGrid {
        items(matches, key = { it }) { key ->
            PickerCell(
                label = categoryIcons.getValue(key).label,
                selected = key == chosen,
                onClick = {
                    haptics.selection()
                    onSelect(key)
                },
            ) { art(key) }
        }
    }
}

private const val ICONS_TAB = 0
private const val BRANDS_TAB = 1
private const val EMOJI_TAB = 2
