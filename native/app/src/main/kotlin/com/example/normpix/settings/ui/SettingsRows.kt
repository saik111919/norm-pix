package com.example.normpix.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.paletteColor
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.ui.components.ListSectionStyle
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
        SettingsPage.Notifications -> SettingsPageNotificationsHue
        SettingsPage.Updates -> SettingsPageUpdatesHue
        SettingsPage.Security -> 0
    }

/**
 * How the groups of the main Settings screen are drawn: rounder ends (24dp), tighter seams between the rows (6dp corners 4dp
 * apart) and a faint tone over the page rather than a solid one, so the page reads as a few soft cards.
 */
@Composable
fun settingsGroupStyle(): ListSectionStyle {
    val fill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = GROUP_FILL_ALPHA)
    return remember(fill) { ListSectionStyle(outer = 24.dp, inner = 6.dp, gap = 4.dp, container = fill) }
}

private const val GROUP_FILL_ALPHA = 0.3f

/**
 * A settings icon in a soft rounded tile of its group's colour (neutral when there is none), the glyph in that colour's deep
 * tone - the tile is tinted, not filled, so a column of them stays quiet.
 */
@Composable
fun SettingsGlyph(icon: ImageVector, hue: Int?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tone = hue?.let { paletteColor(it) }
    Box(
        modifier.size(44.dp).background(tone?.container ?: scheme.surfaceContainerHighest, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tone?.onContainer ?: scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
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
    /** Something needs doing here (an update is waiting): what it says under the name is in the error colour, to be seen from the list. */
    alert: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** Something at the row's end (a chevron, for a row that opens a page). */
    trailing: (@Composable () -> Unit)? = null,
    /**
     * Null for a row of the list on its own. With the list beside the page it opens (Settings on a tablet), whether that page is the
     * open one: the row is filled if it is, and every row is announced as one of the list's tabs, the open one as selected.
     */
    selected: Boolean? = null,
) {
    ListItem(
        modifier = modifier.then(
            when {
                onClick == null -> Modifier
                selected != null -> Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick)
                else -> Modifier.clickable(onClick = onClick)
            },
        ),
        colors = ListItemDefaults.colors(containerColor = selectedRowColor(selected == true)),
        leadingContent = { SettingsGlyph(icon, hue) },
        headlineContent = { Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
        supportingContent = supporting?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        alert -> MaterialTheme.colorScheme.error
                        selected == true -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = trailing,
    )
}

/** What a row of the Settings list is filled with: nothing, or - for the page that is open beside the list - the secondary container. */
@Composable
private fun selectedRowColor(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

/**
 * The top row of Settings: the user's own picture and name - "Set up your profile" until they have given one - leading to the
 * Profile page. Says where the account button goes while that is not Settings, since this is where it would be looked for.
 */
@Composable
fun ProfileSettingsRow(
    name: String,
    emoji: String,
    accountButtonLeadsToProfile: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    photo: String = "",
    /** As for [SettingsRow]: null with the list on its own, else whether the Profile is the page open beside it. */
    selected: Boolean? = null,
) {
    ListItem(
        modifier = modifier.then(
            if (selected != null) Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick) else Modifier.clickable(onClick = onClick),
        ),
        colors = ListItemDefaults.colors(containerColor = selectedRowColor(selected == true)),
        leadingContent = { ProfileAvatar(name = name, emoji = emoji, size = 44.dp, photo = photo) },
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

/**
 * A [SettingsRow] split in two, as Pixel's Settings does "Adaptive brightness": the row itself opens its page ([onClick]), and
 * a switch at its end, after a thin divider, turns the thing on or off without opening anything. Two touch targets, announced
 * as a button and as a switch ([switchLabel] is what the switch is called, as the row's own words say what the page holds).
 */
@Composable
fun SettingsSplitSwitchRow(
    headline: String,
    icon: ImageVector,
    hue: Int?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    switchLabel: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingsRow(
            headline = headline,
            icon = icon,
            hue = hue,
            modifier = Modifier.weight(1f),
            supporting = supporting,
            onClick = onClick,
        )
        Box(
            Modifier
                .width(1.dp)
                .height(SPLIT_DIVIDER_HEIGHT)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Box(
            Modifier
                .heightIn(min = SPLIT_SWITCH_HEIGHT)
                .widthIn(min = SPLIT_SWITCH_WIDTH)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics { contentDescription = switchLabel },
            contentAlignment = Alignment.Center,
        ) {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

private val SPLIT_DIVIDER_HEIGHT = 36.dp
private val SPLIT_SWITCH_HEIGHT = 72.dp
private val SPLIT_SWITCH_WIDTH = 88.dp

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
