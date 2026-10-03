---
name: "architecture-check"
description: "Check the current branch against the Puzzle Agenda constitution and the scope guards of the feature in progress: framework-free domain, module dependency direction, Flyway-only schema changes, test stack per module, mapped error messages, no unjustified dependency. Use at the end of an implementation and before committing or opening a PR, when adding a dependency, a Maven module, a migration or an error code, or when asked whether a change respects the architecture."
argument-hint: "Optional base ref to compare against (default: origin/main)"
---

# Architecture check

Verify that the branch respects the rules this project has written down. This
skill reports; it does not rewrite the rules and it does not fix violations
that fall outside the feature's scope.

## Sources of truth

Read them; do not rely on this file for their content.

1. `.specify/memory/constitution.md` — the five principles. It supersedes
   everything else.
2. `specs/<NNN-name>/plan.md` of the feature in progress — the Constraints
   line and the Complexity Tracking table hold the scope guards and the
   approved exceptions.
3. `specs/<NNN-name>/spec.md` — Assumptions and Out of scope.
4. `specs/001-daily-planning-core/contracts/api.md` — Error Conventions, as
   amended by later features' `contracts/`.

## Procedure

1. Run the mechanical checks from the repository root:

   ```sh
   .claude/skills/architecture-check/check.sh            # compares to origin/main
   .claude/skills/architecture-check/check.sh <base-ref>
   ```

   It prints one `PASS`, `FAIL` or `INFO` line per check and exits non-zero on
   any `FAIL`. `INFO` lines list what changed and need the judgement below.

2. Go through the judgement checks, which a script cannot decide.

3. Report (format at the end).

The compile-time boundary is the authoritative check for Principle I: from
`backend/`, `./mvnw -B -ntp verify` fails if a module reaches the wrong way.
The script only gives faster feedback.

## What the script checks

| Principle | Check |
|---|---|
| I | No Spring, Jakarta, JPA, Hibernate or Jackson import in `domain` or `application` main sources |
| I | `domain/pom.xml` has no dependency outside test scope; `application/pom.xml` depends only on `domain` |
| V | `backend/pom.xml` still declares exactly `domain`, `application`, `infrastructure`, `bootstrap` |
| II | Existing Flyway migrations are untouched; schema changes are new `V<n>__*.sql` files only |
| III | No Spring context in `domain` or `application` tests |
| IV | Every `reason` code emitted by `ApiExceptionHandler` has an entry in `frontend/src/api/errorMessages.js` |
| V | No state-management library in `frontend/package.json` |
| V | `reka-ui` imports are limited to `Dialog*` |
| — | INFO: runtime dependencies added to `frontend/package.json` or to a backend `pom.xml` |
| — | INFO: backend files changed beyond the version bump |

## Judgement checks

**Principle I — hexagonal boundary**

- Use cases in `application` carry no framework annotation. A new use case is
  wired as a `@Bean` in `infrastructure/config/UseCaseConfig.java`.
- Ports are interfaces in `domain/port`; their adapters live in
  `infrastructure/persistence` and use `NamedParameterJdbcTemplate`. There is
  no JPA anywhere in this project.
- Business rules sit in domain services or entities, not in controllers,
  adapters or SQL.

**Principle II — data integrity**

- A new rule on time ranges is enforced in the database as well (range type,
  `EXCLUDE` or `CHECK` constraint), not only in the domain.
- The new migration takes the next number after the highest existing one.

**Principle III — tests**

- The feature ships tests. A rule exercised against several input
  combinations uses `@ParameterizedTest`.
- Infrastructure is tested with Spring Boot Test and Testcontainers
  (`*IT.java`); the frontend with Vitest and Vue Test Utils in
  `frontend/tests/`.
- Both suites pass: `cd backend && ./mvnw -B -ntp verify` (needs Docker) and
  `cd frontend && npm test`.

**Principle IV — API contract**

- A new or changed endpoint is described in the feature's `contracts/`, with
  status codes consistent with the Error Conventions table.
- The UI never renders the backend `message` field. It resolves the `reason`
  through `resolveErrorMessage()`.
- A breaking change carries its justification in `plan.md` or in the PR
  description.

**Principle V — simplicity**

- For each dependency the script lists as added: there is a row for it in the
  feature's Complexity Tracking table, written before implementation. No row
  means a violation, whatever the dependency.
- State lives in composables (`frontend/src/composables/use*.js`) as plain
  refs.
- The frontend is plain JavaScript with `<script setup>`, scoped styles and
  the CSS variables of `style.css`. TypeScript, a CSS framework or a component
  library are not forbidden by name, but each is a new dependency or
  abstraction and needs the same justification.
- No new top-level structure under `frontend/src/` or `backend/`.

**Feature scope guards**

- If the plan says "zero backend changes", the script's backend INFO line must
  be empty: the only backend diff allowed is the version line changed by
  `scripts/bump-rc.sh`.
- Nothing listed as Out of scope in `spec.md` is implemented, even partially.
- No opportunistic refactoring outside the feature.

**Workflow**

- `spec.md`, `plan.md` and `tasks.md` exist for the feature and were reviewed
  by a human before implementation started.

## When something fails

- A violation inside the feature's scope: fix it, then run the check again.
- A violation whose fix is outside the feature's scope, or that would need a
  new exception: stop and report it. Do not fix it on your own and do not add
  a Complexity Tracking row yourself; an exception is the feature owner's
  decision.
- Principle I has no exceptions.

## Report

One table, then the open points.

```text
| Check | Status | Evidence |
|---|---|---|
| I. Domain framework-free | PASS | no framework import, domain/pom.xml test-scope only |
| ...                      | ...  | ... |
```

Then, if any: violations fixed, violations left for the feature owner with
the file and line, and INFO items with the Complexity Tracking row that covers
each of them.
