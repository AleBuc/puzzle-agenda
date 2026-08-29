# Implementation Plan: Multi-Block, Multi-Day Activity Planning

**Branch**: `002-multi-day-activity-planning` | **Date**: 2026-08-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-multi-day-activity-planning/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Primary requirement: let a single backlog activity be planned into several
independent time-block "fragments," spread across one or more days within
the existing 2-week reachable horizon, with the activity's estimated
duration acting as a non-blocking per-day quota, same-activity/same-day
fragments auto-merging on touch or overlap, and per-day planning status
(UNPLANNED / PARTIALLY_PLANNED / PLANNED) always derived rather than
stored — superseding feature 001's "at most one scheduled block per
activity" model.

Technical approach: extend the existing hexagonal backend (same four Maven
modules, same Postgres/Flyway-backed overlap enforcement) with a new
domain merge service alongside `OverlapPolicy`, drop the now-incorrect
"one `PLANNED_ACTIVITY` block per activity" unique index, replace the
`Activity`'s stored-free-but-globally-computed `status` with per-day and
aggregate planning views, and extend the `CreateTimeBlock` /
`EditTimeBlock` / `MoveTimeBlock` / `DeleteTimeBlock` / `DeleteActivity`
use cases accordingly. The frontend (`BacklogView.vue`, `DayView.vue`, and
their composables) is updated to surface per-day remaining time, aggregate
per-day breakdowns, and the two-option fragment-delete prompt — no new
frontend architecture (still Vue 3 Composition API, no state-management
library).

## Technical Context

**Language/Version**: Java 25 (backend); Vue 3 Composition API on Vite
(frontend) — unchanged from feature 001

**Primary Dependencies**: Spring Boot 4.x (web, validation) in
infrastructure/bootstrap; Flyway for schema migrations; Vite + Vue 3, no
state-management library — no new dependency introduced by this feature

**Storage**: PostgreSQL 16+, schema managed exclusively through Flyway
migrations. The existing `EXCLUDE USING GIST (span WITH &&)` constraint on
`time_block` (V1 migration) remains the sole database-level overlap guard
and needs no change — same-activity merges are resolved in the
application layer (delete absorbed fragments + insert the merged one, in
one transaction) before anything is written, so the constraint never sees
a spurious same-activity conflict. A new migration drops
`time_block_activity_id_unique` (V1), the partial unique index that
enforced "at most one `PLANNED_ACTIVITY` block per activity," since that
invariant no longer holds.

**Testing**: Backend — JUnit 5 + AssertJ + Mockito for domain/application
(no Spring context); new `@ParameterizedTest` coverage for fragment merge
(pairwise, transitive/chain, idempotent full-overlap), per-day
UNPLANNED/PARTIALLY_PLANNED/PLANNED boundary cases, midnight-confinement
rejection, and cross-day move-with-merge; Spring Boot Test + Testcontainers
for infrastructure (updated `time_block` queries and the dropped unique
index); Frontend — Vitest + Vue Test Utils, extended for the activity
selector's per-day remaining-time display and the fragment-delete prompt.

**Target Platform**: Spring Boot REST backend + independently served Vue
SPA frontend — unchanged

**Project Type**: Web application (frontend + backend) in the existing
monorepo — unchanged; no new modules

**Performance Goals**: Single-user, interactive personal-planning app —
unchanged from feature 001; merge/status computation operates over at
most a handful of fragments per activity per day, well within
human-perceptible interaction time

**Constraints**: No authentication/multi-user (unchanged); 5-minute
granularity on every block boundary (unchanged); a `PLANNED_ACTIVITY`
fragment MUST NOT span midnight (new, FR-021) — unlike `ROUTINE` blocks,
which still may; per-day remaining time and planning status are computed
on every read, never persisted (FR-003, FR-009)

**Scale/Scope**: Single user; same reachable window as feature 001 (Day 1
through `today + 13`); an activity may now have on the order of a handful
of fragments per day and across the ~14-day reachable window, still well
within "a few dozen blocks per day" from feature 001's scale note

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design — see "Post-Design Re-check" below.*

| Principle | Gate | Status |
|---|---|---|
| I. Hexagonal Architecture with DDD | New fragment-merge logic lives in a domain service (`domain/service`, framework-free), alongside `OverlapPolicy`; per-day/aggregate planning views are computed in the application module from domain data; infrastructure only gains query methods and one migration — module boundaries and dependency direction unchanged | PASS |
| II. Data Integrity First | The existing `EXCLUDE USING GIST` constraint remains the sole DB-level overlap guard, untouched; merges are transactional (absorb-then-replace) so it never falsely rejects a same-activity merge; schema change (dropping the per-activity unique index) goes through a new Flyway migration | PASS |
| III. Test-Backed Development | Domain/application merge and status-derivation logic gets `@ParameterizedTest` coverage (merge chains, quota boundaries, midnight rejection) with no Spring context; infrastructure re-verified with Testcontainers; frontend additions covered with Vitest + Vue Test Utils | PASS |
| IV. API Contract Clarity | One documented breaking change: `GET /api/activities`'s global `status` field and `?status=` filter are replaced by per-day/aggregate planning fields (see Complexity Tracking) — the old field is not just incomplete but actively misleading once an activity can be planned on some days and not others | PASS (one documented deviation) |
| V. Simplicity Over Speculation (YAGNI) | No new backend module, no new frontend state-management library; per-day breakdown is embedded directly in the existing `GET /api/activities` response instead of a new endpoint; fragment-delete scope is a query parameter on the existing `DELETE /api/blocks/{id}`, not a new resource | PASS |

