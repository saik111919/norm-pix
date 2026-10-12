package com.example.normpix.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The window the user frames onto their photo: where it sits and how big it is for each zoom, how a drag and a pinch move
 * it, and that it can never leave the picture.
 */
class PhotoCropTest {
    private fun near(expected: Float, actual: Float, message: String = "") = assertTrue(abs(expected - actual) < 0.01f, "$message expected $expected but was $actual")

    @Test
    fun `unzoomed, the window is the largest square the picture has, in the middle`() {
        val landscape = PhotoCrop().window(400, 300)
        near(300f, landscape.side)
        near(50f, landscape.left)
        near(0f, landscape.top)

        val portrait = PhotoCrop().window(300, 400)
        near(300f, portrait.side)
        near(0f, portrait.left)
        near(50f, portrait.top)

        val square = PhotoCrop().window(500, 500)
        near(500f, square.side)
        near(0f, square.left)
        near(0f, square.top)
    }

    @Test
    fun `zooming in shrinks the window about the middle`() {
        val window = PhotoCrop(zoom = 2f).window(400, 300)
        near(150f, window.side)
        near(125f, window.left)
        near(75f, window.top)

        near(75f, PhotoCrop(zoom = 4f).window(400, 300).side)
    }

    @Test
    fun `the window cannot leave the picture`() {
        val corner = PhotoCrop(zoom = 2f, centerX = 0f, centerY = 0f).window(400, 300)
        near(0f, corner.left)
        near(0f, corner.top)

        val far = PhotoCrop(zoom = 2f, centerX = 1f, centerY = 1f).window(400, 300)
        near(400f - far.side, far.left)
        near(300f - far.side, far.top)

        val wild = PhotoCrop(zoom = 3f, centerX = -5f, centerY = 9f).fitted(400, 300)
        assertTrue(wild.centerX in 0f..1f && wild.centerY in 0f..1f)
    }

    @Test
    fun `the zoom stays between none and four times`() {
        assertEquals(PhotoCrop.MAX_ZOOM, PhotoCrop().zoomedTo(10f, 400, 300).zoom)
        assertEquals(PhotoCrop.MIN_ZOOM, PhotoCrop(zoom = 3f).zoomedTo(0.2f, 400, 300).zoom)
        assertEquals(2.5f, PhotoCrop().zoomedTo(2.5f, 400, 300).zoom)
    }

    @Test
    fun `zooming back out keeps the window inside the picture`() {
        val deep = PhotoCrop(zoom = 4f, centerX = 0.95f, centerY = 0.95f).fitted(400, 300)
        val out = deep.zoomedTo(1f, 400, 300).window(400, 300)
        assertTrue(out.left >= 0f && out.left + out.side <= 400f)
        assertTrue(out.top >= 0f && out.top + out.side <= 300f)
    }

    @Test
    fun `dragging moves the picture with the fingers, so the window the other way`() {
        // A 300 px view of a 400x300 picture at zoom 1: one view pixel is one picture pixel.
        val dragged = PhotoCrop().transformed(panX = 30f, panY = 0f, zoomBy = 1f, viewport = 300f, width = 400, height = 300)
        near(20f, dragged.window(400, 300).left, "the window moved 30 px left as the picture moved right")
        near(0f, dragged.window(400, 300).top)

        val back = dragged.transformed(panX = -60f, panY = 0f, zoomBy = 1f, viewport = 300f, width = 400, height = 300)
        near(80f, back.window(400, 300).left)
    }

    @Test
    fun `a drag counts for less the further in the picture is zoomed`() {
        // At zoom 2 the window is 150 px wide in a 300 px view: a 30 px drag is 15 picture pixels.
        val dragged = PhotoCrop(zoom = 2f).transformed(panX = 30f, panY = 0f, zoomBy = 1f, viewport = 300f, width = 400, height = 300)
        near(125f - 15f, dragged.window(400, 300).left)
    }

    @Test
    fun `a drag past the edge stops at the edge`() {
        val dragged = PhotoCrop().transformed(panX = 5_000f, panY = 5_000f, zoomBy = 1f, viewport = 300f, width = 400, height = 300)
        near(0f, dragged.window(400, 300).left)
        near(0f, dragged.window(400, 300).top)
    }

    @Test
    fun `a pinch multiplies the zoom`() {
        val pinched = PhotoCrop().transformed(0f, 0f, zoomBy = 2f, viewport = 300f, width = 400, height = 300)
        assertEquals(2f, pinched.zoom)
        assertEquals(PhotoCrop.MAX_ZOOM, pinched.transformed(0f, 0f, zoomBy = 5f, viewport = 300f, width = 400, height = 300).zoom)
        assertEquals(PhotoCrop.MIN_ZOOM, pinched.transformed(0f, 0f, zoomBy = 0.1f, viewport = 300f, width = 400, height = 300).zoom)
    }

