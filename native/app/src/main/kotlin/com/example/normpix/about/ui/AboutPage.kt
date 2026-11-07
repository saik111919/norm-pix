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
import com.example.normpix.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.example.normpix.BuildConfig
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import com.example.normpix.ui.components.PagePill
import kotlinx.coroutines.launch

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
    val overlays = LocalOverlays.current
    val scope = LocalAppScope.current
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
                            supporting = "Your data and card details are encrypted on this phone, and you choose what asks for your " +
                                "fingerprint, face or screen lock.",
                            leading = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            supportingMaxLines = 4,
                        )
                    }
                }
                Spacer(Modifier.height(AppSpacing.lg))
                CrashLogSection()
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
                    item {
                        // Its licence asks for its notice to travel with the icons: the row opens it in full (it is ISC, with the
                        // icons Lucide took from Feather under the MIT licence).
                        ListRow(
                            headline = "Lucide icons",
                            supporting = "The Lucide set of interface icons (Settings > Appearance > Icons). By Lucide Contributors and Cole Bemis, under the ISC and MIT licences.",
                            trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                            supportingMaxLines = 5,
                            onClick = {
                                scope.launch {
                                    overlays.sheet<Unit>(expandable = true) { done -> LicenceSheet("Lucide icons", LUCIDE_LICENCE) { done(Unit) } }
                                }
                            },
                        )
                    }
                    item {
                        // Its licence asks for its notice to be shown in the app: the row opens it in full.
                        ListRow(
                            headline = "SQLCipher",
                            supporting = "Encrypts your data on this phone. By Zetetic LLC, under a BSD licence.",
                            trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                            supportingMaxLines = 4,
                            onClick = {
                                scope.launch {
                                    overlays.sheet<Unit>(expandable = true) { done -> LicenceSheet("SQLCipher", SQLCIPHER_LICENCE) { done(Unit) } }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** SQLCipher's licence, as its repository gives it (`assets/licenses/sqlcipher.txt`). */
private const val SQLCIPHER_LICENCE = "licenses/sqlcipher.txt"

/** Lucide's licence file at the release the icons are from (`assets/licenses/lucide.txt`, unchanged). */
private const val LUCIDE_LICENCE = "licenses/lucide.txt"

/** A licence in full, read from the app's [asset] and laid out for the phone's width. */
@Composable
private fun LicenceSheet(title: String, asset: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val text = remember(asset) { runCatching { context.assets.open(asset).bufferedReader().use { reflow(it.readText()) } }.getOrDefault("") }
    EditorSheet(title = title, subtitle = "Licence", saveLabel = "Close", onSave = onClose) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A licence file's text with its hard line breaks (made for 80 columns) taken out, so it wraps to the screen: the lines of each
 * paragraph are joined, and a list item (a line starting with `*`) starts a line of its own.
 */
internal fun reflow(text: String): String =
    text.trim().split(Regex("\\n\\s*\\n")).joinToString("\n\n") { paragraph ->
        paragraph.lines().map(String::trim).filter(String::isNotEmpty).fold(mutableListOf<String>()) { lines, line ->
            if (lines.isEmpty() || line.startsWith("*")) lines += line else lines[lines.lastIndex] = lines.last() + " " + line
            lines
        }.joinToString("\n")
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
