package com.example.normpix.wallet

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.CardDesign
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.rememberDeckStackState
import com.example.normpix.ui.theme.AppRadius
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the card designs really look like, in every theme the app has, and the rules a picture can prove:
 *
 *  - a face fills its own corners (a card is clipped at [AppRadius.lg] by the stacks, the carousel and the hold ring; a face with
 *    rounder corners than that shows whatever is behind it in the gap - the card behind, in a stack);
 *  - the themes really apply (the PNGs of Pure and Volt are not the dark ones under another name).
 *
 * The PNGs go to `app/build/qa_card_designs/<scene>_<design>_<theme>.png` to be looked at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xhdpi")
class CardDesignRenderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "card_design_render")
    private val outDir = File("build/qa_card_designs").also { it.mkdirs() }

    private fun card(id: String, bank: String, number: String, kind: CardKind, name: String = "") = StoredCard(
        id = id, bankName = bank, cardholderName = "Asha Rao", cardNumber = number, expiryMonth = "07", expiryYear = "29",
        cvv = "737", kind = kind, createdAt = LocalDateTime.of(2026, 1, 1, 0, 0), cardName = name,
    )

    private val cards = listOf(
        card("a", "HDFC Bank", "4111111111114242", CardKind.Debit, "Millennia"),
        card("b", "ICICI Bank", "5555555555554444", CardKind.Credit),
        card("c", "Axis Bank", "4000056655665556", CardKind.Debit),
    )

    /** An account with every flag a face can carry, so a face that drops one shows it. */
    private val account = AccountCardData(
        id = 1L, name = "Primary Savings", kind = AccountKind.Bank, bankName = "HDFC Bank", balance = "₹42,500",
        standing = "Owed", isDefault = true, archived = false, cards = 2, credit = "₹74,000 left", creditSpoken = "74,000 rupees left",
        overLimit = false, creditUsed = 0.38f, due = "Due 20 Nov", dueSoon = false, dueSpoken = "due 20 November", share = "45%", hidden = false,
    )

    private enum class Look(val label: String, val mode: ThemeMode, val accent: AccentColor) {
        Light("light", ThemeMode.Light, AccentColor.Green),
        Dark("dark", ThemeMode.Dark, AccentColor.Green),
        Pure("pure", ThemeMode.Dark, AccentColor.Pure),
        VoltLight("volt_light", ThemeMode.Light, AccentColor.Volt),
        VoltDark("volt_dark", ThemeMode.Dark, AccentColor.Volt),
    }

    private val designs = listOf(CardDesign.Classic, CardDesign.Banner, CardDesign.Ledger, CardDesign.Tinted)

    private var probeWidth by mutableStateOf(328.dp)
    private var design by mutableStateOf(CardDesign.Classic)
    private var scene by mutableStateOf("front")

    @AfterTest
    fun tearDown() = env.close()

    private fun setLook(look: Look) {
        env.preferences.setMode(look.mode)
        env.preferences.setAccent(look.accent)
        compose.waitForIdle()
    }

    @Composable
    private fun Scene() {
        val card = cards[0]
        val theme = remember(card) { BankCardDesign.forCard(card) }
        when (scene) {
            "stack" -> Box(Modifier.padding(16.dp).testTag("scene")) {
                CardFolderStack(cards = cards, onOpen = {}, cardDesign = design)
            }
            "deck" -> Box(Modifier.padding(16.dp).testTag("scene")) {
                CardStack(cards = cards, onOpen = {}, state = rememberDeckStackState(), cardDesign = design)
            }
            else -> Box(Modifier.testTag("scene").padding(16.dp)) {
                Column {
                    // The probe is a card on a magenta ground, clipped as every deck clips it: anything left unpainted shows.
                    Box(Modifier.testTag("probe").width(probeWidth).aspectRatio(CARD_ASPECT).background(Color.Magenta).clip(AppRadius.rLg)) {
                        when (scene) {
                            "account" -> AccountCardFacePreview(account, cardDesign = design)
                            "compact" -> AccountCardFacePreview(account, compact = true, cardDesign = design)
                            "back" -> when (design) {
                                CardDesign.Banner -> CardBackBanner(card, theme, false, bottomInset = 0.dp)
                                CardDesign.Ledger -> CardBackLedger(card, theme, false, bottomInset = 0.dp)
                                CardDesign.Tinted -> CardBackTinted(card, theme, false, bottomInset = 0.dp)
                                CardDesign.Classic -> WalletCardFacePreview(card, revealed = false, cardDesign = design)
                            }
                            else -> WalletCardFacePreview(card, revealed = false, cardDesign = design)
                        }
                    }
                }
            }
        }
    }

    private fun start() {
        compose.setContent { env.Content { Surface(color = MaterialTheme.colorScheme.background) { Scene() } } }
        compose.waitForIdle()
    }

    private fun capture(tag: String): ImageBitmap = compose.onNodeWithTag(tag).captureToImage()

    private fun dump(image: ImageBitmap, name: String): String {
        val file = File(outDir, "$name.png")
        file.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        return MessageDigest.getInstance("MD5").digest(file.readBytes()).joinToString("") { "%02x".format(it) }.take(10)
    }

    /** The corners of [image] (a probe, 328 dp wide) that are still the magenta ground 5 dp in from each corner. */
    private fun unpaintedCorners(image: ImageBitmap): List<String> {
        val pixels = image.toPixelMap()
        val inset = (5 * compose.density.density).roundToInt()
        val points = mapOf(
            "top-left" to (inset to inset),
            "top-right" to (image.width - 1 - inset to inset),
            "bottom-left" to (inset to image.height - 1 - inset),
            "bottom-right" to (image.width - 1 - inset to image.height - 1 - inset),
        )
        return points.filter { (_, p) -> pixels[p.first, p.second].let { it.red > 0.78f && it.green < 0.3f && it.blue > 0.78f } }.keys.toList()
    }

    @Test
    fun everyFaceFillsItsCorners() {
        start()
        val failures = mutableListOf<String>()
        for (look in listOf(Look.Light, Look.VoltDark)) {
            setLook(look)
            for (d in designs) {
                design = d
                for (s in listOf("front", "back", "account", "compact")) {
                    scene = s
                    compose.waitForIdle()
                    val bad = unpaintedCorners(capture("probe"))
                    if (bad.isNotEmpty()) failures += "${d.name} $s (${look.label}): corners left unpainted: $bad"
                }
            }
        }
        assertTrue(failures.isEmpty(), "A face does not fill the card's own corners:\n" + failures.joinToString("\n"))
    }

    /**
     * A flat design has no gradient to fill a card wider than its print, so on the Wallet's 380 dp card (a print drawn for 328 dp)
     * a third of the Banner's top panel was blank. The print now scales with the card: the number is the same share of the card's
     * height at every width.
     */
    @Test
    fun theNewDesignsPrintScalesWithTheCard() {
        start()
        scene = "front"
        val failures = mutableListOf<String>()
        for (d in listOf(CardDesign.Banner, CardDesign.Ledger, CardDesign.Tinted)) {
            design = d
            val shares = listOf(300.dp, 328.dp, 396.dp).map { w ->
                probeWidth = w
                compose.waitForIdle()
                val card = compose.onNodeWithTag("probe").fetchSemanticsNode().boundsInRoot
                val number = compose.onNodeWithText("4242", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                number.height / card.height
            }
            if (shares.max() / shares.min() > 1.05f) failures += "${d.name}: the number is $shares of the card's height at 300, 328 and 396 dp"
        }
        probeWidth = 328.dp
        assertTrue(failures.isEmpty(), "The print does not scale with the card:\n" + failures.joinToString("\n"))
    }

    @Test
    fun theThemesReallyApply_andEveryScenePicturesIsDumped() {
        start()
        val hashes = mutableMapOf<String, String>()
        // The stacks animate and are slow to settle: they are pictured in two looks, the faces in all five.
        val stackLooks = setOf(Look.Light, Look.VoltDark)
        for (look in Look.entries) {
            setLook(look)
            for (d in designs) {
                design = d
                val scenes = listOf("front", "back", "account", "compact") + if (look in stackLooks) listOf("stack", "deck") else emptyList()
                for (s in scenes) {
                    scene = s
                    compose.waitForIdle()
                    hashes["${s}_${d.name}_${look.label}"] = dump(capture("scene"), "${s}_${d.name.lowercase()}_${look.label}")
                }
            }
        }
        // Banner and Ledger are painted from the theme's own colours alone, so Dark, Pure and Volt dark must not be the same picture.
        for (d in listOf(CardDesign.Banner, CardDesign.Ledger)) {
            for (s in listOf("front", "account")) {
                val dark = hashes.getValue("${s}_${d.name}_dark")
                val pure = hashes.getValue("${s}_${d.name}_pure")
                val volt = hashes.getValue("${s}_${d.name}_volt_dark")
                assertNotEquals(dark, pure, "${d.name} $s: Pure renders the same as Dark, so the accent was not applied")
                assertNotEquals(dark, volt, "${d.name} $s: Volt renders the same as Dark, so the accent was not applied")
                assertNotEquals(pure, volt, "${d.name} $s: Volt renders the same as Pure")
            }
        }
        // And light is not dark.
        assertNotEquals(hashes.getValue("front_Banner_light"), hashes.getValue("front_Banner_dark"))
        assertEquals(designs.size * (Look.entries.size * 4 + stackLooks.size * 2), hashes.size)
    }
}
