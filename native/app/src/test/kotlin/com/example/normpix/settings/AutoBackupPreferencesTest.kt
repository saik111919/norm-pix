package com.example.normpix.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.backup.AutoBackupProblem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The automatic backup's preferences: what is kept and read back, that a backup neither carries nor changes any of them (they are
 * about this phone: its folder, the password sealed here), and that resetting settings keeps them all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var raw: SharedPreferences
    private lateinit var prefs: AppPreferences
    private val tree = "content://com.android.externalstorage.documents/tree/primary%3ABackups"

    private val keys = listOf(
        "auto_backup_enabled",
        "auto_backup_folder",
        "auto_backup_folder_name",
        "auto_backup_every",
        "auto_backup_cards",
        "auto_backup_last_at",
        "auto_backup_last_file",
        "auto_backup_problem",
        "auto_backup_problem_at",
    )

    @BeforeTest
    fun setUp() {
        raw = context.getSharedPreferences("auto_backup_prefs", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        prefs = AppPreferences(raw)
    }

    private fun onWithEverything() {
        prefs.turnOnAutoBackup(tree, "Backups")
        prefs.setAutoBackupEvery(AutoBackupEvery.Monthly)
        prefs.setAutoBackupCards(true)
        prefs.recordAutoBackup(1_000L, "NormPix-auto-backup-2026-11-14.zip.locked", AutoBackupProblem.CardsLeftOut)
        prefs.recordAutoBackupProblem(AutoBackupProblem.FolderGone, 2_000L)
    }

    @Test
    fun `off, weekly and without cards to begin with`() {
        val state = prefs.state.value
        assertFalse(state.autoBackupEnabled)
        assertEquals("", state.autoBackupFolder)
        assertEquals(AutoBackupEvery.Weekly, state.autoBackupEvery)
        assertFalse(state.autoBackupCards)
        assertNull(state.autoBackupProblem)
    }

    @Test
    fun `every choice is kept and read back`() {
        onWithEverything()
        val read = AppPreferences(raw).state.value
        assertTrue(read.autoBackupEnabled)
        assertEquals(tree, read.autoBackupFolder)
        assertEquals("Backups", read.autoBackupFolderName)
        assertEquals(AutoBackupEvery.Monthly, read.autoBackupEvery)
        assertTrue(read.autoBackupCards)
        assertEquals(1_000L, read.autoBackupLastAt)
        assertEquals("NormPix-auto-backup-2026-11-14.zip.locked", read.autoBackupLastFile)
        assertEquals(AutoBackupProblem.FolderGone, read.autoBackupProblem)
        assertEquals(2_000L, read.autoBackupProblemAt)
        assertEquals(1_000L, read.lastBackupAt, "an automatic backup counts as a backup")
    }

    @Test
    fun `a problem name the app doesn't know reads as none`() {
        raw.edit().putString("auto_backup_problem", "SomethingNew").commit()
        assertNull(AppPreferences(raw).state.value.autoBackupProblem)
    }

    @Test
    fun `a backup neither carries them nor changes them`() {
        onWithEverything()
        val exported = prefs.exportAll()
        for (key in keys) assertFalse(key in exported, "$key is not in a backup")

        // A file that names them (a hand-edited one, say) can't turn automatic backups on, off, or point them anywhere.
        val other = context.getSharedPreferences("auto_backup_prefs_other", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        val elsewhere = AppPreferences(other)
        elsewhere.importAll(
            mapOf(
                "auto_backup_enabled" to PrefValue.BooleanPref(true),
                "auto_backup_folder" to PrefValue.StringPref("content://evil/tree/x"),
                "auto_backup_cards" to PrefValue.BooleanPref(true),
            ),
        )
        assertFalse(elsewhere.state.value.autoBackupEnabled)
        assertEquals("", elsewhere.state.value.autoBackupFolder)
        assertFalse(elsewhere.state.value.autoBackupCards)

        // And restoring a backup here leaves this phone's as they are.
        prefs.importAll(emptyMap())
        assertTrue(prefs.state.value.autoBackupEnabled)
        assertEquals(tree, prefs.state.value.autoBackupFolder)
    }

    @Test
    fun `resetting settings keeps all of it, so the sealed password never loses its switch`() {
        onWithEverything()
        prefs.setMode(ThemeMode.Dark)
        prefs.resetToDefaults()
        for (state in listOf(prefs.state.value, AppPreferences(raw).state.value)) {
            assertEquals(ThemeMode.System, state.mode)
            assertTrue(state.autoBackupEnabled)
            assertEquals(tree, state.autoBackupFolder)
            assertEquals("Backups", state.autoBackupFolderName)
            assertEquals(AutoBackupEvery.Monthly, state.autoBackupEvery)
            assertTrue(state.autoBackupCards)
            assertEquals(1_000L, state.autoBackupLastAt)
            assertEquals(AutoBackupProblem.FolderGone, state.autoBackupProblem)
        }
    }

    @Test
    fun `turning off forgets the switch, the folder, the cards and the problem`() {
        onWithEverything()
        prefs.turnOffAutoBackup()
        for (state in listOf(prefs.state.value, AppPreferences(raw).state.value)) {
            assertFalse(state.autoBackupEnabled)
            assertEquals("", state.autoBackupFolder)
            assertEquals("", state.autoBackupFolderName)
            assertFalse(state.autoBackupCards)
            assertNull(state.autoBackupProblem)
        }
    }

    @Test
    fun `a backup that goes well ends the problem, and a new folder ends a folder problem only`() {
        onWithEverything()
        prefs.setAutoBackupFolder("content://x/tree/new", "New")
        assertNull(prefs.state.value.autoBackupProblem)
        prefs.recordAutoBackupProblem(AutoBackupProblem.OutOfSpace, 3_000L)
        prefs.setAutoBackupFolder("content://x/tree/newer", "Newer")
        assertEquals(AutoBackupProblem.OutOfSpace, prefs.state.value.autoBackupProblem)
        prefs.recordAutoBackup(4_000L, "NormPix-auto-backup-2026-11-15.zip.locked")
        assertNull(AppPreferences(raw).state.value.autoBackupProblem)
    }
}
