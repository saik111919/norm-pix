package com.example.normpix.finance.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.materialkolor.ktx.toHct
import java.io.File
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A picture's colour, read from the file the way the form reads it: small, once, and read again when the file is replaced. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ArtworkSeedsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun picture(name: String, argb: Int, side: Int = 200): File {
        val file = File(context.cacheDir, "$name.png")
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply { eraseColor(argb) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    @Test
    fun aRedFileSaysRed_andAGreyOneNothing() {
        val red = assertNotNull(PictureSeeds.read(context.contentResolver, picture("seed_red", 0xFFD32F2F.toInt())))
        val hue = red.toHct().hue
        assertTrue(hue < 30.0 || hue > 340.0, "hue $hue")
        assertNull(PictureSeeds.read(context.contentResolver, picture("seed_grey", 0xFF808080.toInt())))
    }

    private fun category(color: Int) = Category(
        id = 1, name = "Food", type = CategoryType.Expense, icon = "food", color = color, archived = false, sortOrder = 0,
        createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now(), parentId = null,
    )

    @Test
    fun aPaymentFormWearsItsCategorysColour_elseThePictures_elseTheAccountsCard_elseNothing() {
        val picture = Color(0xFF1565C0)
        val card = Color(0xFF2E7D32)
        assertEquals(paletteSeed(3), artworkSeedOf(category(3), picture, card), "the category first")
        assertEquals(picture, artworkSeedOf(null, picture, card), "no category: the picture")
        assertEquals(card, artworkSeedOf(null, null, card), "no picture either: the account's card")
        assertNull(artworkSeedOf(null, null, null), "nothing: the app's own colours")
        // Another category is another colour, and every one of them is a colour a scheme can be made from.
        assertNotEquals(artworkSeedOf(category(0), null, null), artworkSeedOf(category(1), null, null))
        for (index in 0 until paletteSize) assertTrue(com.example.normpix.ui.theme.ArtworkColors.usable(paletteSeed(index)), "palette $index")
    }

    @Test
    fun aFileThatIsNotAPictureSaysNothing_andIsNotAnError() {
        val file = File(context.cacheDir, "seed_not_a_picture.png").apply { writeText("not a picture") }
        assertNull(PictureSeeds.read(context.contentResolver, file))
        assertNull(PictureSeeds.read(context.contentResolver, File(context.cacheDir, "seed_missing.png")))
    }

    @Test
    fun whatAPictureSaidIsKeptUntilTheFileIsReplaced() {
        val file = picture("seed_cached", 0xFF1565C0.toInt())
        val key = PictureSeeds.keyOf(file)
        assertNull(PictureSeeds.cached(key))
        PictureSeeds.remember(key, null)
        assertNull(PictureSeeds.cached(key)?.seed, "a grey picture is remembered as none")
        assertNotNull(PictureSeeds.cached(key), "...and is known to be read")

        // The same path with other contents is another key: the new picture is read.
        file.delete()
        val replaced = picture("seed_cached", 0xFF2E7D32.toInt(), side = 300)
        assertNotEquals(key, PictureSeeds.keyOf(replaced))
        assertEquals(null, PictureSeeds.cached(PictureSeeds.keyOf(replaced)))
    }
}
