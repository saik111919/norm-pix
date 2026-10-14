package com.example.normpix.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.ui.SettingsCatalog
import com.example.normpix.settings.ui.SettingsSpecial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class SettingsCatalogTest {
    @Test
    fun findsByTitleKeywordAndLocation() {
        assertEquals(listOf("Theme"), SettingsCatalog.search("dark").map { it.title })
        assertTrue(SettingsCatalog.search("BACKUP").any { it.title == "Export backup" })
        // Location matches: every Security entry shows up for "security".
        val found = SettingsCatalog.search("security").map { it.title }
        val inSecurity = SettingsCatalog.entries.filter { it.group == SettingsPage.Security }.map { it.title }
        assertTrue(found.containsAll(inSecurity), "every Security entry shows up: $found")
    }

    @Test
    fun blankQueriesMatchNothing() {
        assertTrue(SettingsCatalog.search("   ").isEmpty())
    }

    @Test
    fun specialEntriesKeepTheirActions() {
        assertEquals(SettingsSpecial.Categories, SettingsCatalog.search("categories").single { it.title == "Categories" }.special)
        assertEquals(SettingsSpecial.Reset, SettingsCatalog.search("defaults").single().special)
    }

    @Test
    fun sampleDataLivesUnderDataAndBackup() {
        for (query in listOf("dummy", "sample data", "demo")) {
            assertEquals(SettingsPage.Data, SettingsCatalog.search(query).single().page, query)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppPreferencesTest {
    private fun prefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("prefs_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun valuesPersistAcrossInstances() {
        val store = prefs()
        AppPreferences(store).apply {
            setMode(ThemeMode.Dark)
            setAccent(AccentColor.Purple)
            setWalletLockEnabled(true)
            setAppLockTimeout(AppLockTimeout.Minute5)
            setDummyDataEnabled(true)
            setSheetBlurEnabled(false)
        }
        val reread = AppPreferences(store).state.value
        assertEquals(ThemeMode.Dark, reread.mode)
        assertEquals(AccentColor.Purple, reread.accent)
        assertEquals(ColorSource.Custom, reread.colorSource, "picking an accent switches to custom colors")
        assertTrue(reread.walletLockEnabled)
        assertEquals(AppLockTimeout.Minute5, reread.appLockTimeout)
        assertTrue(reread.dummyDataEnabled)
        assertEquals(false, reread.sheetBlurEnabled)
    }

    @Test
    fun dummyDataIsOffUntilTurnedOn() {
        val store = prefs()
        val preferences = AppPreferences(store)
        assertEquals(false, preferences.state.value.dummyDataEnabled)
        preferences.setDummyDataEnabled(true)
        assertTrue(preferences.state.value.dummyDataEnabled)
        preferences.setDummyDataEnabled(false)
        assertEquals(false, AppPreferences(store).state.value.dummyDataEnabled)
    }

    @Test
    fun sheetBlurCanBeToggled() {
        val store = prefs()
        val preferences = AppPreferences(store)
        assertTrue(preferences.state.value.sheetBlurEnabled)
        preferences.setSheetBlurEnabled(false)
        assertEquals(false, preferences.state.value.sheetBlurEnabled)
        assertEquals(false, AppPreferences(store).state.value.sheetBlurEnabled)
    }

    @Test
    fun backButtonStyleCanBeToggled() {
        val store = prefs()
        val preferences = AppPreferences(store)
        assertEquals(BackButtonStyle.BottomDock, preferences.state.value.backButtonStyle)
        preferences.setBackButtonStyle(BackButtonStyle.TopFloatingCircle)
        assertEquals(BackButtonStyle.TopFloatingCircle, preferences.state.value.backButtonStyle)
        assertEquals(BackButtonStyle.TopFloatingCircle, AppPreferences(store).state.value.backButtonStyle)
    }

    @Test
    fun resetRestoresDefaultsButRemembersTheOneTimePrompt() {
        val store = prefs()
        val preferences = AppPreferences(store)
        preferences.setMode(ThemeMode.Light)
        preferences.exactAlarmPrompted = true
        preferences.resetToDefaults()
        assertEquals(AppPrefsState(), preferences.state.value)
        assertEquals(AppPrefsState(), AppPreferences(store).state.value)
        assertTrue(AppPreferences(store).exactAlarmPrompted)
    }

    @Test
    fun unknownStoredValuesFallBackToDefaults() {
        val store = prefs()
        store.edit().putString("theme_mode", "Sepia").commit()
        assertEquals(ThemeMode.System, AppPreferences(store).state.value.mode)
    }
}
