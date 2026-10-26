package com.example.normpix.about.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.normpix.about.GuideLink
import com.example.normpix.about.GuideTopic
import com.example.normpix.finance.ui.paletteColor
import com.example.normpix.navigation.AppDestination
import com.example.normpix.navigation.AppNavigator
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.LeadingBadge
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing

/** The heading over a topic's steps, and over its tips. */
internal const val GUIDE_STEPS_HEADING = "Steps"
internal const val GUIDE_TIPS_HEADING = "Good to know"

/**
 * One topic of the guide: its picture, what the feature is for, the steps to use it in order, a few things worth knowing and,
 * where the feature has a screen, a button that goes there.
 */
@Composable
fun GuidePage(topic: GuideTopic) {
    val navigator = LocalNavigator.current
    val tone = paletteColor(topic.hue)
    AppSubPage(title = topic.title, onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                GuideArt(topic, shape = AppRadius.rXxl)
                Text(
                    topic.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg),
                )
                ListSection(title = GUIDE_STEPS_HEADING) {
                    topic.steps.forEachIndexed { index, step ->
                        item {
                            ListRow(
                                headline = step,
                                leading = {
                                    LeadingBadge(background = tone.container, size = 32.dp) {
                                        Text(
                                            "${index + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = tone.onContainer,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                if (topic.tips.isNotEmpty()) {
                    Spacer(Modifier.height(AppSpacing.lg))
                    ListSection(title = GUIDE_TIPS_HEADING) {
                        for (tip in topic.tips) {
                            item {
                                ListRow(
                                    headline = tip,
                                    leading = { LeadingIcon(Icons.Outlined.AutoAwesome, size = 32.dp) },
                                )
                            }
                        }
                    }
                }
                topic.link?.let { link ->
                    Spacer(Modifier.height(AppSpacing.xl))
                    Button(onClick = { link.open(navigator) }, modifier = Modifier.fillMaxWidth()) { Text(link.label) }
                }
            }
        }
    }
}

/** Goes to the screen a topic is about. */
internal fun GuideLink.open(navigator: AppNavigator) {
    when (this) {
        GuideLink.Home -> navigator.go(AppDestination.Home)
        GuideLink.Activity -> navigator.openTransactions()
        GuideLink.Plans -> navigator.openPlans()
        GuideLink.Insights -> navigator.go(AppDestination.Insights)
        GuideLink.Wallet -> navigator.go(AppDestination.Wallet)
        GuideLink.Colors -> navigator.openAppearance(AppearanceSection.Colors)
        GuideLink.DataBackup -> navigator.openSettings(SettingsPage.Data)
        GuideLink.Security -> navigator.openSettings(SettingsPage.Security)
        GuideLink.Availability -> navigator.openSettings(SettingsPage.Availability)
        GuideLink.Widgets -> navigator.openSettings(SettingsPage.Widgets)
    }
}
