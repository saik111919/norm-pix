package com.example.normpix.updates

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.settings.BackgroundUpdates
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The daily run in the background: there while automatic checks are on, on the network the user's choices allow. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateScheduleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val jobs: JobScheduler get() = context.getSystemService(JobScheduler::class.java)

    @AfterTest
    fun cleanUp() = jobs.cancelAll()

    private fun scheduled(): JobInfo? = jobs.getPendingJob(UpdateSchedule.JOB_ID)

    @Test
    fun `once a day, kept over a restart, waiting for Wi-Fi to download`() {
        UpdateSchedule.sync(context, enabled = true, mode = BackgroundUpdates.Download, mobileData = false)
        val job = assertNotNull(scheduled())
        assertTrue(job.isPeriodic)
        assertEquals(UpdateSchedule.INTERVAL_MS, job.intervalMillis)
        assertTrue(job.isPersisted)
        assertTrue(job.isRequireBatteryNotLow)
        assertEquals(JobInfo.NETWORK_TYPE_UNMETERED, UpdateSchedule.networkFor(BackgroundUpdates.Download, mobileData = false))
        assertEquals(JobInfo.NETWORK_TYPE_UNMETERED, UpdateSchedule.networkFor(BackgroundUpdates.Install, mobileData = false))
    }

    @Test
    fun `any network will do with mobile data allowed, or when nothing is downloaded`() {
        assertEquals(JobInfo.NETWORK_TYPE_ANY, UpdateSchedule.networkFor(BackgroundUpdates.Download, mobileData = true))
        assertEquals(JobInfo.NETWORK_TYPE_ANY, UpdateSchedule.networkFor(BackgroundUpdates.Notify, mobileData = false))
    }

    @Test
    fun `the same choices leave the scheduled run alone, and a new one replaces it`() {
        UpdateSchedule.sync(context, enabled = true, mode = BackgroundUpdates.Download, mobileData = false)
        val first = assertNotNull(scheduled())
        UpdateSchedule.sync(context, enabled = true, mode = BackgroundUpdates.Install, mobileData = false)
        assertSame(first, scheduled(), "same network: its day isn't started again")
        UpdateSchedule.sync(context, enabled = true, mode = BackgroundUpdates.Download, mobileData = true)
        val second = assertNotNull(scheduled())
        assertTrue(second !== first)
    }

    @Test
    fun `turning automatic checks off takes the run away`() {
        UpdateSchedule.sync(context, enabled = true, mode = BackgroundUpdates.Download, mobileData = false)
        UpdateSchedule.sync(context, enabled = false, mode = BackgroundUpdates.Download, mobileData = false)
        assertNull(scheduled())
    }
}
