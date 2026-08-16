#!/bin/sh
# Set the version across both projects to exactly <version>.
#
# Usage: scripts/set-version.sh X.Y.Z[-rc.N]
#
# Used by release-prepare.yml (stripping the -rc suffix to the final
# release version) and by scripts/bump-rc.sh (bumping the rc suffix).
set -eu

version=${1:?"usage: $0 <version>"}
root=$(git rev-parse --show-toplevel)

# Fully-qualified, version-pinned plugin coordinates: versions-maven-plugin
# is not declared in backend/pom.xml, so a bare `mvn versions:set` would
# resolve LATEST at run time — not reproducible. -DprocessAllModules
# rewrites the 4 children's <parent><version> cross-references.
# -DgenerateBackupPoms=false avoids leaving *.versionsBackup files behind.
(
  cd "$root/backend"
  ./mvnw -B -ntp \
    org.codehaus.mojo:versions-maven-plugin:2.18.0:set \
    -DnewVersion="$version" \
    -DprocessAllModules \
    -DgenerateBackupPoms=false
)

# --no-git-tag-version: this script never creates a git tag itself — tagging
# is release-publish.yml's job, at merge_commit_sha, not at bump time.
# --allow-same-version: sanity re-runs (e.g. after a failed step) shouldn't
# hard-fail just because the version is already set.
(
  cd "$root/frontend"
  npm version "$version" --no-git-tag-version --allow-same-version
)

echo "set-version: backend + frontend now at $version"
