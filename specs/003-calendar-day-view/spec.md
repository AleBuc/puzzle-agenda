# Feature Specification: Calendar-Style Day Grid View

**Feature Branch**: `003-calendar-day-view`

**Created**: 2026-08-29

**Status**: Draft

**Input**: User description: "Google Calendar-style day view for puzzle-agenda. Replace the current list-based day timeline with a vertical, time-proportional grid: a 24-hour column with hour labels and gridlines, where each time block is positioned and sized proportionally to its start time and duration. Routine, constrained, and planned-activity blocks remain visually distinct. Blocks spanning midnight render clamped to the day's edge with a visual continuation indicator on both affected days. Very short blocks remain identifiable (compact label or accessible tooltip). A current-time indicator line is shown on today's view. Free time is visible as empty grid space (no explicit \"Free\" items). Interaction: clicking an empty slot opens a creation popup with the start time pre-filled, snapped to the nearest 15 minutes, default duration 1 hour, both adjustable at 5-minute granularity; the popup offers the three block types, including the activity selector with per-day remaining time. Clicking an existing block opens a popup showing its details with edit and delete actions; deletion reuses the existing multi-fragment confirmation flow (\"this fragment only\" / \"all fragments of this activity today\"). All existing business rules apply unchanged (overlap rejection with mapped error messages shown in the popup, horizon, 5-minute granularity, fragment merging). The popup is a proper accessible dialog: focus trapped while open, closable with Escape, focus returned to the triggering element, usable with keyboard only (a keyboard path to create a block on a chosen slot must exist, not just mouse clicks). Day-to-day navigation (buttons + arrow keys) is unchanged. Out of scope: drag-and-drop to move or resize blocks (planned as its own feature, using the existing move endpoint), week view, zoom levels, mobile-specific gestures."

## Clarifications

### Session 2026-08-29

- Q: Should the Left/Right arrow-key day-navigation shortcut be suspended while a creation or details popup is open? → A: Yes — day-navigation shortcuts (buttons and arrow keys alike) are suspended while any popup is open and resume once it closes; the day a popup was opened for stays fixed until the popup closes.
- Q: Should clicking outside the popup (on the dimmed backdrop) close it, the same way Escape does? → A: A backdrop click closes the popup, but — unlike Escape, which discards immediately — its current unsaved field values are kept in a temporary draft cache scoped to that specific slot or block; reopening the popup for the same slot or block while still viewing the same day restores the draft. The draft cache is cleared when the user navigates to a different day, or when a block is successfully created or saved.
- Q: What should the grid display while the day's data is loading, or if it fails to load? → A: The existing loading and error messaging ("Loading…" while fetching, "Could not load day." on failure) is preserved as-is; how it is presented alongside the grid (replacing it outright vs. an overlay on the grid skeleton) is left as a presentation choice for planning, not a fixed requirement.
- Q: Should there be a faster keyboard entry point for creating a block, in addition to tabbing through the grid slot-by-slot? → A: Yes — a persistent, keyboard-reachable "Add block" control opens the creation popup directly (with an adjustable default time) as a fast path, alongside (not instead of) tabbing through individual grid slots to target a precise time.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See the day at a glance as a proportional grid (Priority: P1)

A user opens a day and, instead of reading a flat list of time ranges, sees a vertical 24-hour timeline where every block's position and height reflect its actual start time and duration, blocks are colored/styled by type, and free time is simply the empty space between blocks — letting the user judge at a glance how full or free the day is and where things are, the way a paper or digital calendar day view works.

**Why this priority**: This is the core value of the feature and the reason it exists — without it, there is no calendar-style view, only the old list. Every other capability in this feature (creating, editing, deleting via the grid) is built on top of this visual model.

**Independent Test**: Seed a day with a routine block, a constrained block, a planned-activity block, one very short block, and one block spanning midnight into the next day. Open the day view and verify: blocks appear at vertically proportional positions matching their times, are visually distinguishable by type, the midnight-spanning block is clamped at the day boundary with a continuation indicator, the very short block is still identifiable, free intervals show as plain empty space (no "Free" text/items), and — only when the viewed day is today — a current-time indicator line appears at the correct vertical position.

