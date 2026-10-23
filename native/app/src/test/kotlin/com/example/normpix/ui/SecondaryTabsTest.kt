package com.example.normpix.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.AppSecondaryTabs
import com.example.normpix.ui.components.AppTab
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sections inside a primary tab (Budgets | Recurring | Reminders) as a row of pills: the one you are in filled, the others
 * outlined, each a target a finger finds, a count riding the corner of its pill, and - with large text - all the words still there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class SecondaryTabsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private var selected by mutableIntStateOf(0)
    private val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("secondary_tabs_test")
    }

    @After
    fun tearDown() = env.close()

    private val sections = listOf(
        AppTab("Budgets", Icons.Outlined.PieChart, Icons.Rounded.PieChart),
        AppTab("Recurring", Icons.Outlined.Repeat, Icons.Rounded.Repeat),
        AppTab("Reminders", Icons.Outlined.Notifications, Icons.Rounded.Notifications, badge = { Text("2") }),
    )

    private fun show(fontScale: Float? = null) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides if (fontScale != null) Density(density.density, fontScale) else density) {
                env.Content { AppSecondaryTabs(tabs = sections, selectedIndex = selected, onSelect = { selected = it }) }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun eachSectionIsATab_andTheOneYouAreInSaysSo() {
        show()

        compose.onAllNodes(tab).assertCountEquals(3)
        compose.onNode(tab and hasText("Budgets")).assertIsSelected()
        compose.onNode(tab and hasText("Recurring")).assertIsNotSelected()
        compose.onNode(tab and hasText("Reminders")).assertIsNotSelected()
    }

    @Test
    fun pressingAnotherSelectsIt() {
        show()

        compose.onNode(tab and hasText("Reminders")).performClick()
        compose.waitForIdle()

        assertEquals(2, selected)
        compose.onNode(tab and hasText("Reminders")).assertIsSelected()
        compose.onNode(tab and hasText("Budgets")).assertIsNotSelected()
    }

    @Test
    fun aCountIsOnTheTabItBelongsTo() {
        show()

        // Part of the Reminders tab (read with it), not a separate thing to find.
        compose.onNode(tab and hasText("Reminders") and hasText("2")).assertIsDisplayed()
    }

    @Test
    fun everyTabIsATargetAFingerFinds_andTheSectionYouAreInIsTheWiderOne() {
        show()

        val widths = listOf("Budgets", "Recurring", "Reminders").map { label ->
            val bounds = compose.onNode(tab and hasText(label)).getUnclippedBoundsInRoot()
            assertTrue(bounds.height >= 48.dp, "$label is ${bounds.height} tall")
            bounds.width
        }
        assertTrue(widths[0] > widths[1] && widths[0] > widths[2], "Budgets, selected, is the wider: $widths")
    }

    @Test
    fun withLargeTextEveryWordIsStillThere_andStillATarget() {
        show(fontScale = 1.7f)

        for (label in listOf("Budgets", "Recurring", "Reminders")) {
            val node = compose.onNode(tab and hasText(label))
            node.assertIsDisplayed()
            assertTrue(node.getUnclippedBoundsInRoot().height >= 48.dp, "$label keeps its target")
        }
    }
}
