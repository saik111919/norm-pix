package com.example.normpix.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.normpix.settings.InterfaceFont
import com.example.normpix.ui.components.ProfileAvatar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The interface font reaches text that sets its own style (an avatar's initial), and the windows that hold other text - a
 * sheet, a dialog, a menu - take it from the theme they are opened in, so nothing is left in the phone's font by accident.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class InterfaceFontEverywhereTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val gsFlex = Appearance(font = AppFont(InterfaceFont.GsFlex))

    /** Notes the family the text style in force here sets. */
    @Composable
    private fun Probe(into: MutableList<FontFamily?>) {
        val family = LocalTextStyle.current.fontFamily
        SideEffect { into += family }
        Text("probe")
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val node = compose.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first()
    }

    @Test
    fun anAvatarsInitialIsSetInTheInterfaceFont() {
        var expected: FontFamily? = null
        compose.setContent {
            NormPixTheme(gsFlex) {
                expected = MaterialTheme.typography.titleMedium.fontFamily
                ProfileAvatar(name = "sai", emoji = "", size = 48.dp)
            }
        }
        compose.waitForIdle()
        assertNotNull(expected, "the theme sets GS Flex")
        assertEquals(expected, layoutOf("S").layoutInput.style.fontFamily)
    }

    @Test
    fun withThePhonesOwnFontTheInitialIsLeftToTheSystem() {
        compose.setContent {
            NormPixTheme(Appearance(font = AppFont(InterfaceFont.System))) {
                ProfileAvatar(name = "sai", emoji = "", size = 48.dp)
            }
        }
        compose.waitForIdle()
        // The phone's font is Material's own scale, untouched.
        assertEquals(Typography().titleMedium.fontFamily, layoutOf("S").layoutInput.style.fontFamily)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun aSheetADialogAndAMenuTakeTheTextStyleOfTheTheme() {
        val inSheet = mutableListOf<FontFamily?>()
        val inDialog = mutableListOf<FontFamily?>()
        val inMenu = mutableListOf<FontFamily?>()
        var expected: FontFamily? = null
        compose.setContent {
            NormPixTheme(gsFlex) {
                expected = MaterialTheme.typography.bodyLarge.fontFamily
                ModalBottomSheet(onDismissRequest = {}, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                    Probe(inSheet)
                }
                AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Probe(inDialog) })
                DropdownMenu(expanded = true, onDismissRequest = {}) { Probe(inMenu) }
            }
        }
        compose.waitForIdle()
        assertNotNull(expected, "the theme sets GS Flex")
        for ((where, seen) in listOf("sheet" to inSheet, "dialog" to inDialog, "menu" to inMenu)) {
            assertEquals(expected, seen.lastOrNull(), "text in a $where is in the interface font")
        }
    }

    @Test
    fun thePhonesFontLeavesTextStyleAloneInTheSameWindows() {
        val seen = mutableListOf<FontFamily?>()
        compose.setContent {
            NormPixTheme(Appearance(font = AppFont(InterfaceFont.System))) {
                AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Probe(seen) })
            }
        }
        compose.waitForIdle()
        assertEquals(Typography().bodyLarge.fontFamily, seen.lastOrNull())
        assertNull(AppFont(InterfaceFont.System).customFile)
    }
}
