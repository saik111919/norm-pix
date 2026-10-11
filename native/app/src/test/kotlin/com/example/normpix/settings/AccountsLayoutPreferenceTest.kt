package com.example.normpix.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.ui.SettingsCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How the Wallet lays out the accounts: stacked cards until the user says otherwise, remembered, and found by Settings search. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountsLayoutPreferenceTest {
    private fun store() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("accounts_layout_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun isStackedCardsUntilTheUserPicksAnother_andEachChoiceIsRemembered() {
        val store = store()
        assertEquals(AccountsLayout.Stack, AppPreferences(store).state.value.accountsLayout)

        for (layout in AccountsLayout.entries) {
            AppPreferences(store).setAccountsLayout(layout)
            assertEquals(layout, AppPreferences(store).state.value.accountsLayout, "$layout is read back by the next launch")
        }
    }

    @Test
    fun anUnknownStoredValueFallsBackToTheDefault() {
        val store = store()
        store.edit().putString("accounts_layout", "Carousel").commit()
        assertEquals(AccountsLayout.Stack, AppPreferences(store).state.value.accountsLayout)
    }

    @Test
    fun resettingSettingsBringsTheDefaultBack() {
        val preferences = AppPreferences(store())
        preferences.setAccountsLayout(AccountsLayout.Rows)
        preferences.resetToDefaults()
        assertEquals(AccountsLayout.Stack, preferences.state.value.accountsLayout)
    }

    @Test
    fun changingItTouchesNothingElse() {
        val preferences = AppPreferences(store())
        val before = preferences.state.value
        preferences.setAccountsLayout(AccountsLayout.Cards)
        assertEquals(before.copy(accountsLayout = AccountsLayout.Cards), preferences.state.value)
    }

    @Test
    fun settingsSearchFindsItInWalletAndCards() {
        for (query in listOf("accounts layout", "stacked", "rows", "compact")) {
            val hit = SettingsCatalog.search(query).single { it.title == "Accounts layout" }
            assertEquals(SettingsPage.Wallet, hit.group, query)
        }
        assertTrue(SettingsCatalog.search("dark").none { it.title == "Accounts layout" })
    }

    @Test
    fun everyLayoutIsDescribed() {
        for (layout in AccountsLayout.entries) assertTrue(layout.label.isNotBlank() && layout.description.isNotBlank(), layout.name)
    }

    @Test
    fun voltIsAnAccentTheUserCanKeep() {
        val store = store()
        AppPreferences(store).setAccent(AccentColor.Volt)
        val reread = AppPreferences(store).state.value
        assertEquals(AccentColor.Volt, reread.accent)
        assertEquals(ColorSource.Custom, reread.colorSource, "picking an accent switches to curated colours")
    }
}
