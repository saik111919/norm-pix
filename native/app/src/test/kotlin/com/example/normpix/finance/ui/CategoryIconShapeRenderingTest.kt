package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.settings.CategoryIconShape
import com.example.normpix.settings.CategoryIconStyle
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.AssetBitmaps
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shape behind category icons and people ("Icon background" in Appearance): each shape cuts the avatar's corners its
 * own way - told apart by what is drawn in a corner - and with no background there is only the icon. And a payee the app
 * knows by its logo is drawn as that logo on its transaction.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h400dp-xxhdpi")
class CategoryIconShapeRenderingTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val page = Color.White

    @Before
    fun setUp() {
        env = TestEnvironment("category_icon_shape_rendering_test")
    }

    @After
    fun tearDown() = env.close()

    /** The avatar is 100dp wide, 20dp in from the edge of its 140dp white page. */
    private fun showAvatars(avatar: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            env.Content { Box(Modifier.testTag("page").size(140.dp).background(page).padding(20.dp)) { avatar() } }
        }
        compose.waitForIdle()
    }

    private fun shot(shape: CategoryIconShape): PixelMap {
        env.preferences.setCategoryIconShape(shape)
        compose.waitForIdle()
        return compose.onNodeWithTag("page").captureToImage().toPixelMap()
    }

    /** The page is 140dp wide whatever the screen density, so a dp is [width] / 140 pixels. */
    private val PixelMap.pixelsPerDp: Float get() = width / 140f

    /** The pixel [share] of the avatar's width in from its top left corner, along the diagonal. */
    private fun PixelMap.corner(share: Float): Color {
        val at = ((20 + 100 * share) * pixelsPerDp).toInt()
        return this[at, at]
    }

    /** Whether anything but the white page is drawn within the avatar's 100dp square. */
    private fun PixelMap.hasInk(): Boolean {
        val from = (20 * pixelsPerDp).toInt()
        val to = (120 * pixelsPerDp).toInt()
        for (y in from until to) for (x in from until to) if (this[x, y] != page) return true
        return false
    }

    @Test
    fun eachShapeCutsTheCornersItsOwnWay() {
        showAvatars { CategoryAvatar(null, size = 100.dp) }

        // A circle has no corner left at 12% in; a rounded square (30%) has; a square (12%) is cut only at the very corner.
        shot(CategoryIconShape.Circle).let {
            assertEquals(page, it.corner(0.05f), "circle: nothing at 5%")
            assertEquals(page, it.corner(0.12f), "circle: nothing at 12% either")
            assertNotEquals(page, it.corner(0.5f), "circle: the disc is there")
        }
        shot(CategoryIconShape.Rounded).let {
            assertEquals(page, it.corner(0.05f), "rounded: the corner is cut")
            assertNotEquals(page, it.corner(0.12f), "rounded: but not as far in as a circle's")
        }
        shot(CategoryIconShape.Square).let {
            assertEquals(page, it.corner(0.01f), "square: just softened at the very corner")
            assertNotEquals(page, it.corner(0.05f), "square: and filled right up to it")
        }
    }

    @Test
    fun withNoBackground_thereIsOnlyTheIcon() {
        showAvatars { CategoryAvatar(null, size = 100.dp) }

        val bare = shot(CategoryIconShape.None)
        assertEquals(page, bare.corner(0.05f), "no disc in the corner")
        assertEquals(page, bare.corner(0.12f))
        assertTrue(bare.hasInk(), "but the icon is drawn")
    }

    @Test
    fun aTransferFollowsTheShapeToo() {
        showAvatars { TransferAvatar(size = 100.dp) }

        assertEquals(page, shot(CategoryIconShape.Circle).corner(0.12f))
        assertNotEquals(page, shot(CategoryIconShape.Rounded).corner(0.12f), "rounded, its corner is further out than a circle's")
        assertNotEquals(page, shot(CategoryIconShape.Square).corner(0.05f))
        val bare = shot(CategoryIconShape.None)
        assertEquals(page, bare.corner(0.12f), "with no background, no tile behind the arrows")
        assertTrue(bare.hasInk())
    }

    // ------------------------------------------------------------ payee logos on a transaction row

    private val now = LocalDateTime.now().withNano(0)
    private val rapido = "brandlogos/rapido.webp"

    private fun seedTransaction(payee: String, accountName: String): FinanceTransaction = runBlocking {
        val finance = env.container.finance
        finance.load()
        val bank = finance.saveAccount(Account(name = accountName, kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        val shopping = finance.state.value.categories.first { it.name == "Shopping" && it.type == CategoryType.Expense }
        FinanceTransaction(
            type = TransactionType.Expense,
            amount = 120_00,
            accountId = bank.id!!,
            categoryId = shopping.id,
            payee = payee,
            occurredAt = now,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun showRow(tx: FinanceTransaction, accountName: String) {
        compose.setContent { env.Content { Box(Modifier.width(360.dp)) { TransactionTile(tx) } } }
        // The row is up (with its account's name) once the finance state has loaded.
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            compose.onAllNodes(hasText(accountName, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun aPayeeThatIsABrand_putsItsLogoOnTheRow_inPlaceOfTheCategoryIcon() {
        assertNull(AssetBitmaps.cached(rapido), "nothing has asked for the logo yet")
        showRow(seedTransaction("Rapido", "Logo bank"), "Logo bank")

        // The logo is what the row's avatar loads.
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            AssetBitmaps.cached(rapido) != null
        }
    }

    @Test
    fun inTheOutlineStyle_theRowKeepsItsCategoryIcon() {
        env.preferences.setCategoryIconStyle(CategoryIconStyle.Outline)
        showRow(seedTransaction("Zepto", "Outline bank"), "Outline bank")

        compose.waitForIdle()
        assertNull(AssetBitmaps.cached("brandlogos/zepto.webp"), "Outline draws no logos")
    }
}
