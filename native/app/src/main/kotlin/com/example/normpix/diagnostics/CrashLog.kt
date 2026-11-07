package com.example.normpix.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.example.normpix.settings.AppPreferences
import java.io.File

/**
 * The crash reports kept on this phone, in the app's own storage (`files/crash-log/`), one small file for each, the newest 20 only.
 * Nothing leaves the phone by itself: the owner looks at them in About > Crash log and chooses whether to share one.
 *
 * Each report is a file named for when it was made and what it was (`<epoch millis>-crash.txt`), so reading needs no parsing and a
 * write that was cut short loses one report, not the log.
 */
class CrashLog(private val dir: File, private val maxEntries: Int = MAX_ENTRIES) {
    enum class Kind(val word: String, val label: String) {
        Crash("crash", "Crash"),
        Anr("anr", "Not responding"),
        Native("native", "Native crash"),
    }

    /** One report: when it was made (epoch millis), what kind, and its text. */
    data class Entry(val at: Long, val kind: Kind, val text: String)

    /** Keeps [entry], and drops the oldest ones beyond the limit. A report that cannot be written is lost, never an error. */
    fun record(entry: Entry) {
        runCatching {
            dir.mkdirs()
            val file = File(dir, "${entry.at}-${entry.kind.word}.txt")
            val temporary = File(dir, "${file.name}.tmp")
            temporary.writeText(entry.text.take(MAX_TEXT))
            if (!temporary.renameTo(file)) {
                file.writeText(entry.text.take(MAX_TEXT))
                temporary.delete()
            }
            files().drop(maxEntries).forEach { it.delete() }
        }
    }

    /** Every report, the newest first. */
    fun entries(): List<Entry> = files().mapNotNull { file ->
        val (at, kind) = parse(file.name) ?: return@mapNotNull null
        runCatching { Entry(at, kind, file.readText()) }.getOrNull()
    }

    /** When the newest report was made, 0 for none. */
    fun newestAt(): Long = files().firstNotNullOfOrNull { parse(it.name)?.first } ?: 0L

    /** How many reports there are. */
    fun count(): Int = files().size

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** All the reports as one text, the newest first - what is shown, and what is shared. */
    fun text(): String = entries().joinToString("\n\n") { it.text }

    /** The report files, newest first; a leftover `.tmp` from a write that was cut short is not one. */
    private fun files(): List<File> = dir.listFiles { file -> parse(file.name) != null }.orEmpty()
        .sortedByDescending { parse(it.name)!!.first }

    private fun parse(name: String): Pair<Long, Kind>? {
        if (!name.endsWith(".txt")) return null
        val stem = name.removeSuffix(".txt")
        val at = stem.substringBefore('-').toLongOrNull() ?: return null
        val kind = Kind.entries.firstOrNull { it.word == stem.substringAfter('-') } ?: return null
        return at to kind
    }

    companion object {
        const val MAX_ENTRIES = 20

        /** A report is cut to this many characters: a stack is long, a log that grows without limit is not welcome. */
        const val MAX_TEXT = 12_000

        fun of(context: Context): CrashLog = CrashLog(File(context.filesDir, "crash-log"))
    }
}

/** Taking reports: the handler for exceptions nothing caught, and what the system says about a process that ended badly. */
object CrashReporting {
    /** Where and on what NormPix is running, for the top of a report. */
    fun whereOf(context: Context): CrashReport.Where {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val version = info?.versionName ?: "?"
        val code = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() } ?: 0L
        return CrashReport.Where(
            app = "NormPix $version ($code)",
            android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }

    /**
     * Writes a report for every exception nothing catches, then lets the system do what it always did (the app closes). Called
     * once, first thing, so a failure while the app is starting is kept too.
     */
    fun install(context: Context, log: CrashLog = CrashLog.of(context)) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val at = System.currentTimeMillis()
                log.record(CrashLog.Entry(at, CrashLog.Kind.Crash, CrashReport.crash(at, thread.name, whereOf(app), error)))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * The process endings the handler cannot see - the app not responding, a native crash - that the system recorded since
     * [since] (epoch millis; Android 11 and later). Returns when the newest one was, or [since] if none.
     */
    fun recordProcessExits(context: Context, log: CrashLog, since: Long): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return since
        val manager = context.getSystemService(ActivityManager::class.java) ?: return since
        var newest = since
        val exits = runCatching { manager.getHistoricalProcessExitReasons(context.packageName, 0, 10) }.getOrNull().orEmpty()
        for (exit in exits) {
            if (exit.timestamp <= since) continue
            newest = maxOf(newest, exit.timestamp)
            val entry = exitEntry(exit.reason, exit.timestamp, exit.description, whereOf(context)) ?: continue
            log.record(entry)
        }
        return newest
    }

    /** The report for a process that ended with [reason] ([ApplicationExitInfo]'s), or null for the endings that are not a fault. */
    fun exitEntry(reason: Int, at: Long, description: String?, where: CrashReport.Where): CrashLog.Entry? {
        val (kind, what) = when (reason) {
            ApplicationExitInfo.REASON_ANR -> CrashLog.Kind.Anr to "The app was not responding and was stopped"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> CrashLog.Kind.Native to "A native crash"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> CrashLog.Kind.Crash to "The app failed to start"
            else -> return null
        }
        return CrashLog.Entry(at, kind, CrashReport.exit(at, where, what, description))
    }

    /** What to do on the first start of a process: take what the system recorded since last time, and remember how far it read. */
    fun takeProcessExits(context: Context, preferences: AppPreferences, log: CrashLog = CrashLog.of(context)) {
        runCatching {
            val since = preferences.state.value.crashExitsCheckedAt
            val newest = recordProcessExits(context, log, since)
            if (newest > since) preferences.setCrashExitsCheckedAt(newest)
        }
    }
}
