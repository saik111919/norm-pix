package com.example.normpix.settings

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A font file with just enough in it to be read as one: the table directory and the three tables [FontFileInfo] looks at
 * (OS/2, head and, for a variable font, fvar). It is not drawable; the tests that install it say it loads.
 */
internal fun sfnt(weight: Int = 400, italic: Boolean = false, widthClass: Int = 5, axis: IntRange? = null): ByteArray {
    fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())
    fun u32(v: Long) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    val os2 = ByteArray(78).also {
        u16(weight).copyInto(it, 4)
        u16(widthClass).copyInto(it, 6)
        u16(if (italic) 1 else 0).copyInto(it, 62)
    }
    val head = ByteArray(54)
    val tables = linkedMapOf("OS/2" to os2, "head" to head)
    if (axis != null) {
        val fvar = ByteArrayOutputStream().apply {
            write(u16(1)); write(u16(0)); write(u16(16)); write(u16(2)); write(u16(1)); write(u16(20)); write(u16(0)); write(u16(0))
            write("wght".toByteArray(Charsets.ISO_8859_1))
            write(u32(axis.first * 65536L)); write(u32(400L * 65536)); write(u32(axis.last * 65536L))
            write(u16(0)); write(u16(256))
        }.toByteArray()
        tables["fvar"] = fvar
    }
    val out = ByteArrayOutputStream()
    out.write(byteArrayOf(0x00, 0x01, 0x00, 0x00))
    out.write(u16(tables.size)); out.write(u16(0)); out.write(u16(0)); out.write(u16(0))
    var offset = 12L + 16L * tables.size
    for ((tag, data) in tables) {
        out.write(tag.toByteArray(Charsets.ISO_8859_1)); out.write(u32(0)); out.write(u32(offset)); out.write(u32(data.size.toLong()))
        offset += data.size
    }
    for (data in tables.values) out.write(data)
    return out.toByteArray()
}

/** A zip of [entries] (path to bytes), as a download would be. */
internal fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, bytes) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}
