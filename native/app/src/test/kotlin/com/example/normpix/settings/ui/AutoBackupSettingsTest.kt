package com.example.normpix.settings.ui

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.backup.AutoBackupNames
import com.example.normpix.backup.AutoBackupProblem
import com.example.normpix.backup.AutoBackupService
import com.example.normpix.backup.ContainerBackupData
import com.example.normpix.backup.FakeBackupFolders
import com.example.normpix.backup.LockedFile
import com.example.normpix.backup.SealedSecret
import com.example.normpix.notifications.AppNotification
import com.example.normpix.notifications.NotificationKind
import com.example.normpix.notifications.notificationItems
import com.example.normpix.settings.AutoBackupEvery
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.VaultKeyProvider
import java.io.File
import java.nio.file.Files
import java.util.Locale
import javax.crypto.KeyGenerator
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings > Data & backup > Automatic backup, as the user goes through it: the switch, the turn-on steps (what it does, the
 * folder, the password twice), the rows while it is on, and turning it off - with the real sheets, the page's own folder picker
 * (answered by a stand-in for Android's), an in-memory folder and a plain AES key in place of the Keystore's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class AutoBackupSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Android's folder picker, as the user using it: answers each launch at once with [answer] (null: backed out). */
    private class FakeFolderPicker(var answer: String?) : ActivityResultRegistryOwner {
        val launches = mutableListOf<ActivityResultContract<*, *>>()

        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                launches += contract
                @Suppress("UNCHECKED_CAST")
                dispatchResult(requestCode, answer?.let(Uri::parse) as O)
            }
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val tree = "content://com.android.externalstorage.documents/tree/primary%3ABackups"
    private val password = "backup password 1"
    private val folders = FakeBackupFolders()
    private val picker = FakeFolderPicker(tree)
    private lateinit var dir: File
    private lateinit var secret: SealedSecret
    private lateinit var env: TestEnvironment
    private val service get() = env.container.autoBackup!!
    private val prefs get() = env.preferences.state.value

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        dir = Files.createTempDirectory("auto-backup-settings").toFile()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        secret = SealedSecret(File(dir, AutoBackupService.FILE_NAME), VaultKeyProvider { key })
        env = TestEnvironment("auto_backup_settings_test") { preferences, container ->
            AutoBackupService(preferences, secret, folders, { ContainerBackupData(context, container()) }, iterations = 10_000)
        }
    }

    @After
    fun tearDown() {
        env.close()
        dir.deleteRecursively()
    }

    private fun open() {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                env.Content { DataSettingsPage() }
            }
        }
        compose.waitForIdle()
    }

    private fun shown(text: String, substring: Boolean = false) = compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(20_000) { shown(text, substring) }

    // Work done off the main thread resumes on it; nothing here reads a node, so the looper is idled by hand.
    private fun until(condition: () -> Boolean) = compose.waitUntil(20_000) {
        compose.waitForIdle()
        condition()
    }

    private fun tapSwitch() {
        compose.onNodeWithText("Back up automatically").performScrollTo().performClick()
    }

    private fun typePasswords(first: String, again: String) {
        compose.onNodeWithText("Password").performTextClearance()
        compose.onNodeWithText("Password").performTextInput(first)
        compose.onNodeWithText("Password again").performTextClearance()
        compose.onNodeWithText("Password again").performTextInput(again)
        compose.onNodeWithText("Turn on").performClick()
        compose.waitForIdle()
    }

    private fun turnedOnAlready() = runBlocking { service.turnOn(tree, "Backups", password.toCharArray()) }

    @Test
    fun `off, the section is the switch alone and says where backups go and what a cloud folder may do`() {
        open()
        compose.onNodeWithText("Back up automatically").performScrollTo()
        assertTrue(shown("A locked backup of everything, made by itself"))
        assertTrue(shown("that app may upload the locked file", substring = true))
        assertFalse(shown("Back up now"))
        assertFalse(shown("Change password"))
    }

    @Test
    fun `turning on explains, asks for the folder and the password twice, refuses a short or mismatched one, then makes the first backup`() {
        open()
        tapSwitch()
        waitFor("Choose a folder")
        assertTrue(shown("NormPix can't recover it", substring = true), "the password can't be recovered, and it is said")
        compose.onNodeWithText("Choose a folder").performClick()

        waitFor("Set the backup password")
        assertEquals(1, picker.launches.size)
        assertTrue(picker.launches.single() is ActivityResultContracts.OpenDocumentTree)
        assertTrue(shown("Backups go to Backups"))

        typePasswords("short", "short")
        waitFor("Use at least ${LockedFile.MIN_PASSWORD} characters")
        assertFalse(prefs.autoBackupEnabled)

        typePasswords(password, "$password!")
        waitFor("The two passwords don't match")
        assertFalse(prefs.autoBackupEnabled)
        assertFalse(secret.exists)

        typePasswords(password, password)
        waitFor("Automatic backup is on. The first backup is in Backups.")
        assertTrue(prefs.autoBackupEnabled)
        assertEquals(tree, prefs.autoBackupFolder)
        assertEquals("Backups", prefs.autoBackupFolderName)
        assertEquals(listOf(tree), folders.kept)
        assertContentEquals(password.toByteArray(), secret.open())
        val first = folders.folder.files.values.single()
        assertEquals(first.name, prefs.autoBackupLastFile)
        assertTrue(LockedFile.isLocked(first.bytes.toByteArray()))

        for (row in listOf("Last automatic backup", "Folder", "How often", "Include saved cards", "Change password", "Back up now")) {
            compose.onNodeWithText(row).performScrollTo()
        }
        assertTrue(shown("Weekly, into Backups"))
        assertTrue(shown("the newest 5 are kept and older ones are deleted", substring = true))
    }

    @Test
    fun `backing out of the folder picker turns nothing on`() {
        picker.answer = null
        open()
        tapSwitch()
        waitFor("Choose a folder")
        compose.onNodeWithText("Choose a folder").performClick()
        until { picker.launches.isNotEmpty() }
        compose.waitForIdle()
        assertFalse(shown("Set the backup password"))
        assertFalse(prefs.autoBackupEnabled)
        assertFalse(secret.exists)
        assertTrue(folders.kept.isEmpty())
    }

    @Test
    fun `turning off asks first, then deletes the password and gives the folder back`() {
        turnedOnAlready()
        open()
        tapSwitch()
        waitFor("Turn off automatic backup?")
        compose.onNodeWithText("Turn off").performClick()
        waitFor("Automatic backup is off. The backups already made stay in Backups.")
        assertFalse(prefs.autoBackupEnabled)
        assertFalse(secret.exists)
        assertFalse(File(dir, AutoBackupService.FILE_NAME).exists())
        assertEquals(listOf(tree), folders.released)
        assertFalse(shown("Back up now"))
    }

    @Test
    fun `back up now makes one and says its name`() {
        turnedOnAlready()
        open()
        compose.onNodeWithText("Back up now").performScrollTo().performClick()
        waitFor("Backup saved: NormPix-auto-backup-", substring = true)
        val made = folders.folder.files.values.single()
        assertTrue(AutoBackupNames.isAutoBackupName(made.name))
        assertEquals(made.name, prefs.autoBackupLastFile)
        // Said twice: in the snackbar, and on the page's "Last automatic backup" line.
        compose.onNodeWithText("Backup saved: ${made.name}").assertExists()
        compose.onNodeWithText("· ${made.name}", substring = true).performScrollTo()
    }

    @Test
    fun `how often is picked from a sheet`() {
        turnedOnAlready()
        open()
        compose.onNodeWithText("How often").performScrollTo().performClick()
        waitFor("Every 30 days")
        compose.onNodeWithText("Monthly").performClick()
        until { prefs.autoBackupEvery == AutoBackupEvery.Monthly }
    }

    @Test
    fun `including saved cards asks first, and the row is gone while Store cards is off`() {
        turnedOnAlready()
        open()
        compose.onNodeWithText("Include saved cards").performScrollTo().performClick()
        waitFor("Include saved cards?")
        compose.onNodeWithText("Include").performClick()
        until { prefs.autoBackupCards }
        compose.onNodeWithText("Locked with the automatic backup password").performScrollTo()

        compose.onNodeWithText("Include saved cards").performScrollTo().performClick()
        until { !prefs.autoBackupCards }

        compose.runOnIdle { env.preferences.setWalletCardsEnabled(false) }
        compose.waitForIdle()
        assertFalse(shown("Include saved cards"))
    }

    @Test
    fun `a problem is said on the page and in the notifications under Backup`() {
        turnedOnAlready()
        env.preferences.recordAutoBackupProblem(AutoBackupProblem.FolderGone, System.currentTimeMillis())
        var items: List<AppNotification> = emptyList()
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                env.Content {
                    DataSettingsPage()
                    val gathered = notificationItems(null)
                    SideEffect { items = gathered }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Choose the folder again").performScrollTo()
        assertTrue(shown("Tap Folder to choose it again.", substring = true))
        val backup = items.filter { it.kind == NotificationKind.Backup }
        assertEquals(1, backup.size)
        assertEquals("Choose the folder again", backup.single().banner.title)
        assertEquals("Automatic backup", backup.single().banner.eyebrow)
    }
}
