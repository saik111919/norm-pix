package com.example.normpix.widgets

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.MainActivity
import com.example.normpix.finance.domain.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A widget's tap opens NormPix with what it asked for - and only once, not again when the app comes back from Recents. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetIntentsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun intentOf(request: WidgetRequest): Intent = shadowOf(WidgetIntents.open(context, request)).savedIntent

    @Test
    fun `each tap opens the app with its request`() {
        for (request in listOf(WidgetRequest.Add(TransactionType.Income), WidgetRequest.Show(7), WidgetRequest.Activity)) {
            val intent = intentOf(request)
            assertEquals(MainActivity::class.java.name, intent.component?.className)
            assertEquals(request, WidgetIntents.requestOf(intent))
        }
    }

    @Test
    fun `one row never opens another's transaction`() {
        val first = WidgetIntents.open(context, WidgetRequest.Show(1))
        val second = WidgetIntents.open(context, WidgetRequest.Show(2))
        assertNotEquals(first, second)
        assertEquals(WidgetRequest.Show(1), WidgetIntents.requestOf(shadowOf(first).savedIntent))
    }

    @Test
    fun `reopened from Recents, the tap is not done again`() {
        val intent = intentOf(WidgetRequest.Add(TransactionType.Expense)).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(WidgetIntents.requestOf(intent))
        val cleared = intentOf(WidgetRequest.Activity).also(WidgetIntents::clear)
        assertNull(WidgetIntents.requestOf(cleared))
    }
}
