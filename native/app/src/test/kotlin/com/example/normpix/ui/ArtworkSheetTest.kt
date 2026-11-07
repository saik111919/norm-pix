package com.example.normpix.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.role
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.ProvideArtworkSeed
import com.example.normpix.ui.components.appColorScheme
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A sheet whose form says it shows artwork is dressed in colours made from it - and only then: not with nothing said, not with a
 * grey, not with Settings > Appearance > Colors > Artwork colors off - and what means something (an expense, an income, a
 * transfer) keeps the app's own colours whatever the picture is. The sheet follows the seed as it changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ArtworkSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    private class Seen(val scheme: ColorScheme, val app: ColorScheme, val types: List<Color>)

    private var outside: Seen? = null
    private var inside: Seen? = null
    private var seed by mutableStateOf<Color?>(null)

    private val red = Color(0xFFE53935)
    private val blue = Color(0xFF1E88E5)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("artwork_sheet_test")
        env.preferences.setMode(ThemeMode.Light)
        env.preferences.setAccent(AccentColor.Green)
    }

    @After
    fun tearDown() = env.close()

    @Composable
    private fun Probe(record: (Seen) -> Unit) {
        val scheme = MaterialTheme.colorScheme
        val app = appColorScheme
        val types = TransactionType.entries.map { it.role().color }
        androidx.compose.runtime.SideEffect { record(Seen(scheme, app, types)) }
    }

    /** The page, and a sheet over it that shows artwork of [seed] (read each time it recomposes). */
    private fun show() {
        compose.setContent {
            env.Content {
                Probe { outside = it }
                val overlays = LocalOverlays.current
                LaunchedEffect(Unit) {
                    overlays.sheet<Unit> {
                        ProvideArtworkSeed(seed)
                        Probe { inside = it }
                        Text("A form")
                    }
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("A form").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    @Test
    fun aSheetThatShowsNothingWearsTheAppsColours() {
        seed = null
        show()
        assertEquals(outside!!.scheme.primary, inside!!.scheme.primary)
        assertEquals(outside!!.scheme.surfaceContainerHigh, inside!!.scheme.surfaceContainerHigh)
    }

    @Test
    fun aSheetThatShowsAColourfulPictureWearsItsColours() {
        seed = red
        show()
        assertNotEquals(outside!!.scheme.primary, inside!!.scheme.primary, "the primary follows the picture")
        assertNotEquals(outside!!.scheme.surface, inside!!.scheme.surface, "and the surface, softly")
        assertEquals(outside!!.scheme.error, inside!!.scheme.error, "the error colour is the app's")
    }

    @Test
    fun whatMeansSomethingKeepsTheAppsColours() {
        seed = red
        show()
        assertEquals(outside!!.types, inside!!.types, "expense, income and transfer look the same with and without a picture")
        assertEquals(outside!!.scheme.secondary, inside!!.app.secondary, "the app's own scheme is there for the things that need it")
    }

    @Test
    fun aGreyPictureChangesNothing() {
        seed = Color(0xFF808080)
        show()
        assertEquals(outside!!.scheme.primary, inside!!.scheme.primary)
    }

    @Test
    fun withTheSettingOffEverySheetWearsTheAppsColoursExactly() {
        env.preferences.setArtworkColors(false)
        seed = red
        show()
        assertEquals(outside!!.scheme.primary, inside!!.scheme.primary)
        assertEquals(outside!!.scheme.surface, inside!!.scheme.surface)
        assertEquals(outside!!.scheme.secondaryContainer, inside!!.scheme.secondaryContainer)
        assertEquals(outside!!.types, inside!!.types)
    }

    @Test
    fun theSheetFollowsASeedThatChanges_andGoesBackWhenThePictureIsRemoved() {
        seed = red
        show()
        val first = inside!!.scheme.primary
        seed = blue
        compose.waitForIdle()
        val second = inside!!.scheme.primary
        assertNotEquals(first, second, "another picture, another colour")
        seed = null
        compose.waitForIdle()
        assertEquals(outside!!.scheme.primary, inside!!.scheme.primary, "no picture: the app's colours again")
    }

    @Test
    fun aSheetOpenedOverAFormWearsTheFormsColoursToo() {
        seed = red
        var picker: Seen? = null
        compose.setContent {
            env.Content {
                Probe { outside = it }
                val overlays = LocalOverlays.current
                LaunchedEffect(Unit) {
                    overlays.sheet<Unit> {
                        ProvideArtworkSeed(seed)
                        Probe { inside = it }
                        Text("A form")
                        LaunchedEffect(Unit) {
                            overlays.sheet<Unit> {
                                Probe { picker = it }
                                Text("A picker")
                            }
                        }
                    }
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("A picker").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        assertNotEquals(outside!!.scheme.primary, picker!!.scheme.primary, "the picker is not in the app's colours")
        assertEquals(inside!!.scheme.primary, picker!!.scheme.primary, "it is in the form's")
        assertEquals(outside!!.types, picker!!.types)
    }

    @Test
    fun inPureTheSurfacesStayBlackAndWhite() {
        env.preferences.setAccent(AccentColor.Pure)
        seed = red
        show()
        assertEquals(outside!!.scheme.background, inside!!.scheme.background)
        assertEquals(outside!!.scheme.surface, inside!!.scheme.surface)
        assertNotEquals(outside!!.scheme.primary, inside!!.scheme.primary, "the accents still follow")
    }
}
