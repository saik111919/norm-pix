package com.example.normpix.about.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.example.normpix.BuildConfig
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import com.example.normpix.ui.components.PagePill

/**
 * Settings > About NormPix: what the app is, the way to the Feature guide, and the small print.
 *
 * The guide - a card for each feature, each opening its steps - is a page of its own behind a pill under the header
 * ([GuideIndexPage]): until 2026-11-01 its twelve cards filled this page, and the owner asked for them behind a pill with a better
 * name than "How NormPix works".
 */
@Composable
fun AboutPage() {
    val navigator = LocalNavigator.current
    val container = LocalAppContainer.current
    val updater = container.updates
    AppSubPage(title = "About NormPix", onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                AboutHeader()
                PagePill(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    label = GUIDE_TITLE,
                    onClick = navigator::openGuideIndex,
                    modifier = Modifier.padding(top = AppSpacing.lg, start = AppSpacing.xs),
                )
                Spacer(Modifier.height(AppSpacing.xl))
                ListSection(title = "Your data") {
                    item {
                        ListRow(
                            headline = "Everything stays on this device",
                            supporting = "No account and no analytics. NormPix connects only to GitHub, to check for updates and download them.",
                            leading = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null) },
                            supportingMaxLines = 4,
                        )
                    }
                    item {
                        ListRow(
                            headline = "Locked and encrypted",
                            supporting = "Card details are encrypted, and you choose what asks for your fingerprint, face or screen lock.",
                            leading = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            supportingMaxLines = 4,
                        )
                    }
                }
                Spacer(Modifier.height(AppSpacing.lg))
                ListSection(title = "Welcome") {
                    item {
                        ListRow(
                            headline = "Welcome tour",
                            supporting = "Play the introduction a new install begins with: how NormPix looks, your name and picture, reminders.",
                            leading = { Icon(Icons.Outlined.Celebration, contentDescription = null) },
                            trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                            supportingMaxLines = 3,
                            onClick = container.preferences::startOnboarding,
                        )
                    }
                }
                if (updater != null) {
                    Spacer(Modifier.height(AppSpacing.lg))
                    ListSection(title = "Version") {
                        item {
                            ListRow(
                                headline = "Updates & changelog",
                                supporting = "NormPix ${BuildConfig.VERSION_NAME}",
                                leading = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                                trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                                onClick = navigator::openUpdates,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AppSpacing.lg))
                ListSection(title = "Credits") {
                    item {
                        ListRow(
                            headline = "3D category pictures",
                            supporting = "Microsoft Fluent Emoji, under the MIT licence.",
                            supportingMaxLines = 4,
                        )
                    }
                    item {
                        ListRow(
                            headline = "GS Flex font",
                            supporting = "Google Sans Flex, under the SIL Open Font License 1.1. Google Sans Flex is a trademark of Google LLC, " +
                                "and NormPix is not affiliated with Google.",
                            supportingMaxLines = 6,
                        )
                    }
                    item {
                        ListRow(
                            headline = "Nunito font",
                            supporting = "By Vernon Adams, Cyreal and Jacques Le Bailly, under the SIL Open Font License 1.1.",
                            supportingMaxLines = 4,
                        )
                    }
                }
            }
        }
    }
}

/** The app's name and what it is, on the primary container: the first thing on the page. */
@Composable
private fun AboutHeader() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppRadius.rXxl,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(AppSpacing.xxl), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(
                "NormPix",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                "Modern finance, cards & reminders on Android. The Feature guide shows how to get the most out of it.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
