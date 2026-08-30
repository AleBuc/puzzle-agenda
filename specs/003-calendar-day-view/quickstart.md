# Quickstart: Validating the Calendar-Style Day Grid View

This is a validation guide, not an implementation guide — it proves the feature works end-to-end once built. See `data-model.md` and `contracts/` for the shapes and events referenced below.

## Prerequisites

- JDK 25, Maven, Node 20+, npm, Docker (per the repo root `README.md`).
- On branch `003-calendar-day-view` with the feature implemented.

## Setup

```bash
# 1. Database (skip if already running)
docker run -d --name puzzle-agenda-db \
  -e POSTGRES_DB=puzzle_agenda -e POSTGRES_USER=puzzle_agenda -e POSTGRES_PASSWORD=puzzle_agenda \
  -p 5432:5432 postgres:16

# 2. Backend (from backend/) — unchanged by this feature
mvn install -DskipTests
mvn -pl bootstrap spring-boot:run   # → http://localhost:8080

# 3. Frontend (from frontend/, separate shell)
npm install    # picks up the new reka-ui dependency
npm run dev    # → http://localhost:5173
```

## Automated checks

```bash
cd frontend
npm test        # time-grid-utils.spec.js, DayGrid.spec.js, BlockPopup.spec.js, DayView.spec.js all green
npm run build   # production build succeeds
```

## Manual validation scenarios

Each scenario below maps to one user story's Independent Test in `spec.md`. Use a day with no pre-existing blocks unless a scenario says otherwise.

### 1. Proportional grid view (User Story 1, P1)

1. Create, via the grid (see scenario 2) or directly through the API, a routine block, a constrained block, a planned-activity block, one 5-minute block, and one block spanning midnight into the next day.
2. Open that day (`/days/{date}`).
3. **Expect**: blocks appear at vertically proportional positions/heights matching their times; the three types are visually distinct without opening any of them; the midnight-spanning block is clamped at the bottom edge with a continuation indicator (and the next day shows the matching indicator at its top edge); the 5-minute block is still individually visible/clickable (via its compact label or tooltip); free intervals are plain empty grid space (no "Free" text anywhere); no current-time indicator appears (only today's view has one).
4. Open today's view. **Expect**: a current-time indicator line at the correct vertical position, and the grid initially scrolled to that position (FR-021).

### 2. Create a block from an empty slot (User Story 2, P2)

1. On an empty day, click an empty area of the grid at an arbitrary time.
2. **Expect**: the creation popup opens with the start time snapped to the nearest 15-minute mark at or before the click, end time defaulted to one hour later.
3. Adjust the start/end time; confirm only 5-minute increments are reachable.
4. Select "Planned activity"; **expect** the activity selector to list activities with remaining time for this day.
5. Confirm creation. **Expect**: the popup closes and the new block appears on the grid at the right position.
6. Repeat, choosing times that overlap the just-created block. **Expect**: the popup shows the mapped overlap message ("This time slot overlaps an existing block.") and does not create anything, without closing the popup.

### 3. View, edit, and delete via popup (User Story 3, P2)

1. Click an existing single-fragment block. **Expect**: a details popup with its type, time range, name/activity, and Edit/Delete actions.
2. Edit its time to a non-conflicting range. **Expect**: the popup closes and the block's position/size updates on the grid.
3. Create a second same-day fragment of the same activity (two `PLANNED_ACTIVITY` blocks, same `activityId`, non-adjacent times). Click one, choose Delete. **Expect**: the "delete this fragment only" / "delete all fragments of this activity today" choice appears in place, inside the same popup — not a separate page or a nested dialog.
4. Delete a block from a second browser tab, then attempt to delete or edit the same (now-stale) block from the first tab's still-open grid. **Expect**: the mapped "no longer exists" message and a grid refresh to the real state, exactly as the existing list view already does for this case.

### 4. Keyboard-only operation (User Story 4, P3)

1. Without touching the mouse, Tab into the day view and reach the persistent "Add block" control; activate it (Enter/Space). **Expect**: the creation popup opens directly with an adjustable default time (no need to have tabbed through any grid slot first).
2. Fill in and submit the popup entirely via keyboard.
3. Tab through the grid's empty slots (arrow keys move the roving focus in 5-minute steps per FR-023); activate one. **Expect**: same creation popup, pre-filled with that slot's time.
4. While a popup is open, press Tab repeatedly. **Expect**: focus never leaves the popup.
5. Press Escape. **Expect**: the popup closes, any unsaved content is discarded (no draft retained), and focus returns to whatever element opened it.
6. While a popup is open, press the Left/Right arrow day-navigation shortcut. **Expect**: the viewed day does not change. Close the popup and repeat — **expect** normal day navigation now works.
7. Tab to an existing block, activate it, and delete it — entirely via keyboard.

### 5. Backdrop-click draft retention (clarification, covered by `BlockPopup.spec.js` + manual spot-check)

1. Open the creation popup on an empty slot, fill in a name/type/activity/duration, then click outside the popup (on the dimmed backdrop).
2. **Expect**: the popup closes.
3. Click a *different* empty slot on the **same day**.
4. **Expect**: the popup reopens with the previously entered name/type/activity/duration restored, but the start time comes from the newly clicked slot.
5. Navigate to a different day and back, or successfully create a block. **Expect**: the draft no longer reappears.

## Regression check

Confirm nothing outside the day view changed: `/backlog` and `/routine-template` behave exactly as before (per spec Assumptions) — no visual or functional change expected there.
