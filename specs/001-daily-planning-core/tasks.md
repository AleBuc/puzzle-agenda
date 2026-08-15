---

description: "Task list for Daily Schedule Planning (001-daily-planning-core)"
---

# Tasks: Daily Schedule Planning

**Input**: Design documents from `/specs/001-daily-planning-core/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api.md, quickstart.md,
`.specify/memory/constitution.md`

**Tests**: Included and mandatory (not optional) — Constitution Principle III (Test-Backed
Development) requires every feature to ship with tests before merge: JUnit 5 + AssertJ + Mockito
(no Spring context) for domain/application, `@ParameterizedTest` for overlap/materialization
edge cases, Spring Boot Test + Testcontainers (PostgreSQL) for infrastructure, Vitest + Vue Test
Utils for the frontend.

**Organization**: Tasks are grouped by user story (spec.md, priorities P1–P4) to enable
independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps the task to a user story (US1–US4) for traceability
- Every task lists an exact file path

## Path Conventions

Web application monorepo (plan.md Structure Decision):
`backend/{domain,application,infrastructure,bootstrap}/src/{main,test}/java/alebuc/puzzleagenda/...`
and `frontend/src/`, `frontend/tests/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization per plan.md's Technical Context and Project Structure.

- [X] T001 Create the backend Maven multi-module skeleton (parent `backend/pom.xml` plus
      `backend/domain/pom.xml`, `backend/application/pom.xml`, `backend/infrastructure/pom.xml`,
      `backend/bootstrap/pom.xml`) with the dependency direction
      `bootstrap`/`infrastructure` → `application` → `domain` enforced by module deps
      (Constitution Principle I; plan.md Project Structure)
- [X] T002 Initialize the frontend Vite + Vue 3 (Composition API) project, no state-management
      library, in `frontend/` (plan.md Technical Context; Constitution Principle V)
- [X] T003 [P] Add backend test dependencies (JUnit 5, AssertJ, Mockito, Spring Boot Test,
      Testcontainers PostgreSQL module) to the relevant module `pom.xml` files under `backend/`
- [X] T004 [P] Add frontend test tooling (Vitest, Vue Test Utils) to `frontend/package.json`
- [X] T005 [P] Configure the Flyway Maven plugin and PostgreSQL 16+ dev connection properties in
      `backend/infrastructure/pom.xml` and `backend/bootstrap/src/main/resources/application.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, cross-cutting domain primitives, and wiring every user story depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T006 Create Flyway migration for the `activity` and `time_block` tables, including the
      generated `span tsrange` column, the `EXCLUDE USING GIST (span WITH &&)` constraint, and
      5-minute-granularity `CHECK` constraints, in
      `backend/infrastructure/src/main/resources/db/migration/V1__create_activity_and_time_block.sql`
      (research.md §1, §2, §6; data-model.md Activity, TimeBlock)
- [X] T007 [P] Create Flyway migration for the `routine_template_entry` table in
      `backend/infrastructure/src/main/resources/db/migration/V2__create_routine_template_entry.sql`
      (data-model.md RoutineTemplateEntry)
- [X] T008 [P] Create Flyway migration for the `materialized_day` and `horizon_state` tables in
      `backend/infrastructure/src/main/resources/db/migration/V3__create_materialized_day_and_horizon_state.sql`
      (research.md §4, §5; data-model.md MaterializedDay, HorizonState)
- [X] T009 Implement the `TimeRange` value object enforcing 5-minute granularity and half-open
      `[start, end)` semantics in
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/timeblock/TimeRange.java`
      (research.md §1, §6)
- [X] T010 [P] Implement the `BlockType` enum (`ROUTINE`, `CONSTRAINED`, `PLANNED_ACTIVITY`) in
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/timeblock/BlockType.java`
- [X] T011 [P] Implement the `Priority` enum (`LOW`, `MEDIUM`, `HIGH`) in
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/activity/Priority.java`
- [X] T012 Implement the `HorizonState` entity and Day-1/forward-bound reachability rules in
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/horizon/HorizonState.java`
      (research.md §5; data-model.md HorizonState)
- [X] T013 [P] Implement the `MaterializedDay` marker entity in
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/horizon/MaterializedDay.java`
      (research.md §4)
