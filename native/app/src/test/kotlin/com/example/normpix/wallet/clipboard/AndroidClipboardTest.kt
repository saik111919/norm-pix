package com.example.normpix.wallet.clipboard

import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.settings.ClipboardTimeout
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The real clipboard, preferences and alarm behind the guard. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidClipboardTest {
    private lateinit var context: Context
    private lateinit var manager: ClipboardManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(ClipboardManager::class.java)
        manager.clearPrimaryClip()
    }

    private fun clipText() = manager.primaryClip?.getItemAt(0)?.text?.toString()

    @Test
    fun aCopyIsMarkedSensitiveAndTaggedWithItsToken() {
        val port = AndroidClipboardPort(context)
        port.write("Card number", "4111111111111111", "token-1")

        assertEquals("4111111111111111", clipText())
        val description = manager.primaryClipDescription!!
        assertEquals("Card number", description.label)
        assertTrue(description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"), "keyboards and previews hide it")
        assertEquals(ClipboardOwner.NormPix("token-1"), port.owner())
    }

    @Test
    fun aClipFromAnotherAppIsSomeoneElses() {
        val port = AndroidClipboardPort(context)
        port.write("Card number", "4111111111111111", "token-1")
        manager.setPrimaryClip(ClipData.newPlainText("address", "221B Baker Street"))

        assertEquals(ClipboardOwner.Someone, port.owner())
    }

    @Test
    fun clearEmptiesTheClipboard() {
        val port = AndroidClipboardPort(context)
        port.write("CVV", "737", "t")
        port.clear()
        assertNull(clipText())
    }

    @Test
    fun theGuardClearsTheRealClipboardAfterTheTimeoutButNotSomethingNewer() = runTest {
        val port = AndroidClipboardPort(context)
        val store = PrefsPendingClearStore(context.getSharedPreferences("test_clip", Context.MODE_PRIVATE))
        val guard = ClipboardGuard(port, store, backgroundScope, now = { currentTime })

        guard.copy("Card number", "4111111111111111", ClipboardTimeout.Sec10)
        advanceTimeBy(11_000)
        assertNull(clipText(), "cleared")

        guard.copy("CVV", "737", ClipboardTimeout.Sec10)
        manager.setPrimaryClip(ClipData.newPlainText("note", "buy milk"))
        advanceTimeBy(11_000)
        assertEquals("buy milk", clipText(), "left alone")
    }

    @Test
    fun thePendingCopyRoundTripsAsOnlyATokenAndADeadline() {
        val prefs = context.getSharedPreferences("test_pending", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        val store = PrefsPendingClearStore(prefs)
        assertNull(store.load())

        store.save(PendingClear("abc", 12_345L))
        assertEquals(PendingClear("abc", 12_345L), store.load())
        assertEquals(setOf("token", "deadline"), prefs.all.keys, "nothing else is kept")
        assertFalse(prefs.all.values.any { it.toString().contains("4111") })

        store.save(null)
        assertNull(store.load())
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun theBackupAlarmIsSetAndWithdrawn() {
        val backup = AlarmClearBackup(context)
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))

        backup.schedule(System.currentTimeMillis() + 60_000)
        assertNotNull(alarms.peekNextScheduledAlarm(), "an alarm is waiting")

        backup.cancel()
        assertNull(alarms.peekNextScheduledAlarm(), "and it is gone once the copy is done")
    }
}
