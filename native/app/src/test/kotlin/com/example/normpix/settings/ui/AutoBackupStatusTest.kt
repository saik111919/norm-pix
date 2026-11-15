package com.example.normpix.settings.ui

import com.example.normpix.backup.AutoBackupProblem
import com.example.normpix.screens.autoBackupProblemBanner
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.AutoBackupEvery
import com.example.normpix.ui.theme.StatusTone
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What Settings and the notifications say about the last automatic backup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupStatusTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val at = LocalDateTime.of(2026, 11, 14, 9, 30).atZone(zone).toInstant().toEpochMilli()
    private val day = 24L * 60 * 60 * 1000
    private val on = AppPrefsState(autoBackupEnabled = true, autoBackupFolder = "content://x/tree/y", autoBackupFolderName = "Backups")

    @BeforeTest
    fun setUp() = Locale.setDefault(Locale.US)

    @Test
    fun `none yet, then the last one's time and name`() {
        assertEquals(AutoBackupStatus("Last automatic backup", "None yet", problem = false), autoBackupStatus(on, zone))
        val made = on.copy(autoBackupLastAt = at, autoBackupLastFile = "NormPix-auto-backup-2026-11-14.zip.locked")
        assertEquals(
            AutoBackupStatus("Last automatic backup", "14 Nov 2026, 09:30 · NormPix-auto-backup-2026-11-14.zip.locked", problem = false),
            autoBackupStatus(made, zone),
        )
    }

    @Test
    fun `a problem says what happened, when, and what to do`() {
        val status = autoBackupStatus(on.copy(autoBackupLastAt = at - day, autoBackupProblem = AutoBackupProblem.FolderGone, autoBackupProblemAt = at), zone)
        assertTrue(status.problem)
        assertEquals("Choose the folder again", status.headline)
        assertEquals("14 Nov 2026, 09:30: NormPix can't write to the backup folder any more. Tap Folder to choose it again.", status.supporting)
        for (problem in AutoBackupProblem.entries) {
            assertTrue(problem.fix.isNotBlank(), problem.name)
            assertTrue(problem.short.length <= 30, "${problem.name}'s banner line is short")
        }
    }

    @Test
    fun `cards left out is said beside the backup that was made`() {
        val status = autoBackupStatus(
            on.copy(autoBackupLastAt = at, autoBackupLastFile = "f.zip.locked", autoBackupProblem = AutoBackupProblem.CardsLeftOut, autoBackupProblemAt = at),
            zone,
        )
        assertEquals("Last automatic backup", status.headline)
        assertTrue(status.problem)
        assertTrue(status.supporting.endsWith("Saved cards were left out: they couldn't be read."))
    }

    @Test
    fun `the notification is there only while automatic backups are on and something went wrong`() {
        var opened = 0
        assertNull(autoBackupProblemBanner(on, at) { opened++ })
        assertNull(autoBackupProblemBanner(AppPrefsState(autoBackupProblem = AutoBackupProblem.FolderGone, autoBackupProblemAt = at), at) { opened++ }, "off says nothing")

        val gone = on.copy(autoBackupLastAt = at - 5 * day, autoBackupEvery = AutoBackupEvery.Daily, autoBackupProblem = AutoBackupProblem.FolderGone, autoBackupProblemAt = at)
        val banner = assertNotNull(autoBackupProblemBanner(gone, at) { opened++ })
        assertEquals("Automatic backup", banner.eyebrow)
        assertEquals("Choose the folder again", banner.title)
        assertEquals("Can't write to the folder", banner.message)
        assertEquals(StatusTone.Warning, banner.tone)
        assertEquals("5 days ago", banner.figure!!.figure)
        assertEquals("Daily", banner.figure!!.note)
        banner.primary.onClick()
        banner.onOpen()
        assertEquals(2, opened)

        // A new problem is a new notification (seen once each), and cards left out is only information.
        val later = assertNotNull(autoBackupProblemBanner(gone.copy(autoBackupProblemAt = at + day), at) {})
        assertNotEquals(banner.key, later.key)
        val cards = assertNotNull(autoBackupProblemBanner(on.copy(autoBackupProblem = AutoBackupProblem.CardsLeftOut, autoBackupProblemAt = at), at) {})
        assertEquals(StatusTone.Info, cards.tone)
        assertEquals("None yet", cards.figure!!.figure)
        assertFalse(cards.key == banner.key)
    }
}
