package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.normpix.ui.components.AppPrimaryTabs
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.AppTab
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.ProfileAvatar
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.profileInitial
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing

/**
 * Settings > Profile: what the user likes to be called and the picture that goes with it (an emoji, or their initial), and
 * where the round account button at the top of each tab leads. Everything applies as it is changed, like the rest of Settings,
 * and stays on this device.
 */
@Composable
fun ProfilePage() {
    val navigator = LocalNavigator.current
    val preferences = LocalAppContainer.current.preferences
    val prefs by preferences.state.collectAsState()
    val haptics = rememberAppHaptics()

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
                    ProfileAvatar(name = name, emoji = shownEmoji, size = 96.dp)
                    Spacer(Modifier.height(AppSpacing.md))
                    Text(
                        name.trim().ifEmpty { "Add your name" },
                        style = MaterialTheme.typography.titleLarge,
                        color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                Spacer(Modifier.height(AppSpacing.xl))
                ListSection(
                    title = "Account button",
                    footer = if (prefs.backButtonStyle == BackButtonStyle.TopFloatingCircle) {
                        "The round button at the top of Home, Plans, Insights and Wallet. It wears your picture."
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

/** The name as far as the profile keeps it, never cutting a character that takes two UTF-16 units (an emoji in a name) in half. */
internal fun capped(text: String): String {
    if (text.length <= MAX_PROFILE_NAME) return text
    val cut = text.take(MAX_PROFILE_NAME)
    return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}
