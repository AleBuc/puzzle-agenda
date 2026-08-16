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

- [X] T020 [P] [US1] Parameterized domain unit tests for `OverlapPolicy` (adjacent-allowed,
      overlap-rejected, midnight-spanning two-day cases) in
      `backend/domain/src/test/java/alebuc/puzzleagenda/domain/service/OverlapPolicyTest.java`
      (FR-008, FR-014; spec Acceptance Scenarios 1–4)
- [X] T021 [P] [US1] Application unit tests (mocked repositories) for `CreateTimeBlock`,
      `EditTimeBlock`, `DeleteTimeBlock` in
      `backend/application/src/test/java/alebuc/puzzleagenda/application/timeblock/TimeBlockUseCasesTest.java`
- [X] T022 [P] [US1] Infrastructure contract tests for `POST /api/days/{date}/blocks`,
      `PUT /api/blocks/{id}`, `DELETE /api/blocks/{id}`, and the database `EXCLUDE` constraint,
      using Spring Boot Test + Testcontainers, in
      `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/TimeBlockControllerIT.java`.
      **Gaps filled during implementation**: (1) `*IT.java` classes are Maven Failsafe's
      convention, not Surefire's — added the `maven-failsafe-plugin` to
      `backend/infrastructure/pom.xml` (bound to `integration-test`/`verify`) since nothing ran
      this file otherwise; (2) `TestRestTemplate` does not exist anywhere in Spring Boot 4.1's
      split modules — used `org.springframework.web.client.RestClient` (already on the classpath
      via `spring-web`) against `@LocalServerPort` instead; (3) status assertions compare the
      numeric code, not the `HttpStatus` enum constant — 422's reason phrase was renamed from
      `UNPROCESSABLE_ENTITY` to `UNPROCESSABLE_CONTENT` per RFC 9110, so enum-identity comparison
      is not a stable choice going forward. Also added
      `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/TestApplication.java`
      (a minimal `@SpringBootAppplication`) since infrastructure's tests have no access to the
      real one in `bootstrap` (reverse dependency direction).
- [X] T023 [P] [US1] Frontend component test for `DayTimeline` (chronological order, visible
      gaps, per-type styling) in `frontend/tests/DayTimeline.spec.js`

### Implementation for User Story 1

- [X] T024 [US1] Implement the `TimeBlock` entity (start/end via `TimeRange`, derived `day`,
      type immutable after creation) in
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/timeblock/TimeBlock.java`
      (depends on T009, T010). Per the deferral documented in `domain/port/package-info.java`
      (tasks.md T014), the `TimeBlockRepository` port is also defined here, alongside the entity:
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/port/TimeBlockRepository.java`.
- [X] T025 [US1] Implement the `OverlapPolicy` domain service (half-open interval intersection)
      in `backend/domain/src/main/java/alebuc/puzzleagenda/domain/service/OverlapPolicy.java`
      (depends on T024; research.md §1). Also added
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/exception/TimeBlockOverlapException.java`
      (409 `TIME_BLOCK_OVERLAP`) and
      `.../exception/TimeBlockNotFoundException.java` (404, used by T027/T028), plus a catch-all
      `IllegalArgumentException` → 400 `INVALID_REQUEST` handler in `ApiExceptionHandler` for
      `TimeBlock`'s own constructor invariant (e.g. a missing `activityId` on a
      `PLANNED_ACTIVITY` block) — none of these three are in contracts/api.md's six-case Error
      Conventions table, consistent with how plain field-validation failures elsewhere (e.g.
      `POST /api/activities`) aren't either.
- [X] T026 [US1] Implement `CreateTimeBlock` (validates horizon reachability via `HorizonState`,
      overlap via `OverlapPolicy`, establishes Day 1 on the first-ever placement) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/CreateTimeBlock.java`
      (depends on T012, T025; research.md §5)
