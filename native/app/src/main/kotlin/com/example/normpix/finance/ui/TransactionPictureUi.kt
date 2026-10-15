package com.example.normpix.finance.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.normpix.finance.data.TransactionPictures
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.ShareNotice
import com.example.normpix.finance.share.SharedTransactionPrefill
import com.example.normpix.finance.share.decodePicture
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.solidColorScheme
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion
import java.io.File
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------ getting one

/** The two ways to get a picture to attach: [takePhoto] with the camera app, or [choose] one already on the phone. */
class PictureInput internal constructor(val takePhoto: () -> Unit, val choose: () -> Unit)

/** What the loading sheet says while a picture is prepared and read, for the form or for a share. */
internal const val READING_PICTURE = "Reading the picture"
internal const val READING_PICTURE_MESSAGE = "NormPix is reading it on this phone to fill in the transaction."

/** What a picked picture came to under the loading sheet: one that couldn't be opened, or one read and ready to attach. */
private sealed interface PickedPicture {
    data object Unreadable : PickedPicture
    class Ready(val attach: () -> Unit) : PickedPicture
}

/**
 * Starts the camera app or the system's photo picker, then - under a loading sheet ([AppOverlays.loading]) - stages
 * what comes back ([TransactionPictures.stage]) and hands the draft to [onPicked]. The camera writes into the app's own
 * cache, so no camera permission is needed, and the picker shows only what the user picks, so no storage permission
 * either. [onProblem] says what went wrong, in words for the user.
 */
@Composable
fun rememberPictureInput(
    store: TransactionPictures,
    /**
     * Reads the draft - and the picture it was made from, when that stays readable (the photo picker's; not the camera's,
     * which goes) - while the loading sheet is up, and gives back what attaching it does to the form, which is done once the
     * sheet has gone. It changes nothing itself: the sheet can be cancelled at any point until then, leaving the form as it was.
     */
    onPicked: suspend (draft: File, original: Uri?) -> (() -> Unit),
    onProblem: (String) -> Unit,
): PictureInput {
    val lock = LocalAppContainer.current.lock
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val problem by rememberUpdatedState(onProblem)
    // Where the camera app is writing, kept across the trip in case NormPix's screen is rebuilt meanwhile.
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }

    fun stage(uri: Uri, original: Uri?, afterwards: () -> Unit = {}) {
        scope.launch {
            // The draft until the form takes it: a cancelled or failed reading deletes it again.
            var draft: File? = null
            try {
                val outcome = try {
                    overlays.loading<PickedPicture>(READING_PICTURE, READING_PICTURE_MESSAGE) {
                        val staged = store.stage(uri) ?: return@loading PickedPicture.Unreadable
                        draft = staged
                        PickedPicture.Ready(picked(staged, original))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    PickedPicture.Unreadable
                }
                when (outcome) {
                    // Cancelled: the form stays as it was.
                    null -> Unit
                    PickedPicture.Unreadable -> problem("Couldn't open that picture. Try another one.")
                    is PickedPicture.Ready -> {
                        outcome.attach()
                        draft = null
                    }
                }
            } finally {
                store.discard(draft)
                afterwards()
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val capture = capturePath?.let(::File)
        capturePath = null
        if (capture == null) return@rememberLauncherForActivityResult
        // Cancelled in the camera app: nothing to say.
        if (taken) stage(Uri.fromFile(capture), original = null) { store.discard(capture) } else store.discard(capture)
    }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) stage(uri, original = uri)
    }
    return remember(store) {
        PictureInput(
            takePhoto = {
                val (file, uri) = store.newCaptureTarget()
                capturePath = file.path
                try {
                    // A trip to the camera app isn't "leaving" NormPix: don't lock the open form behind it.
                    lock.expectExternalActivity()
                    camera.launch(uri)
                } catch (_: Exception) {
                    store.discard(file)
                    capturePath = null
                    problem("Couldn't open the camera on this phone.")
                }
            },
            choose = {
                try {
                    lock.expectExternalActivity()
                    chooser.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (_: Exception) {
                    problem("Couldn't open your pictures on this phone.")
                }
            },
        )
    }
}

// ------------------------------------------------------------ showing one

/** [file] decoded for the screen, its longer side at most about [maxSide]; null while it loads or if it can't be read. */
@Composable
fun rememberPictureBitmap(file: File?, maxSide: Int): ImageBitmap? {
    val resolver = LocalContext.current.contentResolver
    val bitmap by produceState<ImageBitmap?>(null, file?.path) {
        value = null
        val source = file ?: return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                decodePicture(resolver, Uri.fromFile(source), maxSide)?.asImageBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } catch (_: OutOfMemoryError) {
                null
            }
        }
    }
    return bitmap
}

