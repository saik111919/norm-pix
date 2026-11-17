package com.example.normpix.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.example.normpix.wallet.ActivityHost
import com.example.normpix.wallet.AppLockService
import java.net.URI
import java.net.URISyntaxException

/**
 * How the browser tab that downloads an update looks: its toolbar in the app's own surface colour, light or dark as the app is
 * (whichever theme the user picked, which need not be the phone's).
 */
data class DownloadTabLook(@ColorInt val toolbarColor: Int, val dark: Boolean)

/**
 * Hands an update's download to the user's browser. NormPix downloads and installs nothing itself: the browser saves the APK, the
 * user opens it, and Android's own installer asks before updating NormPix over itself (same signing key, so the data stays).
 */
fun interface UpdateDownloadOpener {
    /** Opens [release]'s APK link. Throws an [UpdateException] ([UpdateProblem.BadLink], [UpdateProblem.NoBrowser]) when it can't. */
    fun open(release: UpdateRelease, look: DownloadTabLook)
}

/**
 * The one kind of link the browser is sent to: a file attached to a release of this app's own GitHub repository, as
 * [ReleaseMetadata.downloadUrl] writes it - `https://github.com/<owner>/<repo>/releases/download/<tag>/<file>`. Another host, a
 * plain `http` link, another repository, a port, credentials, a query or a path that climbs out of the release are all refused.
 */
object DownloadLink {
    private const val HOST = "github.com"
    private val SEGMENT = Regex("""[A-Za-z0-9._-]+""")

    /** [url] when it is a release file of [repository] (`owner/name`) on GitHub; null for anything else. */
    fun trusted(url: String, repository: String): String? {
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return null
        }
        if (uri.isOpaque || uri.scheme != "https" || uri.host?.lowercase() != HOST) return null
        if (uri.port != -1 && uri.port != 443) return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        val path = uri.rawPath ?: return null
        val prefix = "/$repository/releases/download/"
        if (!path.startsWith(prefix)) return null
        // The tag and the file, each a plain name: nothing that encodes, climbs ("..") or adds a level.
        val rest = path.removePrefix(prefix).split('/')
        if (rest.size != 2 || rest.any { !SEGMENT.matches(it) || it == "." || it == ".." }) return null
        return uri.toASCIIString()
    }
}

/**
 * [UpdateDownloadOpener] through a Chrome Custom Tab (the user's browser, in a tab over NormPix, its toolbar in the app's colours),
 * or - where the browser has no Custom Tabs - the browser the way any link opens. Before either, the app lock is told the user is
 * about to leave for another app's screen, as for the system's file pickers, so coming back doesn't lock NormPix.
 *
 * Only [DownloadLink.trusted] links of [repository] are opened. Nothing is logged: the link is a public GitHub one, but what the
 * user downloads is theirs to know.
 */
class BrowserDownloadOpener(
    context: Context,
    private val host: ActivityHost,
    private val lock: AppLockService,
    private val repository: String,
) : UpdateDownloadOpener {
    private val appContext = context.applicationContext

    override fun open(release: UpdateRelease, look: DownloadTabLook) {
        val link = DownloadLink.trusted(release.apkUrl, repository) ?: throw UpdateException(UpdateProblem.BadLink)
        val uri = link.toUri()
        val activity = host.activity
        // Called before the launch, as the pickers do: the activity pauses as soon as the tab is up.
        lock.expectExternalActivity()
        val context: Context = activity ?: appContext
        val newTask = activity == null
        if (launch { tab(look).apply { if (newTask) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.launchUrl(context, uri) }) return
        if (launch { context.startActivity(browse(uri, newTask)) }) return
        // Nothing left the app: the trip the lock was told of isn't happening.
        lock.cancelExternalActivity()
        throw UpdateException(UpdateProblem.NoBrowser)
    }

    private fun tab(look: DownloadTabLook): CustomTabsIntent {
        val colors = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(look.toolbarColor)
            .setNavigationBarColor(look.toolbarColor)
            .build()
        return CustomTabsIntent.Builder()
            .setColorScheme(if (look.dark) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
            .setDefaultColorSchemeParams(colors)
            .setShowTitle(true)
            .build()
    }

    private fun browse(uri: Uri, newTask: Boolean) = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        .apply { if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /** Whether [start] opened something: false when no app takes it (or the one that would refuses). */
    private fun launch(start: () -> Unit): Boolean = try {
        start()
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
