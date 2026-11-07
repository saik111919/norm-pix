package com.example.normpix.ui.components

import android.content.Context
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import com.example.normpix.banks.Bank
import com.example.normpix.wallet.BankLogoArt
import com.example.normpix.wallet.BankLogoFile
import com.example.normpix.wallet.BankLogoResolver
import com.example.normpix.wallet.BankMarks
import java.util.concurrent.ConcurrentHashMap

/** Parsed SVG assets, shared across the app (parsing is the expensive part). */
internal object SvgCache {
    private val cache = ConcurrentHashMap<String, Loaded>()

    /** A cache slot: [svg] is null when the asset is missing or can't be used. */
    private class Loaded(val svg: SVG?)

    fun get(context: Context, path: String): SVG? = cache.getOrPut(path) {
        Loaded(runCatching { SVG.getFromAsset(context.assets, path) }.getOrNull()?.let(::scalable))
    }.svg

    /**
     * Makes [svg] scale to whatever box it is drawn into. AndroidSVG only scales a
     * document that has a viewBox and no fixed size; bundled artwork often has
     * a fixed `width`/`height` (drawn at that many pixels, so it overflows or
     * crops) or no viewBox at all (nothing to scale). The viewBox comes from the
     * declared size when missing, the size becomes 100% of the box, and the
     * artwork is always fitted whole and centered - never stretched or cropped.
     * Returns null for a document with no usable dimensions.
     */
    fun scalable(svg: SVG): SVG? {
        val box = svg.documentViewBox
        if (box == null || box.width() <= 0f || box.height() <= 0f) {
            val width = svg.documentWidth
            val height = svg.documentHeight
            if (width <= 0f || height <= 0f) return null
            svg.setDocumentViewBox(0f, 0f, width, height)
        }
        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")
        svg.documentPreserveAspectRatio = PreserveAspectRatio.LETTERBOX
        return svg
    }
}

/** The width-to-height ratio of a bundled SVG asset, or null when it can't be loaded. */
@Composable
fun rememberSvgAspectRatio(path: String): Float? {
    val context = LocalContext.current
    return remember(path) { SvgCache.get(context, path)?.documentViewBox?.let { it.width() / it.height() } }
}

/**
 * Draws a bundled SVG asset, fitted whole and centered in its box (aspect
 * ratio kept). With a [tint], the artwork is drawn as a single-color silhouette.
 */
@Composable
fun SvgAsset(path: String, modifier: Modifier = Modifier, tint: Color? = null) {
    val context = LocalContext.current
    val svg = remember(path) { SvgCache.get(context, path) } ?: return
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val options = RenderOptions.create()
                .viewPort(0f, 0f, size.width, size.height)
                .preserveAspectRatio(PreserveAspectRatio.LETTERBOX)
            if (tint == null) {
                svg.renderToCanvas(native, options)
            } else {
                val layer = native.saveLayer(RectF(0f, 0f, size.width, size.height), null)
                svg.renderToCanvas(native, options)
                val paint = Paint().apply {
                    color = tint.toArgb()
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                }
                native.drawRect(0f, 0f, size.width, size.height, paint)
                native.restoreToCount(layer)
            }
        }
    }
}

/**
 * The mark of [bank] - the one place an account's or a card's bank is drawn, so the artwork (and the generic bank glyph
 * that stands in when a bank has none, is unknown, or its artwork won't load) can't differ from screen to screen.
 */
@Composable
fun BankGlyph(bank: Bank?, tint: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    BankLogoGlyph(bank?.logo, tint, modifier, size, mark = bank?.mark)

/**
 * The mark of the bank called [bankName], found by [BankLogoResolver] like every other bank logo in the app - for the
 * places that only hold a name (a reminder's linked card). Unknown names get the generic glyph.
 */
@Composable
fun BankGlyph(bankName: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    // A directory bank without artwork wears its mark; a name the directory does not know gets the generic glyph.
    val entry = remember(bankName) { BankLogoResolver.bankFor(bankName) }
    BankLogoGlyph(entry?.logo, tint, modifier, size, mark = entry?.let { BankMarks.of(it.id, it.name) })
}

/** [mark] is the monogram for a bank with no artwork (or whose artwork will not load); null for a bank not known at all, which gets the generic bank icon. */
@Composable
private fun BankLogoGlyph(logo: BankLogoArt?, tint: Color, modifier: Modifier, size: Dp, mark: String?) {
    val onDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val icon = remember(logo, onDark) { logo?.pick(compact = true, onDark = onDark) }
    val loads = icon?.let { rememberBankArtAspectRatio(it) } != null
    if (icon == null || !loads) {
        if (mark != null) {
            BankMonogram(mark, tint, modifier, size)
        } else {
            Icon(Icons.Rounded.AccountBalance, contentDescription = null, tint = tint, modifier = modifier.size(size))
        }
    } else {
        BankArtwork(icon, modifier.size(size))
    }
}

/**
 * A bank's [mark] as the letters of a badge: bold, centred, in [tint], sized to the badge and to how many letters there are, and never
 * scaled with the system font size (the badge does not grow with it, so the letters must not outgrow it).
 */
@Composable
internal fun BankMonogram(mark: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val fraction = when (mark.length) {
        1 -> 0.62f
        2 -> 0.52f
        3 -> 0.4f
        else -> 0.33f
    }
    val fontSize = with(LocalDensity.current) { (size * fraction).toSp() }
    Box(modifier.size(size).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Text(
            mark,
            color = tint,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
            maxLines = 1,
            softWrap = false,
            style = LocalTextStyle.current.copy(lineHeight = fontSize, platformStyle = PlatformTextStyle(includeFontPadding = false)),
        )
    }
}

/** The width-to-height ratio of a bank's logo file: a round picture is square, an SVG has its own; null when an SVG can't be loaded. */
@Composable
fun rememberBankArtAspectRatio(file: BankLogoFile): Float? =
    if (file.isPicture) 1f else rememberSvgAspectRatio(file.path)

/** A bank's logo file drawn whole in its box, whether it is an SVG or a round picture. */
@Composable
fun BankArtwork(file: BankLogoFile, modifier: Modifier = Modifier) {
    if (file.isPicture) AssetImage(file.path, modifier) else SvgAsset(file.path, modifier)
}
