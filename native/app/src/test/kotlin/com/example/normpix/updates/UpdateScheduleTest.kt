package com.example.normpix.updates

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.settings.BackgroundUpdates
import com.example.normpix.settings.UpdateCheckEvery
import com.example.normpix.settings.UpdateCheckWhen
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The check in the background: there while automatic checks are on, as often as the user chose, either whenever Android
 * finds fit or placed at the user's time, on the network the user's choices allow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateScheduleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val jobs: JobScheduler get() = context.getSystemService(JobScheduler::class.java)

    @AfterTest
    fun cleanUp() = jobs.cancelAll()

    private fun scheduled(): JobInfo? = jobs.getPendingJob(UpdateSchedule.JOB_ID)

    /** The network type a job asks for, read back the way it was set (`setRequiredNetworkType`): Android marks only the getter deprecated. */
    @Suppress("DEPRECATION")
    private fun networkTypeOf(job: JobInfo): Int = job.networkType

    private val hour = 60L * 60 * 1000

    /** 2026-10-16 10:00 UTC: a Friday morning. */
    private val morning = Instant.parse("2026-10-16T10:00:00Z").toEpochMilli()

    private val ninePm = 21 * 60

    private fun atSetTime(every: UpdateCheckEvery = UpdateCheckEvery.OnceADay, minuteOfDay: Int = ninePm, mobileData: Boolean = false) =
        UpdateCheckPlan(every = every, timing = UpdateCheckWhen.AtSetTime, minuteOfDay = minuteOfDay, mobileData = mobileData)

    @Test
    fun `once a day, kept over a restart, waiting for Wi-Fi to download`() {
        UpdateSchedule.sync(context, UpdateCheckPlan(mode = BackgroundUpdates.Download, mobileData = false))
        val job = assertNotNull(scheduled())
        assertTrue(job.isPeriodic)
        assertEquals(UpdateSchedule.INTERVAL_MS, job.intervalMillis)
        assertEquals(6 * hour, job.flexMillis, "the last quarter of the day, as it has always been")
        assertTrue(job.isPersisted)
        assertTrue(job.isRequireBatteryNotLow)
        assertEquals(JobInfo.NETWORK_TYPE_UNMETERED, networkTypeOf(job))
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
        UpdateSchedule.sync(context, UpdateCheckPlan(mode = BackgroundUpdates.Download, mobileData = false))
        val first = assertNotNull(scheduled())
        UpdateSchedule.sync(context, UpdateCheckPlan(mode = BackgroundUpdates.Install, mobileData = false))
        assertSame(first, scheduled(), "same network: its day isn't started again")
        UpdateSchedule.sync(context, UpdateCheckPlan(mode = BackgroundUpdates.Download, mobileData = true))
        val second = assertNotNull(scheduled())
        assertTrue(second !== first)
    }

    @Test
    fun `turning automatic checks off takes the run away`() {
        UpdateSchedule.sync(context, UpdateCheckPlan())
        UpdateSchedule.sync(context, UpdateCheckPlan(enabled = false))
        assertNull(scheduled())
        UpdateSchedule.sync(context, atSetTime())
        UpdateSchedule.sync(context, atSetTime().copy(enabled = false))
        assertNull(scheduled(), "a run at a set time goes too")
    }

    // ------------------------------------------------------------ how often, at any time

    @Test
    fun `each wait is the job's period, and Android may move a run within its last quarter`() {
        for (every in UpdateCheckEvery.entries) {
            UpdateSchedule.sync(context, UpdateCheckPlan(every = every))
            val job = assertNotNull(scheduled(), every.name)
            assertTrue(job.isPeriodic, every.name)
            assertEquals(every.intervalMs, job.intervalMillis, every.name)
            assertEquals(UpdateSchedule.flexFor(every.intervalMs), job.flexMillis, every.name)
        }
        assertEquals(6 * hour, UpdateSchedule.flexFor(UpdateCheckEvery.OnceADay.intervalMs))
        assertEquals(3 * hour, UpdateSchedule.flexFor(UpdateCheckEvery.TwiceADay.intervalMs))
    }

    @Test
    fun `a new wait replaces the run, the same one leaves it alone`() {
        UpdateSchedule.sync(context, UpdateCheckPlan(every = UpdateCheckEvery.OnceAWeek))
        val weekly = assertNotNull(scheduled())
        UpdateSchedule.sync(context, UpdateCheckPlan(every = UpdateCheckEvery.OnceAWeek))
        assertSame(weekly, scheduled())
        UpdateSchedule.sync(context, UpdateCheckPlan(every = UpdateCheckEvery.TwiceADay))
        assertEquals(UpdateCheckEvery.TwiceADay.intervalMs, assertNotNull(scheduled()).intervalMillis)
    }

    @Test
    fun `a run scheduled before the user could choose is the daily one, and is left alone`() {
        // As the last version scheduled it: a day, six hours of flex, the network in the extras and nothing else.
        val old = JobInfo.Builder(UpdateSchedule.JOB_ID, ComponentName(context, UpdateJobService::class.java))
            .setPeriodic(UpdateSchedule.INTERVAL_MS, 6 * hour)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            .setRequiresBatteryNotLow(true)
            .setPersisted(true)
            .setExtras(PersistableBundle().apply { putInt("network", JobInfo.NETWORK_TYPE_UNMETERED) })
            .build()
        jobs.schedule(old)
        val pending = assertNotNull(scheduled())
        UpdateSchedule.sync(context, UpdateCheckPlan(mode = BackgroundUpdates.Download, mobileData = false))
        assertSame(pending, scheduled(), "its day isn't started again by the update")
    }

    // ------------------------------------------------------------ at a set time

    @Test
    fun `at a set time it is one job that waits for the next time, on the same network and battery terms`() {
        UpdateSchedule.sync(context, atSetTime(), lastCheckAt = 0, now = morning, zone = ZoneOffset.UTC)
        val job = assertNotNull(scheduled())
        assertFalse(job.isPeriodic)
        assertEquals(11 * hour, job.minLatencyMillis, "from 10:00 to 9:00 PM")
        assertTrue(job.isPersisted)
        assertTrue(job.isRequireBatteryNotLow)
        assertEquals(JobInfo.NETWORK_TYPE_UNMETERED, networkTypeOf(job))
        UpdateSchedule.sync(context, atSetTime(mobileData = true), lastCheckAt = 0, now = morning, zone = ZoneOffset.UTC)
        assertEquals(JobInfo.NETWORK_TYPE_ANY, networkTypeOf(assertNotNull(scheduled())))
    }

    @Test
    fun `a time already past today waits for tomorrow's, counted from the last check`() {
        val evening = Instant.parse("2026-10-16T22:00:00Z").toEpochMilli()
        UpdateSchedule.sync(context, atSetTime(), lastCheckAt = 0, now = evening, zone = ZoneOffset.UTC)
        assertEquals(23 * hour, assertNotNull(scheduled()).minLatencyMillis)

        // Checked a day ago at 9:05 PM: due again tomorrow at 9:00 PM, though that is a few minutes short of a whole day.
        val checked = Instant.parse("2026-10-15T21:05:00Z").toEpochMilli()
        UpdateSchedule.sync(context, atSetTime(), lastCheckAt = checked, now = morning, zone = ZoneOffset.UTC, replace = true)
        assertEquals(11 * hour, assertNotNull(scheduled()).minLatencyMillis)
    }

    @Test
    fun `the same choices leave the run at a set time alone, however the clock has moved on`() {
        UpdateSchedule.sync(context, atSetTime(), lastCheckAt = 0, now = morning, zone = ZoneOffset.UTC)
        val first = assertNotNull(scheduled())
        UpdateSchedule.sync(context, atSetTime(), lastCheckAt = 0, now = morning + 5 * hour, zone = ZoneOffset.UTC)
        assertSame(first, scheduled(), "its wait isn't started again")
        // Though a run that has just finished is set again for the next time.
        UpdateSchedule.sync(context, atSetTime(), lastCheckAt = morning + 11 * hour, now = morning + 11 * hour, zone = ZoneOffset.UTC, replace = true)
        val next = assertNotNull(scheduled())
        assertTrue(next !== first)
        assertEquals(24 * hour, next.minLatencyMillis, "tomorrow at 9:00 PM")
    }

    @Test
    fun `another time, wait, network or time zone sets the run again`() {
        UpdateSchedule.sync(context, atSetTime(), now = morning, zone = ZoneOffset.UTC)
        val first = assertNotNull(scheduled())
        val changes = listOf(
            atSetTime(minuteOfDay = 7 * 60) to ZoneOffset.UTC,
            atSetTime(every = UpdateCheckEvery.OnceAWeek, minuteOfDay = 7 * 60) to ZoneOffset.UTC,
            atSetTime(every = UpdateCheckEvery.OnceAWeek, minuteOfDay = 7 * 60, mobileData = true) to ZoneOffset.UTC,
            atSetTime(every = UpdateCheckEvery.OnceAWeek, minuteOfDay = 7 * 60, mobileData = true) to ZoneId.of("Asia/Kolkata"),
        )
        var before: JobInfo = first
        for ((plan, zone) in changes) {
            UpdateSchedule.sync(context, plan, now = morning, zone = zone)
            val after = assertNotNull(scheduled(), "$plan")
            assertTrue(after !== before, "$plan in $zone")
            before = after
        }
    }

    @Test
    fun `switching between any time and a set time swaps the kind of job`() {
        UpdateSchedule.sync(context, UpdateCheckPlan(), now = morning, zone = ZoneOffset.UTC)
        assertTrue(assertNotNull(scheduled()).isPeriodic)
        UpdateSchedule.sync(context, atSetTime(), now = morning, zone = ZoneOffset.UTC)
        assertFalse(assertNotNull(scheduled()).isPeriodic)
        UpdateSchedule.sync(context, UpdateCheckPlan(), now = morning, zone = ZoneOffset.UTC)
        assertTrue(assertNotNull(scheduled()).isPeriodic)
    }
}

