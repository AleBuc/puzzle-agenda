#!/bin/sh
# Mechanical part of the architecture-check skill: the constitution rules that
# can be decided by looking at the tree and at the diff against main.
#
# Usage: .claude/skills/architecture-check/check.sh [base-ref]
#        (base-ref defaults to origin/main)
#
# Prints one PASS / FAIL / INFO line per check, exits 1 on any FAIL.
# Not a substitute for `./mvnw verify`: Maven module boundaries remain the
# authoritative check for Principle I. See SKILL.md for the checks that need
# judgement.
set -u

base=${1:-origin/main}
root=$(git rev-parse --show-toplevel) || exit 2
cd "$root" || exit 2

status=0
pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1"; [ -n "${2:-}" ] && echo "$2" | sed 's/^/        /'; status=1; }
info() { echo "INFO  $1"; [ -n "${2:-}" ] && echo "$2" | sed 's/^/        /'; }

# check <label> <offending output>: PASS when the output is empty.
check() { if [ -z "$2" ]; then pass "$1"; else fail "$1" "$2"; fi; }

# Non-test-scope dependencies declared in a pom, one artifactId per line.
compile_deps() {
  awk '
    /<dependency>/     { id = ""; test = 0 }
    /<artifactId>/     { id = $0; gsub(/.*<artifactId>|<\/artifactId>.*/, "", id) }
    /<scope>test<\/scope>/ { test = 1 }
    /<\/dependency>/   { if (!test) print id }
  ' "$1"
}

have_base=1
if ! git rev-parse --verify --quiet "$base" >/dev/null; then
  have_base=0
  info "base ref '$base' not found — diff-based checks skipped (git fetch origin main)"
elif ! git merge-base "$base" HEAD >/dev/null 2>&1; then
  # Without a merge base every `$base...HEAD` diff below fails, and an empty
  # output would read as PASS.
  have_base=0
  fail "merge base of '$base' and HEAD not found — diff-based checks skipped" \
       "shallow clone? run: git fetch --unshallow origin"
fi

# --- Principle I: hexagonal boundary ----------------------------------------

out=$(grep -rnE '^import (static )?(org\.springframework|jakarta\.|javax\.persistence|org\.hibernate|com\.fasterxml)' \
        backend/domain/src/main backend/application/src/main 2>/dev/null)
check "I.   no framework import in domain/application main sources" "$out"

check "I.   domain/pom.xml has no dependency outside test scope" \
      "$(compile_deps backend/domain/pom.xml)"

check "I.   application/pom.xml depends only on domain" \
      "$(compile_deps backend/application/pom.xml | grep -vx 'domain')"

# --- Principle II: data integrity -------------------------------------------

migrations=backend/infrastructure/src/main/resources/db/migration
if [ "$have_base" = 1 ]; then
  out=$(git diff --name-status "$base"...HEAD -- "$migrations" | grep -v '^A')
  check "II.  existing Flyway migrations untouched (new files only)" "$out"
fi
out=$(ls "$migrations" 2>/dev/null | grep -vE '^V[0-9]+__[a-z0-9_]+\.sql$')
check "II.  migration files named V<n>__description.sql" "$out"

# --- Principle III: tests ----------------------------------------------------

out=$(grep -rlE 'org\.springframework|SpringBootTest' \
        backend/domain/src/test backend/application/src/test 2>/dev/null)
check "III. no Spring context in domain/application tests" "$out"

# --- Principle IV: API contract ----------------------------------------------

handler=backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/ApiExceptionHandler.java
messages=frontend/src/api/errorMessages.js
emitted=$(grep -oE '"[A-Z][A-Z_]{5,}"' "$handler" | tr -d '"' | sort -u)
out=""
for reason in $emitted; do
  grep -qE "^ +$reason:" "$messages" || out="$out$reason
"
done
check "IV.  every reason code has a mapped message in errorMessages.js" "$(printf '%s' "$out")"

# --- Principle V: simplicity -------------------------------------------------

want="application bootstrap domain infrastructure"
got=$(grep -oE '<module>[^<]+</module>' backend/pom.xml | sed 's/<[^>]*>//g' | sort | tr '\n' ' ' | sed 's/ $//')
if [ "$got" = "$want" ]; then
  pass "V.   backend modules are exactly domain, application, infrastructure, bootstrap"
else
  fail "V.   backend modules are exactly domain, application, infrastructure, bootstrap" "found: $got"
fi

check "V.   no state-management library in frontend/package.json" \
      "$(grep -nE '"(pinia|vuex)"' frontend/package.json)"

# Each file is flattened to one line first, so a multi-line import is matched.
out=$(find frontend/src -type f \( -name '*.vue' -o -name '*.js' \) \
        -exec sh -c 'for f; do tr "\n" " " < "$f"; echo; done' sh {} + \
        | grep -oE "import \{[^}]+\} from ['\"]reka-ui['\"]" \
        | grep -oE '[A-Z][A-Za-z]+' | sort -u | grep -v '^Dialog')
check "V.   reka-ui imports limited to Dialog*" "$out"

# --- Diff-based information, for the judgement checks ------------------------

if [ "$have_base" = 1 ]; then
  out=$(git diff "$base"...HEAD -- frontend/package.json \
          | grep -E '^\+ +"' | grep -vE '"(version|name|private|type)"')
  info "frontend/package.json lines added (each new dependency needs a Complexity Tracking row)" "$out"

  out=$(git diff "$base"...HEAD -- 'backend/pom.xml' 'backend/*/pom.xml' \
          | grep -E '^\+ *<artifactId>')
  info "backend pom.xml artifacts added (same rule)" "$out"

  out=$(git diff --name-only "$base"...HEAD -- backend/ | grep -v 'pom\.xml$')
  pom_out=$(git diff "$base"...HEAD -- 'backend/pom.xml' 'backend/*/pom.xml' \
              | grep -E '^[+-]' | grep -vE '^(\+\+\+|---)' | grep -v '<version>' \
              | sed 's/^/pom.xml: /')
  info "backend changes beyond the version bump (must be empty for a frontend-only feature)" \
       "$(printf '%s\n%s' "$out" "$pom_out" | sed '/^$/d')"
fi

exit $status
