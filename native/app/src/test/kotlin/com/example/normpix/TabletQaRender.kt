package com.example.normpix

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** THROWAWAY: renders every tab at tablet sizes to look at. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletQaRender {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val out = File(System.getProperty("java.io.tmpdir"), "normpix-tablet").also { it.mkdirs() }
    private var env: TestEnvironment? = null

    @After
    fun tearDown() {
        env?.close()
    }

    private val samplePayees = listOf("BigBasket", "DMart", "Swiggy", "Zomato", "Uber", "HDFC", "Chai Point")

    private fun start() {
        val environment = TestEnvironment(prefsName = "tablet_qa").also { env = it }
        environment.preferences.setAccent(AccentColor.Volt)
        environment.preferences.setMode(ThemeMode.Light)
        environment.preferences.setDummyDataEnabled(true)
        compose.setContent { NormPixRoot(container = environment.container) }
        runCatching {
            compose.waitUntil(timeoutMillis = 30_000) {
                samplePayees.any { p ->
                    compose.onAllNodes(hasText(p, substring = true) or hasContentDescription(p, substring = true))
                        .fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    private fun save(name: String) {
        settle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun railTab(label: String) {
        compose.onAllNodes(hasText(label)).onFirst().performClick()
        settle()
    }

    private fun tour(prefix: String) {
        start()
        save("$prefix-home")
        railTab("Plans & Activity")
        save("$prefix-activity")
        railTab("Insights")
        save("$prefix-insights")
        railTab("Wallet")
        save("$prefix-wallet")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp-hdpi")
    fun portrait() = tour("portrait")

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-hdpi")
    fun landscape() = tour("landscape")

    @Test
    @Config(sdk = [34], qualifiers = "w891dp-h411dp-xhdpi")
    fun phoneOnItsSide() = tour("phoneside")
}
