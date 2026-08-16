# Contributing

## Setup

```
./scripts/setup-dev.sh
```

Activates `.githooks/pre-push`, a best-effort local check for the versioning
rule below. It is **not enforcement** — `git push --no-verify` skips it, and
it does nothing until you run this script. The authoritative check is the
`version-check` job in `.github/workflows/ci.yml`, which runs on every PR
regardless of whether you have the hook installed.

## Versioning

Releases are manual and PR-based (see [Releasing](#releasing-a-new-version)
below). Between releases, `main` carries a working `-rc.N` version:

```
0.1.0  --(first PR after release)-->  0.1.1-rc.1  -->  0.1.1-rc.2  -->  ...  --(release)-->  0.1.1 or 0.2.0 or 1.0.0
```

**Rule: every normal PR must set the version to exactly main's current
version with the rc number incremented by 1** — same `X.Y.Z` base, `rc.N+1`.
The first PR merged after a release bumps to the **next patch's** `rc.1`
(`0.1.0` → `0.1.1-rc.1`), not the next minor. This is deliberate: it's the
smallest possible next release, so whatever semantic-release actually
computes when a release is prepared — patch, minor, or major, depending on
the real commits since the last tag — is guaranteed to sort *above* this
rc baseline. Assuming "next minor" instead can under-shoot: if only `fix:`
commits land before a release, the real next version is a patch, which can
sort *below* an already-bumped "next minor" rc (`0.1.1` < `0.2.0-rc.2`) and
get a legitimate release PR wrongly rejected by `version-check`. The rc
number itself carries no meaning about *what kind* of release is coming —
only the release PR's computed version does.

This applies to **both** `backend/pom.xml` (parent + all 4 child modules,
kept in sync automatically by `versions-maven-plugin`) and
`frontend/package.json` + `frontend/package-lock.json` — all of them must
carry the identical version.

Bump your branch's version with:

```
./scripts/bump-rc.sh
```

It fetches `origin/main`, reads its current version, computes `main_version`
with the rc incremented by 1, and applies it to both projects via
`scripts/set-version.sh`. Commit the result:

```
git commit -am "chore: bump version to <version bump-rc.sh printed>"
```

The `version-check` CI job re-reads `main` at the time your PR's checks run
(not at your branch's fork point), and the branch-protection ruleset
requires your PR to be up to date with `main` before it can merge. In
practice this means: **whenever another PR merges while yours is open, your
PR's version is now stale and you'll need to rebase and re-bump**, even if
`version-check` was green before.

### Resolving a version conflict after a rebase

Two PRs open at the same time will usually pick the same next rc number.
Whichever merges second needs to rebase and bump again:

```sh
git fetch origin
git rebase origin/main
# conflicts in backend/**/pom.xml, frontend/package.json, frontend/package-lock.json
# — take main's side, the next step overwrites them anyway:
git checkout origin/main -- backend/pom.xml backend/*/pom.xml \
                            frontend/package.json frontend/package-lock.json
git add -A && git rebase --continue

./scripts/bump-rc.sh
git commit -am "chore: bump version to <version printed above>"
git push --force-with-lease
```

`version-check`'s failure message walks through the same steps — it always
tells you main's version, your PR's version, and what it expected.

### Release branches

`release/X.Y.Z` branches (created by `release-prepare.yml`, see below) are
exempt from the rc-bump rule — they instead must carry a **bare** `X.Y.Z`
version (no `-rc` suffix) that exactly matches the branch name and is
greater than main's current version. `version-check` detects this by branch
name and switches rules accordingly; hand-editing a release branch's version
is unusual and will very likely fail the check unless it lands on exactly
the version the branch name promises.

## Commit messages

Commits are linted (`commitlint.yml`, advisory today, required once the
branch-protection ruleset is fully configured) against [Conventional
Commits](https://www.conventionalcommits.org/), extended with `spec` and
`chore` as allowed types. This matters beyond style: `release-prepare.yml`
runs `semantic-release`'s commit analysis to decide the next version and to
generate release notes, and only PR merges are allowed on `main` (squash and
rebase merging are disabled — see below), so every individual commit you
write is what semantic-release actually reads.

Merges to `main` are **merge commits only** — squash and rebase merging are
disabled at the repository level. This is deliberate: it keeps semantic-release
reading real per-commit history regardless of which merge button gets
clicked, rather than depending on squash-commit-title conventions that are
easy to silently break.

## Releasing a new version

1. From the Actions tab, run **release-prepare** (`workflow_dispatch`, no
   inputs). It:
   - runs `semantic-release --dry-run` against `main`'s commit history since
     the last tag to compute the next version and release notes,
   - fails loudly with no side effects if there's nothing releasable (no
     `feat`/`fix`/`perf`/breaking-change commits since the last tag),
   - sets that version across both projects, strips any `-rc` suffix,
   - opens `release/X.Y.Z` with the generated notes in the PR body.
2. Review the PR like any other — checks (`tests`, `version-check`,
   `commitlint`) run on it same as any PR, since it's opened via a token
   that (unlike the default `GITHUB_TOKEN`) does trigger them. See
   [RELEASE_PR_TOKEN](#release_pr_token-lifecycle) below.
3. Merge it (merge commit, same as any other PR). This triggers
   `release-publish.yml`, which tags `X.Y.Z` at the merge commit and
   publishes a GitHub Release with the PR's notes.
4. If `release-publish` fails after the PR is merged (e.g. a transient API
   error), re-run it from the Actions UI — both the tag and release steps
   are idempotent and safe to retry; they detect existing state at the
   correct commit rather than re-creating or overwriting anything. If a tag
   already exists at a **different** commit than expected, the workflow
   refuses and asks for manual intervention rather than force-moving a
   release tag.

If `release-prepare` itself fails partway through (rare — most of its work
is read-only and happens before anything is pushed), check whether a
`release/X.Y.Z` branch exists with no PR: delete it and re-run, or open the
PR by hand using the notes uploaded as a workflow run artifact.

## RELEASE_PR_TOKEN lifecycle

`release-prepare.yml` needs to open a PR that GitHub's other workflows will
actually react to. PRs created with the default `GITHUB_TOKEN` **do not
trigger `pull_request` workflows** (a platform-level loop guard) — a release
PR opened that way would never get `tests`/`version-check`/`commitlint` to
run, and once those are required status checks, it would be permanently
stuck at "Expected — waiting for status."

`RELEASE_PR_TOKEN` (repository secret, `Settings → Secrets and variables →
Actions`) is a **fine-grained personal access token**, scoped to this repo
only, with:
- **Contents**: Read and write (to push the `release/X.Y.Z` branch)
- **Pull requests**: Read and write (to open the release PR and query for
  an existing one)

It is used **only** by `release-prepare.yml` — never `release-publish.yml`,
which deliberately uses `GITHUB_TOKEN` throughout so tag/release creation
can't accidentally trigger further workflow runs.

**Expiry:** fine-grained PATs cap out at a 1-year lifetime. This token
**will expire** and needs to be regenerated and re-saved as the
`RELEASE_PR_TOKEN` secret before (or when) that happens.

**Failure symptom when it expires:** `release-prepare.yml`'s checkout step
(the one passing `token: ${{ secrets.RELEASE_PR_TOKEN }}`) or a later `git
push` / `gh pr create` step fails with an authentication error — typically
`remote: Invalid username or token` on the push, or `gh: HTTP 401: Bad
credentials` from `gh pr create`/`gh pr list`. The `semantic-release
--dry-run` computation earlier in the job runs against local git history
only and will still succeed, so the failure surfaces specifically at the
push/PR-creation steps, not the version-computation ones. Regenerate the PAT
with the same scopes and repo, and update the `RELEASE_PR_TOKEN` secret.

## Manual repository settings this project depends on

These live in GitHub settings, not in this repo's files:

- **Merge strategy**: only "Allow merge commits" enabled; squash and rebase
  merging disabled.
- **Branch protection / ruleset on `main`**: requires a PR, requires the
  `version-check`, `tests / backend`, `tests / frontend`, and `commitlint`
  status checks, and requires branches to be up to date before merging
  (this last one is what makes a stale PR's old green run stop counting
  once another PR merges — see [Versioning](#versioning)).
- **Tag ruleset on `refs/tags/*`**: blocks tag deletion and update, so a
  published release tag can't be force-moved or removed.
- **`RELEASE_PR_TOKEN`** secret, as described above.
