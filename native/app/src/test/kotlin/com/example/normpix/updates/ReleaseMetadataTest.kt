package com.example.normpix.updates

import com.example.normpix.updates.Releases.apkName
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** A release and its update.json are taken only when every part agrees; anything else is a release the app won't offer. */
class ReleaseMetadataTest {
    private val apk = Releases.apk(1_000)

    private fun release(tag: String = "v1.0.3", files: List<Pair<String, Long>> = listOf("update.json" to 400L, apkName("1.0.3") to 1_000L), body: String = "Release page notes") =
        ReleaseMetadata.release(Releases.api(tag, files, body = body))

    private fun read(changes: Map<String, JsonElement?> = emptyMap(), release: ReleaseMetadata.Release = release()) =
        ReleaseMetadata.metadata(Releases.metadata("1.0.3", 10003, apk, changes), release, Releases.REPO)

    private fun rejected(changes: Map<String, JsonElement?> = emptyMap(), release: ReleaseMetadata.Release = release()): UpdateProblem.BadRelease =
        assertIs(assertFailsWith<UpdateException> { read(changes, release) }.problem)

    @Test
    fun `a release that agrees with its update json`() {
        val metadata = read()
        assertEquals(Releases.APP_ID, metadata.applicationId)
        assertEquals(26, metadata.minSdk)
        val update = metadata.update
        assertEquals(10003L, update.versionCode)
        assertEquals("1.0.3", update.versionName)
        assertEquals("v1.0.3", update.tag)
        assertEquals("NormPix-1.0.3.apk", update.apkName)
        // Built from the repository the app was made for, never taken from what a server said.
        assertEquals("https://github.com/owner/normpix/releases/download/v1.0.3/NormPix-1.0.3.apk", update.apkUrl)
        assertEquals(1_000L, update.size)
        assertEquals(Releases.sha256(apk), update.sha256)
        assertEquals(Instant.parse("2026-10-15T09:00:00Z"), update.publishedAt)
        assertEquals("- Faster receipts\n- Fixes", update.notes)
        assertEquals(false, update.mandatory)
    }

    @Test
    fun `notes and date fall back to the release page, and a release can be marked mandatory`() {
        val update = read(mapOf("notes" to null, "publishedAt" to null, "mandatory" to JsonPrimitive(true))).update
        assertEquals("Release page notes", update.notes)
        assertEquals(Instant.parse("2026-10-15T09:30:00Z"), update.publishedAt)
        assertTrue(update.mandatory)
        // Optional fields may be missing altogether.
        assertEquals(1, read(mapOf("minSdk" to null)).minSdk)
        assertEquals(false, read(mapOf("mandatory" to null)).update.mandatory)
    }

    @Test
    fun `malformed answers are refused, not guessed at`() {
        for (json in listOf("", "not json", "[1, 2]", "{\"tag_name\": 3}", "{\"tag_name\": \"v1.0.3\"}", "{\"tag_name\": \"v1.0.3\", \"assets\": {}}")) {
            assertIs<UpdateProblem.BadRelease>(assertFailsWith<UpdateException>(json) { ReleaseMetadata.release(json) }.problem, json)
        }
        for (json in listOf("", "{", "\"text\"", "{\"schema\": 1}")) {
            assertIs<UpdateProblem.BadRelease>(assertFailsWith<UpdateException>(json) { ReleaseMetadata.metadata(json, release(), Releases.REPO) }.problem, json)
        }
    }

    @Test
    fun `a tag that isn't a version, or names another one, is refused`() {
        rejected(release = release(tag = "nightly"), changes = mapOf("tag" to JsonPrimitive("nightly")))
        rejected(release = release(tag = "v1.0"), changes = mapOf("tag" to JsonPrimitive("v1.0")))
        rejected(release = release(tag = "v1.0.3-beta"), changes = mapOf("tag" to JsonPrimitive("v1.0.3-beta")))
        // update.json copied from another release.
        rejected(mapOf("tag" to JsonPrimitive("v1.0.2")))
        // A version name the tag doesn't carry.
        rejected(mapOf("versionName" to JsonPrimitive("1.0.4")))
    }

    @Test
    fun `the APK has to be attached, at the size and with the checksum described`() {
        rejected(release = release(files = listOf("update.json" to 400L)))
        rejected(release = release(files = listOf("update.json" to 400L, apkName("1.0.3") to 999L)))
        rejected(mapOf("sha256" to JsonPrimitive("abc")))
        rejected(mapOf("sha256" to JsonPrimitive("z".repeat(64))))
        rejected(mapOf("sha256" to null))
        // A name that could step out of the download folder, or isn't an APK.
        rejected(mapOf("apk" to JsonPrimitive("../NormPix-1.0.3.apk")))
        rejected(mapOf("apk" to JsonPrimitive("NormPix-1.0.3.zip")))
        rejected(mapOf("size" to JsonPrimitive(0)))
        // A checksum in capitals is still the checksum.
        assertEquals(Releases.sha256(apk), read(mapOf("sha256" to JsonPrimitive(Releases.sha256(apk).uppercase()))).update.sha256)
    }