/** When a check at a set time is next due, the arithmetic apart from Android. */
class UpdateCheckTimesTest {
    private val utc = ZoneOffset.UTC
    private val newYork = ZoneId.of("America/New_York")
    private val ninePm = 21 * 60

    private fun at(text: String) = Instant.parse(text).toEpochMilli()

    private fun next(
        now: String,
        last: String?,
        every: UpdateCheckEvery = UpdateCheckEvery.OnceADay,
        minuteOfDay: Int = ninePm,
        zone: ZoneId = utc,
    ): String = Instant.ofEpochMilli(
        UpdateCheckTimes.nextCheckAt(at(now), last?.let { at(it) } ?: 0L, every, minuteOfDay, zone),
    ).toString()

    @Test
    fun `the times of day are the chosen one, and for twice a day the same time 12 hours later`() {
        assertEquals(listOf(ninePm), UpdateCheckTimes.minutesOfDay(UpdateCheckEvery.OnceADay, ninePm))
        assertEquals(listOf(ninePm), UpdateCheckTimes.minutesOfDay(UpdateCheckEvery.EveryThreeDays, ninePm))
        assertEquals(listOf(ninePm), UpdateCheckTimes.minutesOfDay(UpdateCheckEvery.OnceAWeek, ninePm))
        assertEquals(listOf(9 * 60, ninePm), UpdateCheckTimes.minutesOfDay(UpdateCheckEvery.TwiceADay, ninePm))
        assertEquals(listOf(9 * 60, ninePm), UpdateCheckTimes.minutesOfDay(UpdateCheckEvery.TwiceADay, 9 * 60))
        assertEquals(listOf(0, 12 * 60), UpdateCheckTimes.minutesOfDay(UpdateCheckEvery.TwiceADay, 0))
        assertEquals(listOf(60), UpdateCheckTimes.minutesOfDay(UpdateCheckEvery.OnceADay, 25 * 60), "a time outside the day wraps")
    }

