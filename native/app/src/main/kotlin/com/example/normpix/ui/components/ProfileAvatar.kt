package com.example.normpix.ui.components

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.example.normpix.settings.ProfilePhotos

/**
 * The letter the user is shown by when they have given a name and no emoji: the name's first letter or digit, in capitals.
 * Null when there is nothing to show (no name, or one that starts with a symbol), so the plain figure shows instead.
 */
fun profileInitial(name: String): String? {
    val first = name.trim().codePoints().findFirst()
    if (!first.isPresent || !Character.isLetterOrDigit(first.asInt)) return null
    return String(Character.toChars(first.asInt)).uppercase()
}

/** Marks an avatar that is showing the user's own photo, for tests (an avatar is otherwise decorative and says nothing). */
const val PROFILE_PHOTO_TAG = "profile_photo"

/**
 * The photos already read, by file name. A photo is never changed once saved - a new one gets a new name - so a name always
 * means the same picture, and a few entries are enough: the current photo, and the one just replaced while it fades.
 */
private val photoCache = LruCache<String, ImageBitmap>(4)

/** The user's photo called [name] (see [ProfilePhotos]), once it has been read; null for no photo, while it loads, or if it can't be read. */
@Composable
private fun rememberProfilePhoto(name: String): ImageBitmap? {
    if (name.isBlank()) return null
    val context = LocalContext.current.applicationContext
    val photo by produceState(photoCache.get(name), name) {
        value = photoCache.get(name) ?: ProfilePhotos(context).read(name)?.asImageBitmap()?.also { photoCache.put(name, it) }
    }
    return photo
}

/**
 * The user's own picture: the photo they gave, else the emoji they chose, else their initial, else a plain figure - on the
 * primary container, the colour the account circle has always had. Whatever stands in while a photo is being read is the
 * emoji or initial, so the circle is never empty. Decorative: whatever carries it says what it is.
 */
@Composable
fun ProfileAvatar(name: String, emoji: String, size: Dp, modifier: Modifier = Modifier, photo: String = "") {
    val scheme = MaterialTheme.colorScheme
    val picture = rememberProfilePhoto(photo)
    val initial = if (emoji.isBlank()) profileInitial(name) else null
    Box(
        modifier.size(size).background(scheme.primaryContainer, CircleShape).clip(CircleShape).clearAndSetSemantics {
            if (picture != null) testTag = PROFILE_PHOTO_TAG
        },
        contentAlignment = Alignment.Center,
    ) {
        // Sized in dp whatever the user's font scale: an avatar is a picture, and does not grow with the text around it.
        val glyph = with(LocalDensity.current) { (size * if (emoji.isBlank()) INITIAL_FILL else EMOJI_FILL).toSp() }
        when {
            picture != null -> Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            emoji.isNotBlank() -> Text(emoji, fontSize = glyph, lineHeight = glyph, maxLines = 1, softWrap = false)
            initial != null -> Text(
                initial,
                // Based on the theme's type scale, not a bare TextStyle, so the initial is in the font the user chose.
                style = MaterialTheme.typography.titleMedium.copy(
                    color = scheme.onPrimaryContainer,
                    fontSize = glyph,
                    lineHeight = glyph,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
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
