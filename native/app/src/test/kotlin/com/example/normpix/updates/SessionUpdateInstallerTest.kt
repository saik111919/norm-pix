package com.example.normpix.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.settings.AppLockTimeout
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.wallet.ActivityHost
import com.example.normpix.wallet.AppLockService
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Handing an update to Android's installer: one session, committed only when the file is still the release, never quietly. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionUpdateInstallerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val host = ActivityHost()
    private var now = 1_000L
    private val lock = AppLockService { now }
    private val installer = SessionUpdateInstaller(context, host, lock)
    private val sessions: PackageInstaller get() = context.packageManager.packageInstaller
    private val dir: File = createTempDirectory("updates").toFile()

    @AfterTest
    fun cleanUp() {
        sessions.mySessions.forEach { sessions.abandonSession(it.sessionId) }
        dir.deleteRecursively()
    }

    private fun apk(bytes: ByteArray) = File(dir, "NormPix-10003.apk").apply { writeBytes(bytes) }

    private fun release(bytes: ByteArray) = UpdateRelease(
        versionCode = 10003, versionName = "1.0.3", tag = "v1.0.3", publishedAt = null, notes = "", apkName = "NormPix-1.0.3.apk",
        apkUrl = "https://github.com/owner/normpix/releases/download/v1.0.3/NormPix-1.0.3.apk", size = bytes.size.toLong(),
        sha256 = Releases.sha256(bytes), mandatory = false,
    )

    @Test
    fun `the update goes to Android in a session of its own, for this app`() {
        val bytes = Releases.apk(20_000)
        var id = -1
        installer.install(apk(bytes), release(bytes)) { id = it }
        assertTrue(id > 0, "the id is known before the installer can answer")
        assertTrue(installer.isActive(id))
        val info = assertNotNull(sessions.getSessionInfo(id))
        assertEquals(context.packageName, info.appPackageName)
    }

    @Test
    fun `a file changed since it was downloaded is not installed`() {
        val bytes = Releases.apk(20_000)
        val changed = bytes.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val problem = assertFailsWith<UpdateException> { installer.install(apk(changed), release(bytes)) {} }.problem
        assertEquals(UpdateProblem.ChecksumMismatch, problem)
        assertTrue(sessions.mySessions.isEmpty(), "its session was abandoned")
    }

    @Test
    fun `an earlier install left behind is dropped for the new one`() {
        val bytes = Releases.apk(20_000)
        var first = -1
        installer.install(apk(bytes), release(bytes)) { first = it }
        var second = -1
        installer.install(apk(bytes), release(bytes)) { second = it }
        assertEquals(listOf(second), sessions.mySessions.map { it.sessionId })
        assertFalse(installer.isActive(first))
    }

    @Test
    fun `leave to install apps is Android's to give, in its own setting`() {
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)
        assertFalse(installer.canInstall())
        shadowOf(context.packageManager).setCanRequestPackageInstalls(true)
        assertTrue(installer.canInstall())

        // No screen to open it from: nothing opens.
        assertFalse(installer.openInstallSetting())
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        host.activity = activity
        assertTrue(installer.openInstallSetting())
        val opened = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, opened.action)
        assertEquals("package:${context.packageName}", opened.dataString)
        // Coming back from it doesn't count as leaving NormPix: the lock lets a short trip pass.
        val prefs = AppPrefsState(walletLockEnabled = true, appLockTimeout = AppLockTimeout.Immediate)
        lock.unlockAll()
        lock.onAppPaused(prefs)
        now += 5_000
        lock.onAppResumed(prefs)
        assertFalse(lock.isWalletLocked(prefs))
    }

    @Test
    fun `what the installer broadcasts reaches the updater`() = runTest {
        val fake = FakeInstaller()
        val network = FakeNetwork()
        Releases.publish(network)
        val github = GitHubAccess(network, "NormPix/test")
        val updater = AppUpdater(
            installed = Releases.installed,
            checker = UpdateChecker(github, Releases.REPO, Releases.installed),
            downloads = UpdateDownloader(github, dir),
            inspector = FakeInspector(),
            installer = fake,
            records = MemoryUpdateRecords(),
            scope = this,
            io = StandardTestDispatcher(testScheduler),
        ).also { it.onForeground() }
        updater.check()
        updater.download()
        advanceUntilIdle()
        assertIs<UpdateState.Installing>(updater.state.value)

        val confirm = Intent("android.content.pm.action.CONFIRM_INSTALL")
        deliverInstallStatus(
            updater,
            Intent().putExtra(PackageInstaller.EXTRA_SESSION_ID, 1)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
                .putExtra(Intent.EXTRA_INTENT, confirm),
        )
        assertEquals(listOf(confirm.action), fake.confirmations.map { it.action })

        deliverInstallStatus(
            updater,
            Intent().putExtra(PackageInstaller.EXTRA_SESSION_ID, 1)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_CONFLICT)
                .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match"),
        )
        assertEquals(UpdateProblem.SignatureMismatch, assertIs<UpdateState.Failed>(updater.state.value).problem)
    }
}
