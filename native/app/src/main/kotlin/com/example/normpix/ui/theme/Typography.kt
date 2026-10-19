package com.example.normpix.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.normpix.R
import com.example.normpix.settings.CustomFont
import com.example.normpix.settings.FontShape
import com.example.normpix.settings.InterfaceFont
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the interface's text is set in: the typeface and, for GS Flex, its shape. For [InterfaceFont.Custom] it is also the font
 * file to draw with ([customFile], from [rememberCustomFontFile]); while that is null (the file is still being made ready, or is
 * gone or no longer loads) the text is set in GS Flex, quietly, so a missing file is never a broken screen.
 */
data class AppFont(
    val font: InterfaceFont = InterfaceFont.System,
    val shape: FontShape = FontShape(),
    val customFile: File? = null,
)

/** The weights a text style can ask for; each is one instance of the variable font, so none is ever faked by emboldening. */
private val WEIGHTS = (100..900 step 100).map(::FontWeight)

/** GS Flex's own weight axis runs this far (the bundled file is cut to it). */
private val WEIGHT_AXIS = 100..900

/**
 * GS Flex (Google Sans Flex, bundled as `res/font/gs_flex.ttf`) drawn with [shape]: every weight a style may ask for maps to
 * the font at that weight moved by [FontShape.weightShift], at the shape's width and roundness. A style that asks for
 * SemiBold therefore still gets SemiBold-and-the-shift, so headings stay heavier than body text in every style.
 */
@OptIn(ExperimentalTextApi::class)
fun gsFlexFamily(shape: FontShape): FontFamily = FontFamily(
    WEIGHTS.map { weight ->
        Font(
            R.font.gs_flex,
            weight = weight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight((weight.weight + shape.weightShift).coerceIn(WEIGHT_AXIS)),
                FontVariation.width(shape.width.toFloat()),
                FontVariation.Setting("ROND", shape.roundness.toFloat()),
            ),
        )
    },
)

/**
 * The font file the user picked, as a family. It is the one file for every weight: a style that asks for a bolder weight than
 * the file has gets it emboldened by the text system (a static font has no other), which keeps headings heavier than body text.
 */
@OptIn(ExperimentalTextApi::class)
fun customFontFamily(file: File): FontFamily = FontFamily(Font(file))

/**
 * The family [font] means, or null for the phone's own font (which the type scale then leaves as it is). A custom font with
 * no usable file is GS Flex.
 */
fun fontFamilyFor(font: AppFont): FontFamily? = when (font.font) {
    InterfaceFont.System -> null
    InterfaceFont.GsFlex -> gsFlexFamily(font.shape)
    InterfaceFont.Custom -> font.customFile?.let(::customFontFamily) ?: gsFlexFamily(font.shape)
}

/**
 * The Material type scale in [font]. The phone's own font is Material's default scale untouched; GS Flex and a custom font are
 * the same scale (sizes, line heights, weights) set in their family. Material's fifteen "emphasized" styles (the heavier twin
 * of each style, which its expressive components draw with) are set in it too, otherwise they would stay in the phone's font.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun appTypography(font: AppFont): Typography {
    val base = Typography()
    val family = fontFamilyFor(font) ?: return base
    fun TextStyle.inFamily() = copy(fontFamily = family)
    return base.copy(
        displayLarge = base.displayLarge.inFamily(),
        displayMedium = base.displayMedium.inFamily(),
        displaySmall = base.displaySmall.inFamily(),
        headlineLarge = base.headlineLarge.inFamily(),
        headlineMedium = base.headlineMedium.inFamily(),
        headlineSmall = base.headlineSmall.inFamily(),
        titleLarge = base.titleLarge.inFamily(),
        titleMedium = base.titleMedium.inFamily(),
        titleSmall = base.titleSmall.inFamily(),
        bodyLarge = base.bodyLarge.inFamily(),
        bodyMedium = base.bodyMedium.inFamily(),
        bodySmall = base.bodySmall.inFamily(),
        labelLarge = base.labelLarge.inFamily(),
        labelMedium = base.labelMedium.inFamily(),
        labelSmall = base.labelSmall.inFamily(),
        displayLargeEmphasized = base.displayLargeEmphasized.inFamily(),
        displayMediumEmphasized = base.displayMediumEmphasized.inFamily(),
        displaySmallEmphasized = base.displaySmallEmphasized.inFamily(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.inFamily(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.inFamily(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.inFamily(),
        titleLargeEmphasized = base.titleLargeEmphasized.inFamily(),
        titleMediumEmphasized = base.titleMediumEmphasized.inFamily(),
        titleSmallEmphasized = base.titleSmallEmphasized.inFamily(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.inFamily(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.inFamily(),
        bodySmallEmphasized = base.bodySmallEmphasized.inFamily(),
        labelLargeEmphasized = base.labelLargeEmphasized.inFamily(),
        labelMediumEmphasized = base.labelMediumEmphasized.inFamily(),
        labelSmallEmphasized = base.labelSmallEmphasized.inFamily(),
    )
}

/**
 * The font file to draw the interface in while [font] is [InterfaceFont.Custom] (null for any other font, and while there is
 * no usable file). [revision] changes whenever the saved font does (a replace, a remove, a restored backup), which is when the
 * file is looked at again. The file is made ready off the main thread; if it is already made (the usual case after the first
 * time) it is there on the first frame, so the text never flashes through another font on the way in.
 */
@Composable
fun rememberCustomFontFile(font: InterfaceFont, revision: Int): File? {
    val context = LocalContext.current.applicationContext
    val first = remember { if (font == InterfaceFont.Custom) CustomFont.ready(context) else null }
    val file by produceState<File?>(first, font, revision) {
        value = if (font == InterfaceFont.Custom) withContext(Dispatchers.IO) { CustomFont.loadable(context) } else null
    }
    return if (font == InterfaceFont.Custom) file else null
}
