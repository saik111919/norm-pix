package com.example.normpix.ui.theme

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
import com.example.normpix.settings.CustomFontFace
import com.example.normpix.settings.FontFileInfo
import com.example.normpix.settings.FontSets
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
    /** The user's family's other weights (a download of static files); empty for a variable font or a single file. */
    val customFaces: List<CustomFontFace> = emptyList(),
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

/** Nunito's own weight axis runs this far (the bundled file is Google Fonts' variable Nunito). */
private val NUNITO_WEIGHT_AXIS = 200..1000

/**
 * Nunito (bundled as `res/font/nunito.ttf`, Google Fonts' variable file, SIL OFL): every weight a style may ask for maps to the
 * font at exactly that weight, so a heading's SemiBold is Nunito's SemiBold and never an emboldened Regular.
 */
@OptIn(ExperimentalTextApi::class)
fun nunitoFamily(): FontFamily = FontFamily(
    WEIGHTS.map { weight ->
        Font(
            R.font.nunito,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight.coerceIn(NUNITO_WEIGHT_AXIS))),
        )
    },
)

/**
 * The font the user picked, as a family that uses all of it:
 *  - a variable font (one file with a weight axis) at every weight a style asks for, so SemiBold is the font's SemiBold;
 *  - a family of several files (a download's Regular, Medium, Bold ...) with each file at the weight it says it has, so the text
 *    system picks the nearest and never emboldens a file that has a bold of its own;
 *  - any other single file as it always was: the one file for every weight, emboldened by the text system where a style asks for
 *    more (a static font has no other), which keeps headings heavier than body text.
 * The file's own tables say which it is ([FontFileInfo]); a file that can't be read that way is the last of these.
 */
@OptIn(ExperimentalTextApi::class)
fun customFontFamily(file: File, faces: List<CustomFontFace> = emptyList()): FontFamily {
    val info = FontFileInfo.read(file)
    val axis = info?.weightAxis
    if (axis != null) {
        return FontFamily(
            WEIGHTS.map { weight ->
                Font(file, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight.coerceIn(axis))))
            },
        )
    }
    if (faces.isNotEmpty()) {
        val main = CustomFontFace(file, FontSets.nearestWeight(info?.weight ?: 400))
        return FontFamily((listOf(main) + faces).map { Font(it.file, FontWeight(it.weight)) })
    }
    return FontFamily(Font(file))
}

/**
 * The family [font] means, or null for the phone's own font (which the type scale then leaves as it is). A custom font with
 * no usable file is GS Flex.
 */
fun fontFamilyFor(font: AppFont): FontFamily? = when (font.font) {
    InterfaceFont.System -> null
    InterfaceFont.GsFlex -> gsFlexFamily(font.shape)
    InterfaceFont.Nunito -> nunitoFamily()
    InterfaceFont.Custom -> font.customFile?.let { customFontFamily(it, font.customFaces) } ?: gsFlexFamily(font.shape)
}

/**
 * The Material type scale in [font]. The phone's own font is Material's default scale untouched; GS Flex and a custom font are
 * the same scale (sizes, line heights, weights) set in their family. (Material's "emphasized" twins of these styles are internal
 * to Material in this version, so an app can neither read nor set them; nothing in NormPix draws with them.)
 */
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
    )
}

/**
 * The font file to draw the interface in while [font] is [InterfaceFont.Custom] (null for any other font, and while there is
 * no usable file). [revision] changes whenever the saved font does (a replace, a remove, a restored backup), which is when the
 * file is looked at again. The file is made ready off the main thread; if it is already made (the usual case after the first
 * time) it is there on the first frame, so the text never flashes through another font on the way in.
 */
@Composable
fun rememberCustomFontFile(font: InterfaceFont, revision: Int): File? = rememberCustomFontFiles(font, revision)?.file

/** The user's font as the app draws with it: the file that draws regular text, and the other weights of its family (if any). */
data class CustomFontFiles(val file: File, val faces: List<CustomFontFace>)

/** [rememberCustomFontFile], with the other weights of the family. Null for any other font and while there is no usable file. */
@Composable
fun rememberCustomFontFiles(font: InterfaceFont, revision: Int): CustomFontFiles? {
    val context = LocalContext.current.applicationContext
    val first = remember {
        if (font == InterfaceFont.Custom) {
            // Both made already (the usual case after the first time) is a font on the first frame; otherwise they are made below.
            CustomFont.ready(context)?.let { file -> CustomFont.readyFaces(context)?.let { faces -> CustomFontFiles(file, faces) } }
        } else {
            null
        }
    }
    val files by produceState<CustomFontFiles?>(first, font, revision) {
        value = if (font == InterfaceFont.Custom) {
            withContext(Dispatchers.IO) { CustomFont.loadable(context)?.let { file -> CustomFontFiles(file, CustomFont.faces(context)) } }
        } else {
            null
        }
    }
    return if (font == InterfaceFont.Custom) files else null
}
