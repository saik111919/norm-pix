package com.example.normpix

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * The Feature guide's pictures: a screen of the real app for each guide topic, on the sample data, on a phone-sized window, in light
 * and in dark, each cropped to the part the guide shows and saved as `<topic>-<light|dark>.webp` (see `GuideShots`). The receipt topic
 * has none (a shared receipt needs ML Kit, which a test cannot run), so it keeps its drawing.
 *
 * Skipped unless asked for. To make them again after a screen changes, from `native/`:
 * `./gradlew testDebugUnitTest --tests '*GuideScreenshots*' -Pguideshots=<absolute path to app/src/main/assets/guide>`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class GuideScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val out: File? = System.getProperty("normpix.guideshots")?.takeIf { it.isNotBlank() }?.let(::File)
    private var env: TestEnvironment? = null
    private var suffix = "light"

    @Before
    fun setUp() {
        assumeTrue("The guide's pictures are only rendered when asked for (-Pguideshots=<folder>)", out != null)
        out!!.mkdirs()
    }

    @After
    fun tearDown() {
        env?.close()
    }

    /** The app on the sample data, in the brand's own colours and [mode], the welcome tour already seen. */
    private fun start(mode: ThemeMode) {
        suffix = if (mode == ThemeMode.Dark) "dark" else "light"
        val environment = TestEnvironment(prefsName = "guide_screenshots").also { env = it }
        environment.preferences.setAccent(AccentColor.Volt)
        environment.preferences.setMode(mode)
        environment.preferences.finishOnboarding()
        environment.preferences.setDummyDataEnabled(true)
        compose.setContent { NormPixRoot(container = environment.container) }
        runCatching { compose.waitUntil(timeoutMillis = 30_000) { sampleShown() } }
        settle()
    }

    private val samplePayees = listOf(
        "BigBasket", "DMart", "Reliance Fresh", "Nature's Basket", "Vegetable market", "Chai Point", "Third Wave Coffee",
        "Swiggy", "Zomato", "Meghana Foods", "Truffles", "Toit", "Namma Metro", "Uber", "Ola", "Rapido", "HDFC",
    )

    private fun sampleShown(): Boolean = samplePayees.any { payee ->
        compose.onAllNodes(hasText(payee, substring = true) or hasContentDescription(payee, substring = true))
            .fetchSemanticsNodes().isNotEmpty()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
    }

    private fun tab(label: String) {
        compose.onNode(hasContentDescription(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    private fun tapText(text: String) {
        val node = compose.onAllNodes((hasText(text) or hasContentDescription(text)) and hasClickAction()).onFirst()
        runCatching { node.performScrollTo() } // only where it is in a scrolling list
        node.performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    /** A step that could not be taken is reported and skipped, so the other pictures are still made. */
    private fun step(name: String, block: () -> Unit) {
        runCatching(block).onFailure { System.err.println("GuideScreenshots: $name ($suffix) skipped: ${it.message?.lines()?.firstOrNull()}") }
    }

    /** A real tap, with the clock running, as the app's own navigation tests open the "+" menu. */
    private fun tapDescribed(description: String) {
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription(description) and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        settle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    /**
     * The part of [name]'s screen the guide shows, as `<name>-<light|dark>.webp`: the window's full width and [SHOWN_HEIGHT_DP] of it
     * from the top (the shape `GuideShots.ASPECT` says), scaled to [SAVED_WIDTH_PX] wide - sharp on a phone at the size the guide
     * shows it, and about 30 kB.
     */
    private fun save(name: String) {
        settle()
        val screen = compose.onRoot().captureToImage().asAndroidBitmap()
        val density = screen.width / WINDOW_WIDTH_DP
        val shown = Bitmap.createBitmap(screen, 0, 0, screen.width, (SHOWN_HEIGHT_DP * density).toInt().coerceAtMost(screen.height))
        val saved = Bitmap.createScaledBitmap(shown, SAVED_WIDTH_PX, SAVED_WIDTH_PX * shown.height / shown.width, true)
        File(out, "$name-$suffix.webp").outputStream().use { saved.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, it) }
    }

    private companion object {
        const val WINDOW_WIDTH_DP = 411f
        /** 411 x 452 dp: the width over the height `GuideShots.ASPECT` gives the screenshot in the guide. */
        const val SHOWN_HEIGHT_DP = 452f
        const val SAVED_WIDTH_PX = 480
        const val WEBP_QUALITY = 80
    }

    /** Every topic's screen, in [mode]: each step on its own, so one that cannot be reached leaves the others' pictures. */
    private fun shoot(mode: ThemeMode) {
        start(mode)
        save("home")
        tab("Plans & Activity")
        save("activity")
        step("plans") { tapText("Plans"); save("plans") }
        tab("Insights")
        save("insights")
        tab("Wallet")
        save("wallet")
        tab("Home")

        // The Settings pages the other topics are about, from the "+" menu's Settings.
        step("settings") {
            tapDescribed("More actions")
            tapDescribed("Settings")
            for ((row, name) in listOf(
                "Widgets" to "widgets",
                "Data & Backup" to "backup",
                "Security & Lock" to "privacy",
                "Availability & Schedules" to "card_exchange",
            )) {
                step(name) {
                    tapText(row)
                    save(name)
                    back()
                }
            }
            step("look_and_feel") {
                tapText("Appearance")
                tapText("Colors")
                save("look_and_feel")
            }
        }
        repeat(3) { back() }

        // Add a transaction, last: the "+" menu's Expense, the form as it opens (a sheet in a window of its own, which this does
        // not close again).
        step("add_transaction") {
            tapDescribed("More actions")
            tapDescribed("Add expense")
            save("add_transaction")
        }
    }

    @Test
    fun light() = shoot(ThemeMode.Light)

    @Test
    fun dark() = shoot(ThemeMode.Dark)
}
