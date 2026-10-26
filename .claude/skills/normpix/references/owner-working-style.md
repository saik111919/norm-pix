# Working with the owner

Built on 2026-10-26 from the owner's own messages across ~10 sessions (Oct 2026) and their standing instructions. It is meant to
let an agent answer the way the owner wants without being told twice. Update it when the owner corrects you: a correction is the
most valuable fact in this file. Use they/them for the owner.

Contents: 1 How they ask, and what that means · 2 Interpreting common phrases · 3 Replies · 4 Models and effort ·
5 The work log (`native/TASKS.md`) · 6 Handoffs and "continue" · 7 Standing instructions

## 1. How they ask, and what that means

Observed patterns, each with the working rule it implies:

1. **Short, informal, typo-tolerant messages that assume shared context.** ("continue where we left", "check the issue an
   resolve it", "improve the design".) -> Never ask them to rephrase or restate. Reconstruct the context from the conversation,
   `git status` / `git log`, the newest `native/TASKS.md` phase and this skill, state your reading in one line, and proceed. Ask a
   question only when a decision is genuinely theirs and cannot be defaulted (use `AskUserQuestion` with concrete options and a
   recommendation); otherwise choose the sensible default and say so.
2. **Several requests in one message**, often joined by "Also ..." and sometimes ordered ("do this after the above one is
   completed"). -> Split into a numbered checklist at once, keep it visible, respect stated order, and close with a line per item:
   done / partly / not done and why. A dropped "also" is the commonest way to disappoint them.
3. **Screenshots and reference apps are the spec for look and feel.** They attach their own screenshots ("this is not looking
   good", "why same on multiple places") and name references (CRED's cards, Pixel's wallpaper & style picker, komi-store's
   navbar, DropSauce, Samsung Reminder, the launcher drawer's blur, a Linux window animation, an Apple Watch-style clock face).
   -> Read the image first. Name the concrete flaws you see (spacing, hierarchy, contrast, duplication, truncation) and fix those;
   take the *idea* from a reference and build it in this app's own design system ("implement our own design"), don't clone
   branding. Render your result and compare with the screenshot before saying it matches (`design-system.md` section 10).
4. **"Follow the design pattern / the app's existing pattern / best practices / Material".** -> Reuse existing components and
   conventions (`EditorSheet`, tonal fields, sheet-first decisions, `overlays.loading`) before inventing; check Material 3 /
   Android guidance for anything unfamiliar (research protocol).
5. **They like choices, not replacements.** "Keep previous design, also give an option to switch between this and the old one";
   "current setup stays the same"; "keep the current design for mobile as is". -> Add a Settings option, keep the old look/behaviour
   reachable, change a *default* only when they say so, and make a new default not disturb what existing users already chose.
6. **They ask for outcomes end to end**, including shipping: "if everything is good push and complete the merge request", "raise
   PR and merge it if everything is good", "yes and release apk". -> "If everything is good" is an instruction to *verify*: run the
   full gate, read the results, and merge only on green (see `build-test-release.md` section 9). Without those words, don't push,
   open or merge anything.
7. **"Check online how we can include this and create a plan and implement it."** -> Research -> short plan -> implement, in one
   go. This is where the research protocol applies (`research-protocol.md`): Haiku 5.5 xhigh + Gemini 3.8 Flash High, fact-checked,
   then carry on with the change.
8. **Bug reports come as symptom + screenshot** ("when sharing the image, the app isn't appearing on the share screen", "the
   amount and Paid to are not filled automatically"). -> Reproduce before theorising (local Gradle, CI log, real input), find the
   root cause, fix, add a regression test, and say what you reproduced. For receipt problems use the *real* shared image's
   recognizer output; "don't read what you created" means their real images, not your fixtures, are the truth.
9. **They care about structure that stays understandable.** "Create each service its own function so we don't get confused."
   -> One function/object per concern (one reader per payment app), named for what it does; no catch-all.
10. **They test on a real phone and report back**, and they remember what was and wasn't verified. -> Report honestly what you
    ran, what you saw, and what you could not try (emulator vs phone); don't claim visual or hardware behaviour from tests alone.
11. **They run several agents / sessions and sometimes hit context limits**, so they ask for handoffs ("give me the context of
    this chat to continue in a new thread"). -> See section 6.
12. **They correct tooling choices directly** ("don't use haiku 5.5 for hard and medium tasks"). -> Obey, and record the rule here
    and in memory so it isn't repeated.
13. **India-specific payments context** (UPI, PhonePe, Google Pay, Paytm, CRED, RuPay, rupee, bank SMS). Read receipts, formats
    and amounts accordingly; the rupee sign is often misread by OCR.

## 2. Interpreting common phrases

| They say | Do |
|---|---|
| "continue" / "continue where we left" / "complete this thread" | Resume the last unfinished item. Check `git status`, the latest handoff/summary and TASKS.md; don't restart, don't re-ask. |
| "this is not looking good, improve the design" + image | Diagnose from the image, apply the design system, render, compare, iterate once or twice before reporting. |
| "give me the context of this chat" | Write a handoff (section 6). |
| "what do we have in todo / task.md" | Read the `Backlog` section of `native/TASKS.md`, **verify each item against the code** (old `[ ]` lines go stale), list the real open items, name the stale ones. |
| "check the issue and resolve it" / "check why the build is failing" | Reproduce, root-cause, fix, regression test, run the affected tests, report cause + fix. |
| "check ci/cd and resolve all the warnings, code works as now" | Behaviour-preserving cleanup: change no behaviour, run the full gate before and after, report counts. |
| "raise PR and merge it if everything is good" | Full gate, push, PR, wait for the three checks, merge with a merge commit on green, report. |
| "release apk" | A merge to `main` publishes a release; or `assembleRelease` locally. Say which, and the version. |
| "add an option / toggle" | New setting following `architecture.md` section 6 (field, key, setter, row, search entry, backup is automatic, tests). |
| "keep it in todo" | Add a line to `native/TASKS.md` Backlog and say so; don't build it. |
| "do it for tablets / wide screens" | Window-size branches only; phone untouched (`design-system.md` section 8). |

## 3. Replies

- Lead with the result in plain words, not with process. Then: what you changed (files only if useful), how you verified it
  (tests run and counts, render looked at, emulator/phone or not), what you did **not** do or could not verify, decisions you
  made for them, and the one next step if there is one. Short beats exhaustive; they read the TASKS entry for detail.
- Be honest about failures and skipped steps: say "tests X failed with Y", not "mostly passing".
- Don't pad with offers. If something needs their decision (a design they must supply, a merge "go"), say that in one line.
- Use screenshots/renders as evidence for visual work; link code as `path:line`.
- Don't merge, publish, or touch their real phone/app data without being asked; confirm anything hard to reverse.

## 4. Models and effort

- **Doing work (implementation, tests, debugging), the owner's tiers (2026-10-13):** Haiku 5.5 for low/mechanical jobs with one
  obvious right answer (a search, a copy tweak, running a build and tallying); Sonnet 5.5 for medium to high (exploration across
  files, well-specified implementation, test writing); Opus 5.5 for complex (design across files, money/domain logic, debugging the
  recognizer, anything where a wrong call costs their data). When unsure, go up a tier. The owner explicitly asked *not* to use
  Haiku for hard or medium tasks.
- **Getting information (2026-10-26, supersedes "information gathering on Sonnet"):** unfamiliar API / library / design
  specification -> research with **Haiku 5.5 at xhigh effort and Gemini 3.8 Flash (High)**, told not to hallucinate, then
  fact-check, then continue with the change. A complex or version-sensitive technical decision -> use **xhigh** effort when its
  extra reasoning is justified. Full procedure and ready-to-paste briefs: `research-protocol.md`.
- A subagent starts cold: give it exact paths, the change, the constraints, ask for `file:line` citations, and verify its key claims.

## 5. The work log: `native/TASKS.md`

A dated log of phases, newest at the end before `Backlog`. The numbering restarts in the middle (two runs of "Phase 17-36"), so
refer to a phase by title + date. Convention for a new entry (match it):

```
## Phase N - Title in plain words (YYYY-MM-DD)
Asked for (YYYY-MM-DD[, with screenshots]): the owner's request paraphrased, including every part
- [x] **Bold lead-in**: what was done and why, naming the files/functions (`<File>.kt`, `barBaz`)
- [x] Version bump if one shipped: **Version 1.5.0**: new features
- [x] Tests: `NewTest` (n), `ChangedTest` (+1) ...
- [ ] Not tried on a phone: the things only a phone can show
```

`[ ]` lines record what wasn't verified or done on purpose. They go stale: verify against code before reporting one as open.
Parked ideas live under `## Backlog - to come back to` (per-bank card designs - blocked on the owner's designs; Samsung-style
reminders - parked). Add an entry for substantial work, not for every tweak, and tick old lines you finished.

## 6. Handoffs and "continue"

When asked for context to continue in a new thread, write (and keep under ~60 lines):

```
# NormPix handoff - <date, time>
Branch / HEAD / working tree state (clean? which files?), versionName, open PRs
What the owner asked for (every part, numbered) and where each stands (done / in progress / not started)
Decisions made and why (especially anything they chose or rejected)
Files touched (paths) and tests added/run with results
What was verified and how; what was not (phone/emulator)
Next steps in order; blockers needing the owner
Gotchas learned (anything that cost time)
```

When *receiving* a handoff or "continue": trust it as a lead, not as truth - re-check branch, `git status`, and that the named
files/tests exist, then proceed without re-asking what it already answers.

## 7. Standing instructions (kept from earlier sessions)

- Features go to `features`, bugs to `bugs`; merge to `main` only on the owner's go (see `build-test-release.md` section 9).
- Per-bank card designs wait for the owner's designs. The preview-update channel waits for their explicit go.
- Phone design never changes for tablet work. Settings Colors row stays the only inline switch.
- Never log or store card numbers, CVVs, clipboard contents, account numbers, UPI ids or references.
- Never put real receipts, names or numbers in the repo; delete temporary files from the owner's phone and PC after QA.
- Use the qa app only on devices; never touch the real NormPix install or its data.
- Don't create docs/README/CLAUDE.md churn the owner didn't ask for; this skill and `CLAUDE.md` are the exceptions they asked for.
