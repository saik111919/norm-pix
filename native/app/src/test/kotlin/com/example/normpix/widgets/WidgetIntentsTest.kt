package com.example.normpix.widgets

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.MainActivity
import com.example.normpix.quickadd.QuickAddActivity
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
            // An add opens the sheet over the app in front; a transaction and Activity are in the app.
            val target = if (request is WidgetRequest.Add) QuickAddActivity::class.java else MainActivity::class.java
            assertEquals(target.name, intent.component?.className, request.toString())
            assertEquals(request, WidgetIntents.requestOf(intent))
        }
    }

    @Test
    fun `the add widget's buttons and the recent widget's plus open the overlay, its rows and heading the app`() {
        for (type in TransactionType.entries) {
            assertEquals(QuickAddActivity::class.java.name, intentOf(WidgetRequest.Add(type)).component?.className, type.name)
        }
        assertEquals(MainActivity::class.java.name, intentOf(WidgetRequest.Show(7)).component?.className)
        assertEquals(MainActivity::class.java.name, intentOf(WidgetRequest.Activity).component?.className)
        // Neither is exported: the system starts a widget's PendingIntent as NormPix, so no other app needs a way in.
        val packages = context.packageManager
        for (target in listOf(QuickAddActivity::class.java, MainActivity::class.java)) {
            assertEquals(false, packages.getActivityInfo(ComponentName(context, target), 0).exported, target.name)
        }
    }

    @Test
    fun `an add request keeps its own pending intent apart from the app's`() {
        // The same word to two different screens (a widget made before the overlay still names MainActivity): never one PendingIntent.
        val overlay = WidgetIntents.open(context, WidgetRequest.Add(TransactionType.Expense))
        val old = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).setAction("com.example.normpix.widget." + WidgetRequest.Add(TransactionType.Expense).key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        assertNotEquals(overlay, old)
        assertEquals(QuickAddActivity::class.java.name, shadowOf(overlay).savedIntent.component?.className)
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
