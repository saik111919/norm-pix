package com.example.normpix.updates

import android.annotation.SuppressLint
import android.app.Notification
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
import android.net.Uri
import android.os.PersistableBundle
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.normpix.MainActivity
import com.example.normpix.NormPixApplication
import com.example.normpix.R
import com.example.normpix.reminders.ReminderNotifications
import com.example.normpix.settings.AppNotifications
import com.example.normpix.settings.BackgroundUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The daily check for updates in the background, kept by Android's JobScheduler: once a day while the user lets NormPix
 * check automatically, on a network that may carry it - Wi-Fi (or another unmetered one) when the update is to be
 * downloaded and the user hasn't allowed mobile data - and not while the battery is low. It survives a restart.
 */
object UpdateSchedule {
    const val JOB_ID = 7_601
    const val INTERVAL_MS = 24L * 60 * 60 * 1000

    /** The part of the day the run may move within, so Android can fit it to the phone's other work. */
    const val FLEX_MS = 6L * 60 * 60 * 1000

    /** The first wait before a run that asked to go again ([BackgroundRun.Again]); it grows each time. */
    const val RETRY_MS = 30L * 60 * 1000

    private const val K_NETWORK = "network"

    /** The network a run needs: any to ask GitHub; an unmetered one to download, unless mobile data is allowed. */
    fun networkFor(mode: BackgroundUpdates, mobileData: Boolean): Int =
        if (!mode.downloads || mobileData) JobInfo.NETWORK_TYPE_ANY else JobInfo.NETWORK_TYPE_UNMETERED

    /**
     * Brings the scheduled run in line with the user's choices: none while automatic checks are off, otherwise one a day on
     * the network [networkFor] says. A run already scheduled the same way is left alone, so its day isn't started again.
     */
    fun sync(context: Context, enabled: Boolean, mode: BackgroundUpdates, mobileData: Boolean) {
        val jobs = context.getSystemService(JobScheduler::class.java) ?: return
        if (!enabled) return jobs.cancel(JOB_ID)
        val network = networkFor(mode, mobileData)
        val pending = jobs.getPendingJob(JOB_ID)
        if (pending != null && pending.isPeriodic && pending.intervalMillis == INTERVAL_MS && pending.extras.getInt(K_NETWORK, -1) == network) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJobService::class.java))
            .setPeriodic(INTERVAL_MS, FLEX_MS)
            .setRequiredNetworkType(network)
            .setRequiresBatteryNotLow(true)
            .setPersisted(true)
            .setBackoffCriteria(RETRY_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setExtras(PersistableBundle().apply { putInt(K_NETWORK, network) })
            .build()
        runCatching { jobs.schedule(job) }
    }
}

