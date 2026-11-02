package com.example.normpix.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.ConnectedChoiceRow
import com.example.normpix.finance.ui.Hero
import com.example.normpix.finance.ui.TonalFieldShape
import com.example.normpix.finance.ui.typedFieldColors
import com.example.normpix.reminders.ReminderNotifications
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.AppIcons
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.AvatarPreset
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.MAX_PROFILE_NAME
import com.example.normpix.settings.ProfilePhotos
import com.example.normpix.settings.ui.AppIconArt
import com.example.normpix.settings.ui.AvatarThumb
import com.example.normpix.settings.ui.themeOptions
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalPermissions
import com.example.normpix.ui.components.ProfileAvatar
import com.example.normpix.ui.components.politeLiveRegion
import com.example.normpix.ui.components.rememberAppHaptics
import kotlinx.coroutines.launch

/** How many steps the tour has. */
internal const val ONBOARDING_STEPS = 4

private val ScreenPadding = 20.dp
private val CardShape = RoundedCornerShape(24.dp)

/** Room the bar of dots and the button takes at the foot, so a step's content ends above it. */
private val BarRoom = 88.dp

/**
 * The welcome tour a new install opens to: four steps in a pager - what NormPix is and how it should look, who to call you, the
 * reminders' notifications, and where to start - ending in the app. The look is chosen on the first step and shows at once, so the
 * tour is already in the colours the app will have. Nothing here is required: every step can be left as it is, and "Skip" goes
 * straight to the app. Settings > About > Welcome tour plays it again; what it sets is all in Settings too.
 *
 * Inspired by DropSauce's onboarding (a pager, a hero icon on each step, animated dots and one button that is an arrow until the
 * last step) and made of this app's own parts. It covers the app, and takes every touch, until [onFinished].
 */
@Composable
fun OnboardingTour(preferences: AppPreferences, onFinished: () -> Unit) {
    val prefs by preferences.state.collectAsState()
    val pager = rememberPagerState(pageCount = { ONBOARDING_STEPS })
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val onLast by remember { derivedStateOf { pager.currentPage == ONBOARDING_STEPS - 1 } }

    // Back goes to the step before; on the first step it is the app's own back, as on any screen.
    BackHandler(enabled = pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The app is composed underneath, ready: nothing of it may be touched through the tour.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { paneTitle = "Welcome to NormPix" },
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { step ->
            when (step) {
                0 -> LookStep(preferences, prefs.mode, prefs.colorSource, prefs.accent)
                1 -> ProfileStep(preferences, prefs.profileName, prefs.profileEmoji, prefs.profilePhoto)
                2 -> NotificationsStep()
                else -> StartStep()
            }
        }
        if (!onLast) {
            TextButton(
                onClick = onFinished,
                modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(end = 8.dp, top = 4.dp),
            ) { Text("Skip") }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = ScreenPadding, end = ScreenPadding, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepDots(current = pager.currentPage)
            FloatingActionButton(
                onClick = {
                    haptics.selection()
                    if (onLast) onFinished() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    if (onLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = if (onLast) "Start using NormPix" else "Next",
                )
            }
        }
    }
}

/** The dots: the one for the step you are on is a longer pill in the primary colour, and the change is a spring. */
@Composable
private fun StepDots(current: Int) {
    Row(
        Modifier.semantics { contentDescription = "Step ${current + 1} of $ONBOARDING_STEPS" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(ONBOARDING_STEPS) { index ->
            val selected = index == current
            val width by animateDpAsState(
                if (selected) 24.dp else 8.dp,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "dot_$index",
            )
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                label = "dot_color_$index",
            )
            Box(Modifier.size(width = width, height = 8.dp).clip(CircleShape).background(color))
        }
    }
}

/**
 * What every step shares, in the app's own language: Home's hero card (the primary colour with the cookie bleeding off its corner)
 * holds the step's picture and title, and its content stands below on the same tonal cards the app uses, scrolling when it is
 * taller than the room. The picture is [hero] where a step has something better than a glyph (the avatar, the app icon), else [icon].
 */
@Composable
private fun Step(
    icon: ImageVector?,
    title: String,
    hero: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val onHero = MaterialTheme.colorScheme.onPrimary
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = ScreenPadding, end = ScreenPadding, top = 56.dp, bottom = BarRoom + 24.dp),
    ) {
        Hero {
            if (hero != null) {
                hero()
            } else if (icon != null) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(onHero.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = onHero, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, color = onHero)
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/** NormPix's own icon, the launcher icon the user has chosen, as the Welcome step's picture. */
@Composable
private fun AppLogo() {
    val context = LocalContext.current
    val icon = remember(context) { AppIcons.current(context) }
    AppIconArt(icon, Modifier.size(80.dp))
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(bottom = 16.dp),
    )
}

/** A card with a small heading, as the settings the tour offers are grouped. */
@Composable
private fun Card(heading: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(heading, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

// ----------------------------------------------------------------------------------------------------- 1: the look

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookStep(
    preferences: AppPreferences,
    mode: com.example.normpix.settings.ThemeMode,
    source: ColorSource,
    accent: AccentColor,
) {
    val haptics = rememberAppHaptics()
    Step(null, "Welcome to NormPix", hero = { AppLogo() }) {
        Body("Track what you spend, plan what comes next and keep your cards safe. There is no account and no server: everything stays on this phone.")
        Card("Theme") {
            ConnectedChoiceRow(
                options = themeOptions,
                selected = mode,
                onSelect = {
                    haptics.selection()
                    preferences.setMode(it)
                },
            )
        }
        Card("Colors") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Swatch(
                        label = "Wallpaper colors",
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        selected = source == ColorSource.System,
                        icon = Icons.Outlined.Palette,
                        onClick = {
                            haptics.selection()
                            preferences.setColorSource(ColorSource.System)
                        },
                    )
                }
                for (choice in AccentColor.entries) {
                    Swatch(
                        label = "${choice.label} colors",
                        color = choice.seed,
                        selected = source == ColorSource.Custom && accent == choice,
                        onClick = {
                            haptics.selection()
                            preferences.setAccent(choice)
                        },
                    )
                }
            }
        }
    }
}