- [X] T014 Define repository ports `HorizonStateRepository`, `MaterializedDayRepository` in
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/port/` (plan.md Project Structure).
      **Scope correction made during implementation**: `ActivityRepository`,
      `TimeBlockRepository`, and `RoutineTemplateRepository` are *not* defined here — their
      entities (`Activity`, `TimeBlock`, `RoutineTemplateEntry`) don't exist until T040/US2,
      T024/US1, T060/US4 respectively, so defining those ports now would forward-reference
      nonexistent types. Each is added to `domain/port/` alongside its entity, in the same task,
      instead. See `domain/port/package-info.java` for the documented rationale.
- [X] T015 [P] Parameterized domain unit tests for `HorizonState` reachability (`day1` null vs.
      set, forward bound = `today + 13`, before/after both bounds) in
      `backend/domain/src/test/java/alebuc/puzzleagenda/domain/horizon/HorizonStateTest.java`,
      plain JUnit 5 + AssertJ (Constitution Principle III; spec Edge Cases — day before Day 1,
      day 13 vs. day 14)
- [X] T016 Implement a global REST exception handler mapping domain/application exceptions to
      `{ "reason", "message" }` bodies with the 400/404/409/422 codes from contracts/api.md's
      Error Conventions table, in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/ApiExceptionHandler.java`
- [X] T017 Wire the Spring Boot bootstrap module (main class, module component scanning,
      datasource + Flyway config) in
      `backend/bootstrap/src/main/java/alebuc/puzzleagenda/bootstrap/PuzzleAgendaApplication.java`
      and `backend/bootstrap/src/main/resources/application.yml`
- [X] T018 [P] Implement `GetHorizon` use case and `GET /api/horizon` endpoint returning
      `{ day1, forwardBound }` in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/day/GetHorizon.java` and
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/DayController.java`
      (contracts/api.md `GET /api/horizon`). **Gap filled during implementation**: no tasks.md
      task provisions a `HorizonStateRepository` adapter anywhere (unlike
      `MaterializedDayRepository`, correctly deferred to T064/US4 since nothing calls it yet),
      so `GetHorizon` had no way to be wired to a real bean. Added
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/persistence/HorizonStateRepositoryAdapter.java`
      (JDBC, `@Repository`) plus
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/config/UseCaseConfig.java`
      (`@Configuration` bean-wiring `GetHorizon`, since the application module carries no Spring
      annotations per Constitution Principle I). Verified end-to-end against a live Postgres 16
      container: Flyway migrations, the `EXCLUDE`/`CHECK` constraints, and
      `GET /api/horizon` all behave as specified.
- [X] T019 [P] Implement the base frontend HTTP client wrapper in `frontend/src/api/client.js`
      and the Vue Router skeleton with a day-navigation route in `frontend/src/router/index.js`

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Build a day's schedule with time blocks (Priority: P1) 🎯 MVP

**Goal**: Create, edit, and delete `ROUTINE`/`CONSTRAINED` time blocks on a day within the
horizon, with overlap and horizon rules enforced, and view the day as a chronological timeline.

**Independent Test**: Open a day, add a routine block and a constrained-time block that don't
overlap, confirm both appear on the day's timeline, then attempt to add a third block that
overlaps one of them and confirm it is rejected (spec.md US1).

### Tests for User Story 1

- [ ] T020 [P] [US1] Parameterized domain unit tests for `OverlapPolicy` (adjacent-allowed,
      overlap-rejected, midnight-spanning two-day cases) in
      `backend/domain/src/test/java/alebuc/puzzleagenda/domain/service/OverlapPolicyTest.java`
      (FR-008, FR-014; spec Acceptance Scenarios 1–4)
- [ ] T021 [P] [US1] Application unit tests (mocked repositories) for `CreateTimeBlock`,
      `EditTimeBlock`, `DeleteTimeBlock` in
      `backend/application/src/test/java/alebuc/puzzleagenda/application/timeblock/TimeBlockUseCasesTest.java`
- [ ] T022 [P] [US1] Infrastructure contract tests for `POST /api/days/{date}/blocks`,
      `PUT /api/blocks/{id}`, `DELETE /api/blocks/{id}`, and the database `EXCLUDE` constraint,
      using Spring Boot Test + Testcontainers, in
      `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/TimeBlockControllerIT.java`
