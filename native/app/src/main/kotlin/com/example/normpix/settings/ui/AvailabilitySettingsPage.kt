package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.normpix.availability.ScheduledFeature
import com.example.normpix.availability.rememberAvailability
import com.example.normpix.availability.ui.AvailabilityStatusRow
import com.example.normpix.availability.ui.ScheduleEditor
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.theme.AppSpacing

/**
 * Settings > Availability & Schedules: when each [ScheduledFeature] may be used. One section per feature -
 * its live state on top, then the editable rule - so a new feature only has to be added to the enum.
 * Edits take effect as you make them.
 */
@Composable
fun AvailabilitySettingsPage() {
    val navigator = LocalNavigator.current
    val preferences = LocalAppContainer.current.preferences

    AppSubPage(title = "Availability & Schedules", onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                Text(
                    "Choose when these features can run. Outside their hours they stay off, and nothing starts by itself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                )
                for (feature in ScheduledFeature.entries) {
                    val ui = rememberAvailability(feature)
                    ListSection(title = "Right now") {
                        item { AvailabilityStatusRow(ui) }
                    }
                    ScheduleEditor(
                        rule = ui.rule,
                        featureName = feature.label,
                        is24Hour = ui.is24Hour,
                        onChange = { preferences.setAvailability(feature, it) },
                    )
                }
            }
        }
    }
}
