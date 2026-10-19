package com.example.normpix.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What an app backup says about itself, and how a NormPix reads another one's. */
class AppBackupManifestTest {
    @Test
    fun `the manifest names the format, its version, the app, the time and the parts`() {
        val manifest = AppBackupManifest("1.4.0", "2026-10-19T09:30:00", listOf("finance", "settings", "cards"))
        val root = Json.parseToJsonElement(manifest.encode()).jsonObject
        assertEquals("normpix-app-backup", root.getValue("format").jsonPrimitive.content)
        assertEquals("1", root.getValue("version").jsonPrimitive.content)
        assertEquals("1.4.0", root.getValue("appVersion").jsonPrimitive.content)
        assertEquals("2026-10-19T09:30:00", root.getValue("exportedAt").jsonPrimitive.content)
        assertEquals(listOf("finance", "settings", "cards"), root.getValue("contents").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `a manifest is read back as it was written`() {
        val written = AppBackupManifest("1.4.0", "2026-10-19T09:30:00", listOf("finance", "reminders"))
        val read = assertIs<AppBackupManifest.Read.Valid>(AppBackupManifest.decode(written.encode())).manifest
        assertEquals("1.4.0", read.appVersion)
        assertEquals("2026-10-19T09:30:00", read.exportedAt)
        assertEquals(listOf("finance", "reminders"), read.contents)
        assertEquals(AppBackupManifest.VERSION, read.version)
    }

    @Test
    fun `a manifest that leaves out the time or the parts is still a manifest`() {
        val read = assertIs<AppBackupManifest.Read.Valid>(
            AppBackupManifest.decode("""{"format":"normpix-app-backup","version":1}"""),
        ).manifest
        assertNull(read.exportedAt)
        assertEquals("", read.appVersion)
        assertEquals(emptyList(), read.contents)
    }

    @Test
    fun `another format is not an app backup, and a newer version is told apart`() {
        assertEquals(AppBackupManifest.Read.Invalid, AppBackupManifest.decode("""{"format":"normpix-cards","version":1}"""))
        assertEquals(AppBackupManifest.Read.Invalid, AppBackupManifest.decode("""{"format":"normpix-app-backup","version":"1"}"""))
        assertEquals(AppBackupManifest.Read.Invalid, AppBackupManifest.decode("nonsense"))
        assertEquals(AppBackupManifest.Read.Newer, AppBackupManifest.decode("""{"format":"normpix-app-backup","version":2}"""))
    }

    @Test
    fun `every part has its own name in the manifest`() {
        assertEquals(BackupPart.entries.size, BackupPart.entries.map { it.key }.toSet().size)
        assertEquals(listOf("finance", "settings", "reminders", "profile", "font", "cards"), BackupPart.entries.map { it.key })
    }
}
