---
name: "pr-delivery"
description: "Take a branch to a reviewable pull request the way this repository's CI expects: branch name, Conventional Commits, the mandatory rc version bump, local checks, PR title and description. Use when committing, bumping the version, opening or updating a PR, after rebasing onto main, or when the version-check or commitlint job fails. Never merges."
argument-hint: "Optional: what to do (commit, bump, open PR, fix version-check)"
---

# PR delivery

`CONTRIBUTING.md` is the reference for everything below; read it when a case
is not covered here. This skill is the short path through it.

## Hard limits

- Never push to `main`. Never merge a PR. A human reviews and merges.
- Never use `git push --no-verify`.
- Never edit a version by hand. Versions change only through
  `scripts/bump-rc.sh` (or `scripts/set-version.sh` to repair a mismatch).
- Never create or edit a `release/X.Y.Z` branch or a tag. Releases are made by
  the `release-prepare` workflow, started by hand from the Actions tab.
- Force-push only with `--force-with-lease`, and only on your own branch.

## Branch

- Feature: `NNN-short-name`, created by `/speckit-specify` (for example
  `003-calendar-day-view`).
- Anything else: `<type>/<short-kebab-name>`, with the type of the main commit
  (`fix/frontend-delete-error-handling`, `docs/root-readme`,
  `ci/dependabot-version-exemption`).

## Commits

Conventional Commits, linted by `.commitlintrc.json`.

- Allowed types: `build`, `chore`, `ci`, `docs`, `feat`, `fix`, `perf`,
  `refactor`, `revert`, `style`, `test`, `spec`.
- Form: `type(scope): subject`. The scope is optional; the ones in use are
  `frontend` and `deps`.
- Subject in lower case, imperative, no final period, header within 100
  characters. Body lines have no length limit.
- End the subject with the task or story it implements when there is one:
  `feat(frontend): render the day as a proportional grid (US1)`,
  `chore(frontend): add reka-ui and shared time-grid math (T001-T003)`.
- One logical change per commit. Spec Kit artifacts (`specs/NNN-…/`) go in
  their own commit, separate from code.
- The body says why, and records anything a reader of the diff could not
  guess: a bug found on the way, a temporary state left for the next commit.

Only `feat`, `fix`, `perf` and breaking changes produce a release:
semantic-release reads the commits on `main` to compute the next version and
the release notes, so the type is not cosmetic. Mark a breaking change with
`!` after the type or a `BREAKING CHANGE:` footer, and justify it in the PR
(constitution, Principle IV).

## Version bump

Every PR must carry `main`'s current version with the rc number incremented by
exactly 1, identical in `backend/pom.xml` and its four modules,
`frontend/package.json` and `frontend/package-lock.json`. The `version-check`
job enforces it.

Do it last, on a branch already rebased on `origin/main`:

```sh
./scripts/bump-rc.sh
git commit -am "chore: bump version to <version printed by the script>"
```

The script needs network access (it fetches `origin/main`), the Maven wrapper
and npm. It computes the target itself, including the case of the first PR
after a release (`0.1.0` → `0.1.1-rc.1`, next patch, never next minor).

If another PR merges while this one is open, the bump is stale even if
`version-check` was green. Redo it:

```sh
git fetch origin
git rebase origin/main
# version conflicts: take main's side, the bump overwrites them anyway
git checkout origin/main -- backend/pom.xml backend/*/pom.xml \
                            frontend/package.json frontend/package-lock.json
git add -A && git rebase --continue
./scripts/bump-rc.sh
git commit -am "chore: bump version to <version printed by the script>"
git push --force-with-lease
```

Dependabot branches are exempt. If the version files disagree with each other,
`./scripts/set-version.sh <version>` realigns them.

## Before pushing

1. `cd backend && ./mvnw -B -ntp verify` (Docker must be running for
   Testcontainers).
2. `cd frontend && npm test && npm run build`.
3. The `architecture-check` skill reports no FAIL.
4. Rebase on `origin/main`, then bump the version (above).
5. The local hook is active: `./scripts/setup-dev.sh` once per clone. It is
   only fast feedback; CI is the gate.

## Pull request

Title: a Conventional Commit header, same rules as a commit subject
(`feat: calendar-style proportional day grid view`). `CONTRIBUTING.md`
specifies merge commits only, but recent PRs were squash-merged, and in a
squash merge the PR title is the commit semantic-release reads. A title that
is a valid header is correct under both strategies.

Description:

```markdown
## Summary
<what changes for the user, in two or three sentences>

## Spec
specs/<NNN-name>/ (spec, plan, tasks)   <!-- or: the bug and how it was found -->

## Changes
- <by user story or by area>

## Tests
- Backend: <what was added>, `./mvnw verify` green
- Frontend: <what was added>, `npm test` green (<n> tests)

## Manual validation
<smoke test: not run yet / passed on <date>, with the report's result>

## Constitution
<"No exception", or the Complexity Tracking row for each one;
 justification of any breaking API change>

## Out of scope
<what was deliberately left out, and anything found on the way and not fixed>
```

Required checks on the PR: `version-check`, `tests / backend`,
`tests / frontend`, `commitlint`, and the branch up to date with `main`.

Once the PR is open, stop. The browser smoke test (`smoke-test-prompt` skill)
runs before the merge, and the merge is the feature owner's.

## When a check fails

- **version-check, stale rc**: the rebase-and-bump sequence above. The job's
  message prints main's version, the PR's and the expected one.
- **version-check, files disagree**: `./scripts/set-version.sh <expected>`,
  commit, push.
- **commitlint**: reword the offending commit (`git commit --amend` for the
  last one, otherwise an interactive rebase run by the feature owner), then
  `git push --force-with-lease`.
- **tests**: fix the cause. Do not skip, disable or loosen a test to get the
  PR green.