## Project Structure

### Documentation (this feature)

```text
specs/002-multi-day-activity-planning/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

No new top-level modules or directories; this feature extends existing
files within feature 001's structure.

```text
backend/
├── domain/
│   ├── src/main/java/alebuc/puzzleagenda/domain/
│   │   ├── activity/     # Activity (status field/ActivityStatus enum removed —
│   │   │                 # see data-model.md), Priority (unchanged)
│   │   ├── timeblock/    # TimeBlock (new: reject midnight span for
│   │   │                 # PLANNED_ACTIVITY), TimeRange, BlockType (unchanged)
│   │   ├── service/      # OverlapPolicy (unchanged), new FragmentMerger,
│   │   │                 # new DayPlanningStatus + calculator,
│   │   │                 # MaterializationService (unchanged)
│   │   ├── port/         # TimeBlockRepository (findByActivityId → List;
│   │   │                 # new findByActivityIdAndDay), ActivityRepository
│   │   │                 # (drop status-join responsibility)
│   │   └── exception/    # new: PlannedActivitySpansMidnightException
│   └── src/test/java/... # plain JUnit 5 + AssertJ, no Spring context
│
├── application/
│   ├── src/main/java/alebuc/puzzleagenda/application/
│   │   ├── activity/  # ListActivities (new: aggregate per-day planning
│   │   │               # fields), DeleteActivity (cascade over N fragments,
│   │   │               # fragment-count message)
│   │   ├── timeblock/ # CreateTimeBlock/EditTimeBlock/MoveTimeBlock (merge
│   │   │               # step + midnight guard), DeleteTimeBlock (scope
│   │   │               # param: this fragment vs. activity-day)
│   │   └── day/       # ViewDay (unchanged shape; activity selector data
│   │                   # comes from the extended ListActivities, not ViewDay)
│   └── src/test/java/... # plain JUnit 5 + AssertJ + Mockito, no Spring context
│
├── infrastructure/
│   ├── src/main/java/alebuc/puzzleagenda/infrastructure/
│   │   ├── rest/         # ActivityController (response shape change),
│   │   │                 # TimeBlockController (scope query param)
│   │   └── persistence/  # TimeBlockRepository adapter (new query;
│   │                     # ActivityRepository adapter drops status join)
│   ├── src/main/resources/db/migration/ # V4__drop_time_block_activity_unique.sql
│   └── src/test/java/...  # Spring Boot Test + Testcontainers (PostgreSQL)
│
└── bootstrap/  # unchanged

frontend/
├── src/
│   ├── components/  # ActivityCard (per-day remaining time / status badge),
│   │                # TimeBlockCard (unchanged), DayTimeline (unchanged)
│   ├── views/        # BacklogView (aggregate per-day breakdown, cascade
│   │                  # delete confirmation with fragment count), DayView
│   │                  # (activity selector shows remaining time; delete
│   │                  # prompt with fragment-scope choice)
│   ├── composables/  # useBacklog (day-scoped load, fragment-count aware
│   │                  # delete), useDaySchedule (scope param on deleteBlock)
│   └── api/           # unchanged client shape
└── tests/             # Vitest + Vue Test Utils
```

**Structure Decision**: Same as feature 001 — "web application" (frontend
+ backend) in the existing monorepo. This feature adds no new module,
service, or top-level directory; it extends existing files in place.

## Post-Design Re-check

Phase 1 design (data-model.md, contracts/api.md) confirmed no new
backend module, no new frontend state-management library, and no new
framework dependency inside the domain module. The merge and
per-day-status logic (`FragmentMerger`, `DayPlanningStatus`) are pure
domain services with no repository or framework dependency, mirroring
`OverlapPolicy` and `MaterializationService`. The database's `EXCLUDE`
constraint is unchanged and remains the sole DB-level overlap guard.
Constitution Check gates above still all PASS; the single documented
deviation (`GET /api/activities` breaking shape change) is unchanged from
the pre-Phase-0 check.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| `GET /api/activities` response shape changes in a backward-incompatible way: the global `status` field (`UNPLANNED`/`PLANNED`) and its `?status=` query filter (feature 001) are replaced by per-day and aggregate planning fields | FR-009 makes planning status a per-day concept; a single global status cannot represent "PLANNED on day D, UNPLANNED on day D+2" for the same activity, so keeping it would actively mislead callers rather than merely omit information | Keeping the old `status` field alongside the new fields was considered, but rejected: there is no non-misleading global value to put in it once multiple independent per-day states exist, and the only consumer of this API is this project's own frontend, updated in this same feature — carrying a dead/ambiguous field forward would violate Principle V for no compatibility benefit |
