package com.example.normpix.widgets

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * The three widgets keep Android's rules for widgets: each is a provider only the system's widget service reaches, with a
 * name and a description for the picker, a preview, a starting size in cells, room to be resized - and none asks to be woken
 * on a timer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetSetupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun info(xml: Int): Map<String, String> {
        val parser = context.resources.getXml(xml)
        while (parser.next() != XmlPullParser.START_TAG) Unit
        assertEquals("appwidget-provider", parser.name)
        return (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
    }

    private fun receiver(name: String): ActivityInfo =
        context.packageManager.getReceiverInfo(ComponentName(context, "com.example.normpix.widgets.$name"), PackageManager.GET_META_DATA)

    @Test
    fun `each widget is declared for the widget service alone, with its provider info`() {
        for ((name, xml) in listOf("ClockWidget" to R.xml.widget_clock_info, "RecentWidget" to R.xml.widget_recent_info, "AddWidget" to R.xml.widget_add_info)) {
            val info = receiver(name)
            assertFalse(info.exported, "$name is reached by the system only")
            assertEquals(xml, info.metaData.getInt("android.appwidget.provider"), name)
            assertTrue(info.labelRes != 0, "$name has a name in the picker")
        }
    }

    @Test
    fun `each has a description, a preview, a size in cells and room to be resized, and none is woken on a timer`() {
        for (xml in listOf(R.xml.widget_clock_info, R.xml.widget_recent_info, R.xml.widget_add_info)) {
            val info = info(xml)
            assertNotNull(info["description"], "$info")
            assertTrue(info["previewLayout"] != null || info["previewImage"] != null, "a preview: $info")
            assertNotNull(info["targetCellWidth"], "$info")
            assertNotNull(info["targetCellHeight"], "$info")
            assertNotNull(info["minResizeWidth"], "$info")
            assertNotNull(info["initialLayout"], "$info")
            // 3 = horizontal|vertical.
            assertEquals(3, Integer.decode(info.getValue("resizeMode")), "$info")
            assertEquals(0, Integer.decode(info.getValue("updatePeriodMillis")), "drawn when something changes, never on a timer: $info")
            assertEquals(1, Integer.decode(info.getValue("widgetCategory")), "the home screen: $info")
        }
    }
}
