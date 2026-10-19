package com.example.normpix.backup

import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.ui.SettingsCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Settings search finds the app backup by the words people use for it, and the older backup entries are still found. */
class AppBackupCatalogTest {
    private fun titles(query: String, cardsEnabled: Boolean = true) = SettingsCatalog.search(query, cardsEnabled).map { it.title }

    @Test
    fun `the backup of everything is found by the words for it`() {
        for (query in listOf("app backup", "everything", "new phone", "backup settings", "export settings", "backup profile", "backup font", "zip")) {
            assertTrue("Backup of everything" in titles(query), query)
        }
    }

    @Test
    fun `restoring settings is found by the words for it`() {
        for (query in listOf("restore settings", "import settings", "restore app backup", "restore everything", "restore profile", "restore font")) {
            assertTrue("Restore settings" in titles(query), query)
        }
    }

    @Test
    fun `the reminders backup is found by the words for it`() {
        for (query in listOf("reminders backup", "backup reminders", "export reminders", "restore reminders", "import reminders")) {
            assertTrue("Reminders backup" in titles(query), query)
        }
    }

    @Test
    fun `all three are on Data and backup, and are not about cards, so they stay when cards are off`() {
        for (title in listOf("Backup of everything", "Restore settings", "Reminders backup")) {
            val entry = SettingsCatalog.entries.single { it.title == title }
            assertEquals(SettingsPage.Data, entry.page, title)
            assertFalse(entry.cardsOnly, title)
            assertTrue(title in titles(entry.keywords.first(), cardsEnabled = false), title)
        }
    }

    @Test
    fun `the older backup entries are found as before`() {
        assertTrue("Export backup" in titles("backup"))
        assertTrue("Import backup" in titles("restore"))
        // The words of the new entries are not the words of sample data, which has one place to be found.
        for (query in listOf("dummy", "sample data", "demo")) assertEquals(1, titles(query).size, query)
    }
}
