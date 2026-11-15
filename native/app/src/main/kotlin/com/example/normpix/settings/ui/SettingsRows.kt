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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.normpix.ui.components.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.Fluent3d
import com.example.normpix.finance.ui.paletteColor
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.ui.components.AppLoadingIndicator
import com.example.normpix.ui.components.AssetImage
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListSectionStyle
import com.example.normpix.ui.components.ProfileAvatar

/**
 * The colour each settings group wears on the main Settings screen: an index into the category palette, so every group is
 * recognisable at a glance and the hues follow the theme like everything else that is coloured by it.
 */
val SettingsPage.hue: Int
    get() = when (this) {
        SettingsPage.Appearance -> 3
        SettingsPage.Widgets -> 6
        SettingsPage.Finance -> 1
        SettingsPage.Banks -> 5
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
 * The emoji each settings group is pictured by in 3D (see [SettingsGlyph]); every one has a bundled 3D picture
 * (`SettingsIcons3dTest` checks).
 */
val SettingsPage.emoji: String
    get() = when (this) {
        SettingsPage.Appearance -> "🎨"
        SettingsPage.Widgets -> "🧩"
        SettingsPage.Finance -> "💰"
        SettingsPage.Banks -> "🏦"
        SettingsPage.Data -> "💾"
        SettingsPage.Wallet -> "💳"
        SettingsPage.Availability -> "🗓️"
        SettingsPage.Notifications -> "🔔"
        SettingsPage.Updates -> "📥"
        SettingsPage.Security -> "🔒"
    }

/**
 * The emoji a search result on the main Settings list is pictured by: its own when it is a special entry (Reset, About ...), its
 * Appearance screen's for a setting there, else its page's - the picture the row it leads to wears. Null for an entry that is none
 * of these, which keeps its flat icon.
 */
internal val SettingsEntry.emoji: String?
    get() = when {
        special != null -> when (special) {
            SettingsSpecial.Profile -> SettingsRowEmoji.PROFILE
            SettingsSpecial.Categories -> SettingsRowEmoji.TAG
            SettingsSpecial.People -> SettingsRowEmoji.HANDSHAKE
            SettingsSpecial.Reset -> SettingsRowEmoji.RESET
            SettingsSpecial.Updates -> SettingsRowEmoji.UPDATES
            SettingsSpecial.About -> SettingsRowEmoji.ABOUT
            SettingsSpecial.WelcomeTour -> SettingsRowEmoji.WELCOME_TOUR
        }
        appearance != null -> appearance.emoji
        else -> page?.emoji
    }

/**
 * The 3D emoji of every other Settings row: the main list's own rows, and the leading icons of the rows on the pages under it
 * ([SettingsLeadingIcon], [SettingsLeadingBadge], the `emoji` of [SettingsRow]). One place to pick a row's picture, so the same thing
 * wears the same picture on every page. Each is a bundled 3D picture: `SettingsIcons3dTest` reads every constant here, so an emoji the
 * app has no picture of is a failing test, not a row that quietly goes flat. A row names its emoji by a constant of this object, not
 * a literal (the test checks that too): add the constant here.
 */
internal object SettingsRowEmoji {
    // The main list's rows
    const val RESET = "🧹"
    const val UPDATES = "📥"
    const val WELCOME_TOUR = "🎉"
    const val ABOUT = "📖"
    const val SETTINGS = "⚙️"
    const val PROFILE = "🙂"

    // Security, lock, privacy
    const val LOCK = "🔒"
    const val LOCK_AND_KEY = "🔐"
    const val KEY = "🔑"
    const val SHIELD = "🛡️"
    const val TAG = "🏷️"
    /** A tag the user names on transactions (Settings > Finance > Tags): the label emoji, apart from the categories' price tag. */
    const val LABEL = "🔖"
    const val PHONE = "📱"
    const val SCREEN = "🖥️"

    // Data, backup, check
    const val BACKUP = "💾"
    const val BOX = "📦"
    const val EXPORT = "📤"
    const val IMPORT = "📥"
    const val SPREADSHEET = "📊"
    const val CLOCK = "🕐"
    const val HOURGLASS = "⏳"
    const val MAGNIFIER = "🔍"
    const val ERASE = "🧹"
    const val SAMPLE = "🧪"
    const val FILE_BOX = "🗃️"
    const val CALENDAR = "🗓️"

    // Money, wallet, banks, people
    const val MONEY = "💰"
    const val STAR = "⭐"
    const val CARD = "💳"
    const val BANK = "🏦"
    const val HANDSHAKE = "🤝"
    const val SWIPE = "🪄"

    // Notifications, alerts, updates, widgets
    const val BELL = "🔔"
    const val ALARM = "⏰"
    const val POWER = "⚡"
    const val STACK = "📑"
    const val SPARKLES = "✨"
    const val MOBILE_DATA = "📶"
    const val LIGHT_BULB = "💡"

    // About, appearance, the rest
    const val GLOBE = "🌐"
    const val BUG = "🐞"
    const val NOTE = "📝"
    const val QUESTION = "❓"
    const val PEN = "🖋️"
    const val PUZZLE = "🧩"
    const val JOYSTICK = "🕹️"
    const val PALETTE = "🎨"
}

/**
 * Whether Settings' own icons are drawn as 3D pictures: the category icon style is 3D and the user has not turned it off for
 * Settings (Appearance > Icons > 3D icons in Settings). Provided by `ProvideAppEnvironment`; flat outside it.
 */
val LocalSettingsIcons3d = staticCompositionLocalOf { false }

/**
 * A settings icon in a soft rounded tile of its group's colour (neutral when there is none), the glyph in that colour's deep
 * tone - the tile is tinted, not filled, so a column of them stays quiet. With 3D icons on ([LocalSettingsIcons3d]) and an [emoji]
 * the app has a 3D picture of, the picture stands on the same tile instead, drawn larger than the glyph as the 3D category
 * pictures are (a 3D picture at a glyph's size looks lost).
 */
@Composable
fun SettingsGlyph(icon: ImageVector, hue: Int?, modifier: Modifier = Modifier, emoji: String? = null) {
    val scheme = MaterialTheme.colorScheme
    val tone = hue?.let { paletteColor(it) }
    val picture = rememberSettingsPicture(emoji)
    Box(
        modifier.size(44.dp).background(tone?.container ?: scheme.surfaceContainerHighest, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null) {
            AssetImage(picture, Modifier.size(30.dp))
        } else {
            Icon(icon, contentDescription = null, tint = tone?.onContainer ?: scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * The bundled 3D picture of [emoji] (its path under `assets/`) while Settings' icons are 3D ([LocalSettingsIcons3d]); null when they
 * are flat, when there is no [emoji], or when the app has no picture of it - the caller then draws its flat icon, so a row never
 * goes blank.
 */
@Composable
internal fun rememberSettingsPicture(emoji: String?): String? {
    val threeD = LocalSettingsIcons3d.current
    val context = LocalContext.current
    return remember(emoji, threeD) { if (threeD && emoji != null) Fluent3d.path(context, emoji) else null }
}

/**
 * The leading icon of a row on a Settings page - the bare icon a `ListRow` or `SwitchRow` draws at its start. Flat, it is exactly
 * that `Icon(icon, contentDescription = null)`; with 3D icons on ([LocalSettingsIcons3d]) it is the 3D picture of [emoji], bare
 * and larger than the glyph it replaces ([BARE_PICTURE_SIZE]: a picture at a glyph's 24 dp looks lost, and a neutral tile behind
 * it vanishes on the light rows), so a one-line row keeps its height. Decorative: no description, as the flat icon has none.
 *
 * A row that is [busy] (a backup being written) shows the app's loading indicator where the icon was, as those rows always have; in
 * 3D it sits in a box of the picture's size, so the row's text does not move when the work starts and ends.
 */
@Composable
fun SettingsLeadingIcon(
    icon: ImageVector,
    emoji: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    busy: Boolean = false,
) {
    val picture = rememberSettingsPicture(emoji)
    when {
        busy && picture == null -> AppLoadingIndicator(modifier.size(24.dp), strokeWidth = 2.5.dp)
        busy -> Box(modifier.size(BARE_PICTURE_SIZE), contentAlignment = Alignment.Center) {
            AppLoadingIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
        }
        picture == null -> Icon(icon, contentDescription = null, modifier = modifier, tint = tint)
        else -> AssetImage(picture, modifier.size(BARE_PICTURE_SIZE))
    }
}

/**
 * [com.example.normpix.ui.components.LeadingIcon]'s tonal badge on a Settings page. Flat, it is exactly that badge; with 3D icons on
 * it is the same disc, in the same colours (a success or an error tone keeps saying so), carrying the 3D picture of [emoji] larger
 * than the glyph it replaces.
 */
@Composable
fun SettingsLeadingBadge(
    icon: ImageVector,
    emoji: String,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.secondaryContainer,
    foreground: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 40.dp,
) {
    val picture = rememberSettingsPicture(emoji)
    if (picture == null) {
        LeadingIcon(icon, modifier, background, foreground, size)
    } else {
        Box(modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
            AssetImage(picture, Modifier.size(size * DISC_PICTURE_FILL))
        }
    }
}

/** What the picture in a Settings leading icon is drawn at, bare: a 3D picture reads as large as a glyph about 24 dp across at this size. */
private val BARE_PICTURE_SIZE = 32.dp

/** How much of a disc its 3D picture fills (the glyph it replaces filled half): as much as the main list's 30 of 44 dp tile. */
private const val DISC_PICTURE_FILL = 0.7f

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
    /** The emoji the row is pictured by when Settings' icons are 3D ([SettingsGlyph]); null keeps [icon] always. */
    emoji: String? = null,
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
        leadingContent = { SettingsGlyph(icon, hue, emoji = emoji) },
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
    emoji: String? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingsRow(
            headline = headline,
            icon = icon,
            hue = hue,
            modifier = Modifier.weight(1f),
            supporting = supporting,
            onClick = onClick,
            emoji = emoji,
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
    /** The emoji the row is pictured by when Settings' icons are 3D ([SettingsGlyph]); null keeps [icon] always. */
    emoji: String? = null,
) {
    ListItem(
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { SettingsGlyph(icon, hue, emoji = emoji) },
        headlineContent = { Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
        supportingContent = supporting?.let { { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}
