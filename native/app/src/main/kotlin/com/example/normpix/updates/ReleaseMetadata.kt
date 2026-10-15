package com.example.normpix.updates

import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Reads a GitHub release and the `update.json` CI publishes with it (`.github/workflows/android.yml`), and accepts them
 * only when every part agrees: the tag names the version, the APK named is attached at the size given, the checksum is a
 * SHA-256. Anything else is a [UpdateProblem.BadRelease]: an update the app can't be sure of is never offered.
 *
 * `update.json`, schema 1:
 * ```
 * { "schema": 1, "applicationId": "com.example.normpix", "versionCode": 10003, "versionName": "1.0.3", "tag": "v1.0.3",
 *   "apk": "NormPix-1.0.3.apk", "size": 12345678, "sha256": "<64 hex>", "minSdk": 26,
 *   "publishedAt": "2026-10-15T10:00:00Z", "mandatory": false, "notes": "What changed" }
 * ```
 */
object ReleaseMetadata {
    const val FILE = "update.json"
    const val SCHEMA = 1L
    const val MAX_APK_BYTES = 200L * 1024 * 1024
    private const val MAX_NOTES = 8_000
    private val TAG = Regex("""v(\d+\.\d+\.\d+)""")
    private val APK_NAME = Regex("""[A-Za-z0-9._-]+\.apk""")
    private val SHA256 = Regex("""[0-9a-f]{64}""")

    /** A GitHub release, as `GET /repos/{repo}/releases/latest` describes it. [assets] maps each uploaded file's name to its size. */
    class Release(
        val tag: String,
        val notes: String,
        val publishedAt: Instant?,
        val draft: Boolean,
        val prerelease: Boolean,
        val assets: Map<String, Long>,
    )

    /** An `update.json` that agrees with its [release]: the update, and who it is for. */
    class Metadata(val applicationId: String, val minSdk: Int, val update: UpdateRelease)

    fun release(json: String): Release {
        val root = objectOf(json)
        val tag = root.string("tag_name") ?: bad("release without a tag")
        val assets = (root["assets"] as? JsonArray ?: bad("release without a list of files")).mapNotNull { element ->
            val asset = element as? JsonObject ?: return@mapNotNull null
            // A file still uploading ("starter", "open") isn't there yet.
            if (asset.string("state") != null && asset.string("state") != "uploaded") return@mapNotNull null
            val name = asset.string("name") ?: return@mapNotNull null
            name to (asset.long("size") ?: return@mapNotNull null)
        }.toMap()
        return Release(
            tag = tag,
            notes = root.string("body").orEmpty(),
            publishedAt = root.string("published_at")?.let(::instantOrNull),
            draft = root.boolean("draft") ?: false,
            prerelease = root.boolean("prerelease") ?: false,
            assets = assets,
        )
    }

    /** [json] (the release's `update.json`) as an update from [repository], when it agrees with [release]. */
    fun metadata(json: String, release: Release, repository: String): Metadata {
        val root = objectOf(json)
        val schema = root.long("schema") ?: bad("update.json without a schema")
        if (schema != SCHEMA) bad("update.json schema $schema is not one this version reads")
        val applicationId = root.string("applicationId")?.takeIf { it.isNotBlank() } ?: bad("no applicationId")
        val versionCode = root.long("versionCode")?.takeIf { it in 1..Int.MAX_VALUE } ?: bad("no valid versionCode")
        val versionName = root.string("versionName") ?: bad("no versionName")
        val tag = root.string("tag") ?: bad("no tag")
        val tagged = TAG.matchEntire(release.tag)?.groupValues?.get(1) ?: bad("release tag ${release.tag} is not vMAJOR.MINOR.PATCH")
        if (tag != release.tag) bad("update.json is for $tag, published with ${release.tag}")
        if (versionName != tagged) bad("versionName $versionName under tag ${release.tag}")
        val apk = root.string("apk")?.takeIf { APK_NAME.matches(it) } ?: bad("no valid apk name")
        val size = root.long("size")?.takeIf { it in 1..MAX_APK_BYTES } ?: bad("no valid size")
        val attached = release.assets[apk] ?: bad("$apk is not attached to ${release.tag}")
        if (attached != size) bad("$apk is $attached bytes, update.json says $size")
        val sha256 = root.string("sha256")?.lowercase()?.takeIf { SHA256.matches(it) } ?: bad("no valid sha256")
        val minSdk = root["minSdk"]?.let { (it as? JsonPrimitive)?.longOrNull?.toInt() ?: bad("minSdk is not a number") } ?: 1
        val mandatory = root["mandatory"]?.let { (it as? JsonPrimitive)?.booleanOrNull ?: bad("mandatory is not true or false") } ?: false
        val notes = (root.string("notes")?.takeIf { it.isNotBlank() } ?: release.notes).trim().take(MAX_NOTES)
        val update = UpdateRelease(
            versionCode = versionCode,
            versionName = versionName,
            tag = tag,
            publishedAt = root.string("publishedAt")?.let(::instantOrNull) ?: release.publishedAt,
            notes = notes,
            apkName = apk,
            apkUrl = downloadUrl(repository, tag, apk),
            size = size,
            sha256 = sha256,
            mandatory = mandatory,
        )
        return Metadata(applicationId, minSdk, update)
    }

