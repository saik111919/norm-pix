#!/usr/bin/env bash
# Checks the normpix skill against the code. It never edits anything.
#
#   verify.sh           full check: facts, identifiers, cited file paths, package map
#   verify.sh --quick   facts and package map only (what the Stop hook runs)
#
# Exit 0: nothing has drifted. Exit 1: each problem is printed, one per line.
# The values it compares are in references/maintenance.md (see its "facts" and "symbols" blocks).
set -u

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
skill="$(cd "$here/.." && pwd)"
root="$(cd "$skill/../../.." && pwd)"
app="$root/native/app"
pkg="$app/src/main/kotlin/com/example/normpix"
maint="$skill/references/maintenance.md"

quick=0
[ "${1:-}" = "--quick" ] && quick=1
problems=0
say() { echo "$1"; problems=$((problems + 1)); }
rd() { tr -d '\r' < "$1"; }   # the working tree is mostly CRLF

[ -f "$maint" ] || { echo "verify.sh: $maint not found"; exit 2; }
[ -d "$pkg" ] || { echo "verify.sh: $pkg not found (is this the NormPix repo?)"; exit 2; }

# The lines between <!-- NAME:begin --> and <!-- NAME:end --> in maintenance.md.
block() {
    rd "$maint" | awk -v b="<!-- $1:begin -->" -v e="<!-- $1:end -->" '$0 == b { f = 1; next } $0 == e { f = 0 } f'
}

toml() { rd "$root/native/gradle/libs.versions.toml" | sed -n "s/^$1 = \"\(.*\)\"/\1/p" | head -1; }
gradle_kts() { rd "$app/build.gradle.kts" | sed -n "s/^ *$1 = \([0-9]*\).*/\1/p" | head -1; }
const_in() { rd "$1" | sed -n "s/.*const val $2 = \([0-9]*\).*/\1/p" | head -1; }

fact() {
    case "$1" in
        versionName) rd "$app/build.gradle.kts" | sed -n 's/.*versionName = "\([0-9.]*\)".*/\1/p' | head -1 ;;
        min_sdk) gradle_kts minSdk ;;
        target_sdk) gradle_kts targetSdk ;;
        compile_sdk) gradle_kts compileSdk ;;
        finance_db_version) const_in "$pkg/finance/data/FinanceDatabase.kt" VERSION ;;
        reminders_db_version) const_in "$pkg/reminders/RemindersDatabase.kt" VERSION ;;
        finance_backup_format) const_in "$pkg/finance/data/FinanceBackup.kt" FORMAT_VERSION ;;
        app_backup_version) const_in "$pkg/backup/AppBackupManifest.kt" VERSION ;;
        cards_file_version) const_in "$pkg/wallet/CardsTransfer.kt" VERSION ;;
        kotlin) toml kotlin ;;
        agp) toml agp ;;
        compose_bom) toml composeBom ;;
        robolectric) toml robolectric ;;
        gradle) rd "$root/native/gradle/wrapper/gradle-wrapper.properties" | sed -n 's/.*gradle-\([0-9.]*\)-all.zip.*/\1/p' | head -1 ;;
        launcher_icon_aliases) rd "$app/src/main/AndroidManifest.xml" | grep -c '<activity-alias' ;;
        guide_topics) rd "$pkg/about/Guide.kt" | grep -cE '^    [A-Z][A-Za-z]+\($' ;;
        *) echo "?" ;;
    esac
}

checked_facts=0
while IFS='=' read -r key want; do
    [ -z "$key" ] && continue
    case "$key" in verified_*) continue ;; esac
    got="$(fact "$key")"
    checked_facts=$((checked_facts + 1))
    if [ "$got" != "$want" ]; then
        say "FACT DRIFT: $key - the skill says '$want', the code says '${got:-<not found>}'"
    fi
done < <(block facts)

checked_packages=0
for d in "$pkg"/*/; do
    n="$(basename "$d")"
    checked_packages=$((checked_packages + 1))
    grep -qF "\`$n/\`" "$skill/SKILL.md" || say "PACKAGE NOT IN MAP: $n/ - add it to the code map in SKILL.md"
done

checked_symbols=0
checked_paths=0
if [ "$quick" -eq 0 ]; then
    while IFS= read -r sym; do
        [ -z "$sym" ] && continue
        checked_symbols=$((checked_symbols + 1))
        grep -rqF --include='*.kt' --include='*.kts' -- "$sym" "$app/src" "$app/build.gradle.kts" 2>/dev/null \
            || say "SYMBOL MISSING: $sym - cited in the skill, not found under native/app"
    done < <(block symbols)

    resolves() {
        local t="$1" b
        for b in "$root" "$root/native" "$app" "$pkg" "$app/src/main" "$app/src/main/kotlin" \
                 "$app/src/test" "$app/src/test/kotlin/com/example/normpix" "$app/src/main/res"; do
            [ -e "$b/$t" ] && return 0
        done
        local where=("$app/src" "$root/native/design" "$root/native/gradle" "$root/.github" "$root/docs" "$root/.claude")
        case "$t" in
            */*) [ -n "$(find "${where[@]}" -path "*/$t" -print -quit 2>/dev/null)" ] ;;
            *)   [ -n "$(find "${where[@]}" -name "$t" -print -quit 2>/dev/null)" ] || [ -e "$root/native/$t" ] ;;
        esac
    }

    while IFS= read -r token; do
        case "$token" in
            */build/*|*...*|/*|local.properties) continue ;;
        esac
        checked_paths=$((checked_paths + 1))
        resolves "$token" || say "PATH MISSING: $token - cited in the skill, not found"
    done < <(grep -ohE '`[A-Za-z0-9_./-]+\.(kt|kts|md|yml|xml|toml|properties|sh|pro|ttf|svg|py|ps1)`' \
                 "$skill/SKILL.md" "$skill"/references/*.md "$root/CLAUDE.md" 2>/dev/null | tr -d '`' | sort -u)
fi

if [ "$problems" -eq 0 ]; then
    echo "OK: skill matches the code ($checked_facts facts, $checked_packages packages, $checked_symbols identifiers, $checked_paths paths checked)"
    exit 0
fi
echo "$problems problem(s). Fix the skill's prose first, then the value in references/maintenance.md, then bump verified_commit/verified_date."
exit 1
