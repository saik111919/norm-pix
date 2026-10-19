package com.example.normpix.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.ui.SettingsCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How sheets and decisions open and close is a setting of its own, apart from the page style: found by Settings search, kept,
 * reset - and, for someone who never picked one, what their sheets showed before there was a setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SheetAnimationPreferenceTest {
    private fun store() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("sheet_animation_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun hasFourStylesAndTheDescriptionsDoNotClaimToMovePages() {
        assertEquals(
            listOf(SheetAnimationType.Window, SheetAnimationType.Slide, SheetAnimationType.Fade, SheetAnimationType.Scale),
            SheetAnimationType.entries.toList(),
        )
        assertEquals(listOf("Window", "Slide", "Fade", "Scale"), SheetAnimationType.entries.map { it.label })
        assertTrue(SheetAnimationType.entries.none { it.description.contains("page", ignoreCase = true) }, "sheets only")
    }

    @Test
    fun anInstallThatNeverPickedOneKeepsWhatItsSheetsShowedBefore() {
        assertEquals(SheetAnimationType.Window, SheetAnimationType.defaultFor(PageAnimationType.LinuxWindow))
        for (pages in PageAnimationType.entries - PageAnimationType.LinuxWindow) {
            assertEquals(SheetAnimationType.Slide, SheetAnimationType.defaultFor(pages), "$pages")
        }

        // A fresh install: the window pages, the window sheets.
        assertEquals(SheetAnimationType.Window, AppPreferences(store()).state.value.sheetAnimation)

        // An install that saved another page style and never a sheet style: plain sheets, as it has always had.
        val old = store()
        old.edit().putString("page_animation", PageAnimationType.SlideHorizontal.name).commit()
        assertEquals(SheetAnimationType.Slide, AppPreferences(old).state.value.sheetAnimation)
    }

    @Test
    fun changingThePagesDoesNotMoveTheSheetsOfSomeoneWhoNeverPickedOne() {
        val store = store()
        val preferences = AppPreferences(store)
        assertEquals(SheetAnimationType.Window, preferences.state.value.sheetAnimation)

        preferences.setPageAnimation(PageAnimationType.SlideHorizontal)
        assertEquals(SheetAnimationType.Window, preferences.state.value.sheetAnimation, "the sheets stay as they were")
        assertEquals(SheetAnimationType.Window, AppPreferences(store).state.value.sheetAnimation, "and after a restart, when a missing choice is worked out again")
    }

    @Test
    fun aPickedStyleIsKeptWhateverThePagesDo() {
        val store = store()
        val preferences = AppPreferences(store)
        preferences.setSheetAnimation(SheetAnimationType.Scale)
        preferences.setPageAnimation(PageAnimationType.FadeThrough)
        preferences.setPageAnimation(PageAnimationType.LinuxWindow)

        assertEquals(SheetAnimationType.Scale, preferences.state.value.sheetAnimation)
        assertEquals(SheetAnimationType.Scale, AppPreferences(store).state.value.sheetAnimation, "read back by the next launch")
        assertTrue(store.contains("sheet_animation"))
    }

    @Test
    fun anUnknownStoredValueFallsBackToTheDefault() {
        val store = store()
        store.edit().putString("sheet_animation", "Wobble").commit()
        assertEquals(SheetAnimationType.Window, AppPreferences(store).state.value.sheetAnimation)
    }

    @Test
    fun resettingSettingsBringsTheDefaultBack() {
        val store = store()
        val preferences = AppPreferences(store)
        preferences.setSheetAnimation(SheetAnimationType.Fade)
        preferences.resetToDefaults()
        assertEquals(SheetAnimationType.Window, preferences.state.value.sheetAnimation)
        assertFalse(store.contains("sheet_animation"), "worked out afresh the next time")
    }

    @Test
    fun settingsSearchFindsItAndOpensItsOwnPageOfAppearance() {
        for (query in listOf("sheet", "dialog", "bottom sheet", "popup")) {
            val hit = SettingsCatalog.search(query).single { it.title == "Sheet & dialog transitions" }
            assertEquals(SettingsPage.Appearance, hit.group, query)
            assertEquals(AppearanceSection.SheetTransitions, hit.appearance, query)
            assertEquals("Appearance > Sheet & dialog transitions", hit.location, query)
        }
        // The page transitions are their own entry, which a search for "page" or "slide" still reaches.
        val pages = SettingsCatalog.search("shared axis").single { it.title == "Page transitions" }
        assertEquals(AppearanceSection.PageTransitions, pages.appearance)
    }
}