- [X] T027 [US1] Implement `EditTimeBlock` (start/end/name edit, same day, overlap re-check) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/EditTimeBlock.java`
      (depends on T025). No horizon re-check: the block's day was already validated reachable at
      creation, and the forward bound only ever grows over time, so re-checking would be a
      structural no-op — consistent with contracts/api.md's `PUT /api/blocks/{id}` not
      documenting a horizon-related error for this endpoint.
- [X] T028 [US1] Implement `DeleteTimeBlock` (`ROUTINE`/`CONSTRAINED`: plain removal) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/DeleteTimeBlock.java`
      (depends on T024)
- [X] T029 [US1] Implement `ViewDay` returning a day's blocks in chronological order (US4 wires
      in real materialization later; here it is a pass-through with reachability checks) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/day/ViewDay.java`
      (depends on T012, T013). `materialized` is unconditionally `false`: nothing has ever
      actually run materialization yet, since `MaterializedDayRepository` has no adapter until
      T064/US4.
- [X] T030 [US1] Implement the `TimeBlockRepository` persistence adapter, including mapping
      to/from the `span tsrange` column, in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/persistence/TimeBlockRepositoryAdapter.java`
      (depends on T006, T014). Maps only the plain `start_at`/`end_at` columns — `span` is
      `GENERATED ALWAYS`, so it's queried (via `&&`, for `findIntersecting`) but never written.
- [X] T031 [US1] Implement `DayController#getDay` (`GET /api/days/{date}`, 404
      `DAY_NOT_REACHABLE` / 422 `DAY_BEYOND_FORWARD_HORIZON`) in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/DayController.java`
      (depends on T029)
- [X] T032 [US1] Implement `TimeBlockController` (`POST /api/days/{date}/blocks`,
      `PUT /api/blocks/{id}`, `DELETE /api/blocks/{id}`) in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/TimeBlockController.java`
      (depends on T026, T027, T028). Added a shared
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/TimeBlockResponse.java`
      DTO, reused by `DayController#getDay` — both endpoints render the same block shape.
- [X] T033 [P] [US1] Implement the `useDaySchedule(date)` composable calling the day/blocks API
      in `frontend/src/composables/useDaySchedule.js`
- [X] T034 [P] [US1] Implement `TimeBlockCard` (visual distinction by `BlockType`) in
      `frontend/src/components/TimeBlockCard.vue`
- [X] T035 [US1] Implement `DayTimeline` (chronological list, computed free-time gaps between
      blocks) in `frontend/src/components/DayTimeline.vue` (depends on T033, T034)
- [X] T036 [US1] Implement `DayView` (day-to-day navigation bounded by the horizon, add/edit/
      delete block forms) in `frontend/src/views/DayView.vue` (depends on T035).
      **Gap filled during implementation**: `frontend/vite.config.js` had no dev proxy, so the
      API client's same-origin `/api/...` calls would 404 against `vite dev` (port 5173) with no
      backend behind that path — added a `server.proxy` block forwarding `/api` to
      `http://localhost:8080`; verified with a live backend that the proxy round-trips real data.

**Checkpoint**: User Story 1 is independently functional and testable. Verified end-to-end
against a live PostgreSQL 16 container and a live backend process (not just the automated test
suites): create/adjacent/overlap/granularity/forward-horizon/before-Day-1, edit, delete, a
midnight-spanning block, and Day 1 establishment on first-ever placement all behave exactly per
spec.md and contracts/api.md. The frontend build and Vitest suite both pass and the dev server
was confirmed to serve the SPA and proxy `/api` correctly; the UI was **not** visually verified
in an actual browser — no browser-automation tool was available in this session.

---

## Phase 4: User Story 2 - Manage the activity backlog (Priority: P2)

**Goal**: Create, edit, and delete backlog activities (name, estimated duration, priority,
optional category) independent of scheduling.

**Independent Test**: Create an activity with a name, estimated duration, and priority,
confirm it appears in the unplanned backlog, then edit and delete it (spec.md US2).

### Tests for User Story 2

- [X] T037 [P] [US2] Application unit tests for `CreateActivity`/`EditActivity`/`DeleteActivity`
      in `backend/application/src/test/java/alebuc/puzzleagenda/application/activity/ActivityUseCasesTest.java`.
      Also covers `ListActivities` (see T041 note) and `DeleteActivity`'s confirm/cascade flow
      (T053's scope, built here — see that task's note).