- [ ] T023 [P] [US1] Frontend component test for `DayTimeline` (chronological order, visible
      gaps, per-type styling) in `frontend/tests/DayTimeline.spec.js`

### Implementation for User Story 1

- [ ] T024 [US1] Implement the `TimeBlock` entity (start/end via `TimeRange`, derived `day`,
      type immutable after creation) in
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/timeblock/TimeBlock.java`
      (depends on T009, T010)
- [ ] T025 [US1] Implement the `OverlapPolicy` domain service (half-open interval intersection)
      in `backend/domain/src/main/java/alebuc/puzzleagenda/domain/service/OverlapPolicy.java`
      (depends on T024; research.md §1)
- [ ] T026 [US1] Implement `CreateTimeBlock` (validates horizon reachability via `HorizonState`,
      overlap via `OverlapPolicy`, establishes Day 1 on the first-ever placement) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/CreateTimeBlock.java`
      (depends on T012, T025; research.md §5)
- [ ] T027 [US1] Implement `EditTimeBlock` (start/end/name edit, same day, overlap re-check) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/EditTimeBlock.java`
      (depends on T025)
- [ ] T028 [US1] Implement `DeleteTimeBlock` (`ROUTINE`/`CONSTRAINED`: plain removal) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/DeleteTimeBlock.java`
      (depends on T024)
- [ ] T029 [US1] Implement `ViewDay` returning a day's blocks in chronological order (US4 wires
      in real materialization later; here it is a pass-through with reachability checks) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/day/ViewDay.java`
      (depends on T012, T013)
- [ ] T030 [US1] Implement the `TimeBlockRepository` persistence adapter, including mapping
      to/from the `span tsrange` column, in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/persistence/TimeBlockRepositoryAdapter.java`
      (depends on T006, T014)
- [ ] T031 [US1] Implement `DayController#getDay` (`GET /api/days/{date}`, 404
      `DAY_NOT_REACHABLE` / 422 `DAY_BEYOND_FORWARD_HORIZON`) in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/DayController.java`
      (depends on T029)
- [ ] T032 [US1] Implement `TimeBlockController` (`POST /api/days/{date}/blocks`,
      `PUT /api/blocks/{id}`, `DELETE /api/blocks/{id}`) in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/TimeBlockController.java`
      (depends on T026, T027, T028)
- [ ] T033 [P] [US1] Implement the `useDaySchedule(date)` composable calling the day/blocks API
      in `frontend/src/composables/useDaySchedule.js`
- [ ] T034 [P] [US1] Implement `TimeBlockCard` (visual distinction by `BlockType`) in
      `frontend/src/components/TimeBlockCard.vue`
- [ ] T035 [US1] Implement `DayTimeline` (chronological list, computed free-time gaps between
      blocks) in `frontend/src/components/DayTimeline.vue` (depends on T033, T034)
- [ ] T036 [US1] Implement `DayView` (day-to-day navigation bounded by the horizon, add/edit/
      delete block forms) in `frontend/src/views/DayView.vue` (depends on T035)

**Checkpoint**: User Story 1 is independently functional and testable.

---

## Phase 4: User Story 2 - Manage the activity backlog (Priority: P2)

**Goal**: Create, edit, and delete backlog activities (name, estimated duration, priority,
optional category) independent of scheduling.

**Independent Test**: Create an activity with a name, estimated duration, and priority,
confirm it appears in the unplanned backlog, then edit and delete it (spec.md US2).

### Tests for User Story 2

- [ ] T037 [P] [US2] Application unit tests for `CreateActivity`/`EditActivity`/`DeleteActivity`
      in `backend/application/src/test/java/alebuc/puzzleagenda/application/activity/ActivityUseCasesTest.java`
- [ ] T038 [P] [US2] Infrastructure contract tests for `/api/activities` endpoints
      (Spring Boot Test + Testcontainers) in
      `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/ActivityControllerIT.java`
- [ ] T039 [P] [US2] Frontend component test for backlog list rendering in
      `frontend/tests/BacklogView.spec.js`

