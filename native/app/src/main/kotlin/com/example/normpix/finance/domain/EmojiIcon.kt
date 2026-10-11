package com.example.normpix.finance.domain

/**
 * A category's icon is one of the app's own (a key such as "grocery") or an emoji, stored as this prefix and the emoji
 * itself: "emoji:🍔". Anything that came from outside the app - the keyboard, the clipboard, a backup - goes through
 * [isSingleEmoji] before it is kept, so what is stored is always exactly one emoji.
 */
const val EMOJI_ICON_PREFIX = "emoji:"

/** The longest single emoji there is, in UTF-16 units: a family of four with its joiners is 11; a subdivision flag, 14. */
const val MAX_EMOJI_LENGTH = 16

/** The icon string for [emoji]. */
fun emojiIcon(emoji: String): String = EMOJI_ICON_PREFIX + emoji

/** The emoji [icon] stands for; null when it is one of the app's own icons, or an "emoji:" that is not one emoji. */
fun emojiOf(icon: String): String? =
    if (icon.startsWith(EMOJI_ICON_PREFIX)) icon.substring(EMOJI_ICON_PREFIX.length).takeIf(::isSingleEmoji) else null

private const val ZWJ = 0x200D
private const val VARIATION_SELECTOR_16 = 0xFE0F
private const val KEYCAP = 0x20E3
private val RegionalIndicators = 0x1F1E6..0x1F1FF
private val SkinTones = 0x1F3FB..0x1F3FF
private val TagCharacters = 0xE0020..0xE007F

/** The characters a keycap emoji (1️⃣, #️⃣, *️⃣) is built on. */
private fun isKeycapBase(cp: Int) = cp in '0'.code..'9'.code || cp == '#'.code || cp == '*'.code

/** Code points that are an emoji (or the start of one) by themselves; the ranges the Unicode emoji data is drawn from. */
private fun isEmojiBase(cp: Int): Boolean = when (cp) {
    in 0x1F000..0x1FAFF, in 0x2600..0x27BF, in 0x2300..0x23FF, in 0x2B00..0x2BFF -> true
    in 0x2194..0x2199, 0x21A9, 0x21AA, 0x25FB, 0x25FC, 0x25FD, 0x25FE -> true
    0x00A9, 0x00AE, 0x203C, 0x2049, 0x2122, 0x2139, 0x24C2, 0x25AA, 0x25AB, 0x25B6, 0x25C0 -> true
    0x2934, 0x2935, 0x3030, 0x303D, 0x3297, 0x3299 -> true
    else -> false
}

/**
 * Whether [text] is exactly one emoji: a pictograph with its variation selector or skin tone, a flag (two regional
 * indicators, or a black flag with tag characters), a keycap, or any of those joined by zero-width joiners (👨‍👩‍👧). Nothing
 * else - no letters, no spaces, no second emoji - and no more than [MAX_EMOJI_LENGTH] characters.
 */
fun isSingleEmoji(text: String): Boolean {
    if (text.isEmpty() || text.length > MAX_EMOJI_LENGTH) return false
    val points = text.codePoints().toArray()
    var i = 0
    while (i < points.size) {
        i = elementEnd(points, i) ?: return false
        if (i == points.size) return true
        // Between two elements there is exactly one joiner, and the sequence cannot end on it.
        if (points[i] != ZWJ) return false
        i++
        if (i == points.size) return false
    }
    return false
}

/** Where the emoji element starting at [start] ends (exclusive): a flag, a keycap, or a pictograph and its modifiers; null if none starts there. */
private fun elementEnd(points: IntArray, start: Int): Int? {
    var i = start
    val first = points[i]
    when {
        first in RegionalIndicators ->
            return if (i + 1 < points.size && points[i + 1] in RegionalIndicators) i + 2 else null
        isKeycapBase(first) -> {
            i++
            if (i < points.size && points[i] == VARIATION_SELECTOR_16) i++
            return if (i < points.size && points[i] == KEYCAP) i + 1 else null
        }
        !isEmojiBase(first) -> return null
    }
    i++
    while (i < points.size) {
        val c = points[i]
        if (c == VARIATION_SELECTOR_16 || c in SkinTones || c in TagCharacters) i++ else break
    }
    return i
}

/** The first emoji in [text] - what a paste or a keyboard handed over, which may hold words or several - or null if there is none. */
fun firstEmoji(text: String): String? {
    val s = text.take(MAX_INPUT_SCAN)
    var start = 0
    while (start < s.length) {
        if (!Character.isLowSurrogate(s[start])) {
            var end = minOf(s.length, start + MAX_EMOJI_LENGTH)
            while (end > start) {
                val splitsPair = end < s.length && Character.isLowSurrogate(s[end])
                if (!splitsPair && isSingleEmoji(s.substring(start, end))) return s.substring(start, end)
                end--
            }
        }
        start++
    }
    return null
}

private const val MAX_INPUT_SCAN = 64