    @Test
    fun `only the names a photo is saved under are names`() {
        assertTrue(isProfilePhotoName("photo-1.jpg"))
        assertTrue(isProfilePhotoName("photo-1760000000000.jpg"))
        for (bad in listOf(null, "", "photo-.jpg", "photo-1.png", "Photo-1.jpg", "photo-1.jpg.exe", "../photo-1.jpg", "dir/photo-1.jpg", "photo-1 .jpg", "pic-1.jpg")) {
            assertFalse(isProfilePhotoName(bad), "$bad")
        }
    }
}

/** The files behind the profile photo: a square copy of what was framed, in a folder of its own, one at a time. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfilePhotoStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dir = File(context.filesDir, ProfilePhotos.DIR)
    private var tick = 1_000L
    private val store = ProfilePhotos(context, clock = { tick++ })

    @BeforeTest
    fun setUp() {
        dir.deleteRecursively()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun solid(width: Int, height: Int, color: Int = Color.RED): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    /** Left half red, right half blue. */
    private fun halves(width: Int, height: Int): Bitmap = solid(width, height, Color.RED).also { bitmap ->
        for (x in width / 2 until width) for (y in 0 until height) bitmap.setPixel(x, y, Color.BLUE)
    }

    private fun decode(name: String): Bitmap = assertNotNull(BitmapFactory.decodeFile(File(dir, name).path))

    private fun save(source: Bitmap, crop: PhotoCrop = PhotoCrop()): String = runBlocking { store.save(source, crop) }

    private fun mostly(color: Int, actual: Int): Boolean =
        abs(Color.red(color) - Color.red(actual)) < 40 && abs(Color.green(color) - Color.green(actual)) < 40 && abs(Color.blue(color) - Color.blue(actual)) < 40

    @Test
    fun `a saved photo is a square JPEG in the profile folder under a valid name`() {
        val name = save(solid(500, 400))
        assertTrue(isProfilePhotoName(name))
        val file = assertNotNull(store.file(name))
        assertEquals(dir, file.parentFile)
        val saved = decode(name)
        assertEquals(saved.width, saved.height, "a square")
        assertEquals(400, saved.width, "the largest square of a 500x400 picture, not scaled up")
    }

    @Test
    fun `a big photo is scaled down to the limit`() {
        val saved = decode(save(solid(2_000, 1_500)))
        assertEquals(ProfilePhotos.SIDE, saved.width)
        assertEquals(ProfilePhotos.SIDE, saved.height)
    }

    @Test
    fun `a small photo is not scaled up`() {
        val saved = decode(save(solid(200, 300)))
        assertEquals(200, saved.width)
        assertEquals(200, saved.height)
    }

    @Test
    fun `what is kept is the part that was framed`() {
        val source = halves(1_000, 500)
        // Unzoomed the square is the middle 500 px: half red, half blue.
        val whole = decode(save(source))
        assertTrue(mostly(Color.RED, whole.getPixel(whole.width / 8, whole.height / 2)), "the left of the middle is red")
        assertTrue(mostly(Color.BLUE, whole.getPixel(whole.width * 7 / 8, whole.height / 2)), "the right of the middle is blue")

        // Zoomed in on the right, only blue is left.
        val right = decode(save(source, PhotoCrop(zoom = 2f, centerX = 0.9f, centerY = 0.5f)))
        assertTrue(mostly(Color.BLUE, right.getPixel(right.width / 8, right.height / 2)))
        assertTrue(mostly(Color.BLUE, right.getPixel(right.width * 7 / 8, right.height / 2)))

        // And on the left, only red.
        val left = decode(save(source, PhotoCrop(zoom = 2f, centerX = 0.1f, centerY = 0.5f)))
        assertTrue(mostly(Color.RED, left.getPixel(left.width / 8, left.height / 2)))
        assertTrue(mostly(Color.RED, left.getPixel(left.width * 7 / 8, left.height / 2)))
    }

    @Test
    fun `a see-through photo is put on white, not black`() {
        val saved = decode(save(solid(300, 300, Color.TRANSPARENT)))
        assertTrue(mostly(Color.WHITE, saved.getPixel(saved.width / 2, saved.height / 2)))
    }

    @Test
    fun `every save is a new file, even in the same instant`() {
        val first = save(solid(300, 300))
        val sameInstant = ProfilePhotos(context, clock = { 5L })
        val a = runBlocking { sameInstant.save(solid(300, 300), PhotoCrop()) }
        val b = runBlocking { sameInstant.save(solid(300, 300), PhotoCrop()) }
        assertNotEquals(a, b)
        assertNotEquals(first, a)
        assertEquals(3, dir.listFiles().orEmpty().size)
    }

    @Test
    fun `only the newest photo is kept`() {
        val old = save(solid(300, 300))
        val fresh = save(solid(300, 300))
        assertEquals(2, dir.listFiles().orEmpty().size)
        runBlocking { store.keepOnly(fresh) }
        assertNull(store.file(old))
        assertNotNull(store.file(fresh))
        assertEquals(listOf(fresh), dir.list().orEmpty().toList())
    }

    @Test
    fun `removing the photo deletes every file`() {
        save(solid(300, 300))
        save(solid(300, 300))
        runBlocking { store.deleteAll() }
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a name that is not a photo's finds nothing, whatever is on disk`() {
        val outside = File(context.filesDir, "secret.jpg").apply { writeText("not for the avatar") }
        try {
            assertNull(store.file("../secret.jpg"))
            assertNull(store.file("secret.jpg"))
            assertNull(store.file(null))
            assertNull(store.file("photo-99.jpg"), "a valid name with no file")
        } finally {
            outside.delete()
        }
    }

    @Test
    fun `a saved photo can be read back`() {
        val name = save(solid(300, 300, Color.GREEN))
        val read = assertNotNull(runBlocking { store.read(name) })
        assertEquals(300, read.width)
        assertNull(runBlocking { store.read("photo-98.jpg") })
        assertNull(runBlocking { store.read("nonsense") })
    }

    private fun serve(bytes: ByteArray): Uri {
        val uri = Uri.parse("content://com.example.gallery/profile-${tick++}")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
        return uri
    }

    private fun png(width: Int, height: Int): ByteArray =
        ByteArrayOutputStream().also { solid(width, height).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    @Test
    fun `a picked picture is opened at a size that is light to frame`() {
        val opened = assertNotNull(runBlocking { store.open(serve(png(4_000, 1_000))) })
        assertTrue(maxOf(opened.width, opened.height) <= ProfilePhotos.OPEN_SIDE, "${opened.width}x${opened.height}")
        assertTrue(opened.width > opened.height, "and it keeps its shape")
    }

    @Test
    fun `a picture that was opened can be framed and kept`() {
        val opened = assertNotNull(runBlocking { store.open(serve(png(1_200, 800))) })
        val name = save(opened, PhotoCrop(zoom = 2f))
        val saved = decode(name)
        assertEquals(400, saved.width)
        assertEquals(400, saved.height)
    }

    @Test
    fun `something that is not a picture is not opened`() {
        assertNull(runBlocking { store.open(serve("just some text".toByteArray())) })
        assertNull(runBlocking { store.open(Uri.parse("content://com.example.gallery/gone")) })
    }
}

