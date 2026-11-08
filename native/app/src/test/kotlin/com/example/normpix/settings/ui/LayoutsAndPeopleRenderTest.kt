package com.example.normpix.settings.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.ui.PeoplePage
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.settings.CardDesign
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import java.io.File
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Two pages, pictured whole (a tall window, so nothing is scrolled out of the picture) to be looked at in
 * `app/build/qa_card_designs/`:
 *
 *  - Settings > Appearance > Layouts, whose Card design rows each show a made-up saved card and account in that design;
 *  - People, with sample people: the room under the month switcher, and the people's pictures at the size a category's has.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2600dp-xhdpi")
class LayoutsAndPeopleRenderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "layouts_people_render")
    private val outDir = File("build/qa_card_designs").also { it.mkdirs() }

    @AfterTest
    fun tearDown() = env.close()

    private fun dump(name: String) {
        val file = File(outDir, "$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun theCardDesignRowsShowTheirCards() {
        for ((mode, label) in listOf(ThemeMode.Light to "light", ThemeMode.Dark to "dark")) {
            env.preferences.setMode(mode)
            if (label == "light") {
                compose.setContent { env.Content { Surface(color = MaterialTheme.colorScheme.background) { AppearanceSectionPage(AppearanceSection.Layouts) } } }
            }
            compose.waitForIdle()
            compose.onNodeWithText("Card design").assertExists()
            for (design in CardDesign.entries) compose.onNode(hasText(design.description)).assertExists()
            dump("settings_layouts_$label")
        }
        assertEquals(CardDesign.Classic, env.preferences.state.value.cardDesign, "looking at the previews chooses nothing")
    }

    @Test
    fun thePeoplePageHasRoomUnderTheMonthSwitcher_andPicturesAtACategorysSize() {
        val now = LocalDateTime.of(2026, 11, 8, 12, 0)
        runBlocking {
            for ((name, emoji, color) in listOf(
                Triple("Riya", "💖", 3), Triple("Arjun", null, 5), Triple("Dad", "🧓", 1), Triple("Kavya", "✨", 7), Triple("Mom", "👩", 2),
            )) {
                env.container.finance.savePerson(Person(name = name, emoji = emoji, color = color, createdAt = now, updatedAt = now))
            }
        }
        compose.setContent { env.Content { Surface(color = MaterialTheme.colorScheme.background) { PeoplePage() } } }
        compose.waitForIdle()
        dump("people_light")
        // A person's row speaks as one sentence, so the name is only in the unmerged tree.
        compose.onNodeWithText("Riya", useUnmergedTree = true).assertExists()
    }
}
