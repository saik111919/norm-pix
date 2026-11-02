package com.example.normpix.widgets

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import kotlin.math.roundToInt

/**
 * Widget settings for one clock: what Android opens when a clock is added, and when the user presses and holds it and chooses
 * Widget settings. A preview of the clock at its size, as the launcher will draw it, with the choices under it: **Transparent**
 * (the face's background is left out), **Clock face or Digital clock** (the plain digital clock for good), the digital clock
 * **when the widget is small**, and how the digital clock reads (12 or 24 hours, seconds, the date).
 *
 * Every choice is applied as it is made ([onChange]); [onDone] only closes the screen.
 */
@Composable
fun ClockWidgetSettings(
    widget: HomeWidget,
    options: ClockOptions,
    sizeDp: Pair<Float, Float>,
    onChange: (ClockOptions) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (width, height) = sizeDp
    val digitalNow = options.showsDigital(width, height)
    // The tint while the slider is being dragged: the preview follows it at once, and the widget (a picture drawn again and sent to
    // the launcher) is told only when the finger lifts.
    var sliding by remember { mutableStateOf<Int?>(null) }
    val shown = sliding?.let { options.copy(backdropPercent = it) } ?: options
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.page)
                .padding(vertical = AppSpacing.lg),
        ) {
            Text(
                "Clock settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(widget.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(AppSpacing.lg))
            ClockPreviewStage(widget, shown, width, height)
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                if (digitalNow) {
                    if (options.style == ClockStyle.Digital) "Showing the digital clock." else "Showing the digital clock: this widget is too small for its face."
                } else {
                    "Showing the clock face."
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = AppSpacing.sm),
            )
            Spacer(Modifier.height(AppSpacing.lg))

            ListSection(title = "Background") {
                item {
                    SwitchRow(
                        headline = "Transparent",
                        supporting = "The wallpaper shows through. The hands, marks and digits stay as they are. You can keep a tint behind them.",
                        checked = options.transparent,
                        onCheckedChange = { onChange(options.copy(transparent = it)) },
                    )
                }
            }
            if (options.transparent) {
                Spacer(Modifier.height(AppSpacing.lg))
                ListSection(
                    title = "Behind the clock",
                    footer = "A home-screen widget can't blur the wallpaper behind it: Android gives widgets no way to, and doesn't " +
                        "tell them where on the screen they are. So this is a tint of the clock's own colour, which its marks and digits " +
                        "are made to read on. Slide it up if they get lost on a bright or busy wallpaper.",
                ) {
                    item {
                        BackdropSlider(
                            percent = shown.backdropPercent,
                            onSlide = { sliding = it },
                            onDone = {
                                sliding?.let { onChange(options.copy(backdropPercent = it)) }
                                sliding = null
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(AppSpacing.lg))

            ListSection(
                title = "Clock",
                modifier = Modifier.selectableGroup(),
                footer = "The digital clock is just the time: no face, nothing added. Choose it for good, or let it stand in when " +
                    "the widget is resized down to one cell.",
            ) {
                item {
                    RadioRow(
                        headline = ClockStyle.Face.label,
                        supporting = widget.pill,
                        selected = options.style == ClockStyle.Face,
                        onClick = { onChange(options.copy(style = ClockStyle.Face)) },
                    )
                }
                item {
                    RadioRow(
                        headline = ClockStyle.Digital.label,
                        supporting = "Only the time, as large as the widget allows",
                        selected = options.style == ClockStyle.Digital,
                        onClick = { onChange(options.copy(style = ClockStyle.Digital)) },
                    )
                }
                item {
                    SwitchRow(
                        headline = "Digital clock when small",
                        supporting = "A one-cell widget or a narrow strip is too small to read a face",
                        checked = options.digitalWhenSmall || options.style == ClockStyle.Digital,
                        enabled = options.style == ClockStyle.Face,
                        onCheckedChange = { onChange(options.copy(digitalWhenSmall = it)) },
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.lg))

            ListSection(title = "Digital clock", modifier = Modifier.selectableGroup()) {
                for (format in ClockTimeFormat.entries) {
                    item {
                        RadioRow(
                            headline = format.label,
                            selected = options.format == format,
                            onClick = { onChange(options.copy(format = format)) },
                        )
                    }
                }
                item {
                    SwitchRow(
                        headline = "Show seconds",
                        checked = options.showSeconds,
                        onCheckedChange = { onChange(options.copy(showSeconds = it)) },
                    )
                }
                item {
                    SwitchRow(
                        headline = "Show the date",
                        checked = options.showDate,
                        onCheckedChange = { onChange(options.copy(showDate = it)) },
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.xl))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Done", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

/** Steps of the tint slider, in percent: 0, 5, 10 ... 100. */
private const val BACKDROP_STEP = 5

/**
 * How much tint stays behind a transparent clock, as a slider from none to the whole face, in steps of [BACKDROP_STEP]: its strength
 * as a number, and under it what that strength is for (the words the four choices it replaced had). [onSlide] follows the finger;
 * [onDone] is when it lifts.
 */
@Composable
private fun BackdropSlider(percent: Int, onSlide: (Int) -> Unit, onDone: () -> Unit) {
    val strength = ClockBackdrop.nearest(percent)
    val valueText = if (percent == 0) "None" else "$percent%"
    Column(Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Tint", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onSlide((it / BACKDROP_STEP).roundToInt() * BACKDROP_STEP) },
            onValueChangeFinished = onDone,
            valueRange = 0f..100f,
            steps = 100 / BACKDROP_STEP - 1,
            modifier = Modifier.semantics {
                contentDescription = "Tint behind the clock"
                stateDescription = "$valueText, ${strength.description}"
            },
        )
        Text(strength.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Where the preview stands: a tonal panel, as the wallpaper a widget sits on, so a transparent clock is seen to be. */
@Composable
private fun ClockPreviewStage(widget: HomeWidget, options: ClockOptions, widthDp: Float, heightDp: Float) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .clip(RoundedCornerShape(AppRadius.xxl))
            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer)))
            .padding(vertical = AppSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        // Never larger than the stage, whatever size the widget has been stretched to.
        val shown = minOf(1f, 240f / maxOf(widthDp, heightDp))
        val w = widthDp * shown
        val h = heightDp * shown
        val context = LocalContext.current
        key(widget, options, w, h) {
            AndroidView(
                modifier = Modifier
                    .size(w.dp, h.dp)
                    .clearAndSetSemantics { contentDescription = "Preview of the ${widget.title} widget" },
                factory = { FrameLayout(it) },
                update = { frame ->
                    frame.removeAllViews()
                    val views = ClockWidgets.preview(context, widget, w, h, options).apply(context, frame)
                    views.makeInert()
                    frame.addView(views)
                },
            )
        }
    }
}