**Acceptance Scenarios**:

1. **Given** a day with a 09:00–10:30 block and a 14:00–15:00 block, **When** the day view is opened, **Then** the two blocks appear as vertically separated regions whose top position and height are proportional to 09:00–10:30 and 14:00–15:00 on a 24-hour scale, with the interval between them rendered as empty grid space and no "Free" label anywhere.
2. **Given** a routine block, a constrained block, and a planned-activity block on the same day, **When** the day view is opened, **Then** each block is visually distinguishable by type without needing to open it.
3. **Given** a block that starts before midnight and ends after midnight (spanning two days), **When** either affected day is opened, **Then** the block is rendered clamped to that day's 00:00–24:00 edge with a visual indicator showing it continues onto the adjacent day.
4. **Given** the day being viewed is today, **When** the day view is open, **Then** a current-time indicator line is shown at the position corresponding to the current time, and no such line appears when viewing a different day.
5. **Given** a block shorter than the minimum comfortably-labelable height, **When** the day view is opened, **Then** the block is still visually present, individually clickable, and its full details are available (via a compact label and/or an accessible tooltip) without being visually merged into a neighboring block or hidden.

---

### User Story 2 - Create a block by selecting a slot in the grid (Priority: P2)

A user clicks (or keyboard-selects) an empty area of the grid at the time they want, and a popup opens pre-filled with that start time (snapped to the nearest 15 minutes) and a default 1-hour duration, letting them adjust start/end at 5-minute granularity, choose the block type, and — for a planned activity — pick from the activity list showing each activity's remaining time for that day, then confirm to create the block.

**Why this priority**: Once the grid can be viewed, creating blocks directly on it is the primary way a user will build out their day; it replaces the previous standalone "Add a block" form.

**Independent Test**: With the day view open, click an empty slot at an arbitrary time, verify the popup opens with the expected pre-filled/snapped start time and default duration, adjust the times, pick each of the three block types in turn (verifying the activity selector appears only for the planned-activity type and lists remaining time per activity), and confirm creation. Separately, trigger a slot whose default duration would overlap an existing block and verify the rejection message appears in the popup without creating anything.

**Acceptance Scenarios**:

1. **Given** the day view is open, **When** the user clicks an empty area of the grid at an arbitrary vertical position, **Then** a creation popup opens with the start time pre-filled to the nearest 15-minute mark at or before the clicked position and the end time defaulted to 1 hour after that start time.
2. **Given** the creation popup is open, **When** the user adjusts the start or end time, **Then** the adjustment moves in 5-minute increments and the popup does not allow submitting a range that violates the existing time-granularity rule.
3. **Given** the creation popup is open and "planned activity" is selected as the type, **When** the activity selector is shown, **Then** it lists the available activities together with each one's remaining time for the day being viewed, consistent with the existing activity-selection behavior.
4. **Given** the creation popup is open with a time range that overlaps an existing block, **When** the user confirms creation, **Then** the popup shows the existing mapped overlap error message and the block is not created.
5. **Given** the creation popup is open, **When** the user confirms a valid, non-overlapping block, **Then** the popup closes, the new block appears on the grid at its proportional position, and the day's data is refreshed.

---

### User Story 3 - View, edit, and delete an existing block via a popup (Priority: P2)

A user clicks an existing block in the grid to open a popup showing its details, from which they can edit its time (and name, where applicable) or delete it; deleting a block that is one of several same-day fragments of the same activity offers the existing choice between removing just that fragment or every fragment of that activity for the day.

**Why this priority**: Equal in priority to creation — a calendar view that can show and create blocks but not manage existing ones is not a viable replacement for the current list view, which already supports edit and delete.

**Independent Test**: Click an existing single-fragment block, edit its time, and verify the change is reflected on the grid. Click an existing block that has sibling fragments of the same activity that day, choose delete, and verify the two-choice confirmation (this fragment only / all fragments of this activity today) appears and each choice behaves as in the existing flow, including showing a mapped error message and refreshing the grid if the deletion fails.

