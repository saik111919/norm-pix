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

/** The interface-icon set the user picks: Material until they say otherwise, remembered, reset with the rest, and found by Settings search. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IconStylePreferenceTest {
    private fun store() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("icon_style_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun isMaterialUntilTheUserPicksAnother_andIsRemembered() {
        val store = store()
        assertEquals(IconStyle.Material, AppPreferences(store).state.value.iconStyle, "nobody's icons change by an update")

        AppPreferences(store).setIconStyle(IconStyle.Lucide)
        assertEquals(IconStyle.Lucide, AppPreferences(store).state.value.iconStyle, "read back by the next launch")
        assertEquals("Lucide", store.getString("icon_style", null), "stored under a name that is never changed")
    }

    @Test
    fun anUnknownStoredValueFallsBackToMaterial() {
        val store = store()
        store.edit().putString("icon_style", "Phosphor").commit()
        assertEquals(IconStyle.Material, AppPreferences(store).state.value.iconStyle)
    }

    @Test
    fun resettingSettingsBringsMaterialBack() {
        val preferences = AppPreferences(store())
        preferences.setIconStyle(IconStyle.Lucide)
        preferences.resetToDefaults()
        assertEquals(IconStyle.Material, preferences.state.value.iconStyle)
    }

    @Test
    fun settingsSearchFindsItInAppearance() {
        for (query in listOf("lucide", "icon set", "interface icons", "thin")) {
            val hit = SettingsCatalog.search(query).first { it.title == "Interface icons" }
            assertEquals(SettingsPage.Appearance, hit.group, query)
        }
        assertTrue(SettingsCatalog.search("dark").none { it.title == "Interface icons" })
    }

    @Test
    fun everyStyleIsDescribed() {
        for (style in IconStyle.entries) assertTrue(style.label.isNotBlank() && style.description.isNotBlank(), style.name)
    }
}
