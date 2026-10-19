package com.example.normpix.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.CustomFont
import com.example.normpix.settings.FontInstall
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the snackbar says when the picked file is not a font NormPix can use (not a font, or one Android cannot load). */
const val NOT_A_FONT_MESSAGE = "That file isn't a font NormPix can use."

/** What it says when the file is bigger than [CustomFont.MAX_BYTES] allows. */
const val FONT_TOO_BIG_MESSAGE = "That font file is too big. NormPix takes fonts up to 10 MB."

/** What it says when the file could not be read at all. */
const val FONT_UNREADABLE_MESSAGE = "Couldn't read that file. Try again."

/** What the Interface font page can do with the user's own font: choose a file for it, or let it go. */
@Immutable
class CustomFontActions(
    /** Opens the system file picker; a good file becomes the interface font, a bad one is said so in a snackbar and changes nothing. */
    val choose: () -> Unit,
    /** Forgets the saved font and goes back to GS Flex. */
    val remove: () -> Unit,
)

/**
 * The picker and the clean-up behind "Your font". The pick is the system's own document picker (any file type, so a file manager
 * that mislabels `.ttf` and `.otf` still shows them); what comes back is checked and copied by [CustomFont.import] off the main
 * thread, in the app's own scope so a copy that has started finishes even if the page is left. The lock does not engage while
 * the picker is open.
 */
@Composable
internal fun rememberCustomFontActions(): CustomFontActions {
    val context = LocalContext.current.applicationContext
    val container = LocalAppContainer.current
    val snackbars = LocalSnackbars.current
    val scope = LocalAppScope.current
    val haptics = rememberAppHaptics()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val picked = CustomFont.import(context, uri)
                when (picked.install) {
                    FontInstall.Saved -> {
                        container.preferences.useCustomFont(picked.name)
                        haptics.success()
                    }
                    FontInstall.NotAFont -> snackbars.show(NOT_A_FONT_MESSAGE, SnackbarKind.Error)
                    FontInstall.TooLarge -> snackbars.show(FONT_TOO_BIG_MESSAGE, SnackbarKind.Error)
                    FontInstall.Failed -> snackbars.show(FONT_UNREADABLE_MESSAGE, SnackbarKind.Error)
                }
            }
        }
    }
    return remember(launcher, container, context, scope, snackbars) {
        CustomFontActions(
            choose = {
                // The system picker isn't "leaving" NormPix: don't re-lock while it's open.
                container.lock.expectExternalActivity()
                launcher.launch(arrayOf("*/*"))
            },
            remove = {
                container.preferences.clearCustomFont()
                scope.launch {
                    withContext(Dispatchers.IO) { CustomFont.delete(context) }
                    snackbars.show("Your font was removed", SnackbarKind.Deleted)
                }
            },
        )
    }
}

/**
 * The "Your font" panel of the Interface font card: before a file is chosen, what to pick and a button to pick it; after, the
 * file's name and whether it is in use, with Replace and Remove. [file] is the font as the app draws with it (null while it
 * is not usable: the text is then in GS Flex, and the panel says so).
 */
@Composable
internal fun CustomFontPanel(prefs: AppPrefsState, file: File?, actions: CustomFontActions) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(AppSpacing.md))
        if (prefs.customFontName.isBlank()) {
            Text(
                "Pick a .ttf or .otf font file from your phone, up to 10 MB. It is copied into NormPix, so it stays when you move the original.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AppSpacing.md))
            Button(onClick = actions.choose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Choose a font file") }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(AppRadius.rLg)
                    .background(scheme.surfaceContainerHighest)
                    .padding(AppSpacing.lg)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.FileOpen, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Column(Modifier.padding(start = AppSpacing.md).weight(1f)) {
                    Text(
                        prefs.customFontName,
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (file != null) "In use" else "Can't be read, so NormPix is using GS Flex",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (file != null) scheme.onSurfaceVariant else scheme.error,
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedButton(onClick = actions.choose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Replace") }
                TextButton(onClick = actions.remove, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove") }
            }
        }
    }
}
