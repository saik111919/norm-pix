package com.example.normpix.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A ratchet on the design system's leaks: how many raw colours, hand-sized corners and off-scale spacings each source file has. The
 * review of 2026-11-06 found ~220 spacings off the scale (6, 10, 14, 18 and 22 dp) and 42 hand-sized corners among 1,100 hand-written
 * sizes; sweeping them would move the phone's design, so the count is held where it is and **may only go down**.
 *
 * `src/test/design-baseline.txt` records each file's counts. A file with MORE than it records fails (use `AppSpacing`, `AppRadius` or a
 * theme colour); a file with FEWER fails too, saying to lower the line, so the slack of a clean-up is never spent again. A file not
 * in the list has none. Art that is meant to have its own colours or sizes (bank cards, logos, guide pictures) is simply in the list.
 *
 * To record the sources as they are: `.\gradlew.bat :app:testDebugUnitTest --tests "*DesignTokenRatchetTest" -PdesignBaseline=write`.
 */
class DesignTokenRatchetTest {
    private val sources = File("src/main/kotlin")
    private val baselineFile = File("src/test/design-baseline.txt")

    /** What is counted: the name in the baseline, and what to write instead. */
    private val kinds: List<Pair<String, Regex>> = listOf(
        "colour" to Regex("""\bColor\(0x"""),
        "corner" to Regex("""RoundedCornerShape\([^()\n]*\d(\.dp|\s*\))"""),
        // The half-steps AppSpacing does not have: 16.dp must not be read as 6.dp, nor 1.6.dp as 6.dp.
        "halfstep" to Regex("""(?<![\w.])(6|10|14|18|22)\.dp\b"""),
    )

    private val advice = mapOf(
        "colour" to "read a colour from MaterialTheme / StatusTone, not Color(0x...)",
        "corner" to "use AppRadius (rXs..rXxlIncreased, rFull), not RoundedCornerShape(<n>.dp)",
        "halfstep" to "use an AppSpacing step (2, 4, 8, 12, 16, 20, 24, 32, 40, 48), not 6 / 10 / 14 / 18 / 22 dp",
    )

    private fun scan(): Map<String, List<Int>> {
        assertTrue(sources.isDirectory, "run from the app module: ${sources.absolutePath}")
        val counts = sortedMapOf<String, List<Int>>()
        sources.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            val found = kinds.map { (_, pattern) -> pattern.findAll(text).count() }
            if (found.any { it > 0 }) counts[file.relativeTo(sources).path.replace('\\', '/')] = found
        }
        return counts
    }

    private fun read(): Map<String, List<Int>> = baselineFile.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .associate { line ->
            val parts = line.split('|')
            parts[0] to parts.drop(1).map { it.trim().toInt() }
        }

    @Test
    fun noFileLeaksMoreThanItDid_andTheBaselineIsKeptTight() {
        val now = scan()
        if (System.getProperty("normpix.designBaseline") == "write") {
            val header = "# file|" + kinds.joinToString("|") { it.first } + "  (see DesignTokenRatchetTest: counts may only go down)"
            baselineFile.writeText((listOf(header) + now.map { (path, found) -> path + "|" + found.joinToString("|") }).joinToString("\n") + "\n")
            return
        }
        val was = read()
        val problems = mutableListOf<String>()
        for (path in (now.keys + was.keys).toSortedSet()) {
            val have = now[path] ?: kinds.map { 0 }
            val allowed = was[path] ?: kinds.map { 0 }
            kinds.forEachIndexed { i, (name, _) ->
                when {
                    have[i] > allowed[i] ->
                        problems += "$path: ${have[i]} $name (was ${allowed[i]}): ${advice.getValue(name)}"
                    have[i] < allowed[i] ->
                        problems += "$path: ${have[i]} $name, the baseline still says ${allowed[i]}: lower it (-PdesignBaseline=write)"
                }
            }
        }
        assertTrue(problems.isEmpty(), "The design system's leaks changed:\n" + problems.joinToString("\n"))
    }
}
