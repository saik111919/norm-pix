package com.example.normpix.finance.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.settings.CardDesign
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import java.io.File
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The category card in each Card design, both prints (the stack's and the carousel's), in Light and Dark, drawn the size Home draws
 * them (a phone's width less the page gutter): pictures in `app/build/qa_category_cards/` to be looked at, since a corner or a
 * cramped panel is what the tests cannot see.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xhdpi")
class CategoryCardDesignRenderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "category_card_designs")
    private val outDir = File("build/qa_category_cards").also { it.mkdirs() }

    @AfterTest
    fun tearDown() = env.close()

    private val now = LocalDateTime.of(2026, 11, 13, 9, 0)

    private fun spend(name: String, icon: String, color: Int, amount: Long, count: Int, share: Double, previous: Long) = CategorySpend(
        categoryId = color.toLong(),
        category = Category(id = color.toLong(), name = name, type = CategoryType.Expense, icon = icon, color = color, createdAt = now, updatedAt = now),
        month = MonthKey.of(now),
        amount = amount,
        count = count,
        share = share,
        previous = previous,
        budget = null,
    )

    private val rent = spend("Rent & housing", "home", 5, 2_850_000, 2, 0.49, 2_850_000)
    private val food = spend("Food & dining", "restaurant", 0, 1_245_000, 14, 0.21, 1_500_000)

    private fun dump(name: String) {
        File(outDir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun everyDesign_inLightAndDark() {
        env.preferences.setMode(ThemeMode.Light)
        compose.setContent {
            env.Content {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (design in CardDesign.entries) SpendCardFace(design, rent, compact = false)
                        for (design in CardDesign.entries) SpendCardFace(design, food, compact = true)
                    }
                }
            }
        }
        compose.waitForIdle()
        dump("category_cards_light")
        // The same content, the theme changed under it.
        env.preferences.setMode(ThemeMode.Dark)
        compose.waitForIdle()
        dump("category_cards_dark")
    }

    @Test
    fun plates_behindTheFront() {
        env.preferences.setMode(ThemeMode.Light)
        compose.setContent {
            env.Content {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (design in CardDesign.entries) SpendCardPlate(design, rent.category)
                    }
                }
            }
        }
        compose.waitForIdle()
        dump("category_plates_light")
    }
}
