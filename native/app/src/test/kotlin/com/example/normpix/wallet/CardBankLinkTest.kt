package com.example.normpix.wallet

import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun card(id: String, bank: String, bankKey: String? = null, number: String = "4111111111114242") = StoredCard(
    id = id,
    bankName = bank,
    cardholderName = "Asha Rao",
    cardNumber = number,
    expiryMonth = "11",
    expiryYear = "29",
    cvv = "737",
    kind = CardKind.Credit,
    createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
    bankKey = bankKey,
)

/** A card's bank link as it is stored: optional, so every vault written before banks were shared still opens. */
class StoredCardBankTest {
    @Test
    fun theBankKeyRoundTripsThroughTheVaultsJson() {
        val linked = card("a", "HDFC Bank", bankKey = "hdfc")
        assertEquals(linked, StoredCard.fromJson(linked.toJson()))
        assertEquals("hdfc", (linked.toJson()["bankKey"] as JsonPrimitive).content)
    }

    @Test
    fun aCardWithoutABankWritesNoKeyAndAnOldCardReadsAsUnlinked() {
        val unlinked = card("a", "HDFC Bank")
        assertFalse("bankKey" in unlinked.toJson())
        assertNull(StoredCard.fromJson(unlinked.toJson()).bankKey)

        val old = JsonObject(unlinked.toJson().filterKeys { it != "bankKey" })
        assertEquals(unlinked, StoredCard.fromJson(old))
    }

    @Test
    fun theLinkIsNotACardDetailThatLeaks() {
        assertEquals("StoredCard(id=a)", card("a", "HDFC Bank", bankKey = "hdfc").toString())
    }
}

/** Cards saved before banks were shared get the bank their name stands for, without losing or changing anything else. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardBankLinkTest {
    private val vault = InMemoryVault()
    private val env = TestEnvironment(prefsName = "card_bank_link_test", vault = vault)
    private val finance = env.container.finance
    private val cards = env.container.cards

    @AfterTest
    fun tearDown() = env.close()

    private fun load(vararg list: StoredCard) = runBlocking {
        vault.cards = list.toList()
        cards.load()
    }

    private fun stored(id: String) = vault.cards.first { it.id == id }

    @Test
    fun aCardIsLinkedToTheBankItsNameStandsFor() = runBlocking {
        load(
            card("a", "HDFC Bank"),
            card("b", "hdfc"),
            card("c", "Axis Bank Platinum"),
            card("d", "ICICI Bank", bankKey = "icici"),
        )
        assertEquals(3, CardBankLinker(cards, finance).link())

        assertEquals("hdfc", stored("a").bankKey)
        assertEquals("hdfc", stored("b").bankKey)
        assertEquals("axis", stored("c").bankKey)
        assertEquals("icici", stored("d").bankKey)
        assertTrue(finance.state.value.customBanks.isEmpty(), "no bank is invented for names the app knows")
    }

    @Test
    fun onlyTheLinkChangesNothingTheCardSaysIsTouched() = runBlocking {
        val original = card("a", "hdfc", number = "4111111111119999")
        load(original)
        CardBankLinker(cards, finance).link()
        assertEquals(original.copy(bankKey = "hdfc"), stored("a"))
        assertEquals("hdfc", stored("a").bankName)
    }

    @Test
    fun aNameTheAppDoesNotKnowBecomesOneBankSharedByEveryCardWithIt() = runBlocking {
        load(card("a", "My Credit Union"), card("b", "my credit union"), card("c", "Other Co-op"))
        CardBankLinker(cards, finance).link()

        val banks = finance.state.value.customBanks
        assertEquals(2, banks.size)
        val shared = assertNotNull(stored("a").bankKey)
        assertEquals(shared, stored("b").bankKey)
        assertTrue(shared.startsWith("custom-"))
        assertEquals(banks.first { it.name == "Other Co-op" }.key, stored("c").bankKey)
        assertEquals("My Credit Union", finance.state.value.catalog.byKey(shared)?.name, "the new bank is in the catalog accounts pick from")
    }

    @Test
    fun aCardWithNoBankNameIsLeftAlone() = runBlocking {
        load(card("a", "  "))
        assertEquals(0, CardBankLinker(cards, finance).link())
        assertNull(stored("a").bankKey)
        assertTrue(finance.state.value.customBanks.isEmpty())
    }

    @Test
    fun linkingTwiceChangesNothingTheSecondTime() = runBlocking {
        load(card("a", "My Credit Union"), card("b", "HDFC Bank"))
        val linker = CardBankLinker(cards, finance)
        assertEquals(2, linker.link())
        val after = vault.cards
        assertEquals(0, linker.link())
        assertEquals(after, vault.cards)
        assertEquals(1, finance.state.value.customBanks.size)
    }

    @Test
    fun aKeyWhoseBankIsGoneIsLinkedAgain() = runBlocking {
        // The finance data was reset: the card still names a bank that no longer exists.
        load(card("a", "My Credit Union", bankKey = "custom-gone"))
        assertEquals(1, CardBankLinker(cards, finance).link())
        val key = assertNotNull(stored("a").bankKey)
        assertTrue(key != "custom-gone")
        assertEquals("My Credit Union", finance.state.value.catalog.byKey(key)?.name)
    }

    @Test
    fun aVaultThatCannotBeWrittenIsNotRetriedInALoop() = runBlocking {
        val failing = object : com.example.normpix.wallet.CardVault {
            var writes = 0
            override suspend fun loadCards() = listOf(card("a", "HDFC Bank"))
            override suspend fun saveCards(cards: List<StoredCard>) {
                writes++
                error("disk full")
            }
        }
        val controller = CardsController(failing)
        controller.load()
        val linker = CardBankLinker(controller, finance)
        assertEquals(0, linker.link())
        assertEquals(0, linker.link())
        assertEquals(1, failing.writes, "tried once, then left alone for this session")
        assertNull(controller.cards.single().bankKey)
    }
}
