package com.example.normpix.finance.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A full backup carries its pictures, and reading one takes nothing but the backup and pictures named as the app names them. */
class FinanceBackupArchiveTest {
    private val dir: File = Files.createTempDirectory("archive-test").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private val name = "pic-0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0.jpg"
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)

    @Test
    fun `a full backup brings back its json and its pictures`() {
        val kept = File(dir, "kept").apply { mkdirs() }
        File(kept, name).writeBytes(jpeg)
        val out = ByteArrayOutputStream()
        val written = FinanceBackupArchive.write(out, """{"format":"normpix"}""", listOf(name, name, "pic-missing.jpg")) { File(kept, it).takeIf(File::isFile) }
        assertEquals(1, written, "the picture that is there, once; a missing one is left out")
        assertTrue(FinanceBackupArchive.isArchive(out.toByteArray().copyOf(4)))

        val read = FinanceBackupArchive.read(ByteArrayInputStream(out.toByteArray()), File(dir, "read"))
        assertEquals("""{"format":"normpix"}""", read.json)
        assertEquals(setOf(name), read.pictures.keys)
        assertContentEquals(jpeg, read.pictures.getValue(name).readBytes())
    }

    @Test
    fun `only pictures named as the app names them, and only JPEGs, are taken`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(path: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
            entry(FinanceBackupArchive.BACKUP_ENTRY, "{}".toByteArray())
            entry("pictures/../../escape.jpg", jpeg)
            entry("pictures/notes.txt", "hello".toByteArray())
            entry("pictures/$name", "not a jpeg".toByteArray())
        }
        val into = File(dir, "read")
        val read = FinanceBackupArchive.read(ByteArrayInputStream(out.toByteArray()), into)
        assertEquals("{}", read.json)
        assertTrue(read.pictures.isEmpty())
        assertFalse(File(dir, "escape.jpg").exists())
        assertTrue(into.listFiles().orEmpty().isEmpty(), "nothing left behind: ${into.listFiles()?.map { it.name }}")
    }

    @Test
    fun `a json backup is not taken for a full one`() {
        assertFalse(FinanceBackupArchive.isArchive("""{"fo""".toByteArray()))
    }
}