/** The photo is a preference like the name and the emoji: kept across restarts and settings resets, and only ever a name we made. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfilePhotoPreferenceTest {
    private lateinit var store: android.content.SharedPreferences
    private lateinit var prefs: AppPreferences

    @BeforeTest
    fun setUp() {
        store = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("profile_photo_prefs", Context.MODE_PRIVATE)
        store.edit().clear().commit()
        prefs = AppPreferences(store)
    }

    @Test
    fun `there is no photo until one is chosen`() {
        assertEquals("", prefs.state.value.profilePhoto)
    }

    @Test
    fun `the photo survives a restart`() {
        prefs.setProfilePhoto("photo-123.jpg")
        assertEquals("photo-123.jpg", prefs.state.value.profilePhoto)
        assertEquals("photo-123.jpg", AppPreferences(store).state.value.profilePhoto)
    }

    @Test
    fun `it can be removed`() {
        prefs.setProfilePhoto("photo-123.jpg")
        prefs.setProfilePhoto("")
        assertEquals("", prefs.state.value.profilePhoto)
        assertEquals("", AppPreferences(store).state.value.profilePhoto)
    }

    @Test
    fun `a name that is not a photo's is no photo`() {
        prefs.setProfilePhoto("../../databases/normpix_finance.db")
        assertEquals("", prefs.state.value.profilePhoto)
        store.edit().putString("profile_photo", "../x.jpg").commit()
        assertEquals("", AppPreferences(store).state.value.profilePhoto, "even if the file was edited")
    }

    @Test
    fun `changing the name or the emoji leaves the photo alone`() {
        prefs.setProfilePhoto("photo-123.jpg")
        prefs.setProfile("Ada", "🍕")
        assertEquals("photo-123.jpg", prefs.state.value.profilePhoto)
        assertEquals("Ada", prefs.state.value.profileName)
    }

    @Test
    fun `settings search finds the profile by photo, picture, image and gallery`() {
        for (query in listOf("photo", "picture", "image", "gallery")) {
            assertTrue(
                com.example.normpix.settings.ui.SettingsCatalog.search(query).any { it.special == com.example.normpix.settings.ui.SettingsSpecial.Profile },
                query,
            )
        }
    }

    @Test
    fun `resetting settings keeps the photo, as it keeps the name`() {
        prefs.setProfile("Ada", "🍕")
        prefs.setProfilePhoto("photo-123.jpg")
        prefs.resetToDefaults()
        assertEquals("photo-123.jpg", prefs.state.value.profilePhoto)
        val again = AppPreferences(store).state.value
        assertEquals("photo-123.jpg", again.profilePhoto)
        assertEquals("Ada", again.profileName)
    }
}
