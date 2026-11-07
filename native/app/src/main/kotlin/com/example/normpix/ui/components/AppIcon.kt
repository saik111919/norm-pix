package com.example.normpix.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.example.normpix.settings.IconStyle

/*
 * The app's `Icon`: Material's own, except that with Settings > Appearance > Icons > Interface icons on Lucide a Material icon is drawn
 * as its Lucide drawing (LucideIcons.kt). The app imports this one, not `androidx.compose.material3.Icon`, so that one choice reaches
 * every icon at once without a change at any of the places that draw one. The same three forms as Material's, and the same defaults.
 */

/** The interface icons in force, from the user's choice; provided once at the root (`ProvideAppEnvironment`). */
val LocalIconStyle = compositionLocalOf { IconStyle.Material }

/** The icon to draw for [icon]: itself, or - on Lucide - its Lucide drawing where there is one. */
@Composable
private fun styled(icon: ImageVector): ImageVector {
    if (LocalIconStyle.current != IconStyle.Lucide) return icon
    val context = LocalContext.current
    return remember(icon, context) { LucideIcons.forMaterial(context, icon) } ?: icon
}

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    androidx.compose.material3.Icon(styled(imageVector), contentDescription, modifier, tint)
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    androidx.compose.material3.Icon(painter, contentDescription, modifier, tint)
}

@Composable
fun Icon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    androidx.compose.material3.Icon(bitmap, contentDescription, modifier, tint)
}