    @Test
    fun `fields of the wrong kind, or a schema this version doesn't know, are refused`() {
        rejected(mapOf("schema" to JsonPrimitive(2)))
        rejected(mapOf("schema" to null))
        rejected(mapOf("versionCode" to JsonPrimitive("10003")))
        rejected(mapOf("versionCode" to JsonPrimitive(-1)))
        rejected(mapOf("versionCode" to JsonNull))
        rejected(mapOf("applicationId" to JsonPrimitive("")))
        rejected(mapOf("minSdk" to JsonPrimitive("twenty")))
        rejected(mapOf("mandatory" to JsonPrimitive("yes")))
    }

    // ------------------------------------------------------- one APK per CPU type

    private val arm64 = Releases.apk(600)
    private val arm32 = Releases.apk(500)

    private fun perCpu(vararg entries: Pair<String, JsonElement>): Map<String, JsonElement?> =
        mapOf("apks" to kotlinx.serialization.json.JsonObject(mapOf(*entries)))

    private fun cpuEntry(name: String, bytes: ByteArray, size: Int = bytes.size): JsonElement = kotlinx.serialization.json.buildJsonObject {
        put("apk", JsonPrimitive(name))
        put("size", JsonPrimitive(size))
        put("sha256", JsonPrimitive(Releases.sha256(bytes)))
    }

    private val splitFiles = listOf(
        "update.json" to 400L,
        apkName("1.0.3") to 1_000L,
        "NormPix-1.0.3-arm64-v8a.apk" to 600L,
        "NormPix-1.0.3-armeabi-v7a.apk" to 500L,
    )

    private fun readFor(abis: List<String>, changes: Map<String, JsonElement?> = splitChanges, files: List<Pair<String, Long>> = splitFiles) =
        ReleaseMetadata.metadata(Releases.metadata("1.0.3", 10003, apk, changes), release(files = files), Releases.REPO, abis)

    private val splitChanges = perCpu(
        "arm64-v8a" to cpuEntry("NormPix-1.0.3-arm64-v8a.apk", arm64),
        "armeabi-v7a" to cpuEntry("NormPix-1.0.3-armeabi-v7a.apk", arm32),
    )

    @Test
    fun `a phone takes the APK made for its own CPU type`() {
        val update = readFor(listOf("arm64-v8a", "armeabi-v7a", "armeabi")).update
        assertEquals("NormPix-1.0.3-arm64-v8a.apk", update.apkName)
        assertEquals("https://github.com/owner/normpix/releases/download/v1.0.3/NormPix-1.0.3-arm64-v8a.apk", update.apkUrl)
        assertEquals(600L, update.size)
        assertEquals(Releases.sha256(arm64), update.sha256)
        // Its first choice may be one the release has no APK for: the next one is taken.
        assertEquals("NormPix-1.0.3-armeabi-v7a.apk", readFor(listOf("x86_64", "armeabi-v7a")).update.apkName)
    }

    @Test
    fun `a phone with no APK of its own, or an app that doesn't ask, takes the universal one`() {
        assertEquals(apkName("1.0.3"), readFor(listOf("x86_64", "x86")).update.apkName)
        assertEquals(apkName("1.0.3"), readFor(emptyList()).update.apkName)
        assertEquals(1_000L, readFor(emptyList()).update.size)
        // A release made before CPU types were listed has only the universal APK.
        assertEquals(apkName("1.0.3"), ReleaseMetadata.metadata(Releases.metadata("1.0.3", 10003, apk), release(), Releases.REPO, listOf("arm64-v8a")).update.apkName)
    }

    @Test
    fun `an APK for a CPU type is held to the same checks as the universal one`() {
        // Not attached, another size than attached, a name that isn't a file name, no checksum: the release is refused, not
        // quietly replaced by the universal APK.
        val bad = listOf(
            perCpu("arm64-v8a" to cpuEntry("NormPix-1.0.3-arm64-v8a.apk", arm64)) to splitFiles.filter { it.first != "NormPix-1.0.3-arm64-v8a.apk" },
            perCpu("arm64-v8a" to cpuEntry("NormPix-1.0.3-arm64-v8a.apk", arm64, size = 601)) to splitFiles,
            perCpu("arm64-v8a" to cpuEntry("../NormPix-1.0.3-arm64-v8a.apk", arm64)) to splitFiles,
            perCpu("arm64-v8a" to kotlinx.serialization.json.buildJsonObject { put("apk", JsonPrimitive("NormPix-1.0.3-arm64-v8a.apk")); put("size", JsonPrimitive(600)) }) to splitFiles,
        )
        for ((changes, files) in bad) assertFailsWith<UpdateException> { readFor(listOf("arm64-v8a"), changes, files) }
        // An entry for a CPU type this phone doesn't have is not looked at.
        assertEquals(apkName("1.0.3"), readFor(listOf("armeabi-v7a"), bad[0].first, splitFiles).update.apkName)
    }

    @Test
    fun `files still uploading are not there yet`() {
        val json = Releases.api("v1.0.3", listOf("update.json" to 400L)).replace("\"uploaded\"", "\"starter\"")
        assertTrue(ReleaseMetadata.release(json).assets.isEmpty())
    }
}