    @Test
    fun `never checked, the next time is today's if it hasn't come, else tomorrow's, and never in the past`() {
        assertEquals("2026-10-16T21:00:00Z", next("2026-10-16T10:00:00Z", null))
        assertEquals("2026-10-17T21:00:00Z", next("2026-10-16T22:00:00Z", null))
        assertEquals("2026-10-16T21:00:00Z", next("2026-10-16T21:00:00Z", null), "right on the time is not the past")
        assertEquals("2026-10-17T21:00:00Z", next("2026-10-16T21:00:00.001Z", null))
    }

    @Test
    fun `a check made a little late still counts for its time, so the next is not pushed a day further`() {
        assertEquals("2026-10-16T21:00:00Z", next("2026-10-15T21:06:00Z", "2026-10-15T21:05:00Z"))
        // Hours late, say, because the phone was on mobile data until it found Wi-Fi.
        assertEquals("2026-10-16T21:00:00Z", next("2026-10-15T23:30:00Z", "2026-10-15T23:30:00Z"))
    }

    @Test
    fun `the next check comes a whole wait after the last, at the chosen time`() {
        // A launch check in the morning, so the day's evening check is not needed: one wait on, at 9:00 PM.
        assertEquals("2026-10-17T21:00:00Z", next("2026-10-16T08:00:00Z", "2026-10-16T08:00:00Z"))
        assertEquals("2026-10-19T21:00:00Z", next("2026-10-16T21:05:00Z", "2026-10-16T21:05:00Z", UpdateCheckEvery.EveryThreeDays))
        assertEquals("2026-10-23T21:00:00Z", next("2026-10-16T21:05:00Z", "2026-10-16T21:05:00Z", UpdateCheckEvery.OnceAWeek))
        // Long overdue: the next time of day, not another wait.
        assertEquals("2026-10-17T21:00:00Z", next("2026-10-16T22:00:00Z", "2026-10-01T09:00:00Z", UpdateCheckEvery.OnceAWeek))
    }

