package com.example.normpix.widgets

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.MainActivity
import com.example.normpix.finance.domain.TransactionType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Adding a transaction from the app's icon or the Quick Settings: the shortcuts and the tile open the app the way the Add widget's
 * buttons do - an explicit intent with only a plain word on it - so a locked app stays locked and nothing of the ledger is on a shortcut.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickAddTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the intent names the app's own activity and carries only the request's word`() {
        for (type in TransactionType.entries) {
            val request = WidgetRequest.Add(type)
            val intent = WidgetIntents.intent(context, request)
            assertEquals(MainActivity::class.java.name, intent.component?.className, "explicit: no other app can catch it")
            assertEquals(request, WidgetIntents.requestOf(intent), type.name)
            assertEquals(setOf(WidgetIntents.EXTRA_REQUEST), intent.extras?.keySet().orEmpty(), "nothing but the word: $type")
            assertEquals(null, intent.data, "no data")
        }
    }

    @Test
    fun `three shortcuts go on the icon, expense first, each opening its own sheet`() {
        QuickAddShortcuts.publish(context)

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }
        assertEquals(listOf("add-expense", "add-income", "add-transfer"), shortcuts.map { it.id })
        assertEquals(QuickAddShortcuts.ids, shortcuts.map { it.id })
        assertEquals(listOf("Add expense", "Add income", "Transfer"), shortcuts.map { it.shortLabel.toString() })
        val opens = shortcuts.map { WidgetIntents.requestOf(it.intent) }
        assertEquals(TransactionType.entries.map { WidgetRequest.Add(it) }.sortedBy { it.type.ordinal }, opens.filterNotNull().sortedBy { (it as WidgetRequest.Add).type.ordinal })
        assertEquals(WidgetRequest.Add(TransactionType.Expense), opens.first())
    }

    @Test
    fun `publishing again leaves three, not six`() {
        QuickAddShortcuts.publish(context)
        QuickAddShortcuts.publish(context)
        assertEquals(3, ShortcutManagerCompat.getDynamicShortcuts(context).size)
    }

    @Test
    fun `the tile is declared, and only the system can bind it`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val service = manifest.substringAfter("android:name=\".widgets.QuickAddTileService\"").substringBefore("</service>")
        assertTrue("android.permission.BIND_QUICK_SETTINGS_TILE" in service, "bound by the system alone")
        assertTrue("android.service.quicksettings.action.QS_TILE" in service)
        assertTrue("android:label=\"Add expense\"" in service)
    }
}
