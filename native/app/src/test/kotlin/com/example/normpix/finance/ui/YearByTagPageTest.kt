package com.example.normpix.finance.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.backup.SettingsBackupCodec
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinancialYear
import com.example.normpix.finance.domain.Tag
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.PrefValue
import com.example.normpix.settings.YearStart
import com.example.normpix.settings.ui.FinanceSettingsPage
import com.example.normpix.settings.ui.SettingsCatalog
import com.example.normpix.settings.ui.TAGS_TITLE
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Insights > Year by tag (2026-11-15): the financial year (April to March unless the user picks January) with every tag's spending,
 * income and count, the tax-deductible tag first; the year switcher (back to the first year with anything, never past this one); a tag
 * opening its transactions over that year; hidden amounts hidden; and the "Year starts in" setting - kept under a key that never
 * changes, in a backup, on Settings > Finance and in its search, and changeable from the page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class YearByTagPageTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now().withSecond(0).withNano(0)
    private val today = LocalDate.now()
    private val thisYear = FinancialYear.containing(today, Month.APRIL)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("year_by_tag_test")
    }

    @After
    fun tearDown() = env.close()

    private val finance get() = env.container.finance
    private val money get() = finance.state.value.money

    private fun tag(name: String): Tag = runBlocking { finance.saveTag(Tag(name = name, createdAt = now)) }

    private fun add(type: TransactionType, amount: Long, at: LocalDateTime, tags: Set<Long>): FinanceTransaction = runBlocking {
        val cash = finance.repository.accounts().first { it.kind == AccountKind.Cash }
        val category = finance.repository.categories().first { it.name == if (type == TransactionType.Income) "Salary" else "Health" }
        finance.saveTransaction(
            FinanceTransaction(
                type = type, amount = amount, accountId = cash.id!!, categoryId = category.id, occurredAt = at, createdAt = now, updatedAt = now,
                tagIds = tags,
            ),
        )
    }

    private fun count(text: String, substring: Boolean = false) = compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun show() {
        compose.setContent { env.Content { YearByTagPage() } }
        compose.waitForIdle()
    }

    /** Some tagged money this year (the first minute of today, so it is in whatever year today is) and last year. */
    private fun seed(): Pair<Tag, Tag> {
        val trip = tag("Goa trip")
        val tax = tag("Tax-deductible")
        val early = today.atStartOfDay().plusMinutes(1)
        add(TransactionType.Expense, 45_000, early, setOf(tax.id!!))
        add(TransactionType.Expense, 90_000, early, setOf(trip.id!!))
        add(TransactionType.Income, 20_000, early, setOf(trip.id!!))
        add(TransactionType.Expense, 7_000, thisYear.previous.firstDay.atTime(10, 0), setOf(trip.id!!))
        // Dated after today: not counted yet.
        add(TransactionType.Expense, 1_00_000, today.plusDays(1).atTime(10, 0), setOf(tax.id!!))
        return tax to trip
    }

    @Test
    fun `this year's tags, the tax-deductible one first, with what each spent, received and how many`() {
        seed()
        show()
        compose.onNodeWithContentDescription("${thisYear.label}, ${thisYear.span}, so far").assertIsDisplayed()
        compose.waitUntil(10_000) { count("Tax-deductible") > 0 }

        // Tax-deductible first though Goa trip moved more.
        val taxTop = compose.onNodeWithText("Tax-deductible").fetchSemanticsNode().boundsInRoot.top
        val tripTop = compose.onNodeWithText("Goa trip").fetchSemanticsNode().boundsInRoot.top
        assertTrue(taxTop < tripTop, "Tax-deductible is listed first")
        compose.onNodeWithText("Spent ${money.format(45_000)} · Received ${money.format(0)}\n1 transaction").assertIsDisplayed()
        compose.onNodeWithText("Spent ${money.format(90_000)} · Received ${money.format(20_000)}\n2 transactions").assertIsDisplayed()
        assertEquals(0, count(money.format(1_00_000), substring = true), "what is dated after today is not in it")
    }

    @Test
    fun `the switcher steps back to last year and no further than the first year with anything, and never past this one`() {
        seed()
        show()
        compose.onNodeWithContentDescription("Next year").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous year").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("${thisYear.previous.label}, ${thisYear.previous.span}").assertIsDisplayed()
        compose.waitUntil(10_000) { count("Spent ${money.format(7_000)}", substring = true) > 0 }
        compose.onNodeWithText("Nothing this year").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous year").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next year").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("${thisYear.label}, ${thisYear.span}, so far").assertIsDisplayed()
    }

    @Test
    fun `a tag opens its transactions over the year shown`() {
        val (_, trip) = seed()
        show()
        compose.waitUntil(10_000) { count("Goa trip") > 0 }
        compose.onNodeWithText("Goa trip").performClick()
        assertEquals(listOf<Pair<Long, DateRange?>>(trip.id!! to thisYear.range), env.navigator.tagHistories)
    }

    @Test
    fun `hidden amounts are hidden`() {
        seed()
        env.preferences.setAmountsHidden(true)
        show()
        compose.waitUntil(10_000) { count("Goa trip") > 0 }
        assertEquals(0, count(money.format(90_000), substring = true))
        assertTrue(count("Spent ₹••••", substring = true) > 0)
    }

    @Test
    fun `Insights leads to the year by tag, saying which year`() {
        seed()
        compose.setContent { env.Content { InsightsScreen() } }
        compose.waitForIdle()
        compose.waitUntil(10_000) { count("Year by tag") > 0 }
        compose.onNodeWithText("What each tag came to in ${thisYear.label}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Year by tag").performClick()
        assertEquals(1, env.navigator.yearByTagOpened)
    }

    @Test
    fun `with no tags it says how to start`() {
        show()
        compose.onNodeWithText("No tags yet").assertIsDisplayed()
    }

    @Test
    fun `the year can start in January instead, from the page itself`() {
        seed()
        show()
        compose.onNodeWithText("Year starts in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("April · India's financial year, April to March").assertIsDisplayed()
        compose.onNodeWithText("Year starts in").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("April") and radio).assertIsSelected()
        compose.onNode(hasText("January") and radio).performClick()
        compose.waitForIdle()
        assertEquals(YearStart.January, env.preferences.state.value.yearStart)
        val calendar = FinancialYear.containing(today, Month.JANUARY)
        compose.onNodeWithContentDescription("${calendar.label}, ${calendar.span}, so far").assertIsDisplayed()
        compose.onNodeWithText("January · The calendar year, January to December").assertIsDisplayed()
    }

    // ============================================================================================== the setting

    @Test
    fun `the year starts in April until another is picked, under a key that never changes`() {
        assertEquals(YearStart.April, env.preferences.state.value.yearStart)
        assertFalse(env.prefsStore.contains("year_start"), "a default is not stored: only a pick is")
        assertEquals(listOf("April", "January"), YearStart.entries.map { it.name }, "the names are what is stored")
        assertEquals(listOf(Month.APRIL, Month.JANUARY), YearStart.entries.map { it.month })
        env.preferences.setYearStart(YearStart.January)
        assertEquals("January", env.prefsStore.getString("year_start", null))
        assertEquals(YearStart.January, AppPreferences(env.prefsStore).state.value.yearStart, "read back by the next start")
        env.prefsStore.edit().putString("year_start", "July").commit()
        assertEquals(YearStart.April, AppPreferences(env.prefsStore).state.value.yearStart, "a value no version knows falls back")
        env.preferences.setYearStart(YearStart.January)
        env.preferences.resetToDefaults()
        assertEquals(YearStart.April, env.preferences.state.value.yearStart)
    }

    @Test
    fun `an app backup carries it`() {
        env.preferences.setYearStart(YearStart.January)
        val exported = env.preferences.exportAll()
        assertEquals(PrefValue.StringPref("January"), exported["year_start"])
        val read = assertNotNull(SettingsBackupCodec.decode(SettingsBackupCodec.encode(exported)))
        val other = TestEnvironment("year_by_tag_other")
        try {
            assertEquals(0, other.preferences.importAll(read.values))
            assertEquals(YearStart.January, other.preferences.state.value.yearStart)
        } finally {
            other.close()
        }
    }

    @Test
    fun `Settings search finds the year start and the tags`() {
        for (query in listOf("financial year", "fiscal", "april", "january", "year by tag", "tax year")) {
            val found = SettingsCatalog.search(query).firstOrNull { it.title == YEAR_START_TITLE }
            assertNotNull(found, "'$query' finds Year starts in")
            assertEquals(SettingsPage.Finance, found.page)
        }
        for (query in listOf("tags", "tax-deductible", "reimbursable", "rename tag")) {
            assertNotNull(SettingsCatalog.search(query).firstOrNull { it.title == TAGS_TITLE }, "'$query' finds Tags")
        }
        assertTrue(SettingsCatalog.search("financial year start").none { it.title == "Financial year start" }, "the old entry with no row is gone")
    }

    @Test
    fun `Settings Finance has the year start and the tags, each opening its sheet`() {
        tag("Tax-deductible")
        compose.setContent { env.Content { FinanceSettingsPage() } }
        compose.waitForIdle()
        compose.onNodeWithText("Year starts in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Year starts in").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("January") and radio).performClick()
        compose.waitForIdle()
        assertEquals(YearStart.January, env.preferences.state.value.yearStart)
        compose.onNodeWithText("January · The calendar year, January to December").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("1 tag · rename or delete it").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(TAGS_TITLE).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Edit tags").assertIsDisplayed()
        compose.onNodeWithContentDescription("Rename Tax-deductible").assertIsDisplayed()
    }
}
