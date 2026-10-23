package com.example.normpix.updates

import java.io.File
import java.net.UnknownHostException
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What each version changed: reading a release's notes, the list of releases, the copy kept on the phone, and asking GitHub. */
class ChangelogTest {
    private val dir: File = createTempDirectory("changelog").toFile()
    private val network = FakeNetwork()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun entry(name: String, notes: String = "- $name", at: String? = "2026-10-15T09:30:00Z") =
        ChangelogEntry(name, ReleaseMetadata.versionCodeOf(name)!!, at?.let(Instant::parse), notes)

    // ------------------------------------------------------------ reading notes

    @Test
    fun `commit subjects become one change each`() {
        val notes = "- Linux-window strip motion\n- Month control styles\r\n- Accessibility audit"
        assertEquals(
            listOf("Linux-window strip motion", "Month control styles", "Accessibility audit").map(NoteBlock::Change),
            ReleaseNotes.parse(notes),
        )
    }

    @Test
    fun `headings, bullets of every kind and prose are told apart`() {
        val notes = """
            ## What's new
            Receipts are read faster.
            * Starred bullet
            + Plus bullet
            • Dot bullet
            1. Numbered
            ### Fixes
            - A fix
        """.trimIndent()
        assertEquals(
            listOf(
                NoteBlock.Heading("What's new"),
                NoteBlock.Paragraph("Receipts are read faster."),
                NoteBlock.Change("Starred bullet"),
                NoteBlock.Change("Plus bullet"),
                NoteBlock.Change("Dot bullet"),
                NoteBlock.Change("Numbered"),
                NoteBlock.Heading("Fixes"),
                NoteBlock.Change("A fix"),
            ),
            ReleaseNotes.parse(notes),
        )
    }

    @Test
    fun `markdown marks are taken out and their words kept`() {
        assertEquals(
            listOf(NoteBlock.Change("Fixed the Currency sheet, see the guide (#12)")),
            ReleaseNotes.parse("- **Fixed** the `Currency` sheet, see [the guide](https://example.com/g) (#12)"),
        )
        assertEquals(listOf(NoteBlock.Paragraph("bold words")), ReleaseNotes.parse("__bold__   words"))
    }

    @Test
    fun `blank lines, lone marks, comments and the comparison link GitHub adds say nothing`() {
        val notes = "\n   \n-\n###\n<!-- hidden -->\n**Full Changelog**: https://github.com/o/r/compare/v1.0.1...v1.0.2\n- Real change"
        assertEquals(listOf<NoteBlock>(NoteBlock.Change("Real change")), ReleaseNotes.parse(notes))
        assertEquals(emptyList(), ReleaseNotes.parse(""))
    }

    @Test
    fun `changes are counted without the headings and prose`() {
        val blocks = ReleaseNotes.parse("## Title\nIntro\n- One\n- Two")
        assertEquals(2, ReleaseNotes.changes(blocks))
    }

    // ------------------------------------------------------------ the list of releases

    @Test
    fun `version codes follow the build's formula`() {
        assertEquals(10003L, ReleaseMetadata.versionCodeOf("1.0.3"))
        assertEquals(11200L, ReleaseMetadata.versionCodeOf("1.12.0"))
        assertEquals(20000L, ReleaseMetadata.versionCodeOf("2.0.0"))
        assertNull(ReleaseMetadata.versionCodeOf("1.100.0"), "minor has to stay below 100")
        assertNull(ReleaseMetadata.versionCodeOf("1.0.100"), "so has patch")
        assertNull(ReleaseMetadata.versionCodeOf("1.0"))
        assertNull(ReleaseMetadata.versionCodeOf("v1.0.3"))
    }

    @Test
    fun `the list holds published releases only, newest version first`() {
        val json = Releases.list(
            Releases.note("1.0.1", "- First"),
            Releases.note("1.0.3", "- Third", publishedAt = "2026-10-20T08:00:00Z"),
            Releases.note("1.0.4", "- A test build of main", prerelease = true),
            Releases.api("v1.0.5", emptyList(), draft = true),
            Releases.note("1.0.10", "- Tenth"),
            Releases.note("1.0.2", "- Second"),
        )
        val entries = ReleaseMetadata.history(json)
        // By number: 1.0.10 comes after 1.0.3, which comparing the names as text would get wrong.
        assertEquals(listOf("1.0.10", "1.0.3", "1.0.2", "1.0.1"), entries.map { it.versionName })
        assertEquals(listOf(10010L, 10003L, 10002L, 10001L), entries.map { it.versionCode })
        assertEquals("- Third", entries.single { it.versionName == "1.0.3" }.notes)
        assertEquals(Instant.parse("2026-10-20T08:00:00Z"), entries.single { it.versionName == "1.0.3" }.publishedAt)
    }

