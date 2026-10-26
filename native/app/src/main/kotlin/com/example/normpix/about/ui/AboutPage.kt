package com.example.normpix.about.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
import com.example.normpix.BuildConfig
import com.example.normpix.about.GuideTopic
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.SectionSubheader
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing

/** How many topic cards sit side by side on the About page. */
internal const val GUIDE_CARDS_PER_ROW = 2

/** What the About page's guide is headed. */
internal const val GUIDE_HEADING = "How NormPix works"

/**
 * Settings > About NormPix: what the app is, a guide to how each of its features works, and the small print.
 *
 * The guide is a grid of cards, each a picture of a feature with its name ([GuideCard]); a card opens that topic's page
 * ([GuidePage]) with the steps. The pictures are drawn in the app's own colors ([GuideArt]), so the page follows the theme, and
 * the guide follows the app as it changes rather than going out of date as screenshots would.
 */
@Composable
fun AboutPage() {
    val navigator = LocalNavigator.current
    val updater = LocalAppContainer.current.updates
    AppSubPage(title = "About NormPix", onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                AboutHeader()
                SectionSubheader(GUIDE_HEADING)
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    for (row in GuideTopic.entries.chunked(GUIDE_CARDS_PER_ROW)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                            for (topic in row) {
                                GuideCard(topic, onClick = { navigator.openGuide(topic) }, modifier = Modifier.weight(1f))
                            }
                            // A last, lone card is as wide as the others rather than stretched across the row.
                            repeat(GUIDE_CARDS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(AppSpacing.lg))
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
                "Modern finance, cards & reminders on Android. Everything below is how to get the most out of it.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** A topic on the About page: its picture above its name. The whole card opens the topic. */
@Composable
private fun GuideCard(topic: GuideTopic, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = AppRadius.rXl,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            GuideArt(topic, shape = androidx.compose.ui.graphics.RectangleShape)
            Text(
                topic.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
            )
        }
    }
}
