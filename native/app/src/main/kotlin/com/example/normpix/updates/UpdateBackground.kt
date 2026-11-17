package com.example.normpix.updates

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.example.normpix.MainActivity
import com.example.normpix.NormPixApplication
import com.example.normpix.R
import com.example.normpix.reminders.ReminderNotifications
import com.example.normpix.settings.AppNotifications
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.DEFAULT_UPDATE_CHECK_MINUTE
import com.example.normpix.settings.UpdateCheckEvery
import com.example.normpix.settings.UpdateCheckWhen
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** What Settings > Automatic updates asks of the check in the background (see [UpdateSchedule.sync]). */
data class UpdateCheckPlan(
    val enabled: Boolean = true,
    val every: UpdateCheckEvery = UpdateCheckEvery.OnceADay,
    val timing: UpdateCheckWhen = UpdateCheckWhen.AnyTime,
    /** For [UpdateCheckWhen.AtSetTime]: the time of day, as minutes since midnight. */
    val minuteOfDay: Int = DEFAULT_UPDATE_CHECK_MINUTE,
)

/**
 * The check in the background the user's choices ask for. The stored "what to do with an update found" and "download on mobile
 * data" choices of earlier versions play no part: the check only asks GitHub a small question and says so.
 */
fun AppPrefsState.updateCheckPlan() = UpdateCheckPlan(
    enabled = updateChecksEnabled,
    every = updateCheckEvery,
    timing = updateCheckWhen,
    minuteOfDay = updateCheckMinuteOfDay,
)

/**
 * The check for updates in the background, kept by Android's JobScheduler while the user lets NormPix check automatically:
 * as often as they chose, on any network (it only asks GitHub a small question; nothing is downloaded) and not while the
 * battery is low. It survives a restart.
 *
 * At any time, it is one periodic job whose run Android fits to the phone's other work. At a set time, it is a single job
 * that waits for the next slot ([UpdateCheckTimes.nextCheckAt]) and is set again for the one after when a run is done.
 */
object UpdateSchedule {
    const val JOB_ID = 7_601

    /** Once a day, which is what the check was before the user could choose. */
    const val INTERVAL_MS = 24L * 60 * 60 * 1000

    /** The first wait before a run that asked to go again ([BackgroundRun.Again]); it grows each time. */
    const val RETRY_MS = 30L * 60 * 1000

    private const val K_NETWORK = "network"
    private const val K_CONFIG = "config"

    /** The part of each wait a periodic run may move within, so Android can fit it to the phone's other work: the last quarter. */
    fun flexFor(intervalMs: Long): Long = intervalMs / 4

    /**
     * The network a run needs: any, as it only asks GitHub. Earlier versions waited for an unmetered one to download an update,
     * so a run they scheduled differs from this and is set again once.
     */
    const val NETWORK = JobInfo.NETWORK_TYPE_ANY

    /**
     * Brings the scheduled run in line with the user's choices ([plan]): none while automatic checks are off, otherwise the
     * one the plan asks for, on any network. A run already scheduled the same way is left alone, so its wait isn't started
     * again - unless [replace], for a run that has just finished and so is about to be gone.
     * [lastCheckAt] (epoch millis, 0 for never) is when GitHub last answered, from which a run at a set time is placed.
     */
    fun sync(
        context: Context,
        plan: UpdateCheckPlan,
        lastCheckAt: Long = 0,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        replace: Boolean = false,
    ) {
        val jobs = context.getSystemService(JobScheduler::class.java) ?: return
        if (!plan.enabled) return jobs.cancel(JOB_ID)
        val pending = jobs.getPendingJob(JOB_ID)
        val builder = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJobService::class.java))
            .setRequiredNetworkType(NETWORK)
            .setRequiresBatteryNotLow(true)
            .setPersisted(true)
            .setBackoffCriteria(RETRY_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
        val extras = PersistableBundle().apply { putInt(K_NETWORK, NETWORK) }
        when (plan.timing) {
            UpdateCheckWhen.AnyTime -> {
                val interval = plan.every.intervalMs
                if (!replace && pending != null && pending.isPeriodic && pending.intervalMillis == interval &&
                    pending.extras.getInt(K_NETWORK, -1) == NETWORK
                ) {
                    return
                }
                builder.setPeriodic(interval, flexFor(interval))
            }
            UpdateCheckWhen.AtSetTime -> {
                val config = listOf(plan.every.name, plan.minuteOfDay, NETWORK, zone.id).joinToString("|")
                if (!replace && pending != null && !pending.isPeriodic && pending.extras.getString(K_CONFIG, null) == config) return
                extras.putString(K_CONFIG, config)
                val at = UpdateCheckTimes.nextCheckAt(now, lastCheckAt, plan.every, plan.minuteOfDay, zone)
                builder.setMinimumLatency((at - now).coerceAtLeast(1L))
            }
        }
        runCatching { jobs.schedule(builder.setExtras(extras).build()) }
    }
}

