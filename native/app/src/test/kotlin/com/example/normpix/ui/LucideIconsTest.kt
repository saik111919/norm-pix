package com.example.normpix.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.ui.components.LucideIcons
import com.example.normpix.ui.theme.AppIcons
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Lucide set behind Settings > Appearance > Icons > Interface icons: the table that says which Lucide icon stands for which
 * Material one, the drawings it names, and that no Material icon the app uses is left out (a hole would show as a Material icon
 * among Lucide ones).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LucideIconsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val table get() = LucideIcons.table(context)

    @Test
    fun everyIconTheTableNamesHasADrawing_andEveryDrawingIsUsed() {
        val named = table.mapping.values.toSet()
        assertEquals(emptySet(), named - table.drawings.keys, "mapped to a Lucide icon that is not in lucide_icons.txt")
        assertEquals(emptySet(), table.drawings.keys - named, "drawn in lucide_icons.txt but no Material icon is mapped to it")
    }

    @Test
    fun everyDrawingBecomesAVectorOfStrokesOnLucidesGrid() {
        for ((name, paths) in table.drawings) {
            val vector = assertNotNull(LucideIcons.vector(context, name), name)
            assertEquals(24f, vector.viewportWidth, name)
            assertEquals(24f, vector.viewportHeight, name)
            assertEquals(paths.size, vector.root.size, "$name: one path for each drawing")
            for (node in vector.root) {
                val path = node as VectorPath
                assertNotNull(path.stroke, "$name is stroked")
                assertNull(path.fill, "$name is not filled")
                assertEquals(2f, path.strokeLineWidth, name)
                assertTrue(path.pathData.isNotEmpty(), "$name has a drawing")
            }
        }
    }

    @Test
    fun aMaterialNameIsReadFromItsVector_andOnlyFromAMaterialOne() {
        assertEquals("Lock", LucideIcons.materialName(Icons.Outlined.Lock.name))
        assertEquals("Lock", LucideIcons.materialName(Icons.Rounded.Lock.name))
        assertEquals("ArrowBack", LucideIcons.materialName(Icons.AutoMirrored.Rounded.ArrowBack.name))
        // The app's own artwork and Lucide's own vectors are nobody's Material icons.
        assertNull(LucideIcons.materialName(AppIcons.Wallet.name))
        assertNull(LucideIcons.materialName("Lucide.lock"))
    }

    @Test
    fun aMaterialIconFindsItsLucideTwin_andMirrorsOnlyAsMaterialDid() {
        assertEquals("Lucide.lock", LucideIcons.forMaterial(context, Icons.Outlined.Lock)?.name)
        assertEquals("Lucide.lock", LucideIcons.forMaterial(context, Icons.Rounded.Lock)?.name, "every family of an icon gets the same drawing")
        assertEquals(false, LucideIcons.forMaterial(context, Icons.Outlined.Lock)?.autoMirror)
        val back = LucideIcons.forMaterial(context, Icons.AutoMirrored.Rounded.ArrowBack)
        assertEquals("Lucide.arrow-left", back?.name)
        assertEquals(true, back?.autoMirror, "a back arrow still points the other way in a right-to-left layout")
    }

    @Test
    fun theIncomeArrowIsAnArrow_notAPhone() {
        // Material's "CallReceived" is a plain down-left arrow, which is why Add income uses it; mapped by its name to Lucide's
        // "phone-incoming" the Add income button showed a phone taking a call (2026-11-07).
        assertEquals("Lucide.arrow-down-left", LucideIcons.forMaterial(context, Icons.AutoMirrored.Rounded.CallReceived)?.name)
        assertTrue(table.mapping.values.none { it.startsWith("phone") }, "no phone drawing stands in for a money icon: ${table.mapping.filterValues { it.startsWith("phone") }}")
    }

    @Test
    fun theAppsOwnArtworkIsLeftAlone() {
        assertNull(LucideIcons.forMaterial(context, AppIcons.Wallet))
        assertNull(LucideIcons.forMaterial(context, AppIcons.WalletFilled))
    }

    @Test
    fun everyMaterialIconTheAppUsesHasALucideTwin() {
        val used = Regex("""Icons\.(?:AutoMirrored\.)?(?:Outlined|Rounded|Filled|Sharp|TwoTone)\.([A-Za-z0-9]+)""")
        val names = sortedSetOf<String>()
        File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            used.findAll(file.readText()).forEach { names += it.groupValues[1] }
        }
        assertTrue(names.size > 200, "the scan found the app's icons (${names.size})")
        val missing = names.filter { it !in table.mapping }
        assertEquals(
            emptyList(),
            missing,
            "Material icons with no Lucide twin: add \"Name=lucide-name\" to res/raw/lucide_map.txt (a name from the release the file's " +
                "header gives) and run native/design/lucide/LucideBuild.java, so the Lucide choice has no holes",
        )
    }
}
