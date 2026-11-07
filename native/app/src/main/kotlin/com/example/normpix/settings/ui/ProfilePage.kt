package com.example.normpix.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import com.example.normpix.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.EmojiPicker
import com.example.normpix.finance.ui.FormGroupLabel
import com.example.normpix.finance.ui.TonalFieldShape
import com.example.normpix.finance.ui.typedFieldColors
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.settings.AccountButtonTarget
import com.example.normpix.settings.AvatarPreset
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.settings.GifProblem
import com.example.normpix.settings.MAX_PROFILE_NAME
import com.example.normpix.settings.PhotoCrop
import com.example.normpix.settings.ProfilePhotos
import com.example.normpix.settings.isAnimatedPhotoName
import com.example.normpix.ui.components.AppPrimaryTabs
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.AppTab
import com.example.normpix.ui.components.AssetBitmaps
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalDetailPane
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.ProfileAvatar
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.StatusTag
import com.example.normpix.ui.components.politeLiveRegion
import com.example.normpix.ui.components.profileInitial
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.components.rememberBackButtonStyle
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // An animated picture is kept as it came, up to a size: framing it would end its movement, so the circle crops it to fit.
    fun keepGif(source: Uri) {
        appScope.launch {
            busy = true
            photoProblem = null
            try {
                val saved = photos.saveGif(source)
                val name = saved.name
                if (name == null) {
                    photoProblem = if (saved.problem == GifProblem.TooLarge) {
                        "That GIF is too big. NormPix keeps animated pictures up to ${ProfilePhotos.MAX_GIF_BYTES / (1024 * 1024)} MB."
                    } else {
                        "Couldn't open that picture. Try another one."
                    }
                } else {
                    preferences.setProfilePhoto(name)
                    photos.keepOnly(name)
                    haptics.success()
                }
            } finally {
                busy = false
            }
        }
    }

    // One of the pictures that come with NormPix, framed as it says and kept as a photo is.
    fun useAvatar(preset: AvatarPreset) {
        appScope.launch {
            busy = true
            photoProblem = null
            try {
                val name = photos.savePreset(preset)
                preferences.setProfilePhoto(name)
                photos.keepOnly(name)
                haptics.success()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                photoProblem = "Couldn't use that avatar. Try again."
            } finally {
                busy = false
            }
        }
    }

    fun usePhoto(source: Uri) {
        scope.launch {
            if (photos.isGif(source)) {
                keepGif(source)
                return@launch
            }
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
                ProfileHero(
                    name = name,
                    emoji = shownEmoji,
                    photo = prefs.profilePhoto,
                    busy = busy,
                    problem = photoProblem,
                    onChoose = ::choosePhoto,
                    onRemove = ::removePhoto,
                )
                Spacer(Modifier.height(AppSpacing.lg))
                AvatarChoices(enabled = !busy, onPick = ::useAvatar)
                Spacer(Modifier.height(AppSpacing.lg))
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

/** The avatar as it is shown on the Profile page: a large one, ringed in the page's colour, with the camera on its edge. */
private val HeroAvatarSize = 120.dp

/**
 * The top of the Profile page: a tonal card with the picture large in the middle - ringed, with a camera badge on its edge that
 * opens the picker as the button under it does - then the name, what the picture is in words (never the picture alone), and the
 * buttons for it. A GIF plays here, as everywhere the picture is worn.
 */
@Composable
private fun ProfileHero(
    name: String,
    emoji: String,
    photo: String,
    busy: Boolean,
    problem: String?,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hasPhoto = photo.isNotBlank()
    Surface(Modifier.fillMaxWidth(), shape = AppRadius.rXxl, color = scheme.surfaceContainer) {
        Column(
            // What does change the card's height (a problem to report, the photo's own buttons) grows and shrinks on the app's
            // spring rather than snapping, so the page under it eases along instead of jumping.
            Modifier.fillMaxWidth().animateContentSize(AppMotion.spatial()).padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                ProfileAvatar(
                    name = name,
                    emoji = emoji,
                    size = HeroAvatarSize,
                    photo = photo,
                    modifier = Modifier.border(4.dp, scheme.surface, CircleShape),
                )
                FilledIconButton(
                    onClick = onChoose,
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.BottomEnd).size(48.dp).border(3.dp, scheme.surfaceContainer, CircleShape),
                ) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = "Pick a new photo", modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(AppSpacing.lg))
            Text(
                name.trim().ifEmpty { "Add your name" },
                style = MaterialTheme.typography.headlineSmall,
                color = if (name.isBlank()) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(AppSpacing.sm))
            // While a picture is being kept the tag says so, in its own place: a line of its own under the card's buttons came and
            // went in a moment, and everything under the card jumped down and back up with it (owner, 2026-11-01).
            StatusTag(
                label = when {
                    busy -> "Getting your photo ready…"
                    isAnimatedPhotoName(photo) -> "Showing your GIF"
                    hasPhoto -> "Showing your photo"
                    emoji.isNotBlank() -> "Showing your emoji"
                    else -> "Showing your initial"
                },
                icon = when {
                    busy -> Icons.Outlined.HourglassTop
                    hasPhoto -> Icons.Outlined.PhotoLibrary
                    else -> Icons.Outlined.Face
                },
                modifier = Modifier.politeLiveRegion(),
            )
            Spacer(Modifier.height(AppSpacing.md))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                FilledTonalButton(onClick = onChoose, enabled = !busy) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(if (hasPhoto) "Change photo" else "Choose a photo")
                }
                if (hasPhoto) {
                    TextButton(onClick = onRemove, enabled = !busy) { Text("Remove photo") }
                }
            }
            Text(
                "A photo is framed to fit the circle. A GIF is kept as it is and keeps moving.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = AppSpacing.sm),
            )
            problem?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = AppSpacing.sm).politeLiveRegion(),
                )
            }
        }
    }
}

/** The size of an avatar to choose from. */
private val AvatarThumbSize = 72.dp

/** The pictures that come with NormPix, in a row to wear one with a tap: no photo to find, nothing to frame. */
@Composable
private fun AvatarChoices(enabled: Boolean, onPick: (AvatarPreset) -> Unit) {
    ListSection(
        title = "Avatars",
        footer = "Tap one to wear it. It takes the place of the photo you have now, and you can change it any time.",
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(AppSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
            ) {
                for (preset in AvatarPreset.entries) AvatarThumb(preset, enabled) { onPick(preset) }
            }
        }
    }
}

@Composable
internal fun AvatarThumb(preset: AvatarPreset, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current.applicationContext
    val image by produceState(AssetBitmaps.cached(preset.asset), preset) {
        if (value == null) value = withContext(Dispatchers.IO) { AssetBitmaps.load(context, preset.asset) }
    }
    Column(Modifier.width(AvatarThumbSize + AppSpacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(AvatarThumbSize)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHighest)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "Wear the ${preset.label} avatar" },
        ) {
            image?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // The same part the kept copy shows: the robot's face is high up.
                    alignment = if (preset.crop.centerY < 0.5f) Alignment.TopCenter else Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            preset.label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = AppSpacing.xs).clearAndSetSemantics {},
        )
    }
}

/** The name as far as the profile keeps it, never cutting a character that takes two UTF-16 units (an emoji in a name) in half. */
internal fun capped(text: String): String {
    if (text.length <= MAX_PROFILE_NAME) return text
    val cut = text.take(MAX_PROFILE_NAME)
    return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}
