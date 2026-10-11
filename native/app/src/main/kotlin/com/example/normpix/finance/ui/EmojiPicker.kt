package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.firstEmoji
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing

/**
 * Choosing an emoji as a category's icon. One field takes either a word - it searches the catalog ("pizza", "fuel") - or an
 * emoji itself, typed from the keyboard or pasted from anywhere, which is chosen as it lands (so an emoji that is not in the
 * catalog works as well as one that is). Under it, the catalog by group, or just the matches.
 *
 * @param selected the emoji now chosen, if the category's icon is an emoji.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiPicker(selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberAppHaptics()
    var text by remember { mutableStateOf("") }
    val query = text.trim()
    val typed = firstEmoji(query)
    val matches = if (query.isEmpty() || typed != null) null else searchEmoji(query)

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
        when {
            matches == null -> for (group in emojiCatalog) {
                EmojiGroupHeading(group.title)
                EmojiGrid(group.emojis, selected) { haptics.selection(); onSelect(it.glyph) }
            }
            matches.isEmpty() -> Text(
                "No emoji matches \"$query\". Paste or type the emoji itself in the field above.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = AppSpacing.sm),
            )
            else -> EmojiGrid(matches, selected) { haptics.selection(); onSelect(it.glyph) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiGrid(emojis: List<EmojiChoice>, selected: String?, onPick: (EmojiChoice) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        for (choice in emojis) EmojiCell(choice, selected = choice.glyph == selected, onClick = { onPick(choice) })
    }
}

/** One emoji on a 44dp disc, tonal while chosen; named by its label for a screen reader. */
@Composable
private fun EmojiCell(choice: EmojiChoice, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = choice.label
                role = Role.RadioButton
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        // 24dp whatever the font scale: the cells are a grid, and a larger font would overflow them.
        Text(choice.glyph, fontSize = with(LocalDensity.current) { 24.dp.toSp() }, maxLines = 1, softWrap = false)
    }
}
