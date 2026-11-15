package com.example.normpix.finance.domain

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which credit cards are warned about, at each level of the Card limit warning (Off, 30%, 50%, 75%, 90%): a card at the level
 * exactly warns, one a paisa under does not; a card with no limit (or a zero one), an archived card, one in credit and one that
 * owes nothing never do; one past its limit is over it; and the most used comes first.
 */
class CardLimitTest {
    private val now = LocalDateTime.of(2026, 11, 15, 12, 0)

    private val limit = 1_00_000_00L // ₹1,00,000

    private fun card(id: Long?, name: String = "Card $id", limit: Long? = this.limit, archived: Boolean = false) =
        Account(id = id, name = name, kind = AccountKind.Card, creditLimit = limit, archived = archived, createdAt = now, updatedAt = now)

    /** [cards] with what each owes (a card's balance is minus what it owes); alerts at [percent]. */
    private fun alerts(percent: Int, vararg cards: Pair<Account, Long>): List<CardLimitAlert> {
        val balances = cards.associate { (account, owed) -> account.id to -owed }
        return cardLimitAlerts(cards.map { it.first }, { id -> balances[id] ?: 0L }, percent)
    }

    @Test
    fun aCardExactlyAtTheLevelWarns_andOneAPaisaUnderDoesNot() {
        val card = card(1)
        for (level in listOf(30, 50, 75, 90)) {
            val exactly = limit * level / 100
            assertEquals(listOf(1L), alerts(level, card to exactly).map { it.account.id }, "$level% is warned about at exactly $level%")
            assertEquals(emptyList(), alerts(level, card to exactly - 1), "a paisa under $level% is not")
            assertEquals(listOf(1L), alerts(level, card to exactly + 1).map { it.account.id }, "a paisa over $level% is")
        }
    }

    @Test
    fun eachLevelWarnsAboutWhatIsAtOrPastIt() {
        val a = card(1, "A") // 29%
        val b = card(2, "B") // 30%
        val c = card(3, "C") // 55%
        val d = card(4, "D") // 80%
        val e = card(5, "E") // 95%
        val all = arrayOf(a to 29_000_00L, b to 30_000_00L, c to 55_000_00L, d to 80_000_00L, e to 95_000_00L)
        fun at(level: Int) = alerts(level, *all).map { it.account.name }

        assertEquals(listOf("E", "D", "C", "B"), at(30))
        assertEquals(listOf("E", "D", "C"), at(50))
        assertEquals(listOf("E", "D"), at(75))
        assertEquals(listOf("E"), at(90))
    }

    @Test
    fun offWarnsAboutNothing() {
        val over = card(1)
        assertEquals(emptyList(), alerts(0, over to limit * 2))
        assertEquals(emptyList(), alerts(-5, over to limit * 2))
    }

    @Test
    fun aCardWithNoLimit_aZeroLimit_orArchivedIsNeverWarnedAbout() {
        assertEquals(emptyList(), alerts(30, card(1, limit = null) to 9_00_000_00L), "no limit: there is nothing to be a share of")
        assertEquals(emptyList(), alerts(30, card(2, limit = 0) to 1_00L), "a zero limit")
        assertEquals(emptyList(), alerts(30, card(3, limit = -5_000_00) to 1_00L), "a limit that is not one")
        assertEquals(emptyList(), alerts(30, card(4, archived = true) to limit * 2), "an archived card, however much it owes")
        assertEquals(emptyList(), alerts(30, card(null) to limit), "an account that was never saved")
        // The one that is fine is still there beside them.
        assertEquals(
            listOf(5L),
            alerts(30, card(1, limit = null) to limit, card(4, archived = true) to limit, card(5) to limit / 2).map { it.account.id },
        )
    }

    @Test
    fun aCardThatOwesNothingOrIsInCreditIsNotWarnedAbout() {
        assertEquals(emptyList(), alerts(30, card(1) to 0L))
        // Paid past its bill: the bank holds money, which is room, not use.
        assertEquals(emptyList(), cardLimitAlerts(listOf(card(2)), { 5_000_00L }, 30))
    }

    @Test
    fun pastTheLimitIsOver_andExactlyAtItIsNot() {
        val at = alerts(30, card(1) to limit).single()
        assertFalse(at.over, "owing exactly the limit leaves nothing available, not less than nothing")
        assertEquals(0L, at.room.available)
        assertEquals(100, at.usedPercent)

        val over = alerts(30, card(2) to 1_20_000_00L).single()
        assertTrue(over.over)
        assertEquals(-20_000_00L, over.room.available)
        assertEquals(120, over.usedPercent, "says the real share, past 100")
        assertEquals(1.0, over.room.used, "the ring is full, not past full")
    }

    @Test
    fun theMostUsedComesFirst_andTiesGoByName() {
        val result = alerts(
            30,
            card(1, "Zeta") to 40_000_00L,
            card(2, "Alpha") to 90_000_00L,
            card(3, "Mid") to 40_000_00L,
            card(4, "Beta", limit = 50_000_00) to 40_000_00L, // 80% of a smaller limit
        )
        assertEquals(listOf("Alpha", "Beta", "Mid", "Zeta"), result.map { it.account.name })
    }

    @Test
    fun theShareShownIsTheNearestWholePercent_andNeverUnderTheLevel() {
        assertEquals(0, usedPercentOf(0, limit))
        assertEquals(0, usedPercentOf(1_000_00, 0), "a limit that is not positive")
        assertEquals(30, usedPercentOf(30_000_00, limit))
        assertEquals(45, usedPercentOf(44_600_00, limit), "44.6% reads 45%")
        assertEquals(44, usedPercentOf(44_400_00, limit), "44.4% reads 44%")
        assertEquals(45, usedPercentOf(44_500_00, limit), "half rounds up")
        // The alert's own figure is never below the level that raised it.
        for (level in listOf(30, 50, 75, 90)) {
            val owed = limit * level / 100
            assertTrue(alerts(level, card(1) to owed).single().usedPercent >= level)
        }
    }

    @Test
    fun aCardPaidDownUnderTheLevelIsNotListedAtTheNextLook() {
        val card = card(1)
        var owed = 45_000_00L
        fun now() = cardLimitAlerts(listOf(card), { -owed }, 30).map { it.usedPercent }
        assertEquals(listOf(45), now())
        owed -= 10_000_00L // 35%
        assertEquals(listOf(35), now())
        owed -= 10_000_00L // 25%
        assertEquals(emptyList(), now(), "worked out from the balance each time: nothing was stored to clear")
        owed = 31_000_00L // spent again
        assertEquals(listOf(31), now())
    }
}
