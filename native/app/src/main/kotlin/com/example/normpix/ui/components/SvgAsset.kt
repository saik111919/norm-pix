package com.example.normpix.ui.components

import android.content.Context
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import com.example.normpix.banks.Bank
import com.example.normpix.wallet.BankLogoArt
import com.example.normpix.wallet.BankLogoResolver
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
    BankLogoGlyph(bank?.logo, tint, modifier, size)

/**
 * The mark of the bank called [bankName], found by [BankLogoResolver] like every other bank logo in the app - for the
 * places that only hold a name (a reminder's linked card). Unknown names get the generic glyph.
 */
@Composable
fun BankGlyph(bankName: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    BankLogoGlyph(remember(bankName) { BankLogoResolver.logoFor(bankName) }, tint, modifier, size)

@Composable
private fun BankLogoGlyph(logo: BankLogoArt?, tint: Color, modifier: Modifier, size: Dp) {
    val onDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val icon = remember(logo, onDark) { logo?.pick(compact = true, onDark = onDark) }
    val loads = icon?.let { rememberSvgAspectRatio(it.path) } != null
    if (icon == null || !loads) {
        Icon(Icons.Rounded.AccountBalance, contentDescription = null, tint = tint, modifier = modifier.size(size))
    } else {
        SvgAsset(icon.path, modifier.size(size))
    }
}
