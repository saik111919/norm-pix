package com.normpix.cashflow

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.NotificationManagerCompat
import com.normpix.cashflow.ui.AlarmScreenContent
import com.normpix.cashflow.ui.alarmBody

/**
 * Launched via a full-screen-intent notification from [ReminderAlarmReceiver] — shows the real
 * alarm UI over the lock screen (or on top of whatever app is open) even if the NormPix process
 * had been killed, since Android starts a fresh process for this Activity as needed.
 */
class AlarmActivity : ComponentActivity() {
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val id = intent.getStringExtra("reminderId")
        val r = id?.let { rid -> loadReminders(this)?.find { it.id == rid } }
        if (r == null) {
            finish()
            return
        }

        NotificationManagerCompat.from(this).cancel(("alarm_" + r.id).hashCode())
        if (r.vibration) startVibration()

        val prefs = getSharedPreferences("cashflow", Context.MODE_PRIVATE)
        val seed = runCatching { Seed.valueOf(prefs.getString("seed", "GREEN")!!) }.getOrDefault(Seed.GREEN)
        val mode = runCatching { ThemeMode.valueOf(prefs.getString("mode", "LIGHT")!!) }.getOrDefault(ThemeMode.LIGHT)
        // A throwaway state, just to compute the live body text (spend totals, budget %, etc.)
        // the same way the in-app preview does — this prototype's transaction data isn't
        // otherwise persisted across process death, so this mirrors a fresh app launch.
        val bodyState = CashflowState(this, prefs)

        setContent {
            CashflowTheme(seed = seed, mode = mode) {
                AlarmScreenContent(
                    icon = r.icon,
                    title = r.title,
                    priority = r.priority,
                    body = alarmBody(bodyState, r),
                    onSnooze = { snooze(r) },
                    onDismiss = { dismiss() },
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 500, 300, 500, 300, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }

    private fun snooze(r: Reminder) {
        vibrator?.cancel()
        scheduleExactAt(this, r, System.currentTimeMillis() + 10 * 60 * 1000)
        finish()
    }

    private fun dismiss() {
        vibrator?.cancel()
        finish()
    }

    override fun onDestroy() {
        vibrator?.cancel()
        super.onDestroy()
    }
}
