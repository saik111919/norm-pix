package com.example.normpix.finance.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
import com.example.normpix.ui.components.politeLiveRegion
import com.example.normpix.ui.components.solidColorScheme
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------ getting one

/** The two ways to get a picture to attach: [takePhoto] with the camera app, or [choose] one already on the phone. */
class PictureInput internal constructor(val takePhoto: () -> Unit, val choose: () -> Unit)

/**
 * Starts the camera app or the system's photo picker, then stages what comes back ([TransactionPictures.stage]) and
 * hands the draft to [onPicked]. The camera writes into the app's own cache, so no camera permission is needed, and
 * the picker shows only what the user picks, so no storage permission either. [onBusy] is true while a picture is
 * being prepared; [onProblem] says what went wrong, in words for the user.
 */
@Composable
fun rememberPictureInput(
    store: TransactionPictures,
    onBusy: (Boolean) -> Unit,
    onPicked: (File) -> Unit,
    onProblem: (String) -> Unit,
): PictureInput {
    val lock = LocalAppContainer.current.lock
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val problem by rememberUpdatedState(onProblem)
    val busy by rememberUpdatedState(onBusy)
    // Where the camera app is writing, kept across the trip in case NormPix's screen is rebuilt meanwhile.
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }

    fun stage(uri: Uri, afterwards: () -> Unit = {}) {
        scope.launch {
            busy(true)
            try {
                val draft = store.stage(uri)
                if (draft != null) picked(draft) else problem("Couldn't open that picture. Try another one.")
            } finally {
                afterwards()
                busy(false)
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val capture = capturePath?.let(::File)
        capturePath = null
        if (capture == null) return@rememberLauncherForActivityResult
        // Cancelled in the camera app: nothing to say.
        if (taken) stage(Uri.fromFile(capture)) { store.discard(capture) } else store.discard(capture)
    }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) stage(uri)
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
 * picture, from the app's menu - and Remove at its end. [busy] shows that a picture is being prepared and read;
 * [errorText] says what went wrong getting one, under the field like any field's error.
 */
@Composable
fun TransactionPictureField(
    picture: File?,
    busy: Boolean,
    input: PictureInput,
    onView: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
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
                    onClick = { if (!busy) input.takePhoto() },
                    modifier = Modifier.weight(1f),
                    shape = left,
                )
                TonalPickerField(
                    label = "Picture",
                    value = "Choose one",
                    icon = Icons.Outlined.Image,
                    onClick = { if (!busy) input.choose() },
                    modifier = Modifier.weight(1f),
                    shape = right,
                )
            }
        } else {
            PictureCard(picture, label = "Picture", value = "Attached", onView = onView) {
                ReplacePictureButton(input, enabled = !busy)
                IconButton(onClick = onRemove, enabled = !busy) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove picture")
                }
            }
        }
        when {
            busy -> {
                Text(
                    "Reading the picture…",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppSpacing.sm, bottom = AppSpacing.xs).politeLiveRegion(),
                )
                AppLinearProgress(Modifier.fillMaxWidth())
            }
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

/** Replace: a new photo or another picture, from the app's menu (solid, as nothing behind a popup is blurred). */
@Composable
private fun ReplacePictureButton(input: PictureInput, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) {
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

/**
 * [picture] on its own, over the whole screen: pinch to zoom (up to 5x) and drag to move around a zoomed picture;
 * double-tap zooms in, or back out. Back or the close button leaves.
 */
@Composable
fun PictureViewer(picture: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val bitmap = rememberPictureBitmap(picture, TransactionPictures.MAX_SIDE)
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var size by remember { mutableStateOf(IntSize.Zero) }

        // A zoomed picture moves only as far as its edges go.
        fun clamp(next: Offset, zoom: Float): Offset {
            val maxX = size.width * (zoom - 1) / 2
            val maxY = size.height * (zoom - 1) / 2
            return Offset(next.x.coerceIn(-maxX, maxX), next.y.coerceIn(-maxY, maxY))
        }

        Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { size = it }) {
            if (bitmap != null) {
                Image(
                    bitmap,
                    contentDescription = "Attached picture",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
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
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(AppSpacing.sm),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
            }
        }
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
