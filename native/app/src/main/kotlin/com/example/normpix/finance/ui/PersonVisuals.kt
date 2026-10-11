package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.emojiIcon

/** The letter a person without a picture is shown by: their name's first character, in capitals. */
fun personInitial(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
}

/**
 * A person's avatar: their emoji, or their initial, on the same tonal disc categories use - in the color they were given, so a
 * family of people reads at a glance. A null [person] (an expense for no one) is a neutral disc with a plain figure.
 */
@Composable
fun PersonAvatar(person: Person?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = person?.let { paletteColor(it.color) }
    AvatarDisc(colors, modifier, size) {
        if (person == null) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size / 2))
        } else {
            PersonGlyph(person, colors?.onContainer ?: MaterialTheme.colorScheme.onSurfaceVariant, size / 2)
        }
    }
}

/**
 * What goes on a person's avatar, without the disc (for places that bring a disc of their own, like a dropdown's badge): the
 * emoji they were given, or their initial in [tint]. Decorative: the person's name is what a screen reader says.
 */
@Composable
fun PersonGlyph(person: Person, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val emoji = person.emoji
    if (emoji != null) {
        CategoryGlyph(emojiIcon(emoji), tint, size, modifier)
        return
    }
    // `size` in dp whatever the user's font scale: an avatar does not grow with the text around it.
    val fontSize = with(LocalDensity.current) { (size * INITIAL_FILL).toSp() }
    Box(modifier.size(size).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Text(
            personInitial(person.name),
            style = TextStyle(
                color = tint,
                fontSize = fontSize,
                lineHeight = fontSize,
                fontWeight = FontWeight.SemiBold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** How much of its box an initial fills. */
private const val INITIAL_FILL = 0.9f
