package com.example.normpix.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.R
import com.example.normpix.testing.TestEnvironment
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Widget settings for a clock: the choices on screen, each applied as it is made, and the activity Android opens for them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
class ClockWidgetSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private var options by mutableStateOf(ClockOptions())
    private var done = 0
    private var size = 160f to 160f

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("clock_widget_settings")
        options = ClockOptions()
    }

    @After
    fun tearDown() = env.close()

    private fun open(widget: HomeWidget = HomeWidget.CookieAnalog) {
        compose.setContent {
            env.Content {
                ClockWidgetSettings(widget, options, size, onChange = { options = it }, onDone = { done++ })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `it names the clock and says what it shows`() {
        open()
        compose.onNodeWithText("Clock settings").assertExists()
        compose.onNodeWithText(HomeWidget.CookieAnalog.title).assertExists()
        compose.onNodeWithText("Showing the clock face.").assertExists()
    }

    @Test
    fun `transparent is a switch, off to begin with`() {
        open()
        val row = compose.onNodeWithText("Transparent")
        row.assertIsOff()
        row.performClick()
        compose.waitForIdle()
        assertTrue(options.transparent)
        row.assertIsOn()
        row.performClick()
        compose.waitForIdle()
        assertTrue(!options.transparent)
    }

    @Test
    fun `the clock face or the digital clock, and the fallback follows the face`() {
        open()
        compose.onNodeWithText("Clock face").assertIsSelected()
        compose.onNodeWithText("Digital clock when small").assertIsEnabled().assertIsOn()
        compose.onNodeWithText("Digital clock when small").performClick()
        compose.waitForIdle()
        assertTrue(!options.digitalWhenSmall)

        compose.onNode(hasText("Digital clock") and isSelectable()).performClick()
        compose.waitForIdle()
        assertEquals(ClockStyle.Digital, options.style)
        compose.onNodeWithText("Showing the digital clock.").assertExists()
        // The digital clock for good has no use for the small fallback.
        compose.onNodeWithText("Digital clock when small").assertIsNotEnabled()
    }

    @Test
    fun `a widget too small for its face says so`() {
        size = 57f to 57f
        open()
        compose.onNodeWithText("Showing the digital clock: this widget is too small for its face.").assertExists()
    }

    @Test
    fun `how the digital clock reads is chosen here`() {
        open()
        compose.onNodeWithText(ClockTimeFormat.System.label).assertIsSelected()
        compose.onNodeWithText(ClockTimeFormat.Hours24.label).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(ClockTimeFormat.Hours24, options.format)
        compose.onNodeWithText("Show seconds").performScrollTo().performClick()
        compose.onNodeWithText("Show the date").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(options.showSeconds && options.showDate)
    }

    @Test
    fun `done closes it`() {
        open()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        assertEquals(1, done)
    }

    // ---- the activity ----

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** A widget of [provider] on the home screen with [id], as the launcher would have bound it. */
    private fun placed(provider: Class<out android.appwidget.AppWidgetProvider>, id: Int): Int {
        val info = android.appwidget.AppWidgetProviderInfo().apply { this.provider = ComponentName(context, provider) }
        shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(id, info)
        return id
    }

    private fun configureIntent(id: Int) = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .setComponent(ComponentName(context, ClockWidgetConfigActivity::class.java))
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

    @Test
    fun `an id that is not a clock of NormPix's finishes at once, and is cancelled`() {
        val activity = Robolectric.buildActivity(ClockWidgetConfigActivity::class.java, configureIntent(9999)).create().get()
        assertTrue(activity.isFinishing)
        assertEquals(android.app.Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        val none = Robolectric.buildActivity(ClockWidgetConfigActivity::class.java, Intent()).create().get()
        assertTrue(none.isFinishing)
    }

    @Test
    fun `for a clock it stays, and keeps the widget whichever way it is left`() {
        val id = placed(CookieAnalogWidget::class.java, 41)
        assertEquals(HomeWidget.CookieAnalog, ClockWidgets.widgetOf(context, id))
        val activity = Robolectric.buildActivity(ClockWidgetConfigActivity::class.java, configureIntent(id)).create().get()
        assertTrue(!activity.isFinishing)
        assertEquals(android.app.Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(id, shadowOf(activity).resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertNotNull(ClockWidgets.sizeOf(context, id))
    }

    @Test
    fun `the clocks are told apart from the other widgets`() {
        val recent = placed(RecentWidget::class.java, 42)
        assertEquals(null, ClockWidgets.widgetOf(context, recent), "Recent has no clock settings")
        for ((index, widget) in HomeWidget.inGroup(HomeWidgetGroup.Clocks).withIndex()) {
            val id = placed(widget.provider, 50 + index)
            assertEquals(widget, ClockWidgets.widgetOf(context, id), widget.name)
        }
    }

    @Test
    fun `every clock's provider info opens these settings, and may be changed later`() {
        val clocks = listOf(
            R.xml.widget_clock_info, R.xml.widget_cookie_analog_info, R.xml.widget_cookie_numbers_info,
            R.xml.widget_cookie_plain_info, R.xml.widget_cookie_digital_info,
        )
        for (xml in clocks) {
            val parser = context.resources.getXml(xml)
            while (parser.next() != org.xmlpull.v1.XmlPullParser.START_TAG) Unit
            val attributes = (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
            assertEquals("com.example.normpix.widgets.ClockWidgetConfigActivity", attributes["configure"], "$attributes")
            // reconfigurable (1) and configuration_optional (4): both bits are set.
            val features = Integer.decode(attributes.getValue("widgetFeatures"))
            assertTrue((features and 1) != 0 && (features and 4) != 0, "reconfigurable and optional: $features")
            // Down to one cell (about 40 dp), where the face gives way to the digital clock.
            assertTrue(attributes.getValue("minResizeWidth").startsWith("40"), "$attributes")
        }
    }
}
