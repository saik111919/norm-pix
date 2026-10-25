package com.example.normpix.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.EmojiPicker
import com.example.normpix.finance.ui.FormGroupLabel
import com.example.normpix.finance.ui.TonalFieldShape
import com.example.normpix.finance.ui.typedFieldColors
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.settings.AccountButtonTarget
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.settings.MAX_PROFILE_NAME
import com.example.normpix.settings.PhotoCrop
import com.example.normpix.settings.ProfilePhotos
import com.example.normpix.ui.components.AppPrimaryTabs
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.AppTab
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalDetailPane
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.ProfileAvatar
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.politeLiveRegion
import com.example.normpix.ui.components.profileInitial
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.components.rememberBackButtonStyle
import com.example.normpix.ui.theme.AppSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Settings > Profile: what the user likes to be called and the picture that goes with it (their own photo, an emoji, or their
 * initial), and where the round account button at the top of each tab leads. Everything applies as it is changed, like the
 * rest of Settings, and stays on this device.
 *
 * A photo is chosen with the system's photo picker (no permission needed), framed in a sheet, and a small copy of the part
 * that shows is kept; it takes the place of the emoji and the initial until it is removed, which brings them back as they
 * were left.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePage() {
    val navigator = LocalNavigator.current
    val container = LocalAppContainer.current
    val preferences = container.preferences
    val prefs by preferences.state.collectAsState()
    val haptics = rememberAppHaptics()
    val context = LocalContext.current
    val photos = remember(context) { ProfilePhotos(context) }
    val overlays = LocalOverlays.current
    val appScope = LocalAppScope.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var photoProblem by remember { mutableStateOf<String?>(null) }
    val hasPhoto = prefs.profilePhoto.isNotBlank()

    // Reads the picked picture, lets the user frame it, and keeps the part they framed. Saving goes on in the app's own scope,
    // so leaving the page half way through does not leave a photo saved and not used.
    fun usePhoto(source: Uri) {
        scope.launch {
            busy = true
            photoProblem = null
            val opened = try {
                photos.open(source)
            } finally {
                busy = false
            }
            if (opened == null) {
                photoProblem = "Couldn't open that picture. Try another one."
                return@launch
            }
            val crop = overlays.sheet<PhotoCrop> { dismiss -> ProfilePhotoSheet(opened, dismiss) } ?: return@launch
            appScope.launch {
                busy = true
                try {
                    val name = photos.save(opened, crop)
                    preferences.setProfilePhoto(name)
                    photos.keepOnly(name)
                    haptics.success()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    photoProblem = "Couldn't save that photo. Try again."
                } catch (_: OutOfMemoryError) {
                    photoProblem = "That picture is too large to use. Try another one."
                } finally {
                    busy = false
                }
            }
        }
    }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) usePhoto(uri)
    }

    fun choosePhoto() {
        photoProblem = null
        try {
            // The picker is another app's screen: a trip there is not leaving NormPix, so it must not lock behind it.
            container.lock.expectExternalActivity()
            chooser.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: Exception) {
            photoProblem = "Couldn't open your pictures on this phone."
        }
    }

    fun removePhoto() {
        photoProblem = null
        preferences.setProfilePhoto("")
        appScope.launch { photos.deleteAll() }
    }

    // The text as typed. The saved name is trimmed, and the space typed on the way to a second word must survive until it
    // arrives, so the field keeps its own copy and the preferences are written from it.
    var name by rememberSaveable { mutableStateOf(prefs.profileName) }
    var emoji by rememberSaveable { mutableStateOf(prefs.profileEmoji) }
    var pictureTab by rememberSaveable { mutableIntStateOf(if (prefs.profileEmoji.isNotBlank()) 1 else 0) }
    // Switching to "Initial" keeps the emoji picked, so switching back finds it where it was.
    val shownEmoji = emoji.takeIf { pictureTab == 1 }.orEmpty()
    fun save(newName: String, newEmoji: String) = preferences.setProfile(newName, newEmoji)

    AppSubPage(title = "Profile", onBack = navigator::back, imeAware = true) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProfileAvatar(name = name, emoji = shownEmoji, size = 96.dp, photo = prefs.profilePhoto)
                    Spacer(Modifier.height(AppSpacing.md))
                    Text(
                        name.trim().ifEmpty { "Add your name" },
                        style = MaterialTheme.typography.titleLarge,
                        color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(AppSpacing.md))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    ) {
                        FilledTonalButton(onClick = ::choosePhoto, enabled = !busy) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AppSpacing.sm))
                            Text(if (hasPhoto) "Change photo" else "Choose a photo")
                        }
                        if (hasPhoto) {
                            TextButton(onClick = ::removePhoto, enabled = !busy) { Text("Remove photo") }
                        }
                    }
                    if (busy) {
                        Text(
                            "Getting your photo ready…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.politeLiveRegion(),
                        )
                    }
                    photoProblem?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.politeLiveRegion())
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = capped(it)
                        save(name, shownEmoji)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your name") },
                    placeholder = { Text("What should NormPix call you?") },
                    supportingText = { Text("${name.trim().length}/$MAX_PROFILE_NAME · Stays on this device") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    colors = typedFieldColors(),
                    shape = TonalFieldShape,
                )
                Spacer(Modifier.height(AppSpacing.lg))
                FormGroupLabel("Picture")
                if (hasPhoto) {
                    Text(
                        "Your photo is shown instead of an emoji or your initial. Remove it to go back to them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    AppPrimaryTabs(
                        tabs = remember { listOf(AppTab("Initial"), AppTab("Emoji")) },
                        selectedIndex = pictureTab,
                        onSelect = { tab ->
                            pictureTab = tab
                            save(name, if (tab == 1) emoji else "")
                        },
                    )
                    Spacer(Modifier.height(AppSpacing.md))
                    if (pictureTab == 0) {
                        Text(
                            profileInitial(name)?.let { "Shows \"$it\", the first letter of your name." }
                                ?: "Add your name and its first letter becomes your picture.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        EmojiPicker(
                            selected = emoji.ifBlank { null },
                            onSelect = { picked ->
                                emoji = picked
                                save(name, picked)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(AppSpacing.xl))
                ListSection(
                    title = "Account button",
                    footer = if (rememberBackButtonStyle() == BackButtonStyle.TopFloatingCircle) {
                        "The round button at the top of Home, Plans, Insights and Wallet. It wears your picture."
                    } else if (prefs.bottomControls) {
                        "The round button is hidden while Bottom controls is on (a phone), set in Appearance. Settings is in the dock's More menu."
                    } else {
                        "The round button shows at the top of each tab with the Top floating circle style, set in Appearance."
                    },
                    modifier = Modifier.selectableGroup(),
                ) {
                    for (target in AccountButtonTarget.entries) {
                        item {
                            RadioRow(
                                headline = target.label,
                                supporting = target.description,
                                selected = prefs.accountButtonTarget == target,
                                onClick = {
                                    haptics.selection()
                                    preferences.setAccountButtonTarget(target)
                                },
                            )
                        }
                    }
                }
                // Beside the Settings list (a tablet) the way to Settings is the list itself.
                if (LocalDetailPane.current == null) {
                    Spacer(Modifier.height(AppSpacing.lg))
                    ListSection {
                        item {
                            SettingsRow(
                                headline = "Settings",
                                supporting = "Appearance, finance, data, security and more",
                                icon = Icons.Outlined.Settings,
                                hue = null,
                                onClick = { navigator.openSettings() },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The name as far as the profile keeps it, never cutting a character that takes two UTF-16 units (an emoji in a name) in half. */
internal fun capped(text: String): String {
    if (text.length <= MAX_PROFILE_NAME) return text
    val cut = text.take(MAX_PROFILE_NAME)
    return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}
