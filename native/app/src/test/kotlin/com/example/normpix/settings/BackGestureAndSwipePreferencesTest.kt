package com.example.normpix.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.ui.SettingsCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The settings of 2026-11-11: what the back gesture shows and how long its close takes (Settings > Appearance > Transitions), and
 * whether a swipe to delete asks first (Settings > Finance). Each has a default, is kept, travels in a backup, falls back to its
 * default for a value no version knows, and is found by Settings search.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackGestureAndSwipePreferencesTest {
    private fun store() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("back_gesture_and_swipe_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun theDefaults_followThePages_atAnUnhurriedSpeed_andASwipeAsks() {
        val prefs = AppPreferences(store())
        assertEquals(BackGestureStyle.PageStyle, prefs.state.value.backGestureStyle)
        assertEquals(BackGestureSpeed.Standard, prefs.state.value.backGestureSpeed)
        assertTrue(prefs.state.value.swipeDeleteAsks, "a swipe to delete asks first unless it is turned off")
        assertEquals(listOf("Match pages", "Shrink", "Slide", "Fade"), BackGestureStyle.entries.map { it.label })
        assertEquals(listOf("Relaxed", "Standard", "Brisk"), BackGestureSpeed.entries.map { it.label })
    }

    @Test
    fun theChoicesAreKeptTravelInABackupAndComeBack() {
        val context = store()
        val prefs = AppPreferences(context)
        prefs.setBackGestureStyle(BackGestureStyle.Slide)
        prefs.setBackGestureSpeed(BackGestureSpeed.Relaxed)
        prefs.setSwipeDeleteAsks(false)
        assertTrue(prefs.exportAll()["back_gesture_style"].toString().contains("Slide"), "back_gesture_style travels in a backup")
        assertTrue(prefs.exportAll()["back_gesture_speed"].toString().contains("Relaxed"), "back_gesture_speed travels in a backup")
        assertTrue("swipe_delete_asks" in prefs.exportAll().keys, "swipe_delete_asks travels in a backup")

        val again = AppPreferences(context)
        assertEquals(BackGestureStyle.Slide, again.state.value.backGestureStyle)
        assertEquals(BackGestureSpeed.Relaxed, again.state.value.backGestureSpeed)
        assertEquals(false, again.state.value.swipeDeleteAsks)
    }

    @Test
    fun aValueNoVersionOfTheAppKnowsFallsBackToTheDefault() {
        val context = store()
        context.edit().putString("back_gesture_style", "Explode").putString("back_gesture_speed", "Ludicrous").commit()
        val prefs = AppPreferences(context)
        assertEquals(BackGestureStyle.PageStyle, prefs.state.value.backGestureStyle)
        assertEquals(BackGestureSpeed.Standard, prefs.state.value.backGestureSpeed)
    }

    @Test
    fun settingsSearchFindsThem() {
        for (query in listOf("back gesture", "predictive", "swipe back")) {
            val hit = SettingsCatalog.search(query).firstOrNull { it.title == "Back gesture" }
            assertTrue(hit != null, "'$query' finds the back gesture")
            assertEquals(SettingsPage.Appearance, hit.page)
            assertEquals(AppearanceSection.Transitions, hit.appearance)
        }
        for (query in listOf("back gesture speed", "timing")) {
            val hit = SettingsCatalog.search(query).firstOrNull { it.title == "Back gesture speed" }
            assertTrue(hit != null, "'$query' finds the back gesture's speed")
            assertEquals(AppearanceSection.Transitions, hit.appearance)
        }
        for (query in listOf("confirm", "ask before", "swipe delete")) {
            val hit = SettingsCatalog.search(query).firstOrNull { it.title == "Ask before a swipe deletes" }
            assertTrue(hit != null, "'$query' finds the swipe question")
            assertEquals(SettingsPage.Finance, hit.page)
        }
    }
}
