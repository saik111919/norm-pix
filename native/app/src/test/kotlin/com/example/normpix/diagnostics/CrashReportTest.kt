package com.example.normpix.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a crash report says and, more to the point, what it leaves out: this app holds card numbers, account numbers and UPI ids, and
 * an exception's message can carry one, so a message is scrubbed before anything is written.
 */
class CrashReportTest {
    private val where = CrashReport.Where(app = "NormPix 1.5.0 (10500)", android = "Android 16 (API 36)", device = "motorola edge 50 neo")
    private val at = 1_800_000_000_000L

    @Test
    fun `numbers of four digits or more are taken out, however they are written`() {
        assertEquals("Card <n> declined", CrashReport.scrub("Card 4111111111111111 declined"))
        assertEquals("Card <n> declined", CrashReport.scrub("Card 4111 1111 1111 1111 declined"))
        assertEquals("Card <n> declined", CrashReport.scrub("Card 4111-1111-1111-1111 declined"))
        assertEquals("Ref <n>", CrashReport.scrub("Ref 612345678901"))
        assertEquals("Account <n>", CrashReport.scrub("Account 000123456789"))
        // Small numbers are how a check says what went wrong ("index 3 of 2"), and are kept.
        assertEquals("Index 3 out of bounds for length 2", CrashReport.scrub("Index 3 out of bounds for length 2"))
        assertEquals("Day 31 of 30", CrashReport.scrub("Day 31 of 30"))
    }

    @Test
    fun `handles and emails are taken out`() {
        assertEquals("Paid <id> failed", CrashReport.scrub("Paid ramesh.k@oksbi failed"))
        assertEquals("Mail <id>", CrashReport.scrub("Mail a.b+c@example.co.in"))
    }

    @Test
    fun `whatever is between quotes is taken out, since a name or a note can be in it`() {
        assertEquals("""Unknown category "..." for payee '...'""", CrashReport.scrub("""Unknown category "Rent to Ramesh" for payee 'Ramesh Kumar'"""))
        assertEquals("no such column: amount_paise", CrashReport.scrub("no such column: amount_paise"), "unquoted code names stay")
    }

    @Test
    fun `a message is one line, cut short, and nothing at all gives nothing`() {
        assertEquals("a b c", CrashReport.scrub("a\nb\r\nc".replace("\r\n", "\n")))
        assertEquals("", CrashReport.scrub(null))
        assertEquals("", CrashReport.scrub("   "))
        val long = CrashReport.scrub("x".repeat(1000))
        assertEquals(CrashReport.MAX_MESSAGE + 3, long.length)
        assertTrue(long.endsWith("..."))
    }

    @Test
    fun `a crash report says where, on what, on which thread and in which code`() {
        val error = IllegalStateException("Account 4111111111111111 has no 'Ramesh'")
        val text = CrashReport.crash(at, "main", where, error)

        assertTrue(text.startsWith("=== "), text)
        assertTrue("NormPix 1.5.0 (10500) - Android 16 (API 36) - motorola edge 50 neo" in text)
        assertTrue("Thread: main" in text)
        assertTrue("java.lang.IllegalStateException: Account <n> has no '...'" in text)
        assertTrue("    at com.example.normpix.diagnostics.CrashReportTest" in text, "the stack names the code")
        assertFalse("4111" in text, "no number from the message")
        assertFalse("Ramesh" in text, "no name from the message")
    }

    @Test
    fun `causes are followed, each scrubbed`() {
        val cause = RuntimeException("UPI ramesh@oksbi rejected")
        val text = CrashReport.crash(at, "worker", where, IllegalArgumentException("wrapper", cause))
        assertTrue("java.lang.IllegalArgumentException: wrapper" in text)
        assertTrue("Caused by: java.lang.RuntimeException: UPI <id> rejected" in text)
        assertFalse("oksbi" in text)
    }

    @Test
    fun `a cause that points back at the error does not loop`() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        val text = CrashReport.crash(at, "main", where, a)
        assertEquals(1, Regex("Caused by: ").findAll(text).count(), "b once, then a is already told")
    }

    @Test
    fun `a stack is cut to its first frames, saying how many were left out`() {
        val error = RuntimeException("deep")
        error.stackTrace = Array(100) { StackTraceElement("com.example.Deep", "call$it", "Deep.kt", it) }
        val text = CrashReport.crash(at, "main", where, error)
        assertEquals(40, Regex("    at com.example.Deep").findAll(text).count())
        assertTrue("    ... 60 more" in text)
    }

    @Test
    fun `what the system recorded of a process that ended badly is reported without its numbers`() {
        val text = CrashReport.exit(at, where, "The app was not responding and was stopped", "user request after error: Input dispatching timed out (pid 12345)")
        assertTrue("The app was not responding and was stopped" in text)
        assertTrue("Input dispatching timed out (pid <n>)" in text)
        assertFalse("12345" in text)
    }

    @Test
    fun `the time reads in the phone's own zone as year-month-day and hours`() {
        assertTrue(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""").matches(CrashReport.time(at)))
    }
}
