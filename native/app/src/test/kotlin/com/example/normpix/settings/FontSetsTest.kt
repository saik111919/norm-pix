package com.example.normpix.settings

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.R
import com.example.normpix.ui.theme.AppFont
import com.example.normpix.ui.theme.appTypography
import com.example.normpix.ui.theme.customFontFamily
import com.example.normpix.ui.theme.fontFamilyFor
import com.example.normpix.ui.theme.nunitoFamily
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A font family picked as a download: the zip Google Fonts hands out holds a variable font and a folder of static ones. The
 * interface keeps the variable file (one file, every weight) or else one file for each weight, and draws each at its own weight
 * instead of emboldening one Regular.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FontSetsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clean() = CustomFont.delete(context)

    // What a font file says about itself ---------------------------------------------------------------------------

    @Test
    fun aFontSaysItsWeightSlantAndWidthFromItsOwnTables() {
        val info = assertNotNull(FontFileInfo.read(sfnt(weight = 700, italic = true, widthClass = 3)))
        assertEquals(700, info.weight)
        assertTrue(info.italic)
        assertEquals(3, info.widthClass)
        assertNull(info.weightAxis, "a static font has no weight axis")
        assertFalse(info.isVariable)
    }

    @Test
    fun aVariableFontSaysHowFarItsWeightAxisRuns() {
        val info = assertNotNull(FontFileInfo.read(sfnt(axis = 200..1000)))
        assertEquals(200..1000, info.weightAxis)
        assertTrue(info.isVariable)
    }

    @Test
    fun somethingThatIsNotAFontSaysNothing() {
        assertNull(FontFileInfo.read("hello, not a font".toByteArray()))
        assertNull(FontFileInfo.read(ByteArray(0)))
    }

    @Test
    fun theBundledNunitoIsReadAsAVariableFont() {
        val bytes = context.resources.openRawResource(R.font.nunito).use { it.readBytes() }
        val info = assertNotNull(FontFileInfo.read(bytes), "Nunito's tables are read")
        assertEquals(200..1000, info.weightAxis, "Google Fonts' Nunito runs from ExtraLight to Black")
    }

    // Choosing from a download -------------------------------------------------------------------------------------

    private class Candidate(val name: String, val info: FontFileInfo)

    private fun candidate(name: String, weight: Int = 400, italic: Boolean = false, width: Int = 5, axis: IntRange? = null) =
        Candidate(name, FontFileInfo(weight, italic, width, axis))

    private fun choose(vararg candidates: Candidate) = FontSets.choose(candidates.toList()) { it.info }.map { it.name }

    @Test
    fun aVariableFontIsKeptAloneBecauseItHoldsEveryWeight() {
        val kept = choose(
            candidate("Nunito-Regular", 400), candidate("Nunito-Bold", 700),
            candidate("Nunito[wght]", axis = 200..1000), candidate("Nunito-Italic[wght]", italic = true, axis = 200..1000),
        )
        assertEquals(listOf("Nunito[wght]"), kept)
    }

    @Test
    fun staticFilesAreKeptOnePerWeightWithTheRegularFirstAndNoItalics() {
        val kept = choose(
            candidate("Bold", 700), candidate("Light", 300), candidate("BoldItalic", 700, italic = true),
            candidate("Regular", 400), candidate("Medium", 500), candidate("Italic", 400, italic = true),
        )
        assertEquals(listOf("Regular", "Medium", "Bold", "Light"), kept)
    }

    @Test
    fun aCondensedFileIsLeftOutWhereNormalWidthOnesAreThere() {
        val kept = choose(candidate("Regular", 400), candidate("Condensed-Regular", 400, width = 3), candidate("Condensed-Bold", 700, width = 3))
        assertEquals(listOf("Regular"), kept)
        assertEquals(listOf("Condensed-Regular"), choose(candidate("Condensed-Regular", 400, width = 3)), "but a family that is all condensed is kept")
    }

    @Test
    fun anItalicOnlyFamilyIsKeptRatherThanNothing() {
        assertEquals(listOf("Slant"), choose(candidate("Slant", 400, italic = true)))
    }

    @Test
    fun noMoreThanTheMostAFamilyKeepsAreKept() {
        val all = (100..900 step 100).map { candidate("W$it", it) } + candidate("Odd", 450)
        assertEquals(FontSets.MAX_FACES, choose(*all.toTypedArray()).size)
        assertEquals("W400", choose(*all.toTypedArray()).first())
    }

    @Test
    fun aDeclaredWeightIsRoundedToTheNearestHundred() {
        assertEquals(300, FontSets.nearestWeight(350 - 1))
        assertEquals(400, FontSets.nearestWeight(400))
        assertEquals(700, FontSets.nearestWeight(650))
        assertEquals(900, FontSets.nearestWeight(1000))
        assertEquals(100, FontSets.nearestWeight(1))
    }

    // The zip --------------------------------------------------------------------------------------------------------

    private fun install(zip: ByteArray): FontInstall = CustomFont.installZip(ByteArrayInputStream(zip), CustomFont.file(context).parentFile!!) { true }

    private fun saved(): List<String> = CustomFont.file(context).parentFile!!.list()!!.toList().sorted()

    @Test
    fun aGoogleFontsDownloadKeepsItsVariableFontAndNothingElse() {
        val result = install(
            zipOf(
                "OFL.txt" to "licence".toByteArray(),
                "Nunito[wght].ttf" to sfnt(weight = 200, axis = 200..1000),
                "Nunito-Italic[wght].ttf" to sfnt(italic = true, axis = 200..1000),
                "static/Nunito-Regular.ttf" to sfnt(weight = 400),
                "static/Nunito-Bold.ttf" to sfnt(weight = 700),
            ),
        )
        assertEquals(FontInstall.Saved, result)
        assertEquals(listOf(CustomFont.FILE_NAME), saved(), "one file, no staging left behind")
        assertEquals("All weights", CustomFont.describe(context))
        assertTrue(CustomFont.extras(context).isEmpty())
    }

    @Test
    fun aDownloadOfStaticFilesKeepsEachWeightBesideTheRegular() {
        val result = install(
            zipOf(
                "__MACOSX/._Regular.ttf" to sfnt(),
                "Family/Family-Bold.ttf" to sfnt(weight = 700),
                "Family/Family-Regular.otf" to sfnt(weight = 400),
                "Family/Family-Italic.ttf" to sfnt(italic = true),
                "Family/Family-Light.ttf" to sfnt(weight = 300),
                "Family/readme.txt" to "read me".toByteArray(),
            ),
        )
        assertEquals(FontInstall.Saved, result)
        assertEquals(
            listOf(CustomFont.FILE_NAME, CustomFont.extraName(300), CustomFont.extraName(700)).sorted(),
            saved(),
        )
        assertEquals(FontFileInfo.read(CustomFont.file(context))?.weight, 400, "the regular is the main file")
        assertEquals(listOf(300, 700), CustomFont.extras(context).mapNotNull { FontFileInfo.read(it)?.weight })
        assertEquals("3 weights", CustomFont.describe(context))
    }

    @Test
    fun aSingleFontPickedLaterReplacesTheFamilyBeforeIt() {
        install(zipOf("A-Regular.ttf" to sfnt(weight = 400), "A-Bold.ttf" to sfnt(weight = 700)))
        assertEquals(1, CustomFont.extras(context).size)

        CustomFont.install(ByteArrayInputStream(sfnt(weight = 400)), CustomFont.file(context)) { true }
        assertTrue(CustomFont.extras(context).isEmpty(), "the old family's weights are not the new font's")
        assertEquals(listOf(CustomFont.FILE_NAME), saved())
    }

    @Test
    fun aZipWithNoFontIsRefusedAndTheFontInUseIsUntouched() {
        CustomFont.install(ByteArrayInputStream(sfnt(weight = 400)), CustomFont.file(context)) { true }
        val before = CustomFont.file(context).readBytes()

        assertEquals(FontInstall.NoFonts, install(zipOf("readme.txt" to "hi".toByteArray(), "logo.png" to ByteArray(10))))
        assertEquals(FontInstall.NoFonts, install(zipOf("Not a font.ttf" to "hello, not a font".toByteArray())), "named like one, but not one")
        assertTrue(CustomFont.file(context).readBytes().contentEquals(before))
        assertEquals(listOf(CustomFont.FILE_NAME), saved())
    }

    @Test
    fun aZipWhoseFontsAndroidCannotLoadIsRefusedAndTheFontInUseIsUntouched() {
        CustomFont.install(ByteArrayInputStream(sfnt(weight = 400)), CustomFont.file(context)) { true }
        val before = CustomFont.file(context).readBytes()

        val zip = zipOf("Broken.ttf" to sfnt(weight = 700))
        assertEquals(FontInstall.NotAFont, CustomFont.installZip(ByteArrayInputStream(zip), CustomFont.file(context).parentFile!!) { false })
        assertTrue(CustomFont.file(context).readBytes().contentEquals(before))
        assertEquals(listOf(CustomFont.FILE_NAME), saved())
    }

    @Test
    fun somethingThatLooksLikeAZipButIsNotOneIsRefused() {
        val notAZip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4, 5)
        assertTrue(install(notAZip) != FontInstall.Saved, "a damaged zip saves nothing")
        assertEquals(emptyList(), saved(), "nothing is left in the fonts folder")
    }

    @Test
    fun aFileIsAZipByItsFirstBytes() {
        assertTrue(CustomFont.hasZipHeader(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0)))
        assertFalse(CustomFont.hasZipHeader(byteArrayOf(0x00, 0x01, 0x00, 0x00)))
        assertFalse(CustomFont.hasZipHeader(byteArrayOf(0x50, 0x4B)))
    }

    // Drawing with it ------------------------------------------------------------------------------------------------

    @Test
    fun nunitoSetsEveryStyleOfTheScaleAndIsKeptUnderItsName() {
        val styles = appTypography(AppFont(InterfaceFont.Nunito))
        val described = nunitoFamily().toString()
        assertEquals(described, fontFamilyFor(AppFont(InterfaceFont.Nunito)).toString())
        assertEquals(described, styles.bodyMedium.fontFamily.toString())
        assertEquals(described, styles.headlineLarge.fontFamily.toString())
        assertEquals("Nunito", InterfaceFont.Nunito.name, "the name is what is kept in the preferences")
    }

    @Test
    fun aVariableFileIsDrawnAtEveryWeightAndAFamilyAtEachFilesOwn() {
        val variable = File(context.cacheDir, "variable.ttf").apply { writeBytes(sfnt(weight = 200, axis = 200..1000)) }
        val bold = File(context.cacheDir, "bold.ttf").apply { writeBytes(sfnt(weight = 700)) }
        val regular = File(context.cacheDir, "regular.ttf").apply { writeBytes(sfnt(weight = 400)) }

        val variableFamily = customFontFamily(variable).toString()
        assertTrue(variableFamily.split("weight").size - 1 >= 9, "one entry for each weight 100..900: $variableFamily")

        val family = customFontFamily(regular, listOf(CustomFontFace(bold, 700))).toString()
        assertTrue(regular.path in family && bold.path in family, "both files are in the family: $family")
        assertTrue("700" in family, "the bold file is drawn as the bold: $family")
    }

    // Keeping it ---------------------------------------------------------------------------------------------------

    @Test
    fun theExtraWeightsAreNamedLikeTheBackupExpectsAndNothingElseIs() {
        assertTrue(CustomFont.isSavedName(CustomFont.FILE_NAME))
        assertTrue(CustomFont.isSavedName(CustomFont.extraName(700)))
        assertFalse(CustomFont.isSavedName("custom-interface-font-w750.ttf"))
        assertFalse(CustomFont.isSavedName("../custom-interface-font.ttf"))
        assertFalse(CustomFont.isSavedName("other.ttf"))
    }

    @Test
    fun theWeightsAreDrawnFromCopiesThatChangeWhenTheFamilyDoes() {
        install(zipOf("A-Regular.ttf" to sfnt(weight = 400), "A-Bold.ttf" to sfnt(weight = 700)))
        val main = assertNotNull(CustomFont.loadable(context) { true })
        assertNull(CustomFont.readyFaces(context), "not made yet")
        val faces = CustomFont.faces(context)
        assertEquals(listOf(700), faces.map { it.weight })
        assertTrue(faces.single().file.isFile && faces.single().file != CustomFont.extras(context).single(), "a copy in the cache")
        assertEquals(faces, CustomFont.readyFaces(context), "made, so the first frame can have them")
        assertTrue(main.isFile, "the main copy stays when the weights are made")

        // A new family is new paths: the text system keeps a font it loaded by its path.
        val file = CustomFont.file(context)
        install(zipOf("B-Regular.ttf" to sfnt(weight = 400), "B-Bold.ttf" to sfnt(weight = 700), "B-Black.ttf" to sfnt(weight = 900)))
        file.setLastModified(file.lastModified() + 5_000)
        val replaced = assertNotNull(CustomFont.loadable(context) { true })
        assertFalse(main.exists(), "the older copy of the main file is cleared away")
        assertFalse(faces.single().file.exists(), "and the older weights with it")
        assertEquals(listOf(700, 900), CustomFont.faces(context).map { it.weight })
        assertTrue(File(replaced.path).isFile)
    }
}