    @Test
    fun `twice a day is the chosen time and 12 hours later`() {
        assertEquals("2026-10-17T09:00:00Z", next("2026-10-16T21:05:00Z", "2026-10-16T21:05:00Z", UpdateCheckEvery.TwiceADay))
        assertEquals("2026-10-17T21:00:00Z", next("2026-10-17T09:10:00Z", "2026-10-17T09:10:00Z", UpdateCheckEvery.TwiceADay))
        assertEquals("2026-10-16T21:00:00Z", next("2026-10-16T10:00:00Z", null, UpdateCheckEvery.TwiceADay))
        assertEquals("2026-10-16T09:00:00Z", next("2026-10-16T08:00:00Z", null, UpdateCheckEvery.TwiceADay))
    }

    @Test
    fun `a clock set back before the last check counts as never having checked`() {
        assertEquals("2026-10-16T21:00:00Z", next("2026-10-16T10:00:00Z", "2026-10-20T10:00:00Z"))
    }

    @Test
    fun `the slack is a quarter of the wait, never more than six hours`() {
        val hour = 60L * 60 * 1000
        assertEquals(3 * hour, UpdateCheckTimes.slackFor(UpdateCheckEvery.TwiceADay))
        assertEquals(6 * hour, UpdateCheckTimes.slackFor(UpdateCheckEvery.OnceADay))
        assertEquals(6 * hour, UpdateCheckTimes.slackFor(UpdateCheckEvery.EveryThreeDays))
        assertEquals(6 * hour, UpdateCheckTimes.slackFor(UpdateCheckEvery.OnceAWeek))
    }

    @Test
    fun `daylight saving time moves the wait, not the time of day`() {
        // New York put its clocks forward at 2:00 AM on Sunday 8 March 2026: 9:00 PM Saturday and 9:00 PM Sunday are 23 hours apart.
        val saturday = "2026-03-08T02:05:00Z" // 9:05 PM EST on Saturday 7 March
        assertEquals("2026-03-09T01:00:00Z", next(saturday, saturday, zone = newYork), "9:00 PM EDT on Sunday")
        // A time that doesn't exist that day (2:30 AM) comes after the gap.
        assertEquals("2026-03-08T07:30:00Z", next("2026-03-07T17:00:00Z", null, minuteOfDay = 2 * 60 + 30, zone = newYork))
        // One that happens twice (1:30 AM on 1 November) is the first.
        assertEquals("2026-11-01T05:30:00Z", next("2026-10-31T16:00:00Z", null, minuteOfDay = 60 + 30, zone = newYork))
    }

    @Test
    fun `the time is read on the wall clock of the zone the phone is in`() {
        // 9:00 PM in Kolkata (UTC+5:30) is 15:30 UTC.
        assertEquals("2026-10-16T15:30:00Z", next("2026-10-16T10:00:00Z", null, zone = ZoneId.of("Asia/Kolkata")))
    }
}