### Implementation for User Story 2

- [ ] T040 [P] [US2] Implement the `Activity` entity (name, duration, priority, category;
      derived `UNPLANNED`/`PLANNED` status) in
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/activity/Activity.java`
      (depends on T011)
- [ ] T041 [US2] Implement `CreateActivity`, `EditActivity`, `DeleteActivity` (direct delete
      only while `UNPLANNED`, FR-004) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/activity/CreateActivity.java`,
      `.../EditActivity.java`, `.../DeleteActivity.java` (depends on T040)
- [ ] T042 [US2] Implement the `ActivityRepository` persistence adapter in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/persistence/ActivityRepositoryAdapter.java`
      (depends on T006, T014, T040)
- [ ] T043 [US2] Implement `ActivityController` (`GET`/`POST`/`PUT /api/activities`,
      `DELETE /api/activities/{id}` for the `UNPLANNED` case) in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/ActivityController.java`
      (depends on T041)
- [ ] T044 [P] [US2] Implement the `useBacklog()` composable in
      `frontend/src/composables/useBacklog.js`
- [ ] T045 [P] [US2] Implement `ActivityCard` in `frontend/src/components/ActivityCard.vue`
- [ ] T046 [US2] Implement `BacklogView` (create/edit/delete an unplanned activity) in
      `frontend/src/views/BacklogView.vue` (depends on T044, T045)

**Checkpoint**: User Stories 1 and 2 both independently functional.

---

## Phase 5: User Story 3 - Plan an activity into the schedule (Priority: P3)

**Goal**: Assign a backlog activity to a slot as a `PLANNED_ACTIVITY` block, move it, delete it
(returning the activity to the backlog), and require confirmation when deleting a planned
activity directly.

**Independent Test**: Place a backlog activity onto a free slot on a day, confirm it disappears
from the backlog and appears on the day's timeline, then delete the block and confirm the
activity returns to the backlog (spec.md US3).

### Tests for User Story 3

- [ ] T047 [P] [US3] Domain unit tests for `Activity` `UNPLANNED`/`PLANNED` transitions in
      `backend/domain/src/test/java/alebuc/puzzleagenda/domain/activity/ActivityTest.java`
- [ ] T048 [P] [US3] Application unit tests for planning/moving/deleting a `PLANNED_ACTIVITY`
      block, including the confirm-required delete flow (FR-005), in
      `backend/application/src/test/java/alebuc/puzzleagenda/application/timeblock/PlanActivityTest.java`
- [ ] T049 [P] [US3] Infrastructure contract tests for `POST /api/days/{date}/blocks` with
      `type=PLANNED_ACTIVITY`, `PATCH /api/blocks/{id}/move`, and
      `DELETE /api/activities/{id}?confirm=` in
      `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/PlanActivityControllerIT.java`

### Implementation for User Story 3

- [ ] T050 [US3] Extend `CreateTimeBlock` to require and validate an `UNPLANNED` `activityId`
      for `PLANNED_ACTIVITY` (409 `ACTIVITY_NOT_AVAILABLE`) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/CreateTimeBlock.java`
      (depends on T026, T040)
- [ ] T051 [US3] Implement `MoveTimeBlock` (reschedule a `PLANNED_ACTIVITY` block to a new
      day/slot, overlap + horizon re-check) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/MoveTimeBlock.java`
      (depends on T025, T012)
- [ ] T052 [US3] Extend `DeleteTimeBlock` to return the linked `Activity` to `UNPLANNED` when
      deleting a `PLANNED_ACTIVITY` block in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/DeleteTimeBlock.java`
      (depends on T028, T040)
- [ ] T053 [US3] Extend `DeleteActivity` to require `confirm=true` and cascade-delete the
      scheduled `TimeBlock` when the activity is `PLANNED` (FR-005) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/activity/DeleteActivity.java`
      (depends on T041, T028)
