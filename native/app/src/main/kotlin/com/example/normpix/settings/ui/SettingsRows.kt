package com.example.normpix.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.paletteColor
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.ui.components.ProfileAvatar

/**
 * The colour each settings group wears on the main Settings screen: an index into the category palette, so every group is
 * recognisable at a glance and the hues follow the theme like everything else that is coloured by it.
 */
val SettingsPage.hue: Int
    get() = when (this) {
        SettingsPage.Appearance -> 3
        SettingsPage.Finance -> 1
        SettingsPage.Data -> 2
        SettingsPage.Wallet -> 7
        SettingsPage.Availability -> 4
        SettingsPage.Security -> 0
    }

/** A settings icon in a solid disc of its group's colour (neutral when there is none), the glyph in whichever of light and dark reads on it. */
@Composable
fun SettingsGlyph(icon: ImageVector, hue: Int?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tone = hue?.let { paletteColor(it).accent }
    val ink = when {
        tone == null -> scheme.onSurfaceVariant
        tone.luminance() > 0.5f -> Color(0xFF1B1B1F)
        else -> Color.White
    }
    Box(modifier.size(44.dp).background(tone ?: scheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
    }
}

/**
 * A row of the main Settings screen: the group's coloured glyph, its name in the heading weight and, under it, what it holds
 * right now. Sits in a [com.example.normpix.ui.components.ListSection], which gives the rows their grouped, rounded look.
 */
@Composable
fun SettingsRow(
    headline: String,
    icon: ImageVector,
    hue: Int?,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { SettingsGlyph(icon, hue) },
        headlineContent = { Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
        supportingContent = supporting?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

/**
 * The top row of Settings: the user's own picture and name - "Set up your profile" until they have given one - leading to the
 * Profile page. Says where the account button goes while that is not Settings, since this is where it would be looked for.
 */
@Composable
fun ProfileSettingsRow(name: String, emoji: String, accountButtonLeadsToProfile: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { ProfileAvatar(name = name, emoji = emoji, size = 44.dp) },
        headlineContent = {
            Text(name.ifBlank { "Set up your profile" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                if (accountButtonLeadsToProfile) "Your name and picture · Account button opens Profile" else "Your name and picture",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** [SettingsRow] with an on/off switch: the whole row toggles (one touch target, announced as a switch). */
@Composable
fun SettingsSwitchRow(
    headline: String,
    icon: ImageVector,
    hue: Int?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    ListItem(
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { SettingsGlyph(icon, hue) },
        headlineContent = { Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
        supportingContent = supporting?.let { { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}
