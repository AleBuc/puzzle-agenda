---
description: "Task list for Calendar-Style Day Grid View"
---

# Tasks: Calendar-Style Day Grid View

**Input**: Design documents from `/specs/003-calendar-day-view/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md (all present)

**Tests**: Included — Constitution Principle III ("Every feature MUST ship with tests before merge") and the feature owner's explicit testing-strategy plan input require them. Each pure-function/composable/component pair below follows write-test-then-implement.

**Organization**: Tasks are grouped by user story (spec.md priorities P1–P3) so each story is independently implementable and testable. This is a frontend-only feature (`frontend/` only); `backend/` is untouched throughout.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Maps the task to spec.md's US1–US4
- Every task names an exact file path

## Path Conventions

Web app monorepo, frontend-only for this feature: `frontend/src/`, `frontend/tests/` (see plan.md Project Structure for the full file list).

## Important sequencing note (read before starting)

The new interaction model moves block management from per-row Edit/Delete buttons (in the retired `TimeBlockCard.vue`) to "click the block to open a popup" (`GridBlock.vue` + `BlockPopup.vue`, per FR-011/FR-012). Because of this, **editing and deleting an existing block is temporarily unreachable in the UI between the end of User Story 1 and the end of User Story 3** (creation, via the grid+popup, becomes available at the end of User Story 2; the old bottom form covers creation until then). This is an accepted, documented consequence of implementing stories in priority order on one feature branch — not a bug to fix mid-sequence. `spec.md`'s SC-004 ("zero loss of capability") is satisfied once User Story 3 is complete, not necessarily at every intermediate checkpoint.

---

## Phase 1: Setup

**Purpose**: Bring in the one new dependency this feature needs.

- [X] T001 Install `reka-ui@^2.10.4` in `frontend/` (`npm install reka-ui@^2.10.4`, updating `frontend/package.json` and `frontend/package-lock.json`) — per plan.md Technical Context and research.md §1

**Checkpoint**: Dependency available; no code changed yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The minutes/percent/snapping math every story's components rely on (layout in US1, click/keyboard snapping in US2–US4).

**⚠️ CRITICAL**: No user story work should begin until this phase is complete.

- [X] T002 [P] Write `frontend/tests/time-grid-utils.spec.js`: unit tests for `toMinutes`, `formatMinutes`, `effectiveStart`, `effectiveEnd`, `minutesToPercent`, `snapDownToQuarterHour`, `snapToFiveMinutes`, and `layoutBlocks`, per the exact signatures in `contracts/component-contracts.md` and the `GridPositionedBlock` shape in `data-model.md` (include a midnight-spanning block case for `effectiveStart`/`effectiveEnd`/`layoutBlocks`, and a ≤5-minute block case asserting `isVeryShort: true`). These tests fail until T003.
- [X] T003 Implement `frontend/src/time-grid-utils.js`: extract `toMinutes`, `formatMinutes`, `effectiveStart` from `frontend/src/components/DayTimeline.vue` unchanged; add `effectiveEnd` (symmetric to `effectiveStart`, using `endsNextDay`), `minutesToPercent`, `snapDownToQuarterHour`, `snapToFiveMinutes`, and `layoutBlocks` (maps `TimeBlock[]` to `GridPositionedBlock[]`, setting `isVeryShort` at ≤5 minutes per FR-022 and `isContinuationOnly` from `startsPreviousDay`). Makes T002 pass.

**Checkpoint**: Shared time-math ready. `DayTimeline.vue`/`TimeBlockCard.vue` still render the day view unchanged — nothing is broken yet.

---

## Phase 3: User Story 1 - See the day at a glance as a proportional grid (Priority: P1) 🎯 MVP

**Goal**: Replace the flat list with the proportional 24h grid: positioned/sized blocks, per-type styling, midnight clamp + continuation indicator, short-block treatment, current-time indicator, correct initial scroll position. Read-only for now — clicking a block or an empty slot does not yet do anything; the existing bottom add/edit form and delete/confirm UI are left exactly as they are.

**Independent Test**: spec.md User Story 1 — seed a day with one block of each type, one ≤5-minute block, and one midnight-spanning block; open the day and verify proportional positions, type distinction, clamp + continuation indicator, short-block visibility, no "Free" labels, and (today only) the current-time indicator.

### Tests for User Story 1

- [X] T004 [P] [US1] Write `frontend/tests/GridBlock.spec.js`: per-type CSS class (routine/constrained/planned_activity, reusing `TimeBlockCard.vue`'s existing color scheme), the continuation-indicator markup when `positioned.isContinuationOnly` is true, and the compact-label/tooltip rendering when `positioned.isVeryShort` is true (FR-003, FR-004, FR-005)
- [X] T005 [P] [US1] Write `frontend/tests/DayGrid.spec.js` (view-only subset): hour gridlines/labels render for 00:00–24:00; blocks passed through `layoutBlocks` are positioned at the expected `top`/`height` percentages; the current-time indicator renders only when `date` equals today's ISO date (mock `todayIsoDate`) and at the expected position; the grid's initial scroll position targets "now" when `date` is today and 00:00 otherwise (FR-006, FR-021)

### Implementation for User Story 1

- [X] T006 [US1] Implement `frontend/src/components/GridBlock.vue` per `contracts/component-contracts.md` (prop: `positioned: GridPositionedBlock`; emits `activate` with `positioned.block`). Makes T004 pass.
- [X] T007 [US1] Implement `frontend/src/components/DayGrid.vue` per `contracts/component-contracts.md` (props: `date`, `blocks`; renders hour gridlines/labels, `GridBlock` children via `layoutBlocks`, the current-time indicator, and sets the initial scroll position per FR-021; emits `activate-slot`/`activate-block` on click, not yet consumed by anything). Makes T005 pass.
- [X] T008 [US1] In `frontend/src/views/DayView.vue`, replace `<DayTimeline :blocks="day?.blocks ?? []" @edit="startEdit" @delete="handleDelete" />` with `<DayGrid :date="date" :blocks="day?.blocks ?? []" />`. Keep the existing `v-if="loading"` / `v-else-if="error"` states exactly as they are (FR-025). Leave the inline add/edit `<form>` and the `<div class="day-view__confirm">` block untouched.
- [X] T009 [US1] Update `frontend/tests/DayView.spec.js`: **correction discovered during implementation** — the per-row Edit/Delete buttons (`.time-block-card__actions`) that the existing delete/scope-confirmation/error-mapping tests depend on come from the now-retired `TimeBlockCard`/`DayTimeline`, not from the bottom form; `GridBlock` has no per-row buttons (per its contract, the whole block is clickable and emits `activate`, unwired until User Story 3). So: (a) keep the activity-selector test unchanged (it only exercises the still-present bottom form), (b) remove the four delete/scope-confirmation/error-mapping tests that click `.time-block-card__actions` — they test UI that is genuinely gone until User Story 3 rebuilds it against the popup (tracked in T021, not silently dropped), (c) add a basic rendering assertion confirming `DayGrid` receives `day.blocks` and renders the expected number of `.grid-block` elements for a given fixture.

**Checkpoint**: Grid renders correctly end-to-end. Creation still works via the untouched bottom form. Per the sequencing note above, editing/deleting an existing block is temporarily unreachable (no per-row buttons anymore, and the popup that replaces them doesn't exist until User Story 3) — expected at this point.

---

## Phase 4: User Story 2 - Create a block by selecting a slot in the grid (Priority: P2)

**Goal**: Clicking an empty grid slot opens an accessible popup (Reka UI `Dialog`), pre-filled/snapped, that creates a block on confirm and shows mapped errors in place on rejection; a backdrop-click dismissal retains a same-day draft. Retires the old inline creation form.

**Independent Test**: spec.md User Story 2 — click an empty slot, verify pre-fill/snap and the three type options (with per-day remaining time for planned activities), confirm a valid block, and separately confirm an overlapping one shows the mapped rejection message without creating anything.

### Tests for User Story 2

- [ ] T010 [P] [US2] Write `frontend/tests/useBlockDraft.spec.js`: `captureDraft(fields)` sets `draft.value` to `{ day, ...fields }`; `clearDraft()` sets it to `null`; changing the watched date ref clears an existing draft automatically (per `contracts/component-contracts.md` and `data-model.md`'s `BlockDraft` lifecycle)
- [ ] T011 [P] [US2] Write `frontend/tests/BlockPopup.spec.js` (creation-mode subset): opens with the given `popupState.startTime` and a default end time 1 hour later; start/end adjust only in 5-minute increments; the activity selector appears only for `PLANNED_ACTIVITY` and lists `dayActivities` with remaining time; confirming emits `submit-create` with the assembled payload; an `errorMessage` prop renders inside the popup without closing it; Escape emits `closed({reason:"escape"})` with no `snapshot`; a simulated backdrop-outside interaction emits `closed({reason:"backdrop", snapshot:{...}})` and does **not** emit `submit-create`; when a non-null `draft` prop is given, the popup pre-fills `type`/`name`/`activityId`/duration from it while `startTime` still comes from `popupState.startTime`

### Implementation for User Story 2

- [ ] T012 [P] [US2] Implement `frontend/src/composables/useBlockDraft.js` per `contracts/component-contracts.md` and `data-model.md`'s `BlockDraft` lifecycle (plain `ref`, no state-management library — Constitution Principle V). Makes T010 pass.
- [ ] T013 [P] [US2] Implement `frontend/src/components/BlockPopup.vue`, creation mode only: wrap `reka-ui`'s `DialogRoot`/`DialogPortal`/`DialogOverlay`/`DialogContent`/`DialogTitle`/`DialogClose` bound to `:open="popupState !== null"`; render type/start/end/activity-or-name fields per FR-008–FR-010; emit `submit-create` on confirm and `closed` on Escape (`reason:"escape"`), the popup's own close control (`reason:"close-button"`), and the Reka `@pointer-down-outside` event (`reason:"backdrop"`, with a `snapshot` of current field values when in create mode). Makes T011 pass.
- [ ] T014 [US2] In `frontend/src/views/DayView.vue`: add a `popupState` ref (`data-model.md` `PopupState`) and `const { draft, captureDraft, clearDraft } = useBlockDraft(dateRef)`; wire `DayGrid`'s `activate-slot` to set `popupState.value = { mode: "create", startTime }`; handle `BlockPopup`'s `submit-create` (call `createBlock`, map failures via `resolveErrorMessage`/`GENERIC_ERROR_MESSAGE` into an `errorMessage` ref passed to the popup, and on success set `popupState.value = null` and `clearDraft()`) and `closed` (`reason === "backdrop"` → `captureDraft(snapshot)`; anything else → `clearDraft()`; always `popupState.value = null`); render `<BlockPopup :popup-state="popupState" :day-activities="dayActivities" :draft="draft" :error-message="errorMessage" @submit-create="..." @closed="..." />`.
- [ ] T015 [US2] In `frontend/src/views/DayView.vue`, remove the old inline creation form and its now-superseded logic: `emptyForm`, `submitForm`'s create branches, `startEdit`, `cancelEdit`, and the `<form class="day-view__form">` template block. Keep the `formError` ref and its `<p v-if="formError" class="day-view__error">` display, relocated to render directly in the template (no longer nested inside the removed `<form>`) — it is still used by `handleDelete`/`confirmFragmentDelete` until User Story 3 replaces them.
- [ ] T016 [US2] Update `frontend/tests/DayView.spec.js`: replace the old inline-form creation assertions with popup-driven ones (activate an empty slot → popup opens pre-filled → submit → block appears on the grid; an overlapping submission shows the mapped message inside the popup and creates nothing).

**Checkpoint**: Blocks can be created end-to-end via the grid and popup, including the mapped-error and draft-retention paths. Editing/deleting still not reachable (User Story 3).

---

## Phase 5: User Story 3 - View, edit, and delete an existing block via a popup (Priority: P2)

**Goal**: Clicking an existing block (including a spillover/continuation rendering) opens `BlockPopup` in details mode with edit and delete, including the in-place multi-fragment choice and the read-only spillover variant with its "go to start day" link. Retires the old confirm-delete UI. Restores full CRUD parity (spec.md SC-004).

**Independent Test**: spec.md User Story 3 — edit a single-fragment block and see it move on the grid; delete a single-fragment block immediately; create two same-day fragments of one activity, delete one, and verify the in-place two-choice confirmation; trigger a stale-state failure (edit/delete from a second tab first) and verify the mapped message plus a full-state grid refresh.

### Tests for User Story 3

- [ ] T017 [US3] Extend `frontend/tests/BlockPopup.spec.js` with a details-mode subset: renders the block's type/time/name-or-activity with edit and delete actions; a valid edit emits `submit-edit` with `{id, startTime, endTime, name}`; deleting a block that is its activity's only same-day fragment emits `submit-delete({id, scope:"self"})` immediately; deleting one of several same-day fragments first shows the in-place "this fragment only" / "all fragments of this activity today" choice (as local component state, not a second dialog) before emitting `submit-delete` with the corresponding `scope`; an `errorMessage` prop renders without losing the popup's current view; when `popupState.mode === "details"` and `popupState.readOnly` is true, no edit/delete controls render, a "Starts on {date}" (or "Continues from {date}" if the exact start time isn't available) notice with a link is shown instead, and activating that link emits `closed({reason:"navigate-to-start-day"})`

### Implementation for User Story 3

- [ ] T018 [US3] Extend `frontend/src/components/BlockPopup.vue` with details mode per `contracts/component-contracts.md`: block-detail display, an edit sub-form emitting `submit-edit`, a delete action that emits `submit-delete` directly for a single fragment or shows the in-place multi-fragment choice first (FR-013), and the `readOnly` rendering with its "Starts {block.startTime} on {date} — edit it from that day" notice and "go to start day" link (`closed({reason:"navigate-to-start-day"})`) — `block.startTime` is always the real local time, confirmed never clamped (`TimeBlockResponseAssembler`/`TimeBlockResponse`, verified during planning). Makes T017 pass.
- [ ] T019 [US3] In `frontend/src/views/DayView.vue`: wire `DayGrid`'s `activate-block` to set `popupState.value = { mode: "details", block, readOnly: block.startsPreviousDay }`; handle `submit-edit` (call `editBlock`, same success/error handling pattern as `submit-create`) and `submit-delete` (call `deleteBlock` with the given `scope`; on failure, set `errorMessage`, `await load()` to refresh the grid per FR-016, and close the popup only if the reloaded day no longer contains the target block); handle the `navigate-to-start-day` close reason by clearing `popupState` and then calling `goToDate(shiftIsoDate(date, -1))`.
- [ ] T020 [US3] In `frontend/src/views/DayView.vue`, remove the now-superseded `pendingFragmentDelete`, `confirmFragmentDelete`, `cancelFragmentDelete`, `sameActivityFragmentCount`, and `handleDelete`, plus the `<div class="day-view__confirm">` template block. Remove the `formError` ref and its display `<p>` kept from User Story 2 — `BlockPopup`'s `errorMessage` prop now covers every error path.
- [ ] T021 [US3] Update `frontend/tests/DayView.spec.js`: rebuild, against the new popup, the delete/scope-confirmation/error-mapping coverage removed in T009 — activating an existing block opens the details popup; editing and single-fragment deleting both work through it; a multi-fragment delete shows the in-place choice; a stale-state failure (mock a 404 on edit/delete) shows the mapped message and a full grid reload; activating a continuation-only (spillover) block opens the read-only popup and its link navigates to the previous day.

**Checkpoint**: Full CRUD parity with the old list view is restored (spec.md SC-004). All four business flows (create, edit, single-fragment delete, multi-fragment delete) work via the grid+popup. Not yet fully keyboard-operable (User Story 4).

---

## Phase 6: User Story 4 - Operate the grid and popups using only the keyboard (Priority: P3)

**Goal**: A keyboard-only user can reach and activate any empty slot or existing block via a roving-tabindex grid model, use a persistent "Add block" fast path instead of tabbing slot-by-slot, and have day-navigation shortcuts suspended while a popup is open (resuming once it closes). Confirms the dialog's focus-trap/Escape/focus-return behavior end-to-end.

**Independent Test**: spec.md User Story 4 — without a mouse, reach and activate the persistent "Add block" control; separately, tab/arrow through grid slots in 5-minute steps and activate one; confirm Tab never escapes an open popup; confirm Escape closes a popup and returns focus to its trigger; confirm arrow-key day-navigation is inert while a popup is open and resumes once it closes.

### Tests for User Story 4

- [ ] T022 [P] [US4] Extend `frontend/tests/DayGrid.spec.js` with a keyboard subset: the roving-tabindex focus moves across empty slots in 5-minute increments (FR-023) and can reach every existing block; Enter/Space on a focused empty slot or block fires the same `activate-slot`/`activate-block` event as a click; the persistent "Add block" control is always keyboard-reachable (a normal tab stop, not part of the roving-tabindex sequence) and fires `activate-slot` with the today-vs-other-day default time from research.md §6
- [ ] T023 [P] [US4] Extend `frontend/tests/DayView.spec.js` with a keyboard subset: with `popupState` non-null, pressing the day-navigation buttons or Left/Right arrow keys does not change the viewed day; after the popup closes, the same actions navigate normally (FR-019)

### Implementation for User Story 4

- [ ] T024 [US4] In `frontend/src/components/DayGrid.vue`, implement the roving-tabindex keyboard model over the grid's 288 five-minute slots and add the persistent "Add block" control (a normal, always-focusable button) per `contracts/component-contracts.md` and research.md §3/§6. Makes T022 pass.
- [ ] T025 [US4] In `frontend/src/views/DayView.vue`, add a `popupState.value !== null` guard at the top of the existing `handleKeydown`, alongside its current form-control guard, so day-navigation shortcuts are suspended while any popup is open. Makes T023 pass.
- [ ] T026 [P] [US4] Extend `frontend/tests/BlockPopup.spec.js` with an accessibility subset verifying, to the extent observable under Vue Test Utils/jsdom (research.md §7 caveat noted): Tab cycling stays within the open dialog's interactive elements; Escape returns focus to the element that opened the popup. Adjust `BlockPopup.vue`'s Reka UI wiring only if a real gap is found — this is expected to already pass, since `reka-ui`'s `Dialog` provides focus trap and focus-return out of the box (research.md §1).

**Checkpoint**: All four user stories complete. spec.md's Success Criteria (SC-001 through SC-007) are all satisfiable; run `quickstart.md` next.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Remove now-dead code and do a final end-to-end pass.

- [ ] T027 [P] Delete `frontend/src/components/DayTimeline.vue` and `frontend/src/components/TimeBlockCard.vue` (fully superseded since Phase 3; unreferenced from `DayView.vue` since T008)
- [ ] T028 [P] Delete `frontend/tests/DayTimeline.spec.js` (superseded by `time-grid-utils.spec.js` and `DayGrid.spec.js`)
- [ ] T029 Run `npm test` and `npm run build` from `frontend/` and fix any remaining failures
- [ ] T030 Walk through every manual scenario in `specs/003-calendar-day-view/quickstart.md` against a running dev server (`npm run dev` + the existing backend) and confirm each expected outcome, including the regression check that `/backlog` and `/routine-template` are unaffected

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup. Blocks all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational only.
- **User Story 2 (Phase 4)**: Depends on Foundational; in practice built after User Story 1 lands (`DayGrid`'s `activate-slot` must exist to wire into), so treat as sequential after Phase 3 despite no data dependency on US1's visual output.
- **User Story 3 (Phase 5)**: Depends on Foundational and, in practice, on `DayGrid`'s `activate-block` (Phase 3) and `BlockPopup`'s shell (Phase 4) — sequential after Phase 4.
- **User Story 4 (Phase 6)**: Depends on `DayGrid.vue` (Phase 3) and `BlockPopup.vue`/`popupState` (Phases 4–5) already existing — sequential after Phase 5.
- **Polish (Phase 7)**: Depends on all four user stories being complete.

Unlike a typical multi-team feature, these four stories are **not independently parallelizable by different developers** in this codebase: they modify the same three files (`DayGrid.vue`, `BlockPopup.vue`, `DayView.vue`) in an additive sequence. Treat Phases 3→4→5→6 as strictly ordered; the "independent" in each Independent Test refers to independent *verification*, not independent *implementation order*.

### Within Each User Story

- Write the story's tests first (where a test task precedes its matching implementation task); confirm they fail before implementing.
- Implement composables/pure functions before the components that consume them.
- Wire `DayView.vue` integration only after the component(s) it wires exist.
- Remove superseded code only in the same phase that finishes its replacement.

### Parallel Opportunities

- Within Phase 2: none (single file pair, sequential T002→T003).
- Within Phase 3: T004 and T005 (different test files).
- Within Phase 4: T010 and T011 (different test files); T012 and T013 (different implementation files, each depending only on its own preceding test).
- Within Phase 5: none (single sequential chain touching shared files).
- Within Phase 6: T022 and T023 (different test files); T026 alongside T024/T025 (independent concern, different files).
- Within Phase 7: T027 and T028 (independent deletions).

---

## Parallel Example: User Story 2

```bash
# Tests first, in parallel (different files):
Task: "Write frontend/tests/useBlockDraft.spec.js"
Task: "Write frontend/tests/BlockPopup.spec.js (creation-mode subset)"

