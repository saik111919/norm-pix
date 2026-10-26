package com.example.normpix.settings

import android.content.Context
import androidx.compose.material3.Typography
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.ui.SettingsCatalog
import com.example.normpix.ui.theme.AppFont
import com.example.normpix.ui.theme.AppIcons
import com.example.normpix.ui.theme.appTypography
import com.example.normpix.ui.theme.customFontFamily
import com.example.normpix.ui.theme.fontFamilyFor
import com.example.normpix.ui.theme.gsFlexFamily
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Your font": a .ttf or .otf the user picks becomes the interface font. It is checked on the way in, kept in the app's own
 * folder, remembered by name, and quietly replaced by GS Flex whenever it cannot be used.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomFontTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun store() = context.getSharedPreferences("custom_font_test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    private val truetype = byteArrayOf(0x00, 0x01, 0x00, 0x00, 1, 2, 3, 4)
    private val opentype = "OTTO....".toByteArray(Charsets.US_ASCII)
    private val appleTruetype = "true....".toByteArray(Charsets.US_ASCII)

    @Before
    fun clean() = CustomFont.delete(context)

    // The file ----------------------------------------------------------------------------------------------------

    @Test
    fun aFontIsKeptAtTheAgreedPlaceInTheAppsOwnFolder() {
        assertEquals(File(context.filesDir, "fonts/custom-interface-font.ttf"), CustomFont.file(context))
        assertFalse(CustomFont.exists(context))
    }

    @Test
    fun aFileIsAFontByItsFirstBytes() {
        assertTrue(CustomFont.hasFontHeader(truetype), "TrueType")
        assertTrue(CustomFont.hasFontHeader(opentype), "OpenType with CFF outlines")
        assertTrue(CustomFont.hasFontHeader(appleTruetype), "Apple's TrueType")
        assertFalse(CustomFont.hasFontHeader("%PDF-1.7".toByteArray()), "a PDF")
        assertFalse(CustomFont.hasFontHeader(byteArrayOf(0x00, 0x01)), "too short to say")
        assertFalse(CustomFont.hasFontHeader(ByteArray(0)), "empty")
    }

    @Test
    fun aGoodFileIsSavedAndReplacesTheOneBefore() {
        val target = CustomFont.file(context)
        assertEquals(FontInstall.Saved, CustomFont.install(ByteArrayInputStream(truetype), target) { true })
        assertTrue(target.readBytes().contentEquals(truetype))

        assertEquals(FontInstall.Saved, CustomFont.install(ByteArrayInputStream(opentype), target) { true })
        assertTrue(target.readBytes().contentEquals(opentype), "the new font is the saved one")
        assertEquals(listOf(target.name), target.parentFile!!.list()!!.toList(), "no half-copied file is left behind")
    }

    @Test
    fun aFileThatIsNotAFontIsRefusedAndTheFontInUseIsUntouched() {
        val target = CustomFont.file(context)
        CustomFont.install(ByteArrayInputStream(truetype), target) { true }

        assertEquals(FontInstall.NotAFont, CustomFont.install(ByteArrayInputStream("hello, not a font".toByteArray()), target) { true })
        assertTrue(target.readBytes().contentEquals(truetype), "the header check refused it")

        assertEquals(FontInstall.NotAFont, CustomFont.install(ByteArrayInputStream(opentype), target) { false })
        assertTrue(target.readBytes().contentEquals(truetype), "Android could not load it")
        assertEquals(listOf(target.name), target.parentFile!!.list()!!.toList())
    }

    @Test
    fun aFileOverTheLimitIsRefused() {
        val target = CustomFont.file(context)
        val big = ByteArray((CustomFont.MAX_BYTES + 1).toInt()).also { truetype.copyInto(it) }
        assertEquals(FontInstall.TooLarge, CustomFont.install(ByteArrayInputStream(big), target) { true })
        assertFalse(target.exists())
        assertEquals(10L * 1024 * 1024, CustomFont.MAX_BYTES)
    }

    @Test
    fun theNameShownIsTheLastPartOfTheFileName() {
        assertEquals("Fira Sans.ttf", CustomFont.cleanName("primary:Fonts/Fira Sans.ttf"))
        assertEquals("Your font", CustomFont.cleanName(null))
        assertEquals("Your font", CustomFont.cleanName("   "))
        assertEquals(MAX_CUSTOM_FONT_NAME, CustomFont.cleanName("x".repeat(500)).length)
    }

    @Test
    fun theFontDrawnWithIsAFreshCopyPerVersionAndNothingWithoutAFile() {
        assertNull(CustomFont.loadable(context) { true }, "no font saved")
        assertNull(CustomFont.ready(context))

        val target = CustomFont.file(context)
        CustomFont.install(ByteArrayInputStream(truetype), target) { true }
        assertNull(CustomFont.ready(context), "not made yet")
        val copy = assertNotNull(CustomFont.loadable(context) { true })
        assertNotEquals(target, copy, "the copy in the cache, not the saved file")
        assertEquals(copy, CustomFont.ready(context), "made, so the first frame can have it")

        // A replaced font is a different path, so the text system does not hand back the one it loaded before.
        target.writeBytes(opentype + ByteArray(40))
        target.setLastModified(target.lastModified() + 5_000)
        val replaced = assertNotNull(CustomFont.loadable(context) { true })
        assertNotEquals(copy, replaced)
        assertFalse(copy.exists(), "the older copy is cleared away")

        // Unloadable now: nothing to draw with, so the caller falls back to GS Flex.
        target.writeBytes("not a font".toByteArray())
        assertNull(CustomFont.loadable(context) { true })
        CustomFont.delete(context)
        assertFalse(CustomFont.exists(context))
    }

    // The choice -------------------------------------------------------------------------------------------------

    @Test
    fun theChoiceAndTheNameAreRememberedUnderTheirKeys() {
        val store = store()
        val preferences = AppPreferences(store)
        val before = preferences.state.value.customFontRevision

        preferences.useCustomFont("  Fira Sans.ttf ")
        assertEquals(InterfaceFont.Custom, preferences.state.value.interfaceFont)
        assertEquals("Fira Sans.ttf", preferences.state.value.customFontName)
        assertEquals(before + 1, preferences.state.value.customFontRevision, "what draws with it looks at the file again")
        assertEquals("Custom", store.getString("interface_font", null))
        assertEquals("Fira Sans.ttf", store.getString("custom_font_name", null))

        val next = AppPreferences(store).state.value
        assertEquals(InterfaceFont.Custom, next.interfaceFont, "read back by the next launch")
        assertEquals("Fira Sans.ttf", next.customFontName)
    }

    @Test
    fun removingTheFontBringsGsFlexBackAndForgetsTheName() {
        val store = store()
        val preferences = AppPreferences(store)
        preferences.useCustomFont("Fira Sans.ttf")
        val revision = preferences.state.value.customFontRevision

        preferences.clearCustomFont()
        assertEquals(InterfaceFont.GsFlex, preferences.state.value.interfaceFont)
        assertEquals("", preferences.state.value.customFontName)
        assertEquals(revision + 1, preferences.state.value.customFontRevision)
        assertFalse(store.contains("custom_font_name"))
        assertEquals("GsFlex", store.getString("interface_font", null))
    }

    @Test
    fun removingAFontThatIsNotInUseLeavesTheChoiceAlone() {
        val preferences = AppPreferences(store())
        preferences.useCustomFont("Fira Sans.ttf")
        preferences.setInterfaceFont(InterfaceFont.System)

        preferences.clearCustomFont()
        assertEquals(InterfaceFont.System, preferences.state.value.interfaceFont)
        assertEquals("", preferences.state.value.customFontName)
    }

    @Test
    fun anOverlongNameIsCut() {
        val preferences = AppPreferences(store())
        preferences.useCustomFont("n".repeat(500))
        assertEquals(MAX_CUSTOM_FONT_NAME, preferences.state.value.customFontName.length)
    }

    @Test
    fun resettingSettingsForgetsTheFont() {
        val store = store()
        val preferences = AppPreferences(store)
        preferences.useCustomFont("Fira Sans.ttf")
        preferences.resetToDefaults()
        assertNotEquals(InterfaceFont.Custom, preferences.state.value.interfaceFont)
        assertEquals("", preferences.state.value.customFontName)
        assertFalse(store.contains("custom_font_name"))
    }

    // The type scale ----------------------------------------------------------------------------------------------

    private fun Typography.allStyles() = listOf(
        displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
        titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall,
    )

    @Test
    fun thePhonesOwnFontLeavesMaterialsTypeScaleAsItIs() {
        assertNull(fontFamilyFor(AppFont(InterfaceFont.System)))
        assertEquals(Typography(), appTypography(AppFont(InterfaceFont.System)))
    }

    @Test
    fun gsFlexSetsEveryStyleOfTheScale() {
        val font = AppFont(InterfaceFont.GsFlex)
        val styles = appTypography(font).allStyles()
        assertEquals(15, styles.size)
        assertTrue(styles.all { it.fontFamily == gsFlexFamily(font.shape) }, "every style is in the app font")
    }

    @Test
    fun yourFontIsUsedForEveryStyleOnceItsFileIsThere() {
        CustomFont.install(ByteArrayInputStream(truetype), CustomFont.file(context)) { true }
        val file = assertNotNull(CustomFont.loadable(context) { true })
        val font = AppFont(InterfaceFont.Custom, customFile = file)

        // A font read from a file has no value equality (two families over the same file are never ==), so a family is compared by
        // what it describes: the file, its weight and its style.
        val described = customFontFamily(file).toString()
        assertTrue(file.path in described, "the family is made from the installed file: $described")
        assertEquals(described, fontFamilyFor(font).toString())
        val typography = appTypography(font)
        val styles = typography.allStyles()
        assertTrue(styles.all { it.fontFamily.toString() == described }, "every style is in the file's family")
        // The sizes and weights of the scale are Material's own, in whatever family.
        assertEquals(Typography().bodyLarge.fontSize, typography.bodyLarge.fontSize)
        assertEquals(Typography().titleLarge.fontWeight, typography.titleLarge.fontWeight)
    }

    @Test
    fun yourFontWithNoUsableFileIsGsFlexNotABrokenScreen() {
        val font = AppFont(InterfaceFont.Custom, customFile = null)
        assertEquals(gsFlexFamily(font.shape), fontFamilyFor(font))
        assertEquals(gsFlexFamily(font.shape), appTypography(font).bodyMedium.fontFamily)
    }

    // Finding it --------------------------------------------------------------------------------------------------

    @Test
    fun settingsSearchFindsYourFontInInterfaceFont() {
        for (query in listOf("custom font", "upload font", "ttf", "otf", "font file")) {
            val hit = SettingsCatalog.search(query).single { it.title == "Your font" }
            assertEquals(SettingsPage.Appearance, hit.group, query)
            assertEquals(AppearanceSection.InterfaceFont, hit.appearance, query)
            assertEquals("Appearance > Interface font", hit.location, query)
        }
    }

    @Test
    fun theWalletSettingsIconIsTheWalletOfTheDock() {
        assertEquals(AppIcons.Wallet, SettingsCatalog.iconFor(SettingsPage.Wallet))
        assertEquals(AppIcons.Wallet, SettingsCatalog.entries.first { it.page == SettingsPage.Wallet }.icon)
    }

    @Test
    fun anAppearanceSectionIsFoundByItsRouteName() {
        for (section in AppearanceSection.entries) assertEquals(section, AppearanceSection.named(section.name))
        // A page folded into another keeps its name, and a link to it opens the page it is part of now.
        assertEquals(AppearanceSection.Controls, AppearanceSection.named("BottomControls"))
        assertEquals(AppearanceSection.Controls, AppearanceSection.named("BackButton"))
        assertEquals(AppearanceSection.Controls, AppearanceSection.named("MonthControl"))
        assertEquals(AppearanceSection.Layouts, AppearanceSection.named("TopSpending"))
        assertEquals(AppearanceSection.Transitions, AppearanceSection.named("PageTransitions"))
        assertEquals(AppearanceSection.Transitions, AppearanceSection.named("SheetTransitions"))
        assertNull(AppearanceSection.named("NoSuchPage"))
        assertNull(AppearanceSection.named(null))
    }
}
