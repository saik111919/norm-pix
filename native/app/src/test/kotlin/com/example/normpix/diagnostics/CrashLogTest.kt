package com.example.normpix.diagnostics

import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.diagnostics.CrashLog.Entry
import com.example.normpix.diagnostics.CrashLog.Kind
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reports kept on the phone: the newest few, one small file each, newest first; a write cut short loses one report and not the log;
 * clearing empties it; and what the handler does when something is thrown that nothing catches - keep a scrubbed report, then let the
 * app go the way it was going.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashLogTest {
    private lateinit var dir: File
    private lateinit var log: CrashLog
    private val where = CrashReport.Where("NormPix 1.5.0 (10500)", "Android 16 (API 36)", "motorola edge 50 neo")

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("crash-log-test").toFile()
        log = CrashLog(File(dir, "crash-log"), maxEntries = 3)
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun entry(at: Long, kind: Kind = Kind.Crash, text: String = "report $at") = Entry(at, kind, text)

    @Test
    fun `reports come back newest first, each with its text and kind`() {
        log.record(entry(1_000))
        log.record(entry(3_000, Kind.Anr, "not responding"))
        log.record(entry(2_000, Kind.Native))

        assertEquals(listOf(3_000L, 2_000L, 1_000L), log.entries().map { it.at })
        assertEquals(Kind.Anr, log.entries().first().kind)
        assertEquals("not responding", log.entries().first().text)
        assertEquals(3_000L, log.newestAt())
        assertEquals(3, log.count())
        assertEquals("not responding\n\nreport 2000\n\nreport 1000", log.text())
    }

    @Test
    fun `only the newest few are kept`() {
        for (at in 1L..6L) log.record(entry(at * 1_000))
        assertEquals(listOf(6_000L, 5_000L, 4_000L), log.entries().map { it.at })
        assertEquals(3, log.count())
    }

    @Test
    fun `a report is cut to the limit, so a log cannot grow without one`() {
        log.record(entry(1_000, text = "x".repeat(CrashLog.MAX_TEXT * 3)))
        assertEquals(CrashLog.MAX_TEXT, log.entries().single().text.length)
    }

    @Test
    fun `an empty log has nothing, and clearing empties a full one`() {
        assertEquals(emptyList(), log.entries())
        assertEquals(0L, log.newestAt())
        assertEquals("", log.text())

        log.record(entry(1_000))
        log.record(entry(2_000))
        log.clear()
        assertEquals(0, log.count())
        assertEquals(0L, log.newestAt())
    }

    @Test
    fun `a file left by a write that was cut short, or one that is not a report, is not one`() {
        log.record(entry(1_000))
        val folder = File(dir, "crash-log")
        File(folder, "2000-crash.txt.tmp").writeText("half")
        File(folder, "notes.txt").writeText("not a report")
        File(folder, "3000-unknown.txt").writeText("a kind that is not known")

        assertEquals(listOf(1_000L), log.entries().map { it.at })
        assertEquals(1, log.count())
    }

    @Test
    fun `a place that cannot be written is lost, never an error`() {
        val blocked = File(dir, "not-a-folder").also { it.writeText("a file where the folder should be") }
        CrashLog(File(blocked, "crash-log")).record(entry(1_000)) // must not throw
        assertEquals(emptyList(), CrashLog(File(blocked, "crash-log")).entries())
    }

    @Test
    fun `the handler keeps a scrubbed report and then hands the error on to what was there before`() {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        var handedOn: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, error -> handedOn = error }
        try {
            val context: Context = ApplicationProvider.getApplicationContext()
            CrashReporting.install(context, log)
            val error = IllegalStateException("Card 4111111111111111 for 'Ramesh Kumar' failed")

            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)

            val kept = log.entries().single()
            assertEquals(Kind.Crash, kept.kind)
            assertTrue("java.lang.IllegalStateException: Card <n> for '...' failed" in kept.text, kept.text)
            assertFalse("4111" in kept.text)
            assertFalse("Ramesh" in kept.text)
            assertTrue("Thread: " in kept.text)
            assertTrue("NormPix" in kept.text && "Android" in kept.text, "says where it ran")
            assertEquals(error, handedOn, "the system still gets to close the app as it always did")
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
    }

    @Test
    fun `an app not responding and a native crash are kept, other endings are not a fault`() {
        val anr = assertNotNull(CrashReporting.exitEntry(ApplicationExitInfo.REASON_ANR, 5_000, "input dispatching timed out (pid 4242)", where))
        assertEquals(Kind.Anr, anr.kind)
        assertEquals(5_000L, anr.at)
        assertTrue("not responding" in anr.text)
        assertFalse("4242" in anr.text)

        assertEquals(Kind.Native, CrashReporting.exitEntry(ApplicationExitInfo.REASON_CRASH_NATIVE, 6_000, null, where)?.kind)
        assertEquals(Kind.Crash, CrashReporting.exitEntry(ApplicationExitInfo.REASON_INITIALIZATION_FAILURE, 7_000, null, where)?.kind)
        for (reason in listOf(
            ApplicationExitInfo.REASON_EXIT_SELF,
            ApplicationExitInfo.REASON_USER_REQUESTED,
            ApplicationExitInfo.REASON_USER_STOPPED,
            ApplicationExitInfo.REASON_LOW_MEMORY,
            ApplicationExitInfo.REASON_OTHER,
            ApplicationExitInfo.REASON_CRASH, // a Java crash: the handler has it already
        )) {
            assertNull(CrashReporting.exitEntry(reason, 8_000, "x", where), "reason $reason")
        }
    }
}
