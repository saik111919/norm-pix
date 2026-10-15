package com.example.normpix.updates

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.SocketTimeoutException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

/** Downloading an update: only a complete file that is exactly the release is ever kept. */
class UpdateDownloaderTest {
    private val network = FakeNetwork()
    private val dir: File = createTempDirectory("updates").toFile()
    private val downloader = UpdateDownloader(GitHubAccess(network, "NormPix/test"), dir)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun releaseFor(apk: ByteArray): UpdateRelease {
        val metadata = Releases.metadata("1.0.3", 10003, apk)
        val release = ReleaseMetadata.release(Releases.api("v1.0.3", listOf("update.json" to 1L, Releases.apkName("1.0.3") to apk.size.toLong())))
        return ReleaseMetadata.metadata(metadata, release, Releases.REPO).update
    }

    private val apkUrl = Releases.download("v1.0.3", Releases.apkName("1.0.3"))

    private fun failure(release: UpdateRelease): UpdateProblem = runBlocking {
        assertFailsWith<UpdateException> { downloader.download(release) { _, _ -> } }.problem
    }

    private fun leftovers() = dir.listFiles().orEmpty().map { it.name }

    @Test
    fun `a complete download is the release, and nothing partial is left beside it`() = runTest {
        val apk = Releases.publish(network)
        val release = releaseFor(apk)
        val progress = mutableListOf<Long>()
        val file = downloader.download(release) { done, total ->
            assertEquals(apk.size.toLong(), total)
            progress += done
        }
        assertContentEquals(apk, file.readBytes())
        assertEquals("NormPix-10003.apk", file.name)
        assertEquals(listOf("NormPix-10003.apk"), leftovers())
        assertEquals(apk.size.toLong(), progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b > a }, "progress only moves forward")
        // Fetched through GitHub's redirect to its download host.
        assertTrue(network.asked.any { it.startsWith("https://release-assets.githubusercontent.com/") })
    }

    @Test
    fun `a file that isn't the release is deleted`() {
        val apk = Releases.publish(network)
        val tampered = apk.copyOf().also { it[1000] = (it[1000] + 1).toByte() }
        Releases.serveFile(network, "v1.0.3", Releases.apkName("1.0.3"), tampered)
        assertEquals(UpdateProblem.ChecksumMismatch, failure(releaseFor(apk)))
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun `a download cut short is never taken for a complete one`() {
        val apk = Releases.publish(network)
        val release = releaseFor(apk)
        // The connection ends early, without a word.
        network.stream(apkUrl, length = -1) { ByteArrayInputStream(apk.copyOf(apk.size / 2)) }
        assertEquals(UpdateProblem.Interrupted, failure(release))
        assertEquals(emptyList(), leftovers())
        // The connection drops.
        network.stream(apkUrl, length = apk.size.toLong()) { DroppingStream(apk.copyOf(apk.size / 3)) }
        assertEquals(UpdateProblem.Interrupted, failure(release))
        assertEquals(emptyList(), leftovers())
        // More than the release is not the release either.
        network.answer(apkUrl, body = apk + ByteArray(10), length = -1)
        assertEquals(UpdateProblem.ChecksumMismatch, failure(release))
        // A server announcing another size is stopped before anything is written.
        network.answer(apkUrl, body = apk, length = apk.size + 5L)
        assertIs<UpdateProblem.BadRelease>(failure(release))
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun `server errors and timeouts fail the download`() {
        val apk = Releases.publish(network)
        val release = releaseFor(apk)
        network.answer(apkUrl, code = 503)
        assertEquals(UpdateProblem.ServerError(503), failure(release))
        network.fail(apkUrl, SocketTimeoutException("timeout"))
        assertEquals(UpdateProblem.TimedOut, failure(release))
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun `trying again after a failure downloads it whole`() = runTest {
        val apk = Releases.publish(network)
        val release = releaseFor(apk)
        network.stream(apkUrl, length = apk.size.toLong()) { DroppingStream(apk.copyOf(1000)) }
        assertFailsWith<UpdateException> { downloader.download(release) { _, _ -> } }
        network.answer(apkUrl, body = apk)
        assertContentEquals(apk, downloader.download(release) { _, _ -> }.readBytes())
    }

    @Test
    fun `a cancelled download leaves nothing behind`() = runTest {
        val apk = Releases.publish(network)
        val release = releaseFor(apk)
        lateinit var job: Job
        network.stream(apkUrl, length = apk.size.toLong()) { SlowStream(apk) { if (it > 100_000) job.cancel() } }
        val download = async { downloader.download(release) { _, _ -> } }
        job = download
        assertFailsWith<CancellationException> { download.await() }
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun `an update already downloaded and intact isn't downloaded again`() = runTest {
        val apk = Releases.publish(network)
        val release = releaseFor(apk)
        downloader.download(release) { _, _ -> }
        network.asked.clear()
        downloader.download(release) { _, _ -> }
        assertEquals(emptyList(), network.asked)
        // One that was altered since is.
        File(dir, "NormPix-10003.apk").writeBytes(ByteArray(apk.size))
        assertContentEquals(apk, downloader.download(release) { _, _ -> }.readBytes())
        assertTrue(network.asked.isNotEmpty())
    }

    @Test
    fun `downloads the installed version has made unnecessary are cleared`() {
        dir.mkdirs()
        listOf("NormPix-10002.apk", "NormPix-10003.apk", "NormPix-10004.apk", "NormPix-10004.apk.part", "stray.tmp")
            .forEach { File(dir, it).writeText("x") }
        downloader.clearInstalled(10003)
        assertEquals(listOf("NormPix-10004.apk"), leftovers())
    }
}

/** Hands out [bytes] a chunk at a time, telling [onRead] how far it got. */
private class SlowStream(private val bytes: ByteArray, private val onRead: (Int) -> Unit) : InputStream() {
    private var at = 0

    override fun read(): Int = if (at < bytes.size) bytes[at++].toInt() and 0xFF else -1

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (at >= bytes.size) return -1
        val n = minOf(len, 8 * 1024, bytes.size - at)
        System.arraycopy(bytes, at, b, off, n)
        at += n
        onRead(at)
        return n
    }
}
