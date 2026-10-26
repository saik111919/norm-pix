#!/usr/bin/env bash
# Stop hook: keeps the normpix skill (.claude/skills/normpix) in step with the code.
#
# When an agent finishes a turn, this looks for source files the skill describes that have uncommitted changes newer than the
# newest skill file, and for drift that `verify.sh --quick` can see (versions, DB versions, new packages). If it finds either, it
# blocks the stop once and asks the agent to review the skill. It asks at most once per turn (Claude Code sets
# stop_hook_active on the follow-up stop) and FAILS OPEN: any problem here means "do nothing", never "trap the session".
#
# Remove the "Stop" entry from .claude/settings.json to switch it off. See .claude/skills/normpix/references/maintenance.md.

input="$(cat 2>/dev/null || true)"
printf '%s' "$input" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true' && exit 0

root="${CLAUDE_PROJECT_DIR:-}"
[ -n "$root" ] || root="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0
cd "$root" 2>/dev/null || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

skill=".claude/skills/normpix"
[ -d "$skill" ] || exit 0

# The newest file in the skill: editing any of it (or bumping the stamp) counts as having looked at it.
newest="$(find "$skill" -type f 2>/dev/null | xargs ls -t 2>/dev/null | head -1)"
[ -n "$newest" ] || exit 0

stale=""
count=0
while IFS= read -r line; do
    f="${line:3}"
    f="${f##* -> }"
    f="${f%\"}"
    f="${f#\"}"
    [ -f "$f" ] || continue
    if [ "$f" -nt "$newest" ]; then
        count=$((count + 1))
        [ "$count" -le 8 ] && stale="$stale $f"
    fi
done < <(git status --porcelain --untracked-files=all -- \
    native/app/src/main native/app/build.gradle.kts native/app/proguard-rules.pro \
    native/gradle/libs.versions.toml native/gradle.properties .github/workflows 2>/dev/null)

drift=""
if [ -f "$skill/scripts/verify.sh" ]; then
    drift="$(bash "$skill/scripts/verify.sh" --quick 2>/dev/null)"
    [ $? -eq 1 ] || drift=""
fi

[ "$count" -eq 0 ] && [ -z "$drift" ] && exit 0

msg="The normpix skill may be out of date."
[ "$count" -gt 0 ] && msg="$msg $count source file(s) changed after the skill was last touched:$stale."
[ -n "$drift" ] && msg="$msg Drift found by verify.sh: $(printf '%s' "$drift" | tr '\n' ' ' | tr -d '"\\')"
msg="$msg Before finishing, review .claude/skills/normpix (the code-area to section table is in references/maintenance.md). If your change altered something the skill states, update that reference and SKILL.md in this same change, run bash .claude/skills/normpix/scripts/verify.sh, and bump verified_commit and verified_date. If nothing the skill says has changed, just bump the stamp in references/maintenance.md. This reminder appears once per turn."

printf '{"decision":"block","reason":"%s"}\n' "$msg"
exit 0
