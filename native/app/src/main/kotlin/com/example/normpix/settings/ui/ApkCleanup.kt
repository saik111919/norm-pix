package com.example.normpix.settings.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.ConfirmSheetContent
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.updates.APK_MIME_TYPE
import com.example.normpix.updates.AndroidApkFileStore
import com.example.normpix.updates.ApkFileStore
import com.example.normpix.updates.PickedApk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The files [rememberApkCleanup] reads and deletes; null (the app's) is the phone's own, a test brings one of its own. */
val LocalApkFileStore = compositionLocalOf<ApkFileStore?> { null }

/** The most of the list of picked files the sheet shows at once; a longer one scrolls, so the buttons stay in reach. */
private val FileListMaxHeight = 260.dp

/**
 * Settings > Automatic updates > "Delete downloaded APK files": opens the system's document picker for APKs (several at once)
 * and, with what is picked, [cleanUpApks]. Returns what the row calls. The picker is the system's own, so NormPix needs no
 * storage permission, and it only ever touches files the user picks.
 */
@Composable
internal fun rememberApkCleanup(): () -> Unit {
    val container = LocalAppContainer.current
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val scope = LocalAppScope.current
    val context = LocalContext.current.applicationContext
    val store = LocalApkFileStore.current ?: remember(context) { AndroidApkFileStore(context) }
    val picked = rememberUpdatedState { uris: List<Uri> ->
        scope.launch { cleanUpApks(uris, store, overlays, snackbars) }
        Unit
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) picked.value(uris)
    }
    return remember(picker, container) {
        {
            // The system picker isn't "leaving" NormPix: don't re-lock while it's open.
            container.lock.expectExternalActivity()
            try {
                picker.launch(arrayOf(APK_MIME_TYPE))
            } catch (_: ActivityNotFoundException) {
                // A phone without the system's file picker (some stripped-down builds): say so rather than fail.
                scope.launch {
                    overlays.alert(
                        title = "No file picker",
                        message = "This phone has no app for choosing files. Delete the APK in a file manager instead.",
                        icon = Icons.Outlined.ErrorOutline,
                    )
                }
            }
        }
    }
}

/**
 * What happens to the files the user picked: asks first (a sheet listing each one, with its size, and a note on any that is not
 * named like a NormPix update - it is still deleted if the user says so), deletes them, then says how it went in a snackbar
 * once the sheet has closed. A file the provider says cannot be deleted is listed, left out of the count and counted as not
 * deleted; if none can be, an alert says so instead. A file that fails to delete leaves the others alone. Nothing is logged:
 * a file's name is the user's.
 */
internal suspend fun cleanUpApks(uris: List<Uri>, store: ApkFileStore, overlays: AppOverlays, snackbars: AppSnackbars) {
    val files = try {
        store.describe(uris.distinct())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }
    if (files.isEmpty()) return
    val deletable = files.filter { it.canDelete != false }
    if (deletable.isEmpty()) {
        overlays.alert(
            title = "Can't delete from here",
            message = "Android doesn't let NormPix delete ${if (files.size == 1) "this file" else "these files"}. " +
                "Delete ${if (files.size == 1) "it" else "them"} in the Files app instead.",
            icon = Icons.Outlined.ErrorOutline,
        )
        return
    }
    val confirmed = overlays.sheet<Boolean> { dismiss ->
        ConfirmSheetContent(
            title = "Delete downloaded APK files?",
            message = "They are removed from your phone for good. NormPix only deletes the ${if (files.size == 1) "file" else "files"} you picked.",
            confirmLabel = "Delete ${fileCount(deletable.size)}",
            cancelLabel = "Cancel",
            destructive = true,
            icon = Icons.Outlined.DeleteSweep,
            onConfirm = { dismiss(true) },
            onCancel = { dismiss(false) },
            body = { PickedApkList(files) },
        )
    } ?: false
    if (!confirmed) return

    var deleted = 0
    var failed = files.size - deletable.size
    for (file in deletable) {
        val gone = try {
            store.delete(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (gone) deleted++ else failed++
    }
    when {
        failed == 0 -> snackbars.show("Deleted ${fileCount(deleted)}", SnackbarKind.Deleted)
        deleted == 0 -> snackbars.show(
            if (failed == 1) "Couldn't delete the file" else "Couldn't delete the files",
            SnackbarKind.Warning,
        )
        else -> snackbars.show("Deleted ${fileCount(deleted)}; $failed couldn't be deleted", SnackbarKind.Warning)
    }
}

private fun fileCount(n: Int) = if (n == 1) "1 file" else "$n files"

/** The picked files, one row each: its name, its size and anything to know about it. Scrolls past [FileListMaxHeight]. */
@Composable
private fun PickedApkList(files: List<PickedApk>) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().heightIn(max = FileListMaxHeight).verticalScroll(rememberScrollState())) {
        for (file in files) {
            val details = listOfNotNull(
                file.size?.let { Formatter.formatShortFileSize(context, it) },
                "Not named like a NormPix update".takeUnless { file.looksLikeNormPix },
                "Can't be deleted from here".takeIf { file.canDelete == false },
            ).joinToString(" · ")
            ListRow(
                headline = file.name,
                supporting = details.ifEmpty { null },
                supportingMaxLines = 3,
                leading = { LeadingIcon(Icons.Outlined.InstallMobile) },
            )
        }
    }
}
