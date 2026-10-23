package com.example.normpix.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.ui.components.BackButton
import com.example.normpix.ui.components.FloatingCircleBackButton
import com.example.normpix.ui.components.ListSectionStyle
import com.example.normpix.ui.components.ProfileAvatar
import com.example.normpix.ui.components.SearchField
import com.example.normpix.ui.components.rememberBackButtonStyle
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing

/**
 * The list of Settings beside its page (a tablet), as a tablet's own Settings has it: the search field across the top with the
 * user's picture at its end - the way to the Profile - and under them the groups as plain rows on the page's own colour, the one
 * that is open filled as a rounded pill. Where a phone's list is a screen with a title and soft cards, this is a column that stays
 * put while the page beside it changes; the way out of Settings is a back button before the search field (or, in the bottom-dock
 * style, the dock the frame draws across the window, see [SettingsTwoPane]).
 */
@Composable
internal fun SettingsListPane(selected: SettingsDetail, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val prefs by LocalAppContainer.current.preferences.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val dock = rememberBackButtonStyle() == BackButtonStyle.BottomDock
    val rows = remember { ListSectionStyle(outer = ROW_RADIUS, inner = ROW_RADIUS, gap = 2.dp, container = Color.Transparent) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        if (dock) AppLayout.navClearance + 24.dp else AppSpacing.xxl

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            when (rememberBackButtonStyle()) {
                BackButtonStyle.TopFloatingCircle -> FloatingCircleBackButton(onBack = navigator::back)
                BackButtonStyle.StandardTopBar -> BackButton(navigator::back)
                // The dock is the way back, drawn once across the window.
                BackButtonStyle.BottomDock -> Unit
            }
            SearchField(value = query, onValueChange = { query = it }, placeholder = "Search settings", modifier = Modifier.weight(1f))
            ProfileButton(
                name = prefs.profileName,
                emoji = prefs.profileEmoji,
                photo = prefs.profilePhoto,
                selected = selected == SettingsDetail.Profile,
                onClick = navigator::openProfile,
            )
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = AppSpacing.md, end = AppSpacing.md, top = AppSpacing.xs, bottom = bottom),
        ) {
            item(key = "body") {
                SettingsListContent(query = query, groupStyle = rows, selected = selected, showProfileRow = false)
            }
        }
    }
}

/** The corner of a row of the list: the open one is a pill of this radius, the rest have it for their ripple. */
private val ROW_RADIUS = 28.dp

/** The user's picture at the end of the search field: the way to the Profile, ringed while that is the page that is open. */
@Composable
private fun ProfileButton(name: String, emoji: String, photo: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(PROFILE_BUTTON)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = "Profile" },
        contentAlignment = Alignment.Center,
    ) {
        ProfileAvatar(name = name, emoji = emoji, size = PROFILE_BUTTON - 10.dp, photo = photo)
    }
}

private val PROFILE_BUTTON = 44.dp
