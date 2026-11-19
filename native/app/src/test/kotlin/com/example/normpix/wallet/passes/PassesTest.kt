package com.example.normpix.wallet.passes

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.wallet.CardsTransfer
import com.example.normpix.wallet.StoredCard
import com.example.normpix.wallet.VaultKeyProvider
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import javax.crypto.KeyGenerator
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Loyalty, membership and gift cards (2026-11-19): a pass as its vault and a cards file write it, the checks, merging without doubling;
 * its code drawn - and refused where the number can't be one in its format; the encrypted vault and the controller; and the cards file,
 * which carries passes as version 2 while a file of cards alone stays version 1, as it always was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PassesTest {
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)

    private fun pass(
        name: String = "DMart",
        number: String = "5901234123457",
        format: PassCodeFormat = PassCodeFormat.Ean13,
        kind: PassKind = PassKind.Loyalty,
    ) = StoredPass(id = "pass-$name-$number", kind = kind, name = name, number = number, format = format, createdAt = now, updatedAt = now)

    private val gift = StoredPass(
        kind = PassKind.GiftCard, name = "Gift voucher", number = "6061 2204 9918 3307", format = PassCodeFormat.Qr, colorIndex = 3,
        pin = "482913", balance = 75_000, expiry = LocalDate.of(2027, 7, 1), note = "From Asha", createdAt = now, updatedAt = now,
    )

    private val keys = run {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        VaultKeyProvider { key }
    }

    // ============================================================================================ the model

    @Test
    fun `a pass is written and read back whole, a missing optional key is none, and toString never says the number or PIN`() {
        assertEquals(gift, StoredPass.fromJson(gift.toJson()))
        val plain = pass()
        val read = StoredPass.fromJson(plain.toJson())
        assertEquals(plain, read)
        assertFalse("pin" in plain.toJson(), "no PIN, no key")
        val text = gift.toString()
        assertFalse("482913" in text)
        assertFalse("6061" in text)
        assertEquals("3307", gift.ending)
        // A newer app's key is ignored, an unknown kind or format falls back.
        val newer = JsonObject(gift.toJson() + ("future" to Json.parseToJsonElement("1")) + ("kind" to Json.parseToJsonElement("\"club\"")))
        assertEquals(PassKind.Other, StoredPass.fromJson(newer).kind)
    }

    @Test
    fun `a pass needs a name and a number, a short note and no negative balance`() {
        assertNull(pass().fault())
        assertNotNull(pass(name = " ").fault())
        assertNotNull(pass(number = "").fault())
        assertNotNull(pass().copy(note = "x".repeat(StoredPass.MAX_NOTE + 1)).fault())
        assertNotNull(gift.copy(balance = -1).fault())
    }

    @Test
    fun `merging adds what is new and leaves what is there, by id or by name and number`() {
        val kept = listOf(pass())
        val (added, already) = mergePasses(kept, listOf(pass().copy(id = "other"), gift, pass(name = "dmart ").copy(id = "third")))
        assertEquals(listOf(gift), added, "the same name and number is the same card")
        assertEquals(2, already)
    }

    // ============================================================================================ codes

    @Test
    fun `every format draws a number it can hold`() {
        val samples = mapOf(
            PassCodeFormat.Code128 to "BB4417-2093",
            PassCodeFormat.Qr to "https://example.com/m/12345",
            PassCodeFormat.Ean13 to "5901234123457",
            PassCodeFormat.Ean8 to "96385074",
            PassCodeFormat.UpcA to "036000291452",
            PassCodeFormat.UpcE to "01234565",
            PassCodeFormat.Code39 to "ABC-123",
            PassCodeFormat.Code93 to "ABC-123",
            PassCodeFormat.Codabar to "A40156B",
            PassCodeFormat.Itf to "00012345678905",
            PassCodeFormat.Pdf417 to "MEMBER 12345",
            PassCodeFormat.Aztec to "MEMBER 12345",
            PassCodeFormat.DataMatrix to "MEMBER 12345",
        )
        assertEquals(PassCodeFormat.entries.toSet() - PassCodeFormat.None, samples.keys, "every drawable format is tried")
        for ((format, number) in samples) {
            val matrix = assertNotNull(passCodeMatrix(number, format), "$format draws $number")
            assertNull(passCodeFault(number, format))
            if (!format.twoDimensional) {
                assertEquals(1, matrix.height, "$format is one row")
                assertTrue(barsOf(matrix).isNotEmpty())
            }
        }
        assertNull(passCodeMatrix("anything", PassCodeFormat.None), "number only: no code")
        assertNull(passCodeFault("anything", PassCodeFormat.None))
    }

    @Test
    fun `a number its format can't hold draws nothing and says why`() {
        assertNull(passCodeMatrix("5901234123458", PassCodeFormat.Ean13), "a wrong check digit")
        assertNull(passCodeMatrix("59012341234AB", PassCodeFormat.Ean13), "letters in an EAN")
        assertTrue(passCodeFault("123", PassCodeFormat.Ean8)!!.contains("can't be drawn as EAN-8"))
    }

    @Test
    fun `a typed number's code is guessed from its shape`() {
        assertEquals(PassCodeFormat.Ean13, likelyFormatOf("5901234123457"))
        assertEquals(PassCodeFormat.Ean13, likelyFormatOf("590 1234 123457"), "spaces aside")
        assertEquals(PassCodeFormat.Code128, likelyFormatOf("5901234123458"), "13 digits with a wrong check digit are no EAN")
        assertEquals(PassCodeFormat.Ean8, likelyFormatOf("96385074"))
        assertEquals(PassCodeFormat.UpcA, likelyFormatOf("036000291452"))
        assertEquals(PassCodeFormat.Code128, likelyFormatOf("BB4417 2093"))
        assertEquals("5901 2341 2345 7", groupedNumber("5901234123457"))
        assertEquals("BB4417-2093", groupedNumber("BB4417-2093"))
    }

    @Test
    fun `ML Kit's code types are the app's, and one it has no name for is Code 128`() {
        val bc = com.google.mlkit.vision.barcode.common.Barcode::class.java
        fun f(name: String) = bc.getField(name).getInt(null)
        assertEquals(PassCodeFormat.Ean13, MlKitPassCodeReader.formatOf(f("FORMAT_EAN_13")))
        assertEquals(PassCodeFormat.Qr, MlKitPassCodeReader.formatOf(f("FORMAT_QR_CODE")))
        assertEquals(PassCodeFormat.Pdf417, MlKitPassCodeReader.formatOf(f("FORMAT_PDF417")))
        assertEquals(PassCodeFormat.Code128, MlKitPassCodeReader.formatOf(f("FORMAT_CODE_128")))
        assertEquals(PassCodeFormat.Code128, MlKitPassCodeReader.formatOf(-12345))
    }

    // ============================================================================================ the vault and the controller

    @Test
    fun `the vault keeps passes encrypted, reads them back, and an empty or missing file is none`() = runBlocking {
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "passes-test").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "pass_vault.bin")
        val vault = FilePassVault(file, keys)
        assertTrue(vault.load().isEmpty())
        vault.save(listOf(pass(), gift))
        assertEquals(listOf(pass(), gift).map { it.id }, vault.load().map { it.id })
        val bytes = file.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("5901234123457" in bytes, "the number is not in the file in the clear")
        assertFalse("482913" in bytes)
        assertFalse(File(dir, "pass_vault.bin.tmp").exists())
        // Another key can't read it.
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertFailsWith<PassVaultException> { FilePassVault(file, { other }).load() }
        dir.deleteRecursively()
        Unit
    }

    @Test
    fun `the controller adds, edits, deletes and puts back, and never writes over a vault it couldn't read`() = runBlocking {
        val controller = PassesController(InMemoryPassVault())
        controller.load()
        assertTrue(controller.add(pass()))
        assertTrue(controller.add(gift))
        assertTrue(controller.update(gift.copy(balance = 50_000)))
        assertEquals(50_000L, controller.passes.last().balance)
        assertTrue(controller.delete(pass().id))
        assertTrue(controller.restore(pass(), 0))
        assertEquals(listOf(pass().id, gift.id), controller.passes.map { it.id })
        assertEquals(1 to 1, controller.addAll(listOf(gift, pass(name = "Cult.fit", number = "CF-1"))))

        val broken = object : PassVault {
            override suspend fun load(): List<StoredPass> = throw PassVaultException("unreadable")
            override suspend fun save(passes: List<StoredPass>) = error("must not be written")
        }
        val refusing = PassesController(broken)
        assertFalse(refusing.add(pass()))
        assertIs<PassesState.Failed>(refusing.state.value)
        assertNull(refusing.addAll(listOf(pass())))
    }

    // ============================================================================================ the cards file

    private val card = StoredCard(
        id = "card-1", bankName = "Test Bank", cardholderName = "A HOLDER", cardNumber = "4111111111111111", expiryMonth = "12",
        expiryYear = "30", cvv = "", kind = com.example.normpix.wallet.CardKind.entries.first(), createdAt = now,
    )

    private fun versionOf(file: String) = (Json.parseToJsonElement(file) as JsonObject)["version"]!!.jsonPrimitive.content.toInt()

    @Test
    fun `a cards file of cards alone is version 1, as it always was, and one with passes is version 2 and carries them`() {
        val password = "correct horse".toCharArray()
        val cardsOnly = CardsTransfer.encode(listOf(card), password, now, iterations = 10_000)
        assertEquals(1, versionOf(cardsOnly))
        val read = assertIs<CardsTransfer.Result.Cards>(CardsTransfer.decode(cardsOnly, password))
        assertEquals(1, read.cards.size)
        assertTrue(read.passes.isEmpty())

        val withPasses = CardsTransfer.encode(listOf(card), password, now, iterations = 10_000, passes = listOf(pass(), gift))
        assertEquals(2, versionOf(withPasses))
        val both = assertIs<CardsTransfer.Result.Cards>(CardsTransfer.decode(withPasses, password))
        assertEquals(listOf(pass(), gift), both.passes)
        assertEquals(1, both.cards.size)

        val passesOnly = CardsTransfer.encode(emptyList(), password, now, iterations = 10_000, passes = listOf(gift))
        assertEquals(listOf(gift), assertIs<CardsTransfer.Result.Cards>(CardsTransfer.decode(passesOnly, password)).passes)
        assertIs<CardsTransfer.Result.WrongPassword>(CardsTransfer.decode(withPasses, "wrong password".toCharArray()))
        // A version 2 file whose version was changed to 1 doesn't open: the version is bound into the encryption.
        val lowered = withPasses.replace("\"version\":2", "\"version\":1")
        assertTrue(lowered != withPasses)
        assertIs<CardsTransfer.Result.WrongPassword>(CardsTransfer.decode(lowered, password))
    }
}