- [X] T038 [P] [US2] Infrastructure contract tests for `/api/activities` endpoints
      (Spring Boot Test + Testcontainers) in
      `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/ActivityControllerIT.java`.
      **Gap hit while writing this**: AssertJ's `containsEntry` on a `Map<?, ?>`-typed variable
      fails to compile ("String cannot be converted to capture of ?") — wildcard capture doesn't
      let the compiler verify the key/value types. Declare the JSON-body variable as
      `Map<String, Object>` (matching what Jackson actually produces for `Map.class`), not
      `Map<?, ?>`.
- [X] T039 [P] [US2] Frontend component test for backlog list rendering in
      `frontend/tests/BacklogView.spec.js`

### Implementation for User Story 2

- [X] T040 [P] [US2] Implement the `Activity` entity (name, duration, priority, category;
      derived `UNPLANNED`/`PLANNED` status) in
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/activity/Activity.java`
      (depends on T011). `status` is never set by domain logic — `create()` defaults it to
      `UNPLANNED`; `reconstitute()` takes it as given, since only the repository (via a join
      against `time_block`) actually knows it. The `ActivityRepository` port is also defined
      here, alongside the entity, per the same deferral pattern as T024's `TimeBlockRepository`.
- [X] T041 [US2] Implement `CreateActivity`, `EditActivity`, `DeleteActivity` (direct delete
      only while `UNPLANNED`, FR-004) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/activity/CreateActivity.java`,
      `.../EditActivity.java`, `.../DeleteActivity.java` (depends on T040). **`DeleteActivity`
      was built directly to its final, T053-extended shape** (confirm check + cascade-delete the
      scheduled block), not staged behind a simpler UNPLANNED-only version — US2 and US3 were
      implemented in the same pass, so a temporarily-incomplete version (which would hit the
      `time_block.activity_id` foreign key constraint on delete instead of failing cleanly) would
      have served no purpose. Also added `ListActivities` — not a tasks.md-named use case, but a
      natural completion of the read side of `GET /api/activities` (T043 needs it; per
      Constitution Principle I this belongs in the application module, not the controller).
- [X] T042 [US2] Implement the `ActivityRepository` persistence adapter in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/persistence/ActivityRepositoryAdapter.java`
      (depends on T006, T014, T040). `status` is computed per row via an `EXISTS` subquery
      against `time_block` (never stored), matching the entity's design.
- [X] T043 [US2] Implement `ActivityController` (`GET`/`POST`/`PUT /api/activities`,
      `DELETE /api/activities/{id}` for the `UNPLANNED` case) in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/ActivityController.java`
      (depends on T041). `ApiExceptionHandler` gained `ActivityNotFoundException` → 404
      `ACTIVITY_NOT_FOUND`, `ActivityCurrentlyPlannedException` → 409
      `ACTIVITY_CURRENTLY_PLANNED`, `ActivityNotAvailableException` → 409
      `ACTIVITY_NOT_AVAILABLE`. `ACTIVITY_NOT_FOUND` isn't one of contracts/api.md's six named
      business-rule cases, consistent with `TIME_BLOCK_NOT_FOUND` (T025) — plain not-found,
      not a business rule.
- [X] T044 [P] [US2] Implement the `useBacklog()` composable in
      `frontend/src/composables/useBacklog.js`
- [X] T045 [P] [US2] Implement `ActivityCard` in `frontend/src/components/ActivityCard.vue`
- [X] T046 [US2] Implement `BacklogView` (create/edit/delete an unplanned activity) in
      `frontend/src/views/BacklogView.vue` (depends on T044, T045). **Built directly with T056's
      confirm-delete flow included** (same reasoning as `DeleteActivity`/T041). Also added a
      `/backlog` route to `frontend/src/router/index.js` and a minimal top nav to
      `frontend/src/App.vue` (Today / Backlog links) — a view needs to be reachable to be
      meaningfully "implemented".

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

- [X] T047 [P] [US3] Domain unit tests for `Activity` `UNPLANNED`/`PLANNED` transitions in
      `backend/domain/src/test/java/alebuc/puzzleagenda/domain/activity/ActivityTest.java`
