package com.example.normpix.settings.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.example.normpix.about.ui.AboutPage
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import java.io.File
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pages under Settings, pictured whole in Light and Dark with the 3D icons on and off, to be looked at in
 * `app/build/qa_settings_icons3d/` (tall pages come in slices): with 3D on the rows' leading icons are 3D pictures like the main
 * list's, with it off every page is what it was. What the tests cannot see is whether a picture is the right size on its row, so the
 * pictures are the proof; the assertions only say each page drew.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h4000dp-xhdpi")
class SettingsSubPagesRenderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "settings_sub_pages_render")
    private val outDir = File("build/qa_settings_icons3d").also { it.mkdirs() }
    private var fontScale by mutableStateOf(1f)

    @BeforeTest
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun tearDown() = env.close()

    private fun show(page: @Composable () -> Unit) {
        compose.setContent {
            env.Content {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { page() }
            }
        }
        compose.waitForIdle()
    }

    /** The pictures decode off the main thread, so give them a moment before looking. */
    private fun settle() {
        repeat(12) {
            Thread.sleep(50)
            compose.waitForIdle()
        }
    }

    /** The page as it is now, in as many pictures of at most 2,600 px as it takes, without the empty page under its end. */
    private fun dump(name: String) {
        settle()
        val full = compose.onRoot().captureToImage().asAndroidBitmap()
        val background = full.getPixel(full.width - 1, full.height - 1)
        var end = full.height
        scan@ while (end > 0) {
            for (x in 0 until full.width step 4) if (full.getPixel(x, end - 1) != background) break@scan
            end--
        }
        end = (end + 48).coerceAtMost(full.height)
        var top = 0
        var part = 1
        while (top < end) {
            val height = minOf(SLICE, end - top)
            val slice = Bitmap.createBitmap(full, 0, top, full.width, height)
            File(outDir, "${name}_$part.png").outputStream().use { slice.compress(Bitmap.CompressFormat.PNG, 100, it) }
            top += height
            part++
        }
    }

    /** Light and Dark, 3D on and off, of whatever [show] put up. */
    private fun dumpAll(page: String, scale: String = "") {
        for (threeD in listOf(true, false)) {
            env.preferences.setSettingsIcons3d(threeD)
            for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                env.preferences.setMode(mode)
                compose.waitForIdle()
                dump("${page}${scale}_${if (threeD) "3d" else "flat"}_${mode.name.lowercase()}")
            }
        }
    }

    @Test
    fun security() {
        env.preferences.setWalletLockEnabled(true)
        env.preferences.setNfcTagRevealEnabled(true)
        env.registeredTags.register(byteArrayOf(0x04, 0x5A, 0x1C, 0x32, 0x7B, 0x66, 0x7F))
        show { SecuritySettingsPage() }
        compose.onNodeWithText("Biometric lock").assertExists()
        dumpAll("security")
    }

    @Test
    fun dataAndBackup() {
        show { DataSettingsPage() }
        compose.onNodeWithText("Back up").assertExists()
        dumpAll("data")
    }

    @Test
    fun appearance() {
        show { AppearanceSettingsPage() }
        compose.onNodeWithText("Layouts").assertExists()
        dumpAll("appearance")
    }

    @Test
    fun notifications() {
        show { NotificationsSettingsPage() }
        compose.onNodeWithText("Allow notifications").assertExists()
        dumpAll("notifications")
    }

    @Test
    fun about() {
        show { AboutPage() }
        compose.onNodeWithText("Your data").assertExists()
        dumpAll("about")
    }

    @Test
    fun largeText() {
        // The rows keep their room at 1.6x text: a picture is a fixed size, the words around it grow.
        fontScale = 1.6f
        env.preferences.setWalletLockEnabled(true)
        env.preferences.setNfcTagRevealEnabled(true)
        show { SecuritySettingsPage() }
        env.preferences.setSettingsIcons3d(true)
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            env.preferences.setMode(mode)
            compose.waitForIdle()
            dump("security_16x_3d_${mode.name.lowercase()}")
        }
        assertTrue(File(outDir, "security_16x_3d_light_1.png").exists())
    }

    private companion object {
        const val SLICE = 2600
    }
}
