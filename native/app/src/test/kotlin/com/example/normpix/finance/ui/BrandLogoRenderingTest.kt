package com.example.normpix.finance.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.ui.components.SvgCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrandLogoRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyBundledBrandLogoIsAValidSvgWithViewBox() {
        val brands = context.assets.list("brands") ?: emptyArray()
        assertTrue("brands directory should not be empty", brands.isNotEmpty())
        for (file in brands) {
            val path = "brands/$file"
            val svg = SvgCache.get(context, path)
            assertNotNull("$path failed to load as SVG", svg)
            val box = svg!!.documentViewBox
            assertTrue("$path has no usable viewBox", box != null && box.width() > 0f && box.height() > 0f)
        }
    }

    @Test
    fun brandCategoryIconsMapCorrectlyToAssets() {
        assertTrue("brandCategoryIcons should have registered brands", brandCategoryIcons.isNotEmpty())
        for ((key, spec) in brandCategoryIcons) {
            assertNotNull("Spec for $key should define a brand asset", spec.brandAsset)
            assertEquals("brandAsset($key) should match spec asset", spec.brandAsset, brandAsset(key))
            val svg = SvgCache.get(context, spec.brandAsset!!)
            assertNotNull("${spec.brandAsset} failed to load from assets", svg)
            assertTrue("Key $key must be present in global categoryIcons map", categoryIcons.containsKey(key))
            assertNotNull("categoryIcon($key) fallback must exist", categoryIcon(key))
            assertTrue("categoryEmoji($key) fallback must exist", categoryEmoji(key).isNotBlank())
        }
    }
}
