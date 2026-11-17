package com.example.normpix.updates

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.settings.AppLockScope
import com.example.normpix.settings.AppLockTimeout
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.wallet.ActivityHost
import com.example.normpix.wallet.AppLockService
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val REPO = Releases.REPO
private const val APK_URL = "https://github.com/$REPO/releases/download/v1.0.3/NormPix-1.0.3.apk"

private fun releaseAt(apkUrl: String) = UpdateRelease(
    versionCode = 10003, versionName = "1.0.3", tag = "v1.0.3", publishedAt = Instant.parse("2026-10-15T09:00:00Z"),
    notes = "- Faster receipts", apkName = "NormPix-1.0.3.apk", apkUrl = apkUrl, size = 1_000, sha256 = "0".repeat(64), mandatory = false,
)

/** The one kind of link the browser is sent to: a file of this app's own GitHub releases, and nothing else. */
class DownloadLinkTest {
    private fun trusted(url: String) = DownloadLink.trusted(url, REPO)

    @Test
    fun `the link a release is described with is trusted`() {
        assertEquals(APK_URL, trusted(APK_URL))
        assertEquals(APK_URL, trusted(ReleaseMetadata.downloadUrl(REPO, "v1.0.3", "NormPix-1.0.3.apk")))
        assertEquals(
            "https://github.com/$REPO/releases/download/v1.12.0/NormPix-1.12.0.apk",
            trusted(Releases.download("v1.12.0", "NormPix-1.12.0.apk")),
        )
    }

    @Test
    fun `another host is refused, GitHub's own other hosts included`() {
        val path = "$REPO/releases/download/v1.0.3/NormPix-1.0.3.apk"
        for (host in listOf(
            "evil.example", "github.com.evil.example", "evilgithub.com", "api.github.com", "objects.githubusercontent.com",
            "release-assets.githubusercontent.com", "raw.githubusercontent.com", "www.github.com", "github.com.", "gist.github.com",
        )) {
            assertNull(trusted("https://$host/$path"), host)
        }
    }

    @Test
    fun `only https links are trusted, http and every other kind of link is refused`() {
        val rest = "github.com/$REPO/releases/download/v1.0.3/NormPix-1.0.3.apk"
        for (scheme in listOf("http", "ftp", "file", "content", "intent", "javascript", "market", "HTTPS")) {
            assertNull(trusted("$scheme://$rest"), scheme)
        }
        assertNull(trusted("javascript:alert(1)"))
        assertNull(trusted("file:///sdcard/Download/NormPix-1.0.3.apk"))
        assertNull(trusted("//$rest"))
        assertNull(trusted("github.com/$REPO/releases/download/v1.0.3/NormPix-1.0.3.apk"))
    }

    @Test
    fun `another repository is refused, a longer name and another case included`() {
        for (other in listOf("someone/else", "owner/normpix-evil", "owner/normpix.evil", "owner-evil/normpix", "Owner/Normpix", "owner", "")) {
            assertNull(trusted("https://github.com/$other/releases/download/v1.0.3/NormPix-1.0.3.apk"), other)
        }
        // The same link for the other repository is that repository's.
        assertEquals(APK_URL, DownloadLink.trusted(APK_URL, "owner/normpix"))
        assertNull(DownloadLink.trusted(APK_URL, "someone/else"))
    }

    @Test
    fun `a port, credentials, a query or a fragment is refused`() {
        for (url in listOf(
            "https://github.com:8443/$REPO/releases/download/v1.0.3/NormPix-1.0.3.apk",
            "https://user@github.com/$REPO/releases/download/v1.0.3/NormPix-1.0.3.apk",
            "https://user:pass@github.com/$REPO/releases/download/v1.0.3/NormPix-1.0.3.apk",
            "https://github.com@evil.example/$REPO/releases/download/v1.0.3/NormPix-1.0.3.apk",
            "https://evil.example@github.com/$REPO/releases/download/v1.0.3/NormPix-1.0.3.apk",
            "$APK_URL?token=1",
            "$APK_URL?",
            "$APK_URL#frag",
        )) {
            assertNull(trusted(url), url)
        }
        assertEquals(APK_URL, trusted("https://github.com:443/$REPO/releases/download/v1.0.3/NormPix-1.0.3.apk")?.replace(":443", ""))
    }

