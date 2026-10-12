package com.example.normpix.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.example.normpix.settings.ui.PHOTO_FRAME_TAG
import com.example.normpix.settings.ui.ProfilePage
import com.example.normpix.settings.ui.SettingsScreen
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.AppPage
import com.example.normpix.ui.components.LocalWindowBlurOverride
import com.example.normpix.ui.components.PROFILE_PHOTO_TAG
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The user's own photo through the real Profile page: chosen in the system's photo picker, framed in a sheet, kept as a small
 * square copy, worn by the page, the Settings row and the account button - and removable, which brings the emoji or initial
 * back as they were left.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfilePhotoFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var env: TestEnvironment
    private val photoDir get() = File(env.context.filesDir, ProfilePhotos.DIR)
    private val state get() = env.preferences.state.value
    private var served = 0

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("profile_photo_flow")
        photoDir.deleteRecursively()
    }

    @After
    fun tearDown() {
        env.close()
        photoDir.deleteRecursively()
    }

    private fun show() {
        compose.setContent { env.Content { ProfilePage() } }
        compose.waitForIdle()
    }

    /** A picture in another app (the gallery), as the system's photo picker hands it over. */
    private fun galleryPicture(width: Int = 1200, height: Int = 800, bytes: ByteArray? = null, split: Boolean = false): Uri {
        val data = bytes ?: run {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(if (split) Color.RED else Color.BLUE) }
            // A split picture is red on its left half and blue on its right, so where the frame sat shows in what was kept.
            if (split) for (x in width / 2 until width) for (y in 0 until height) bitmap.setPixel(x, y, Color.BLUE)
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
        val uri = Uri.parse("content://com.example.gallery/me-${served++}")
        shadowOf(env.context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(data) }
        return uri
    }

    /** Presses "Choose a photo" (or "Change photo") and answers the picker with [picture], or with a cancel when it is null. */
    private fun pick(picture: Uri?, button: String = "Choose a photo") {
        compose.onNodeWithText(button).performClick()
        compose.waitForIdle()
        val activity = shadowOf(compose.activity)
        val started = assertNotNull(activity.nextStartedActivityForResult, "the photo picker was opened")
        if (picture != null) {
            activity.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(picture))
        } else {
            activity.receiveResult(started.intent, Activity.RESULT_CANCELED, null)
        }
        compose.waitForIdle()
    }

    private fun waitFor(text: String) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    /**
     * Waits for [condition] about state outside the screen (the preferences, the files). Each poll lets the main thread catch up
     * first: work that finished on another thread resumes there, and nothing here would otherwise let it.
     */
    private fun until(condition: () -> Boolean) = compose.waitUntil(timeoutMillis = 10_000) {
        compose.waitForIdle()
        condition()
    }

    private fun waitForPhoto() {
        try {
            until { state.profilePhoto.isNotBlank() }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            // Say what the screen showed instead: a message on the page, a sheet still open.
            throw AssertionError("no photo was kept; the screen shows:\n" + compose.onRoot().printToString(maxDepth = 30), e)
        }
    }

    private fun savedFiles(): List<File> = photoDir.listFiles().orEmpty().toList()

    private fun wearsPhoto() = compose.onAllNodes(hasTestTag(PROFILE_PHOTO_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a page without a photo offers to choose one, and wears the initial`() {
        show()
        compose.onNodeWithText("Choose a photo").assertIsDisplayed()
        compose.onAllNodesWithText("Remove photo").assertCountEquals(0)
        compose.onNodeWithText("Initial").assertExists()
        compose.onNodeWithText("Emoji").assertExists()
        assertTrue(!wearsPhoto())
    }

    @Test
    fun `a photo is picked, framed and kept, and the page wears it`() {
        show()
        pick(galleryPicture())

        waitFor("Position your photo")
        compose.onNodeWithText("Use photo").performClick()
        waitForPhoto()

        val kept = savedFiles().single()
        assertEquals(state.profilePhoto, kept.name)
        val saved = assertNotNull(BitmapFactory.decodeFile(kept.path))
        assertEquals(saved.width, saved.height, "a square copy")
        assertEquals(ProfilePhotos.SIDE, saved.width, "the largest square of a 1200x800 picture, scaled down to the limit")

        waitFor("Change photo")
        compose.onNodeWithText("Remove photo").assertIsDisplayed()
        compose.waitUntil(10_000) { wearsPhoto() }
        compose.onAllNodesWithText("Initial").assertCountEquals(0)
        compose.onNodeWithText("Your photo is shown instead of an emoji or your initial. Remove it to go back to them.").assertExists()
        // It stays across a restart of the preferences.
        assertEquals(kept.name, AppPreferences(env.prefsStore).state.value.profilePhoto)
    }

    @Test
    fun `zooming in the frame keeps less of the photo`() {
        show()
        pick(galleryPicture())
        waitFor("Position your photo")

        compose.onNodeWithContentDescription("Zoom").performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        compose.waitForIdle()
        compose.onNodeWithText("Use photo").performClick()
        waitForPhoto()

        val saved = assertNotNull(BitmapFactory.decodeFile(savedFiles().single().path))
        assertEquals(200, saved.width, "a quarter of the 800 px short side")
    }

    @Test
    fun `the frame can be moved without touching, for a screen reader`() {
        show()
        pick(galleryPicture(split = true))
        waitFor("Position your photo")

        compose.onNode(hasTestTag(PHOTO_FRAME_TAG)).assertExists()
        val frame = compose.onNodeWithContentDescription("Photo preview. Drag to move it.").fetchSemanticsNode()
        val moveLeft = frame.config[SemanticsActions.CustomActions].first { it.label == "Move photo left" }
        compose.runOnUiThread { assertTrue(moveLeft.action()) }
        compose.waitForIdle()
        compose.onNodeWithText("Use photo").performClick()
        waitForPhoto()

        // Unmoved, the square is the middle 800 px of 1200, half red and half blue. Moved left by a tenth of itself (80 px)
        // it takes in more red: 55% of the way across is still red, which it would not be unmoved.
        val saved = assertNotNull(BitmapFactory.decodeFile(savedFiles().single().path))
        val sample = saved.getPixel((saved.width * 0.55f).toInt(), saved.height / 2)
        assertTrue(Color.red(sample) > 150 && Color.blue(sample) < 100, "red, because the frame moved left: ${Integer.toHexString(sample)}")
    }

    @Test
    fun `choosing another photo replaces the first and its file goes`() {
        show()
        pick(galleryPicture())
        waitFor("Position your photo")
        compose.onNodeWithText("Use photo").performClick()
        waitForPhoto()
        val first = state.profilePhoto
        waitFor("Change photo")

        pick(galleryPicture(900, 900), button = "Change photo")
        waitFor("Position your photo")
        compose.onNodeWithText("Use photo").performClick()
        until { state.profilePhoto.isNotBlank() && state.profilePhoto != first }

        until { savedFiles().size == 1 }
        assertEquals(state.profilePhoto, savedFiles().single().name)
    }

    @Test
    fun `a cancelled picker changes nothing`() {
        show()
        pick(null)
        compose.onAllNodesWithText("Position your photo").assertCountEquals(0)
        assertEquals("", state.profilePhoto)
        assertTrue(savedFiles().isEmpty())
    }

    @Test
    fun `something that is not a picture is said so, and nothing is kept`() {
        show()
        pick(galleryPicture(bytes = "not a picture".toByteArray()))
        waitFor("Couldn't open that picture. Try another one.")
        compose.onAllNodesWithText("Position your photo").assertCountEquals(0)
        assertEquals("", state.profilePhoto)
        assertTrue(savedFiles().isEmpty())
        compose.onNodeWithText("Choose a photo").assertIsDisplayed()
    }

    @Test
    fun `removing the photo brings back the emoji that was there before`() {
        env.preferences.setProfile("Ada", "🍕")
        show()
        pick(galleryPicture())
        waitFor("Position your photo")
        compose.onNodeWithText("Use photo").performClick()
        waitForPhoto()
        waitFor("Remove photo")
        assertEquals("🍕", state.profileEmoji, "the emoji is kept behind the photo")

        compose.onNodeWithText("Remove photo").performClick()
        until { state.profilePhoto.isEmpty() }
        until { savedFiles().isEmpty() }
        waitFor("Choose a photo")
        compose.onNodeWithText("Emoji").assertExists()
        assertEquals("🍕", state.profileEmoji)
        compose.waitUntil(10_000) { !wearsPhoto() }
    }

    @Test
    fun `changing the name while a photo is worn leaves the photo and the emoji alone`() {
        env.preferences.setProfile("Ada", "🍕")
        show()
        pick(galleryPicture())
        waitFor("Position your photo")
        compose.onNodeWithText("Use photo").performClick()
        waitForPhoto()
        val photo = state.profilePhoto

        env.preferences.setProfile("Grace", state.profileEmoji)
        compose.waitForIdle()
        assertEquals(photo, state.profilePhoto)
        assertEquals("🍕", state.profileEmoji)
    }

    // ---- where else it is worn ----

    private fun givenAPhotoOnDisk(): String {
        val name = runBlocking {
            val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            ProfilePhotos(env.context, clock = { 4_242L + served++ }).save(bitmap, PhotoCrop())
        }
        env.preferences.setProfilePhoto(name)
        return name
    }

    @Test
    fun `the Settings row wears the photo`() {
        givenAPhotoOnDisk()
        env.preferences.setProfile("Ada", "")
        compose.setContent { env.Content { SettingsScreen() } }
        compose.waitUntil(10_000) { wearsPhoto() }
    }

    @Test
    fun `without a photo the Settings row wears no photo`() {
        env.preferences.setProfile("Ada", "")
        compose.setContent { env.Content { SettingsScreen() } }
        compose.waitForIdle()
        compose.onNodeWithText("Ada").assertExists()
        assertTrue(!wearsPhoto())
    }

    @Test
    fun `a photo whose file is gone falls back to the initial, not an empty circle`() {
        env.preferences.setProfilePhoto("photo-777.jpg")
        env.preferences.setProfile("Ada", "")
        compose.setContent { env.Content { SettingsScreen() } }
        compose.waitForIdle()
        compose.onNodeWithText("Ada").assertExists()
        assertTrue(!wearsPhoto())
        assertNull(ProfilePhotos(env.context).file(state.profilePhoto))
    }

    @Test
    fun `the account button at the top of a tab wears the photo`() {
        givenAPhotoOnDisk()
        env.preferences.setBackButtonStyle(BackButtonStyle.TopFloatingCircle)
        compose.setContent {
            CompositionLocalProvider(LocalWindowBlurOverride provides true) {
                env.Content {
                    AppPage(title = "Insights", listState = rememberLazyListState()) {
                        item { Text("Row") }
                    }
                }
            }
        }
        compose.waitUntil(10_000) { wearsPhoto() }
    }
}
