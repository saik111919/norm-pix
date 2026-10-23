package com.example.normpix.updates

import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** One published version of NormPix and what it changed: a line of the changelog. */
data class ChangelogEntry(
    val versionName: String,
    /** The version's number as the build gives it (see [ReleaseMetadata.versionCodeOf]), which is what orders versions. */
    val versionCode: Long,
    val publishedAt: Instant?,
    /** What changed, as the release wrote it (see [ReleaseNotes] for reading it); may be empty. */
    val notes: String,
)

/**
 * Where the changelog stands: [entries] are what is on hand - saved from an earlier look, or just fetched - newest version
 * first, [loading] while GitHub is being asked, and [problem] when asking failed (the entries are then the saved ones).
 */
data class ChangelogState(
    val entries: List<ChangelogEntry> = emptyList(),
    val loading: Boolean = false,
    val problem: UpdateProblem? = null,
)

/**
 * The changelog kept between launches, so it can be read with no connection and a release's notes are still there once its
 * update has been installed. Every call is blocking: use it off the main thread.
 */
interface ChangelogStore {
    fun load(): List<ChangelogEntry>

    fun save(entries: List<ChangelogEntry>)

    /** Adds [entry] to what is kept, in place of the one for the same version. */
    fun remember(entry: ChangelogEntry) = save(load().with(entry))
}

/** [entries] with [entry] in it - replacing its version's - newest version first, and no more than [MAX_ENTRIES]. */
internal fun List<ChangelogEntry>.with(entry: ChangelogEntry): List<ChangelogEntry> =
    (filterNot { it.versionCode == entry.versionCode } + entry).sortedByDescending { it.versionCode }.take(Changelog.MAX_ENTRIES)

class MemoryChangelogStore(private var entries: List<ChangelogEntry> = emptyList()) : ChangelogStore {
    @Synchronized
    override fun load(): List<ChangelogEntry> = entries

    @Synchronized
    override fun save(entries: List<ChangelogEntry>) {
        this.entries = entries
    }

    @Synchronized
    override fun remember(entry: ChangelogEntry) = save(load().with(entry))
}

/** [ChangelogStore] in one JSON file. A file that can't be read or written is as good as none: the changelog is only a convenience. */
class FileChangelogStore(private val file: File) : ChangelogStore {
    @Synchronized
    override fun load(): List<ChangelogEntry> = try {
        val root = Json.parseToJsonElement(file.readText()) as? JsonObject
        (root?.get("entries") as? JsonArray).orEmpty().mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            ChangelogEntry(
                versionName = entry.string("versionName") ?: return@mapNotNull null,
                versionCode = (entry["versionCode"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null,
                publishedAt = entry.string("publishedAt")?.let(Changelog::instantOrNull),
                notes = entry.string("notes").orEmpty(),
            )
        }
    } catch (_: IOException) {
        emptyList()
    } catch (_: SerializationException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }

    // Read, changed and written under one lock: two looks at once (a check, the page) don't lose each other's version.
    @Synchronized
    override fun remember(entry: ChangelogEntry) = save(load().with(entry))

    @Synchronized
    override fun save(entries: List<ChangelogEntry>) {
        val json = buildJsonObject {
            put("schema", 1)
            put(
                "entries",
                buildJsonArray {
                    for (entry in entries) {
                        add(
                            buildJsonObject {
                                put("versionName", entry.versionName)
                                put("versionCode", entry.versionCode)
                                entry.publishedAt?.let { put("publishedAt", it.toString()) }
                                put("notes", entry.notes)
                            },
                        )
                    }
                },
            )
        }.toString()
        try {
            // Written beside the file and moved over it, so a write cut short never leaves half a changelog.
            file.parentFile?.mkdirs()
            val partial = File(file.parentFile, file.name + ".tmp")
            partial.writeText(json)
            if (!partial.renameTo(file)) {
                file.writeText(json)
                partial.delete()
            }
        } catch (_: IOException) {
            // Nothing kept: the next look asks GitHub again.
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

object Changelog {
    /** How many versions are kept and shown. */
    const val MAX_ENTRIES = 15

    internal fun instantOrNull(text: String): Instant? = try {
        Instant.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }
}

/** A line of a release's notes, read for showing as a list of changes rather than as a block of text. */
sealed interface NoteBlock {
    /** A title over the changes under it ("Fixes"). */
    data class Heading(val text: String) : NoteBlock

    /** One change: a bullet of the notes. */
    data class Change(val text: String) : NoteBlock

    /** A line of prose. */
    data class Paragraph(val text: String) : NoteBlock
}

/**
 * Reads a release's notes - the commit subjects CI lists as `- ...` lines, or a tag message written by hand in Markdown - into
 * [NoteBlock]s: bullets become changes, `#` lines headings, anything else prose. The Markdown marks that would show as stray
 * symbols (bold, code, links) are taken out, keeping their words.
 */
object ReleaseNotes {
    private val HEADING = Regex("""^#{1,6}\s*(.*)$""")
    private val BULLET = Regex("""^(?:[-*+•]|\d+[.)])\s+(.*)$""")
    private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val MARKS = Regex("""\*\*|__|`""")
    private val SPACES = Regex("""\s+""")

    fun parse(notes: String): List<NoteBlock> = notes.lineSequence().mapNotNull { block(it.trim()) }.toList()

    /** How many of [blocks] are changes, as "7 changes" counts them. */
    fun changes(blocks: List<NoteBlock>): Int = blocks.count { it is NoteBlock.Change }

    private fun block(line: String): NoteBlock? {
        if (line.isEmpty() || line.startsWith("<!--")) return null
        // What GitHub's own notes end with: a link to a comparison, which says nothing here.
        if (line.replace("*", "").startsWith("Full Changelog", ignoreCase = true)) return null
        HEADING.matchEntire(line)?.let { return clean(it.groupValues[1])?.let(NoteBlock::Heading) }
        BULLET.matchEntire(line)?.let { return clean(it.groupValues[1])?.let(NoteBlock::Change) }
        return clean(line)?.let(NoteBlock::Paragraph)
    }

    /** [text] without Markdown's marks; null when no words are left (a lone "-" or "###"). */
    private fun clean(text: String): String? = text.replace(LINK, "$1").replace(MARKS, "").replace(SPACES, " ").trim()
        .takeIf { line -> line.any(Char::isLetterOrDigit) }
}
