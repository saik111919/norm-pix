package com.example.normpix.finance.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Insights screen drawn whole with a believable month behind it, in Light and Dark - pictures in `app/build/qa_insights/` to be
 * looked at: where a chart's labels, a row's figure or the order of the panels is wrong is what the tests cannot see.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2000dp-xhdpi")
class InsightsRenderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "insights_render")
    private val outDir = File("build/qa_insights").also { it.mkdirs() }
    private val now = LocalDateTime.now()

    @BeforeTest
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun tearDown() = env.close()

    private fun dump(name: String) {
        File(outDir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** Six months of spending in five categories, and a salary, shaped like a real phone's: this month part-way through. */
    private fun seed() = runBlocking {
        val finance = env.container.finance
        val account = finance.saveAccount(Account(name = "Main", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        suspend fun category(name: String, icon: String, color: Int, type: CategoryType = CategoryType.Expense) =
            finance.saveCategory(Category(name = name, type = type, icon = icon, color = color, createdAt = now, updatedAt = now))
        val rent = category("Rent & housing", "home", 5)
        val food = category("Food & dining", "restaurant", 0)
        val groceries = category("Groceries", "grocery", 1)
        val transport = category("Transport", "transport", 2)
        val fun_ = category("Entertainment", "entertainment", 7)
        val salary = category("Salary", "salary", 1, CategoryType.Income)

        suspend fun record(type: TransactionType, c: Category, amount: Long, at: LocalDateTime) {
            finance.saveTransaction(
                FinanceTransaction(type = type, amount = amount, accountId = account.id!!, categoryId = c.id, occurredAt = at, createdAt = now, updatedAt = now),
            )
        }
        val today = LocalDate.now()
        for (back in 0..5) {
            val month = MonthKey.of(now).addMonths(-back)
            // Up to today in this month; any day of an earlier one.
            val lastDay = if (back == 0) maxOf(1, today.dayOfMonth - 1) else month.start.toLocalDate().lengthOfMonth()
            fun at(day: Int) = month.start.withDayOfMonth(minOf(day, lastDay)).plusHours(10)
            record(TransactionType.Expense, rent, 2_850_000, at(1))
            record(TransactionType.Expense, food, 310_000 + back * 70_000L, at(3))
            record(TransactionType.Expense, groceries, 420_000 - back * 20_000L, at(6))
            record(TransactionType.Expense, transport, 150_000 + back * 15_000L, at(8))
            record(TransactionType.Expense, groceries, 380_000, at(12))
            record(TransactionType.Expense, food, 210_000, at(15))
            record(TransactionType.Expense, fun_, 90_000 + back * 40_000L, at(18))
            record(TransactionType.Expense, transport, 110_000, at(22))
            record(TransactionType.Expense, food, 160_000, at(26))
            record(TransactionType.Income, salary, 9_500_000, at(1))
        }
    }

    @Test
    fun theScreenWhole() {
        seed()
        env.preferences.setMode(ThemeMode.Light)
        compose.setContent { env.Content { Surface(color = MaterialTheme.colorScheme.background) { InsightsScreen() } } }
        compose.waitUntil(30_000) {
            compose.waitForIdle()
            compose.onAllNodes(hasText("Spent this month")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        dump("insights_light")
        env.preferences.setMode(ThemeMode.Dark)
        compose.waitForIdle()
        dump("insights_dark")
    }
}
