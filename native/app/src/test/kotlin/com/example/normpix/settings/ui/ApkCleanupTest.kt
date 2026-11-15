package com.example.normpix.settings.ui

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import com.example.normpix.settings.AppLockScope
import com.example.normpix.settings.AppLockTimeout
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.updates.APK_MIME_TYPE
import com.example.normpix.updates.ApkFileStore
import com.example.normpix.updates.PickedApk
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings > Automatic updates > Delete downloaded APK files: the row, the system picker it opens, the sheet that asks, the deleting
 * and what the page says afterwards. The phone's picker is replaced by a registry that answers with the files a test picks
 * (everything else, the launcher included, is the app's own), and the phone's files by a store that remembers what was deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ApkCleanupTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var env: TestEnvironment

    @BeforeTest
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment(prefsName = "apk_cleanup_test")
    }

    @AfterTest
    fun tearDown() = env.close()

    private val release = file("NormPix-1.5.12-universal.apk", 12_582_912)
    private val stranger = file("holiday-photos.apk", 3_200_000)

    private fun file(name: String, size: Long?, canDelete: Boolean? = null) =
        PickedApk(Uri.parse("content://downloads/document/${name.hashCode()}"), name, size, canDelete)

    /** The files on the "phone": [describe] knows them by their address, [delete] forgets them unless it is told one will fail. */
    private class FakeStore(val files: List<PickedApk>, val failing: Set<Uri> = emptySet()) : ApkFileStore {
        val deleted = mutableListOf<Uri>()
        val asked = mutableListOf<Uri>()

        override suspend fun describe(uris: List<Uri>): List<PickedApk> = uris.map { uri -> files.first { it.uri == uri } }

        override suspend fun delete(file: PickedApk): Boolean {
            asked += file.uri
            if (file.uri in failing) return false
            deleted += file.uri
            return true
        }
    }

    /** The system's document picker, as the user using it: every launch is answered at once with [picks]. */
    private class FakePicker(var picks: List<Uri>) : ActivityResultRegistryOwner {
        val launches = mutableListOf<Pair<ActivityResultContract<*, *>, Any?>>()

        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                launches += contract to input
                @Suppress("UNCHECKED_CAST")
                dispatchResult(requestCode, picks as O)
            }
        }
    }

    private fun show(store: ApkFileStore, picker: FakePicker) {
        compose.setContent {
            env.Content {
                CompositionLocalProvider(LocalApkFileStore provides store, LocalActivityResultRegistryOwner provides picker) {
                    UpdatesSettingsPage()
                }
            }
        }
    }

    private fun tapRow() {
        compose.onNodeWithText("Delete downloaded APK files").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun present(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(10_000) {
        compose.waitForIdle()
        present(text, substring)
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `the row is on the page, with what it is for and why it asks you to pick`() {
        show(FakeStore(emptyList()), FakePicker(emptyList()))
        compose.onNodeWithText("Update files").assertIsDisplayed()
        compose.onNodeWithText("Delete downloaded APK files").assertIsDisplayed()
        compose.onNodeWithText("Pick NormPix files left in Downloads after an update").assertIsDisplayed()
        compose.onNodeWithText(
            "Updates NormPix downloads itself are deleted once they're installed. A file your browser saved stays in Downloads " +
                "until it's deleted; NormPix only touches the files you pick.",
        ).assertIsDisplayed()
    }

    @Test
    fun `the row opens the picker for several APK files, and says it is going to another screen`() {
        val picker = FakePicker(emptyList())
        show(FakeStore(emptyList()), picker)
        // Locked right away when put in the background, unless NormPix itself sent the user away.
        env.preferences.setWalletLockEnabled(true)
        env.preferences.setAppLockScope(AppLockScope.EntireApp)
        env.preferences.setAppLockTimeout(AppLockTimeout.Immediate)
        compose.waitForIdle()
        val lock = env.container.lock
        val prefs = env.preferences.state.value

        lock.unlockAll()
        lock.onAppPaused(prefs)
        assertTrue(lock.isAppLocked(prefs), "without the row, going to the background locks the app")

        lock.unlockAll()
        tapRow()
        assertEquals(1, picker.launches.size)
        val (contract, input) = picker.launches.single()
        assertTrue(contract is ActivityResultContracts.OpenMultipleDocuments)
        @Suppress("UNCHECKED_CAST")
        val intent = (contract as ActivityResultContracts.OpenMultipleDocuments).createIntent(env.context, input as Array<String>)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals(listOf(APK_MIME_TYPE), intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList())
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false), "more than one file can be picked")
        assertFalse(intent.hasExtra(DocumentsContract.EXTRA_INITIAL_URI), "no folder is asked for")
        lock.onAppPaused(prefs)
        assertFalse(lock.isAppLocked(prefs), "the picker is not leaving NormPix")
    }

    @Test
    fun `picking nothing changes nothing and asks nothing`() {
        val store = FakeStore(listOf(release))
        show(store, FakePicker(emptyList()))
        tapRow()
        assertFalse(present("Delete downloaded APK files?"))
        assertTrue(store.deleted.isEmpty())
    }

    @Test
    fun `the sheet lists each file with its size, and notes the one not named like an update`() {
        val store = FakeStore(listOf(release, stranger))
        show(store, FakePicker(listOf(release.uri, stranger.uri)))
        tapRow()
        waitFor("Delete downloaded APK files?")

        compose.onNodeWithText("NormPix-1.5.12-universal.apk").assertIsDisplayed()
        compose.onNodeWithText("holiday-photos.apk").assertIsDisplayed()
        compose.onNodeWithText(Formatter.formatShortFileSize(env.context, 12_582_912), substring = true).assertIsDisplayed()
        compose.onNodeWithText(Formatter.formatShortFileSize(env.context, 3_200_000), substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Not named like a NormPix update", substring = true).assertCountEquals(1)
        compose.onNodeWithText("Delete 2 files").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        assertTrue(store.deleted.isEmpty(), "nothing goes before the user says so")
    }

    @Test
    fun `Delete removes every picked file, and the page then says how many`() {
        val store = FakeStore(listOf(release, stranger))
        show(store, FakePicker(listOf(release.uri, stranger.uri)))
        tapRow()
        waitFor("Delete 2 files")
        tap("Delete 2 files")

        waitFor("Deleted 2 files")
        assertEquals(listOf(release.uri, stranger.uri), store.deleted)
        assertFalse(present("Delete downloaded APK files?"), "the sheet has closed")
    }

    @Test
    fun `a single file is said in the singular`() {
        val store = FakeStore(listOf(release))
        show(store, FakePicker(listOf(release.uri)))
        tapRow()
        waitFor("Delete 1 file")
        tap("Delete 1 file")
        waitFor("Deleted 1 file")
        assertEquals(listOf(release.uri), store.deleted)
        assertEquals(0, compose.onAllNodesWithText("Not named like a NormPix update", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `Cancel deletes nothing and says nothing`() {
        val store = FakeStore(listOf(release, stranger))
        show(store, FakePicker(listOf(release.uri, stranger.uri)))
        tapRow()
        waitFor("Cancel")
        tap("Cancel")

        compose.waitUntil(10_000) {
            compose.waitForIdle()
            !present("Delete downloaded APK files?")
        }
        assertTrue(store.asked.isEmpty())
        assertFalse(present("Deleted", substring = true))
        assertFalse(present("Couldn't delete", substring = true))
    }

    @Test
    fun `a file that will not delete is said, and does not stop the others`() {
        val store = FakeStore(listOf(release, stranger), failing = setOf(release.uri))
        show(store, FakePicker(listOf(release.uri, stranger.uri)))
        tapRow()
        waitFor("Delete 2 files")
        tap("Delete 2 files")

        waitFor("Deleted 1 file; 1 couldn't be deleted")
        assertEquals(listOf(release.uri, stranger.uri), store.asked)
        assertEquals(listOf(stranger.uri), store.deleted)
    }

    @Test
    fun `when none will delete it says so`() {
        val store = FakeStore(listOf(release), failing = setOf(release.uri))
        show(store, FakePicker(listOf(release.uri)))
        tapRow()
        waitFor("Delete 1 file")
        tap("Delete 1 file")

        waitFor("Couldn't delete the file")
        assertTrue(store.deleted.isEmpty())
    }

    @Test
    fun `a file the provider says it cannot delete is listed, left out of the count and not tried`() {
        val locked = file("NormPix-1.5.11.apk", 11_000_000, canDelete = false)
        val store = FakeStore(listOf(release, locked))
        show(store, FakePicker(listOf(release.uri, locked.uri)))
        tapRow()
        waitFor("Delete 1 file")
        compose.onNodeWithText("NormPix-1.5.11.apk").assertIsDisplayed()
        compose.onNodeWithText("Can't be deleted from here", substring = true).assertIsDisplayed()
        tap("Delete 1 file")

        waitFor("Deleted 1 file; 1 couldn't be deleted")
        assertEquals(listOf(release.uri), store.asked, "the one that can't be is not tried")
    }

    @Test
    fun `when none can be deleted from here an alert says so instead of a sheet that does nothing`() {
        val locked = file("NormPix-1.5.11.apk", 11_000_000, canDelete = false)
        val store = FakeStore(listOf(locked))
        show(store, FakePicker(listOf(locked.uri)))
        tapRow()
        waitFor("Can't delete from here")
        compose.onNodeWithText("Android doesn't let NormPix delete this file. Delete it in the Files app instead.").assertIsDisplayed()
        assertFalse(present("Delete downloaded APK files?"))
        tap("OK")
        assertTrue(store.asked.isEmpty())
    }

    @Test
    fun `a file whose size the provider did not give is still listed`() {
        val unknown = file("NormPix-1.5.10.apk", size = null)
        val store = FakeStore(listOf(unknown))
        show(store, FakePicker(listOf(unknown.uri)))
        tapRow()
        waitFor("Delete 1 file")
        compose.onNodeWithText("NormPix-1.5.10.apk").assertIsDisplayed()
        tap("Delete 1 file")
        waitFor("Deleted 1 file")
    }
}