- [X] T048 [P] [US3] Application unit tests for planning/moving/deleting a `PLANNED_ACTIVITY`
      block, including the confirm-required delete flow (FR-005), in
      `backend/application/src/test/java/alebuc/puzzleagenda/application/timeblock/PlanActivityTest.java`.
      The confirm-required delete flow is exercised in `ActivityUseCasesTest` (T037) instead —
      not duplicated here; this file covers `CreateTimeBlock`'s `ACTIVITY_NOT_AVAILABLE` checks
      and `MoveTimeBlock`, which are this story's actually-new application logic.
- [X] T049 [P] [US3] Infrastructure contract tests for `POST /api/days/{date}/blocks` with
      `type=PLANNED_ACTIVITY`, `PATCH /api/blocks/{id}/move`, and
      `DELETE /api/activities/{id}?confirm=` in
      `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/PlanActivityControllerIT.java`.
      **Gap hit while writing this**: `GET /api/activities` returns a JSON *array*, not an
      object — the shared `Map`-typed response helper used for every other endpoint in this file
      can't deserialize it (Jackson can't map an array into a `Map`). Worked around by asserting
      via a direct SQL query (already needed elsewhere in the file) rather than adding a
      second array-typed response helper for a single call site.

### Implementation for User Story 3

- [X] T050 [US3] Extend `CreateTimeBlock` to require and validate an `UNPLANNED` `activityId`
      for `PLANNED_ACTIVITY` (409 `ACTIVITY_NOT_AVAILABLE`) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/CreateTimeBlock.java`
      (depends on T026, T040)
- [X] T051 [US3] Implement `MoveTimeBlock` (reschedule a `PLANNED_ACTIVITY` block to a new
      day/slot, overlap + horizon re-check) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/MoveTimeBlock.java`
      (depends on T025, T012)
