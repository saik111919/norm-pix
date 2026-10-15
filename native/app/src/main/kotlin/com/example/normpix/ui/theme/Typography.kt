package com.example.normpix.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.normpix.R
import com.example.normpix.settings.FontShape
import com.example.normpix.settings.InterfaceFont

/** What the interface's text is set in: the typeface and, for GS Flex, its shape. */
data class AppFont(val font: InterfaceFont = InterfaceFont.System, val shape: FontShape = FontShape())

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
 * The Material type scale in [font]. The phone's own font is Material's default scale untouched; GS Flex is the same scale
 * (sizes, line heights, weights) set in [gsFlexFamily].
 */
fun appTypography(font: AppFont): Typography {
    val base = Typography()
    if (font.font == InterfaceFont.System) return base
    val family = gsFlexFamily(font.shape)
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
