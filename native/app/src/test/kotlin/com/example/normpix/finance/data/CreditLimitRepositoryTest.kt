package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.creditRoom
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A credit card can say what it lets its holder borrow. It is kept with the account, only a card has one, it is an amount
 * above zero, it survives a backup - and what is left of it is worked out from the card's balance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreditLimitRepositoryTest {
    private val now = LocalDateTime.of(2026, 9, 15, 12, 0)
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private suspend fun save(name: String, kind: AccountKind, limit: Long? = null, opening: Long = 0): Account =
        repo.saveAccount(Account(name = name, kind = kind, openingBalance = opening, creditLimit = limit, createdAt = now, updatedAt = now))

    private suspend fun stored(id: Long?): Account = repo.accounts().first { it.id == id }

    @Test
    fun aCardKeepsItsLimit_andAnAccountWithoutOneHasNone() = runBlocking<Unit> {
        val card = save("HDFC Credit", AccountKind.Card, limit = 50_000_00, opening = -12_000_00)
        assertEquals(50_000_00L, stored(card.id).creditLimit)
        assertNull(stored(save("Plain card", AccountKind.Card).id).creditLimit)
        assertTrue(repo.accounts().filter { it.id != card.id }.all { it.creditLimit == null })
    }

    @Test
    fun onlyACardHasALimit_forAnythingElseItIsDropped() = runBlocking<Unit> {
        val bank = save("HDFC Savings", AccountKind.Bank, limit = 50_000_00)
        assertNull(stored(bank.id).creditLimit)

        // And a card that stops being one loses it.
        val card = save("Card", AccountKind.Card, limit = 20_000_00)
        repo.saveAccount(stored(card.id).copy(kind = AccountKind.Bank))
        assertNull(stored(card.id).creditLimit)
    }

    @Test
    fun aLimitIsAnAmountAboveZero() = runBlocking<Unit> {
        assertFailsWith<FinanceValidationException> { save("Card", AccountKind.Card, limit = 0) }
        assertFailsWith<FinanceValidationException> { save("Card", AccountKind.Card, limit = -5_000_00) }
    }

    @Test
    fun theLimitCanBeChangedAndRemoved() = runBlocking<Unit> {
        val card = save("Card", AccountKind.Card, limit = 20_000_00)
        repo.saveAccount(stored(card.id).copy(creditLimit = 35_000_00))
        assertEquals(35_000_00L, stored(card.id).creditLimit)
        repo.saveAccount(stored(card.id).copy(creditLimit = null))
        assertNull(stored(card.id).creditLimit)
    }

    @Test
    fun aBackupCarriesTheLimit() = runBlocking<Unit> {
        val card = save("HDFC Credit", AccountKind.Card, limit = 50_000_00)
        val text = FinanceBackupCodec.encode(repo.snapshot(), now)
        val read = FinanceBackupCodec.decode(text)
        val snapshot = assertNotNull(read.snapshot, read.errors.toString())
        assertEquals(50_000_00L, snapshot.accounts.first { it.name == "HDFC Credit" }.creditLimit)

        repo.restore(snapshot)
        assertEquals(50_000_00L, repo.accounts().first { it.name == "HDFC Credit" }.creditLimit)
        assertNotNull(card.id)
    }

    @Test
    fun aBackupWithAnImpossibleLimitIsRejected() = runBlocking<Unit> {
        val card = save("HDFC Credit", AccountKind.Card, limit = 50_000_00)
        val bank = save("HDFC Savings", AccountKind.Bank)
        val snapshot = repo.snapshot()

        fun problems(change: (Account) -> Account) =
            FinanceBackupCodec.validate(snapshot.copy(accounts = snapshot.accounts.map { if (it.id == card.id || it.id == bank.id) change(it) else it }))

        assertTrue(problems { if (it.id == card.id) it.copy(creditLimit = 0) else it }.any { "above zero" in it })
        assertTrue(problems { if (it.id == bank.id) it.copy(creditLimit = 10_000_00) else it }.any { "not a card" in it })
        assertTrue(FinanceBackupCodec.validate(snapshot).isEmpty())
        assertFalse(snapshot.accounts.any { it.creditLimit == null && it.kind == AccountKind.Card })
    }

    @Test
    fun whatIsLeftIsTheLimitLessWhatIsOwed() {
        val room = assertNotNull(creditRoom(limit = 50_000_00, balance = -12_000_00))
        assertEquals(12_000_00L, room.owed)
        assertEquals(38_000_00L, room.available)
        assertFalse(room.overLimit)
        assertEquals(0.24, room.used, 1e-9)

        val over = assertNotNull(creditRoom(limit = 10_000_00, balance = -12_500_00))
        assertTrue(over.overLimit)
        assertEquals(-2_500_00L, over.available)
        assertEquals(1.0, over.used, "a card cannot be more than full")

        // Paid past its bill: money with the bank, which adds to what can be spent.
        val ahead = assertNotNull(creditRoom(limit = 10_000_00, balance = 1_500_00))
        assertEquals(0L, ahead.owed)
        assertEquals(11_500_00L, ahead.available)

        assertNull(creditRoom(limit = null, balance = -1_00))
    }
}
