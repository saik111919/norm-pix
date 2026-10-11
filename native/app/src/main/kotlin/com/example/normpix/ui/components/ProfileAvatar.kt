package com.example.normpix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp

/**
 * The letter the user is shown by when they have given a name and no emoji: the name's first letter or digit, in capitals.
 * Null when there is nothing to show (no name, or one that starts with a symbol), so the plain figure shows instead.
 */
fun profileInitial(name: String): String? {
    val first = name.trim().codePoints().findFirst()
    if (!first.isPresent || !Character.isLetterOrDigit(first.asInt)) return null
    return String(Character.toChars(first.asInt)).uppercase()
}

/**
 * The user's own picture: the emoji they chose, else their initial, else a plain figure - on the primary container, the
 * colour the account circle has always had. Decorative: whatever carries it says what it is.
 */
@Composable
fun ProfileAvatar(name: String, emoji: String, size: Dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val initial = if (emoji.isBlank()) profileInitial(name) else null
    Box(
        modifier.size(size).background(scheme.primaryContainer, CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        // Sized in dp whatever the user's font scale: an avatar is a picture, and does not grow with the text around it.
        val glyph = with(LocalDensity.current) { (size * if (emoji.isBlank()) INITIAL_FILL else EMOJI_FILL).toSp() }
        when {
            emoji.isNotBlank() -> Text(emoji, fontSize = glyph, lineHeight = glyph, maxLines = 1, softWrap = false)
            initial != null -> Text(
                initial,
                style = TextStyle(
                    color = scheme.onPrimaryContainer,
                    fontSize = glyph,
                    lineHeight = glyph,
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                maxLines = 1,
                softWrap = false,
            )
            else -> Icon(Icons.Outlined.Person, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(size * 0.6f))
        }
    }
}

/** How much of the circle an emoji, and an initial, fills. */
private const val EMOJI_FILL = 0.52f
private const val INITIAL_FILL = 0.46f
