package com.example.normpix.updates

import com.example.normpix.updates.Releases.LATEST
import com.example.normpix.updates.Releases.apkName
import com.example.normpix.updates.Releases.installed
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive

/** Asking GitHub for the latest release: what is an update, and every way the asking can go wrong. */
class UpdateCheckerTest {
    private val network = FakeNetwork()

    private fun checker(app: InstalledApp = installed) = UpdateChecker(GitHubAccess(network, "NormPix/test"), Releases.REPO, app)

    private fun problem(app: InstalledApp = installed): UpdateProblem = assertFailsWith<UpdateException> { checker(app).latest() }.problem

    @Test
    fun `a newer release is an update`() {
        Releases.publish(network)
        val newer = assertIs<ReleaseCheck.Newer>(checker().latest())
        assertEquals("1.0.3", newer.release.versionName)
        // The API for the release, then its update.json through GitHub's redirect - and nothing else (the APK waits).
        assertEquals(
            listOf(LATEST, Releases.download("v1.0.3", "update.json")),
            network.asked.take(2),
        )
        assertTrue(network.asked.none { it.endsWith(".apk") }, "nothing is downloaded by a check")
    }

    @Test
    fun `the same or an older version is not`() {
        Releases.publish(network, "1.0.2", 10002)
        assertIs<ReleaseCheck.Current>(checker().latest())
        Releases.publish(network, "1.0.1", 10001)
        assertEquals("1.0.1", assertIs<ReleaseCheck.Current>(checker().latest()).latest.versionName)
    }

    @Test
    fun `versions are compared as numbers, not as text`() {
        // "1.0.10" < "1.0.9" as text; 10010 > 10009 as numbers.
        Releases.publish(network, "1.0.10", 10010)
        assertIs<ReleaseCheck.Newer>(checker(installed.copy(versionCode = 10009, versionName = "1.0.9")).latest())
        Releases.publish(network, "1.0.9", 10009)
        assertIs<ReleaseCheck.Current>(checker(installed.copy(versionCode = 10010, versionName = "1.0.10")).latest())
    }

    @Test
    fun `a draft, a prerelease, or a release without update json is never offered`() {
        val files = listOf("update.json" to 300L, apkName("1.0.3") to 300_000L)
        network.answer(LATEST, Releases.api("v1.0.3", files, prerelease = true))
        assertIs<UpdateProblem.BadRelease>(problem())
        network.answer(LATEST, Releases.api("v1.0.3", files, draft = true))
        assertIs<UpdateProblem.BadRelease>(problem())
        network.answer(LATEST, Releases.api("v1.0.3", listOf(apkName("1.0.3") to 300_000L)))
        assertIs<UpdateProblem.BadRelease>(problem())
        assertEquals(listOf(LATEST), network.asked.distinct(), "update.json isn't asked for when it isn't attached")
    }

    @Test
    fun `a release for another app, or for a newer Android, says so`() {
        Releases.publish(network, changes = mapOf("applicationId" to JsonPrimitive("com.example.other")))
        assertEquals(UpdateProblem.OtherApp("com.example.other"), problem())
        Releases.publish(network, changes = mapOf("minSdk" to JsonPrimitive(36)))
        assertEquals(UpdateProblem.NeedsAndroid(36), problem())
    }

    @Test
    fun `no release, rate limits and server errors`() {
        // Nothing published (or a private repository, which looks the same without signing in).
        assertEquals(UpdateProblem.NoRelease, problem())
        network.answer(LATEST, code = 403, headers = mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "1792000000"))
        assertEquals(UpdateProblem.RateLimited(Instant.ofEpochSecond(1_792_000_000)), problem())
        network.answer(LATEST, code = 429, headers = mapOf("Retry-After" to "60"))
        assertEquals(UpdateProblem.RateLimited(null), problem())
        network.answer(LATEST, code = 403)
        assertEquals(UpdateProblem.ServerError(403), problem())
        network.answer(LATEST, code = 502)
        assertEquals(UpdateProblem.ServerError(502), problem())
    }

    @Test
    fun `no connection, a timeout, and a failed secure connection`() {
        network.fail(LATEST, UnknownHostException("api.github.com"))
        assertEquals(UpdateProblem.Offline, problem())
        network.fail(LATEST, SocketTimeoutException("timeout"))
        assertEquals(UpdateProblem.TimedOut, problem())
        network.fail(LATEST, SSLHandshakeException("bad certificate"))
        assertEquals(UpdateProblem.Insecure, problem())
        // update.json timing out once the release was found.
        Releases.publish(network)
        network.fail(Releases.download("v1.0.3", "update.json"), SocketTimeoutException("timeout"))
        assertEquals(UpdateProblem.TimedOut, problem())
    }

    @Test
    fun `malformed or oversized answers are refused`() {
        network.answer(LATEST, "<html>Unicorn!</html>")
        assertIs<UpdateProblem.BadRelease>(problem())
        Releases.publish(network)
        network.answer(Releases.download("v1.0.3", "update.json"), "{\"schema\": 1, \"versionCode\": ")
        assertIs<UpdateProblem.BadRelease>(problem())
        network.answer(Releases.download("v1.0.3", "update.json"), body = ByteArray(65 * 1024) { ' '.code.toByte() })
        assertIs<UpdateProblem.BadRelease>(problem())
    }

    @Test
    fun `redirects are followed only to GitHub, over HTTPS`() {
        Releases.publish(network)
        val metadata = Releases.download("v1.0.3", "update.json")
        network.redirect(metadata, "https://evil.example.com/update.json")
        assertEquals(UpdateProblem.Untrusted, problem())
        network.redirect(metadata, "http://objects.githubusercontent.com/update.json")
        assertEquals(UpdateProblem.Untrusted, problem())
        network.redirect(metadata, "https://github.com.evil.example/update.json")
        assertEquals(UpdateProblem.Untrusted, problem())
        network.redirect(metadata, "https://objects.githubusercontent.com:8443/update.json")
        assertEquals(UpdateProblem.Untrusted, problem())
        // A redirect that never ends.
        network.redirect(metadata, metadata)
        assertEquals(UpdateProblem.Untrusted, problem())
        assertTrue(network.asked.none { "evil" in it || it.startsWith("http:") }, "an untrusted address is never even asked")
    }

    @Test
    fun `GitHub is asked plainly, without credentials`() {
        Releases.publish(network)
        checker().latest()
        val first = network.headersSent.first()
        assertEquals("application/vnd.github+json", first["Accept"])
        assertEquals("NormPix/test", first["User-Agent"])
        assertTrue(network.headersSent.none { headers -> headers.keys.any { it.equals("Authorization", ignoreCase = true) } })
    }
}
