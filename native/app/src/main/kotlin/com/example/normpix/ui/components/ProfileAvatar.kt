package com.example.normpix.ui.components

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.RequiresApi
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import com.example.normpix.settings.ProfilePhotos
import com.example.normpix.settings.isAnimatedPhotoName
import com.example.normpix.ui.theme.rememberReducedMotion
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            picture != null -> {
                // Its first frame at once; a GIF's animation is laid over it as soon as it is ready (and not at all where animations are off).
                Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (isAnimatedPhotoName(photo)) AnimatedPhoto(photo, Modifier.fillMaxSize())
            }
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

/** The longest side a GIF is decoded at: sharp at the largest the picture is shown, and light on memory frame by frame. */
private const val GIF_DECODE_SIDE = 512

/**
 * The animation of a GIF [name] (see [ProfilePhotos.saveGif]), playing on and on over the still frame beneath it, filled into
 * the circle like a photo. Nothing is drawn - the still frame stays - before Android 9 (which cannot play one this way), while it
 * is being read, if it cannot be, and where the system has animations off.
 */
@Composable
private fun AnimatedPhoto(name: String, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    val reduced = rememberReducedMotion()
    val decoded by produceState<Drawable?>(null, name, reduced) {
        value = if (!reduced && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            withContext(Dispatchers.IO) { ProfilePhotos(context).file(name)?.let { decodeAnimated(it) } }
        } else {
            null
        }
    }
    val playing = decoded ?: return
    DisposableEffect(playing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && playing is AnimatedImageDrawable) {
            playing.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            playing.start()
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && playing is AnimatedImageDrawable) playing.stop()
        }
    }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        update = { it.setImageDrawable(playing) },
        modifier = modifier,
    )
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeAnimated(file: File): Drawable? = runCatching {
    ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val shorter = minOf(info.size.width, info.size.height)
        if (shorter > GIF_DECODE_SIDE) {
            val scale = GIF_DECODE_SIDE.toFloat() / shorter
            decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
        }
    }.takeIf { it is AnimatedImageDrawable }
}.getOrNull()

/** How much of the circle an emoji, and an initial, fills. */
private const val EMOJI_FILL = 0.52f
private const val INITIAL_FILL = 0.46f