- [X] T052 [US3] Extend `DeleteTimeBlock` to return the linked `Activity` to `UNPLANNED` when
      deleting a `PLANNED_ACTIVITY` block in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/DeleteTimeBlock.java`
      (depends on T028, T040). **No code change was needed.** `Activity.status` is never stored
      (T040/T042) — it's computed live via an `EXISTS` subquery against `time_block` on every
      read. Deleting the `TimeBlock` row is therefore *itself* sufficient for the activity to
      report `UNPLANNED` on the next read; there is no second place that needs updating.
      Verified live against a real Postgres instance: created an activity, planned it, deleted
      the block directly (not the activity), and it reappeared in the unplanned backlog with no
      application-layer code touching `Activity` at all.
- [X] T053 [US3] Extend `DeleteActivity` to require `confirm=true` and cascade-delete the
      scheduled `TimeBlock` when the activity is `PLANNED` (FR-005) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/activity/DeleteActivity.java`
      (depends on T041, T028). Built together with T041 (see that task's note) rather than as a
      separate later edit. Required adding `TimeBlockRepository.findByActivityId(UUID)` (and its
      JDBC implementation) — not in any prior task, needed so `DeleteActivity` can find the
      specific block to cascade-delete.
- [X] T054 [US3] Add `PATCH /api/blocks/{id}/move` to `TimeBlockController` in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/TimeBlockController.java`
      (depends on T051)
- [X] T055 [P] [US3] Add the "assign backlog activity to a slot" affordance to `DayView`/
      `TimeBlockCard` in `frontend/src/views/DayView.vue` and
      `frontend/src/components/TimeBlockCard.vue` (depends on T036, T046). **Contract extension
      found and fixed while wiring this up**: data-model.md says a `PLANNED_ACTIVITY` block's
      display label is its linked Activity's name (its own `name` field is "unused" for that
      type), but contracts/api.md's response shape never actually carried that information — the
      frontend had no way to render a meaningful label. Added `activityName` (populated only for
      `PLANNED_ACTIVITY` blocks) to `TimeBlockResponse` and documented it in contracts/api.md.
      Introduced a shared `TimeBlockResponseAssembler` component (used by both `DayController`
      and `TimeBlockController`) so the enrichment lookup isn't duplicated. Scope was kept to the
      "plan an activity" form control only — no drag-and-drop/move UI was built, since nothing in
      tasks.md's frontend tasks calls for one; `MoveTimeBlock`'s endpoint (T054) is ready for a
      future UI iteration.
- [X] T056 [P] [US3] Add the planned-activity confirm-delete UI flow to `BacklogView` in
      `frontend/src/views/BacklogView.vue` (depends on T046). Built together with T046 (see that
      task's note) rather than as a separate later edit.

**Checkpoint**: User Stories 1–3 independently functional; activities flow backlog ↔ schedule.
Verified end-to-end against a live PostgreSQL 16 container and a live backend process, beyond
the automated suites (56 backend unit tests + 24 Testcontainers-backed integration tests, all
passing; frontend: 10 Vitest tests, `npm run build` green): create/edit/delete an activity;
plan it into a slot (disappears from the unplanned backlog, `activityName` populated); reject
re-planning an already-planned or nonexistent activity (409 `ACTIVITY_NOT_AVAILABLE`); move a
planned-activity block to a new day (rejects moving a non-`PLANNED_ACTIVITY` block, 400); reject
deleting a planned activity without `confirm=true` (409 `ACTIVITY_CURRENTLY_PLANNED`) and confirm
that `confirm=true` cascades to delete its scheduled block; and confirm that deleting the block
directly (not the activity) returns it to the backlog with zero code dedicated to that effect
(T052).

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

- [X] T057 [P] [US4] Domain unit tests for `RoutineTemplateEntry` overlap validation using the
      two-day projection rule in
      `backend/domain/src/test/java/alebuc/puzzleagenda/domain/routine/RoutineTemplateEntryTest.java`
      (FR-016). **Correctness issue found and fixed while writing this**: naively projecting both
      entries onto the *same* reference day and comparing does not reproduce FR-016's own worked
      example (sleep 23:00-07:00 vs. an entry from 06:30-07:00) — projected onto the same day,
      sleep's tail lands on day D+1 while a same-day 06:30-07:00 entry lands on day D's morning;
      they'd never overlap under that scheme. The correct model: every entry recurs daily, so
      checking day offsets `{-1, 0, +1}` between the two entries' projections is what actually
      reproduces the example (see `RoutineTemplateEntry.conflictsWith`'s Javadoc for the full
      reasoning). Tests assert `conflictsWith` directly rather than raw same-day projection.
- [X] T058 [P] [US4] Parameterized domain unit tests for `MaterializationService` clipping (full
      coverage → zero blocks, partial clip, split into two sub-intervals, spans midnight,
      spillover from/into an adjacent day) in
      `backend/domain/src/test/java/alebuc/puzzleagenda/domain/service/MaterializationServiceTest.java`
      (FR-017; spec Edge Cases worked examples). **Documented ambiguity resolution**: spec.md's
      two sleep-23:00-07:00 worked examples are inconsistent if read fully literally — the
      02:00-03:00 example explicitly produces two blocks, but the 06:00-06:30-jog example's
      prose ("clipped to 23:00-06:00, stopping where the jog starts") doesn't mention the
      06:30-07:00 remainder. Since FR-017's general rule and research.md §3 both mandate
      producing one block per maximal free sub-interval, and the jog example doesn't contradict
      that when read as a partial/leading-clip illustration rather than a complete-output spec,
      this implementation produces the trailing remainder too — see
      `MaterializationService`'s class-level Javadoc for the full note. Verified live against
      quickstart.md's own worked example.
- [X] T059 [P] [US4] Infrastructure contract tests for routine-template CRUD and
      materialization-on-first-view via `GET /api/days/{date}` (Spring Boot Test +
      Testcontainers) in
      `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/RoutineTemplateControllerIT.java`.
      **Gaps hit while writing this**: (1) same `Map<?, ?>` → `Map<String, Object>` wildcard-
      capture fix as T038; (2) testing "a past day is never materialized" needs a controllable
      clock — Day 1 always becomes *today at the moment of establishment*, never the day
      targeted, so there is no way to make a genuinely past day reachable without simulating time
      actually passing. Added a mutable test `Clock` (`@Primary`, distinct bean name from
      `UseCaseConfig.clock()` to avoid a name collision) that the test advances mid-run. It also
      needed an explicit `@Import(...)` on the test class — the nested `@TestConfiguration` was
      not auto-detected, apparently because `@SpringBootTest(classes = TestApplication.class)`
      already specifies configuration classes explicitly.

### Implementation for User Story 4

- [X] T060 [US4] Implement the `RoutineTemplateEntry` entity with two-day-projection overlap
      validation in
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/routine/RoutineTemplateEntry.java`
      (depends on T009). The overlap check is `RoutineTemplateEntry.conflictsWith(other)` (see
      T057's note for why same-day projection alone is wrong). The `RoutineTemplateRepository`
      port is also defined here, alongside the entity, per the same deferral pattern as
      T024/T040 — this was the last of the five ports data-model.md calls for; see the updated
      `domain/port/package-info.java`.
- [X] T061 [US4] Implement `MaterializationService` (project each entry onto the target day,
      subtract intersecting existing blocks, emit one `ROUTINE` `TimeBlock` per maximal free
      sub-interval) in
      `backend/domain/src/main/java/alebuc/puzzleagenda/domain/service/MaterializationService.java`
      (depends on T024, T060; research.md §3). Pure/stateless, no repository dependency — the
      caller (`ViewDay`, T063) supplies both the template entries and a pre-fetched list of
      candidate blocks, keeping this service unit-testable with plain constructed `TimeBlock`s
      (matches T058's "parameterized domain unit tests" framing).
- [X] T062 [US4] Implement `CreateRoutineEntry`, `EditRoutineEntry`, `DeleteRoutineEntry` in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/routine/CreateRoutineEntry.java`,
      `.../EditRoutineEntry.java`, `.../DeleteRoutineEntry.java` (depends on T060)
- [X] T063 [US4] Wire `ViewDay` to run `MaterializationService` against the current routine
      template and persist a `MaterializedDay` marker on first-ever access to a today-or-future
      day (idempotent; never applied to a past day) in
      `backend/application/src/main/java/alebuc/puzzleagenda/application/day/ViewDay.java`
      (depends on T029, T061; research.md §4). Replaces T029/US1's hardcoded `materialized =
      false`. Candidate blocks for clipping are fetched from a single `[day-1, day+2)` window
      (research.md §3: a block can sit on the materialized day, spill into it from the previous
      day, or already sit on the following day — no single entry's projected span reaches
      further than that).
- [X] T064 [US4] Implement the `RoutineTemplateRepository` and `MaterializedDayRepository`
      persistence adapters in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/persistence/RoutineTemplateRepositoryAdapter.java`
      and `.../MaterializedDayRepositoryAdapter.java` (depends on T007, T008, T014). The latter
      was the one deferred, uncalled port implementation flagged back in T018's note — `ViewDay`
      finally calls it now.
- [X] T065 [US4] Implement `RoutineTemplateController`
      (`GET`/`POST`/`PUT`/`DELETE /api/routine-template/entries`) in
      `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/RoutineTemplateController.java`
      (depends on T062)
- [X] T066 [P] [US4] Implement the `useRoutineTemplate()` composable in
      `frontend/src/composables/useRoutineTemplate.js`
- [X] T067 [P] [US4] Implement `RoutineEntryForm` in
      `frontend/src/components/RoutineEntryForm.vue`
- [X] T068 [US4] Implement `RoutineTemplateView` (create/edit/delete template entries) in
      `frontend/src/views/RoutineTemplateView.vue` (depends on T066, T067). Also added a
      `/routine-template` route to `frontend/src/router/index.js` and a nav link in
      `frontend/src/App.vue`, same reasoning as T046/US2's nav addition.

**Checkpoint**: All four user stories independently functional. Verified end-to-end against a
live PostgreSQL 16 container and a live backend process, beyond the automated suites (65 backend
unit tests + 36 Testcontainers-backed integration tests, all passing; frontend: 10 Vitest tests,
`npm run build` green): created a midnight-spanning Sleep template entry, confirmed a
not-yet-visited day materializes it (`endsNextDay: true`), confirmed a template edit afterward
does not alter that already-materialized day (FR-019), confirmed materialization clips against a
pre-existing block on the following day producing exactly the leading-clip-plus-trailing-
remainder split described in T058's note, and confirmed an overlapping template entry is
rejected with 409 `TEMPLATE_ENTRY_OVERLAP` — this exactly replays quickstart.md's own §4/§5
walkthrough.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T069 [P] Run the full `quickstart.md` walkthrough end-to-end against a local backend and
      Testcontainers/PostgreSQL instance. Ran it live, section by section, against a fresh
      PostgreSQL 16 container and the packaged jar (not Testcontainers here specifically — that's
      what the infrastructure IT suite already exercises; this task's value is running the
      *documented walkthrough itself*, verbatim, as a human/CI operator would). Every expected
      result in every one of the six sections matched exactly, including §5's clipping example.
- [X] T070 [P] Document `OverlapPolicy` and `MaterializationService` invariants for future
      contributors in `backend/domain/README.md`. Didn't exist before this task — created new.
- [X] T071 Audit every REST endpoint against contracts/api.md's Error Conventions table for
      status-code/`reason` consistency. **Found and fixed six real doc/implementation mismatches**
      (each verified live before and after the fix, not just inferred from reading code):
      1. `POST /api/days/{date}/blocks`'s 400 case wrongly attributed a missing `activityId` on a
         `PLANNED_ACTIVITY` block to `INVALID_TIME_GRANULARITY` — live testing showed it's
         actually `409 ACTIVITY_NOT_AVAILABLE` (the activity-lookup runs before any structural
         null check would). The real 400 case is the *reverse*: an `activityId` present on a
         non-`PLANNED_ACTIVITY` block. Also improved `CreateTimeBlock`'s error message for this
         case (was "Activity null is not available...", now "activityId is required...") and
         added a regression test in `PlanActivityTest`.
      2. `GET /api/activities`'s `400 INVALID_REQUEST` for an invalid `status` value was
         undocumented entirely.
      3. `PUT /api/activities/{id}`'s `400` validation-failure case (blank name, non-positive
         duration) was undocumented — the doc only mentioned 404.
      4. `PATCH /api/blocks/{id}/move`'s `400 INVALID_TIME_GRANULARITY` case was undocumented —
         the doc only mentioned the not-`PLANNED_ACTIVITY` 400 case.
      5. `POST`/`PUT /api/routine-template/entries`'s 400 case had the same wrong-attribution bug
         as (1): blank name is `INVALID_REQUEST`, not `INVALID_TIME_GRANULARITY`.
      6. Every plain "id doesn't exist" 404 across the whole document was missing its actual
         `reason` string (`ACTIVITY_NOT_FOUND`, `TIME_BLOCK_NOT_FOUND`,
         `ROUTINE_TEMPLATE_ENTRY_NOT_FOUND`) — added throughout for consistency, plus a note in
         the Error Conventions intro explaining why more reason strings appear in the document
         than the six-row table lists.
- [X] T072 [P] Keyboard-navigation/accessibility pass on `DayView`'s day-to-day navigation
      controls in `frontend/src/views/DayView.vue`. Added: descriptive `aria-label`s naming the
      actual target date on both nav buttons; `role="navigation"` + `aria-label` on the header;
      `aria-live="polite"` on the date heading so screen readers announce day changes; Left/Right
      arrow-key navigation (bounded by the same horizon check as the buttons), guarded to not
      fire while focus is inside a form control (`INPUT`/`SELECT`/`TEXTAREA`) so it doesn't hijack
      normal use of the add/edit-block form on the same page.

**Checkpoint**: The feature spec (spec.md) is fully implemented across all four user stories,
with contracts/api.md now accurately reflecting real backend behavior end to end. Final combined
test count: 66 backend unit tests, 36 Testcontainers-backed integration tests, 10 frontend Vitest
tests — all passing — plus the full quickstart.md walkthrough verified live against a real
PostgreSQL instance.

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
