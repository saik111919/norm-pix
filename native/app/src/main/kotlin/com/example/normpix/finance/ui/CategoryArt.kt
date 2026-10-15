package com.example.normpix.finance.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.example.normpix.finance.domain.emojiOf
import com.example.normpix.settings.CategoryIconStyle
import com.example.normpix.ui.components.AssetImage
import com.example.normpix.ui.components.SvgAsset

/**
 * Microsoft's Fluent Emoji in 3D (MIT licence, `assets/licenses/fluentui-emoji.txt`), bundled as
 * `assets/fluent3d/<code points>.webp` for the emoji the app offers - the built-in icons, the emoji catalog - not for every
 * emoji there is. An emoji without a picture is drawn by the device's font instead.
 */
internal object Fluent3d {
    private const val DIR = "fluent3d"

    @Volatile
    private var files: Set<String>? = null

    /** The bundled picture of [emoji], or null when the app has none. */
    fun path(context: Context, emoji: String): String? {
        val name = fileName(emoji)
        return if (name in files(context)) "$DIR/$name.webp" else null
    }

    /**
     * The file [emoji] is stored under: its code points in lowercase hex, joined by "_", without U+FE0F (which only asks for
     * the colourful form) - so 🛡️ and 🛡 are both `1f6e1`, and 👨‍👩‍👧 is `1f468_200d_1f469_200d_1f467`.
     */
    fun fileName(emoji: String): String = buildString {
        var i = 0
        while (i < emoji.length) {
            val point = emoji.codePointAt(i)
            if (point != VARIATION_SELECTOR_16) {
                if (isNotEmpty()) append('_')
                append(Integer.toHexString(point))
            }
            i += Character.charCount(point)
        }
    }

    /** The pictures there are, listed once: asking the asset index is quick, but not something to do on every frame. */
    private fun files(context: Context): Set<String> = files ?: synchronized(this) {
        files ?: runCatching { context.assets.list(DIR) }.getOrNull().orEmpty()
            .mapTo(HashSet()) { it.removeSuffix(".webp") }
            .also { files = it }
    }

    private const val VARIATION_SELECTOR_16 = 0xFE0F
}

/** What a category's icon is drawn as: it depends on the icon, and on the user's [CategoryIconStyle]. */
@Immutable
internal sealed interface CategoryArt {
    /** A brand's own logo: round and full-colour, so on an avatar it is the whole disc. */
    data class Logo(val path: String) : CategoryArt

    /** A 3D picture of an emoji. */
    data class Picture(val path: String) : CategoryArt

    /** A brand's mark (a vector), as a silhouette in the category's colour. */
    data class Mark(val path: String) : CategoryArt

    /** An emoji, drawn by the device's font. */
    data class Glyph(val emoji: String) : CategoryArt

    /** One of the app's outlined icons, in the category's colour. */
    data class Outline(val icon: ImageVector) : CategoryArt
}

/**
 * How [icon] (a category's stored icon) is drawn in [style]. [name] is the category's name: a category called after a brand
 * the app knows ("Netflix", "Swiggy") shows that brand whatever icon it has - unless it was given an emoji of its own, which
 * is always kept, in 3D where the app has that picture.
 * 1. 3D: brands by their logo, everything else as a 3D picture.
 * 2. Emoji: brands by their logo, everything else in the device's emoji.
 * 3. Outline: brands by their mark where the app has one, everything else as an outlined icon.
 * Each falls back a step when the art it wants is missing (an emoji the device can't draw becomes the outlined icon).
 */
internal fun categoryArt(icon: String, name: String?, style: CategoryIconStyle, context: Context): CategoryArt {
    emojiOf(icon)?.let { own ->
        return if (style == CategoryIconStyle.ThreeD) Fluent3d.path(context, own)?.let(CategoryArt::Picture) ?: CategoryArt.Glyph(own) else CategoryArt.Glyph(own)
    }
    val brand = brandKey(icon) ?: name?.let(::brandKeyForName)
    if (brand != null) brandArt(brand, style)?.let { return it }
    val spec = categoryIcons[icon] ?: brand?.let(categoryIcons::get) ?: categoryIcons.getValue("other")
    val glyph = spec.emoji.takeIf(::deviceCanDraw)?.let(CategoryArt::Glyph)
    return when (style) {
        CategoryIconStyle.ThreeD -> Fluent3d.path(context, spec.emoji)?.let(CategoryArt::Picture) ?: glyph ?: CategoryArt.Outline(spec.icon)
        CategoryIconStyle.Emoji -> glyph ?: CategoryArt.Outline(spec.icon)
        CategoryIconStyle.Outline -> CategoryArt.Outline(spec.icon)
    }
}

/** A brand's art in [style]: its logo in colour, its mark in outline; null when it has neither in that style. */
private fun brandArt(key: String, style: CategoryIconStyle): CategoryArt? {
    val mark = brandAsset(key)?.let(CategoryArt::Mark)
    if (style == CategoryIconStyle.Outline) return mark
    return brandLogo(key)?.let(CategoryArt::Logo) ?: mark
}

/** A brand's own art whatever the style - its logo, else its mark - for choosing among brands. Null for a key that isn't one. */
internal fun brandPreviewArt(key: String): CategoryArt? = brandArt(key, CategoryIconStyle.ThreeD)

/**
 * The logo of the brand a [payee] is ([brandKeyForPayee]), or null: no payee, none the app knows, or the Outline style, which
 * draws no logos (the category's own icon stays then).
 */
internal fun payeeArt(payee: String?, style: CategoryIconStyle): CategoryArt.Logo? {
    if (payee == null || style == CategoryIconStyle.Outline) return null
    return brandKeyForPayee(payee)?.let(::brandLogo)?.let(CategoryArt::Logo)
}

/** [payeeArt] for the ambient style; worked out again only when the payee or the style changes. */
@Composable
internal fun rememberPayeeArt(payee: String?): CategoryArt.Logo? {
    val style = LocalCategoryIconStyle.current
    return remember(payee, style) { payeeArt(payee, style) }
}

/** [categoryArt] for the ambient style; worked out again only when the icon, the name or the style changes. */
@Composable
internal fun rememberCategoryArt(icon: String, name: String? = null): CategoryArt {
    val style = LocalCategoryIconStyle.current
    val context = LocalContext.current
    return remember(icon, name, style) { categoryArt(icon, name, style, context) }
}

/**
 * [art] in a [size] box: a logo clipped to the user's avatar shape ([LocalCategoryIconShape]), a picture or emoji fitted whole,
 * a mark or outline in [tint]. Decorative.
 */
@Composable
internal fun CategoryArtView(art: CategoryArt, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    when (art) {
        is CategoryArt.Logo -> AssetImage(art.path, modifier.size(size).clip(LocalCategoryIconShape.current.outline))
        is CategoryArt.Picture -> AssetImage(art.path, modifier.size(size))
        is CategoryArt.Mark -> SvgAsset(art.path, modifier = modifier.size(size), tint = tint)
        is CategoryArt.Outline -> Icon(art.icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
        is CategoryArt.Glyph -> {
            // `size` in dp whatever the user's font scale: an icon does not grow with the text around it.
            val fontSize = with(LocalDensity.current) { (size * EMOJI_FILL).toSp() }
            Box(modifier.size(size).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                Text(
                    art.emoji,
                    style = TextStyle(fontSize = fontSize, lineHeight = fontSize, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** How much of its box an emoji fills, so one reads as large as an outlined icon of the same size. */
private const val EMOJI_FILL = 0.92f
