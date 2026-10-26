package com.example.normpix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.ui.ALL_TIME_CAPTION
import com.example.normpix.finance.ui.AllTimeSwitcher
import com.example.normpix.finance.ui.CategoryCarousel
import com.example.normpix.finance.ui.CategorySpend
import com.example.normpix.finance.ui.DateRangeSwitcher
import com.example.normpix.finance.ui.MonthScopeButton
import com.example.normpix.finance.ui.MonthSwitcher
import com.example.normpix.finance.ui.monthLabel
import com.example.normpix.finance.ui.rangeLabel
import com.example.normpix.settings.MonthControlStyle
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.AppSecondaryTabs
import com.example.normpix.ui.components.AppTab
import com.example.normpix.wallet.AccountCardData
import com.example.normpix.wallet.AccountCarousel
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Text at the largest size the phone offers (200%, WCAG 1.4.4) on a small phone (360dp): nothing a person has to read is cut
 * short, and what does not fit beside its neighbour goes under it. Measured with the real font, so a word that does not fit
 * says so (`hasVisualOverflow`) - the 200% renders found "Budg…", "Remin…", a month that was only "…", and a fact row that
 * said "CREDIT" and "₹32,7…".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1000dp")
class LargeTextTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private var selected by mutableIntStateOf(0)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("large_text_test")
    }

    @After
    fun tearDown() = env.close()

    private fun show(fontScale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            // Inside the app's own theme and environment, which is where the text size is read.
            env.Content {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Fails when the text in this node had to be cut short (clipped or ended with an ellipsis). */
    private fun SemanticsNodeInteraction.assertNotCutShort(what: String) {
        val results = mutableListOf<TextLayoutResult>()
        val read = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) == true
        assertTrue(read && results.isNotEmpty(), "$what: no text to measure")
        // Cut short = ended in an ellipsis, ran out of lines or height, or - on one line - is wider than the room it was given. (Not
        // `hasVisualOverflow`, which also calls text "overflowing" when it is merely narrower than the room.)
        val cut = results.any { r ->
            r.didOverflowHeight || (r.lineCount == 1 && r.multiParagraph.maxIntrinsicWidth > r.layoutInput.constraints.maxWidth + 0.5f) ||
                (0 until r.lineCount).any { r.isLineEllipsized(it) }
        }
        assertTrue(!cut, "$what is cut short")
    }

    private val september = MonthKey(2026, 9)
    private val october = MonthKey(2026, 10)
    private val pillSpoken = "September 2026, last month, choose month"

    private fun pillBounds() = compose.onNode(hasContentDescription(pillSpoken)).getUnclippedBoundsInRoot()

    // ------------------------------------------------------------ the month control

    @Test
    fun atTwiceTheTextTheMonthControlGivesItsWayOutTheRoomUnderneath_andTheMonthIsWhole() {
        show(2.0f) {
            MonthSwitcher(
                september, october, {},
                trailing = { MonthScopeButton("All time", {}, icon = Icons.Rounded.DateRange, style = MonthControlStyle.Track) },
                style = MonthControlStyle.Track,
            )
        }

        compose.onNodeWithText(monthLabel(september), useUnmergedTree = true).assertNotCutShort("the month")
        compose.onNodeWithText("Last month", useUnmergedTree = true).assertNotCutShort("how far back it is")
        val pill = pillBounds()
        val allTime = compose.onNodeWithText("All time").getUnclippedBoundsInRoot()
        assertTrue(allTime.top >= pill.bottom, "All time is under the control, not squeezed beside it ($allTime under $pill)")
    }

    @Test
    fun atTheUsualTextOnASmallPhoneTheWayOutStaysBesideTheMonth_andTheMonthIsStillWhole() {
        show(1.0f) {
            MonthSwitcher(
                september, october, {},
                trailing = { MonthScopeButton("All time", {}, icon = Icons.Rounded.DateRange, style = MonthControlStyle.Track) },
                style = MonthControlStyle.Track,
            )
        }

        // The longest month names shrink to fit the room beside the way out rather than being cut.
        compose.onNodeWithText(monthLabel(september), useUnmergedTree = true).assertNotCutShort("the month")
        val pill = pillBounds()
        val allTime = compose.onNodeWithText("All time").getUnclippedBoundsInRoot()
        assertTrue(allTime.top < pill.bottom, "side by side at the usual size ($allTime beside $pill)")
    }

    @Test
    fun theEarlierSplitButtonsDoNotCutTheMonthEither() {
        show(2.0f) {
            MonthSwitcher(
                september, october, {},
                trailing = { MonthScopeButton("All time", {}, style = MonthControlStyle.Split) },
                style = MonthControlStyle.Split,
            )
        }
        compose.onNodeWithText(monthLabel(september), useUnmergedTree = true).assertNotCutShort("the month")
    }

    private val picked = DateRange(LocalDateTime.of(2026, 10, 3, 0, 0), LocalDateTime.of(2026, 10, 16, 0, 0))

    @Test
    fun atTwiceTheTextThePickedDaysAreWhole_andTheWayBackToOneMonthIsUnderThem() {
        show(2.0f) { DateRangeSwitcher(picked, {}, {}, style = MonthControlStyle.Track) }

        // Beside "By month" the dates were "3 Oct …": the very thing the pill is there to say.
        compose.onNodeWithText(rangeLabel(picked), useUnmergedTree = true).assertNotCutShort("the days")
        compose.onNodeWithText("13 days", useUnmergedTree = true).assertNotCutShort("how many")
        val dates = compose.onNode(hasContentDescription("3 Oct – 15 Oct, 13 days, choose dates")).getUnclippedBoundsInRoot()
        val byMonth = compose.onNodeWithText("By month").getUnclippedBoundsInRoot()
        assertTrue(byMonth.top >= dates.bottom, "By month is under the dates, not squeezing them ($byMonth under $dates)")
    }

    @Test
    fun atTwiceTheTextAllTimeIsWholeAndItsCaptionIsSpokenEvenWhereItIsNotShown() {
        show(2.0f) { AllTimeSwitcher({}, style = MonthControlStyle.Track) }

        compose.onNodeWithText("All time", useUnmergedTree = true).assertNotCutShort("All time")
        // "Every tr…" would only say again what "All time" says: it is left out, not cut - and still said to a screen reader.
        assertEquals(0, compose.onAllNodes(hasText(ALL_TIME_CAPTION), useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNode(hasContentDescription("All time. $ALL_TIME_CAPTION")).assertIsDisplayed()
        val track = compose.onNodeWithText("All time", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val byMonth = compose.onNodeWithText("By month").getUnclippedBoundsInRoot()
        assertTrue(byMonth.top >= track.bottom, "By month is under it")
    }

    @Test
    fun atTheUsualTextAllTimeShowsItsCaption_andByMonthIsBesideIt() {
        show(1.0f) { AllTimeSwitcher({}, style = MonthControlStyle.Track) }

        compose.onNodeWithText(ALL_TIME_CAPTION, useUnmergedTree = true).assertIsDisplayed()
        val track = compose.onNodeWithText("All time", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val byMonth = compose.onNodeWithText("By month").getUnclippedBoundsInRoot()
        assertTrue(byMonth.top < track.bottom, "side by side at the usual size")
    }

    @Test
    fun theEarlierSplitButtonsPutByMonthUnderThePickedDaysToo() {
        show(2.0f) { DateRangeSwitcher(picked, {}, {}, style = MonthControlStyle.Split) }

        compose.onNodeWithText(rangeLabel(picked), useUnmergedTree = true).assertNotCutShort("the days")
        val dates = compose.onNode(hasContentDescription("3 Oct – 15 Oct, 13 days, choose dates")).getUnclippedBoundsInRoot()
        assertTrue(compose.onNodeWithText("By month").getUnclippedBoundsInRoot().top >= dates.bottom)
    }

    // ------------------------------------------------------------ the Plans sections

    private val sections = listOf(
        AppTab("Budgets", Icons.Outlined.PieChart, Icons.Rounded.PieChart),
        AppTab("Recurring", Icons.Outlined.Repeat, Icons.Rounded.Repeat),
        AppTab("Reminders", Icons.Outlined.Notifications, Icons.Rounded.Notifications),
    )

    private val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test
    fun atTwiceTheTextEverySectionNameIsWhole_theRowScrollsAndKeepsTheOneYouAreInInView() {
        selected = 0
        show(2.0f) { AppSecondaryTabs(sections, selected, { selected = it }) }

        for (label in listOf("Budgets", "Recurring", "Reminders")) {
            compose.onNode(tab and hasText(label)).assertExists()
            compose.onNodeWithText(label, useUnmergedTree = true).assertNotCutShort(label)
        }
        compose.onNode(tab and hasText("Budgets")).assertIsDisplayed()

        // The one off to the side comes into view as it is chosen.
        compose.onNode(tab and hasText("Recurring")).performClick()
        compose.waitForIdle()
        compose.onNode(tab and hasText("Recurring")).assertIsDisplayed()
        selected = 2
        compose.waitForIdle()
        compose.onNode(tab and hasText("Reminders")).assertIsDisplayed()
    }

    @Test
    fun atTheUsualTextTheSectionsAreEqualPillsAcrossTheWidth_notAScrollingRow() {
        selected = 0
        show(1.0f) { AppSecondaryTabs(sections, selected, { selected = it }) }

        for (label in listOf("Budgets", "Recurring", "Reminders")) compose.onNode(tab and hasText(label)).assertIsDisplayed()
        val widths = listOf("Recurring", "Reminders").map { compose.onNode(tab and hasText(it)).getUnclippedBoundsInRoot().width }
        assertEquals(widths[0].value, widths[1].value, 1f, "the two that are not chosen are the same width")
    }

    // ------------------------------------------------------------ the account panel

    private val owed = AccountCardData(
        id = 1L, name = "ICICI Credit Card", kind = AccountKind.Card, bankName = "ICICI Bank", balance = "-₹17,287.97", standing = "Owed",
        isDefault = false, archived = false, cards = 0, credit = "₹32,712.03 left", creditUsed = 0.34f, due = "Due in 21 days",
    )

    private fun showPanel(fontScale: Float) = show(fontScale) { AccountCarousel(listOf(owed), onOpen = {}, onManage = {}) }

    @Test
    fun atTwiceTheTextThePanelsFactsStandOneUnderAnother_eachWhole() {
        showPanel(2.0f)

        val credit = compose.onNodeWithText("CREDIT LEFT").getUnclippedBoundsInRoot()
        val used = compose.onNodeWithText("USED").getUnclippedBoundsInRoot()
        val bill = compose.onNodeWithText("BILL").getUnclippedBoundsInRoot()
        assertEquals(credit.left.value, used.left.value, 1f, "in one column")
        assertEquals(credit.left.value, bill.left.value, 1f, "in one column")
        assertTrue(used.top > credit.top && bill.top > used.top, "one under the other: $credit, $used, $bill")

        for (text in listOf("CREDIT LEFT", "₹32,712.03", "USED", "34%", "BILL", "In 21 days")) {
            compose.onNodeWithText(text).assertNotCutShort(text)
        }
        // "Balance · Owed" beside the page marker wraps to a second line rather than ending "Balance ·".
        compose.onNodeWithText("BALANCE · OWED").assertNotCutShort("the balance's label")
    }

    @Test
    fun atTheUsualTextThePanelsFactsShareARow() {
        showPanel(1.0f)

        val credit = compose.onNodeWithText("CREDIT LEFT").getUnclippedBoundsInRoot()
        val used = compose.onNodeWithText("USED").getUnclippedBoundsInRoot()
        val bill = compose.onNodeWithText("BILL").getUnclippedBoundsInRoot()
        assertEquals(credit.top.value, used.top.value, 1f, "one row")
        assertEquals(credit.top.value, bill.top.value, 1f, "one row")
        assertTrue(used.left > credit.left && bill.left > used.left)
    }

    // ------------------------------------------------------------ the top spending panel

    @Test
    fun atTwiceTheTextTheTopSpendingPanelsFactsStandOneUnderAnother_eachWhole() {
        val spend = runBlocking {
            env.container.finance.load()
            val food = env.container.finance.state.value.categories.first { it.type == CategoryType.Expense && it.parentId == null }
            CategorySpend(food.id!!, food, MonthKey.of(LocalDateTime.now()), amount = 2_002_00, count = 12, share = 0.65, previous = 1_000_00, budget = null)
        }
        show(2.0f) { CategoryCarousel(listOf(spend), onOpen = {}, onSeeAll = {}) }

        val share = compose.onNodeWithText("OF SPENDING").getUnclippedBoundsInRoot()
        val budget = compose.onNodeWithText("BUDGET LEFT").getUnclippedBoundsInRoot()
        assertEquals(share.left.value, budget.left.value, 1f, "in one column")
        assertTrue(budget.top > share.top, "one under the other: $share, $budget")
        for (text in listOf("OF SPENDING", "65%", "BUDGET LEFT", "None set")) compose.onNodeWithText(text).assertNotCutShort(text)
    }
}