/** One colour to choose: a 48 dp circle, ticked and ringed when it is the one in use. */
@Composable
private fun Swatch(label: String, color: Color, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val scheme = MaterialTheme.colorScheme
    val ink = if (color.luminance() > 0.5f) Color.Black else Color.White
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(3.dp, scheme.primary, CircleShape) else Modifier.border(1.dp, scheme.outlineVariant, CircleShape))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Rounded.Check, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
            icon != null -> Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
    }
}

// -------------------------------------------------------------------------------------------------- 2: who you are

@Composable
private fun ProfileStep(preferences: AppPreferences, savedName: String, emoji: String, photo: String) {
    val context = LocalContext.current
    val photos = remember(context) { ProfilePhotos(context) }
    val appScope = LocalAppScope.current
    val haptics = rememberAppHaptics()
    // The text as typed, so the space typed on the way to a second word survives (the saved name is trimmed).
    var name by rememberSaveable { mutableStateOf(savedName) }
    var problem by remember { mutableStateOf<String?>(null) }

    Step(
        Icons.Outlined.Face,
        "What should we call you?",
        // The picture itself is the step's hero: it changes as a name is typed and an avatar is picked.
        hero = { ProfileAvatar(name = name, emoji = emoji, size = 96.dp, photo = photo) },
    ) {
        Body("Your name and picture show at the top of every screen. Both stay on this phone, and you can change them in Settings > Profile.")
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(MAX_PROFILE_NAME)
                preferences.setProfile(name, emoji)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Your name") },
            placeholder = { Text("What should NormPix call you?") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
        Spacer(Modifier.height(20.dp))
        Text("Or wear an avatar", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (preset in AvatarPreset.entries) {
                AvatarThumb(preset, enabled = true) {
                    appScope.launch {
                        problem = null
                        try {
                            val kept = photos.savePreset(preset)
                            preferences.setProfilePhoto(kept)
                            photos.keepOnly(kept)
                            haptics.success()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            problem = "Couldn't use that avatar. You can pick one later in Settings."
                        }
                    }
                }
            }
        }
        problem?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp).politeLiveRegion())
        }
    }
}

// ------------------------------------------------------------------------------------------------ 3: notifications

@Composable
private fun NotificationsStep() {
    val context = LocalContext.current
    val permissions = LocalPermissions.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(ReminderNotifications.hasPermission(context)) }

    Step(Icons.Outlined.NotificationsActive, "Reminders that arrive") {
        Body("NormPix can remind you about bills, repeating payments and cards about to expire. Reminders are made on your phone; nothing is sent anywhere.")
        Card("Notifications") {
            if (allowed) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.politeLiveRegion()) {
                    Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Notifications are on", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Text(
                    "Android asks first. You can say no here and turn them on later, when you set a reminder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.request(Manifest.permission.POST_NOTIFICATIONS)
                            allowed = ReminderNotifications.hasPermission(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Allow notifications") }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------- 4: start

@Composable
private fun StartStep() {
    Step(Icons.Outlined.TaskAlt, "You're all set") {
        Body("Here is where to begin. None of it has to be done today.")
        Card("Ways in") {
            Tip(Icons.Outlined.AddCircleOutline, "Add your first expense", "The + button at the bottom opens the actions: an expense, an income, a reminder and more.")
            Tip(Icons.Outlined.Share, "Share a receipt", "Share a payment screenshot from your payment app into NormPix and it fills the form in. It is read on this phone.")
            Tip(Icons.Outlined.Lock, "Keep it private", "Lock NormPix and your cards, and back everything up, from Settings.")
        }
    }
}

@Composable
private fun Tip(icon: ImageVector, title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