# Then implementations, in parallel (different files, each satisfying only its own test):
Task: "Implement frontend/src/composables/useBlockDraft.js"
Task: "Implement frontend/src/components/BlockPopup.vue (creation mode)"

# Then the integration task, sequential (depends on both above):
Task: "Wire PopupState/useBlockDraft into frontend/src/views/DayView.vue"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup.
2. Phase 2: Foundational.
3. Phase 3: User Story 1.
4. **STOP and VALIDATE**: run `quickstart.md` scenario 1 against a build with only these phases. The grid renders correctly; creation still works via the old form; editing/deleting is temporarily unreachable (see sequencing note) — acceptable for an MVP checkpoint on a feature branch, not for a production deploy of just this slice.

### Incremental Delivery

1. Setup + Foundational → base ready.
2. User Story 1 → grid view lands (MVP demo of the visual change).
3. User Story 2 → creation moves to the grid+popup.
4. User Story 3 → edit/delete moves to the grid+popup; full parity restored (SC-004).
5. User Story 4 → full keyboard operability; all Success Criteria satisfiable.
6. Polish → dead code removed, `npm test`/`npm run build` green, `quickstart.md` fully walked.

This feature ships as one PR/branch merge once Phase 7 is done — see "Dependencies & Execution Order" above on why these stories are sequential rather than parallel-team work here.

---

## Notes

- [P] tasks touch different files and have no dependency on an incomplete task.
- [Story] labels map each task to spec.md's US1–US4 for traceability.
- Constitution Principle V (YAGNI): no task introduces a state-management library or any Reka UI primitive beyond `Dialog`.
- Constitution Principle IV: no task modifies `backend/` or any REST contract — verify this holds when reviewing each PR/commit against these tasks.
- Commit after each task or logical group; stop at any Checkpoint to validate that story's Independent Test before continuing.
