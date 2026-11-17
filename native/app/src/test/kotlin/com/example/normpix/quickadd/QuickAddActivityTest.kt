package com.example.normpix.quickadd

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.AppContainer
import com.example.normpix.BuildConfig
import com.example.normpix.MainActivity
import com.example.normpix.R
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.PictureText
import com.example.normpix.finance.share.SharedPictureReader
import com.example.normpix.finance.ui.CATEGORY_PICKER_TAG
import com.example.normpix.finance.ui.FinanceStatus
import com.example.normpix.settings.AppLockScope
import com.example.normpix.settings.AppLockTimeout
import com.example.normpix.testing.RecordingAuthenticator
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.Authenticator
import com.example.normpix.widgets.WidgetIntents
import com.example.normpix.widgets.WidgetRequest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Stands in for the main screen where a test needs one: holds no UI, only the places the services keep their activity. */
class StandInMainActivity : FragmentActivity()

/**
 * The quick add overlay as an activity: what a shortcut, a share or a tile start. It is built on a test environment whose ledger is
 * not loaded (a process where the main screen never ran), so what it needs it loads itself; the prompt is the test's.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickAddActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private var env = TestEnvironment(prefsName = "quick_add_activity_test", preload = false)
    private val prompt = RecordingAuthenticator()

    private val originalContainer = QuickAddActivity.containerOf
    private val originalAuthenticator = QuickAddActivity.authenticatorOf

    @Before
    fun setUp() {
        useEnvironment(env)
    }

    @After
    fun tearDown() {
        QuickAddActivity.containerOf = originalContainer
        QuickAddActivity.authenticatorOf = originalAuthenticator
        env.close()
        File(env.context.cacheDir, "picture-drafts").deleteRecursively()
        File(env.context.filesDir, "pictures").deleteRecursively()
    }

    private fun useEnvironment(next: TestEnvironment) {
        env = next
        val container: AppContainer = next.container
        QuickAddActivity.containerOf = { container }
        QuickAddActivity.authenticatorOf = { prompt }
    }

    private fun addIntent(type: TransactionType): Intent = WidgetIntents.intent(app, WidgetRequest.Add(type))

    private val hdfc = "Sent Rs.250.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 10/10/26\nRef 428312345678\n" +
        "Not You?\nCall 18002586161/SMS BLOCK UPI to 7308080808"

    /** A share as ShareTransactionActivity hands it over (with its mark). */
    private fun shareIntent(text: String?, picture: Uri? = null): Intent =
        Intent(Intent.ACTION_SEND).setClass(app, QuickAddActivity::class.java).putExtra(EXTRA_HANDED_OVER, true).apply {
            if (text != null) putExtra(Intent.EXTRA_TEXT, text)
            if (picture == null) {
                setType("text/plain")
            } else {
                setType("image/png").putExtra(Intent.EXTRA_STREAM, picture)
            }
        }

    private fun start(intent: Intent) = Robolectric.buildActivity(QuickAddActivity::class.java, intent).setup()

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(timeoutMillis = 10_000) { shown(text) }

    private fun waitGone(text: String) = compose.waitUntil(timeoutMillis = 10_000) { !shown(text) }

    private fun dismissSheet() =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss)

    // ------------------------------------------------------------------------ a shortcut

    @Test
    fun `a shortcut opens the sheet on its type, loads the ledger it finds unloaded and does not open the main screen`() {
        assertEquals(FinanceStatus.Loading, env.container.finance.state.value.status, "no main screen has run")
        for ((type, title) in listOf(
            TransactionType.Income to "New income",
            TransactionType.Expense to "New expense",
            TransactionType.Transfer to "New transfer",
        )) {
            val controller = start(addIntent(type))
            waitFor(title)
            assertEquals(FinanceStatus.Ready, env.container.finance.state.value.status)
            assertNull(shadowOf(controller.get()).nextStartedActivity, "the main screen is not started")
            assertNull(shadowOf(app).nextStartedActivity)
            controller.pause().stop().destroy()
            compose.waitForIdle()
        }
    }

    @Test
    fun `the ledger is loaded once when the main screen has loaded it already`() {
        runBlockingLoad()
        val revision = env.container.finance.state.value.revision
        val controller = start(addIntent(TransactionType.Expense))
        waitFor("New expense")
        assertEquals(revision, env.container.finance.state.value.revision, "nothing was read again")
        controller.pause().stop().destroy()
    }

    private fun runBlockingLoad() = kotlinx.coroutines.runBlocking { env.container.finance.ensureLoaded() }

    // --------------------------------------------------------------------------- a share

    @Test
    fun `a shared text opens the form filled in, over the sharing app, with no main screen`() {
        val controller = start(shareIntent(hdfc))
        waitFor("Filled in from shared text")
        compose.onNodeWithContentDescription("Amount in INR").assert(hasText("250"))
        compose.onNodeWithText("SWIGGY").assertExists()
        assertNull(shadowOf(controller.get()).nextStartedActivity)
        assertNull(shadowOf(app).nextStartedActivity)
        // Nothing is saved until the user saves.
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }

    /** Stands in for the on-device recognizer: what it "reads" in any picture, and what it was asked. */
    private class FakePictures(private val text: String) : SharedPictureReader {
        val asked = mutableListOf<Uri>()

        override suspend fun read(picture: Uri): PictureText? {
            asked += picture
            return PictureText(text)
        }
    }

    @Test
    fun `a shared picture is read by the app's reader and its staged copy is attached`() {
        val reader = FakePictures("To SWIGGY\n₹250\nCompleted\n10 Oct 2026, 12:34 pm\nUPI transaction ID\n428312345678")
        env.close()
        useEnvironment(TestEnvironment(prefsName = "quick_add_activity_test", pictures = reader, preload = false))
        val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val picture = Uri.parse("content://com.example.payments.files/receipts/receipt.png")
        shadowOf(env.context.contentResolver).registerInputStreamSupplier(picture) { ByteArrayInputStream(bytes) }

        val controller = start(shareIntent(null, picture))
        waitFor("Filled in from a shared picture")
        assertEquals(listOf(picture), reader.asked)
        compose.onNodeWithContentDescription("Amount in INR").assert(hasText("250"))
        // The copy staged at once is attached to the form...
        compose.onNodeWithText("Attached").performScrollTo().assertExists()
        val drafts = File(env.context.cacheDir, "picture-drafts")
        assertTrue(drafts.listFiles().orEmpty().isNotEmpty(), "a staged copy is there")
        assertNull(shadowOf(controller.get()).nextStartedActivity)

        // ... and goes with the draft when the user lets it go.
        dismissSheet()
        waitFor("Discard this entry?")
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { controller.get().isFinishing }
        compose.waitUntil(timeoutMillis = 10_000) { drafts.listFiles().orEmpty().isEmpty() }
        controller.pause().stop().destroy()
    }

    @Test
    fun `a second request while one is open waits behind it`() {
        val controller = start(addIntent(TransactionType.Expense))
        waitFor("New expense")
        compose.onNodeWithContentDescription("Amount in INR").performTextInput("75")

        controller.newIntent(shareIntent(hdfc))
        compose.waitForIdle()
        val activity = controller.get()
        // The first draft is still there; the share is queued, not shown and not lost.
        assertTrue(shown("New expense"))
        assertFalse(shown("Filled in from shared text"))
        assertEquals(1, activity.requests.waiting.value.size)
        assertFalse(activity.isFinishing)

        dismissSheet()
        waitFor("Discard this entry?")
        compose.onNodeWithText("Discard").performClick()
        waitFor("Filled in from shared text")
        assertTrue(activity.requests.waiting.value.isEmpty())
        controller.pause().stop().destroy()
    }

    // ---------------------------------------------------------------------- finishing

    @Test
    fun `dismissing the sheet finishes the activity at once`() {
        val controller = start(addIntent(TransactionType.Expense))
        waitFor("New expense")
        assertFalse(controller.get().isFinishing)
        dismissSheet()
        waitGone("New expense")
        compose.waitUntil(timeoutMillis = 3_000) { controller.get().isFinishing }
        controller.pause().stop().destroy()
    }

    @Test
    fun `saving finishes the activity once the snackbar the save said has gone`() {
        val controller = start(addIntent(TransactionType.Expense))
        waitFor("New expense")
        compose.onNodeWithContentDescription("Amount in INR").performTextInput("120")
        compose.onNodeWithText("Category").performScrollTo().performClick()
        compose.onNodeWithTag(CATEGORY_PICKER_TAG).performScrollToNode(hasText("Food & dining"))
        compose.onNodeWithText("Food & dining").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Save expense").performClick()
        waitGone("New expense")
        waitFor("Expense of ₹120.00 saved")
        assertFalse(controller.get().isFinishing, "the snackbar is still up")

        compose.waitUntil(timeoutMillis = 15_000) { controller.get().isFinishing }
        controller.pause().stop().destroy()
    }

    @Test
    fun `an intent with nothing to do, or one restored after the process went, finishes at once`() {
        val nothing = Robolectric.buildActivity(QuickAddActivity::class.java, Intent().setClass(app, QuickAddActivity::class.java)).setup()
        assertTrue(nothing.get().isFinishing)

        // Whether there is nothing to do is decided as it resumes (a request may still come through onNewIntent before that).
        val restored = Robolectric.buildActivity(QuickAddActivity::class.java, addIntent(TransactionType.Expense))
            .create(android.os.Bundle()).start().resume()
        assertTrue(restored.get().isFinishing, "what was open is gone with the process")

        val fromRecents = Robolectric.buildActivity(
            QuickAddActivity::class.java,
            addIntent(TransactionType.Expense).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY),
        ).create().start().resume()
        assertTrue(fromRecents.get().isFinishing, "the request was done when the shortcut was pressed")
    }

    @Test
    fun `a task brought back after the process went shows the new request, not the old emptied one`() {
        // As after an update with the overlay left open: restarted with its first intent, extras gone; the new share follows.
        val emptied = Intent(Intent.ACTION_SEND).setClass(app, QuickAddActivity::class.java).setType("text/plain")
        val controller = Robolectric.buildActivity(QuickAddActivity::class.java, emptied).create().start()
        controller.newIntent(shareIntent(hdfc))
        controller.resume()
        assertFalse(controller.get().isFinishing, "the new share is shown")
        val requests = controller.get().requests
        assertFalse(requests.isEmpty, "the new share is there to do")
        assertTrue(requests.waiting.value.size <= 1, "and only it: the emptied intent asked for nothing")
        controller.pause().stop().destroy()
    }

    // ---------------------------------------------------------------------------- the window

    @Test
    fun `the window is secure exactly when the main screen's is`() {
        val controller = start(addIntent(TransactionType.Expense))
        val secure = (controller.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        assertEquals(BuildConfig.SECURE_WINDOW, secure)
        controller.pause().stop().destroy()
    }

    @Test
    fun `it is declared as a see-through task of its own that the launcher and the system can start but no other app`() {
        val packages = app.packageManager
        val overlay = packages.getActivityInfo(ComponentName(app, QuickAddActivity::class.java), 0)
        val main = packages.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
        assertFalse(overlay.exported)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, overlay.launchMode)
        // An affinity of its own: never the main screen's task, nor the empty one the reminder alert and the widget settings share.
        assertEquals("${app.packageName}.quickadd", overlay.taskAffinity)
        assertNotEquals(main.taskAffinity, overlay.taskAffinity)
        // (Robolectric does not carry the recents flag into ActivityInfo, so it is read where it is written.)
        val declared = File("src/main/AndroidManifest.xml").readText().substringAfter("android:name=\".quickadd.QuickAddActivity\"").substringBefore("/>")
        assertTrue("android:excludeFromRecents=\"true\"" in declared, "never in Recents")
        assertTrue("android:taskAffinity=\"\${applicationId}.quickadd\"" in declared, "its own affinity is written in the manifest")
        assertEquals(R.style.Theme_NormPix_Overlay, overlay.theme)
        assertEquals(main.configChanges, overlay.configChanges, "turning the phone keeps the draft, as in the app")
        assertEquals(main.softInputMode, overlay.softInputMode)
    }

    @Test
    fun `the overlay's theme is the app's, made see-through, in every variant the app's has`() {
        val res = File("src/main/res")
        val base = File(res, "values/themes.xml").readText()
        val overlay = base.substringAfter("<style name=\"Theme.NormPix.Overlay\">").substringBefore("</style>")
        // No parent of its own: it is the app's window theme (Theme.NormPix, which the dark and Android 12 variants define), changed.
        assertTrue("windowIsTranslucent\">true" in overlay)
        assertTrue("windowBackground\">@android:color/transparent" in overlay)
        assertTrue("backgroundDimEnabled\">false" in overlay)
        assertTrue("windowAnimationStyle\">@null" in overlay)
        for (variant in listOf("values", "values-night", "values-v31", "values-night-v31")) {
            assertTrue("<style name=\"Theme.NormPix\"" in File(res, "$variant/themes.xml").readText(), "the window theme in $variant")
        }
        // Android 12 and later would draw the cookie's launch screen over the other app for a cold start: not for the overlay.
        val v31 = File(res, "values-v31/themes.xml").readText()
        val overlayV31 = v31.substringAfter("<style name=\"Theme.NormPix.Overlay\">").substringBefore("</style>")
        assertTrue("windowIsTranslucent\">true" in overlayV31)
        assertTrue("windowSplashScreenAnimatedIcon\">@drawable/splash_icon_empty" in overlayV31)
    }

    // ------------------------------------------------------------------------- the lock

    private fun lockTheApp(timeout: AppLockTimeout = AppLockTimeout.Immediate) {
        env.preferences.setWalletLockEnabled(true)
        env.preferences.setAppLockScope(AppLockScope.EntireApp)
        env.preferences.setAppLockTimeout(timeout)
    }

    @Test
    fun `a locked app asks for the prompt over the other app first, and finishes with nothing shown when it is refused`() {
        lockTheApp()
        prompt.succeed = false
        val controller = start(addIntent(TransactionType.Expense))
        compose.waitUntil(timeoutMillis = 10_000) { controller.get().isFinishing }

        assertEquals(1, prompt.asked)
        assertFalse(shown("New expense"))
        // Nothing of the ledger was composed, or even read.
        assertEquals(FinanceStatus.Loading, env.container.finance.state.value.status)
        controller.pause().stop().destroy()
    }

    @Test
    fun `a locked app opens the sheet once the prompt is passed, and locks again when it is left`() {
        lockTheApp()
        val controller = start(addIntent(TransactionType.Income))
        waitFor("New income")
        assertEquals(1, prompt.asked)
        assertFalse(env.container.lock.isAppLocked(env.preferences.state.value))

        // The overlay tells the lock it was left as the main screen does: with the timeout "immediately" the app locks.
        controller.pause().stop()
        assertTrue(env.container.lock.isAppLocked(env.preferences.state.value))
        controller.destroy()
    }

    // ----------------------------------------------------------- what it borrows from the main screen

    @Test
    fun `it takes the activity and the prompt from the main screen and gives them back when it goes`() {
        val main = Robolectric.buildActivity(StandInMainActivity::class.java).setup().get()
        val mainPrompt: Authenticator = RecordingAuthenticator()
        env.container.host.activity = main
        env.container.lock.authenticator = mainPrompt

        val controller = start(addIntent(TransactionType.Expense))
        assertSame(controller.get(), env.container.host.activity)
        assertSame(prompt, env.container.lock.authenticator)
        // `visible` is the MAIN screen's: the NFC outlet hands a card readied there only while it is in front, never while the overlay is.
        assertFalse(env.container.host.visible, "the overlay does not count as the main screen in front")

        controller.pause().stop().destroy()
        assertSame(main, env.container.host.activity, "NFC, the updater and the lock keep working in the app")
        assertSame(mainPrompt, env.container.lock.authenticator)
        assertFalse(env.container.host.visible, "the main screen was not in front, and is not now")
    }

    @Test
    fun `a main screen that has gone is not given back, and one opened meanwhile keeps what it took`() {
        val gone = Robolectric.buildActivity(StandInMainActivity::class.java).setup()
        env.container.host.activity = gone.get()
        env.container.lock.authenticator = RecordingAuthenticator()
        val first = start(addIntent(TransactionType.Expense))
        gone.pause().stop().destroy()
        first.pause().stop().destroy()
        assertNull(env.container.host.activity)
        assertNull(env.container.lock.authenticator)

        val opened = Robolectric.buildActivity(StandInMainActivity::class.java).setup().get()
        val openedPrompt = RecordingAuthenticator()
        val second = start(addIntent(TransactionType.Expense))
        env.container.host.activity = opened
        env.container.lock.authenticator = openedPrompt
        second.pause().stop().destroy()
        assertSame(opened, env.container.host.activity)
        assertSame(openedPrompt, env.container.lock.authenticator)
        assertNotNull(opened)
    }
}
