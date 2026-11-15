package com.example.normpix.settings.ui

import com.example.normpix.about.GuideTopic
import com.example.normpix.navigation.SettingsPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The bank statement import is found by the words people use for it, in Settings search and in the About guide. */
class StatementImportCatalogTest {
    private fun titles(query: String, cardsEnabled: Boolean = true) = SettingsCatalog.search(query, cardsEnabled).map { it.title }

    @Test
    fun `Settings search finds it by the words for it`() {
        for (query in listOf("import", "csv", "bank statement", "statement", "excel", "spreadsheet", "upload transactions", "import transactions", "passbook")) {
            assertTrue("Import a bank statement" in titles(query), query)
        }
    }

    @Test
    fun `it is on Data and backup, not about cards, so it stays when cards are off`() {
        val entry = SettingsCatalog.entries.single { it.title == "Import a bank statement" }
        assertEquals(SettingsPage.Data, entry.page)
        assertFalse(entry.cardsOnly)
        assertTrue("Import a bank statement" in titles("csv", cardsEnabled = false))
    }

    @Test
    fun `restoring a backup is still found as before`() {
        assertTrue("Import backup" in titles("restore"))
    }

    @Test
    fun `the guide's backup topic says how to import a statement`() {
        val tip = GuideTopic.Backup.tips.single { "Import a bank statement" in it }
        for (part in listOf("CSV", "bank's website or app", "review every transaction", "left unticked", "again safely", "all the ticked ones are added together, or none are")) {
            assertTrue(part in tip, part)
        }
    }
}