    @Test
    fun `a path that is not a release's tag and file is refused`() {
        val base = "https://github.com/$REPO/releases"
        for (url in listOf(
            "$base/latest/download/NormPix-1.0.3.apk",
            "$base/download/v1.0.3",
            "$base/download/v1.0.3/",
            "$base/download//NormPix-1.0.3.apk",
            "$base/download/v1.0.3/sub/NormPix-1.0.3.apk",
            "$base/download/v1.0.3/../../../../evil/x/raw/NormPix.apk",
            "$base/download/../NormPix-1.0.3.apk",
            "$base/download/./NormPix-1.0.3.apk",
            "$base/download/v1.0.3/%2e%2e%2fNormPix.apk",
            "$base/download/v1.0.3/NormPix%2D1.0.3.apk",
            "$base/download/v1.0.3/NormPix 1.0.3.apk",
            "$base/tag/v1.0.3",
            "https://github.com/$REPO",
            "https://github.com/$REPO/archive/refs/heads/main.zip",
            "https://github.com/$REPO/raw/main/NormPix.apk",
        )) {
            assertNull(trusted(url), url)
        }
    }

    @Test
    fun `nothing that is not a link is trusted`() {
        for (junk in listOf("", " ", "not a url", "https://", "https:///$REPO/releases/download/v1.0.3/x.apk", "mailto:a@b.c", "\u0000")) {
            assertNull(trusted(junk), junk)
        }
    }
}

/** A screen that remembers every launch, can look at the app lock as each starts, and can refuse launches as a phone with no app for them does. */
class RecordingActivity : FragmentActivity() {
    val launched = mutableListOf<Intent>()

    /** Called as each launch begins, before it goes: what the app lock is doing at that moment is what the test looks at. */
    var onLaunch: (Intent) -> Unit = {}

    /** Which launches fail with [ActivityNotFoundException]. */
    var refuses: (Intent) -> Boolean = { false }

    /** Which launches fail with a [SecurityException] instead. */
    var forbids: (Intent) -> Boolean = { false }

    // Every launch ends here: startActivity(intent) calls this with no options.
    override fun startActivity(intent: Intent?, options: Bundle?) {
        val launch = checkNotNull(intent)
        onLaunch(launch)
        if (refuses(launch)) throw ActivityNotFoundException("No Activity found to handle $launch")
        if (forbids(launch)) throw SecurityException("not allowed")
        launched += launch
        super.startActivity(launch, options)
    }
}