/**
 * The form's picture field, in the form's own tonal style. Without a picture: "Take a photo" and "Choose one" as one
 * split pill, like the date and time. With one: a [PictureCard] (which opens it whole) with Replace - the camera or a
 * picture, from the app's menu - and Remove at its end, after [onReadAgain] (when given): the picture read once more and
 * what it says put in the form. While a picture is prepared and read, a loading sheet is up
 * ([rememberPictureInput]); [errorText] says what went wrong getting one, under the field like any field's error.
 */
@Composable
fun TransactionPictureField(
    picture: File?,
    input: PictureInput,
    onView: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    onReadAgain: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        if (picture == null) {
            val (left, right) = remember { connectedFieldShapes() }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TonalPickerField(
                    label = "Picture",
                    value = "Take a photo",
                    icon = Icons.Outlined.PhotoCamera,
                    onClick = input.takePhoto,
                    modifier = Modifier.weight(1f),
                    shape = left,
                )
                TonalPickerField(
                    label = "Picture",
                    value = "Choose one",
                    icon = Icons.Outlined.Image,
                    onClick = input.choose,
                    modifier = Modifier.weight(1f),
                    shape = right,
                )
            }
        } else {
            PictureCard(picture, label = "Picture", value = "Attached", onView = onView) {
                onReadAgain?.let { ReadAgainButton(it) }
                ReplacePictureButton(input)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove picture")
                }
            }
        }
        when {
            errorText != null -> FieldError(errorText)
            picture == null -> Text(
                "A bill, receipt or payment screenshot. NormPix reads it on this phone to fill in what's still empty.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.xs),
            )
        }
    }
}

/**
 * An attached picture as a tonal card like the form's selection fields: its thumbnail where they have their icon, then
 * [label] and [value], and any [actions] at its end. The thumbnail and words are one button, which opens it whole.
 */
