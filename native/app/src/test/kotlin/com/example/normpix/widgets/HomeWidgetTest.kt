package com.example.normpix.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Settings > Widgets lists the widgets NormPix has: the list is the one the launcher's picker shows, each is previewed as the
 * home screen draws it, and each can be asked of the launcher.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the list is the receivers of the manifest that are widgets - no more, no fewer`() {
        val declared = context.packageManager
            .queryBroadcastReceivers(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName), PackageManager.GET_META_DATA)
            .map { it.activityInfo.name }
            .toSet()
        assertEquals(declared, HomeWidget.entries.map { it.provider.name }.toSet())
    }

    @Test
    fun `every widget is a receiver the launcher can place, with a name for the picker`() {
        for (widget in HomeWidget.entries) {
            val info = context.packageManager.getReceiverInfo(widget.component(context), PackageManager.GET_META_DATA)
            assertFalse(info.exported, "${widget.name} is reached by the system only")
            assertTrue(info.labelRes != 0, "${widget.name} has a name in the picker")
            assertNotNull(info.metaData.getInt("android.appwidget.provider").takeIf { it != 0 }, widget.name)
        }
    }

    @Test
    fun `every widget is told apart and described`() {
        assertEquals(HomeWidget.entries.size, HomeWidget.entries.map { it.pill }.toSet().size, "pills are told apart")
        assertEquals(HomeWidget.entries.size, HomeWidget.entries.map { it.title }.toSet().size, "titles are told apart")
        for (widget in HomeWidget.entries) {
            assertTrue(widget.description.isNotBlank(), widget.name)
            assertTrue(widget.previewWidthDp > 0 && widget.previewHeightDp > 0, widget.name)
            assertTrue(widget in HomeWidget.inGroup(widget.group), widget.name)
        }
        assertEquals(HomeWidget.entries.toSet(), HomeWidgetGroup.entries.flatMap { HomeWidget.inGroup(it) }.toSet())
    }

    @Test
    fun `the cookie clock comes in three styles, beside the watch dial`() {
        assertEquals(
            listOf(HomeWidget.CookieAnalog, HomeWidget.CookieNumbers, HomeWidget.CookieDigital, HomeWidget.WatchDial),
            HomeWidget.inGroup(HomeWidgetGroup.Clocks),
        )
        assertEquals(listOf(HomeWidget.Recent, HomeWidget.Add), HomeWidget.inGroup(HomeWidgetGroup.Transactions))
    }

    @Test
    fun `every widget previews, and what is previewed does nothing when tapped`() {
        for (widget in HomeWidget.entries) {
            val frame = FrameLayout(context)
            val shown = widget.previewViews(context).apply(context, frame)
            shown.makeInert()
            assertTrue(clickable(shown).isEmpty(), "${widget.name}'s preview has nothing to tap: ${clickable(shown)}")
        }
    }

    private fun clickable(view: View): List<String> = buildList {
        if (view.isClickable || view.hasOnClickListeners()) add(view.javaClass.simpleName)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(clickable(view.getChildAt(i)))
    }

    @Test
    fun `none is on the home screen until the launcher places it`() {
        for (widget in HomeWidget.entries) assertEquals(0, widget.placed(context), widget.name)
    }

    @Test
    fun `a launcher that can be asked is asked, and one that cannot is not`() {
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        manager.setRequestPinAppWidgetSupported(false)
        assertFalse(WidgetPinning.supported(context))
        for (widget in HomeWidget.entries) assertFalse(WidgetPinning.request(context, widget), widget.name)

        manager.setRequestPinAppWidgetSupported(true)
        assertTrue(WidgetPinning.supported(context))
        for (widget in HomeWidget.entries) assertTrue(WidgetPinning.request(context, widget), widget.name)
    }

    @Test
    fun `the component asked for is the widget's own receiver`() {
        assertEquals(ComponentName(context, CookieDigitalWidget::class.java), HomeWidget.CookieDigital.component(context))
        assertEquals(ComponentName(context, ClockWidget::class.java), HomeWidget.WatchDial.component(context))
    }
}
