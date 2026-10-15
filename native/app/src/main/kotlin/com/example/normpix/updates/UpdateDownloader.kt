package com.example.normpix.updates

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Downloads a release's APK into [dir] - the app's own cache, which no other app can write to - checking it on the way:
 * its exact size and its SHA-256. It is written to a `.part` file and only renamed to the APK once all of it came and
 * matched, so a partial or altered download is never taken for a finished one. A failed or cancelled download leaves
 * nothing behind. Blocking file and network work: call it off the main thread.
 */
class UpdateDownloader(private val github: GitHubAccess, private val dir: File) {
    /** The checked APK for [release], downloading it unless an earlier download already holds it. Cancellable between reads. */
    suspend fun download(release: UpdateRelease, onProgress: (done: Long, total: Long) -> Unit): File {
        val target = File(dir, fileName(release))
        if (target.isFile && target.length() == release.size && runCatching { sha256Of(target) }.getOrNull() == release.sha256) {
            onProgress(release.size, release.size)
            return target
        }
        if (!dir.isDirectory && !dir.mkdirs()) throw UpdateException(UpdateProblem.Storage)
        // Only one update is ever kept: older downloads and the leftovers of an interrupted one go first.
        clear()
        if (dir.usableSpace in 0 until release.size + SPACE_MARGIN) throw UpdateException(UpdateProblem.Storage)
        val part = File(dir, target.name + PART)
        try {
            github.open(release.apkUrl, "application/octet-stream").use { response ->
                if (response.length >= 0 && response.length != release.size) {
                    throw UpdateException(UpdateProblem.BadRelease("the download is ${response.length} bytes, the release says ${release.size}"))
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                val out = try {
                    FileOutputStream(part)
                } catch (e: IOException) {
                    throw UpdateException(UpdateProblem.Storage, e)
                }
                out.use {
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = try {
                            response.body.read(buffer)
                        } catch (e: IOException) {
                            throw UpdateException(UpdateProblem.Interrupted, e)
                        }
                        if (n < 0) break
                        done += n
                        // More than the release describes is not the release.
                        if (done > release.size) throw UpdateException(UpdateProblem.ChecksumMismatch)
                        try {
                            out.write(buffer, 0, n)
                        } catch (e: IOException) {
                            throw UpdateException(UpdateProblem.Storage, e)
                        }
                        digest.update(buffer, 0, n)
                        onProgress(done, release.size)
                    }
                    try {
                        out.fd.sync()
                    } catch (e: IOException) {
                        throw UpdateException(UpdateProblem.Storage, e)
                    }
                }
                if (done != release.size) throw UpdateException(UpdateProblem.Interrupted)
                if (digest.digest().toHex() != release.sha256) throw UpdateException(UpdateProblem.ChecksumMismatch)
            }
            if (!part.renameTo(target)) throw UpdateException(UpdateProblem.Storage)
            return target
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
    }

    /** Deletes every download but [keep]. */
    fun clear(keep: File? = null) {
        dir.listFiles()?.forEach { if (it != keep) it.delete() }
    }

    /** After an update installed, or at launch: deletes the downloads of [installedCode] and older, and any partial one. */
    fun clearInstalled(installedCode: Long) {
        dir.listFiles()?.forEach { file ->
            val code = NAME.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()
            if (code == null || code <= installedCode) file.delete()
        }
    }

    companion object {
        private const val PART = ".part"
        private const val BUFFER_BYTES = 64 * 1024
        /** Room left over after the download, so the phone isn't left full. */
        private const val SPACE_MARGIN = 20L * 1024 * 1024
        private val NAME = Regex("""NormPix-(\d+)\.apk""")

        fun fileName(release: UpdateRelease) = "NormPix-${release.versionCode}.apk"
    }
}

/** The SHA-256 of [file], lowercase hex. */
fun sha256Of(file: File): String = file.inputStream().use(::sha256Of)

fun sha256Of(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
    }
    return digest.digest().toHex()
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
