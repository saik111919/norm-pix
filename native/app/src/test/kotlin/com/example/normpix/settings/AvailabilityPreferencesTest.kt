package com.example.normpix.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.availability.AvailabilityMode
import com.example.normpix.availability.AvailabilityRule
import com.example.normpix.availability.ScheduledFeature
import com.example.normpix.availability.WeeklySchedule
import com.example.normpix.settings.ui.SettingsCatalog
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AvailabilityPreferencesTest {
    private fun store() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("availability_prefs_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    private val feature = ScheduledFeature.NfcExchange

    @Test
    fun newUsersGetTheNfcExchangeOffAndSwipingEnabled() {
        val state = AppPreferences(store()).state.value
        assertEquals(AvailabilityRule(mode = AvailabilityMode.Off), state.availabilityOf(feature))
        assertEquals(AvailabilityMode.Off, state.availabilityOf(feature).mode)
        assertTrue(state.swipeActionsEnabled)
        assertEquals(TransactionSwipeActions.EditAndDelete, state.transactionSwipeActions)
    }

    @Test
    fun aRuleSurvivesRestartsExactly() {
        val store = store()
        val rule = AvailabilityRule(
            AvailabilityMode.Scheduled,
            WeeklySchedule(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), LocalTime.of(22, 15), LocalTime.of(6, 45)),
        )
        AppPreferences(store).setAvailability(feature, rule)

        assertEquals(rule, AppPreferences(store).state.value.availabilityOf(feature))
    }

    @Test
    fun theEditIsVisibleToObserversImmediately() {
        val preferences = AppPreferences(store())
        preferences.setAvailability(feature, AvailabilityRule(AvailabilityMode.Always))
        assertEquals(AvailabilityMode.Always, preferences.state.value.availabilityOf(feature).mode)
    }

    @Test
    fun aChoiceToTurnItOnSurvivesRestarts() {
        val store = store()
        AppPreferences(store).setAvailability(feature, AvailabilityRule(AvailabilityMode.Always))
        assertEquals(AvailabilityMode.Always, AppPreferences(store).state.value.availabilityOf(feature).mode)
    }

    @Test
    fun swipeChoicesPersist() {
        val store = store()
        AppPreferences(store).apply {
            setSwipeActionsEnabled(false)
            setTransactionSwipeActions(TransactionSwipeActions.DeleteOnly)
        }
        val reread = AppPreferences(store).state.value
        assertEquals(false, reread.swipeActionsEnabled)
        assertEquals(TransactionSwipeActions.DeleteOnly, reread.transactionSwipeActions)
    }

    @Test
    fun resetBringsBackTheNfcExchangeOffAndSwiping() {
        val store = store()
        val preferences = AppPreferences(store)
        preferences.setAvailability(feature, AvailabilityRule(AvailabilityMode.Always))
        preferences.setSwipeActionsEnabled(false)

        preferences.resetToDefaults()

        assertEquals(AvailabilityMode.Off, AppPreferences(store).state.value.availabilityOf(feature).mode)
        assertTrue(AppPreferences(store).state.value.swipeActionsEnabled)
    }

    @Test
    fun damagedStoredValuesFallBackInsteadOfCrashing() {
        val store = store()
        store.edit()
            .putString("availability_nfcexchange_mode", "Scheduled")
            .putString("availability_nfcexchange_days", "0,9,x,2")
            .putInt("availability_nfcexchange_start", 99_999)
            .putInt("availability_nfcexchange_end", -5)
            .commit()

        val rule = AppPreferences(store).state.value.availabilityOf(feature)

        assertEquals(AvailabilityMode.Scheduled, rule.mode)
        assertEquals(setOf(DayOfWeek.TUESDAY), rule.schedule.days, "only valid ISO days are kept")
        assertEquals(LocalTime.of(23, 59), rule.schedule.start)
        assertEquals(LocalTime.of(0, 0), rule.schedule.end)
    }

    @Test
    fun theSettingsSearchFindsTheNewSettings() {
        assertTrue(SettingsCatalog.search("nfc").any { it.title == "NFC card exchange schedule" })
        assertTrue(SettingsCatalog.search("schedule").any { it.page != null && it.page.name == "Availability" })
        assertTrue(SettingsCatalog.search("swipe").any { it.title == "Swipe actions" })
    }
}
