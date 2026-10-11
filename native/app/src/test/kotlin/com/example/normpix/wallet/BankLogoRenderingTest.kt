package com.example.normpix.wallet

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.SVG
import com.example.normpix.ui.components.SvgCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BankLogoRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyBundledLogoScalesToItsBoxWithoutStretching() {
        for (path in context.assets.list("banks")!!.map { "banks/$it" }) {
            val svg = SvgCache.get(context, path)
            assertNotNull(path, svg)
            val box = svg!!.documentViewBox
            assertTrue("$path has no usable viewBox", box != null && box.width() > 0f && box.height() > 0f)
            // Sized to whatever it is drawn into, fitted whole: no fixed pixel size to overflow or crop.
            assertEquals(path, -1f, svg.documentWidth)
            assertEquals(path, PreserveAspectRatio.LETTERBOX, svg.documentPreserveAspectRatio)
        }
    }

    @Test
    fun aDocumentWithOnlyAPixelSizeKeepsItsProportions() {
        // hdfc.svg declares width/height but no viewBox - the case that used to crop.
        val hdfc = SvgCache.get(context, "banks/hdfc.svg")!!.documentViewBox!!
        assertEquals(289f / 50f, hdfc.width() / hdfc.height(), 0.01f)
        // sbin.svg has both: the viewBox, not the pixel size, is the artwork.
        val sbi = SvgCache.get(context, "banks/sbin.svg")!!.documentViewBox!!
        assertEquals(100f / 34.26f, sbi.width() / sbi.height(), 0.01f)
    }
}
