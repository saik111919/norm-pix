package com.example.normpix.updates

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A network that answers what a test told it to, records what was asked, and answers anything else with a 404. */
class FakeNetwork : UpdateNetwork {
    val asked = mutableListOf<String>()
    val headersSent = mutableListOf<Map<String, String>>()
    private val answers = mutableMapOf<String, () -> NetworkResponse>()

    fun answer(url: String, code: Int = 200, body: ByteArray = ByteArray(0), headers: Map<String, String> = emptyMap(), length: Long = body.size.toLong()) {
        answers[url] = { NetworkResponse(code, headers, length, ByteArrayInputStream(body)) }
    }

    fun answer(url: String, body: String) = answer(url, body = body.toByteArray())

    fun stream(url: String, length: Long, body: () -> InputStream) {
        answers[url] = { NetworkResponse(200, emptyMap(), length, body()) }
    }

    fun redirect(url: String, to: String, code: Int = 302) = answer(url, code = code, headers = mapOf("Location" to to))

    fun fail(url: String, error: IOException) {
        answers[url] = { throw error }
    }

    override fun get(url: URL, headers: Map<String, String>): NetworkResponse {
        asked += url.toString()
        headersSent += headers
        return answers[url.toString()]?.invoke() ?: NetworkResponse(404, emptyMap(), 0, ByteArrayInputStream(ByteArray(0)))
    }
}

/** A body that hands out [bytes] and then fails, as a connection dropped half way does. */
class DroppingStream(private val bytes: ByteArray) : InputStream() {
    private var at = 0

    override fun read(): Int = if (at < bytes.size) bytes[at++].toInt() and 0xFF else throw IOException("connection reset")

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (at >= bytes.size) throw IOException("connection reset")
        val n = minOf(len, bytes.size - at)
        System.arraycopy(bytes, at, b, off, n)
        at += n
        return n
    }
}

/** A release of NormPix as CI publishes it: the GitHub API's description, its update.json, and the APK. */
object Releases {
    const val REPO = "owner/normpix"
    const val APP_ID = "com.example.normpix"
    const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    val installed = InstalledApp(APP_ID, versionCode = 10002, versionName = "1.0.2", sdk = 34)

    fun apk(size: Int = 300_000): ByteArray = ByteArray(size) { (it * 31 % 251).toByte() }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun apkName(versionName: String) = "NormPix-$versionName.apk"

    fun download(tag: String, file: String) = "https://github.com/$REPO/releases/download/$tag/$file"

    /** What `GET /releases/latest` says of a release tagged [tag] with [assets] (name to size). */
    fun api(
        tag: String,
        assets: List<Pair<String, Long>>,
        body: String = "Notes from the release page",
        draft: Boolean = false,
        prerelease: Boolean = false,
        publishedAt: String = "2026-10-15T09:30:00Z",
    ): String = buildJsonObject {
        put("tag_name", tag)
        put("name", "NormPix $tag")
        put("body", body)
        put("draft", draft)
        put("prerelease", prerelease)
        put("published_at", publishedAt)
        put("html_url", "https://github.com/$REPO/releases/tag/$tag")
        put(
            "assets",
            buildJsonArray {
                for ((name, size) in assets) {
                    add(
                        buildJsonObject {
                            put("name", name)
                            put("size", size)
                            put("state", "uploaded")
                            put("browser_download_url", download(tag, name))
                        },
                    )
                }
            },
        )
    }.toString()

    /** An update.json for [versionName] / [versionCode] describing [apk]; [changes] replaces or (with null) drops fields. */
    fun metadata(
        versionName: String,
        versionCode: Long,
        apk: ByteArray,
        changes: Map<String, JsonElement?> = emptyMap(),
    ): String {
        val fields = linkedMapOf<String, JsonElement>(
            "schema" to JsonPrimitive(1),
            "applicationId" to JsonPrimitive(APP_ID),
            "versionCode" to JsonPrimitive(versionCode),
            "versionName" to JsonPrimitive(versionName),
            "tag" to JsonPrimitive("v$versionName"),
            "apk" to JsonPrimitive(apkName(versionName)),
            "size" to JsonPrimitive(apk.size),
            "sha256" to JsonPrimitive(sha256(apk)),
            "minSdk" to JsonPrimitive(26),
            "publishedAt" to JsonPrimitive("2026-10-15T09:00:00Z"),
            "mandatory" to JsonPrimitive(false),
            "notes" to JsonPrimitive("- Faster receipts\n- Fixes"),
        )
        for ((key, value) in changes) if (value == null) fields.remove(key) else fields[key] = value
        return JsonObject(fields).toString()
    }

    /**
     * Serves a whole release on [network], as GitHub does: the API's description, update.json and the APK, the two files
     * by way of a redirect to GitHub's download host. Gives back the APK's bytes.
     */
    fun publish(
        network: FakeNetwork,
        versionName: String = "1.0.3",
        versionCode: Long = 10003,
        apk: ByteArray = apk(),
        changes: Map<String, JsonElement?> = emptyMap(),
    ): ByteArray {
        val tag = "v$versionName"
        val metadata = metadata(versionName, versionCode, apk, changes)
        network.answer(LATEST, api(tag, listOf(ReleaseMetadata.FILE to metadata.length.toLong(), apkName(versionName) to apk.size.toLong())))
        serveFile(network, tag, ReleaseMetadata.FILE, metadata.toByteArray())
        serveFile(network, tag, apkName(versionName), apk)
        return apk
    }

    fun serveFile(network: FakeNetwork, tag: String, file: String, bytes: ByteArray) {
        val stored = "https://release-assets.githubusercontent.com/github-production-release-asset/1/$tag-$file?sig=abc"
        network.redirect(download(tag, file), stored)
        network.answer(stored, body = bytes)
    }
}