- [ ] T054 [US3] Add `PATCH /api/blocks/{id}/move` to `TimeBlockController` in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/TimeBlockController.java`
      (depends on T051)
- [ ] T055 [P] [US3] Add the "assign backlog activity to a slot" affordance to `DayView`/
      `TimeBlockCard` in `frontend/src/views/DayView.vue` and
      `frontend/src/components/TimeBlockCard.vue` (depends on T036, T046)
- [ ] T056 [P] [US3] Add the planned-activity confirm-delete UI flow to `BacklogView` in
      `frontend/src/views/BacklogView.vue` (depends on T046)

**Checkpoint**: User Stories 1–3 independently functional; activities flow backlog ↔ schedule.

---

## Phase 6: User Story 4 - Define a daily routine template (Priority: P4)

**Goal**: Define reusable routine template entries that pre-fill newly materialized days,
clipped against pre-existing blocks, without ever retroactively touching an already-materialized
day or being affected by later template edits.

**Independent Test**: Define a template entry (e.g., sleep 23:00–07:00), view a not-yet-visited
day within the horizon and confirm it is pre-filled with that routine block, edit that day's
copy, then confirm a later template change does not alter that already-materialized day
(spec.md US4).

### Tests for User Story 4

- [ ] T057 [P] [US4] Domain unit tests for `RoutineTemplateEntry` overlap validation using the
      two-day projection rule in
      `backend/domain/src/test/java/alebuc/puzzleagenda/domain/routine/RoutineTemplateEntryTest.java`
      (FR-016)
- [ ] T058 [P] [US4] Parameterized domain unit tests for `MaterializationService` clipping (full
      coverage → zero blocks, partial clip, split into two sub-intervals, spans midnight,
      spillover from/into an adjacent day) in
      `backend/domain/src/test/java/alebuc/puzzleagenda/domain/service/MaterializationServiceTest.java`
      (FR-017; spec Edge Cases worked examples)
- [ ] T059 [P] [US4] Infrastructure contract tests for routine-template CRUD and
      materialization-on-first-view via `GET /api/days/{date}` (Spring Boot Test +
      Testcontainers) in
      `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/RoutineTemplateControllerIT.java`

### Implementation for User Story 4

- [ ] T060 [US4] Implement the `RoutineTemplateEntry` entity with two-day-projection overlap
      validation in
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/routine/RoutineTemplateEntry.java`
      (depends on T009)
- [ ] T061 [US4] Implement `MaterializationService` (project each entry onto the target day,
      subtract intersecting existing blocks, emit one `ROUTINE` `TimeBlock` per maximal free
      sub-interval) in
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/service/MaterializationService.java`
      (depends on T024, T060; research.md §3)
- [ ] T062 [US4] Implement `CreateRoutineEntry`, `EditRoutineEntry`, `DeleteRoutineEntry` in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/routine/CreateRoutineEntry.java`,
      `.../EditRoutineEntry.java`, `.../DeleteRoutineEntry.java` (depends on T060)
- [ ] T063 [US4] Wire `ViewDay` to run `MaterializationService` against the current routine
      template and persist a `MaterializedDay` marker on first-ever access to a today-or-future
      day (idempotent; never applied to a past day) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/day/ViewDay.java`
      (depends on T029, T061; research.md §4)
- [ ] T064 [US4] Implement the `RoutineTemplateRepository` and `MaterializedDayRepository`
      persistence adapters in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/persistence/RoutineTemplateRepositoryAdapter.java`
      and `.../MaterializedDayRepositoryAdapter.java` (depends on T007, T008, T014)