@Composable
fun PictureCard(
    picture: File,
    label: String,
    value: String,
    onView: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(TonalFieldShape)
            .background(scheme.surfaceContainerHighest)
            .padding(end = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(onClickLabel = "See it whole", role = Role.Button, onClick = onView)
                .padding(start = AppSpacing.sm, top = AppSpacing.sm, end = AppSpacing.sm, bottom = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PictureThumbnail(picture)
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/** Read again: the picture read once more by this version of NormPix, and what it says put in the form. Named on long press. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadAgainButton(onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text("Read again") } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) {
            Icon(Icons.Outlined.Autorenew, contentDescription = "Read the picture again")
        }
    }
}

/** Replace: a new photo or another picture, from the app's menu (solid, as nothing behind a popup is blurred). */
@Composable
private fun ReplacePictureButton(input: PictureInput) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.SwapHoriz, contentDescription = "Replace picture")
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = solidColorScheme.surfaceContainer,
            shape = AppRadius.rLg,
        ) {
            DropdownMenuItem(
                text = { Text("Take a photo") },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                onClick = {
                    open = false
                    input.takePhoto()
                },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            DropdownMenuItem(
                text = { Text("Choose a picture") },
                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                onClick = {
                    open = false
                    input.choose()
                },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

/** A small preview of [picture], cropped to fill it, in the badge's tone while it loads (or if it can't be read). */
@Composable
fun PictureThumbnail(picture: File, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val bitmap = rememberPictureBitmap(picture, THUMBNAIL_SIDE)
    Box(
        modifier
            .size(width = 48.dp, height = 64.dp)
            .clip(AppRadius.rSm)
            .background(scheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
    }
}

/** The most a picture zooms in, and how far a double tap or the + button takes it. */
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val ZOOM_STEP = 1.5f

/**
 * [picture] on its own, over the whole screen, the way the app's own pages are dressed: a dark stage, the app's glass circle
 * to close it at the top, and a toolbar at the bottom to zoom out, see how far it is zoomed (tap it to fit the picture
 * again), and zoom in. Pinch zooms and drag moves around a zoomed picture; a double tap zooms in on where it is tapped, or
 * back out. Zooming from the buttons glides; a zoomed picture only moves as far as its edges. Back or Close leaves.
 */
@Composable
fun PictureViewer(picture: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val bitmap = rememberPictureBitmap(picture, TransactionPictures.MAX_SIDE)
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var size by remember { mutableStateOf(IntSize.Zero) }
        val scope = rememberCoroutineScope()
        var gliding by remember { mutableStateOf<Job?>(null) }
        val reduced = rememberReducedMotion()

        // A zoomed picture moves only as far as its edges go.
        fun clamp(next: Offset, zoom: Float): Offset {
            val maxX = size.width * (zoom - 1) / 2
            val maxY = size.height * (zoom - 1) / 2
            return Offset(next.x.coerceIn(-maxX, maxX), next.y.coerceIn(-maxY, maxY))
        }

        /** Glides to [target] zoom, keeping the point [focus] (on screen; the middle when null) where it is. */
        fun zoomTo(target: Float, focus: Offset? = null) {
            val to = target.coerceIn(1f, MAX_ZOOM)
            val centre = Offset(size.width / 2f, size.height / 2f)
            // Where the picture point under [focus] is now, unzoomed; it should end up under [focus] again.
            val at = focus ?: centre
            val point = centre + (at - centre - offset) / scale
            val endOffset = if (to == 1f) Offset.Zero else clamp((at - centre) - (point - centre) * to, to)
            val fromScale = scale
            val fromOffset = offset
            gliding?.cancel()
            gliding = scope.launch {
                if (reduced) {
                    scale = to
                    offset = endOffset
                } else {
                    animate(0f, 1f, animationSpec = tween(AppMotion.MEDIUM_MS, easing = AppMotion.standard)) { t, _ ->
                        scale = fromScale + (to - fromScale) * t
                        offset = fromOffset + (endOffset - fromOffset) * t
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize().background(ViewerStage).onSizeChanged { size = it }) {
            if (bitmap != null) {
                Image(
                    bitmap,
                    contentDescription = "Attached picture",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { at -> if (scale > 1.01f) zoomTo(1f) else zoomTo(DOUBLE_TAP_ZOOM, at) })
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                gliding?.cancel()
                                scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                offset = if (scale == 1f) Offset.Zero else clamp(offset + pan, scale)
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            } else {
                AppLinearProgress(Modifier.align(Alignment.Center).width(120.dp), color = Color.White)
            }
            // The header: the app's glass circle to close, and what this is.
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewerButton(Icons.AutoMirrored.Rounded.ArrowBack, "Close", onDismiss)
                Spacer(Modifier.width(AppSpacing.md))
                Text("Picture", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            // The zoom tools, a glass pill at the bottom.
            if (bitmap != null) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(bottom = AppSpacing.lg)
                        .clip(AppRadius.rFull)
                        .background(ViewerGlass)
                        .border(1.dp, ViewerEdge, AppRadius.rFull)
                        .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewerToolButton(Icons.Rounded.ZoomOut, "Zoom out", enabled = scale > 1.01f) { zoomTo(scale / ZOOM_STEP) }
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 64.dp)
                            .clip(AppRadius.rFull)
                            .clickable(onClickLabel = "Fit the picture", role = Role.Button) { zoomTo(1f) }
                            .semantics { contentDescription = "Zoomed to ${(scale * 100).roundToInt()} percent. Tap to fit" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${(scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                    ViewerToolButton(Icons.Rounded.ZoomIn, "Zoom in", enabled = scale < MAX_ZOOM - 0.01f) { zoomTo(scale * ZOOM_STEP) }
                    ViewerToolButton(Icons.Rounded.FitScreen, "Fit to screen", enabled = scale > 1.01f) { zoomTo(1f) }
                }
            }
        }
    }
}

/** Behind the picture: near-black, so any picture reads, and the glass controls over it. */
private val ViewerStage = Color(0xFF0B0C0E)
private val ViewerGlass = Color.White.copy(alpha = 0.14f)
private val ViewerEdge = Color.White.copy(alpha = 0.18f)

/** The viewer's close button: the app's floating glass circle, in white over the dark stage. */
@Composable
private fun ViewerButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(ViewerGlass)
            .border(1.dp, ViewerEdge, CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun ViewerToolButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f))
    }
}

private const val THUMBNAIL_SIDE = 320

// --------------------------------------------------------- filling the form

/** What the form already has, for [pictureFill]: a value counts as given, and is never replaced by what a picture says. */
internal data class FormGaps(
    val type: TransactionType,
    /** The type may still change: a new form with nothing entered whose type the user hasn't picked. */
    val typeOpen: Boolean,
    val amountEmpty: Boolean,
    val payeeEmpty: Boolean,
    val noteEmpty: Boolean,
    val categoryEmpty: Boolean,
    /** The date is still the one a new form starts with, or has to be chosen. */
    val dateOpen: Boolean,
    /** The account is still the one a new form starts with. */
    val accountOpen: Boolean,
)

/**
 * What reading a picture attached in the form came to, said at the top of the form the way a share's draft says where
 * its values came from (a snackbar would sit under the sheet). [readText] is what was read, for the user to check.
 */
internal data class PictureNote(
    val title: String,
    val body: String,
    val notices: List<ShareNotice> = emptyList(),
    val readText: String? = null,
)

/** What a picture read in the form fills in; null fields stay as they are. */
internal data class PictureFill(
    val type: TransactionType? = null,
    val amount: Long? = null,
    val payee: String? = null,
    val note: String? = null,
    val categoryId: Long? = null,
    val occurredAt: LocalDateTime? = null,
    val accountId: Long? = null,
    /**
     * The picture shows an account's number that none of the user's accounts has: the account the form started with (the
     * default one) is a guess, so it is cleared for the user to choose.
     */
    val chooseAccount: Boolean = false,
    /** Something the picture says was left out because the form already had it. */
    val kept: Boolean = false,
) {
    /** "the amount, payee and date" - what was filled in, for the message; null when nothing was. */
    val summary: String?
        get() {
            val names = listOfNotNull(
                type?.let { "type" },
                amount?.let { "amount" },
                payee?.let { "payee" },
                categoryId?.let { "category" },
                occurredAt?.let { "date" },
                accountId?.let { "account" },
                note?.let { "reference" },
            )
            return when (names.size) {
                0 -> null
                1 -> "the ${names[0]}"
                else -> "the ${names.dropLast(1).joinToString(", ")} and ${names.last()}"
            }
        }
}

/**
 * What a picture read while the user fills in the form ([found], from the same reading a shared picture gets) adds to
 * it: only what the form doesn't have yet ([gaps]), so nothing the user entered or chose is overwritten. The type
 * changes only on a form with nothing entered yet; a transfer takes no payee or category.
 */
internal fun pictureFill(found: SharedTransactionPrefill, gaps: FormGaps): PictureFill {
    val type = found.type?.takeIf { gaps.typeOpen && it != gaps.type }
    val finalType = type ?: gaps.type
    val transfer = finalType == TransactionType.Transfer
    // A category read for the picture's direction only fits a form going the same way.
    val categoryFits = found.type == finalType
    val kept = (found.type != null && found.type != gaps.type && !gaps.typeOpen) ||
        (found.amount != null && !gaps.amountEmpty) ||
        (found.payee != null && !gaps.payeeEmpty && !transfer) ||
        (found.note != null && !gaps.noteEmpty) ||
        (found.categoryId != null && !gaps.categoryEmpty && !transfer && categoryFits) ||
        (found.occurredAt != null && !gaps.dateOpen) ||
        (found.accountId != null && !gaps.accountOpen)
    return PictureFill(
        type = type,
        amount = found.amount?.takeIf { gaps.amountEmpty },
        payee = found.payee?.takeIf { gaps.payeeEmpty && !transfer },
        note = found.note?.takeIf { gaps.noteEmpty },
        categoryId = found.categoryId?.takeIf { gaps.categoryEmpty && !transfer && categoryFits },
        occurredAt = found.occurredAt?.takeIf { gaps.dateOpen },
        accountId = found.accountId?.takeIf { gaps.accountOpen },
        chooseAccount = found.accountNeedsChoice && found.accountId == null && gaps.accountOpen && !transfer,
        kept = kept,
    )
}

/** What the form holds, for [pictureRefill]. */
internal data class FormValues(
    /** Null while the user hasn't said expense or income. */
    val type: TransactionType?,
    val amount: Long?,
    val payee: String,
    val note: String,
    val categoryId: Long?,
    /** Null while the date has to be chosen. */
    val occurredAt: LocalDateTime?,
    val accountId: Long?,
)

/**
 * What reading the attached picture again ([found]) changes in the form ([now]). Unlike [pictureFill] - which fills only
 * what is empty - what the picture says takes the place of what the form has: it is asked for to put right a picture an
 * earlier NormPix read wrongly, so the form has to follow the picture. What a picture can't say stays: the category the
 * user chose (unless the type changes from under it), the note they wrote, a transfer's type, and a time it doesn't show.
 */
internal fun pictureRefill(found: SharedTransactionPrefill, now: FormValues): PictureFill {
    val transfer = now.type == TransactionType.Transfer
    val type = found.type?.takeIf { !transfer && it != now.type }
    val finalType = type ?: now.type
    val categoryFits = found.type == finalType
    val occurredAt = found.occurredAt?.let { read ->
        if (found.timeRead || now.occurredAt == null) read else LocalDateTime.of(read.toLocalDate(), now.occurredAt.toLocalTime())
    }
    return PictureFill(
        type = type,
        amount = found.amount?.takeIf { it != now.amount },
        payee = found.payee?.takeIf { !transfer && it != now.payee },
        note = found.note?.takeIf { now.note.isBlank() },
        categoryId = found.categoryId?.takeIf { !transfer && categoryFits && (now.categoryId == null || type != null) && it != now.categoryId },
        occurredAt = occurredAt?.takeIf { it != now.occurredAt },
        accountId = found.accountId?.takeIf { it != now.accountId },
    )
}
