---
description: "Task list for Multi-Block, Multi-Day Activity Planning (002-multi-day-activity-planning)"
---

# Tasks: Multi-Block, Multi-Day Activity Planning

**Input**: Design documents from `/specs/002-multi-day-activity-planning/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api.md, quickstart.md, `.specify/memory/constitution.md`

**Tests**: Included and mandatory (not optional) — Constitution Principle III (Test-Backed Development) requires every feature to ship with tests before merge: JUnit 5 + AssertJ + Mockito (no Spring context) for domain/application, `@ParameterizedTest` for merge/status/midnight-boundary cases, Spring Boot Test + Testcontainers (PostgreSQL) for infrastructure, Vitest + Vue Test Utils for frontend.

**Organization**: Tasks are grouped by user story (spec.md, priorities P1–P4) to enable independent implementation and testing of each story. This feature extends feature 001's implementation in place — no new module, dependency, or top-level directory is introduced (plan.md Technical Context).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps a task to its user story (US1–US4) for traceability
- Every task lists an exact file path

## Path Conventions

Web application monorepo (plan.md Structure Decision, unchanged from feature 001):
`backend/{domain,application,infrastructure,bootstrap}/src/{main,test}/java/alebuc/puzzleagenda/...`,
`frontend/src/`, `frontend/tests/`.

---

## Phase 1: Setup

**Purpose**: Project initialization and basic structure.

**Not applicable**: this feature introduces no new dependency, module, or environment configuration (plan.md Technical Context) — it extends existing files under feature 001's already-initialized stack. Proceed directly to Phase 2.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Remove feature 001's "at most one `PLANNED_ACTIVITY` block per activity" model and put the shared building blocks (merge, per-day status, repository shape) in place. Every user story below depends on this phase.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T001 Create Flyway migration dropping `time_block_activity_id_unique` in `backend/infrastructure/src/main/resources/db/migration/V4__drop_time_block_activity_unique.sql` (data-model.md Migration)
- [X] T002 Remove the `status` field, `ActivityStatus` import/usage, and `isPlanned()` method from `Activity` (adjust `create`/`reconstitute` signatures accordingly) in `backend/domain/src/main/java/alebuc/puzzleagenda/domain/activity/Activity.java`, and delete `backend/domain/src/main/java/alebuc/puzzleagenda/domain/activity/ActivityStatus.java` (data-model.md Activity)
- [X] T003 [P] Update `ActivityTest.java` to drop status-based assertions and cover the new `create`/`reconstitute` signatures in `backend/domain/src/test/java/alebuc/puzzleagenda/domain/activity/ActivityTest.java` (depends on T002)
- [X] T004 [P] Add a `PLANNED_ACTIVITY`-only midnight-confinement invariant to `TimeBlock.create` and `TimeBlock.withRangeAndName` (reject when `type == PLANNED_ACTIVITY && range.spansMidnight()`; `ROUTINE`/`CONSTRAINED` unaffected) in `backend/domain/src/main/java/alebuc/puzzleagenda/domain/timeblock/TimeBlock.java` (research.md §4, FR-021)
- [X] T005 [P] Create `PlannedActivitySpansMidnightException` in `backend/domain/src/main/java/alebuc/puzzleagenda/domain/exception/PlannedActivitySpansMidnightException.java`
- [X] T006 [P] Create `backend/domain/src/test/java/alebuc/puzzleagenda/domain/timeblock/TimeBlockTest.java` with `@ParameterizedTest` coverage for the new midnight-confinement invariant (rejects `PLANNED_ACTIVITY` spanning midnight; still accepts `ROUTINE`/`CONSTRAINED` spanning midnight; existing `activityId`-required-iff-`PLANNED_ACTIVITY` invariant) (depends on T004, T005)
- [X] T007 [P] Contract test: `POST /api/days/{date}/blocks` and `PUT /api/blocks/{id}` with `type=PLANNED_ACTIVITY` and an `endTime <= startTime` (midnight-spanning) request return `400 PLANNED_ACTIVITY_SPANS_MIDNIGHT`, while the identical time values for `type=ROUTINE` still succeed, in `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/PlanActivityControllerIT.java` (depends on T004, T005)
- [X] T008 [P] Add `touchesOrOverlaps(TimeRange, TimeRange)` (overlap-or-shared-boundary predicate, using `<=` instead of `overlaps()`'s `<`) to `OverlapPolicy` in `backend/domain/src/main/java/alebuc/puzzleagenda/domain/service/OverlapPolicy.java` (research.md §2)
- [X] T009 [P] Add `@ParameterizedTest` cases for `touchesOrOverlaps` (disjoint, adjacent/touching, overlapping) to `backend/domain/src/test/java/alebuc/puzzleagenda/domain/service/OverlapPolicyTest.java` (depends on T008)
- [X] T010 Implement `FragmentMerger` domain service — pure, stateless, mirroring `MaterializationService`'s shape — with a method merging a candidate `TimeRange` against a list of same-activity/same-day `TimeBlock`s into a single union range plus the subset absorbed, iterating with `touchesOrOverlaps` until a full pass absorbs nothing more (transitive/chain merge, FR-006) in `backend/domain/src/main/java/alebuc/puzzleagenda/domain/service/FragmentMerger.java` (research.md §2, FR-005–FR-007) (depends on T008)
- [X] T011 [P] Create `backend/domain/src/test/java/alebuc/puzzleagenda/domain/service/FragmentMergerTest.java` with `@ParameterizedTest` coverage: disjoint fragments (no merge), adjacent pair, overlapping pair, three-way transitive chain, identical-range idempotent merge (depends on T010)
- [X] T012 [P] Implement `DayPlanningStatus` enum (`UNPLANNED`, `PARTIALLY_PLANNED`, `PLANNED`) with a static `of(int estimatedDurationMinutes, int plannedMinutes)` factory in `backend/domain/src/main/java/alebuc/puzzleagenda/domain/activity/DayPlanningStatus.java` (data-model.md DayPlanningStatus, FR-009)
- [X] T013 [P] Implement `DayPlanning` record (`plannedMinutes`, `remainingMinutes` floored at zero for display, `status`) with a factory computing all three from an `estimatedDurationMinutes` and a list of fragment `TimeRange`s in `backend/domain/src/main/java/alebuc/puzzleagenda/domain/activity/DayPlanning.java` (data-model.md DayPlanningStatus, spec.md Assumptions) (depends on T012)
- [X] T014 [P] Create `backend/domain/src/test/java/alebuc/puzzleagenda/domain/activity/DayPlanningTest.java` with `@ParameterizedTest` boundary coverage: zero planned (`UNPLANNED`), partial (`PARTIALLY_PLANNED`), exactly at quota and over quota (both `PLANNED`, remaining floored at `0`) (depends on T013)
- [X] T015 Change `TimeBlockRepository.findByActivityId` to return `List<TimeBlock>` (was `Optional<TimeBlock>`) and add `findByActivityIdAndDay(UUID activityId, LocalDate day): List<TimeBlock>` in `backend/domain/src/main/java/alebuc/puzzleagenda/domain/port/TimeBlockRepository.java` (data-model.md TimeBlock)
- [X] T016 Update `TimeBlockRepositoryAdapter` for the new `findByActivityId` return type and the new `findByActivityIdAndDay` query in `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/persistence/TimeBlockRepositoryAdapter.java` (depends on T015)
- [X] T017 Update `ActivityRepositoryAdapter` to stop hydrating a `status` column/join now that `Activity` has none (depends on T002) in `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/persistence/ActivityRepositoryAdapter.java`
- [X] T018 Update `CreateTimeBlock` to replace `requireUnplannedActivity` with an existence-only check for `PLANNED_ACTIVITY` blocks — still `409 ACTIVITY_NOT_AVAILABLE` for a missing/nonexistent `activityId`, no longer for one that already has fragments (FR-001) in `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/CreateTimeBlock.java` (depends on T002)
- [X] T019 Rename `ActivityCurrentlyPlannedException` to `ActivityHasPlannedFragmentsException` (`backend/domain/src/main/java/alebuc/puzzleagenda/domain/exception/ActivityHasPlannedFragmentsException.java`) and update `DeleteActivity` to trigger the confirmation requirement from `timeBlockRepository.findByActivityId(id)` being non-empty (not a global status), cascading deletion of every returned fragment on confirm, with the exception message stating the exact total fragment count (FR-016) in `backend/application/src/main/java/alebuc/puzzleagenda/application/activity/DeleteActivity.java` (depends on T002, T015)
- [X] T020 Update `ApiExceptionHandler`: rename the handler for `ActivityHasPlannedFragmentsException` → `409 ACTIVITY_HAS_PLANNED_FRAGMENTS`, and add a handler for `PlannedActivitySpansMidnightException` → `400 PLANNED_ACTIVITY_SPANS_MIDNIGHT` in `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/ApiExceptionHandler.java` (depends on T005, T019)
- [X] T021 Update `ListActivities` and `ActivityController`/`ActivityResponse` to drop the removed `status` field and `?status=` query filter from the base (no `day` param) response shape (contracts/api.md `GET /api/activities` base shape) in `backend/application/src/main/java/alebuc/puzzleagenda/application/activity/ListActivities.java` and `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/ActivityController.java` (depends on T002)
- [X] T022 [P] Update `ActivityUseCasesTest.java` and `ActivityControllerIT.java` for the removed `status` field/filter and the renamed, re-triggered delete-confirmation exception in `backend/application/src/test/java/alebuc/puzzleagenda/application/activity/ActivityUseCasesTest.java` and `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/ActivityControllerIT.java` (depends on T018, T019, T020, T021)
- [X] T023 [P] Update `TimeBlockUseCasesTest.java` and `PlanActivityTest.java` for the relaxed `CreateTimeBlock` activity-availability rule (creating a second fragment for an already-fragmented activity now succeeds) in `backend/application/src/test/java/alebuc/puzzleagenda/application/timeblock/TimeBlockUseCasesTest.java` and `backend/application/src/test/java/alebuc/puzzleagenda/application/timeblock/PlanActivityTest.java` (depends on T018)

**Checkpoint**: The old one-fragment-per-activity model is fully removed and every existing test that assumed it is updated to the new one; user story implementation can now begin.

---

## Phase 3: User Story 1 - Plan an activity across multiple days (Priority: P1) 🎯 MVP

**Goal**: A backlog activity can have independent `PLANNED_ACTIVITY` fragments on several different days, each day's remaining time/status computed only from that day's own fragments, with over-quota fragments always accepted.

**Independent Test**: Plan a 2h fragment of a 5h activity on day D and a 2h fragment on day D+2; confirm both exist, each day shows 3h remaining independently, and an extra fragment on an already-fully-planned day is still accepted.

### Tests for User Story 1

- [X] T024 [P] [US1] Contract test: `POST /api/days/{date}/blocks` accepts a `PLANNED_ACTIVITY` fragment for an activity that already has a fragment on a *different* day (no `ACTIVITY_NOT_AVAILABLE`) in `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/PlanActivityControllerIT.java`
- [X] T025 [P] [US1] Contract test: `GET /api/activities?day=YYYY-MM-DD` returns `remainingMinutesForDay`/`dayStatus` per activity, and the same activity queried for two different days returns independent values in `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/ActivityControllerIT.java`
- [X] T026 [P] [US1] Integration test: planning fragments on day D and day D+2 for the same activity keeps each day's remaining time/status independent; an extra fragment pushing day D over its estimated duration is accepted, not rejected, in `backend/application/src/test/java/alebuc/puzzleagenda/application/timeblock/PlanActivityTest.java`

### Implementation for User Story 1

- [X] T027 [US1] Implement a day-scoped planning computation (using `DayPlanning` + `findByActivityIdAndDay`) and add an optional `LocalDate day` overload to `ListActivities` returning `remainingMinutesForDay`/`dayStatus` per activity in `backend/application/src/main/java/alebuc/puzzleagenda/application/activity/ListActivities.java` (depends on T012, T013, T015)
- [X] T028 [US1] Extend `ActivityController.getActivities`/`ActivityResponse` to accept `?day=` and include `remainingMinutesForDay`/`dayStatus` when present, `400 INVALID_REQUEST` for a malformed date, in `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/ActivityController.java` (depends on T027)
- [X] T029 [P] [US1] Extend `useBacklog.js`'s `load` to accept an optional day and pass it through as `?day=` in `frontend/src/composables/useBacklog.js`
- [X] T030 [US1] Update `DayView.vue`'s activity selector to source options from the day-scoped activity list, display each activity's remaining time for the displayed day, and keep fully-planned activities selectable with a distinct visual marker (FR-011) in `frontend/src/views/DayView.vue` (depends on T028, T029)
- [X] T031 [P] [US1] Add/extend a frontend test covering the day-scoped selector's remaining-time display and fully-planned marker in `frontend/tests/DayView.spec.js` (create if it does not already exist) (depends on T030)

**Checkpoint**: An activity can be planned across multiple days independently, with correct per-day remaining time and status visible in the day view. This is the MVP slice.

---

## Phase 4: User Story 2 - Split an activity into multiple fragments within one day, with automatic merging (Priority: P2)

**Goal**: Fragments of the same activity on the same day that touch or overlap (via create, edit, or move) merge atomically into one block covering their union; overlap with any other activity's or type's block is still rejected.

**Independent Test**: Create two non-touching fragments of the same activity on the same day; create/edit a third so it touches or overlaps one or both; confirm they merge into a single block while an unrelated fragment is untouched, and that overlapping a different activity's/type's block is still rejected.

### Tests for User Story 2

- [X] T032 [P] [US2] Contract test: `POST /api/days/{date}/blocks` with a range adjacent to or overlapping an existing same-activity, same-day fragment returns `201` with the merged block (not `TIME_BLOCK_OVERLAP`) in `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/PlanActivityControllerIT.java`
- [X] T033 [P] [US2] Contract test: `PUT /api/blocks/{id}` edited so it touches/overlaps another same-activity, same-day fragment merges them; editing a fragment positioned between two others merges all three in one call in `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/TimeBlockControllerIT.java`
- [X] T034 [P] [US2] Regression contract test: a create/edit/move that would overlap a *different* activity's fragment, or a `ROUTINE`/`CONSTRAINED` block, still returns `409 TIME_BLOCK_OVERLAP` in `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/TimeBlockControllerIT.java`

### Implementation for User Story 2

- [X] T035 [US2] Wire `FragmentMerger` into `CreateTimeBlock.execute`: after the existing hard-conflict check against other activities'/types' blocks, fetch `findByActivityIdAndDay` for the candidate's activity and day, merge, delete every absorbed fragment, and save the single merged block in `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/CreateTimeBlock.java` (depends on T010, T015, T018)
- [X] T036 [US2] Wire the same merge step into `EditTimeBlock.execute`, excluding the block's own current row from the merge candidates, in `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/EditTimeBlock.java` (depends on T010, T015)
- [X] T037 [US2] Wire the same merge step into `MoveTimeBlock.execute`, scoped to the destination day only and excluding the block's own current row (relevant when the destination day equals the origin day), in `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/MoveTimeBlock.java` (depends on T010, T015)
- [X] T038 [P] [US2] Extend `PlanActivityTest.java`/`TimeBlockUseCasesTest.java` with application-level merge scenarios: pairwise, three-way transitive via edit, and cross-day move merging only against the destination day's fragments in `backend/application/src/test/java/alebuc/puzzleagenda/application/timeblock/PlanActivityTest.java` and `backend/application/src/test/java/alebuc/puzzleagenda/application/timeblock/TimeBlockUseCasesTest.java` (depends on T035, T036, T037)

**Checkpoint**: Same-activity, same-day fragments merge correctly on create, edit, and move; overlap with any other block is still rejected exactly as before.

---

## Phase 5: User Story 3 - See per-day and aggregate planning progress (Priority: P3)

**Goal**: The backlog shows, per activity, the total fragment count and which reachable days have fragments (with per-day planned time), independent of the day-view selector already built in User Story 1.

**Independent Test**: With fragments spread across 3 days for one activity, confirm the backlog lists it as planned on 3 days with an accurate per-day breakdown, while each day's own selector (User Story 1) still reflects only that day's fragments.

### Tests for User Story 3

- [X] T039 [P] [US3] Contract test: `GET /api/activities` (no `day`) returns `totalFragmentCount`, `plannedDayCount`, and a sparse `days[]` breakdown (only days with ≥1 fragment) per activity in `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/ActivityControllerIT.java`
- [X] T040 [P] [US3] Integration test: an activity with fragments on 3 different reachable days reports the correct `totalFragmentCount`/`plannedDayCount`/`days[]` in the aggregate view, independent of any single day's own status, in `backend/application/src/test/java/alebuc/puzzleagenda/application/activity/ActivityUseCasesTest.java`

### Implementation for User Story 3

- [X] T041 [US3] Implement the `ActivityPlanningSummary` computation (group an activity's `findByActivityId` fragments by day; compute `totalFragmentCount`/`plannedDayCount`/sparse `days[]`, counting any day with ≥1 fragment per `/speckit-clarify` Q2) and wire it into `ListActivities`'s base (no-`day`) path in `backend/application/src/main/java/alebuc/puzzleagenda/application/activity/ListActivities.java` (depends on T015, T027)
- [X] T042 [US3] Extend `ActivityResponse`/`ActivityController`'s base (no-`day`) response to include `totalFragmentCount`/`plannedDayCount`/`days[]` in `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/ActivityController.java` (depends on T041)
- [X] T043 [P] [US3] Update `useBacklog.js` to expose the new aggregate fields already present in the `GET /api/activities` JSON response in `frontend/src/composables/useBacklog.js`
- [X] T044 [US3] Update `BacklogView.vue`/`ActivityCard.vue` to show "planned on N days" and an expandable per-day breakdown sourced from `days[]` in `frontend/src/views/BacklogView.vue` and `frontend/src/components/ActivityCard.vue` (depends on T042, T043)
- [X] T045 [P] [US3] Update `BacklogView.spec.js` for the new aggregate display in `frontend/tests/BacklogView.spec.js` (depends on T044)

**Checkpoint**: Per-day (User Story 1) and aggregate, cross-day (User Story 3) planning progress are both visible and mutually consistent.

---

## Phase 6: User Story 4 - Delete fragments, and cascade-delete a multi-fragment activity (Priority: P4)

**Goal**: Deleting a fragment offers the right choice (immediate vs. two-option scope prompt) depending on how many same-activity fragments exist that day; deleting an activity states its exact total fragment count before cascading.

**Independent Test**: With one fragment on day A and two on day B for the same activity, delete day A's fragment directly (no prompt), delete one of day B's two fragments via the scope choice, then delete the activity and confirm the stated count matches before cascading.

### Tests for User Story 4

- [X] T046 [P] [US4] Contract test: `DELETE /api/blocks/{id}` with no `scope` (default) or `scope=self` deletes only that block; `scope=activityDay` deletes every same-activity, same-day fragment in one call; an invalid `scope` value returns `400 INVALID_REQUEST` in `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/TimeBlockControllerIT.java`
- [X] T047 [P] [US4] Contract test: `DELETE /api/activities/{id}` without `confirm` on an activity with fragments across multiple days returns `409 ACTIVITY_HAS_PLANNED_FRAGMENTS` with the exact total fragment count in the message; with `confirm=true` removes the activity and every fragment across every day in `backend/infrastructure/src/test/java/alebuc/puzzleagenda/infrastructure/rest/ActivityControllerIT.java`

### Implementation for User Story 4

- [X] T048 [US4] Add a `scope` parameter (`SELF` default / `ACTIVITY_DAY`) to `DeleteTimeBlock`: when `ACTIVITY_DAY`, look up the block's `activityId` and day via `findByActivityIdAndDay` and delete every fragment returned, in `backend/application/src/main/java/alebuc/puzzleagenda/application/timeblock/DeleteTimeBlock.java` (depends on T015)
- [X] T049 [US4] Extend `TimeBlockController.deleteBlock` to accept and validate a `scope` query parameter (`self`/`activityDay`, default `self`; otherwise `400 INVALID_REQUEST`) in `backend/infrastructure/src/main/java/alebuc/puzzleagenda/infrastructure/rest/TimeBlockController.java` (depends on T048)
- [X] T050 [P] [US4] Update `useDaySchedule.js`'s `deleteBlock` to accept an optional `scope` and pass it through as a query param in `frontend/src/composables/useDaySchedule.js`
- [X] T051 [US4] Update `DayView.vue` (and/or `TimeBlockCard.vue`) to count same-activity, same-day fragments from the already-loaded day and either delete immediately (a single fragment) or show a two-option confirmation ("delete this fragment only" / "delete all fragments of this activity on this day"), reusing `BacklogView.vue`'s existing custom-modal delete-confirmation pattern (spec.md Assumptions — no native browser dialog), in `frontend/src/views/DayView.vue` (depends on T050)
- [X] T052 [US4] Update `BacklogView.vue`'s existing delete-confirmation modal to state the exact `totalFragmentCount` (from User Story 3's aggregate data) when deleting an activity that has one or more fragments in `frontend/src/views/BacklogView.vue` (depends on T044)
- [X] T053 [P] [US4] Extend `DayTimeline.spec.js`/`BacklogView.spec.js` for the new fragment-scope delete prompt and the fragment-count-aware activity delete confirmation in `frontend/tests/DayTimeline.spec.js` and `frontend/tests/BacklogView.spec.js` (depends on T051, T052)

**Checkpoint**: Fragment deletion offers the correct scope choice, and deleting a multi-fragment activity states an accurate count before cascading. All four user stories are now independently functional together.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end validation and regression sweep across all four user stories.

- [X] T054 [P] Run `specs/002-multi-day-activity-planning/quickstart.md`'s full curl walkthrough end-to-end against a live backend, confirming every annotated status code and payload shape
- [X] T055 [P] Re-run the full backend test suite (`mvn test` across all four modules) and the frontend suite (`npm run test`) to confirm no regression in feature 001's existing coverage
- [X] T056 Review `ApiExceptionHandler` and `specs/002-multi-day-activity-planning/contracts/api.md` side by side to confirm every new/changed/removed reason code (`PLANNED_ACTIVITY_SPANS_MIDNIGHT`, `ACTIVITY_HAS_PLANNED_FRAGMENTS`, the narrowed `ACTIVITY_NOT_AVAILABLE` case) is implemented exactly as documented

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Not applicable — no dependencies, nothing to do.
- **Foundational (Phase 2)**: No dependency on Phase 1. **BLOCKS all user stories** — every story reads or writes through the `Activity`/`TimeBlock` shapes, repository methods, and domain services this phase establishes.
- **User Stories (Phase 3-6)**: All depend on Foundational (Phase 2) completion.
  - User Story 1 (P1): No dependency on other stories — the MVP slice.
  - User Story 2 (P2): Builds on Foundational's `FragmentMerger`/`OverlapPolicy` (Phase 2), independent of User Story 1's day-selector work.
  - User Story 3 (P3): Reuses User Story 1's day-scoped `ListActivities`/`DayView` selector work directly (T027/T028) rather than duplicating it, and adds the aggregate, cross-day view on top.
  - User Story 4 (P4): Reuses User Story 3's aggregate fragment count (T044) for the activity-delete confirmation message; otherwise independent (fragment-scope delete is new work).
- **Polish (Phase 7)**: Depends on all four user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: Foundational only.
- **User Story 2 (P2)**: Foundational only — independently testable in isolation from User Story 1 (a fresh day with no cross-day fragments still exercises merge fully).
- **User Story 3 (P3)**: Foundational + User Story 1 (reuses its day-scoped computation and `DayView` selector).
- **User Story 4 (P4)**: Foundational + User Story 3 (reuses its aggregate fragment count for the activity-delete message); the fragment-scope delete itself only needs Foundational.

### Within Each User Story

- Tests are written first and MUST fail before the corresponding implementation task lands.
- Domain/application changes before REST controller changes before frontend changes.
- Backend of a story complete before that story's frontend work.
- Story checkpoint reached (backend + frontend + tests green) before moving to the next priority.

### Parallel Opportunities

- All `[P]`-marked Foundational tasks (T003, T004-T014 pairs, T022, T023) can run in parallel once their stated dependency lands — most are in different files.
- Once Foundational (Phase 2) completes, User Story 1 and User Story 2 can be implemented in parallel by different developers (User Story 3 and 4 must wait for User Story 1 and 3 respectively, per the dependency notes above).
- All `[P]`-marked test tasks within a story phase can run in parallel.
- All `[P]`-marked frontend composable/test tasks within a story phase can run in parallel with that story's backend implementation tasks, once the API shape they depend on is fixed by the contract tests.

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Contract test for POST /api/days/{date}/blocks second-fragment acceptance in backend/infrastructure/src/test/java/.../PlanActivityControllerIT.java"
Task: "Contract test for GET /api/activities?day= remaining time in backend/infrastructure/src/test/java/.../ActivityControllerIT.java"
Task: "Integration test for cross-day independence in backend/application/src/test/java/.../PlanActivityTest.java"

# Frontend composable and its test can proceed in parallel with the backend implementation tasks:
Task: "Extend useBacklog.js load() with an optional day param in frontend/src/composables/useBacklog.js"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 2: Foundational (CRITICAL — blocks all stories).
2. Complete Phase 3: User Story 1.
3. **STOP and VALIDATE**: run `quickstart.md`'s §1 walkthrough independently.
4. Deploy/demo if ready — this alone lets a user spread one activity across several days with correct per-day remaining time.

### Incremental Delivery

1. Foundational → foundation ready.
2. Add User Story 1 → validate independently → demo (MVP!).
3. Add User Story 2 → validate independently (fresh-day merge scenarios) → demo.
4. Add User Story 3 → validate independently (aggregate backlog view) → demo.
5. Add User Story 4 → validate independently (deletion scopes) → demo.
6. Phase 7 Polish → full regression + quickstart end-to-end.

### Parallel Team Strategy

With multiple developers, after Foundational (Phase 2) completes:

- Developer A: User Story 1, then User Story 3 (depends on US1's day-scoped work).
- Developer B: User Story 2 in parallel with A (independent of US1/US3).
- Developer C: starts User Story 4's fragment-scope-delete work (Foundational-only part) in parallel, then waits on Developer A's US3 checkpoint before finishing the activity-delete message task (T052).

---

## Notes

- `[P]` tasks touch different files with no unmet dependency.
- `[Story]` labels map tasks to spec.md's User Story 1-4 for traceability; Setup, Foundational, and Polish tasks carry no story label by design.
- Constitution Principle III makes tests mandatory here — every implementation task above has a corresponding test task before or alongside it in the same phase.
- Commit after each task or logical group; stop at any checkpoint to validate a story independently.
- Avoid: vague tasks, same-file conflicts within a `[P]` group, and cross-story dependencies beyond the ones explicitly documented above.
