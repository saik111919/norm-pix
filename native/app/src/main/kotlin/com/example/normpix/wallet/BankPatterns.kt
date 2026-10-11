package com.example.normpix.wallet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Subtle background textures matching each bank's card styling. All geometry is in dp-independent card units. */
fun DrawScope.drawBankPattern(type: BankPatternType, accent: Color?) {
    when (type) {
        BankPatternType.HdfcGeometricGrid -> hdfcGrid()
        BankPatternType.SbiConcentricRings -> sbiRings()
        BankPatternType.IciciWaves -> iciciWaves(accent ?: Color(0xFFF37021))
        BankPatternType.AxisAngularFacets -> axisFacets()
        BankPatternType.KotakInfinity -> kotakLoops()
        BankPatternType.AmexCenturionKey -> amexKeyline()
        BankPatternType.ChaseOctagonRays -> chaseRays()
        BankPatternType.CitiArcWaves -> citiArcs()
        BankPatternType.HsbcHexagonGrid -> hsbcGrid()
        BankPatternType.MetallicBrushed -> brushedMetal()
        BankPatternType.MinimalGeometric -> minimalDots()
    }
}

private fun white(alpha: Float) = Color.White.copy(alpha = alpha)

private fun DrawScope.hdfcGrid() {
    val spacing = 18.dp.toPx()
    var i = -size.height
    while (i < size.width + size.height) {
        drawLine(white(0.04f), Offset(i, 0f), Offset(i + size.height, size.height), strokeWidth = 1.dp.toPx())
        drawLine(white(0.04f), Offset(i, size.height), Offset(i + size.height, 0f), strokeWidth = 1.dp.toPx())
        i += spacing
    }
}

private fun DrawScope.sbiRings() {
    val center = Offset(size.width * 0.85f, size.height * 0.2f)
    var r = 30.dp.toPx()
    while (r < size.width) {
        val alpha = max(0.015f, 0.08f - (r / size.width) * 0.07f)
        drawCircle(white(alpha), r, center, style = Stroke(1.2.dp.toPx()))
        r += 28.dp.toPx()
    }
}

private fun DrawScope.iciciWaves(accent: Color) {
    val stroke = Stroke(2.dp.toPx())
    val w = size.width
    val h = size.height
    drawPath(
        Path().apply {
            moveTo(0f, h * 0.45f)
            cubicTo(w * 0.35f, h * 0.3f, w * 0.65f, h * 0.7f, w, h * 0.5f)
        },
        white(0.07f),
        style = stroke,
    )
    drawPath(
        Path().apply {
            moveTo(0f, h * 0.55f)
            cubicTo(w * 0.35f, h * 0.4f, w * 0.65f, h * 0.8f, w, h * 0.6f)
        },
        accent.copy(alpha = 0.09f),
        style = stroke,
    )
}

private fun DrawScope.axisFacets() {
    val stroke = Stroke(1.2.dp.toPx())
    val w = size.width
    val h = size.height
    drawPath(
        Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.8f)
            lineTo(w * 0.2f, h)
            close()
        },
        white(0.06f),
        style = stroke,
    )
    drawLine(white(0.06f), Offset(w * 0.7f, 0f), Offset(w * 0.3f, h), strokeWidth = 1.2.dp.toPx())
    drawLine(white(0.06f), Offset(w * 0.9f, 0f), Offset(w * 0.5f, h), strokeWidth = 1.2.dp.toPx())
}

private fun DrawScope.kotakLoops() {
    val w = size.width
    val h = size.height
    drawPath(
        Path().apply {
            moveTo(w * 0.2f, h * 0.5f)
            cubicTo(w * 0.4f, h * 0.2f, w * 0.6f, h * 0.8f, w * 0.8f, h * 0.5f)
            cubicTo(w * 0.6f, h * 0.2f, w * 0.4f, h * 0.8f, w * 0.2f, h * 0.5f)
        },
        white(0.05f),
        style = Stroke(2.5.dp.toPx()),
    )
}

private fun DrawScope.amexKeyline() {
    val stroke = Stroke(1.dp.toPx())
    val inset = 12.dp.toPx()
    drawRect(white(0.08f), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = stroke)
    val inset2 = 16.dp.toPx()
    drawRect(white(0.08f), Offset(inset2, inset2), Size(size.width - inset2 * 2, size.height - inset2 * 2), style = stroke)
}

private fun DrawScope.chaseRays() {
    val center = Offset(size.width * 0.9f, size.height * 0.1f)
    for (i in 0 until 8) {
        val angle = i * (PI / 4).toFloat()
        val dest = Offset(center.x + cos(angle) * size.width * 1.2f, center.y + sin(angle) * size.width * 1.2f)
        drawLine(white(0.04f), center, dest, strokeWidth = 1.dp.toPx())
    }
}

private fun DrawScope.citiArcs() {
    val w = size.width
    val h = size.height
    for (i in 0 until 3) {
        drawPath(
            Path().apply {
                moveTo(-20f, h * (0.3f + i * 0.18f))
                quadraticTo(w * 0.5f, h * (0.05f + i * 0.15f), w + 20f, h * (0.5f + i * 0.18f))
            },
            white(0.04f + i * 0.02f),
            style = Stroke(2.dp.toPx()),
        )
    }
}

private fun DrawScope.hsbcGrid() {
    val r = 24.dp.toPx()
    var y = 0f
    while (y < size.height + r) {
        var x = 0f
        while (x < size.width + r) {
            drawCircle(white(0.04f), 1.2.dp.toPx(), Offset(x, y))
            x += r * sqrt(3f)
        }
        y += r * 1.5f
    }
}

private fun DrawScope.brushedMetal() {
    var y = 4.dp.toPx()
    while (y < size.height) {
        drawLine(white(0.03f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8.dp.toPx())
        y += 7.dp.toPx()
    }
}

private fun DrawScope.minimalDots() {
    val step = 20.dp.toPx()
    val inset = 12.dp.toPx()
    var x = inset
    while (x < size.width - inset) {
        var y = inset
        while (y < size.height - inset) {
            drawCircle(white(0.05f), 0.9.dp.toPx(), Offset(x, y))
            y += step
        }
        x += step
    }
}
