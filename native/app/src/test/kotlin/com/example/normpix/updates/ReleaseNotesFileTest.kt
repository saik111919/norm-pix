package com.example.normpix.updates

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `native/release-notes.md` is what the next release's notes are, when it has changed since the last release (the workflow says so),
 * and so what the app's Changelog page shows for it. The page shows each `-` line as one change under its `##` heading, so a line
 * wrapped onto a second one, loose prose, a long run-on bullet or a repeated one would all read badly there.
 */
class ReleaseNotesFileTest {
    // The tests run in native/app; the file is in native/.
    private val notes = File("../release-notes.md")

    @Test
    fun `the notes read as short bullets under headings, one change on each line`() {
        assertTrue(notes.isFile, "native/release-notes.md exists")
        val blocks = ReleaseNotes.parse(notes.readText())

        assertTrue(blocks.first() is NoteBlock.Heading, "starts with a heading, not with prose")
        assertEquals(emptyList(), blocks.filterIsInstance<NoteBlock.Paragraph>(), "every line is a heading or a bullet: no wrapped or loose lines")
        val changes = blocks.filterIsInstance<NoteBlock.Change>().map { it.text }
        assertTrue(changes.size in 1..40, "between 1 and 40 changes (${changes.size})")
        for (change in changes) assertTrue(change.length <= 110, "short enough to read at a glance (${change.length}): $change")
        assertEquals(changes.size, changes.toSet().size, "no change is said twice")
        assertTrue(blocks.zipWithNext().none { (a, b) -> a is NoteBlock.Heading && b is NoteBlock.Heading }, "no heading without changes under it")
        assertTrue(blocks.last() is NoteBlock.Change, "ends on a change, not a heading")
    }

    @Test
    fun `the notes are about the app, not about the repository`() {
        val text = ReleaseNotes.parse(notes.readText()).filterIsInstance<NoteBlock.Change>().joinToString("\n") { it.text }
        for (noise in listOf("README", "screenshots", "Merge ", "ratchet", "TASKS", "skill")) {
            assertTrue(noise !in text, "\"$noise\" is for the code, not for the person using the app")
        }
    }
}
