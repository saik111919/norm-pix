package com.example.normpix.settings.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.FrameLayout
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SectionSubheader
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.widgets.ClockWidget
import com.example.normpix.widgets.HomeWidget
import com.example.normpix.widgets.HomeWidgetGroup
import com.example.normpix.widgets.WatchDialTicker
import com.example.normpix.widgets.WidgetPinning
import com.example.normpix.widgets.makeInert

/**
 * Settings > Widgets: every widget NormPix puts on the home screen, each a pill. A pill shows the widget in the stage above
 * - drawn by the same layout the home screen uses, so what is previewed is what is placed - and the button under it asks the
 * launcher to add it, so a widget is one tap from here instead of a hunt through the launcher's widget picker.
 *
 * The launcher decides where the widget goes: it shows its own "Add to home screen" and the user chooses the spot. One that
 * can't be asked (a few launchers) gets the way to the picker in words instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetsSettingsPage() {
    val navigator = LocalNavigator.current
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val snackbars = LocalSnackbars.current
    var chosen by rememberSaveable { mutableStateOf(HomeWidget.CookieAnalog.name) }
    val widget = HomeWidget.entries.firstOrNull { it.name == chosen } ?: HomeWidget.CookieAnalog
    val canPin = remember { WidgetPinning.supported(context) }
    // How many of the chosen widget are on the home screen: read again as the page comes back from the launcher's dialog, which
    // is how an addition shows up here.
    var placed by remember(widget) { mutableIntStateOf(widget.placed(context)) }
    // Whether the watch dial's rings can step on the minute (Android 12 and later ask the user for exact alarms); read again on return.
    var exactAlarms by remember { mutableStateOf(WatchDialTicker.onTheMinute(context)) }
    val current by rememberUpdatedState(widget)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = current.placed(context)
        if (now > placed) snackbars.show("${current.title} added to your home screen")
        placed = now
        val onTheMinute = WatchDialTicker.onTheMinute(context)
        // Just allowed: the alarm that is set is the inexact one, so set the next as an exact one now.
        if (onTheMinute && !exactAlarms) ClockWidget.refreshAll(context)
        exactAlarms = onTheMinute
    }

    AppSubPage(title = "Widgets", onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                WidgetStage(widget)
                for (group in HomeWidgetGroup.entries) {
                    SectionSubheader(group.title)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg).selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    ) {
                        for (entry in HomeWidget.inGroup(group)) {
                            WidgetPill(entry, selected = entry == widget) {
                                if (entry != widget) haptics.selection()
                                chosen = entry.name
                            }
                        }
                    }
                }
                Spacer(Modifier.height(AppSpacing.xl))
                Column(Modifier.padding(horizontal = AppSpacing.lg)) {
                    Text(widget.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(widget.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (placed > 0) {
                        Spacer(Modifier.height(AppSpacing.sm))
                        Text(
                            if (placed == 1) "On your home screen now" else "$placed on your home screen now",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (widget == HomeWidget.WatchDial && !exactAlarms) {
                    Spacer(Modifier.height(AppSpacing.md))
                    ListSection {
                        item {
                            ListRow(
                                headline = "Let the rings step on the minute",
                                supporting = "Allow alarms & reminders for NormPix. Until then the rings can run a little behind the minute.",
                                leading = { LeadingIcon(Icons.Outlined.Schedule) },
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        // Android's own page for it comes over NormPix: coming back from it is not leaving.
                                        container.lock.expectExternalActivity()
                                        val settings = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                                        runCatching { context.startActivity(settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AppSpacing.lg))
                if (canPin) {
                    Button(
                        onClick = {
                            haptics.selection()
                            // The launcher's own dialog comes over NormPix: coming back from it is not leaving.
                            container.lock.expectExternalActivity()
                            if (!WidgetPinning.request(context, widget)) {
                                snackbars.show("Your launcher didn't take the request. Press and hold the home screen, then choose Widgets.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                        Spacer(Modifier.size(AppSpacing.sm))
                        Text("Add to home screen")
                    }
                } else {
                    ListSection {
                        item {
                            ListRow(
                                headline = "Your launcher can't add it from here",
                                supporting = "Press and hold the home screen, choose Widgets and find NormPix.",
                                leading = { LeadingIcon(Icons.Outlined.Info) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AppSpacing.md))
                Text(
                    "The clocks keep time on their own. On Android 12 and later the widgets wear your wallpaper's colors, and " +
                        "none shows anything of your ledger while NormPix is locked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppSpacing.lg),
                )
            }
        }
    }
}

/** One widget's pill: round, with a check on the one whose preview is up. */
@Composable
private fun WidgetPill(widget: HomeWidget, selected: Boolean, onSelect: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onSelect,
        label = { Text(widget.pill) },
        shape = CircleShape,
        // One choice of several, so it is announced as a radio button rather than the checkbox a filter chip is.
        modifier = Modifier.semantics { role = Role.RadioButton },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

/** Where the preview stands: a tonal panel, as the wallpaper a widget sits on, the widget in the middle of it. */
@Composable
private fun WidgetStage(widget: HomeWidget, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = STAGE_HEIGHT)
            .clip(RoundedCornerShape(AppRadius.xxl))
            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer)))
            .padding(vertical = AppSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = widget, animationSpec = tween(AppMotion.duration(AppMotion.SHORT_MS)), label = "widget_preview") {
            WidgetPreview(it)
        }
    }
}

private val STAGE_HEIGHT = 224.dp

/**
 * [widget] as the launcher would draw it: the very RemoteViews the widget is placed with, laid out here for the size it is
 * previewed at, and made a picture (its taps open the clock app or a sheet, which a settings page must not). The clocks keep
 * time in it the way they do on the home screen.
 */
@Composable
internal fun WidgetPreview(widget: HomeWidget, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    key(widget) {
        AndroidView(
            modifier = modifier
                .size(widget.previewWidthDp.dp, widget.previewHeightDp.dp)
                .clearAndSetSemantics { contentDescription = "Preview of the ${widget.title} widget" },
            factory = { FrameLayout(it) },
            update = { frame ->
                frame.removeAllViews()
                val shown = widget.previewViews(context).apply(context, frame)
                shown.makeInert()
                frame.addView(shown)
            },
        )
    }
}
