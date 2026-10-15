package com.example.normpix.updates

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.normpix.NormPixApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps an update download the user asked for going while NormPix is closed - swiped away, or left for another app - the
 * way a browser keeps a download: a foreground service, whose notification is the download's progress (with Cancel). It
 * starts with the download (Update now in the app, or Download on the "Update available" notification, which needs no
 * NormPix on screen) and stops when the download does; [AppUpdater] does the downloading and checking as always, and once
 * it is done the "ready to install" notification takes over.
 *
 * Not exported: only the app's own intents (and its notification's PendingIntent) reach it.
 */
class UpdateDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var run: Job? = null

    private val updater: AppUpdater? get() = (application as? NormPixApplication)?.container?.updates?.takeIf { it.enabled }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val updater = updater
        val downloading = updater?.state?.value as? UpdateState.Downloading
        // In the foreground at once, whatever comes next: Android stops an app that starts one and doesn't.
        val shown = runCatching {
            ServiceCompat.startForeground(
                this,
                SystemUpdateNotifier.DOWNLOAD_ID,
                SystemUpdateNotifier.progress(this, downloading?.release, downloading?.done ?: 0, downloading?.total ?: 0),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        }.isSuccess
        if (!shown || updater == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (run?.isActive == true) return START_NOT_STICKY
        run = scope.launch {
            try {
                if (intent?.action == ACTION_DOWNLOAD) updater.downloadNow()
                // As long as the download goes on; the updater keeps the notification up to date meanwhile.
                updater.state.first { it !is UpdateState.Downloading }
            } finally {
                ServiceCompat.stopForeground(this@UpdateDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** Android's daily allowance for this kind of work ran out (Android 15): the download stops, to be started again. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        updater?.cancelDownload()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Download on the "Update available" notification: find the release (if this process doesn't know it) and download it. */
        const val ACTION_DOWNLOAD = "com.example.normpix.updates.action.DOWNLOAD"
    }
}

/** Cancel on the download's notification. Not exported: only that notification's PendingIntent reaches it. */
class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updater = (context.applicationContext as? NormPixApplication)?.container?.updates ?: return
        if (intent.action == ACTION_CANCEL) updater.cancelDownload()
    }

    companion object {
        const val ACTION_CANCEL = "com.example.normpix.updates.action.CANCEL_DOWNLOAD"
    }
}
