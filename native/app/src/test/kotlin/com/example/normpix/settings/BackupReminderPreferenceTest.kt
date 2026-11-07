package com.example.normpix.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the app remembers for the backup reminder: when the last real backup was made (a time, never the file), how often to ask, and a
 * "Later". The first and last are about this phone, so a backup file never carries them and "Reset settings" keeps them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupReminderPreferenceTest {
    private fun store() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("backup_reminder_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun startsAsNeverBackedUp_askingEveryTwoWeeks_andIsRemembered() {
        val store = store()
        val first = AppPreferences(store).state.value
        assertEquals(0L, first.lastBackupAt)
        assertEquals(14, first.backupReminderDays)
        assertEquals(0L, first.backupNudgeSnoozedUntil)

        AppPreferences(store).recordBackup(1_700_000_000_000L)
        AppPreferences(store).setBackupReminderDays(30)
        AppPreferences(store).snoozeBackupNudge(1_700_000_500_000L)
        val back = AppPreferences(store).state.value
        assertEquals(1_700_000_000_000L, back.lastBackupAt)
        assertEquals(30, back.backupReminderDays)
        // A backup made after the "Later" ends it: the nudge starts counting afresh.
        assertEquals(1_700_000_500_000L, back.backupNudgeSnoozedUntil)
    }

    @Test
    fun aBackupEndsALater() {
        val preferences = AppPreferences(store())
        preferences.snoozeBackupNudge(1_700_000_500_000L)
        preferences.recordBackup(1_700_000_000_000L)
        assertEquals(0L, preferences.state.value.backupNudgeSnoozedUntil)
    }

    @Test
    fun aBackupOfTheSampleSetIsNotABackup() {
        val preferences = AppPreferences(store())
        preferences.setDummyDataEnabled(true)
        preferences.recordBackupSaved(1_700_000_000_000L)
        assertEquals(0L, preferences.state.value.lastBackupAt)

        preferences.setDummyDataEnabled(false)
        preferences.recordBackupSaved(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, preferences.state.value.lastBackupAt)
    }

    @Test
    fun onlyTheChoicesTheAppOffersAreKept() {
        val store = store()
        val preferences = AppPreferences(store)
        preferences.setBackupReminderDays(0)
        assertEquals(0, preferences.state.value.backupReminderDays)
        preferences.setBackupReminderDays(7)
        assertEquals(7, preferences.state.value.backupReminderDays)
        preferences.setBackupReminderDays(5)
        assertEquals(14, preferences.state.value.backupReminderDays, "not a choice: the default")
        store.edit().putInt("backup_reminder_days", 365).commit()
        assertEquals(14, AppPreferences(store).state.value.backupReminderDays, "an edited file")
    }

    @Test
    fun resettingSettingsKeepsWhenTheLastBackupWas_butBringsBackTheDefaultReminder() {
        val preferences = AppPreferences(store())
        preferences.recordBackup(1_700_000_000_000L)
        preferences.snoozeBackupNudge(1_700_000_500_000L)
        preferences.setBackupReminderDays(30)

        preferences.resetToDefaults()
        assertEquals(1_700_000_000_000L, preferences.state.value.lastBackupAt, "forgetting it would say never backed up")
        assertEquals(14, preferences.state.value.backupReminderDays, "the choice is a setting: back to the default")
    }

    @Test
    fun aBackupFileCarriesTheReminderChoice_butNotWhenThisPhoneWasBackedUp() {
        val preferences = AppPreferences(store())
        preferences.recordBackup(1_700_000_000_000L)
        preferences.snoozeBackupNudge(1_700_000_500_000L)
        preferences.setBackupReminderDays(30)

        val carried = preferences.exportAll().keys
        assertTrue("backup_reminder_days" in carried)
        assertFalse("last_backup_at" in carried, "another phone's file must not say this one was backed up")
        assertFalse("backup_nudge_snoozed_until" in carried)
    }
}
