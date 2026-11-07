package com.example.normpix.diagnostics

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * What a crash report says (2026-11-07; the owner asked for it on 2026-10-31: "when the app crashes can we keep a log so it can be
 * used to check the issues and fix them"). The app version, Android version and device model, the thread, and the exception's class
 * and stack - which are code, not data. An exception's *message* can carry a value (a number a check was made of, a name in quotes),
 * and this app holds card numbers, account numbers and UPI ids, so a message is scrubbed before it is written: long numbers, handles
 * like name@bank, and anything in quotes are taken out. The report stays on the phone; the owner chooses whether to send it.
 */
object CrashReport {
    /** A message is cut to this many characters after scrubbing. */
    const val MAX_MESSAGE = 240

    /** Most frames written for one exception, and most causes followed. */
    private const val MAX_FRAMES = 40
    private const val MAX_CAUSES = 4

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** A handle or an email: `name@bank`, `a.b+c@example.co.in`. */
    private val handle = Regex("""[\w.+\-]+@[\w\-]+(?:\.[\w\-]+)*""")

    /** Four digits or more, however they are spaced or hyphenated: a card or account number, a reference, a phone number. */
    private val longNumber = Regex("""\d(?:[\d \-]*\d){3,}""")

    /** Whatever is between quotes: a payee, a note, a category name typed by the user. */
    private val quoted = Regex("""(["'`])[^"'`]+\1""")

    /** Where and on what the app was running: shown at the top of every report. */
    data class Where(val app: String, val android: String, val device: String)

    /** [message] with anything that could be the user's data taken out, and cut short; empty for none. */
    fun scrub(message: String?): String {
        if (message.isNullOrBlank()) return ""
        var text = message.replace('\n', ' ').replace('\r', ' ')
        text = quoted.replace(text) { "${it.groupValues[1]}...${it.groupValues[1]}" }
        text = handle.replace(text, "<id>")
        text = longNumber.replace(text, "<n>")
        text = text.trim()
        return if (text.length > MAX_MESSAGE) text.take(MAX_MESSAGE) + "..." else text
    }

    /** The report for [error], thrown on [thread] at [at] (epoch millis), as plain text. */
    fun crash(at: Long, thread: String, where: Where, error: Throwable): String = buildString {
        header(at, where)
        appendLine("Thread: $thread")
        var current: Throwable? = error
        var depth = 0
        val seen = HashSet<Throwable>()
        while (current != null && seen.add(current) && depth <= MAX_CAUSES) {
            if (depth > 0) append("Caused by: ")
            appendLine(describe(current))
            val frames = current.stackTrace
            for (frame in frames.take(MAX_FRAMES)) appendLine("    at $frame")
            if (frames.size > MAX_FRAMES) appendLine("    ... ${frames.size - MAX_FRAMES} more")
            current = current.cause
            depth++
        }
    }.trimEnd()

    /** The report for something the system recorded about how the process ended (not responding, a native crash). */
    fun exit(at: Long, where: Where, what: String, description: String?): String = buildString {
        header(at, where)
        appendLine(what)
        scrub(description).takeIf { it.isNotEmpty() }?.let { appendLine(it) }
    }.trimEnd()

    /** "2026-11-07 17:20:31" in the phone's own time. */
    fun time(at: Long): String = stamp.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault()))

    private fun StringBuilder.header(at: Long, where: Where) {
        appendLine("=== ${time(at)} ===")
        appendLine("${where.app} - ${where.android} - ${where.device}")
    }

    /** The class, and the scrubbed message after it when there is one. */
    private fun describe(error: Throwable): String {
        val message = scrub(error.message)
        return if (message.isEmpty()) error.javaClass.name else "${error.javaClass.name}: $message"
    }
}
