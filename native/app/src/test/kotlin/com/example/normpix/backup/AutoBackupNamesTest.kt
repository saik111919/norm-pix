package com.example.normpix.backup

import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The names automatic backups get, and the only names ever deleted. */
class AutoBackupNamesTest {
    private val day = LocalDate.of(2026, 11, 14)

    @Test
    fun `a backup is named for its day, and a second one that day gets a number`() {
        assertEquals("NormPix-auto-backup-2026-11-14.zip.locked", AutoBackupNames.nameFor(day))
        assertEquals("NormPix-auto-backup-2026-11-14-2.zip.locked", AutoBackupNames.nameFor(day, 2))
        assertEquals("NormPix-auto-backup-2026-11-14.zip.locked", AutoBackupNames.freeName(day, listOf("other.zip")))
        assertEquals(
            "NormPix-auto-backup-2026-11-14-3.zip.locked",
            AutoBackupNames.freeName(day, listOf("NormPix-auto-backup-2026-11-14.zip.locked", "normpix-auto-backup-2026-11-14-2.ZIP.locked")),
        )
    }

    @Test
    fun `a day with every name taken has none left`() {
        val all = (1..999).map { AutoBackupNames.nameFor(day, it) }
        assertNull(AutoBackupNames.freeName(day, all))
    }

    @Test
    fun `only the exact automatic backup names count as one`() {
        for (name in listOf("NormPix-auto-backup-2026-11-14.zip.locked", "NormPix-auto-backup-2026-11-14-2.zip.locked", "NormPix-auto-backup-2026-11-14-999.zip.locked")) {
            assertTrue(AutoBackupNames.isAutoBackupName(name), name)
        }
        for (
        name in listOf(
            "normpix-app-backup-2026-11-14.zip.locked", // a manual backup
            "NormPix-auto-backup-2026-11-14.zip", // not locked
            "NormPix-auto-backup-2026-11-14 (1).zip.locked", // renamed by a file manager
            "NormPix-auto-backup-2026-11-14.zip.locked.txt",
            "Copy of NormPix-auto-backup-2026-11-14.zip.locked",
            "NormPix-auto-backup-2026-11-14-0.zip.locked",
            "NormPix-auto-backup-2026-11-14-1000.zip.locked",
            "NormPix-auto-backup-latest.zip.locked",
            "notes.txt",
        )
        ) {
            assertFalse(AutoBackupNames.isAutoBackupName(name), name)
        }
    }

    @Test
    fun `backups are ordered by their day, then their number`() {
        val a = AutoBackupNames.orderOf("NormPix-auto-backup-2026-11-14.zip.locked")!!
        val b = AutoBackupNames.orderOf("NormPix-auto-backup-2026-11-14-2.zip.locked")!!
        val c = AutoBackupNames.orderOf("NormPix-auto-backup-2026-12-01.zip.locked")!!
        assertEquals("2026-11-14" to 1, a)
        assertEquals("2026-11-14" to 2, b)
        assertTrue(c.first > b.first)
        assertNull(AutoBackupNames.orderOf("notes.txt"))
    }

    @Test
    fun `what went wrong is told from what was thrown`() {
        assertEquals(AutoBackupProblem.OutOfSpace, AutoBackupService.problemOf(IOException("write failed: ENOSPC (No space left on device)")))
        assertEquals(AutoBackupProblem.OutOfSpace, AutoBackupService.problemOf(IOException("wrapped", IOException("No space left on device"))))
        assertEquals(AutoBackupProblem.FolderGone, AutoBackupService.problemOf(SecurityException("Permission Denial")))
        assertEquals(AutoBackupProblem.FolderGone, AutoBackupService.problemOf(FileNotFoundException("gone")))
        assertEquals(AutoBackupProblem.FolderGone, AutoBackupService.problemOf(IllegalArgumentException("Invalid URI")))
        assertEquals(AutoBackupProblem.Failed, AutoBackupService.problemOf(IOException("Broken pipe")))
    }
}
