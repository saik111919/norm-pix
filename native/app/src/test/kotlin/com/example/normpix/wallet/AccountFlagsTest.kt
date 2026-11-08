package com.example.normpix.wallet

import com.example.normpix.finance.domain.AccountKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What an account face says besides its name and balance. Classic prints a pill for each of these; the new designs lay the same list
 * out their own ways and must not drop one (the Banner's old else-if chain showed only the first it met).
 */
class AccountFlagsTest {
    private fun account(
        standing: String? = null,
        due: String? = null,
        dueSoon: Boolean = false,
        credit: String? = null,
        creditUsed: Float? = null,
        overLimit: Boolean = false,
        isDefault: Boolean = false,
        cards: Int = 0,
        archived: Boolean = false,
    ) = AccountCardData(
        id = 1L, name = "Everyday", kind = AccountKind.Bank, bankName = "Sample Bank", balance = "₹1,000", standing = standing,
        isDefault = isDefault, archived = archived, cards = cards, credit = credit, creditSpoken = credit, overLimit = overLimit,
        creditUsed = creditUsed, due = due, dueSoon = dueSoon, dueSpoken = due, share = "", hidden = false,
    )

    private fun texts(data: AccountCardData, compact: Boolean = false) = accountFlags(data, compact).map { it.text }

    @Test
    fun everyThingTheFaceCanSayIsThere_inClassicsOrder() {
        val all = account(standing = "Owed", due = "Due 20 Nov", credit = "₹74,000 left", isDefault = true, cards = 2, archived = true)
        assertEquals(listOf("OWED", "DUE 20 NOV", "₹74,000 LEFT", "DEFAULT", "2 CARDS", "ARCHIVED"), texts(all))
    }

    @Test
    fun theLimitIsSaidByTheMeterNotAPill_whenThereIsAMeter() {
        val withMeter = account(credit = "₹74,000 left", creditUsed = 0.38f, isDefault = true)
        assertEquals(listOf("DEFAULT"), texts(withMeter))
    }

    @Test
    fun oneCardIsSingular_andNoCardsIsNothing() {
        assertEquals(listOf("1 CARD"), texts(account(cards = 1)))
        assertEquals(emptyList(), texts(account()))
    }

    @Test
    fun theCarouselsPrintIsOnlyDefaultAndArchived() {
        val all = account(standing = "Owed", due = "Due 20 Nov", credit = "₹74,000 left", isDefault = true, cards = 2, archived = true)
        assertEquals(listOf("DEFAULT", "ARCHIVED"), texts(all, compact = true))
        assertEquals(emptyList(), texts(account(standing = "Owed", cards = 3), compact = true))
    }

    @Test
    fun tonesFollowClassic() {
        fun tone(data: AccountCardData, text: String) = accountFlags(data, false).first { it.text == text }.tone
        assertEquals(FlagTone.Owed, tone(account(standing = "Owed"), "OWED"))
        assertEquals(FlagTone.Credit, tone(account(standing = "In credit"), "IN CREDIT"))
        assertEquals(FlagTone.Owed, tone(account(due = "Due tomorrow", dueSoon = true), "DUE TOMORROW"))
        assertEquals(FlagTone.Quiet, tone(account(due = "Due 20 Nov"), "DUE 20 NOV"))
        assertEquals(FlagTone.Owed, tone(account(credit = "Over by ₹500", overLimit = true), "OVER BY ₹500"))
        assertEquals(FlagTone.Default, tone(account(isDefault = true), "DEFAULT"))
    }
}
