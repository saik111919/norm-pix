package com.example.normpix.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.ui.CurrencySheet
import com.example.normpix.settings.ui.SettingsCatalog
import com.example.normpix.testing.TestEnvironment
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** For now the ledger is kept in the Indian rupee alone: the other currencies are listed as coming soon, and can't be chosen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CurrencyAvailabilityTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val picked = mutableListOf<CurrencySpec?>()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("currency_availability")
    }

    @After
    fun tearDown() = env.close()

    private fun showSheet(current: CurrencySpec) {
        compose.setContent { env.Content { CurrencySheet(current = current, onPick = { picked += it }) } }
        compose.waitForIdle()
    }

    @Test
    fun `only the rupee is active, and the rest are still known`() {
        assertEquals(listOf(CurrencySpec.INR), CurrencySpec.active)
        assertTrue(CurrencySpec.isActive("INR"))
        for (other in CurrencySpec.all.filter { it.code != "INR" }) assertFalse(CurrencySpec.isActive(other.code), other.code)
        // Still in the catalogue: a ledger kept in another currency before, or a backup of one, is still shown and formatted.
        assertTrue(CurrencySpec.all.size > 1)
        assertEquals("$1,234.50", MoneyFormatter(CurrencySpec.byCode("USD")).format(123_450))
    }

    @Test
    fun `the sheet lets the rupee be picked`() {
        showSheet(CurrencySpec.INR)
        compose.onNodeWithText("Indian rupee").assertIsEnabled().assertIsSelected()
        compose.onNodeWithText("Indian rupee").performClick()
        assertEquals(listOf<CurrencySpec?>(CurrencySpec.INR), picked)
    }

    @Test
    fun `the others are shown as coming soon, and can't be picked`() {
        showSheet(CurrencySpec.INR)
        compose.onNodeWithText("Only the Indian rupee is available for now. The others are on their way.").assertExists()
        compose.onAllNodesWithText("Coming soon", substring = true).assertCountEquals(CurrencySpec.all.size - 1)
        for (other in CurrencySpec.all.filter { it.code != "INR" }) {
            compose.onNodeWithText(other.name).assertIsNotEnabled().assertIsNotSelected()
            compose.onNodeWithText(other.name).performClick()
        }
        assertEquals(emptyList(), picked, "not one of them was picked")
    }

    @Test
    fun `a ledger already kept in another currency can still go back to the rupee, and shows what it has`() {
        showSheet(CurrencySpec.byCode("USD"))
        compose.onNodeWithText("US dollar").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("Indian rupee").assertIsEnabled().assertIsNotSelected()
        compose.onNodeWithText("Indian rupee").performClick()
        assertEquals(listOf<CurrencySpec?>(CurrencySpec.INR), picked)
    }

    @Test
    fun `settings search finds the currency on the Finance page`() {
        for (query in listOf("currency", "rupee", "inr", "dollar")) {
            val found = SettingsCatalog.search(query).single { it.title == "Currency" }
            assertEquals(SettingsPage.Finance, found.page, query)
        }
    }
}