    /** Where GitHub serves a file attached to a release (it redirects from there to its download host). */
    fun downloadUrl(repository: String, tag: String, file: String) = "https://github.com/$repository/releases/download/$tag/$file"

    private fun objectOf(json: String): JsonObject = try {
        Json.parseToJsonElement(json) as? JsonObject ?: bad("not a JSON object")
    } catch (e: SerializationException) {
        throw UpdateException(UpdateProblem.BadRelease("malformed JSON"), e)
    } catch (e: IllegalArgumentException) {
        throw UpdateException(UpdateProblem.BadRelease("malformed JSON"), e)
    }

    private fun bad(detail: String): Nothing = throw UpdateException(UpdateProblem.BadRelease(detail))

    private fun instantOrNull(text: String): Instant? = try {
        Instant.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
}

/** What the latest release means for the installed app. */
sealed interface ReleaseCheck {
    data class Newer(val release: UpdateRelease) : ReleaseCheck

    /** The latest release is the installed version, or older. */
    data class Current(val latest: UpdateRelease) : ReleaseCheck
}

/**
 * Asks GitHub for the latest release of [repository] and whether it is an update for [installed]. Only the release GitHub
 * calls latest is considered: never a draft or a prerelease (test builds from main). Blocking: call it off the main thread.
 */
class UpdateChecker(
    private val github: GitHubAccess,
    private val repository: String,
    private val installed: InstalledApp,
) {
    init {
        require(REPOSITORY.matches(repository)) { "Not a GitHub repository: $repository" }
    }

    fun latest(): ReleaseCheck {
        val json = github.text("https://api.github.com/repos/$repository/releases/latest", "application/vnd.github+json", MAX_RELEASE_BYTES)
        val release = ReleaseMetadata.release(json)
        if (release.draft || release.prerelease) throw UpdateException(UpdateProblem.BadRelease("${release.tag} is not a published release"))
        if (ReleaseMetadata.FILE !in release.assets) throw UpdateException(UpdateProblem.BadRelease("${release.tag} has no ${ReleaseMetadata.FILE}"))
        val metadataUrl = ReleaseMetadata.downloadUrl(repository, release.tag, ReleaseMetadata.FILE)
        val metadata = ReleaseMetadata.metadata(github.text(metadataUrl, "application/octet-stream", MAX_METADATA_BYTES), release, repository)
        if (metadata.applicationId != installed.applicationId) throw UpdateException(UpdateProblem.OtherApp(metadata.applicationId))
        val update = metadata.update
        // By number: 1.0.10 (10010) is newer than 1.0.9 (10009), which comparing the names as text would get wrong.
        if (update.versionCode <= installed.versionCode) return ReleaseCheck.Current(update)
        if (metadata.minSdk > installed.sdk) throw UpdateException(UpdateProblem.NeedsAndroid(metadata.minSdk))
        return ReleaseCheck.Newer(update)
    }

    private companion object {
        const val MAX_RELEASE_BYTES = 1024 * 1024
        const val MAX_METADATA_BYTES = 64 * 1024
        val REPOSITORY = Regex("""[A-Za-z0-9-]+/[A-Za-z0-9._-]+""")
    }
}
