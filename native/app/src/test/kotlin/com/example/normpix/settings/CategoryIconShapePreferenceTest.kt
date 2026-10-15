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

/** The shape behind category icons and people: a circle until the user picks another, remembered, and found by Settings search. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryIconShapePreferenceTest {
    private fun store() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("category_icon_shape_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun isACircleUntilTheUserPicksAnother_andIsRemembered() {
        val store = store()
        assertEquals(CategoryIconShape.Circle, AppPreferences(store).state.value.categoryIconShape)

        AppPreferences(store).setCategoryIconShape(CategoryIconShape.Square)
        assertEquals(CategoryIconShape.Square, AppPreferences(store).state.value.categoryIconShape, "read back by the next launch")
        assertEquals("Square", store.getString("category_icon_shape", null))
    }

    @Test
    fun anUnknownStoredValueFallsBackToTheCircle() {
        val store = store()
        store.edit().putString("category_icon_shape", "Hexagon").commit()
        assertEquals(CategoryIconShape.Circle, AppPreferences(store).state.value.categoryIconShape)
    }

    @Test
    fun resettingSettingsBringsTheCircleBack() {
        val preferences = AppPreferences(store())
        preferences.setCategoryIconShape(CategoryIconShape.None)
        preferences.resetToDefaults()
        assertEquals(CategoryIconShape.Circle, preferences.state.value.categoryIconShape)
    }

    @Test
    fun theShapesGoFromRoundToSquare_andEveryOneIsDescribed() {
        val percent = CategoryIconShape.entries.associateWith { it.cornerPercent }
        assertEquals(50, percent.getValue(CategoryIconShape.Circle), "a circle is corners of half the size")
        assertTrue(percent.getValue(CategoryIconShape.Circle) > percent.getValue(CategoryIconShape.Rounded))
        assertTrue(percent.getValue(CategoryIconShape.Rounded) > percent.getValue(CategoryIconShape.Square))
        for (shape in CategoryIconShape.entries) assertTrue(shape.label.isNotBlank() && shape.description.isNotBlank(), shape.name)
    }

    @Test
    fun settingsSearchFindsItInAppearance() {
        for (query in listOf("icon background", "shape", "radius", "rounded", "square", "circle", "corners")) {
            val hit = SettingsCatalog.search(query).single { it.title == "Icon background" }
            assertEquals(SettingsPage.Appearance, hit.group, query)
        }
        assertTrue(SettingsCatalog.search("dark").none { it.title == "Icon background" })
    }
}
