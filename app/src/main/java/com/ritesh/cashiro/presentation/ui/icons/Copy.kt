package com.ritesh.cashiro.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Copy: ImageVector
    get() {
        if (_Copy != null) {
            return _Copy!!
        }
        _Copy = ImageVector.Builder(
            name = "Copy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            group(
                clipPathData = PathData {
                    moveTo(0f, 0f)
                    horizontalLineToRelative(24f)
                    verticalLineToRelative(24f)
                    horizontalLineToRelative(-24f)
                    close()
                }
            ) {
                path(fill = SolidColor(Color.White)) {
                    moveTo(16f, 12.9f)
                    verticalLineTo(17.1f)
                    curveTo(16f, 20.6f, 14.6f, 22f, 11.1f, 22f)
                    horizontalLineTo(6.9f)
                    curveTo(3.4f, 22f, 2f, 20.6f, 2f, 17.1f)
                    verticalLineTo(12.9f)
                    curveTo(2f, 9.4f, 3.4f, 8f, 6.9f, 8f)
                    horizontalLineTo(11.1f)
                    curveTo(14.6f, 8f, 16f, 9.4f, 16f, 12.9f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(17.1f, 2f)
                    horizontalLineTo(12.9f)
                    curveTo(9.817f, 2f, 8.371f, 3.094f, 8.07f, 5.739f)
                    curveTo(8.007f, 6.292f, 8.465f, 6.75f, 9.022f, 6.75f)
                    horizontalLineTo(11.1f)
                    curveTo(15.3f, 6.75f, 17.25f, 8.7f, 17.25f, 12.9f)
                    verticalLineTo(14.978f)
                    curveTo(17.25f, 15.535f, 17.708f, 15.993f, 18.261f, 15.93f)
                    curveTo(20.906f, 15.629f, 22f, 14.183f, 22f, 11.1f)
                    verticalLineTo(6.9f)
                    curveTo(22f, 3.4f, 20.6f, 2f, 17.1f, 2f)
                    close()
                }
            }
        }.build()

        return _Copy!!
    }

@Suppress("ObjectPropertyName")
private var _Copy: ImageVector? = null
