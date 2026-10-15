package com.example.normpix

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import java.io.File
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The README's screenshots: the app's main screens with the sample data (Settings > Data & backup > Dummy data), on a
 * phone-sized window, in light and in dark. Rendered by Robolectric, so they come from the real screens and change with
 * them; `.github/workflows/screenshots.yml` runs this and keeps the pictures in `docs/screenshots`.
 *
 * Skipped unless asked for: `./gradlew testDebugUnitTest --tests '*ReadmeScreenshots*' -Pscreenshots=<folder>`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ReadmeScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val out: File? = System.getProperty("normpix.screenshots")?.takeIf { it.isNotBlank() }?.let(::File)
    private var env: TestEnvironment? = null

    @Before
    fun setUp() {
        assumeTrue("README screenshots are only rendered when asked for (-Pscreenshots=<folder>)", out != null)
        out!!.mkdirs()
    }

    @After
    fun tearDown() {
        env?.close()
    }

    /** The app on the sample data, in the brand's own colours (not the test machine's wallpaper's) and [mode]. */
    private fun start(mode: ThemeMode) {
        val environment = TestEnvironment(prefsName = "readme_screenshots").also { env = it }
        environment.preferences.setAccent(AccentColor.Volt)
        environment.preferences.setMode(mode)
        environment.preferences.setDummyDataEnabled(true)
        compose.setContent { NormPixRoot(container = environment.container) }
        // The sample set opens in the background: wait for its transactions to show, not the user's empty start. A slow
        // run still takes its pictures (of whatever is there) rather than none.
        runCatching { compose.waitUntil(timeoutMillis = 30_000) { sampleShown() } }
        settle()
    }

    /** Payees only the sample set has (which of them shows depends on the day). */
    private val samplePayees = listOf(
        "BigBasket", "DMart", "Reliance Fresh", "Nature's Basket", "Vegetable market", "Chai Point", "Third Wave Coffee",
        "Swiggy", "Zomato", "Meghana Foods", "Truffles", "Toit", "Namma Metro", "Uber", "Ola", "Rapido", "HDFC",
    )

    private fun sampleShown(): Boolean = samplePayees.any { payee ->
        compose.onAllNodes(hasText(payee, substring = true) or hasContentDescription(payee, substring = true))
            .fetchSemanticsNodes().isNotEmpty()
    }

    /** Lets the data load and every motion finish, so nothing is caught half way. */
    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    private fun tab(label: String) {
        compose.onNode(hasContentDescription(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    private fun tap(text: String) {
        compose.onAllNodes(hasText(text)).onFirst().performClick()
        settle()
    }

    private fun save(name: String) {
        settle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun light() {
        start(ThemeMode.Light)
        save("home")
        tab("Plans & Activity")
        save("activity")
        tap("Plans")
        save("budgets")
        tab("Insights")
        save("insights")
        tab("Wallet")
        save("wallet")
    }

    @Test
    fun dark() {
        start(ThemeMode.Dark)
        save("home-dark")
        tab("Insights")
        save("insights-dark")
        tab("Wallet")
        save("wallet-dark")
        tab("Plans & Activity")
        save("activity-dark")
    }
}
