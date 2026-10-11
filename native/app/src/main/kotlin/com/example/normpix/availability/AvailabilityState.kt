package com.example.normpix.availability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import android.text.format.DateFormat
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.normpix.navigation.LocalAppContainer
import java.time.Clock
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/** The wall clock the availability UI reads; tests substitute a fixed one. */
val LocalAvailabilityClock = staticCompositionLocalOf<Clock> { Clock.systemDefaultZone() }

/** A feature's rule evaluated at [now], with the sentence a screen shows for it. */
@Immutable
data class AvailabilityUi(
    val feature: ScheduledFeature,
    val rule: AvailabilityRule,
    val availability: Availability,
    val now: LocalDateTime,
    val is24Hour: Boolean = false,
) {
    val isOpen: Boolean get() = availability.isOpen

    /** "NFC is unavailable right now" is the caller's headline; this is the one-line detail under it. */
    val message: String get() = availability.describe(now, is24Hour)
    val summary: String get() = rule.summary(is24Hour)
}

/**
 * Whether [feature] may be used right now, kept current: it re-evaluates when the rule is edited, when the
 * app comes back to the foreground, and exactly when the window next opens or closes. A screen that gates
 * on this therefore switches itself on or off without being reopened.
 */
@Composable
fun rememberAvailability(feature: ScheduledFeature): AvailabilityUi {
    val container = LocalAppContainer.current
    val prefs by container.preferences.state.collectAsState()
    val rule = prefs.availabilityOf(feature)
    val clock = LocalAvailabilityClock.current
    var now by remember(clock) { mutableStateOf(LocalDateTime.now(clock)) }

    LifecycleResumeEffect(clock) {
        now = LocalDateTime.now(clock)
        onPauseOrDispose {}
    }
    LaunchedEffect(rule, clock) {
        while (true) {
            now = LocalDateTime.now(clock)
            val untilChange = rule.availabilityAt(now).nextChangeAfter(now) ?: return@LaunchedEffect
            // Wake just after the boundary; never sleep longer than a few minutes, so a clock or time-zone change is picked up.
            delay((untilChange.toMillis() + 250).coerceIn(250, MAX_SLEEP_MS))
        }
    }
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(feature, rule, now, is24Hour) { AvailabilityUi(feature, rule, rule.availabilityAt(now), now, is24Hour) }
}

private const val MAX_SLEEP_MS = 5 * 60 * 1000L

/** Provides a fixed clock to everything below (tests, previews). */
@Composable
fun WithAvailabilityClock(clock: Clock, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAvailabilityClock provides clock, content = content)
}
