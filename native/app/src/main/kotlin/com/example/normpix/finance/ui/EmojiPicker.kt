package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.emojiIcon
import com.example.normpix.finance.domain.firstEmoji
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing

/**
 * Choosing an emoji as a category's icon. One field takes either a word - it searches the catalog ("pizza", "fuel") - or an
 * emoji itself, typed from the keyboard or pasted from anywhere, which is chosen as it lands (so an emoji that is not in the
 * catalog works as well as one that is). Under it, the catalog by group, or just the matches, drawn in the user's icon style
 * (in 3D, as 3D pictures).
 *
 * @param selected the emoji now chosen, if the category's icon is an emoji.
 */
@Composable
fun EmojiPicker(selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberAppHaptics()
    var text by remember { mutableStateOf("") }
    val query = text.trim()
    val typed = firstEmoji(query)
    val matches = if (query.isEmpty() || typed != null) null else remember(query) { searchEmoji(query) }
    val pick = { choice: EmojiChoice -> haptics.selection(); onSelect(choice.glyph) }

    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { entered ->
                text = entered
                firstEmoji(entered)?.let(onSelect)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search or paste an emoji") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
        Spacer(Modifier.height(AppSpacing.md))
        if (matches?.isEmpty() == true) {
            Text(
                "No emoji matches \"$query\". Paste or type the emoji itself in the field above.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = AppSpacing.sm),
            )
        } else {
            PickerGrid {
                if (matches == null) {
                    for (group in emojiCatalog) {
                        item(key = "group:${group.title}", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
                            EmojiGroupHeading(group.title)
                        }
                        items(group.emojis, key = { "${group.title}:${it.glyph}" }, contentType = { "emoji" }) { choice ->
                            EmojiCell(choice, selected = choice.glyph == selected, onClick = { pick(choice) })
                        }
                    }
                } else {
                    items(matches, key = { it.glyph }, contentType = { "emoji" }) { choice ->
                        EmojiCell(choice, selected = choice.glyph == selected, onClick = { pick(choice) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiGroupHeading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = AppSpacing.sm, bottom = AppSpacing.xs).semantics { heading() },
    )
}

@Composable
private fun EmojiCell(choice: EmojiChoice, selected: Boolean, onClick: () -> Unit) {
    PickerCell(choice.label, selected, onClick) {
        val art = rememberCategoryArt(emojiIcon(choice.glyph))
        CategoryArtView(art, LocalContentColor.current, pickerArtSize(art))
    }
}

/**
 * A picker's grid of [PickerCell]s: lazy, as there are hundreds to choose from, and some six rows tall at most - it scrolls
 * inside itself, so the sheet around it (which scrolls too) keeps its other fields within reach.
 */
@Composable
internal fun PickerGrid(content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.FixedSize(PICKER_CELL),
        modifier = Modifier.fillMaxWidth().heightIn(max = PICKER_GRID_HEIGHT),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        content = content,
    )
}

/** One choice in a [PickerGrid]: [content] on a disc, tonal while chosen; named [label] for a screen reader. */
@Composable
internal fun PickerCell(label: String, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(PICKER_CELL)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(scheme.secondaryContainer, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.RadioButton
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant) {
            content()
        }
    }
}

/**
 * How big [art] is drawn in a [PickerCell], in dp whatever the font scale (the cells are a grid): a logo nearly fills the
 * disc, so the chosen one shows a ring of tone; a 3D picture is larger than a flat icon, which reads bolder at the same size.
 */
internal fun pickerArtSize(art: CategoryArt): Dp = when (art) {
    is CategoryArt.Logo -> 40.dp
    is CategoryArt.Picture -> 32.dp
    is CategoryArt.Glyph -> 26.dp
    else -> 24.dp
}

private val PICKER_CELL = 48.dp
private val PICKER_GRID_HEIGHT = 48.dp * 6 + 4.dp * 5
