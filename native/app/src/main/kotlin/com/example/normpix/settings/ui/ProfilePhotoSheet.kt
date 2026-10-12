package com.example.normpix.settings.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.normpix.settings.PhotoCrop
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import kotlin.math.min
import kotlin.math.roundToInt

/** The area of the sheet that frames the photo, for tests. */
const val PHOTO_FRAME_TAG = "profile_photo_frame"

/** How far one "move" of a screen reader's custom action shifts the window: a tenth of its own side. */
private const val NUDGE = 0.1f

/**
 * Frames a photo for the profile picture, shown as a bottom sheet: the photo behind a circle, dragged to move it and pinched
 * (or slid on the slider below) to zoom. Calls [onDone] with the crop to keep when "Use photo" is pressed, or null when the
 * sheet is closed without it. Nothing is saved here - the caller does that with the crop.
 */
@Composable
fun ProfilePhotoSheet(photo: Bitmap, onDone: (PhotoCrop?) -> Unit) {
    val width = photo.width
    val height = photo.height
    val image = remember(photo) { photo.asImageBitmap() }
    var crop by remember(photo) { mutableStateOf(PhotoCrop()) }
    var viewport by remember { mutableFloatStateOf(1f) }

    fun nudge(dx: Int, dy: Int): Boolean {
        val side = min(width, height) / crop.zoom
        crop = PhotoCrop(crop.zoom, crop.centerX + dx * NUDGE * side / width, crop.centerY + dy * NUDGE * side / height).fitted(width, height)
        return true
    }

    EditorSheet(
        title = "Position your photo",
        subtitle = "Drag to move it, pinch or slide to zoom. What is inside the circle becomes your picture.",
        saveLabel = "Use photo",
        onSave = { onDone(crop.fitted(width, height)) },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(AppRadius.lg))
                .background(Color.Black)
                .onSizeChanged { viewport = it.width.toFloat() }
                .pointerInput(photo) {
                    detectTransformGestures { _, pan, zoomBy, _ ->
                        crop = crop.transformed(pan.x, pan.y, zoomBy, viewport, width, height)
                    }
                }
                .semantics {
                    contentDescription = "Photo preview. Drag to move it."
                    customActions = listOf(
                        CustomAccessibilityAction("Move photo left") { nudge(-1, 0) },
                        CustomAccessibilityAction("Move photo right") { nudge(1, 0) },
                        CustomAccessibilityAction("Move photo up") { nudge(0, -1) },
                        CustomAccessibilityAction("Move photo down") { nudge(0, 1) },
                    )
                }
                .testTag(PHOTO_FRAME_TAG),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val window = crop.window(width, height)
                val scale = size.width / window.side
                drawImage(
                    image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(width, height),
                    dstOffset = IntOffset((-window.left * scale).roundToInt(), (-window.top * scale).roundToInt()),
                    dstSize = IntSize((width * scale).roundToInt(), (height * scale).roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
                // Dim everything the circle leaves out, then draw the circle's edge.
                val circle = Path().apply { addOval(Rect(Offset.Zero, size)) }
                clipPath(circle, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.55f)) }
                drawCircle(Color.White.copy(alpha = 0.9f), radius = size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
        }
        Spacer(Modifier.height(AppSpacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ZoomOut, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Slider(
                value = crop.zoom,
                onValueChange = { crop = crop.zoomedTo(it, width, height) },
                valueRange = PhotoCrop.MIN_ZOOM..PhotoCrop.MAX_ZOOM,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = "Zoom"
                        stateDescription = "${(crop.zoom * 100).roundToInt()}%"
                    },
            )
            Icon(Icons.Outlined.ZoomIn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
}
