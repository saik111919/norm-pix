package com.example.normpix.settings

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What a font file says about itself that the interface needs to draw with it properly: how heavy it is, whether it is slanted
 * or narrow, and - for a variable font - how far its weight axis runs. Read from the file's own tables (OS/2, head, fvar), so a
 * file's name never has to be trusted: Google Fonts' downloads hold a variable file and a folder of static ones, and every one
 * of them says what it is.
 */
data class FontFileInfo(
    /** The weight class the font declares (400 regular, 700 bold); 400 when it does not say. */
    val weight: Int,
    val italic: Boolean,
    /** The width class: 5 is normal, less is condensed, more is expanded. */
    val widthClass: Int,
    /** The range of the `wght` axis when this is a variable font with one, else null. */
    val weightAxis: IntRange?,
) {
    val isVariable: Boolean get() = weightAxis != null

    companion object {
        /** The facts in the font at [file], or null when it can't be read as a TrueType or OpenType font. */
        fun read(file: File): FontFileInfo? = runCatching { RandomAccessFile(file, "r").use(::read) }.getOrNull()

        /** The facts in [bytes], a whole font file or its start (a font's tables are read wherever the directory says they are). */
        fun read(bytes: ByteArray): FontFileInfo? = runCatching { parse(Bytes(bytes)) }.getOrNull()

        private fun read(source: RandomAccessFile): FontFileInfo? = parse(Disk(source))

        private interface Source {
            fun at(offset: Long, count: Int): ByteArray
        }

        private class Disk(private val file: RandomAccessFile) : Source {
            override fun at(offset: Long, count: Int): ByteArray = ByteArray(count).also {
                file.seek(offset)
                file.readFully(it)
            }
        }

        private class Bytes(private val all: ByteArray) : Source {
            override fun at(offset: Long, count: Int): ByteArray = all.copyOfRange(offset.toInt(), offset.toInt() + count)
        }

        private fun parse(source: Source): FontFileInfo? {
            val header = source.at(0, 12)
            val tables = u16(header, 4)
            if (tables !in 1..MAX_TABLES) return null
            val directory = source.at(12, tables * 16)

            /** The [count] bytes of table [tag] from its byte [from], or null when the font has no such table or it is too short. */
            fun table(tag: String, from: Int, count: Int): ByteArray? {
                for (i in 0 until tables) {
                    val at = i * 16
                    if (String(directory, at, 4, Charsets.ISO_8859_1) != tag) continue
                    val offset = u32(directory, at + 8)
                    val length = u32(directory, at + 12)
                    return if (from + count <= length) source.at(offset + from, count) else null
                }
                return null
            }

            val os2 = table("OS/2", 0, 64)
            val weightClass = os2?.let { u16(it, 4) }?.takeIf { it in 1..1000 } ?: 400
            val widthClass = os2?.let { u16(it, 6) }?.takeIf { it in 1..9 } ?: 5
            val italicBit = os2?.let { u16(it, 62) and 0x01 != 0 } ?: false
            val macItalic = table("head", 44, 2)?.let { u16(it, 0) and 0x02 != 0 } ?: false

            var axis: IntRange? = null
            val fvar = table("fvar", 0, 16)
            if (fvar != null) {
                val axesAt = u16(fvar, 4)
                val count = u16(fvar, 8)
                val size = u16(fvar, 10)
                if (size >= 20 && count in 1..MAX_AXES) {
                    val axes = table("fvar", axesAt, count * size)
                    if (axes != null) {
                        for (i in 0 until count) {
                            val at = i * size
                            if (String(axes, at, 4, Charsets.ISO_8859_1) == "wght") {
                                val low = fixed(axes, at + 4).roundToInt()
                                val high = fixed(axes, at + 12).roundToInt()
                                if (low in 1..1000 && high >= low) axis = low..high
                            }
                        }
                    }
                }
            }
            return FontFileInfo(weightClass, italicBit || macItalic, widthClass, axis)
        }

        private const val MAX_TABLES = 200
        private const val MAX_AXES = 64

        private fun u16(b: ByteArray, at: Int): Int = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

        private fun u32(b: ByteArray, at: Int): Long =
            (u16(b, at).toLong() shl 16) or u16(b, at + 2).toLong()

        /** A 16.16 fixed-point number, as a font stores an axis's range. */
        private fun fixed(b: ByteArray, at: Int): Float = u32(b, at).toInt() / 65536f
    }
}

/**
 * Which of the fonts in a download the interface keeps. A Google Fonts zip holds a variable font that covers every weight in one
 * file, and static files (Regular, Bold, Italic ...) beside it; the interface wants the upright, normal-width fonts, and one
 * variable file does for all of them, so that is taken whenever there is one. Without it, one file for each weight asked for,
 * the regular first.
 */
object FontSets {
    /** The most files kept for one font family: the weights a text style asks for, and a few either side. */
    const val MAX_FACES = 8

    /** The order weights are kept in when a family has more than [MAX_FACES]: the regular, then what text asks for, then the rest. */
    private val PRIORITY = listOf(400, 500, 600, 700, 300, 800, 200, 900, 100)

    /** The weight ([100..900] in steps of 100) a declared weight class stands for: 350 is a light, 650 a semibold. */
    fun nearestWeight(declared: Int): Int = ((declared / 100f).roundToInt() * 100).coerceIn(100, 900)

    /** The files to keep out of [candidates], the one that draws regular text first; empty when there are none to choose from. */
    fun <T> choose(candidates: List<T>, info: (T) -> FontFileInfo): List<T> {
        // Upright and normal width where there are any: a "Condensed" or "Italic" file in the same download is not the family's text.
        val upright = candidates.filter { !info(it).italic }.ifEmpty { candidates }
        val normal = upright.filter { info(it).widthClass == 5 }.ifEmpty { upright }
        val variable = normal.filter { info(it).isVariable }.maxByOrNull { info(it).weightAxis!!.let { axis -> axis.last - axis.first } }
        if (variable != null) return listOf(variable)
        val byWeight = LinkedHashMap<Int, T>()
        for (candidate in normal) byWeight.putIfAbsent(nearestWeight(info(candidate).weight), candidate)
        return byWeight.entries
            .sortedBy { (weight, _) -> PRIORITY.indexOf(weight).let { if (it < 0) PRIORITY.size else it } * 1000 + abs(weight - 400) }
            .take(MAX_FACES)
            .map { it.value }
    }
}
