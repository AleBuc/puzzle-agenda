#!/bin/sh
# One-time local setup: activates the versioned pre-push hook in .githooks/.
#
# Usage: scripts/setup-dev.sh
set -eu

root=$(git rev-parse --show-toplevel)
cd "$root"
git config core.hooksPath .githooks
echo "setup-dev: core.hooksPath set to .githooks — pre-push version check is now active."
echo "           This is best-effort local feedback, not enforcement: CI's version-check"
echo "           job is the authoritative gate. See CONTRIBUTING.md#versioning."
