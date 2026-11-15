package com.example.normpix.backup

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.AutoBackupEvery
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The automatic backup's job: scheduled while it is on, as often as chosen, on battery and storage that aren't low; gone when off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupScheduleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val jobs: JobScheduler get() = context.getSystemService(JobScheduler::class.java)
    private fun scheduled(): JobInfo? = jobs.getPendingJob(AutoBackupSchedule.JOB_ID)

    @Test
    fun `on, it is one periodic job as often as chosen, persisted, never on a low battery or low storage, needing no network`() {
        AutoBackupSchedule.sync(context, AutoBackupPlan(enabled = true, every = AutoBackupEvery.Weekly))
        val job = assertNotNull(scheduled())
        assertTrue(job.isPeriodic)
        assertEquals(AutoBackupEvery.Weekly.intervalMs, job.intervalMillis)
        assertEquals(AutoBackupEvery.Weekly.intervalMs / 4, job.flexMillis)
        assertTrue(job.isPersisted)
        assertTrue(job.isRequireBatteryNotLow)
        assertTrue(job.isRequireStorageNotLow)
        assertEquals(JobInfo.NETWORK_TYPE_NONE, job.networkType)
        assertEquals(ComponentName(context, AutoBackupJobService::class.java), job.service)
    }

    @Test
    fun `the same plan leaves the job alone, another changes it, and off cancels it`() {
        AutoBackupSchedule.sync(context, AutoBackupPlan(true, AutoBackupEvery.Weekly))
        val first = scheduled()
        AutoBackupSchedule.sync(context, AutoBackupPlan(true, AutoBackupEvery.Weekly))
        assertSame(first, scheduled(), "not scheduled again, so its wait isn't started over")
        AutoBackupSchedule.sync(context, AutoBackupPlan(true, AutoBackupEvery.Daily))
        assertEquals(AutoBackupEvery.Daily.intervalMs, scheduled()!!.intervalMillis)
        AutoBackupSchedule.sync(context, AutoBackupPlan(true, AutoBackupEvery.Monthly))
        assertEquals(AutoBackupEvery.Monthly.intervalMs, scheduled()!!.intervalMillis)
        AutoBackupSchedule.sync(context, AutoBackupPlan(false, AutoBackupEvery.Monthly))
        assertNull(scheduled())
    }

    @Test
    fun `the plan is on only with the switch on and a folder`() {
        assertFalse(AppPrefsState().autoBackupPlan().enabled)
        assertFalse(AppPrefsState(autoBackupEnabled = true).autoBackupPlan().enabled)
        assertTrue(AppPrefsState(autoBackupEnabled = true, autoBackupFolder = "content://x/tree/y").autoBackupPlan().enabled)
        assertEquals(AutoBackupEvery.Weekly, AppPrefsState().autoBackupPlan().every)
    }

    @Test
    fun `the job's service is not exported and only JobScheduler can bind it`() {
        val info = context.packageManager.getServiceInfo(ComponentName(context, AutoBackupJobService::class.java), PackageManager.GET_META_DATA)
        assertFalse(info.exported)
        assertEquals("android.permission.BIND_JOB_SERVICE", info.permission)
    }
}
