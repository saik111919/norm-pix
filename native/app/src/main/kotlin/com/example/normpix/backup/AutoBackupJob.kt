package com.example.normpix.backup

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.example.normpix.NormPixApplication
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.AutoBackupEvery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** What Settings > Data & backup > Automatic backup asks of the job: whether it runs, and how often. */
data class AutoBackupPlan(val enabled: Boolean, val every: AutoBackupEvery)

/** The job the user's choices ask for: on only with a folder to write to. */
fun AppPrefsState.autoBackupPlan() = AutoBackupPlan(autoBackupEnabled && autoBackupFolder.isNotEmpty(), autoBackupEvery)

/**
 * The automatic backup, kept by Android's JobScheduler while it is on: one periodic job, as often as the user chose (daily,
 * weekly or monthly), whose run Android fits to the phone's other work - not while the battery or the storage is low. It needs no
 * network (nothing is sent anywhere) and survives a restart. Copied from the update check's schedule (`UpdateSchedule`).
 */
object AutoBackupSchedule {
    /** Beside the update check's 7_601. */
    const val JOB_ID = 7_602

    /** The part of each wait a run may move within, so Android can fit it to the phone's other work: the last quarter. */
    fun flexFor(intervalMs: Long): Long = intervalMs / 4

    /**
     * Brings the scheduled job in line with [plan]: none while automatic backups are off, else one every [AutoBackupEvery.intervalMs].
     * A job already scheduled the same way is left alone, so its wait isn't started again.
     */
    fun sync(context: Context, plan: AutoBackupPlan) {
        val jobs = context.getSystemService(JobScheduler::class.java) ?: return
        if (!plan.enabled) return jobs.cancel(JOB_ID)
        val interval = plan.every.intervalMs
        val pending = jobs.getPendingJob(JOB_ID)
        if (pending != null && pending.isPeriodic && pending.intervalMillis == interval) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, AutoBackupJobService::class.java))
            .setPeriodic(interval, flexFor(interval))
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setPersisted(true)
            .build()
        runCatching { jobs.schedule(job) }
    }
}

/**
 * Where Android runs the automatic backup (see [AutoBackupSchedule]): [AutoBackupService.backUpNow] on the app's own container -
 * the user's data, whatever the screens show. What came of it is recorded there. Not exported: only JobScheduler can bind it.
 */
class AutoBackupJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var run: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val service = (application as? NormPixApplication)?.container?.autoBackup ?: return false
        if (!service.isOn) return false
        run = scope.launch {
            try {
                service.backUpNow()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Recorded by the service where it could say what went wrong; the next run tries again.
            }
            jobFinished(params, false)
        }
        return true
    }

    /** Android's time for the run is up: it stops (a file it was writing is deleted) and is run again later. */
    override fun onStopJob(params: JobParameters): Boolean {
        run?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
