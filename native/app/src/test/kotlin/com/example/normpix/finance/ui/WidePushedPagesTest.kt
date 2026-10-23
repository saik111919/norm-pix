package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.theme.AppLayout
import java.util.Locale
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The lists pushed over the tabs (Categories, People, Budget history) take [AppLayout.maxListWidth] of a big window, as the
 * transaction lists do, rather than the 720dp column of a form; a phone's window is as wide as it ever was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp")
class WidePushedPagesTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("wide_pushed_pages")
    }

    @After
    fun tearDown() = env.close()

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    /** How wide the Categories column is: what its Expense | Income control spans, plus the page's 16dp on each side. */
    private fun categoriesColumn(): Dp {
        compose.waitForIdle()
        val expense = compose.onAllNodes(hasText("Expense") and radio).onFirst().getUnclippedBoundsInRoot()
        val income = compose.onAllNodes(hasText("Income") and radio).onFirst().getUnclippedBoundsInRoot()
        return income.right - expense.left + 32.dp
    }

    private fun widthOf(tag: String): Dp = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().width

    @Test
    fun categories_takeTheWiderListColumn() {
        compose.setContent { env.Content { CategoriesPage() } }
        val width = categoriesColumn()
        assertTrue(width > AppLayout.maxContentWidth, "the column is $width, no wider than a form's ${AppLayout.maxContentWidth}")
        assertTrue(width <= AppLayout.maxListWidth, "the column is $width, past ${AppLayout.maxListWidth}")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h900dp")
    fun categories_onAPhone_fillTheWindow() {
        compose.setContent { env.Content { CategoriesPage() } }
        assertTrue(categoriesColumn() <= 411.dp)
    }

    @Test
    fun aSubPage_keepsTheFormColumn_unlessItAsksForMore() {
        compose.setContent {
            env.Content {
                AppSubPage(title = "Plain", onBack = {}) {
                    item { Box(Modifier.fillMaxWidth().height(10.dp).testTag("plain")) }
                }
            }
        }
        compose.waitForIdle()
        assertTrue(widthOf("plain") <= AppLayout.maxContentWidth, "a form's column is ${widthOf("plain")}")
    }

    @Test
    fun aSubPage_thatAsksForMore_getsIt() {
        compose.setContent {
            env.Content {
                AppSubPage(title = "List", onBack = {}, maxWidth = AppLayout.maxListWidth) {
                    item { Box(Modifier.fillMaxWidth().height(10.dp).testTag("list")) }
                }
            }
        }
        compose.waitForIdle()
        val width = widthOf("list")
        assertTrue(width > AppLayout.maxContentWidth && width <= AppLayout.maxListWidth, "the list is $width wide")
    }
}
