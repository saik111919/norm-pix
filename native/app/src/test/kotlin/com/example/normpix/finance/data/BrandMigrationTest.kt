package com.example.normpix.finance.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.ui.brandKeyForName
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrandMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `brandKeyForName resolves common names and variations`() {
        assertEquals("netflix", brandKeyForName("Netflix"))
        assertEquals("youtube", brandKeyForName("YouTube"))
        assertEquals("amazonprime", brandKeyForName("Amazon Prime"))
        assertEquals("amazonprime", brandKeyForName("Prime Video"))
        assertEquals("disneyplus", brandKeyForName("Disney+ Hotstar"))
        assertEquals("appletv", brandKeyForName("Apple TV+"))
        assertEquals("chatgpt", brandKeyForName("ChatGPT"))
        assertEquals("claude", brandKeyForName("Claude"))
        assertEquals("gemini", brandKeyForName("Google Gemini"))
        assertEquals("spotify", brandKeyForName("Spotify"))
        assertEquals("playstation", brandKeyForName("PlayStation Plus"))
        assertEquals("xbox", brandKeyForName("Xbox Game Pass"))
    }

    @Test
    fun `migrating database updates existing brand categories with generic icons`() {
        // Seed an in-memory DB where categories were added with generic icons
        val helper = FinanceDatabase(context, name = null)
        try {
            val db = helper.writableDatabase
            val stamp = 1_000_000L

            // Insert categories with generic icons
            val testRows = listOf(
                "Netflix" to "movies",
                "YouTube" to "streaming",
                "Amazon Prime" to "streaming",
                "Disney+ Hotstar" to "streaming",
                "Apple TV+" to "streaming",
                "ChatGPT" to "ai",
                "Spotify" to "music",
            )
            for ((name, icon) in testRows) {
                db.insert(
                    FinanceDatabase.CATEGORIES,
                    null,
                    ContentValues().apply {
                        put("name", name)
                        put("type", "expense")
                        put("icon", icon)
                        put("color", 1)
                        put("archived", 0)
                        put("sort_order", 0)
                        put("created_at", stamp)
                        put("updated_at", stamp)
                    },
                )
            }

            // Execute migration
            FinanceDatabase.migrateBrandCategoryIcons(db)

            // Verify they were migrated to exact brand keys
            fun getIcon(name: String): String? {
                return db.rawQuery(
                    "SELECT icon FROM ${FinanceDatabase.CATEGORIES} WHERE name = ?",
                    arrayOf(name),
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }

            assertEquals("netflix", getIcon("Netflix"))
            assertEquals("youtube", getIcon("YouTube"))
            assertEquals("amazonprime", getIcon("Amazon Prime"))
            assertEquals("disneyplus", getIcon("Disney+ Hotstar"))
            assertEquals("appletv", getIcon("Apple TV+"))
            assertEquals("chatgpt", getIcon("ChatGPT"))
            assertEquals("spotify", getIcon("Spotify"))
        } finally {
            helper.close()
        }
    }
}
