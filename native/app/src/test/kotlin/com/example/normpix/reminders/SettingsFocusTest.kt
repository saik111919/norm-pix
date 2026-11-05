package com.example.normpix.reminders

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Android pages NormPix sends the person to are opened on NormPix by name: the package is in every request, so the phones that
 * honour it land on NormPix's own switch. On Android 17's Settings (an emulator) "Display over other apps" ignores the package and
 * opens the list of every app - and its one-app action is refused to other apps - so the person is also told which row to tap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsFocusTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun displayOverOtherAppsIsRequestedForNormPixByName() {
        val intent = ReminderAccess.overlaySettingsIntent(context)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }

    @Test
    fun thePersonIsToldWhichRowToTapInCaseTheListOpens() {
        assertTrue("NormPix" in ReminderAccess.OVERLAY_HINT)
        assertTrue("Allow display over other apps" in ReminderAccess.OVERLAY_HINT, "in the words Android's own switch uses")
    }

    @Test
    fun theOtherPagesAreAlreadyOnNormPix() {
        assertEquals("package:${context.packageName}", ReminderAccess.fullScreenSettingsIntent(context)?.dataString)
        assertEquals("package:${context.packageName}", AlarmReminderScheduler(context).exactAlarmSettingsIntent()?.dataString)
        assertEquals("package:${context.packageName}", ReminderAccess.appDetailsIntent(context).dataString)
    }
}
