package com.example.normpix.updates

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * NormPix updates through the user's browser, so it has no "install apps" power of its own: Google Play Protect blocked the app
 * as "harmful" with `REQUEST_INSTALL_PACKAGES` and passed it without. What the in-app updater needed - that permission, the
 * silent-update one, the foreground service that kept a download going and the receivers of the installer - stays out of the
 * manifest, the one in the source and the one the app is built with (the libraries' merged in).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateManifestTest {
    private val gone = listOf(
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
        // Only the update download used a foreground service.
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
    )
    private val goneComponents = listOf("UpdateDownloadService", "UpdateInstallReceiver", "UpdateActionReceiver")

    @Test
    fun `the manifest in the source asks for none of the updater's powers`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        // Comments may say what the app does not have; only a declaration counts.
        val declarations = manifest.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        for (permission in gone) assertFalse(permission in declarations, "$permission is declared")
        for (component in goneComponents) assertFalse(component in declarations, "$component is declared")
        assertFalse("foregroundServiceType" in declarations, "no service runs in the foreground")
        // The check in the background stays.
        assertTrue(".updates.UpdateJobService" in declarations)
        assertTrue("android.permission.INTERNET" in declarations)
    }

    @Test
    fun `the manifest the app is built with, libraries merged in, has none of them either`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        val permissions = assertNotNull(info.requestedPermissions, "the built manifest's permissions were read").toList()
        assertTrue("android.permission.INTERNET" in permissions, "the manifest read is the app's: $permissions")
        for (permission in gone) assertFalse(permission in permissions, "$permission is requested: $permissions")

        val services = info.services.orEmpty().map { it.name.substringAfterLast('.') }
        val receivers = info.receivers.orEmpty().map { it.name.substringAfterLast('.') }
        assertTrue("UpdateJobService" in services, "the check in the background is still there: $services")
        for (component in goneComponents) {
            assertFalse(component in services || component in receivers, "$component is declared")
        }
        assertEquals(emptyList(), info.services.orEmpty().filter { it.foregroundServiceType != 0 }.map { it.name })
    }
}
