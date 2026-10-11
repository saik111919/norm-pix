package com.example.normpix.finance.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmojiIconTest {

    /** Builds text from code points, so the test source stays plain ASCII. */
    private fun emoji(vararg codePoints: Int): String = buildString {
        for (cp in codePoints) appendCodePoint(cp)
    }

    private val pizza = emoji(0x1F355)
    private val burger = emoji(0x1F354)
    private val family = emoji(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467)
    private val indiaFlag = emoji(0x1F1EE, 0x1F1F3)

    @Test
    fun singleEmojiAreAccepted() {
        assertTrue(isSingleEmoji(pizza), "pizza")
        assertTrue(isSingleEmoji(emoji(0x2764, 0xFE0F)), "heart with variation selector")
        assertTrue(isSingleEmoji(emoji(0x1F44D, 0x1F3FD)), "thumbs up with skin tone")
        assertTrue(isSingleEmoji(family), "ZWJ family")
        assertTrue(isSingleEmoji(indiaFlag), "flag from two regional indicators")
        assertTrue(isSingleEmoji(emoji(0x31, 0xFE0F, 0x20E3)), "keycap 1")
        assertTrue(
            isSingleEmoji(emoji(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F)),
            "England flag with tag characters",
        )
    }

    @Test
    fun anythingElseIsRejected() {
        val rejected = listOf(
            "",
            " ",
            "a",
            "abc",
            "5",
            pizza + burger,
            pizza + "a",
            "a" + pizza,
            " " + pizza,
            emoji(0x200D),
            pizza + emoji(0x200D),
            emoji(0x1F1EE),
        )
        for (text in rejected) {
            assertFalse(isSingleEmoji(text), "should reject ${text.codePoints().toArray().contentToString()}")
        }
    }

    @Test
    fun tooLongIsRejected() {
        val many = pizza.repeat(9)
        assertTrue(many.length > MAX_EMOJI_LENGTH)
        assertFalse(isSingleEmoji(many))
    }

    @Test
    fun emojiIconAddsThePrefix() {
        assertEquals("emoji:" + pizza, emojiIcon(pizza))
    }

    @Test
    fun emojiOfReadsBackTheEmoji() {
        assertEquals(pizza, emojiOf("emoji:" + pizza))
    }

    @Test
    fun emojiOfIsNullForAppIconsAndBadPayloads() {
        assertNull(emojiOf("grocery"))
        assertNull(emojiOf("emoji:"))
        assertNull(emojiOf("emoji:abc"))
        assertNull(emojiOf("emoji:" + pizza + burger))
    }

    @Test
    fun firstEmojiFindsTheFirstEmojiInText() {
        assertEquals(pizza, firstEmoji("Pizza " + pizza + " night"))
        assertEquals(pizza, firstEmoji(pizza + burger))
        assertEquals(family, firstEmoji(family + " family"))
        assertEquals(indiaFlag, firstEmoji("Go " + indiaFlag + " now"))
    }

    @Test
    fun firstEmojiIsNullWhenThereIsNoEmoji() {
        assertNull(firstEmoji("no emoji here"))
        assertNull(firstEmoji(""))
    }
}
