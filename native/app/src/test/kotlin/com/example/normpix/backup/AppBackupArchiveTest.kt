package com.example.normpix.backup

import com.example.normpix.finance.data.FinanceBackupArchive
import com.example.normpix.settings.CustomFont
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The zip of an app backup: the finance backup as it always was, and beside it the settings, reminders, profile photo, font and
 * locked cards. Reading it takes only what is named and shaped as the app makes it, and refuses a file that could write elsewhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppBackupArchiveTest {
    private val dir: File = Files.createTempDirectory("app-archive-test").toFile()
    private var reads = 0

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private val pictureName = "pic-0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0.jpg"
    private val photoName = "photo-1760000000000.jpg"
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
    private val font = byteArrayOf(0, 1, 0, 0, 9, 9, 9, 9)
    private val manifestText = AppBackupManifest("1.4.0", "2026-10-19T09:30:00", listOf("finance", "settings")).encode()

    private fun file(name: String, bytes: ByteArray): File = File(dir, "source/$name").also {
        it.parentFile.mkdirs()
        it.writeBytes(bytes)
    }

    private fun partsOf(
        settings: String? = """{"theme_mode":{"type":"string","value":"Dark"}}""",
        reminders: String? = """{"reminders":[]}""",
        photos: List<File> = listOf(file(photoName, jpeg)),
        fonts: List<File> = listOf(file(CustomFont.FILE_NAME, font)),
        cards: String? = """{"format":"normpix-cards"}""",
    ) = AppBackupArchive.Parts(AppBackupManifest("1.4.0", "2026-10-19T09:30:00", listOf("finance")), settings, reminders, photos, fonts, cards)

    private fun write(app: AppBackupArchive.Parts = partsOf()): ByteArray {
        val kept = file("kept/$pictureName", jpeg).parentFile
        val out = ByteArrayOutputStream()
        AppBackupArchive.write(out, """{"format":"normpix"}""", listOf(pictureName), app) { File(kept, it).takeIf(File::isFile) }
        return out.toByteArray()
    }

    private fun readBack(bytes: ByteArray): AppBackupArchive.Contents =
        AppBackupArchive.read(ByteArrayInputStream(bytes), File(dir, "read-${reads++}"))

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun zipWithBig(name: String, head: ByteArray, size: Long): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(FinanceBackupArchive.BACKUP_ENTRY))
            zip.write("{}".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifestText.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(name))
            zip.write(head)
            writeZeros(zip, size - head.size)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun writeZeros(out: OutputStream, count: Long) {
        val chunk = ByteArray(64 * 1024)
        var left = count
        while (left > 0) {
            val n = minOf(left, chunk.size.toLong()).toInt()
            out.write(chunk, 0, n)
            left -= n
        }
    }

    private fun entryNames(bytes: ByteArray): List<String> = buildList {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) add((zip.nextEntry ?: break).name)
        }
    }

    @Test
    fun `an app backup brings back the finance part and everything beside it`() {
        val bytes = write()
        assertTrue(FinanceBackupArchive.isArchive(bytes.copyOf(4)))
        assertEquals(
            listOf(
                "normpix-backup.json",
                "pictures/$pictureName",
                "app/manifest.json",
                "app/settings.json",
                "app/reminders.json",
                "app/profile/$photoName",
                "app/fonts/custom-interface-font.ttf",
                "app/cards.json",
            ),
            entryNames(bytes),
        )

        val read = readBack(bytes)
        assertEquals("""{"format":"normpix"}""", read.finance.json)
        assertEquals(setOf(pictureName), read.finance.pictures.keys)
        val app = assertNotNull(read.app)
        assertEquals("1.4.0", app.manifest.appVersion)
        assertEquals("2026-10-19T09:30:00", app.manifest.exportedAt)
        assertEquals(listOf("finance"), app.manifest.contents)
        assertEquals("""{"theme_mode":{"type":"string","value":"Dark"}}""", app.settings)
        assertEquals("""{"reminders":[]}""", app.reminders)
        assertEquals("""{"format":"normpix-cards"}""", app.cards)
        assertContentEquals(jpeg, app.profilePhotos.getValue(photoName).readBytes())
        assertContentEquals(font, app.fonts.getValue(CustomFont.FILE_NAME).readBytes())
    }

    @Test
    fun `parts that are not there are not written, and not read`() {
        val bytes = write(partsOf(settings = null, reminders = null, photos = emptyList(), fonts = emptyList(), cards = null))
        assertEquals(listOf("normpix-backup.json", "pictures/$pictureName", "app/manifest.json"), entryNames(bytes))
        val app = assertNotNull(readBack(bytes).app)
        assertNull(app.settings)
        assertNull(app.reminders)
        assertNull(app.cards)
        assertTrue(app.profilePhotos.isEmpty())
        assertTrue(app.fonts.isEmpty())
    }

    @Test
    fun `a NormPix from before app backups still reads the finance part, and ignores the rest`() {
        val read = FinanceBackupArchive.read(ByteArrayInputStream(write()), File(dir, "old-reader"))
        assertEquals("""{"format":"normpix"}""", read.json)
        assertEquals(setOf(pictureName), read.pictures.keys)
    }

    @Test
    fun `a full backup from before app backups has no app part`() {
        val out = ByteArrayOutputStream()
        FinanceBackupArchive.write(out, """{"format":"normpix"}""", listOf(pictureName)) { file("kept/$it", jpeg) }
        val read = readBack(out.toByteArray())
        assertEquals("""{"format":"normpix"}""", read.finance.json)
        assertEquals(setOf(pictureName), read.finance.pictures.keys)
        assertNull(read.app)
    }

    @Test
    fun `an entry whose name is not a plain relative path makes the whole file unusable`() {
        val unsafe = listOf(
            "../evil.txt",
            "app/../../evil.txt",
            "/etc/evil",
            "app/profile/../../../evil.jpg",
            "C:/evil.txt",
            "app\\evil.txt",
            "a\u0000b",
        )
        for (name in unsafe) {
            val bytes = zipOf(FinanceBackupArchive.BACKUP_ENTRY to "{}".toByteArray(), name to "x".toByteArray(), MANIFEST to manifestText.toByteArray())
            val failure = assertFailsWith<IOException>(name) { readBack(bytes) }
            assertEquals(AppBackupArchive.UNSAFE, failure.message, name)
        }
        assertFalse(File(dir.parentFile, "evil.txt").exists())
    }

    @Test
    fun `a name that tries to leave the pictures folder is just not a picture`() {
        val bytes = zipOf(
            FinanceBackupArchive.BACKUP_ENTRY to "{}".toByteArray(),
            "pictures/../../escape.jpg" to jpeg,
            MANIFEST to manifestText.toByteArray(),
        )
        val read = readBack(bytes)
        assertTrue(read.finance.pictures.isEmpty())
        assertFalse(File(dir, "escape.jpg").exists())
        assertFalse(File(dir.parentFile, "escape.jpg").exists())
    }

    @Test
    fun `which names are unsafe`() {
        for (name in listOf("", "/a", "a/../b", "..", "a\\b", "c:x", "a\u0000")) assertTrue(AppBackupArchive.isUnsafeName(name), name)
        for (name in listOf("app/settings.json", "app/profile/photo-1.jpg", "a..b/c", "notes.txt")) assertFalse(AppBackupArchive.isUnsafeName(name), name)
    }

    @Test
    fun `an entry over its size limit makes the file unusable`() {
        val cases = listOf(
            AppBackupArchive.SETTINGS_ENTRY to AppBackupArchive.MAX_SETTINGS_BYTES,
            AppBackupArchive.REMINDERS_ENTRY to AppBackupArchive.MAX_REMINDERS_BYTES,
            AppBackupArchive.CARDS_ENTRY to AppBackupArchive.MAX_CARDS_BYTES,
            AppBackupArchive.FONTS_PREFIX + CustomFont.FILE_NAME to CustomFont.MAX_BYTES,
        )
        for ((name, limit) in cases) {
            val head = if (name.startsWith(AppBackupArchive.FONTS_PREFIX)) font.copyOf(4) else ByteArray(0)
            val failure = assertFailsWith<IOException>(name) { readBack(zipWithBig(name, head, limit + 1)) }
            assertEquals(FinanceBackupArchive.TOO_LARGE, failure.message, name)
        }
    }

    @Test
    fun `an entry exactly at its limit is read`() {
        val bytes = zipWithBig(AppBackupArchive.SETTINGS_ENTRY, ByteArray(0), AppBackupArchive.MAX_SETTINGS_BYTES)
        assertEquals(AppBackupArchive.MAX_SETTINGS_BYTES, assertNotNull(readBack(bytes).app).settings?.length?.toLong())
    }

    @Test
    fun `a manifest from a newer NormPix is refused, so nothing it holds is silently dropped`() {
        val newer = """{"format":"normpix-app-backup","version":${AppBackupManifest.VERSION + 1},"appVersion":"9.0","contents":[]}"""
        val bytes = zipOf(FinanceBackupArchive.BACKUP_ENTRY to "{}".toByteArray(), MANIFEST to newer.toByteArray())
        val failure = assertFailsWith<IOException> { readBack(bytes) }
        assertEquals(AppBackupArchive.NEWER, failure.message)
    }

    @Test
    fun `a zip with some other manifest is a full backup and no more`() {
        for (manifest in listOf("""{"format":"something-else","version":1}""", "not json", "[]", """{"format":"normpix-app-backup"}""")) {
            val bytes = zipOf(
                FinanceBackupArchive.BACKUP_ENTRY to "{}".toByteArray(),
                MANIFEST to manifest.toByteArray(),
                AppBackupArchive.SETTINGS_ENTRY to "{}".toByteArray(),
            )
            val read = readBack(bytes)
            assertEquals("{}", read.finance.json)
            assertNull(read.app, manifest)
        }
    }

    @Test
    fun `a photo or a font is taken only as the app makes them`() {
        val bytes = zipOf(
            FinanceBackupArchive.BACKUP_ENTRY to "{}".toByteArray(),
            MANIFEST to manifestText.toByteArray(),
            "app/profile/$photoName" to "not a jpeg".toByteArray(),
            "app/profile/photo-x.jpg" to jpeg,
            "app/profile/sub/photo-2.jpg" to jpeg,
            "app/profile/photo-3.jpg" to jpeg,
            "app/fonts/${CustomFont.FILE_NAME}" to "not a font".toByteArray(),
            "app/fonts/other.ttf" to font,
            "app/fonts/../fonts/${CustomFont.FILE_NAME}" to font,
        )
        // The last one has a ".." in it, which is refused outright.
        assertFailsWith<IOException> { readBack(bytes) }

        val withoutTraversal = zipOf(
            FinanceBackupArchive.BACKUP_ENTRY to "{}".toByteArray(),
            MANIFEST to manifestText.toByteArray(),
            "app/profile/$photoName" to "not a jpeg".toByteArray(),
            "app/profile/photo-x.jpg" to jpeg,
            "app/profile/sub/photo-2.jpg" to jpeg,
            "app/profile/photo-3.jpg" to jpeg,
            "app/fonts/${CustomFont.FILE_NAME}" to "not a font".toByteArray(),
            "app/fonts/other.ttf" to font,
        )
        val app = assertNotNull(readBack(withoutTraversal).app)
        assertEquals(setOf("photo-3.jpg"), app.profilePhotos.keys)
        assertTrue(app.fonts.isEmpty())
    }

    @Test
    fun `the font header the app accepts is the one a backup accepts`() {
        for (head in listOf(byteArrayOf(0, 1, 0, 0), "OTTO".toByteArray(), "true".toByteArray())) {
            val bytes = zipOf(
                FinanceBackupArchive.BACKUP_ENTRY to "{}".toByteArray(),
                MANIFEST to manifestText.toByteArray(),
                "app/fonts/${CustomFont.FILE_NAME}" to head + ByteArray(16),
            )
            assertEquals(setOf(CustomFont.FILE_NAME), assertNotNull(readBack(bytes).app).fonts.keys)
        }
        val collection = zipOf(
            FinanceBackupArchive.BACKUP_ENTRY to "{}".toByteArray(),
            MANIFEST to manifestText.toByteArray(),
            "app/fonts/${CustomFont.FILE_NAME}" to "ttcf".toByteArray() + ByteArray(16),
        )
        assertTrue(assertNotNull(readBack(collection).app).fonts.isEmpty(), "the app can't draw with a collection, so a backup doesn't carry one")
    }

    @Test
    fun `no more photos than the limit are taken from one backup`() {
        val entries = buildList {
            add(FinanceBackupArchive.BACKUP_ENTRY to "{}".toByteArray())
            add(MANIFEST to manifestText.toByteArray())
            for (i in 1..AppBackupArchive.MAX_FILES + 3) add("app/profile/photo-$i.jpg" to jpeg)
        }
        val app = assertNotNull(readBack(zipOf(*entries.toTypedArray())).app)
        assertEquals(AppBackupArchive.MAX_FILES, app.profilePhotos.size)
    }

    @Test
    fun `the app's parts are written with a photo and a font only under the names the app uses`() {
        val bytes = write(partsOf(photos = listOf(file("not-a-photo.jpg", jpeg)), fonts = listOf(file("evil.ttf", font))))
        assertEquals(listOf("normpix-backup.json", "pictures/$pictureName", "app/manifest.json", "app/settings.json", "app/reminders.json", "app/cards.json"), entryNames(bytes))
    }

    private companion object {
        const val MANIFEST = "app/manifest.json"
    }
}
