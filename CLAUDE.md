# NormPix

A native Android app (Kotlin, Jetpack Compose, Material 3 Expressive) in `native/`: private, offline-first personal finance plus an
encrypted card wallet. One owner; releases ship from `main` through CI and the app updates itself from GitHub.

## Start here

**Before exploring or changing anything, invoke the `normpix` skill** (`.claude/skills/normpix/SKILL.md`). It holds the
architecture, code map, feature rules, design system, build/test/release/git workflow, how the owner works, and how to research
unfamiliar APIs, so you do not have to re-read the code to learn them. Open the reference file it points to for your task; read
source only to change it or to confirm a detail the skill does not state. History and reasoning for a feature: `Grep`
`native/TASKS.md` for its name (do not read the whole file).

## Rules for every task

- **Privacy:** the only network host is GitHub (updates). Never log or store card numbers, CVVs, clipboard contents, account numbers,
  UPI ids or references; never commit real receipts or personal data.
- **Data is sacred:** SQLite migrations and backup formats are additive; never drop data, rename a persisted key, or regenerate or
  commit the release keystore.
- **Design:** the phone design is the reference (wide-screen work branches on window size only); decisions and forms are bottom
  sheets; no snackbars from inside a sheet; reuse existing components; keep old designs as a Settings option when asked.
- **Windows tooling:** most sources are CRLF, so use the Edit tool for multi-line edits; "BUILD FAILED" with no failing test means the
  tests did not run (rerun with `--no-build-cache`); run `lintDebug` on its own; check that no other agent is running Gradle.
- **Git:** commit, push, open or merge a PR only when asked; check `git branch --show-current` and `git status` first; features go on
  `features`, bugs on `bugs`; a push to `main` publishes a release.
- **Devices:** test with the `qa` build only and never touch the owner's real NormPix install.
- **Prove it:** run the affected tests and read the result files, render UI changes and look at them, and say plainly what you could not
  verify (especially on a phone).

## When you need facts you do not have

Unfamiliar API, library or design specification: research with **Haiku 5.5 at xhigh effort and Gemini 3.8 Flash (High)** in
parallel, tell both not to hallucinate, fact-check what comes back against primary sources, then continue with the change. A complex
or version-sensitive technical decision: use xhigh effort when its extra reasoning is justified. Procedure, ready-to-paste brief and
the T3 `delegate_task` calls: `.claude/skills/normpix/references/research-protocol.md`. Do not research what the skill or the repo
already answers.

## Keep the skill true

The skill is part of the code. When a change alters something the skill states, update the matching file under
`.claude/skills/normpix/` in the same commit (the code-area to section table is in `references/maintenance.md`), run
`bash .claude/skills/normpix/scripts/verify.sh`, and bump `verified_commit` / `verified_date`. A `Stop` hook
(`.claude/hooks/skill-sync.sh`) reminds you when source files are newer than the skill. Record corrections from the owner in
`references/owner-working-style.md`.