**Acceptance Scenarios**:

1. **Given** an existing block on the grid, **When** the user clicks it, **Then** a popup opens showing that block's details (type, time range, and name or activity) with edit and delete actions.
2. **Given** the details popup is open for a block, **When** the user chooses edit and changes its time range to one that does not conflict with any other block, **Then** the change is saved, the popup closes, and the block's position/size on the grid updates accordingly.
3. **Given** the details popup is open for a block that is the only fragment of its activity that day, **When** the user chooses delete, **Then** the block is removed immediately without an additional confirmation step, consistent with existing behavior.
4. **Given** the details popup is open for a block that is one of several same-day fragments of the same activity, **When** the user chooses delete, **Then** a choice between "delete this fragment only" and "delete all fragments of this activity today" is presented before any deletion occurs.
5. **Given** an edit or delete action fails (for example, the block was already changed or removed elsewhere), **When** the failure occurs, **Then** the popup (or the grid, once the popup closes) shows the existing mapped error message and the grid refreshes to reflect the real state, with no partially-applied change left visible.

---

### User Story 4 - Operate the grid and popups using only the keyboard (Priority: P3)

A keyboard-only user navigates the day grid, reaches and activates an empty slot to open the creation popup, and operates every popup (creation or details) — including moving between its fields and confirming or canceling — without ever needing a mouse; while a popup is open, focus stays within it, Escape closes it, and focus returns to whatever element was used to open it.

**Why this priority**: This is an accessibility requirement layered on top of the interactions introduced by User Stories 2 and 3; it does not introduce new planning capability but makes the capability already delivered usable without a mouse.

**Independent Test**: Without using a mouse, tab/arrow through the grid to reach a chosen empty time slot, activate it to open the creation popup, fill it in and submit using only the keyboard, then tab to an existing block, activate it, and delete it using only the keyboard — confirming at each popup that focus is trapped inside while open, Escape closes it, and focus lands back on the element that opened it.

**Acceptance Scenarios**:

1. **Given** the day view has keyboard focus, **When** the user uses the keyboard to move through the grid, **Then** every empty slot and every existing block can be reached and activated (to open the creation or details popup respectively) without using a mouse.
2. **Given** a popup (creation or details) is open, **When** the user presses Tab repeatedly, **Then** focus cycles only among the popup's own interactive elements and never escapes to the page behind it.
3. **Given** a popup is open, **When** the user presses Escape, **Then** the popup closes without applying any unsaved change, and keyboard focus returns to the element that had opened the popup.
4. **Given** the day view with no popup open, **When** the user uses the existing day-to-day navigation (buttons or Left/Right arrow keys), **Then** navigation behaves exactly as it did before this feature. **Given** a popup is open, **When** the user presses a day-navigation shortcut, **Then** the viewed day does not change until the popup is closed, after which day-navigation resumes normally.
5. **Given** the day view has keyboard focus, **When** the user reaches and activates the persistent "Add block" control, **Then** the creation popup opens directly with an adjustable default time, without the user needing to tab through individual grid slots first.

---

### Edge Cases

