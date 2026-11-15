package com.example.normpix.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CardLimitAlert
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.creditRoom
import com.example.normpix.finance.domain.usedPercentOf
import com.example.normpix.notifications.LocalNotifications
import com.example.normpix.notifications.NotificationKind
import com.example.normpix.notifications.Notifications
import com.example.normpix.notifications.NotificationsPage
import com.example.normpix.notifications.ProvideNotifications
import com.example.normpix.notifications.attentionSummary
import com.example.normpix.settings.CardLimitWarning
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.theme.StatusTone
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
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
 * The credit card limit warning as the user meets it: a banner on the Notifications page, under Cards, for each card that has used
 * the share of its limit chosen in Settings > Wallet & Cards (30% unless changed). Made as a budget's is - a warning, an error once
 * over the limit, the share as the ring, what is owed against the limit as its message, what is left as its figure - with a View
 * card button that opens the account. It comes and goes with the balances, and with the setting, with nothing stored.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CardLimitBannerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "card_limit_banner_test")
    private val finance = env.container.finance
    private val now = LocalDateTime.now().withNano(0)

    @Before
    fun setUp() = Locale.setDefault(Locale.US)

    @After
    fun tearDown() = env.close()

    private val inr = MoneyFormatter(CurrencySpec.INR)

    private fun account(name: String, limit: Long?, owed: Long = 0, kind: AccountKind = AccountKind.Card, archived: Boolean = false) =
        Account(name = name, kind = kind, openingBalance = -owed, creditLimit = limit, archived = archived, createdAt = now, updatedAt = now)

    /** Saves a card with a ₹1,00,000 limit owing [owed] (in rupees), the way a card holding a balance is kept: as an opening balance. */
    private fun card(name: String, owed: Long, limit: Long? = 1_00_000, archived: Boolean = false): Account = runBlocking {
        finance.load()
        finance.saveAccount(account(name, limit?.times(100), owed * 100, archived = archived))
    }

    // ---------------------------------------------------------------------------------------------------- the banner

    private fun alertOf(owedRupees: Long, limitRupees: Long = 1_00_000): CardLimitAlert {
        val room = creditRoom(limitRupees * 100, -owedRupees * 100)!!
        return CardLimitAlert(
            account(name = "HDFC Regalia", limit = limitRupees * 100).copy(id = 7),
            room,
            usedPercentOf(room.owed, room.limit),
        )
    }

    @Test
    fun aCardBelowItsLimitIsAWarning_madeAsABudgetsIs() {
        var opened = 0
        val banner = cardLimitBanner(alertOf(owedRupees = 45_000), inr, open = { opened++ })

        assertEquals("card-limit-7", banner.key)
        assertEquals(StatusTone.Warning, banner.tone)
        assertEquals("Limit used", banner.eyebrow)
        assertEquals("HDFC Regalia", banner.title)
        assertEquals("₹45,000 of ₹1,00,000", banner.message)
        val bar = assertNotNull(banner.bar)
        assertEquals(0.45f, bar.fraction, 0.0001f)
        assertEquals("45% used", bar.label)
        assertEquals("45%", bar.short)
        val figure = assertNotNull(banner.figure)
        assertEquals("Available", figure.title)
        assertEquals("₹55,000.00", figure.figure)
        assertEquals("View card", banner.primary.label)
        assertEquals(null, banner.secondary, "no Later for now")
        banner.primary.onClick()
        banner.onOpen()
        assertEquals(2, opened, "its button and the banner itself both open the card")
    }

    @Test
    fun aCardOverItsLimitIsAnError_andSaysByHowMuch() {
        val banner = cardLimitBanner(alertOf(owedRupees = 1_20_000), inr, open = {})

        assertEquals(StatusTone.Error, banner.tone)
        assertEquals("Over the limit", banner.eyebrow)
        assertEquals("₹1,20,000 of ₹1,00,000", banner.message)
        val bar = assertNotNull(banner.bar)
        assertEquals(1f, bar.fraction, "the ring is full, not past full")
        assertEquals("120%", bar.short, "and says the real share")
        assertEquals("120% used", bar.label)
        assertEquals("Over by", banner.figure?.title)
        assertEquals("₹20,000.00", banner.figure?.figure)
    }

    @Test
    fun hiddenAmountsHideEveryMoneyFigure_butNotTheShare() {
        val banner = cardLimitBanner(alertOf(owedRupees = 45_000), MoneyFormatter(CurrencySpec.INR, hidden = true), open = {})

        assertEquals("₹•••• of ₹••••", banner.message)
        assertEquals("₹••••", banner.figure?.figure)
        // Like a budget banner's ring: a share is not an amount.
        assertEquals("45%", banner.bar?.short)
        val said = "${banner.eyebrow}. ${banner.title}. ${banner.message}. ${banner.figure?.title} ${banner.figure?.figure}. ${banner.bar?.label}"
        assertTrue("45,000" !in said && "55,000" !in said && "1,00,000" !in said, said)
    }

    // --------------------------------------------------------------------------------- on the Notifications page

    private var gathered: Notifications = Notifications.None

    private fun show() {
        compose.setContent {
            env.Content {
                ProvideNotifications {
                    val notifications = LocalNotifications.current
                    SideEffect { gathered = notifications }
                    NotificationsPage()
                }
            }
        }
        compose.waitForIdle()
    }

    private val spoken45 = "Limit used. HDFC Regalia. ₹45,000 of ₹1,00,000. Available ₹55,000.00. 45% used"

    private fun waitForBanner(spoken: String = spoken45) = compose.waitUntil(10_000) {
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription(spoken)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun bannerIsThere(spoken: String = spoken45) =
        compose.onAllNodes(hasContentDescription(spoken)).fetchSemanticsNodes().isNotEmpty()

    private fun anyLimitBanner() =
        compose.onAllNodes(hasContentDescription("Limit used", substring = true) or hasContentDescription("Over the limit", substring = true))
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aCardAt45PercentIsOnThePage_underCards_withItsRingMessageAndFigure() {
        val saved = card("HDFC Regalia", owed = 45_000)
        show()
        waitForBanner()

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription(spoken45))
        compose.onNodeWithContentDescription(spoken45).assertIsDisplayed()
        // Under the heading the card-expiry banners are under, not a heading of its own.
        compose.onNodeWithText("Cards").assertIsDisplayed()
        // The ring says how far along it is, inside it; the words a screen reader hears stay the bar's label.
        compose.onNodeWithText("45%", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("45% used", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("HDFC Regalia", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Limit used", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("₹45,000 of ₹1,00,000", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Available", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("₹55,000.00", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("View card", useUnmergedTree = true).assertIsDisplayed()

        // It is one of the notifications the bell counts and Home's Needs attention is made of: a card.
        val item = gathered.items.single { it.key == "card-limit-${saved.id}" }
        assertEquals(NotificationKind.Cards, item.kind)
        assertEquals("1 card", attentionSummary(gathered.items.filter { it.kind == NotificationKind.Cards }))
    }

    @Test
    fun viewCardOpensTheAccount_andSoDoesPressingTheBanner() {
        val saved = card("HDFC Regalia", owed = 45_000)
        show()
        waitForBanner()

        compose.onNodeWithText("View card", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription(spoken45).performClick()
        val id = assertNotNull(saved.id)
        assertEquals(listOf(id, id), env.navigator.accountHistories)
    }

    @Test
    fun aCardPaidDownUnderTheLevelLosesItsBannerAtOnce_andComesBackWhenItIsUsedAgain() {
        val saved = card("HDFC Regalia", owed = 45_000)
        val bank = runBlocking { finance.saveAccount(account("Savings", limit = null, kind = AccountKind.Bank).copy(openingBalance = 2_00_000_00)) }
        show()
        waitForBanner()

        // A payment of ₹20,000 onto the card leaves ₹25,000 owed: 25%, under 30%.
        runBlocking {
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Transfer, amount = 20_000_00, accountId = bank.id!!, toAccountId = saved.id!!,
                    occurredAt = now.minusMinutes(1), createdAt = now, updatedAt = now,
                ),
            )
        }
        compose.waitUntil(10_000) { compose.waitForIdle(); !anyLimitBanner() }
        assertTrue(gathered.items.none { it.kind == NotificationKind.Cards }, "gone from the notifications, not only from the page")

        // Spending ₹6,000 more is 31%, which is warned about again: nothing was kept that says it was dealt with.
        runBlocking {
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Transfer, amount = 6_000_00, accountId = saved.id!!, toAccountId = bank.id!!,
                    occurredAt = now.minusMinutes(1), createdAt = now, updatedAt = now,
                ),
            )
        }
        waitForBanner("Limit used. HDFC Regalia. ₹31,000 of ₹1,00,000. Available ₹69,000.00. 31% used")
    }

    @Test
    fun theLevelDecidesWhichCardsAreListed_andOffListsNone() {
        card("HDFC Regalia", owed = 45_000)
        show()
        waitForBanner()

        compose.runOnIdle { env.preferences.setCardLimitWarning(CardLimitWarning.At50) }
        compose.waitUntil(10_000) { compose.waitForIdle(); !anyLimitBanner() }

        compose.runOnIdle { env.preferences.setCardLimitWarning(CardLimitWarning.At30) }
        waitForBanner()

        compose.runOnIdle { env.preferences.setCardLimitWarning(CardLimitWarning.Off) }
        compose.waitUntil(10_000) { compose.waitForIdle(); !anyLimitBanner() }
        assertTrue(gathered.items.none { it.kind == NotificationKind.Cards }, "Off: no banner at all, however much is owed")
    }

    @Test
    fun aCardOverItsLimitReadsOverTheLimit_andTheMostUsedIsFirst() {
        card("Axis Flipkart", owed = 40_000, limit = 1_00_000)
        card("ICICI Amazon", owed = 1_20_000, limit = 1_00_000)
        show()
        compose.waitUntil(10_000) { compose.waitForIdle(); bannerIsThere("Over the limit. ICICI Amazon. ₹1,20,000 of ₹1,00,000. Over by ₹20,000.00. 120% used") }
        assertTrue(bannerIsThere("Limit used. Axis Flipkart. ₹40,000 of ₹1,00,000. Available ₹60,000.00. 40% used"))

        val limits = gathered.items.filter { it.key.startsWith("card-limit-") }
        assertEquals(listOf("ICICI Amazon", "Axis Flipkart"), limits.map { it.banner.title }, "most used first")
        assertEquals(StatusTone.Error, limits.first().banner.tone)
        assertEquals(StatusTone.Warning, limits.last().banner.tone)
    }

    @Test
    fun aCardWithNoLimit_anArchivedOne_andABankAccountAreNotListed() {
        card("No limit card", owed = 90_000, limit = null)
        card("Old card", owed = 90_000, archived = true)
        runBlocking { finance.saveAccount(account("Overdrawn bank", limit = null, kind = AccountKind.Bank).copy(openingBalance = -90_000_00)) }
        assertTrue(finance.state.value.accounts.first { it.name == "Old card" }.archived, "the card really is archived")
        show()
        compose.waitUntil(10_000) { compose.waitForIdle(); compose.onAllNodesWithText("Notification settings").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(gathered.items.none { it.kind == NotificationKind.Cards })
        assertTrue(!anyLimitBanner())
    }

    @Test
    fun withStoreCardsOffTheLimitWarningStillComes_itIsAboutTheAccount() {
        env.preferences.setWalletCardsEnabled(false)
        card("HDFC Regalia", owed = 45_000)
        show()
        waitForBanner()
        assertTrue(bannerIsThere())
    }

    @Test
    fun hiddenAmountsHideTheMoneyOnThePage_butTheRingStillSaysHowMuchIsUsed() {
        card("HDFC Regalia", owed = 45_000)
        env.preferences.setAmountsHidden(true)
        show()
        waitForBanner("Limit used. HDFC Regalia. ₹•••• of ₹••••. Available ₹••••. 45% used")
        compose.onNodeWithText("₹•••• of ₹••••", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("45%", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("45,000", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText("55,000", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText("1,00,000", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
