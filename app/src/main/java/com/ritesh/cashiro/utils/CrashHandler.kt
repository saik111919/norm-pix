package com.ritesh.cashiro.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ritesh.cashiro.BuildConfig
import com.ritesh.cashiro.presentation.ui.screens.crash.CrashActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val applicationContext: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashLog = buildCrashLog(throwable)
            Log.e("CashiroCrash", "App crashed on thread: ${thread.name}", throwable)

            // Write crash log to disk first — Intent extras are limited in size and
            // the process may die before the activity reads the extra.
            val logFile = writeCrashLogToDisk(crashLog)

            // Launch crash activity, passing BOTH the file path and a truncated inline log
            val intent = Intent(applicationContext, CrashActivity::class.java).apply {
                putExtra(EXTRA_CRASH_LOG, crashLog.take(MAX_INLINE_LOG_CHARS))
                if (logFile != null) {
                    putExtra(EXTRA_CRASH_LOG_FILE, logFile.absolutePath)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            applicationContext.startActivity(intent)

            // Give the OS a moment to start CrashActivity before we die
            Thread.sleep(500)

        } catch (e: Exception) {
            Log.e("CashiroCrash", "Error handling crash", e)
            defaultHandler?.uncaughtException(thread, throwable)
        } finally {
            // Kill the current process
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }
    }

    private fun writeCrashLogToDisk(crashLog: String): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(applicationContext.cacheDir, "cashiro_crash_$timestamp.txt")
            file.writeText(crashLog)
            Log.d("CashiroCrash", "Crash log written to: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e("CashiroCrash", "Failed to write crash log to disk", e)
            null
        }
    }

    private fun buildCrashLog(throwable: Throwable): String {
        val stackTrace = StringWriter().apply {
            throwable.printStackTrace(PrintWriter(this))
        }.toString()

        return buildString {
            appendLine("Cashiro AI Tracker — Crash Report")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("Manufacturer : ${Build.MANUFACTURER}")
            appendLine("Device       : ${Build.MODEL}")
            appendLine("Android      : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App Version  : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            appendLine("=".repeat(50))
            appendLine("Stack Trace:")
            appendLine("=".repeat(50))
            appendLine()
            append(stackTrace)
        }
    }

    companion object {
        const val EXTRA_CRASH_LOG = "crash_log"
        const val EXTRA_CRASH_LOG_FILE = "crash_log_file"
        private const val MAX_INLINE_LOG_CHARS = 50_000

        fun install(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            // Covers all Java/Kotlin threads — this is the global default for any thread
            // that hasn't set its own UncaughtExceptionHandler.
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Log.d("CashiroCrash", "CrashHandler installed")
        }

        fun triggerCrash(context: Context, throwable: Throwable) {
            try {
                val handler = CrashHandler(context.applicationContext)
                val crashLog = handler.buildCrashLog(throwable)
                Log.e("CashiroCrash", "Manually triggered crash screen", throwable)

                val logFile = handler.writeCrashLogToDisk(crashLog)

                val intent = Intent(context.applicationContext, CrashActivity::class.java).apply {
                    putExtra(EXTRA_CRASH_LOG, crashLog.take(MAX_INLINE_LOG_CHARS))
                    if (logFile != null) {
                        putExtra(EXTRA_CRASH_LOG_FILE, logFile.absolutePath)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                context.applicationContext.startActivity(intent)
            } catch (e: Exception) {
                Log.e("CashiroCrash", "Error in triggerCrash", e)
            }
        }
    }
}