- A block spans midnight in both directions relevant to the viewed day: it starts the previous day and is still ongoing at 00:00 of the viewed day, or it starts during the viewed day and continues past 24:00 into the next — both cases must clamp to the viewed day's edge and show a continuation indicator.
- Two blocks are exactly adjacent (one ends exactly when the next starts) — the grid must render them with a visible boundary but no gap and no overlap artifact.
- A click lands exactly on the boundary between an empty slot and an existing block, or between two adjacent blocks — the system must unambiguously resolve it to one target (an existing block takes precedence over an empty-slot click at the same point) and open the corresponding popup.
- The default 1-hour duration from a slot click would overlap an existing block or push past the reachable horizon — the existing rejection rule applies and the mapped message is shown in the popup; the user can still adjust the times and retry without losing the popup state.
- A day has zero free space (fully booked) — the grid renders correctly with no clickable empty area, and existing blocks remain individually clickable.
- The viewed day is at the edge of the reachable horizon (the earliest or latest navigable day) — the grid and popups function identically to any other in-horizon day; only day-to-day navigation is bounded, as today.
- Rapid, repeated activation of different slots or blocks before a popup finishes opening or closing — only one popup can be open at a time, and activating a new target closes any popup already open for a previous target.
- A block's edit or delete fails because another view already changed or removed it (stale state) — the popup surfaces the existing mapped error message and the grid reloads to the real state, exactly as the existing list view already does for this case.
- The user accidentally clicks outside a popup with unfilled or partially-edited changes still in it — the popup closes but its draft content is not lost: reopening the popup for the same slot or block, while still on the same day, restores what was entered; changing day or successfully saving a block clears that draft.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The day view MUST render a vertical, time-proportional 24-hour grid (00:00–24:00) with hour labels and gridlines, replacing the current flat list of blocks and "Free" entries.
- **FR-002**: Each time block MUST be positioned vertically and sized in height in proportion to its start time and duration within the 24-hour grid.
- **FR-003**: The grid MUST visually distinguish routine, constrained, and planned-activity blocks from one another without requiring the user to open any block.
- **FR-004**: A block whose range extends before 00:00 or past 24:00 of the viewed day MUST be rendered clamped to that day's edge, with a visual indicator on both affected days showing that the block continues onto the adjacent day.
- **FR-005**: A block whose rendered height would otherwise be too small to comfortably show its label MUST remain individually identifiable and clickable, via a compact label, an accessible tooltip, or both.
- **FR-006**: When the day being viewed is the current day, the grid MUST display a current-time indicator line at the position corresponding to the current time; this indicator MUST NOT appear when viewing any other day.
- **FR-007**: Free time MUST be represented purely as empty grid space; the view MUST NOT render explicit "Free" entries or labels.
- **FR-008**: Clicking (or otherwise activating) an empty area of the grid MUST open a creation popup with the start time pre-filled to the nearest 15-minute mark corresponding to the activated position and the end time defaulted to 1 hour after that start time.
- **FR-009**: The creation popup MUST allow the user to adjust both the start and end time in 5-minute increments before confirming.
- **FR-010**: The creation popup MUST offer all three existing block types (routine, constrained, planned activity) and, when planned activity is selected, MUST show the activity selector listing each activity's remaining time for the viewed day, consistent with existing behavior.
- **FR-011**: Clicking (or otherwise activating) an existing block MUST open a details popup showing that block's type, time range, and name or associated activity.
- **FR-012**: The details popup MUST offer an edit action that allows changing the block's time range (and name, where applicable) and a delete action.
- **FR-013**: Deleting a block that is one of several same-day fragments of the same activity MUST present the existing choice between deleting only that fragment and deleting every fragment of that activity for the day, before any deletion occurs; deleting a block that is its activity's only fragment that day MUST proceed immediately without that extra choice, exactly as today.
- **FR-014**: All existing business rules — overlap rejection, the two-week planning horizon, 5-minute time granularity, and same-activity fragment merging — MUST continue to apply unchanged to blocks created or edited from the grid.
- **FR-015**: When an action taken from the creation or details popup is rejected by a business rule, the popup MUST show the existing mapped, user-facing error message (never a raw backend message) without closing, so the user can adjust and retry.
- **FR-016**: When an edit or delete action fails because the target block or activity was already changed elsewhere (stale state), the system MUST show the existing mapped error message and refresh the grid to the real state, leaving no partially-applied change visible.
- **FR-017**: Both the creation and details popups MUST behave as accessible dialogs: keyboard focus MUST be trapped within the open popup, the Escape key MUST close the popup without applying unsaved changes, and focus MUST return to the element that triggered the popup when it closes.
- **FR-018**: The system MUST provide a keyboard-only path to reach an empty slot in the grid and activate it to open the creation popup; creating a block MUST NOT require a mouse. This MUST include a persistent, keyboard-reachable "Add block" control that opens the creation popup directly with an adjustable default time, so a keyboard user is not limited to tabbing through the grid slot-by-slot as the only way to create a block.
- **FR-019**: Existing day-to-day navigation (previous/next buttons and Left/Right arrow keys) MUST continue to behave exactly as before this feature when no popup is open. While a creation or details popup is open, day-to-day navigation MUST be suspended (the viewed day stays fixed) and MUST resume automatically once the popup closes.
- **FR-020**: The system MUST support opening only one popup (creation or details) at a time; activating a new slot or block while a popup is open MUST close the previous popup before opening the new one.
- **FR-021**: When the day view is opened, the grid MUST be scrolled to the current time if the viewed day is today, or to the start of the day (00:00) if the viewed day is any other day.
- **FR-022**: A block MUST be treated as "very short" for the purposes of FR-005 when its duration is at or below the system's minimum time granularity (5 minutes).
- **FR-023**: Keyboard navigation across the grid (for the purposes of FR-018) MUST move the focused, activatable slot in 5-minute increments, matching the finest editable time granularity.
- **FR-024**: Clicking outside an open popup (on its backdrop) MUST close the popup. Unlike Escape (FR-017), which discards unsaved changes immediately, a backdrop-click dismissal MUST retain the popup's current unsaved field values in a temporary draft, scoped to the specific slot or block the popup was opened for; reopening that same slot's or block's popup while still viewing the same day MUST restore the retained draft. The draft MUST be discarded when the user navigates to a different day or when a block is successfully created or saved.
- **FR-025**: While the viewed day's data is being fetched, the view MUST communicate a loading state to the user; if the fetch fails, the view MUST communicate that the day could not be loaded — preserving the existing loading and error messaging, regardless of whether it is shown in place of the grid or as an overlay on it.

