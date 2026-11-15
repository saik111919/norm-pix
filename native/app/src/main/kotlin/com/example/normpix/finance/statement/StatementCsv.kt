package com.example.normpix.finance.statement

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Reading a bank statement's file into rows of cells (bank statement import, 2026-11-15). Pure: it works on the bytes it is given,
 * keeps nothing and never writes any of it anywhere - not to a log, not to a message of an exception.
 *
 * Banks' CSV files are not tidy: a block of account details before the table, a summary after it, any of three delimiters, and
 * text in UTF-8 or in the old Windows code page. This reads all of them into plain rows; [StatementLayout] then finds the table.
 */
object StatementCsv {
    /** A statement is a small file: a bigger one is something else, and is not read. */
    const val MAX_BYTES = 2 * 1024 * 1024

    /** What a file is when it is not text a statement could be in. */
    enum class NotText { Pdf, Workbook }

    /** Whether [bytes] is a PDF or a workbook (.xlsx, .xls's newer sibling: a zip), which this cannot read; null for anything else. */
    fun notText(bytes: ByteArray): NotText? = when {
        bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte() -> NotText.Pdf
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2] == 3.toByte() && bytes[3] == 4.toByte() -> NotText.Workbook
        // The old binary Excel format starts with this fixed header.
        bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte() -> NotText.Workbook
        else -> null
    }

    private val windows1252: Charset = Charset.forName("windows-1252")

    /**
     * The text in [bytes]: UTF-8 (a byte order mark is dropped) or, for a file that is not valid UTF-8, Windows-1252 - what Excel and
     * most Indian banks' downloads use when they are not UTF-8 (a rupee sign or an accented letter reads right instead of garbled).
     */
    fun decode(bytes: ByteArray): String {
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, windows1252)
        }
        return text.removePrefix("\uFEFF")
    }

    /**
     * Every record of [text] as its cells, with the delimiter found from the text ([detectDelimiter]). Blank lines are left out.
     * Cells are trimmed; a quoted cell may hold the delimiter, a doubled quote (one quote) and line breaks.
     */
    fun parse(text: String): List<List<String>> = records(text, detectDelimiter(text))

    /**
     * Which of comma, semicolon and tab separates the cells: the one that splits the first records into the most rows of one width
     * - a table's rows all have the same number of cells, while the lines of account details around it have few. Comma without a clear
     * winner.
     */
    fun detectDelimiter(text: String): Char {
        val sample = if (text.length > SAMPLE_CHARS) text.substring(0, SAMPLE_CHARS) else text
        var best = ','
        var bestScore = 0L
        for (delimiter in listOf(',', ';', '\t')) {
            val widths = records(sample, delimiter, limit = SAMPLE_RECORDS).groupingBy { it.size }.eachCount().filterKeys { it >= 2 }
            val common = widths.maxWithOrNull(compareBy<Map.Entry<Int, Int>>({ it.value }, { it.key })) ?: continue
            val score = common.value.toLong() * (common.key - 1)
            if (score > bestScore) {
                best = delimiter
                bestScore = score
            }
        }
        return best
    }

    private const val SAMPLE_CHARS = 64 * 1024
    private const val SAMPLE_RECORDS = 300

    /**
     * [text] split into records of cells by [delimiter], by the rules of RFC 4180 as banks use them: a cell that starts with a quote
     * runs to the closing quote, holding delimiters and line breaks, and `""` inside it is one quote; a quote in the middle of a
     * cell is only a character. A line break (`\r\n`, `\n` or a lone `\r`) ends a record outside quotes. Records with nothing in
     * them are dropped.
     */
    private fun records(text: String, delimiter: Char, limit: Int = Int.MAX_VALUE): List<List<String>> {
        val out = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var quoted = false
        var onlyBlank = true
        val n = text.length
        var i = 0

        fun endCell() {
            row.add(cell.toString().trim())
            cell.setLength(0)
            quoted = false
            onlyBlank = true
        }

        fun endRecord() {
            endCell()
            if (row.any { it.isNotEmpty() }) out.add(row)
            row = ArrayList()
        }

        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') {
                        cell.append('"')
                        i += 2
                        continue
                    }
                    inQuotes = false
                } else {
                    cell.append(c)
                }
                i++
                continue
            }
            when {
                // A quote opens a quoted cell only where a cell starts (blanks before it are allowed).
                c == '"' && onlyBlank && !quoted -> {
                    inQuotes = true
                    quoted = true
                    cell.setLength(0)
                    onlyBlank = false
                }
                c == delimiter -> endCell()
                c == '\r' || c == '\n' -> {
                    if (c == '\r' && i + 1 < n && text[i + 1] == '\n') i++
                    endRecord()
                    if (out.size >= limit) return out
                }
                else -> {
                    cell.append(c)
                    if (!c.isWhitespace()) onlyBlank = false
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty() || quoted) endRecord()
        return out
    }
}
