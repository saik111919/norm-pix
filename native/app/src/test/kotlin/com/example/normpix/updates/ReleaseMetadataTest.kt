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

    @Test
    fun `files still uploading are not there yet`() {
        val json = Releases.api("v1.0.3", listOf("update.json" to 400L)).replace("\"uploaded\"", "\"starter\"")
        assertTrue(ReleaseMetadata.release(json).assets.isEmpty())
    }
}
