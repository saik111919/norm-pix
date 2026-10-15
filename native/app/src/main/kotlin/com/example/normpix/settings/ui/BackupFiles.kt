package com.example.normpix.settings.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.normpix.navigation.LocalAppContainer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thrown when a picked file is bigger than any real backup could be. */
class BackupTooLargeException : IOException("File too large")

/**
 * Where backups go and come from: the system's own save/open dialogs (Storage
 * Access Framework). The user picks every location, so the app needs no
 * storage permission and never writes anywhere on its own.
 */
interface BackupFiles {
    /** Asks the user where to save [bytes]. Returns false if they cancelled; throws if writing failed. */
    suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): Boolean

    /** Asks the user for a file to open. Returns null if they cancelled; throws if reading failed. */
    suspend fun open(): ByteArray?

    /**
     * Asks where to save, then has [write] write the file there as it goes - for one too big to build in memory first (a
     * backup with its pictures). False if the user cancelled; throws if writing failed, leaving no half-written file.
     */
    suspend fun saveStreaming(fileName: String, mimeType: String, write: suspend (OutputStream) -> Unit): Boolean {
        val buffer = ByteArrayOutputStream()
        write(buffer)
        return save(fileName, mimeType, buffer.toByteArray())
    }

    /** Asks for a file, then hands what is in it to [read] as it is read. Null if the user cancelled. */
    suspend fun <T> openStreaming(read: suspend (InputStream) -> T): T? = open()?.let { read(ByteArrayInputStream(it)) }

    companion object {
        /** Far above any real ledger; protects memory from picking a huge unrelated file. */
        const val MAX_BYTES = 32L * 1024 * 1024
    }
}

private class CreateDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

private class OpenDocument : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            // Many file managers label .json files as octet-stream or text, so don't filter them out.
            .setType("*/*")

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** [BackupFiles] backed by the system document picker, for the current activity. */
@Composable
fun rememberBackupFiles(): BackupFiles {
    val context = LocalContext.current.applicationContext
    val lock = LocalAppContainer.current.lock
    val pending = remember { arrayOfNulls<CompletableDeferred<Uri?>>(1) }
    val createLauncher = rememberLauncherForActivityResult(CreateDocument()) { uri -> pending[0]?.complete(uri) }
    val openLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri -> pending[0]?.complete(uri) }

    return remember(context, lock, createLauncher, openLauncher) {
        object : BackupFiles {
            private suspend fun pick(launch: () -> Unit): Uri? {
                val result = CompletableDeferred<Uri?>()
                pending[0]?.complete(null)
                pending[0] = result
                // The system picker isn't "leaving" NormPix: don't re-lock while it's open.
                lock.expectExternalActivity()
                launch()
                return try {
                    result.await()
                } finally {
                    if (pending[0] === result) pending[0] = null
                }
            }

            override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
                val uri = pick { createLauncher.launch(fileName to mimeType) } ?: return false
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    try {
                        val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
                            ?: resolver.openOutputStream(uri, "w")
                            ?: throw IOException("Couldn't open the file for writing")
                        stream.use { it.write(bytes) }
                    } catch (e: Exception) {
                        // Don't leave an empty or half-written file behind.
                        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                        throw e
                    }
                }
                return true
            }

            override suspend fun saveStreaming(fileName: String, mimeType: String, write: suspend (OutputStream) -> Unit): Boolean {
                val uri = pick { createLauncher.launch(fileName to mimeType) } ?: return false
                val resolver = context.contentResolver
                try {
                    val stream = withContext(Dispatchers.IO) {
                        runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull() ?: resolver.openOutputStream(uri, "w")
                    } ?: throw IOException("Couldn't open the file for writing")
                    stream.buffered().use { write(it) }
                } catch (e: Exception) {
                    // Don't leave an empty or half-written file behind.
                    withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
                    throw e
                }
                return true
            }

            override suspend fun <T> openStreaming(read: suspend (InputStream) -> T): T? {
                val uri = pick { openLauncher.launch(Unit) } ?: return null
                val stream = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri) } ?: throw IOException("Couldn't open the file")
                return stream.buffered().use { read(it) }
            }

            override suspend fun open(): ByteArray? {
                val uri = pick { openLauncher.launch(Unit) } ?: return null
                return withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Couldn't open the file")
                    stream.use { input ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > BackupFiles.MAX_BYTES) throw BackupTooLargeException()
                            out.write(buffer, 0, read)
                        }
                        out.toByteArray()
                    }
                }
            }
        }
    }
}