### Key Entities

- **Time Block**: An existing entity (routine, constrained, or planned-activity) with a start time, end time, type, and — for planned activities — an associated activity; this feature changes only how blocks are visualized and interacted with, not their data or the rules governing them.
- **Activity**: An existing entity from the backlog that can be linked to a planned-activity block; this feature reuses the existing per-day remaining-time calculation when offering the activity selector inside the creation popup.
- **Grid Slot**: A conceptual, non-persisted position on the visual grid corresponding to a point in time on the viewed day; used only to determine what start time to pre-fill when an empty area is activated to create a block.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can determine how much free time remains in a day, and roughly where it falls, within a couple of seconds of opening the day view, without reading any text list.
- **SC-002**: All three block types are visually distinguishable from one another in the grid in 100% of cases, without opening any block.
- **SC-003**: A user can create a new block, from clicking an empty slot to the block appearing on the grid, in 4 interactions or fewer for the default case (activate slot, choose type, confirm, done).
- **SC-004**: Every planning action available in the previous list-based day view (create, edit, delete a block, including the multi-fragment delete choice) remains fully possible from the new grid view, with zero loss of capability.
- **SC-005**: A keyboard-only user can complete both "create a block" and "delete a block" end-to-end without using a mouse, in 100% of attempts.
- **SC-006**: A block spanning midnight is correctly identified by users, on both affected days, as continuing onto the adjacent day, without needing to open it, in usability review.
- **SC-007**: Blocks below the "very short" threshold remain individually identifiable and clickable in 100% of cases, with no accidental activation of the wrong block.

## Assumptions

- The existing three block types, their business rules (overlap rejection, 5-minute granularity, two-week horizon, same-activity fragment merging), and the existing REST endpoints for creating, editing, deleting, and listing blocks for a day are unchanged and sufficient for this feature; no new backend capability is introduced.
- The existing "move" endpoint referenced for a future drag-and-drop feature is not invoked by this feature, since drag-and-drop is explicitly out of scope here.
- The Backlog and Routine Template pages are unaffected by this feature; only the single-day view's presentation and interaction model change.
- This is a personal, single-user scheduler (per the project's constitution); the existing stale-state error handling (mapped error message plus reload) is reused as-is for popups and is not being redesigned by this feature.
- The grid is designed for a desktop/pointer-and-keyboard-capable browser; per the stated out-of-scope items, mobile-specific gestures, a week view, and zoom levels are not addressed, though the grid is not expected to be literally unusable on a smaller screen.
- Week view and zoom levels do not exist yet in this project and are not introduced by this feature.
