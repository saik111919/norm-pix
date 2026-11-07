package com.example.normpix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.normpix.settings.IconStyle
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.LocalIconStyle
import com.example.normpix.ui.theme.AppIcons
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `Icon` draws what the user chose: with Material the Material icon, with Lucide that icon's Lucide drawing - different pixels from
 * the same call - and the app's own artwork the same in both. Switching changes every icon on screen at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h400dp-xxhdpi")
class IconStyleDrawingTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var env: TestEnvironment

    private var style by mutableStateOf(IconStyle.Material)
    private var icon by mutableStateOf<ImageVector>(Icons.Outlined.Lock)

    @Before
    fun setUp() {
        env = TestEnvironment("icon_style_drawing_test")
        compose.setContent {
            env.Content {
                CompositionLocalProvider(LocalIconStyle provides style) {
                    Box(Modifier.testTag("icon").size(48.dp)) { Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Black) }
                }
            }
        }
        compose.waitForIdle()
    }

    @After
    fun tearDown() = env.close()

    /** The icon's pixels as seen on screen: how dark each is (the ink is black on the window's light ground), row by row. */
    private fun pixels(): List<Int> {
        val map = compose.onNodeWithTag("icon").captureToImage().toPixelMap()
        return buildList { for (y in 0 until map.height) for (x in 0 until map.width) add(255 - (map[x, y].red * 255).toInt()) }
    }

    private fun show(style: IconStyle, icon: ImageVector = Icons.Outlined.Lock): List<Int> {
        compose.runOnIdle {
            this.style = style
            this.icon = icon
        }
        compose.waitForIdle()
        return pixels()
    }

    @Test
    fun theSameCallDrawsMaterialOrLucideByTheChoice() {
        val material = show(IconStyle.Material)
        val lucide = show(IconStyle.Lucide)
        assertTrue(material.count { it > 128 } > 100, "the Material lock is drawn")
        assertTrue(lucide.count { it > 128 } > 100, "the Lucide lock is drawn")
        assertNotEquals(material, lucide, "and they are not the same picture")
        assertEquals(material, show(IconStyle.Material), "choosing Material again gives Material back")
    }

    @Test
    fun theAppsOwnArtworkIsTheSameInBothStyles() {
        assertEquals(show(IconStyle.Material, AppIcons.Wallet), show(IconStyle.Lucide, AppIcons.Wallet))
    }
}