/** Opening an update's download: in a Custom Tab over NormPix, or as a browser link; the lock told first; only GitHub's link of this app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowserDownloadOpenerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var now = 1_000L
    private val lock = AppLockService { now }
    private val host = ActivityHost()
    private val opener = BrowserDownloadOpener(context, host, lock, REPO)
    private val dark = DownloadTabLook(toolbarColor = 0xFF1B1B1F.toInt(), dark = true)
    private val light = DownloadTabLook(toolbarColor = 0xFFFDFBFF.toInt(), dark = false)
    private val release = releaseAt(APK_URL)

    /** The app locks the moment it leaves (Immediate), unless it was told the trip was coming. */
    private val strict = AppPrefsState(walletLockEnabled = true, appLockScope = AppLockScope.EntireApp, appLockTimeout = AppLockTimeout.Immediate)

    private fun screen(setUp: RecordingActivity.() -> Unit = {}): RecordingActivity =
        Robolectric.buildActivity(RecordingActivity::class.java).setup().get().also {
            it.setUp()
            host.activity = it
        }

    private fun isTab(intent: Intent) = intent.hasExtra(CustomTabsIntent.EXTRA_SESSION)

    @Test
    fun `the release's APK link opens in a Custom Tab, the toolbar in the app's own colour`() {
        val screen = screen()
        opener.open(release, dark)
        val launched = shadowOf(screen).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, launched.action)
        assertEquals(APK_URL, launched.dataString)
        assertTrue(isTab(launched), "a Custom Tab: the browser's own tab over NormPix")
        assertEquals(CustomTabsIntent.COLOR_SCHEME_DARK, launched.getIntExtra(CustomTabsIntent.EXTRA_COLOR_SCHEME, -1))
        assertEquals(dark.toolbarColor, launched.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0))
        assertEquals(dark.toolbarColor, launched.getIntExtra(CustomTabsIntent.EXTRA_NAVIGATION_BAR_COLOR, 0))
        assertEquals(1, screen.launched.size)
    }

    @Test
    fun `the tab is light when the app is, whatever the phone is`() {
        val screen = screen()
        opener.open(release, light)
        val launched = shadowOf(screen).nextStartedActivity
        assertEquals(CustomTabsIntent.COLOR_SCHEME_LIGHT, launched.getIntExtra(CustomTabsIntent.EXTRA_COLOR_SCHEME, -1))
        assertEquals(light.toolbarColor, launched.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0))
    }

    @Test
    fun `the app lock is told before the tab goes up, so coming back does not lock NormPix`() {
        // The screen pauses the moment the tab opens: here, as the launch begins. Told first, the lock lets that pass.
        val screen = screen { onLaunch = { lock.onAppPaused(strict) } }
        lock.unlockAll()
        opener.open(release, dark)
        assertFalse(lock.isAppLocked(strict), "left for the browser without locking")
        now += 20_000
        lock.onAppResumed(strict)
        assertFalse(lock.isAppLocked(strict), "and back from it")
        assertEquals(1, screen.launched.size)
    }

    @Test
    fun `without being told, the same trip would have locked the app (the check above checks something)`() {
        screen { onLaunch = { lock.onAppPaused(strict) } }
        lock.unlockAll()
        // Opened by something that never told the lock: the pause locks at once.
        host.activity!!.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(APK_URL)))
        assertTrue(lock.isAppLocked(strict))
    }

    @Test
    fun `a browser with no Custom Tabs gets the link the way any link opens`() {
        val screen = screen { refuses = { isTab(it) } }
        lock.unlockAll()
        opener.open(release, dark)
        val launched = screen.launched.single()
        assertEquals(Intent.ACTION_VIEW, launched.action)
        assertEquals(APK_URL, launched.dataString)
        assertFalse(isTab(launched))
        assertTrue(Intent.CATEGORY_BROWSABLE in launched.categories.orEmpty())
        // The trip is on: the lock was told, and the told-of trip is not cancelled.
        lock.onAppPaused(strict)
        assertFalse(lock.isAppLocked(strict))
    }

    @Test
    fun `with no browser at all it says so, and the lock is as strict as before`() {
        val screen = screen { refuses = { true } }
        lock.unlockAll()
        val problem = assertFailsWith<UpdateException> { opener.open(release, dark) }.problem
        assertEquals(UpdateProblem.NoBrowser, problem)
        assertEquals(emptyList(), screen.launched)
        // Nothing left the app, so the next real trip away locks at once.
        lock.onAppPaused(strict)
        assertTrue(lock.isAppLocked(strict))
    }

    @Test
    fun `a browser that refuses the link is no browser`() {
        val screen = screen { forbids = { true } }
        val problem = assertFailsWith<UpdateException> { opener.open(release, dark) }.problem
        assertEquals(UpdateProblem.NoBrowser, problem)
        assertEquals(emptyList(), screen.launched)
    }

    @Test
    fun `a link that is not this app's on GitHub is refused, opens nothing, and leaves the lock alone`() {
        val screen = screen()
        lock.unlockAll()
        for (url in listOf(
            "http://github.com/$REPO/releases/download/v1.0.3/NormPix-1.0.3.apk",
            "https://evil.example/$REPO/releases/download/v1.0.3/NormPix-1.0.3.apk",
            "https://github.com/someone/else/releases/download/v1.0.3/NormPix-1.0.3.apk",
            "https://api.github.com/repos/$REPO/releases/assets/1",
            "https://github.com/$REPO/releases/download/v1.0.3/NormPix-1.0.3.apk?x=1",
            "intent://github.com/#Intent;scheme=https;end",
        )) {
            val problem = assertFailsWith<UpdateException>(url) { opener.open(releaseAt(url), dark) }.problem
            assertEquals(UpdateProblem.BadLink, problem, url)
        }
        assertEquals(emptyList(), screen.launched)
        assertNull(shadowOf(screen).nextStartedActivity)
        lock.onAppPaused(strict)
        assertTrue(lock.isAppLocked(strict), "no trip was expected, so none was allowed for")
    }

    @Test
    fun `a link is checked against the repository the opener was made for`() {
        val screen = screen()
        val other = BrowserDownloadOpener(context, host, lock, "someone/else")
        assertEquals(UpdateProblem.BadLink, assertFailsWith<UpdateException> { other.open(release, dark) }.problem)
        assertEquals(emptyList(), screen.launched)
    }

    @Test
    fun `with no screen to open it from, the tab opens in a task of its own`() {
        host.activity = null
        opener.open(release, dark)
        val launched = shadowOf(context as Application).nextStartedActivity
        assertEquals(APK_URL, launched.dataString)
        assertTrue(isTab(launched))
        assertTrue(launched.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
