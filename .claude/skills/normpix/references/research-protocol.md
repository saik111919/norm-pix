# Research protocol: getting facts you don't have, without inventing them

The owner's rule (2026-10-26): when an agent needs information it doesn't have, it gets it from **Haiku 5.5 at extra-high effort
(xhigh) and Gemini 3.8 at high** - both told not to hallucinate - then **fact-checks** what comes back, and only then continues
with the change. This file is the procedure. The aim is fewer wrong guesses about APIs and library versions, which are the
expensive mistakes in a project pinned to Compose BOM 2026.09.00 / material3 1.4.0 / AGP 9.1.0 / Kotlin 2.4.0.

Contents: 1 When to use it · 2 Launching the researchers · 3 The brief · 4 Fact-check · 5 Record and continue · 6 If a model is unavailable

## 1. When to use it

| Situation | Route |
|---|---|
| Unfamiliar API, library or design specification | Research with **Haiku 5.5 (xhigh) + Gemini 3.8 Flash (High)** in parallel, fact-check, then continue |
| Complex or version-sensitive technical decision | Use **xhigh** effort when its additional reasoning is justified (below) |
| The answer is in this skill or in the repo | Don't research: read the reference, or `grep` the code |
| Doing the work (implementation, tests, debugging) | The owner's tiers: Haiku low / Sonnet 5.5 medium-high / Opus 5.5 complex (`owner-working-style.md` section 4) |

Raise effort to **xhigh** for: a choice that depends on a library's version (an API that changed or doesn't exist in material3
1.4.0, an AGP/Gradle behaviour), anything touching data safety (SQLite migrations, backup formats, Keystore/crypto, update
signature checks, NFC/APDU behaviour), and multi-file design with competing options or lifecycle/concurrency subtleties. Don't
spend xhigh on lookups the code already answers or on mechanical edits - the reasoning isn't "justified" there.

Check the skill first (`verified-facts.md` may already hold the answer, with its version and date). Keep the question narrow: one
question per researcher beats a survey.

## 2. Launching the researchers