/**
 * Where Android runs the daily check (see [UpdateSchedule]): it does as much as the user chose in Settings > Automatic
 * updates ([AppUpdater.runInBackground]). Not exported: only JobScheduler can bind it.
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
                updater.runInBackground(prefs.backgroundUpdates)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                BackgroundRun.Done
            }
            jobFinished(params, result == BackgroundRun.Again)
        }
        return true
    }

    /** Android's time for the run is up, or the network it needed went: it stops, and is run again later. */
    override fun onStopJob(params: JobParameters): Boolean {
        run?.cancel()
        container?.updates?.stopBackgroundWork()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/**
 * The notifications about updates, on a quiet channel of their own ("App updates"): an update is out (with Download, which
 * fetches it without opening NormPix), it is downloading (how far, with Cancel; kept going by [UpdateDownloadService] when
 * the user started it, so NormPix can be closed meanwhile), it is downloaded and ready to install (with Install), or NormPix
 * installed one by itself. A tap opens the update page. Only the version is said, so there is nothing to hide on the lock
 * screen. Nothing is posted while the user has NormPix's notifications off (Settings > Notifications) or Android does.
 */
class SystemUpdateNotifier(private val context: Context) : UpdateNotifier {
    override fun available(release: UpdateRelease): Boolean = post(
        WAITING,
        "Update available",
        "NormPix ${release.versionName} is out. Download it now, or tap to see what's new.",
        action = NotificationCompat.Action(0, "Download", downloadIntent(context)),
    )

    override fun ready(release: UpdateRelease): Boolean = post(
        WAITING,
        "Update ready to install",
        "NormPix ${release.versionName} is downloaded and checked. Tap Install to update.",
        action = NotificationCompat.Action(0, "Install", openIntent(context, INSTALL_REQUEST, install = true)),
    )

    override fun installed(versionName: String): Boolean =
        post(INSTALLED, "NormPix updated", "NormPix is now on version $versionName.", openUpdate = false)

    override fun withdraw() {
        NotificationManagerCompat.from(context).cancel(TAG, WAITING)
    }

    /** Whether the download under way has asked for [UpdateDownloadService] already (once is enough). */
    @Volatile
    private var serviceAsked = false

    @SuppressLint("MissingPermission")
    override fun downloading(release: UpdateRelease, done: Long, total: Long, background: Boolean) {
        // One the user started is kept going by a foreground service, whose notification this is: NormPix can be closed.
        // (One the daily check started is kept going by its job; Android wouldn't start a service for it from the background.)
        if (!background && !serviceAsked) {
            serviceAsked = true
            runCatching { ContextCompat.startForegroundService(context, Intent(context, UpdateDownloadService::class.java)) }
        }
        if (!canPost()) return
        NotificationManagerCompat.from(context).notify(DOWNLOAD_ID, progress(context, release, done, total))
    }

    override fun downloadStopped() {
        serviceAsked = false
        NotificationManagerCompat.from(context).cancel(DOWNLOAD_ID)
    }

    private fun canPost(): Boolean {
        if (!AppNotifications.allowed(context) || !ReminderNotifications.hasPermission(context)) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        createChannel(context)
        val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_ID)
        return channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    @SuppressLint("MissingPermission")
    private fun post(
        id: Int,
        title: String,
        text: String,
        openUpdate: Boolean = true,
        action: NotificationCompat.Action? = null,
    ): Boolean {
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
            .setContentIntent(openIntent(context, id, install = false, openUpdate = openUpdate))
            .apply { if (action != null) addAction(action) }
            .build()
        NotificationManagerCompat.from(context).notify(TAG, id, notification)
        return true
    }

    companion object {
        const val CHANNEL_ID = "normpix_updates"

        /** A tap on an update notification asks the app to show the update page. */
        const val EXTRA_OPEN_UPDATE = "open_update"

        /** Install on the ready notification: the update page opens and installs it (Android then asks the user). */
        const val EXTRA_INSTALL_UPDATE = "install_update"

        /** Tagged, so their ids can't meet a reminder's. */
        private const val TAG = "normpix-update"

        /** The one notification about an update waiting (out, or ready): the later replaces the earlier. */
        private const val WAITING = 1
        private const val INSTALLED = 2

        /**
         * The download's progress: untagged, as a foreground service's notification has to be, and below zero, where no
         * reminder's (untagged, by its id) can be.
         */
        const val DOWNLOAD_ID = -7_601

        // What each tap or button opens; distinct, so one PendingIntent never stands in for another.
        private const val OPEN_REQUEST = 3
        private const val INSTALL_REQUEST = 4
        private const val DOWNLOAD_REQUEST = 5
        private const val CANCEL_REQUEST = 6

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A new version of NormPix is out, downloading, downloaded, or installed"
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        /** How far the download of [release] is ([done] of [total] bytes); [release] null while it is still being found. */
        fun progress(context: Context, release: UpdateRelease?, done: Long, total: Long): Notification {
            createChannel(context)
            val percent = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
            val known = release != null && total > 0
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_update)
                .setContentTitle(release?.let { "Downloading NormPix ${it.versionName}" } ?: "Getting the update")
                .setContentText(
                    if (known) {
                        "${Formatter.formatShortFileSize(context, done)} of ${Formatter.formatShortFileSize(context, total)} · $percent%"
                    } else {
                        "Starting…"
                    },
                )
                .setProgress(100, percent, !known)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openIntent(context, OPEN_REQUEST, install = false))
                .addAction(0, "Cancel", cancelIntent(context))
                .build()
        }

        /** Opens NormPix at the update page ([openUpdate] false: just opens it), and with [install] installs what's ready. */
        private fun openIntent(context: Context, request: Int, install: Boolean, openUpdate: Boolean = true): PendingIntent =
            PendingIntent.getActivity(
                context,
                request,
                Intent(context, MainActivity::class.java)
                    .setData(Uri.parse("normpix://updates/$request"))
                    .putExtra(EXTRA_OPEN_UPDATE, openUpdate)
                    .putExtra(EXTRA_INSTALL_UPDATE, install)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** Downloads the update without opening NormPix: Android lets a notification's button start the foreground service. */
        private fun downloadIntent(context: Context): PendingIntent =
            PendingIntent.getForegroundService(
                context,
                DOWNLOAD_REQUEST,
                Intent(context, UpdateDownloadService::class.java).setAction(UpdateDownloadService.ACTION_DOWNLOAD),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun cancelIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                CANCEL_REQUEST,
                Intent(context, UpdateActionReceiver::class.java).setAction(UpdateActionReceiver.ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