/**
 * Where Android runs the check in the background (see [UpdateSchedule]): it asks GitHub and says an update is out
 * ([AppUpdater.runInBackground]). Not exported: only JobScheduler can bind it.
 */
class UpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var run: Job? = null

    private val container get() = (application as? NormPixApplication)?.container

    override fun onStartJob(params: JobParameters): Boolean {
        val container = container ?: return false
        val updater = container.updates?.takeIf { it.enabled } ?: return false
        val prefs = container.preferences.state.value
        if (!prefs.updateChecksEnabled) return false
        run = scope.launch {
            val result = try {
                updater.runInBackground()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                BackgroundRun.Done
            }
            jobFinished(params, result == BackgroundRun.Again)
            // A run at a set time is a single job: the next one is set now this one is done. One asking to go again is that next one.
            if (result != BackgroundRun.Again) {
                val plan = container.preferences.state.value.updateCheckPlan()
                if (plan.timing == UpdateCheckWhen.AtSetTime) {
                    UpdateSchedule.sync(this@UpdateJobService, plan, updater.lastCheckAt, replace = true)
                }
            }
        }
        return true
    }

    /** Android's time for the run is up, or the network it needed went: it stops, and is run again later. */
    override fun onStopJob(params: JobParameters): Boolean {
        run?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/**
 * The notifications about updates, on a quiet channel of their own ("App updates"): an update is out, or NormPix updated itself
 * (an earlier version did that). A tap on the first opens the update page, where Download update is. Only the version is said,
 * so there is nothing to hide on the lock screen. Nothing is posted while the user has NormPix's notifications off (Settings >
 * Notifications) or Android does.
 */
class SystemUpdateNotifier(private val context: Context) : UpdateNotifier {
    override fun available(release: UpdateRelease): Boolean = post(
        WAITING,
        "Update available",
        "NormPix ${release.versionName} is out. Tap to see what's new and download it.",
    )

    override fun installed(versionName: String): Boolean =
        post(INSTALLED, "NormPix updated", "NormPix is now on version $versionName.", openUpdate = false)

    override fun withdraw() {
        NotificationManagerCompat.from(context).cancel(TAG, WAITING)
    }

    private fun canPost(): Boolean {
        if (!AppNotifications.allowed(context) || !ReminderNotifications.hasPermission(context)) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        createChannel(context)
        val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_ID)
        return channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    @SuppressLint("MissingPermission")
    private fun post(id: Int, title: String, text: String, openUpdate: Boolean = true): Boolean {
        if (!canPost()) return false
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, id, openUpdate))
            .build()
        NotificationManagerCompat.from(context).notify(TAG, id, notification)
        return true
    }

    companion object {
        const val CHANNEL_ID = "normpix_updates"

        /** A tap on an update notification asks the app to show the update page. */
        const val EXTRA_OPEN_UPDATE = "open_update"

        /** Tagged, so their ids can't meet a reminder's. */
        private const val TAG = "normpix-update"

        /** The one notification about an update waiting: a later one replaces the earlier. */
        private const val WAITING = 1
        private const val INSTALLED = 2

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A new version of NormPix is out, or NormPix was updated"
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        /** Opens NormPix at the update page ([openUpdate] false: just opens it). [request] keeps one PendingIntent from standing in for another. */
        private fun openIntent(context: Context, request: Int, openUpdate: Boolean): PendingIntent =
            PendingIntent.getActivity(
                context,
                request,
                Intent(context, MainActivity::class.java)
                    .setData("normpix://updates/$request".toUri())
                    .putExtra(EXTRA_OPEN_UPDATE, openUpdate)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
