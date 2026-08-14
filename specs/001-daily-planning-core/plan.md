# Implementation Plan: Daily Schedule Planning

**Branch**: `001-daily-planning-core` | **Date**: 2026-08-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-daily-planning-core/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Primary requirement: let a single user build a conflict-free daily schedule
(routine, constrained-time, and planned-activity time blocks, all on
5-minute granularity) across a reachable window from Day 1 (the day of
first use) through 13 days ahead of today, backed by an activity backlog
and an optional daily routine template that pre-fills newly visited future
days without ever retroactively touching a day that was already
materialized.

Technical approach: a hexagonal/DDD backend (Java 25, Spring Boot 4.x,
PostgreSQL 16+ with Flyway) that enforces overlap and horizon rules in the
domain layer and again at the database via a timestamp range + exclusion
constraint, exposed as a REST API to a Vue 3 + Vite frontend with no
state-management library, per the project constitution.

## Technical Context

**Language/Version**: Java 25 (backend); Vue 3 Composition API on Vite
(frontend)

**Primary Dependencies**: Spring Boot 4.x (web, validation) in
infrastructure/bootstrap; Flyway for schema migrations; Vite + Vue 3, no
state-management library, on the frontend

**Storage**: PostgreSQL 16+, schema managed exclusively through Flyway
migrations; time-slot overlap prevented with a timestamp range column +
`EXCLUDE` constraint in addition to domain-level checks (see research.md)

**Testing**: Backend — JUnit 5 + AssertJ + Mockito for domain/application
(no Spring context), `@ParameterizedTest` for overlap/materialization edge
cases, Spring Boot Test + Testcontainers (PostgreSQL) for infrastructure;
Frontend — Vitest + Vue Test Utils

**Target Platform**: Spring Boot REST backend + independently served Vue
SPA frontend; no OS-specific requirement

**Project Type**: Web application (frontend + backend) in a monorepo:
`backend/` (Maven multi-module — domain, application, infrastructure,
bootstrap) and `frontend/` (Vue 3 + Vite)

**Performance Goals**: Single-user, interactive personal-planning app —
all CRUD and day-view operations complete well within normal
human-perceptible interaction time; no high-throughput or concurrency
requirement

**Constraints**: No authentication/multi-user (explicitly out of scope);
5-minute granularity on every block/template boundary; overlap and
reachable-range rules enforced at both the domain and database layers
(Constitution Principle II)

**Scale/Scope**: Single user; the reachable window grows over time from a
fixed Day 1 through a forward bound that slides with today (at most 14
"active" days at once, plus an unbounded but append-only past); at most a
few dozen time blocks per day

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design — see "Post-Design Re-check" below.*

| Principle | Gate | Status |
|---|---|---|
| I. Hexagonal Architecture with DDD | Domain (entities, value objects, `OverlapPolicy`, `MaterializationService`, repository ports) has zero framework dependencies; application module holds use cases and depends only on domain; infrastructure holds REST + persistence + Flyway; dependency direction enforced by Maven module boundaries | PASS |
| II. Data Integrity First | Overlap prevention modeled as a PostgreSQL range column + `EXCLUDE` constraint (GiST index), on top of the domain's `OverlapPolicy`; all schema changes via Flyway migrations | PASS |
| III. Test-Backed Development | Domain/application tested with plain JUnit 5 + AssertJ + Mockito (no Spring context), `@ParameterizedTest` planned for overlap and materialization-clipping cases; infrastructure tested with Spring Boot Test + Testcontainers; frontend with Vitest + Vue Test Utils | PASS |
| IV. API Contract Clarity | REST resources and status codes defined in `contracts/api.md`; this is the contract's initial version, no breaking-change concern yet | PASS |
| V. Simplicity Over Speculation (YAGNI) | Exactly the four constitution-mandated backend modules, no extra ones; no state-management library on the frontend; free-slot suggestion, weekly view, and general recurrence remain out of scope per the spec | PASS |

No violations — the Complexity Tracking table is intentionally empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-daily-planning-core/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── domain/
│   ├── src/main/java/dev/puzzleagenda/domain/
│   │   ├── activity/     # Activity entity, Priority value object
│   │   ├── timeblock/    # TimeBlock entity, TimeRange value object, BlockType
│   │   ├── routine/      # RoutineTemplateEntry entity
│   │   ├── horizon/      # HorizonState (Day 1), MaterializedDay, reachability rules
│   │   ├── service/      # OverlapPolicy, MaterializationService (clipping algorithm)
│   │   └── port/         # ActivityRepository, TimeBlockRepository,
│   │                     # RoutineTemplateRepository, HorizonStateRepository (interfaces)
│   └── src/test/java/... # plain JUnit 5 + AssertJ, no Spring context
│
├── application/
│   ├── src/main/java/dev/puzzleagenda/application/
│   │   ├── activity/  # CreateActivity, EditActivity, DeleteActivity
│   │   ├── timeblock/ # CreateTimeBlock, EditTimeBlock, MoveTimeBlock, DeleteTimeBlock
│   │   ├── routine/   # CreateRoutineEntry, EditRoutineEntry, DeleteRoutineEntry
│   │   └── day/       # ViewDay (materializes on first access), GetHorizon
│   └── src/test/java/... # plain JUnit 5 + AssertJ + Mockito, no Spring context
│
├── infrastructure/
│   ├── src/main/java/dev/puzzleagenda/infrastructure/
│   │   ├── rest/         # ActivityController, TimeBlockController,
│   │   │                 # RoutineTemplateController, DayController
│   │   ├── persistence/  # repository adapters implementing domain ports
│   │   └── config/       # Spring wiring
│   ├── src/main/resources/db/migration/ # Flyway V1__..., V2__... scripts
│   └── src/test/java/...  # Spring Boot Test + Testcontainers (PostgreSQL)
│
└── bootstrap/
    ├── src/main/java/dev/puzzleagenda/bootstrap/
    │   └── PuzzleAgendaApplication.java # Spring Boot main class, module wiring
    └── src/main/resources/application.yml

frontend/
├── src/
│   ├── components/  # TimeBlockCard, ActivityCard, DayTimeline, RoutineEntryForm, ...
│   ├── views/        # DayView, BacklogView, RoutineTemplateView
│   ├── composables/  # useDaySchedule, useBacklog, useRoutineTemplate
│   ├── api/           # HTTP client wrappers per backend resource
│   └── router/        # Vue Router config (day navigation, view switching)
└── tests/             # Vitest + Vue Test Utils
```

**Structure Decision**: Option "web application" (frontend + backend),
matching the constitution's Technology Constraints exactly: a monorepo
with `backend/` split into the four mandated Maven modules
(`domain` → `application` → `infrastructure`/`bootstrap`) and a separate
`frontend/` (Vue 3 + Vite, no state-management library).

## Post-Design Re-check

Phase 1 design (data-model.md, contracts/api.md) introduced no new
modules, no state-management library, and no framework dependency inside
the domain module. The overlap/horizon/materialization rules described in
data-model.md live in domain services (`OverlapPolicy`,
`MaterializationService`, `HorizonState` reachability checks) with the
database `EXCLUDE` constraint as a second, independent enforcement layer.
Constitution Check gates above still all PASS; no entries were added to
Complexity Tracking.

## Complexity Tracking

> No violations — this section is intentionally left empty.
