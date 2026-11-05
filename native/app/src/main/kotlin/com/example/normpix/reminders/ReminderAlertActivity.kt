package com.example.normpix.reminders

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.normpix.NormPixApplication
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.NormPixTheme

/**
 * A due reminder on its own screen, over a locked phone: what a notification's full-screen alert opens, where Android allows one.
 * It is only the reminder's card - the title and when it was due - with Mark as paid, Snooze, Dismiss and Open; it holds nothing of
 * the user's data, so the lock screen shows no more than the notification would. "Open app" asks Android to unlock the phone first
 * and then opens NormPix, which asks for its own lock if the user set one.
 *
 * Android raises it only when the phone is locked or its screen is off (on an unlocked phone the notification is a heads-up and,
 * if the user allowed it, the floating card appears instead). If the full-screen permission is not granted this is never started
 * and the notification is all there is.
 */
class ReminderAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverTheLockScreen()
        enableEdgeToEdge()
        val id = intent?.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1) ?: -1
        if (id < 0) {
            finish()
            return
        }
        val container = (application as NormPixApplication).container
        setContent {
            val prefs by container.preferences.state.collectAsState()
            // Read once the list is loaded (the alarm can start this before anything else in the app has run).
            val reminder by produceState<Reminder?>(null, id) {
                container.reminders.ensureLoaded()
                value = container.reminders.findById(id)
            }
            NormPixTheme(Appearance(prefs.mode, prefs.colorSource, prefs.accent, wallpaperPalette = prefs.wallpaperPalette)) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    reminder?.takeIf { it.status != ReminderStatus.Completed }?.let { shown ->
                        ReminderAlertCard(
                            reminder = shown,
                            onAction = { action -> act(id, action) },
                            modifier = Modifier.widthIn(max = 480.dp),
                        )
                    }
                }
            }
        }
    }

    private fun act(id: Long, action: ReminderCardAction) {
        if (action == ReminderCardAction.Open) {
            // Unlock first; the app opens on the reminder once the phone is open.
            val keyguard = getSystemService(KeyguardManager::class.java)
            if (keyguard != null && keyguard.isKeyguardLocked) {
                keyguard.requestDismissKeyguard(
                    this,
                    object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            ReminderDelivery.perform(this@ReminderAlertActivity, id, action)
                            finish()
                        }

                        override fun onDismissCancelled() = Unit

                        override fun onDismissError() = Unit
                    },
                )
                return
            }
        }
        ReminderDelivery.perform(this, id, action)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun showOverTheLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }
}