- [ ] T065 [US4] Implement `RoutineTemplateController`
      (`GET`/`POST`/`PUT`/`DELETE /api/routine-template/entries`) in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/RoutineTemplateController.java`
      (depends on T062)
- [ ] T066 [P] [US4] Implement the `useRoutineTemplate()` composable in
      `frontend/src/composables/useRoutineTemplate.js`
- [ ] T067 [P] [US4] Implement `RoutineEntryForm` in
      `frontend/src/components/RoutineEntryForm.vue`
- [ ] T068 [US4] Implement `RoutineTemplateView` (create/edit/delete template entries) in
      `frontend/src/views/RoutineTemplateView.vue` (depends on T066, T067)

**Checkpoint**: All four user stories independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T069 [P] Run the full `quickstart.md` walkthrough end-to-end against a local backend and
      Testcontainers/PostgreSQL instance
- [ ] T070 [P] Document `OverlapPolicy` and `MaterializationService` invariants for future
      contributors in `backend/domain/README.md`
- [ ] T071 Audit every REST endpoint against contracts/api.md's Error Conventions table for
      status-code/`reason` consistency
- [ ] T072 [P] Keyboard-navigation/accessibility pass on `DayView`'s day-to-day navigation
      controls in `frontend/src/views/DayView.vue`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories.
- **User Stories (Phase 3–6)**: All depend on Foundational completion.
  - US1 (P1) has no dependency on the other stories.
  - US2 (P2) has no dependency on the other stories (its Activity entity is independent of
    TimeBlock).
  - US3 (P3) depends on both US1 (`TimeBlock`, `OverlapPolicy`, `CreateTimeBlock`/
    `DeleteTimeBlock`) and US2 (`Activity`) being implemented first — it wires the two together.
  - US4 (P4) depends on US1 (`TimeBlock`, `ViewDay`) being implemented first — it adds
    materialization on top of day-viewing.
- **Polish (Phase 7)**: Depends on all desired user stories being complete.

### Within Each User Story

- Tests are written first and MUST fail before implementation begins (Constitution Principle
  III).
- Domain entities/services before application use cases; use cases before REST controllers and
  persistence adapters; backend before the corresponding frontend composable/component/view.
- Story complete (checkpoint) before moving to the next priority.

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel.
- Foundational migration tasks T007/T008 (different files) can run in parallel with each other
  once T006 exists; T010/T011/T013/T015/T018/T019 marked [P] can also run in parallel.
- US2 can be implemented in parallel with US1 once Foundational is complete — they touch
  disjoint files (`activity/` vs. `timeblock/`) until US3 unifies them.
- US3 and US4 cannot start in parallel with each other's prerequisites but can be staffed in
  parallel once both US1 and US2 (for US3) or US1 alone (for US4) are done.
- All tests for a given story marked [P] can run in parallel with each other.

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Parameterized domain unit tests for OverlapPolicy in backend/domain/src/test/java/alebuc/puzzleagenda/domain/service/OverlapPolicyTest.java"
Task: "Application unit tests for CreateTimeBlock/EditTimeBlock/DeleteTimeBlock in backend/application/src/test/java/alebuc/puzzleagenda/application/timeblock/TimeBlockUseCasesTest.java"
Task: "Infrastructure contract tests for time-block endpoints in backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/TimeBlockControllerIT.java"
Task: "Frontend component test for DayTimeline in frontend/tests/DayTimeline.spec.js"

# Launch independent frontend pieces for User Story 1 together:
Task: "Implement useDaySchedule(date) composable in frontend/src/composables/useDaySchedule.js"
Task: "Implement TimeBlockCard component in frontend/src/components/TimeBlockCard.vue"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories).
3. Complete Phase 3: User Story 1.
4. **STOP and VALIDATE**: run the quickstart.md §2 walkthrough (overlap rejection, adjacency,
   forward-bound rejection) independently.
5. Deploy/demo if ready — a user can already see committed vs. free time for any day in the
   horizon.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. Add US1 → validate independently → MVP demo.
3. Add US2 → validate independently (backlog CRUD, no scheduling yet) → demo.
4. Add US3 → validate independently (backlog ↔ schedule round-trip, confirm-delete) → demo.
5. Add US4 → validate independently (template pre-fill, clipping, template-edit isolation) →
   demo.
6. Each story adds value without breaking the previous ones.

### Parallel Team Strategy

With multiple developers, once Foundational is done:

- Developer A: User Story 1.
- Developer B: User Story 2 (independent of US1 until US3 begins).
- Once both land: Developer A or B picks up User Story 3 (needs both US1 and US2).
- Developer C: User Story 4 (needs only US1).

---

## Notes

- [P] tasks touch different files with no unmet dependency.
- [Story] labels map every user-story-phase task to US1–US4 for traceability back to spec.md.
- Tests are mandatory per Constitution Principle III — write them first, confirm they fail, then
  implement.
- Commit after each task or logical group.
- Stop at any checkpoint to validate a story independently before moving on.
- Avoid: vague tasks, same-file conflicts inside a story, and cross-story dependencies beyond
  the ones explicitly called out above (US3 → US1+US2, US4 → US1).
