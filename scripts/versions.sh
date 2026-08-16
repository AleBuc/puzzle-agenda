#!/bin/sh
# Shared version-parsing and versioning-rule helpers.
#
# Sourced by (never executed directly):
#   .githooks/pre-push            — best-effort local check
#   .github/workflows/ci.yml      — version-check job (authoritative)
#   scripts/bump-rc.sh            — the rc-bump helper
#
# POSIX sh, no bashisms beyond `local` (supported by dash/bash/ash — the
# shells this actually runs under). Every read_* function accepts a file
# path, or "-" to read from stdin (both xmllint and awk understand "-").

# read_pom_version <file|->
# Prints the project's effective version: its OWN <version> (a direct
# child of <project>) if present, else the inherited <parent><version>.
# Needed because in this repo's multi-module layout, the root pom has
# its own version but the 4 child modules don't — they inherit it from
# <parent>, standard Maven convention.
read_pom_version() {
  file=$1
  if command -v xmllint >/dev/null 2>&1; then
    v=$(xmllint --xpath 'string(/*[local-name()="project"]/*[local-name()="version"])' "$file" 2>/dev/null)
    if [ -z "$v" ]; then
      v=$(xmllint --xpath 'string(/*[local-name()="project"]/*[local-name()="parent"]/*[local-name()="version"])' "$file" 2>/dev/null)
    fi
    echo "$v"
  else
    awk '
      /<parent>/ { in_parent = 1 }
      /<\/parent>/ { in_parent = 0; next }
      /<dependencies>/ { in_deps = 1 }
      /<\/dependencies>/ { in_deps = 0; next }
      /<version>/ {
        line = $0
        sub(/.*<version>/, "", line)
        sub(/<\/version>.*/, "", line)
        if (in_parent) {
          parent_version = line
        } else if (!in_deps && own_version == "") {
          own_version = line
        }
      }
      END { print (own_version != "") ? own_version : parent_version }
    ' "$file"
  fi
}

# read_json_version <file|->
read_json_version() {
  file=$1
  if command -v jq >/dev/null 2>&1; then
    jq -r '.version' "$file"
  else
    sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$file" | head -1
  fi
}

# read_lock_versions <file|->
# Prints TWO lines: top-level .version, then .packages[""].version
# (npm lockfileVersion 3 carries the version in both places).
read_lock_versions() {
  file=$1
  if command -v jq >/dev/null 2>&1; then
    jq -r '.version, .packages[""].version' "$file"
  else
    sed -n '0,/"version"/{s/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p}' "$file"
    awk '
      /"packages"[[:space:]]*:/ { in_packages = 1 }
      in_packages && /^\s*""[[:space:]]*:/ { in_root_pkg = 1 }
      in_root_pkg && /"version"/ {
        line = $0
        sub(/.*"version"[[:space:]]*:[[:space:]]*"/, "", line)
        sub(/".*/, "", line)
        print line
        exit
      }
    ' "$file"
  fi
}

# assert_all_equal <v1> <v2> <v3> [<v4> ...]
# Returns 0 if every argument is identical and non-empty, 1 otherwise.
assert_all_equal() {
  first=$1
  [ -n "$first" ] || return 1
  shift
  for v in "$@"; do
    [ "$v" = "$first" ] || return 1
  done
  return 0
}

# assert_bare_semver <version>
# True if <version> is X.Y.Z with no prerelease suffix.
assert_bare_semver() {
  echo "$1" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'
}

# assert_rc_semver <version>
# True if <version> is X.Y.Z-rc.N.
assert_rc_semver() {
  echo "$1" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+-rc\.[0-9]+$'
}

# next_rc <main_version>
# Bare X.Y.Z              -> X.Y.(Z+1)-rc.1
# X.Y.Z-rc.N              -> X.Y.Z-rc.(N+1)
# Anything else           -> prints nothing, returns 1
#
# Deliberately assumes the *smallest* possible next release (a patch), not
# a minor: whatever semantic-release actually computes for the real release
# (patch, minor, or major) is then guaranteed to be numerically greater
# than this rc baseline, which is what keeps a release/X.Y.Z branch's
# version always passing version-check's semver_gt-against-main check.
# Assuming "next minor" instead can under-shoot: if only fix: commits land
# between releases, the real next version is a patch, which can sort BELOW
# an already-bumped "next minor" rc baseline (e.g. 0.1.1 < 0.2.0-rc.2) and
# get a correct release PR wrongly rejected.
next_rc() {
  v=$1
  if assert_rc_semver "$v"; then
    base=${v%-rc.*}
    n=${v##*-rc.}
    echo "${base}-rc.$((n + 1))"
    return 0
  fi
  if assert_bare_semver "$v"; then
    x=$(echo "$v" | cut -d. -f1)
    y=$(echo "$v" | cut -d. -f2)
    z=$(echo "$v" | cut -d. -f3)
    echo "${x}.${y}.$((z + 1))-rc.1"
    return 0
  fi
  return 1
}

# semver_gt <a> <b>
# True if version <a> is strictly greater than <b>, where either may
# carry a -rc.N suffix. A bare release always outranks any of its own
# prereleases (0.2.0 > 0.2.0-rc.9), matching standard semver precedence.
semver_gt() {
  a=$1
  b=$2
  a_base=${a%%-rc.*}
  b_base=${b%%-rc.*}

  a_x=$(echo "$a_base" | cut -d. -f1); a_y=$(echo "$a_base" | cut -d. -f2); a_z=$(echo "$a_base" | cut -d. -f3)
  b_x=$(echo "$b_base" | cut -d. -f1); b_y=$(echo "$b_base" | cut -d. -f2); b_z=$(echo "$b_base" | cut -d. -f3)

  [ "$a_x" -gt "$b_x" ] && return 0
  [ "$a_x" -lt "$b_x" ] && return 1
  [ "$a_y" -gt "$b_y" ] && return 0
  [ "$a_y" -lt "$b_y" ] && return 1
  [ "$a_z" -gt "$b_z" ] && return 0
  [ "$a_z" -lt "$b_z" ] && return 1

  case "$a" in
    *-rc.*) a_is_rc=1 ;;
    *) a_is_rc=0 ;;
  esac
  case "$b" in
    *-rc.*) b_is_rc=1 ;;
    *) b_is_rc=0 ;;
  esac

  [ "$a_is_rc" -eq 0 ] && [ "$b_is_rc" -eq 1 ] && return 0
  [ "$a_is_rc" -eq 1 ] && [ "$b_is_rc" -eq 0 ] && return 1
  [ "$a_is_rc" -eq 0 ] && [ "$b_is_rc" -eq 0 ] && return 1

  a_rc=${a##*-rc.}
  b_rc=${b##*-rc.}
  [ "$a_rc" -gt "$b_rc" ]
}
