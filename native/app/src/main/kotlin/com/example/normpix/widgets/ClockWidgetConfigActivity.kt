package com.example.normpix.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.normpix.NormPixApplication
import com.example.normpix.navigation.ProvideAppEnvironment
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.NormPixTheme

/**
 * Android's "Widget settings" for a clock: opened by the launcher when a clock is added and when the user presses and holds it
 * and chooses Widget settings. It shows [ClockWidgetSettings] for that one widget, applies each choice as it is made
 * ([ClockOptionsStore], then the widget is drawn again) and keeps the widget however it is left, so leaving by Back is not
 * taking the widget away.
 *
 * Anything may start it (it must be exported for the launcher), so it does nothing for an id that is not one of NormPix's clocks,
 * and it shows nothing of the user's data.
 */
class ClockWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val widget = if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) null else ClockWidgets.widgetOf(this, appWidgetId)
        if (widget == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        // The widget is kept however this is left: every choice is already applied.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        enableEdgeToEdge()
        val container = (application as NormPixApplication).container
        val store = ClockOptionsStore(this)
        val size = ClockWidgets.sizeOf(this, appWidgetId)
        setContent {
            val prefs by container.preferences.state.collectAsState()
            var options by remember { mutableStateOf(store.get(appWidgetId)) }
            NormPixTheme(Appearance(prefs.mode, prefs.colorSource, prefs.accent, wallpaperPalette = prefs.wallpaperPalette)) {
                ProvideAppEnvironment(container) {
                    ClockWidgetSettings(
                        widget = widget,
                        options = options,
                        sizeDp = size,
                        onChange = {
                            options = it
                            store.set(appWidgetId, it)
                            ClockWidgets.update(this@ClockWidgetConfigActivity, appWidgetId)
                        },
                        onDone = ::finish,
                    )
                }
            }
        }
    }
}
