package com.example.normpix.wallet

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Revealing a card can ask for one of the user's own NFC tags. What identifies a tag is its hardware ID: these pin down
 * what is accepted, what is refused, and that the stored entries say nothing about the tag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RegisteredTagsTest {
    private val mine = byteArrayOf(0x04, 0x5A, 0x1C, 0x32, 0x7B, 0x66, 0x80.toByte())
    private val other = byteArrayOf(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)

    private fun tags(store: TagStore = InMemoryTagStore(), keys: VaultKeyProvider = RegisteredTags.ephemeralKeys(), limit: Int = 3) =
        RegisteredTags(store, keys, limit)

    @Test
    fun aRegisteredTagIsRecognisedAndAnyOtherIsNot() {
        val tags = tags()
        assertEquals(TagRegistration.Added, tags.register(mine))
        assertTrue(tags.matches(mine))
        assertFalse(tags.matches(other))
        assertFalse(tags.matches(byteArrayOf()))
        assertEquals(1, tags.count.value)
    }

    @Test
    fun nothingMatchesBeforeAnythingIsRegistered() {
        assertFalse(tags().matches(mine))
    }

    @Test
    fun registeringTheSameTagAgainChangesNothing() {
        val tags = tags()
        tags.register(mine)
        assertEquals(TagRegistration.AlreadyRegistered, tags.register(mine.copyOf()))
        assertEquals(1, tags.count.value)
    }

    @Test
    fun severalTagsCanBeRegisteredUpToTheLimit() {
        val tags = tags(limit = 2)
        assertEquals(TagRegistration.Added, tags.register(mine))
        assertEquals(TagRegistration.Added, tags.register(other))
        assertEquals(TagRegistration.LimitReached, tags.register(byteArrayOf(0x04, 9, 9, 9, 9, 9, 9)))
        assertTrue(tags.matches(mine) && tags.matches(other))
        assertEquals(2, tags.count.value)
    }

    @Test
    fun aTagThatShowsANewIdEveryTapCannotBeRegistered() {
        val tags = tags()
        // ISO 14443-3: a 4-byte ID starting 0x08 is made up fresh for each tap (a phone emulating a card, many payment cards).
        assertEquals(TagRegistration.ChangesEveryTap, tags.register(byteArrayOf(0x08, 1, 2, 3)))
        assertEquals(TagRegistration.ChangesEveryTap, tags.register(byteArrayOf()))
        assertEquals(0, tags.count.value)
        // A real 4-byte ID is fine.
        assertEquals(TagRegistration.Added, tags.register(byteArrayOf(0x2A, 1, 2, 3)))
    }

    @Test
    fun theyAreStillThereAfterARestart() {
        val store = InMemoryTagStore()
        val keys = RegisteredTags.ephemeralKeys()
        tags(store, keys).register(mine)

        val again = tags(store, keys)
        assertEquals(1, again.count.value)
        assertTrue(again.matches(mine))
        assertFalse(again.matches(other))
    }

    @Test
    fun removingThemAllLeavesNothingToMatch() {
        val tags = tags()
        tags.register(mine)
        tags.clear()
        assertEquals(0, tags.count.value)
        assertFalse(tags.matches(mine))
    }

    @Test
    fun theStoredEntriesSayNothingAboutTheTag() {
        val store = InMemoryTagStore()
        val keys = RegisteredTags.ephemeralKeys()
        tags(store, keys).register(mine)
        val entry = store.load().single()

        assertFalse(entry.contains("045A1C32"), "no hex of the ID")
        val raw = Base64.decode(entry, Base64.NO_WRAP)
        assertTrue(raw.indices.none { start -> start + mine.size <= raw.size && (mine.indices).all { raw[start + it] == mine[it] } }, "the ID is not in the stored bytes")

        // The same tag registered afresh looks different: every entry has its own IV.
        val second = InMemoryTagStore()
        tags(second, keys).register(mine)
        assertNotEquals(entry, second.load().single())
    }

    @Test
    fun withoutTheKeyTheEntriesAreUselessAndCountAsNoTags() {
        val store = InMemoryTagStore()
        tags(store, RegisteredTags.ephemeralKeys()).register(mine)

        // A reset or restored device has lost the Keystore key.
        val afterKeyLoss = tags(store, RegisteredTags.ephemeralKeys())
        assertEquals(0, afterKeyLoss.count.value)
        assertFalse(afterKeyLoss.matches(mine))

        // Registering again starts clean: the unreadable entry is dropped, not kept alongside.
        assertEquals(TagRegistration.Added, afterKeyLoss.register(other))
        assertEquals(1, store.load().size)
        assertTrue(afterKeyLoss.matches(other))
    }

    @Test
    fun aTamperedEntryIsIgnoredNotTrusted() {
        val store = InMemoryTagStore()
        val keys = RegisteredTags.ephemeralKeys()
        tags(store, keys).register(mine)
        val raw = Base64.decode(store.load().single(), Base64.NO_WRAP)
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 1).toByte()
        store.save(setOf(Base64.encodeToString(raw, Base64.NO_WRAP)))

        val reopened = tags(store, keys)
        assertEquals(0, reopened.count.value)
        assertFalse(reopened.matches(mine))
    }

    @Test
    fun theEntriesLiveInPreferencesAndSurviveReloadingThem() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("registered_tags_test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        val keys = RegisteredTags.ephemeralKeys()
        tags(PrefsTagStore(prefs), keys).register(mine)

        val reloaded = tags(PrefsTagStore(context.getSharedPreferences("registered_tags_test", Context.MODE_PRIVATE)), keys)
        assertTrue(reloaded.matches(mine))
    }
}
