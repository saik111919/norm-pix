package com.example.normpix.wallet

import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.expiry.CardExpiryTracker
import com.example.normpix.wallet.expiry.ExpiryNotifier
import com.example.normpix.wallet.expiry.ExpiryScheduler
import com.example.normpix.wallet.expiry.CardExpiryRecord
import com.example.normpix.wallet.expiry.InMemoryCardExpiryStore
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class RecordingScheduler : ExpiryScheduler {
    val alarms = mutableMapOf<String, Long>()
    var failToCancel = false

    override fun schedule(cardId: String, atMillis: Long) {
        alarms[cardId] = atMillis
    }

    override fun cancel(cardId: String) {
        if (failToCancel) error("The alarm could not be cancelled.")
        alarms.remove(cardId)
    }
}

private class QuietNotifier : ExpiryNotifier {
    val cancelled = mutableListOf<String>()

    override fun areEnabled() = true

    override fun show(record: CardExpiryRecord, expired: Boolean) = true

    override fun cancel(cardId: String) {
        cancelled += cardId
    }
}

/**
 * Turning Store cards off deletes everything about cards: the vault, the expiry reminders, the copied card detail, the NFC
 * tags registered for revealing cards and every account's link to a card - and the accounts themselves stay. If the vault
 * can't be erased nothing at all changes; if something around it can't be cleaned up, the result says what, and doing it
 * again finishes the job.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardsWipeTest {
    private val now = LocalDateTime.of(2026, 10, 12, 9, 0)
    private val tag = byteArrayOf(0x04, 0x5A, 0x1C, 0x32, 0x7B, 0x66, 0x80.toByte())

    private val vault = InMemoryVault(
        listOf(
            StoredCard("rupay", "HDFC Bank", "THE USER", "6528505555554455", "08", "29", "123", CardKind.Credit, now),
            StoredCard("millennia", "HDFC Bank", "THE USER", "5282000011112222", "03", "30", "", CardKind.Debit, now),
        ),
    )
    private val store = InMemoryCardExpiryStore()
    private val scheduler = RecordingScheduler()
    private val notifier = QuietNotifier()
    private val env = TestEnvironment(
        prefsName = "cards_wipe_test",
        vault = vault,
        expiry = CardExpiryTracker(store, scheduler, notifier),
    )

    @AfterTest
    fun tearDown() = env.close()

    private fun account(name: String, kind: AccountKind, cardId: String? = null, ending: String? = null): Account = runBlocking {
        env.container.finance.saveAccount(
            Account(name = name, kind = kind, numberEnding = ending, cardId = cardId, createdAt = now, updatedAt = now),
        )
    }

    private fun accounts(): List<Account> = runBlocking { env.container.finance.repository.accounts() }

    /** Cards, reminders, a registered tag and a copied detail: everything a wipe has to reach. */
    private fun setUpEverything() {
        env.container.expiry.sync(vault.cards)
        env.registeredTags.register(tag)
        env.preferences.setNfcRevealRegisteredOnly(true)
        account("HDFC RuPay", AccountKind.Card, cardId = "rupay", ending = "4455")
        account("HDFC Savings", AccountKind.Bank, cardId = "millennia")
        account("Cash", AccountKind.Cash)
        assertEquals(setOf("rupay", "millennia"), store.all().keys)
    }

    @Test
    fun `erasing the cards leaves no card, reminder, tag or link, and keeps the accounts`() = runBlocking<Unit> {
        setUpEverything()

        val result = eraseCards(env.container)

        assertEquals(CardsWipe.Erased(emptyList()), result)
        assertTrue((result as CardsWipe.Erased).complete)
        // The vault, and what the app shows of it.
        assertTrue(vault.cards.isEmpty())
        assertEquals(CardsState.Ready(emptyList()), env.container.cards.state.value)
        // Expiry reminders: records, alarms and notifications.
        assertTrue(store.all().isEmpty())
        assertTrue(scheduler.alarms.isEmpty())
        assertEquals(setOf("rupay", "millennia"), notifier.cancelled.toSet())
        assertTrue(env.container.expiry.states.value.isEmpty())
        // The clipboard was asked to let go of any card detail.
        assertEquals(1, env.clipboard.clears)
        // Registered tags are gone, and the reveal gate is back to any tag.
        assertEquals(0, env.registeredTags.count.value)
        assertFalse(env.registeredTags.matches(tag))
        assertFalse(env.preferences.state.value.nfcRevealRegisteredOnly)
        // Accounts stay, unlinked; their own digits are theirs.
        val after = accounts()
        assertEquals(setOf("HDFC RuPay", "HDFC Savings", "Cash"), after.map { it.name }.toSet())
        assertTrue(after.all { it.cardId == null })
        assertEquals("4455", after.first { it.name == "HDFC RuPay" }.numberEnding)
    }

    @Test
    fun `a vault that can't be erased changes nothing at all`() = runBlocking<Unit> {
        setUpEverything()
        vault.failToClear = true

        assertEquals(CardsWipe.VaultFailed, eraseCards(env.container))

        assertEquals(listOf("rupay", "millennia"), vault.cards.map { it.id })
        assertEquals(listOf("rupay", "millennia"), env.container.cards.cards.map { it.id })
        assertEquals(setOf("rupay", "millennia"), store.all().keys)
        assertEquals(0, env.clipboard.clears)
        assertEquals(1, env.registeredTags.count.value)
        assertTrue(env.registeredTags.matches(tag))
        assertTrue(env.preferences.state.value.nfcRevealRegisteredOnly)
        assertEquals(listOf("millennia", "rupay"), accounts().mapNotNull { it.cardId }.sorted())
    }

    @Test
    fun `when only the reminders can't be cancelled the cards are still gone, it says so, and doing it again finishes`() = runBlocking<Unit> {
        setUpEverything()
        scheduler.failToCancel = true

        val first = assertIs<CardsWipe.Erased>(eraseCards(env.container))

        assertEquals(listOf("the card-expiry reminders"), first.leftOver)
        assertFalse(first.complete)
        // Every card is gone, and the steps that could be done were.
        assertTrue(vault.cards.isEmpty())
        assertEquals(0, env.registeredTags.count.value)
        assertTrue(accounts().all { it.cardId == null })
        // The reminders' records are still there to be found, so a second go can cancel what the first could not.
        assertEquals(setOf("rupay", "millennia"), store.all().keys)

        scheduler.failToCancel = false
        val second = assertIs<CardsWipe.Erased>(eraseCards(env.container))

        assertTrue(second.complete)
        assertTrue(store.all().isEmpty())
        assertTrue(scheduler.alarms.isEmpty())
    }

    @Test
    fun `erasing when there is nothing to erase is fine`() = runBlocking<Unit> {
        vault.cards = emptyList()
        env.container.cards.load()

        val result = assertIs<CardsWipe.Erased>(eraseCards(env.container))

        assertTrue(result.complete)
        assertNull(accounts().firstOrNull { it.cardId != null })
    }

    @Test
    fun `an unreadable vault is erased all the same`() = runBlocking<Unit> {
        // Erasing never reads the cards first, so a vault that can't be opened (a lost key) can still be cleared.
        val broken = object : CardVault {
            var erased = false
            override suspend fun loadCards(): List<StoredCard> = if (erased) emptyList() else throw CardVaultException("The vault can't be opened.")
            override suspend fun saveCards(cards: List<StoredCard>) {
                throw CardVaultException("The vault can't be written.")
            }
            override suspend fun clear() {
                erased = true
            }
        }
        val controller = CardsController(broken)
        controller.load()
        assertIs<CardsState.Failed>(controller.state.value)

        assertTrue(controller.deleteAll())

        assertTrue(broken.erased)
        assertEquals(CardsState.Ready(emptyList()), controller.state.value)
    }
}
