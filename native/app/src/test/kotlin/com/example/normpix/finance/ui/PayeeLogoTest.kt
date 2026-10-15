package com.example.normpix.finance.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.settings.CategoryIconStyle
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A payee the app knows by its logo ("Ola", "Swiggy Instamart") is drawn as that logo on its transaction instead of the
 * category's icon - and a payee that only looks like a brand ("Max Healthcare") is not.
 */
@RunWith(RobolectricTestRunner::class)
class PayeeLogoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aPayeeThatIsABrand_isThatBrand() {
        assertEquals("ola", brandKeyForPayee("Ola"))
        assertEquals("ola", brandKeyForPayee("  OLA  "), "whatever the case and spacing")
        assertEquals("amazonpay", brandKeyForPayee("Amazon Pay"))
        assertEquals("max", brandKeyForPayee("HBO Max"), "a name the brand goes by")
        assertEquals("cred", brandKeyForPayee("CRED"), "a brand that is also a word is itself when it is the whole payee")
    }

    @Test
    fun aPayeeThatStartsWithABrand_isThatBrand() {
        assertEquals("swiggy", brandKeyForPayee("Swiggy Instamart"))
        assertEquals("uber", brandKeyForPayee("Uber India Systems Pvt Ltd"))
        assertEquals("googlepay", brandKeyForPayee("Google Pay UPI"), "the longest start that is a brand wins over a shorter one")
        assertEquals("google", brandKeyForPayee("Google Cloud"))
        assertEquals("cultfit", brandKeyForPayee("Cult.fit membership"), "punctuation between the words does not matter")
        assertEquals("maxlife", brandKeyForPayee("Max Life Insurance"))
    }

    @Test
    fun aBrandThatIsAlsoAnEverydayName_isNotTakenForTheBrand() {
        assertNull(brandKeyForPayee("Max Healthcare"))
        assertNull(brandKeyForPayee("Max"), "on its own it is as likely a shop or a person")
        assertNull(brandKeyForPayee("Medium Rare Cafe"))
        assertNull(brandKeyForPayee("Visa Card"))
        assertNull(brandKeyForPayee("Coin Base"))
        assertNull(brandKeyForPayee("Slice of Pizza"))
    }

    @Test
    fun aPayeeThatIsNoBrand_hasNoLogo() {
        assertNull(brandKeyForPayee(""))
        assertNull(brandKeyForPayee("   "))
        assertNull(brandKeyForPayee("Local chai stall"))
        assertNull(brandKeyForPayee("Ravi's Kirana"))
        assertNull(brandKeyForPayee("Olaf"), "a word that only begins with a brand's name")
    }

    @Test
    fun theLogoIsShownInThe3DAndEmojiStyles_notInOutline() {
        val logo = CategoryArt.Logo("brandlogos/ola.webp")
        assertEquals(logo, payeeArt("Ola", CategoryIconStyle.ThreeD))
        assertEquals(logo, payeeArt("Ola Cabs", CategoryIconStyle.Emoji))
        assertNull(payeeArt("Ola", CategoryIconStyle.Outline), "Outline has no logos: the category's own icon stays")
        assertNull(payeeArt(null, CategoryIconStyle.ThreeD))
        assertNull(payeeArt("Local chai stall", CategoryIconStyle.ThreeD))
    }

    @Test
    fun everyLogoAPayeeCanGet_isBundled() {
        for (payee in listOf("Ola", "Swiggy", "Zomato", "Uber", "Amazon Pay", "PhonePe", "Google Pay", "Netflix", "Spotify")) {
            val path = (payeeArt(payee, CategoryIconStyle.ThreeD) ?: error("$payee has no logo")).path
            assertNotNull(runCatching { context.assets.open(path).use { it.read() } }.getOrNull(), "$payee: $path is in the assets")
        }
        assertTrue(brandLogoLabels.isNotEmpty())
    }
}
