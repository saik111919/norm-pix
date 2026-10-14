package com.example.normpix.finance.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.data.CategorySuggestions
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.domain.emojiIcon
import com.example.normpix.finance.domain.emojiOf
import com.example.normpix.settings.CategoryIconStyle
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a category's icon is drawn as in each icon style - a 3D picture, a brand's logo or mark, an emoji, an outline - and
 * that the bundled pictures cover everything the app offers: every built-in icon, every emoji in the catalog and in the
 * ready-made categories, every brand with a logo.
 */
@RunWith(RobolectricTestRunner::class)
class CategoryArtTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun art(icon: String, style: CategoryIconStyle, name: String? = null) = categoryArt(icon, name, style, context)

    @Test
    fun aPictureIsFiledByItsCodePoints_withoutTheColourSelector() {
        assertEquals("1f355", Fluent3d.fileName("🍕"))
        assertEquals("1f6e1", Fluent3d.fileName("🛡️"))
        assertEquals("1f6e1", Fluent3d.fileName("🛡"))
        assertEquals("2764_200d_1fa79", Fluent3d.fileName("❤️‍🩹"))
    }

    @Test
    fun aBuiltInIcon_isA3DPicture_anEmoji_orAnOutline_byStyle() {
        assertEquals(CategoryArt.Picture("fluent3d/1f6d2.webp"), art("grocery", CategoryIconStyle.ThreeD))
        // The phone's own emoji where its font has it (Robolectric's doesn't), else the outline.
        val glyph = if (deviceCanDraw("🛒")) CategoryArt.Glyph("🛒") else CategoryArt.Outline(categoryIcon("grocery"))
        assertEquals(glyph, art("grocery", CategoryIconStyle.Emoji))
        assertEquals(CategoryArt.Outline(categoryIcon("grocery")), art("grocery", CategoryIconStyle.Outline))
        assertEquals(art("other", CategoryIconStyle.ThreeD), art("no-longer-a-key", CategoryIconStyle.ThreeD), "an unknown key is Other")
    }

    @Test
    fun anEmojiOfItsOwn_isKeptInEveryStyle_in3DAsAPicture() {
        val pizza = emojiIcon("🍕")
        assertEquals(CategoryArt.Picture("fluent3d/1f355.webp"), art(pizza, CategoryIconStyle.ThreeD))
        assertEquals(CategoryArt.Glyph("🍕"), art(pizza, CategoryIconStyle.Emoji))
        assertEquals(CategoryArt.Glyph("🍕"), art(pizza, CategoryIconStyle.Outline))
        // The app has no picture of a unicorn: 3D falls back to the device's emoji.
        assertEquals(CategoryArt.Glyph("🦄"), art(emojiIcon("🦄"), CategoryIconStyle.ThreeD))
        // ... and it wins over a brand the category's name matches.
        assertEquals(CategoryArt.Picture("fluent3d/1f355.webp"), art(pizza, CategoryIconStyle.ThreeD, name = "Swiggy"))
    }

    @Test
    fun aBrand_showsItsLogo_orInOutlineItsMark() {
        assertEquals(CategoryArt.Logo("brandlogos/swiggy.webp"), art("swiggy", CategoryIconStyle.ThreeD))
        assertEquals(CategoryArt.Logo("brandlogos/swiggy.webp"), art("brand:swiggy", CategoryIconStyle.Emoji))
        assertEquals(CategoryArt.Logo("brandlogos/netflix.webp"), art("netflix", CategoryIconStyle.ThreeD))
        assertEquals(CategoryArt.Mark("brands/netflix.svg"), art("netflix", CategoryIconStyle.Outline))
        // Only a logo, and Outline has none: the brand's generic outline.
        assertEquals(CategoryArt.Outline(categoryIcon("swiggy")), art("swiggy", CategoryIconStyle.Outline))
        // A brand with only a mark shows it in every style.
        assertEquals(CategoryArt.Mark("brands/notion.svg"), art("notion", CategoryIconStyle.ThreeD))
    }

    @Test
    fun aCategoryCalledAfterABrand_showsTheBrand_unlessTheNameIsAlsoAWord() {
        assertEquals(CategoryArt.Logo("brandlogos/swiggy.webp"), art("delivery", CategoryIconStyle.ThreeD, name = "Swiggy"))
        assertEquals("phonepe", brandKeyForName("PhonePe"))
        assertEquals("amazonpay", brandKeyForName("Amazon Pay"))
        assertEquals("netflix", brandKeyForName(" netflix "))
        assertNull(brandKeyForName("Visa"), "a visa is a travel cost before it is the card network")
        assertNull(brandKeyForName("Groceries"))
        assertEquals(art("cash", CategoryIconStyle.ThreeD), art("cash", CategoryIconStyle.ThreeD, name = "Coin"))
    }

    @Test
    fun everyBuiltInIconsEmoji_hasA3DPicture() {
        val missing = categoryIcons.filterKeys { brandKey(it) == null }.values.map { it.emoji }.filter { Fluent3d.path(context, it) == null }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun everyEmojiOffered_hasA3DPicture() {
        val catalog = emojiCatalog.flatMap { group -> group.emojis.map { it.glyph } }
        val seeded = (FinanceDatabase.defaultCategories.map { it.icon } + CategorySuggestions.byParent.values.flatten().map { it.icon })
            .mapNotNull(::emojiOf)
        val missing = (catalog + seeded).distinct().filter { Fluent3d.path(context, it) == null }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun everyBrandLogo_isBundled_andEveryBrandIsAnIcon() {
        assertTrue(brandLogoLabels.size > 300)
        for (key in brandLogoLabels.keys) {
            val path = assertNotNull(brandLogo(key))
            context.assets.open(path).use { assertTrue(it.read() >= 0, "$path is empty") }
            assertTrue(key in categoryIcons, "$key is not offered as an icon")
            assertTrue(isBrandIcon(key))
        }
        assertEquals(brandLogoLabels.size, context.assets.list("brandlogos")!!.size, "a logo the app doesn't list")
    }

    @Test
    fun theFluentLicenceShipsWithThePictures() {
        val licence = context.assets.open("licenses/fluentui-emoji.txt").bufferedReader().use { it.readText() }
        assertTrue("MIT" in licence && "Microsoft" in licence)
    }
}