    @Test
    fun `a release this app can't name is left out, not fatal to the list`() {
        val json = Releases.list(
            Releases.note("1.0.2", "- Fine"),
            Releases.api("nightly", emptyList()),
            Releases.api("v1.200.0", emptyList()),
            Releases.note("1.0.2", "- A duplicate tag"),
            "42",
            "{}",
        )
        assertEquals(listOf("1.0.2"), ReleaseMetadata.history(json).map { it.versionName })
        assertEquals("- Fine", ReleaseMetadata.history(json).single().notes)
    }

    @Test
    fun `no more than the most recent versions are kept, and their notes are trimmed`() {
        val many = (0..30).map { Releases.note("1.0.$it", "- $it") }.toTypedArray()
        val entries = ReleaseMetadata.history(Releases.list(*many))
        assertEquals(Changelog.MAX_ENTRIES, entries.size)
        assertEquals("1.0.30", entries.first().versionName)
        val long = ReleaseMetadata.history(Releases.list(Releases.note("1.0.1", "- x".repeat(10_000)))).single()
        assertTrue(long.notes.length <= 8_000, "notes are trimmed: ${long.notes.length}")
    }

    @Test
    fun `something that isn't a list of releases is a bad release, not a crash`() {
        for (json in listOf("not json", "{}", "\"text\"")) {
            val problem = assertFailsWith<UpdateException>(json) { ReleaseMetadata.history(json) }.problem
            assertIs<UpdateProblem.BadRelease>(problem, json)
        }
    }

    // ------------------------------------------------------------ the copy kept on the phone

    @Test
    fun `the file keeps the changelog exactly, and reads it back`() {
        val store = FileChangelogStore(File(dir, "nested/changelog.json"))
        assertEquals(emptyList(), store.load(), "nothing saved yet")
        val saved = listOf(entry("1.0.3", "- One\n- Two"), entry("1.0.2", "", at = null))
        store.save(saved)
        assertEquals(saved, FileChangelogStore(File(dir, "nested/changelog.json")).load())
    }

    @Test
    fun `a file that is damaged is as good as none`() {
        val file = File(dir, "changelog.json")
        val store = FileChangelogStore(file)
        for (text in listOf("", "not json", "[]", """{"entries": "none"}""", """{"entries": [1, {"versionName": "1.0.2"}]}""")) {
            file.writeText(text)
            assertEquals(emptyList(), store.load(), text)
        }
        store.save(listOf(entry("1.0.2")))
        assertEquals(listOf("1.0.2"), store.load().map { it.versionName }, "and is mended by the next save")
    }

    @Test
    fun `remembering a version replaces its entry, keeps the order, and keeps no more than the most recent`() {
        val store = FileChangelogStore(File(dir, "changelog.json"))
        store.remember(entry("1.0.2", "- Old words"))
        store.remember(entry("1.0.4"))
        store.remember(entry("1.0.2", "- New words"))
        assertEquals(listOf("1.0.4", "1.0.2"), store.load().map { it.versionName })
        assertEquals("- New words", store.load().last().notes)
        for (patch in 5..30) store.remember(entry("1.0.$patch"))
        assertEquals(Changelog.MAX_ENTRIES, store.load().size)
        assertEquals("1.0.30", store.load().first().versionName)
    }

    // ------------------------------------------------------------ asking GitHub

    private fun checker() = UpdateChecker(GitHubAccess(network, "NormPix/test"), Releases.REPO, Releases.installed)

    @Test
    fun `the checker asks for one page of releases and reads it`() {
        network.answer(Releases.HISTORY, Releases.list(Releases.note("1.0.2", "- Second"), Releases.note("1.0.3", "- Third")))
        assertEquals(listOf("1.0.3", "1.0.2"), checker().history().map { it.versionName })
        assertEquals(listOf(Releases.HISTORY), network.asked)
    }

    @Test
    fun `asking fails in the ways a check does`() {
        assertIs<UpdateProblem.NoRelease>(assertFailsWith<UpdateException> { checker().history() }.problem)
        network.fail(Releases.HISTORY, UnknownHostException())
        assertIs<UpdateProblem.Offline>(assertFailsWith<UpdateException> { checker().history() }.problem)
    }
}
