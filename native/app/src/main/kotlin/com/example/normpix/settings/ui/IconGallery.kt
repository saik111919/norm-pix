package com.example.normpix.settings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.BudgetHealth
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.CategoryIconPicker
import com.example.normpix.finance.ui.budgetIcon
import com.example.normpix.finance.ui.budgetLabel
import com.example.normpix.finance.ui.categoryIcons
import com.example.normpix.finance.ui.icon as financeIcon
import com.example.normpix.finance.ui.label
import com.example.normpix.reminders.ReminderCategory
import com.example.normpix.reminders.ReminderUrgency
import com.example.normpix.reminders.icon as reminderIcon
import com.example.normpix.settings.AppIcon
import com.example.normpix.ui.components.AppPrimaryTabs
import com.example.normpix.ui.components.AppTab
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.theme.AppSpacing

/**
 * Every icon NormPix draws, in one place (Settings > Appearance > Icons > Icon gallery): the interface's own icons grouped by what
 * they stand for, the category icons, brands and emoji a category can wear, and the launcher icons NormPix can have. It is a way to
 * see the whole set at once - to find the icon for something, and to notice two things pictured differently. The interface groups
 * are read from the enums the screens themselves draw from, so a gallery entry cannot differ from what a screen shows.
 */
@Composable
fun IconGallerySheet() {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.md)) {
            SheetHeader(title = "Icon gallery", subtitle = "All the icons NormPix uses")
            Spacer(Modifier.height(AppSpacing.md))
            AppPrimaryTabs(
                tabs = remember { listOf(AppTab("Interface"), AppTab("Categories"), AppTab("App icons")) },
                selectedIndex = tab,
                onSelect = { tab = it },
            )
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.xxl),
        ) {
            when (tab) {
                0 -> InterfaceIcons()
                1 -> CategoryIcons()
                else -> LauncherIcons()
            }
        }
    }
}

/** A named set of icons: what [title] shows, drawn the same way as on its screen. */
private class IconGroup(val title: String, val icons: List<Pair<String, ImageVector>>)

private val interfaceGroups: List<IconGroup> by lazy {
    listOf(
        IconGroup("Transactions", TransactionType.entries.map { it.label to it.financeIcon }),
        IconGroup("Repeats", RecurrenceFrequency.entries.map { it.label to it.financeIcon }),
        IconGroup("Reminders", ReminderCategory.entries.map { it.label to it.reminderIcon }),
        IconGroup("Reminder status", ReminderUrgency.entries.map { it.label to it.icon }),
        IconGroup("Budget status", BudgetHealth.entries.map { budgetLabel(it) to budgetIcon(it) }),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterfaceIcons() {
    for (group in interfaceGroups) {
        Text(
            group.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = AppSpacing.lg, bottom = AppSpacing.sm).semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            for ((label, icon) in group.icons) GalleryTile(label) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun CategoryIcons() {
    var picked by remember { mutableStateOf("") }
    Text(
        text = categoryIcons[picked]?.label ?: "Tap an icon to see its name",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = AppSpacing.md, bottom = AppSpacing.md),
    )
    CategoryIconPicker(icon = picked, onSelect = { picked = it })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LauncherIcons() {
    Text(
        "The icons NormPix can have on your home screen. Change it in Settings > Appearance > App icon.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = AppSpacing.md),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        for (icon in AppIcon.entries) {
            Column(
                Modifier.width(72.dp).clearAndSetSemantics { contentDescription = "${icon.label} app icon" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))) {
                    Image(painterResource(icon.background), contentDescription = null, modifier = Modifier.size(64.dp))
                    Image(painterResource(icon.foreground), contentDescription = null, modifier = Modifier.size(64.dp))
                }
                Text(
                    icon.label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = AppSpacing.xs),
                )
            }
        }
    }
}

/** One icon on its tonal tile with its name under it; heard as that name. */
@Composable
private fun GalleryTile(label: String, art: @Composable () -> Unit) {
    Column(
        Modifier.width(76.dp).clearAndSetSemantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) { art() }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AppSpacing.xs),
        )
    }
}