Catalog as of 2026-10-26 (re-check with `orchestrator_capabilities`, e.g. `search: "gemini"`, if a call is rejected): Claude
Haiku 5.5 = provider instance `claudeAgent`, model `claude-haiku-5-5`, option `effort` (`xhigh` available); Gemini =
provider instance `antigravity`, models `gemini-3.8-flash-high` / `-medium` / `-low` (**Gemini 3.8 Flash (High)** is the "3.8
high" the owner means; there is no separate Pro entry and Gemini has no `effort` option - the level is in the model id).

**In T3 Code (the `t3-code` MCP tools exist): send both `delegate_task` calls in one message** so they run in parallel, with
`mode: "wait"` because the next step depends on the answers:

```
delegate_task {
  title: "Research: <topic> (Haiku 5.5 xhigh)", role: "research", mode: "wait",
  clientRequestId: "<topic>-haiku-1",
  target: { providerInstanceId: "claudeAgent", model: "claude-haiku-5-5", options: { effort: "xhigh" } },
  task: "<the brief from section 3>" }

delegate_task {
  title: "Research: <topic> (Gemini 3.8 Flash High)", role: "research", mode: "wait",
  clientRequestId: "<topic>-gemini-1",
  target: { providerInstanceId: "antigravity", model: "gemini-3.8-flash-high" },
  task: "<the same brief>" }
```

Options must be passed in `target.options` (a native subagent cannot reliably apply T3 model options). Check the returned
`providerInstanceId`, `model` and `options` match what you asked. Keep `clientRequestId` stable across retries of the same call
and distinct per round. The child shares the checkout, so the brief must say **read-only: do not edit, create or delete files**.
Use a fresh `delegate_task` for a follow-up round (restate the brief and the open objections); don't message the child thread.

**Without those tools** (plain Claude Code): `Agent` with `model: "haiku"`, `effort: "xhigh"` (permitted here because this skill
asks for it) and a research brief; Gemini is only reachable through T3's `delegate_task` or a Gemini CLI if one is installed.
If neither exists, **say that Gemini was not available** and use a second independent pass (another Haiku xhigh with a
differently-phrased brief, or Sonnet 5.5) plus primary sources. Never imply a model ran that didn't.

## 3. The brief (paste, fill in, send the same text to both)

```
You are a read-only researcher for an Android app. Do not edit, create or delete any file. Do not hallucinate.

QUESTION: <one precise question>
WHY IT MATTERS: <the decision this feeds, in one sentence>
PROJECT FACTS: Kotlin 2.4.0, AGP 9.1.0, Gradle 9.3.1, Jetpack Compose BOM 2026.09.00 (androidx.compose.material3 1.4.0),
  navigation-compose 2.10.2, minSdk 26, targetSdk 35, compileSdk 37, Robolectric 4.17. <add only what is relevant>
TODAY: <YYYY-MM-DD>. Your training data may be older than these library versions: say when something may have changed.

RULES
1. Every factual claim needs a source: an official URL (developer.android.com, kotlinlang.org, m3.material.io, the library's
   GitHub release notes or source), or a file path and line in this repository or in the library's sources. Quote exact
   signatures, package names, artifact coordinates and flag names.
2. Say which version or API level each claim applies to.
3. If you are not sure, write UNVERIFIED and what would verify it. Never invent an API, parameter, flag, artifact or version
   number. "I could not find this" is a correct and useful answer.
4. Label every claim [SOURCE-CHECKED] (you opened the source), [RECALLED] (from memory, not checked) or [INFERRED].
5. Use web search / fetch if you have it. If you do not have it, say so instead of answering from memory as if you had checked.
6. Offer alternatives only if they really exist; recommend one in two lines.

OUTPUT (max 40 lines): Answer. Evidence table (claim | source | label). Risks and unknowns.
```

## 4. Fact-check before using anything

1. **Compare.** A claim both researchers make *with a checkable source* is a candidate. Anything they disagree on, anything
   uncited, and anything labelled [RECALLED] / [INFERRED] / UNVERIFIED **must be verified** before it touches code.
2. **Verify against primary sources, strongest first:**
   a. The repo: `grep` for the API's existing use (the project may already do it).
   b. The dependency's actual code at the *pinned* version: Gradle cache, e.g.
      `find ~/.gradle/caches/modules-2/files-2.1/androidx.compose.material3 -name '*sources.jar'`, then `unzip -l` / `unzip -p`
      and grep. (This is how it was proven that material3 1.4.0 has the Expressive tokens but not their composables.)
   c. Official documentation and release notes for that version (`WebFetch` / `WebSearch`; prefer the standard search mode, use
      the extended one only when results are thin or the topic is very recent or niche).
   d. Compile it: the smallest snippet or a Robolectric test; an unresolved reference ends the argument.
   e. For platform behaviour: the emulator or the owner's phone with the qa build (rules in `build-test-release.md` section 10).
3. **The primary source wins** over both models, and over this skill if they disagree (then fix the skill).
4. If a fact can't be verified, don't build on it silently: pick the option that doesn't depend on it, or tell the owner what is
   unverified.

## 5. Record and continue

- Add each durable, version-specific fact you verified to `verified-facts.md` (claim, version, source, date) so nobody pays for it
  twice. One line is enough.
- Then make the change. Tell the owner in one line what was researched and what was confirmed or left unverified. If the research
  changes the plan (an API doesn't exist), say so and take the alternative rather than forcing the original.

## 6. If a model is unavailable

The T3 catalog changes. If `claude-haiku-5-5` or `gemini-3.8-flash-high` is rejected, run `orchestrator_capabilities` with a
search term, use the nearest listed equivalent (same provider, highest listed effort / "high" variant), and state the
substitution. Update this file's catalog line afterwards.
