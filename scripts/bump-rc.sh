#!/bin/sh
# Bump this branch's version to main's current version with the rc number
# incremented by 1 — what rule B requires of every PR.
#
# Usage: scripts/bump-rc.sh [remote]   (remote defaults to "origin")
#
# Run this after rebasing onto an updated main (see CONTRIBUTING.md's
# conflict runbook) or as the first version bump on a fresh branch.
set -eu

remote=${1:-origin}
root=$(git rev-parse --show-toplevel)
cd "$root"
. "$root/scripts/versions.sh"

git fetch --quiet --no-tags --depth=1 "$remote" main
main_version=$(git show FETCH_HEAD:backend/pom.xml | read_pom_version -)

target=$(next_rc "$main_version") || {
  echo "bump-rc: main's version '$main_version' is neither a bare release" >&2
  echo "         (X.Y.Z) nor an rc version (X.Y.Z-rc.N) — cannot compute" >&2
  echo "         the next rc. main may need a one-time version bootstrap." >&2
  exit 1
}

echo "bump-rc: main is at $main_version, setting this branch to $target"
"$root/scripts/set-version.sh" "$target"
