package com.example.normpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.example.normpix.settings.AccountButtonTarget
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.ACCOUNT_BUTTON_LABEL
import com.example.normpix.ui.components.AppPage
import com.example.normpix.ui.components.LocalWindowBlurOverride
import com.example.normpix.ui.components.PROFILE_BUTTON_LABEL
import com.example.normpix.ui.theme.AppSpacing
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * In the top floating circle style the top-level pages (Home, Plans & Transactions, Insights, Wallet) float their header over
 * the list: the title at the start and, at the end, one pill with the page's actions and an account circle that opens Settings.
 * A pushed page opens with a Back circle instead. The other styles keep the app bar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class FloatingTabHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private lateinit var list: LazyListState
    private var withActions by mutableStateOf(false)
    private var withPinned by mutableStateOf(false)
    private var backs = 0
    private var pushed by mutableStateOf(false)
    private var shown = false

    @Before
    fun setUp() {
        env = TestEnvironment("floating_tab_header_test")
    }

    @After
    fun tearDown() = env.close()

    private val share: @Composable RowScope.() -> Unit = {
        IconButton(onClick = {}) { Icon(Icons.Rounded.Share, contentDescription = "Share") }
    }

    /** A tab page whose list opens with a red block, then rows; scrolled to its top. */
    private fun page(style: BackButtonStyle, actions: Boolean = false, pinned: Boolean = false, pushed: Boolean = false) {
        env.preferences.setBackButtonStyle(style)
        withActions = actions
        withPinned = pinned
        this.pushed = pushed
        if (!shown) {
            shown = true
            compose.setContent {
                CompositionLocalProvider(LocalWindowBlurOverride provides true) {
                    env.Content {
                        list = rememberLazyListState()
                        AppPage(
                            title = "Insights",
                            listState = list,
                            actions = if (withActions) share else null,
                            pinnedHeader = if (withPinned) ({ Box(Modifier.fillMaxWidth().height(48.dp).background(Color.Blue).testTag("pinned")) }) else null,
                            onBack = if (this.pushed) ({ backs++ }) else null,
                        ) {
                            item { Box(Modifier.fillMaxWidth().height(120.dp).background(Color.Red).testTag("red")) }
                            items(20) { Box(Modifier.fillMaxWidth().height(80.dp)) { Text("Row $it") } }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        scrollTo(0)
    }

    /** Scrolls the list's start up by [dp]: the red block, first in it, then runs on under the header. */
    private fun scrollByDp(dp: Int) {
        compose.runOnUiThread {
            CoroutineScope(Dispatchers.Main.immediate).launch { list.scrollToItem(0, with(compose.density) { dp.dp.roundToPx() }) }
        }
        compose.waitForIdle()
    }

    private fun scrollTo(index: Int) {
        compose.runOnUiThread { CoroutineScope(Dispatchers.Main.immediate).launch { list.scrollToItem(index) } }
        compose.waitForIdle()
    }

    private fun account(): Rect = compose.onNode(hasContentDescription(ACCOUNT_BUTTON_LABEL) and hasClickAction()).getBoundsInRoot().toPx()

    private fun DpRect.toPx() = with(compose.density) { Rect(left.toPx(), top.toPx(), right.toPx(), bottom.toPx()) }

    private fun heading() = compose.onNode(hasText("Insights") and isHeading())

    @Test
    fun aTopLevelPageFloatsItsHeader_theTitleAtTheStart_andAnAccountCircleAtTheEnd() {
        page(BackButtonStyle.TopFloatingCircle)
        val account = account()
        val title = heading().getBoundsInRoot().toPx()
        assertTrue(title.center.y in account.top..account.bottom, "the title is in the circle's row: $title vs $account")
        assertTrue(title.right < account.left, "at the start, the circle at the end")

        scrollByDp(40)
        val red = compose.onNodeWithTag("red").getBoundsInRoot().toPx()
        assertTrue(red.overlaps(account), "the list runs on under the header: $red, $account")
        assertEquals(account, account(), "which stays where it is")
        assertEquals(1, compose.onAllNodes(hasText("Insights") and isHeading()).fetchSemanticsNodes().size, "the title is spoken once")
    }

    @Test
    fun theAccountCircleOpensSettings() {
        page(BackButtonStyle.TopFloatingCircle)
        compose.onNode(hasContentDescription(ACCOUNT_BUTTON_LABEL) and hasClickAction()).performClick()
        compose.waitForIdle()

        assertEquals(listOf<Any?>(null), env.navigator.settingsPages)
    }

    @Test
    fun theAccountCircleOpensTheProfileWhenThatIsWhereTheUserPointedIt() {
        env.preferences.setAccountButtonTarget(AccountButtonTarget.Profile)
        page(BackButtonStyle.TopFloatingCircle)
        compose.onAllNodes(hasContentDescription(ACCOUNT_BUTTON_LABEL)).assertCountEquals(0)
        compose.onNode(hasContentDescription(PROFILE_BUTTON_LABEL) and hasClickAction()).performClick()
        compose.waitForIdle()

        assertEquals(1, env.navigator.profileOpened)
        assertTrue(env.navigator.settingsPages.isEmpty(), "and not Settings")
    }

    @Test
    fun theAccountCircleWearsTheUsersPicture_theirEmoji_orTheirInitial_orAPlainFigure() {
        fun shown(text: String) = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        page(BackButtonStyle.TopFloatingCircle)
        assertTrue(!shown("A") && !shown("🍕"), "no profile yet: a plain figure")

        env.preferences.setProfile("Ada", "")
        compose.waitForIdle()
        assertTrue(shown("A"), "the first letter of the name")

        env.preferences.setProfile("Ada", "🍕")
        compose.waitForIdle()
        assertTrue(shown("🍕"), "the emoji, once there is one")
    }

    @Test
    fun theActionsAndTheAccountCircleShareOnePill() {
        page(BackButtonStyle.TopFloatingCircle, actions = true)
        val share = compose.onNodeWithContentDescription("Share").getBoundsInRoot().toPx()
        val account = account()
        assertTrue(share.center.y in account.top..account.bottom, "on the circle's row: $share vs $account")
        assertTrue(share.right <= account.left + 1f && account.left - share.right < 24f, "right beside it, in the same pill: $share vs $account")
    }

    @Test
    fun aPageWithAPinnedHeader_keepsItsTitleInTheRow_andHangsTheHeaderUnderIt() {
        page(BackButtonStyle.TopFloatingCircle, pinned = true)
        val account = account()
        val title = heading().getBoundsInRoot().toPx()
        assertTrue(title.center.y in account.top..account.bottom, "the title is in the circle's row: $title vs $account")
        val pinned = compose.onNodeWithTag("pinned").getBoundsInRoot().toPx()
        assertTrue(pinned.top >= account.bottom, "the pinned header is under the controls: $pinned vs $account")

        scrollTo(3)
        assertEquals(pinned, compose.onNodeWithTag("pinned").getBoundsInRoot().toPx(), "and stays put as the list scrolls")
        assertEquals(1, compose.onAllNodes(hasText("Insights") and isHeading()).fetchSemanticsNodes().size, "the title is spoken once")
    }

    @Test
    fun aPinnedHeadersFadeDoesNotLieOverTheListsFirstItemAtRest() {
        // The search field of Plans, Transactions and History is the first item under a pinned header: at rest nothing of it is veiled.
        page(BackButtonStyle.TopFloatingCircle, pinned = true)
        val pinned = compose.onNodeWithTag("pinned").getBoundsInRoot().toPx()
        val red = compose.onNodeWithTag("red").getBoundsInRoot().toPx()
        val fade = with(compose.density) { AppSpacing.md.toPx() }
        assertTrue(red.top >= pinned.bottom + fade - 1f, "the first item starts below the header and its fade: $red vs $pinned + $fade")
    }

    @Test
    fun theListRunsOnUnderAPinnedHeader_andIsNotCutOffAtItsFoot() {
        page(BackButtonStyle.TopFloatingCircle, pinned = true)
        val pinned = compose.onNodeWithTag("pinned").getBoundsInRoot().toPx()
        scrollByDp(20)
        // The red block, scrolled up, passes behind the pinned header: part of it is above the header's foot.
        val red = compose.onNodeWithTag("red").getBoundsInRoot().toPx()
        assertTrue(red.top < pinned.bottom, "the list slides away under the header, not clipped at its edge: $red vs $pinned")
    }

    @Test
    fun aPushedPage_opensWithTheBackCircle_andItsTitleBelow_notTheAccountCircle() {
        page(BackButtonStyle.TopFloatingCircle, pushed = true)
        compose.onAllNodes(hasContentDescription(ACCOUNT_BUTTON_LABEL)).assertCountEquals(0)
        val back = compose.onNode(hasContentDescription("Back") and hasClickAction()).getBoundsInRoot().toPx()
        assertTrue(heading().getBoundsInRoot().toPx().top >= back.bottom, "the page opens with its title, below the circle")

        compose.onNode(hasContentDescription("Back") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(1, backs)
    }

    @Test
    fun theOtherStyles_keepTheAppBar_andNoAccountCircle() {
        for (style in listOf(BackButtonStyle.StandardTopBar, BackButtonStyle.BottomDock)) {
            page(style)
            compose.onAllNodes(hasContentDescription(ACCOUNT_BUTTON_LABEL)).assertCountEquals(0)
            assertTrue(compose.onAllNodes(hasText("Insights") and isHeading()).fetchSemanticsNodes().isEmpty(), "$style: the title is in the bar")
            assertTrue(compose.onAllNodes(hasText("Insights")).fetchSemanticsNodes().isNotEmpty(), "$style: it is there")
        }
    }

    @Test
    fun aPushedPage_inTheOtherStyles_hasTheBackArrowInItsBar() {
        page(BackButtonStyle.StandardTopBar, pushed = true)
        compose.onNode(hasContentDescription("Back") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(1, backs)
    }
}
