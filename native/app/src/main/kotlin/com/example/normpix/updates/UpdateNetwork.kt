package com.example.normpix.updates

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.time.Instant
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

/**
 * The hosts the updater may reach: GitHub's API, its download links, and the two hosts GitHub hands those downloads to.
 * `res/xml/network_security_config.xml` holds the same list, so the rest of the app can't reach anything else either.
 */
val TRUSTED_UPDATE_HOSTS = setOf(
    "api.github.com",
    "github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
)

/** One HTTPS GET that follows no redirect: all the updater asks of the network, so tests can answer in its place. */
fun interface UpdateNetwork {
    /** The server's answer, whatever its status. Throws [IOException] when there is none (no connection, a timeout, TLS). */
    fun get(url: URL, headers: Map<String, String>): NetworkResponse
}

/** A server's answer: the caller reads [body] and closes it. */
class NetworkResponse(
    val code: Int,
    headers: Map<String, String>,
    /** The body's length when the server said, -1 when it didn't. */
    val length: Long,
    val body: InputStream,
    private val release: () -> Unit = {},
) : Closeable {
    private val headers = headers.mapKeys { it.key.lowercase() }

    fun header(name: String): String? = headers[name.lowercase()]

    override fun close() {
        try {
            body.close()
        } catch (_: IOException) {
        } finally {
            release()
        }
    }
}

/** [UpdateNetwork] over [HttpsURLConnection]: HTTPS only, no caches, and redirects are handed back, not followed. */
class HttpsUpdateNetwork(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : UpdateNetwork {
    override fun get(url: URL, headers: Map<String, String>): NetworkResponse {
        require(url.protocol == "https") { "HTTPS only" }
        val connection = url.openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        headers.forEach(connection::setRequestProperty)
        try {
            val code = connection.responseCode
            val body = (if (code >= 400) connection.errorStream else connection.inputStream) ?: ByteArrayInputStream(ByteArray(0))
            val fields = connection.headerFields.orEmpty().filterKeys { it != null }.mapValues { it.value.firstOrNull().orEmpty() }
            return NetworkResponse(code, fields, connection.contentLengthLong, body) { connection.disconnect() }
        } catch (e: IOException) {
            connection.disconnect()
            throw e
        }
    }
}

/**
 * GitHub, as the updater reaches it: through [network], to [TRUSTED_UPDATE_HOSTS] only, following redirects by hand so
 * each hop is checked. Never sends credentials (it has none). Every failure comes out as an [UpdateException].
 */
class GitHubAccess(
    private val network: UpdateNetwork,
    private val userAgent: String,
) {
    /** GETs [url] and gives back its 200 answer, to read and close. */
    fun open(url: String, accept: String): NetworkResponse {
        var target = trusted(url)
        repeat(MAX_REDIRECTS + 1) {
            val headers = mapOf(
                "Accept" to accept,
                "User-Agent" to userAgent,
                "X-GitHub-Api-Version" to "2022-11-28",
                // Exact lengths: a compressed answer has none, and the download is checked against its size.
                "Accept-Encoding" to "identity",
            )
            val response = try {
                network.get(target, headers)
            } catch (e: IOException) {
                throw UpdateException(problemOf(e), e)
            }
            when (response.code) {
                HttpURLConnection.HTTP_OK -> return response
                301, 302, 303, 307, 308 -> {
                    val location = response.header("Location")
                    response.close()
                    location ?: throw UpdateException(UpdateProblem.ServerError(response.code))
                    target = trusted(location, base = target)
                }
                else -> {
                    val problem = problemOf(response)
                    response.close()
                    throw UpdateException(problem)
                }
            }
        }
        throw UpdateException(UpdateProblem.Untrusted)
    }

    /** [url]'s body as text, refusing one longer than [limit] bytes. */
    fun text(url: String, accept: String, limit: Int): String = open(url, accept).use { response ->
        if (response.length > limit) throw UpdateException(UpdateProblem.BadRelease("answer too large"))
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        try {
            while (true) {
                val n = response.body.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > limit) throw UpdateException(UpdateProblem.BadRelease("answer too large"))
            }
        } catch (e: IOException) {
            throw UpdateException(problemOf(e), e)
        }
        out.toString(Charsets.UTF_8.name())
    }

    companion object {
        const val MAX_REDIRECTS = 5

        /** [url] (relative to [base], for a redirect) when it is HTTPS on a trusted host, on the default port, without credentials. */
        fun trusted(url: String, base: URL? = null): URL {
            val parsed = try {
                if (base != null) URL(base, url) else URL(url)
            } catch (e: MalformedURLException) {
                throw UpdateException(UpdateProblem.Untrusted, e)
            }
            val ok = parsed.protocol == "https" && parsed.host.lowercase() in TRUSTED_UPDATE_HOSTS &&
                (parsed.port == -1 || parsed.port == 443) && parsed.userInfo == null
            if (!ok) throw UpdateException(UpdateProblem.Untrusted)
            return parsed
        }

        fun problemOf(e: IOException): UpdateProblem = when (e) {
            is SocketTimeoutException -> UpdateProblem.TimedOut
            is SSLException -> UpdateProblem.Insecure
            // No connection, an unknown host, a refused or reset connection: GitHub couldn't be reached.
            else -> UpdateProblem.Offline
        }

        /** What a non-200, non-redirect answer means. GitHub says it is rate-limiting with a 403 or a 429. */
        fun problemOf(response: NetworkResponse): UpdateProblem {
            val remaining = response.header("X-RateLimit-Remaining")
            return when {
                response.code == 429 || (response.code == 403 && (remaining == "0" || response.header("Retry-After") != null)) ->
                    UpdateProblem.RateLimited(retryAt(response))
                response.code == HttpURLConnection.HTTP_NOT_FOUND -> UpdateProblem.NoRelease
                else -> UpdateProblem.ServerError(response.code)
            }
        }

        private fun retryAt(response: NetworkResponse): Instant? {
            response.header("X-RateLimit-Reset")?.trim()?.toLongOrNull()?.let { return Instant.ofEpochSecond(it) }
            return null
        }
    }
}
