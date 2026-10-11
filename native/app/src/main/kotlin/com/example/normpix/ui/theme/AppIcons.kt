package com.example.normpix.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons that are not in the Material set, as vectors so they tint, scale and animate like any other
 * `ImageVector` (the destination icons, tabs and buttons all go through `Icon(imageVector, tint)`).
 */
object AppIcons {
    /** Outlined wallet (inactive state): the supplied Wallet artwork, drawn as four 3.6-wide rounded strokes on a 48 grid. */
    val Wallet: ImageVector by lazy { wallet(filled = false) }

    /** The same wallet with its lower body filled (active state): same silhouette and size, so selecting never shifts the layout. */
    val WalletFilled: ImageVector by lazy { wallet(filled = true) }

    private const val FLAP_TOP = "M43.5,21.09V13.6252a6.21,6.21,0,0,0-6.1995-6.1995H10.7a6.2507,6.2507,0,0,0-6.1995,6.2v7.718"
    private const val FLAP_MID = "M43.5,18.844a6.21,6.21,0,0,0-6.1995-6.1995H10.7A6.21,6.21,0,0,0,4.5,18.844"
    private const val FLAP_LOW = "M42.9532,21.4883a6.2117,6.2117,0,0,0-5.6527-3.656H10.7a6.2115,6.2115,0,0,0-5.645,3.6391"
    private const val BODY =
        "M29.298,27.1,4.5,21.3432V34.3748a6.21,6.21,0,0,0,6.2,6.2h26.601a6.21,6.21,0,0,0,6.2-6.1995V21.09l-5.915,4.3017A10.1987,10.1987,0,0,1,29.298,27.1Z"

    private fun wallet(filled: Boolean): ImageVector {
        val ink = SolidColor(Color.Black)
        return ImageVector.Builder(
            name = if (filled) "WalletFilled" else "Wallet",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 48f,
            viewportHeight = 48f,
        ).apply {
            for (flap in listOf(FLAP_TOP, FLAP_MID, FLAP_LOW)) {
                addPath(
                    pathData = addPathNodes(flap),
                    stroke = ink,
                    strokeLineWidth = 3.6f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            addPath(
                pathData = addPathNodes(BODY),
                fill = if (filled) ink else null,
                stroke = ink,
                strokeLineWidth = 3.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }
}
