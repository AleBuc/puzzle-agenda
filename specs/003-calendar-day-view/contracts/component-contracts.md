# Component Contracts: Calendar-Style Day Grid View

Since this feature introduces no backend interface (`api-usage.md`), its real "interface" is the boundary between the new/changed Vue components. This is the contract implementation and tests are written against.

## `time-grid-utils.js` (pure functions, no Vue dependency)

| Export | Signature | Purpose |
|---|---|---|
| `toMinutes` | `(hhmm: "HH:mm") => number` | Existing helper, extracted from `DayTimeline.vue` unchanged. |
| `formatMinutes` | `(minutes: number) => "HH:mm"` | Existing helper, extracted unchanged (supports `"24:00"`, no wraparound). |
| `effectiveStart` | `(block: TimeBlock) => number` | Existing helper, extracted unchanged: `0` if `startsPreviousDay`, else `toMinutes(block.startTime)`. |
| `effectiveEnd` | `(block: TimeBlock) => number` | New, symmetric to `effectiveStart`: `1440` if `endsNextDay`, else `toMinutes(block.endTime)`. |
| `minutesToPercent` | `(minutes: number) => number` | New: `(minutes / 1440) * 100`. Used for both a block's `top` and `height`. |
| `snapDownToQuarterHour` | `(minutes: number) => number` | New: floors to the nearest 15-minute mark at or before `minutes` (FR-008). |
| `snapToFiveMinutes` | `(minutes: number) => number` | New: rounds to the nearest 5-minute mark (FR-009 popup adjustment, FR-023 keyboard step). |
| `layoutBlocks` | `(blocks: TimeBlock[]) => GridPositionedBlock[]` | New: maps each block through `effectiveStart`/`effectiveEnd`/`minutesToPercent` into the `data-model.md` `GridPositionedBlock` shape, including `isVeryShort` (FR-022) and `isContinuationOnly` (`startsPreviousDay`). |

No default export; all named exports, matching `date-utils.js`'s existing style.

## `useBlockDraft(dateRef)` (composable)

```text
useBlockDraft(dateRef: Ref<string>) => {
  draft: Ref<BlockDraft | null>,
  captureDraft(fields: { type, name, activityId, durationMinutes }): void,
  clearDraft(): void,
}
```

- `captureDraft` sets `draft.value` to `{ day: dateRef.value, ...fields }` — called by `DayView.vue` when `BlockPopup` emits `closed` with `reason: "backdrop"` and a snapshot.
- `clearDraft` sets `draft.value = null` — called on `closed` with `reason: "escape" | "close-button" | "navigate-to-start-day"`, on a successful `submit-create`, and automatically via an internal `watch(dateRef, clearDraft)` (mirrors `useDaySchedule`'s day-change watchers).
- Owns no API calls and no knowledge of `PopupState` — single responsibility, per `data-model.md`.

## `DayGrid.vue`

**Props**:

| Prop | Type | Notes |
|---|---|---|
| `date` | `String` (required) | The viewed ISO date; used internally (via `todayIsoDate()`) to decide whether to render the current-time indicator (FR-006) and to pick the today-vs-other-day default for the initial scroll position (FR-021) and the "Add block" control's default time (research.md §6). |
| `blocks` | `Array<TimeBlock>` (default `[]`) | Passed through `layoutBlocks` internally (or the parent may pass pre-computed `GridPositionedBlock[]`; either is acceptable — the important contract is the emitted events below). |

**Emits**:

| Event | Payload | Fired when |
|---|---|---|
| `activate-slot` | `{ startTime: "HH:mm" }` | An empty grid area is clicked, or a keyboard-focused empty slot is activated (Enter/Space), or the persistent "Add block" control is activated. `startTime` is already snapped (15-minute for pointer, per FR-008; the roving-tabindex position's own 5-minute granularity for keyboard, per FR-023). |
| `activate-block` | `TimeBlock` | An existing block is clicked or keyboard-activated. For a block rendered with `isContinuationOnly: true`, the event is still emitted — `DayView.vue` opens the details popup in read-only mode for it (see `data-model.md`). |

`DayGrid.vue` owns: rendering gridlines/hour labels, positioning `GridBlock` children, the current-time indicator, the roving-tabindex keyboard model across empty slots, and the persistent "Add block" control. It owns no popup state and makes no API calls — `DayView.vue` reacts to its two events to set `PopupState`.

## `GridBlock.vue`

**Props**: `positioned: GridPositionedBlock` (required; see `data-model.md`).

**Emits**: `activate` with payload `positioned.block` — emitted for every block, including `isContinuationOnly` ones (whose activation opens the read-only popup).

Owns: per-type styling (reusing `TimeBlockCard.vue`'s existing color scheme — routine/constrained/planned-activity), the compact-label/tooltip treatment for `isVeryShort` blocks (FR-005), and the continuation-indicator markup for a midnight-clamped block (FR-004), whether or not it is the block's start-day representation.

## `BlockPopup.vue`

**Props**:

| Prop | Type | Notes |
|---|---|---|
| `popupState` | `PopupState` (`null \| {mode:"create",...} \| {mode:"details", block, readOnly, sameDayFragmentCount}`, see `data-model.md`) | `null` keeps the Reka `DialogRoot`'s `open` false. `sameDayFragmentCount` lets the popup itself decide, without needing the day's full block list, whether Delete acts immediately (`=== 1`) or shows the in-place scope choice (`> 1`). |
| `dayActivities` | `Array<Activity>` | For the creation popup's activity selector (FR-010), unchanged shape from `DayView.vue`'s existing `dayActivities`. |
| `draft` | `BlockDraft \| null` | Used only when `popupState.mode === "create"`, to pre-fill `type`/`name`/`activityId`/duration on open (`startTime` always comes from `popupState.startTime`, never the draft — see `data-model.md`). |
| `errorMessage` | `String \| null` | Mapped, display-ready error text from the most recent failed action (FR-015); shown inside the popup without closing it. |
| `date` | `String` (the viewed ISO date) | Used only in read-only details mode, to compute and display the block's real start day (`shiftIsoDate(date, -1)` — a spillover block always starts exactly one calendar day before the day it spills into, since no block spans more than one midnight) in the "Starts {time} on {day}" notice. |

**Emits**:

| Event | Payload | Fired when | Handled by `DayView.vue` as |
|---|---|---|---|
| `submit-create` | `{ type, startTime, endTime, name, activityId }` | The creation form is confirmed | Calls `useDaySchedule.createBlock`; on success, clears `PopupState` and the draft; on failure, sets `errorMessage` and leaves the popup open (FR-015). |
| `submit-edit` | `{ id, startTime, endTime, name }` | The details popup's edit form is confirmed | Calls `useDaySchedule.editBlock`; same success/failure handling as above. |
| `submit-delete` | `{ id, scope: "self" \| "activityDay" }` | Delete is confirmed (immediately for a single fragment, or after the in-place multi-fragment choice for several — FR-013) | Calls `useDaySchedule.deleteBlock`; on failure, sets `errorMessage` and reloads the day in the background (FR-016), but keeps the popup open — same pattern as `submit-create`/`submit-edit` — since closing it would hide the very error message just shown. |
| `closed` | `{ reason: "escape" \| "close-button" \| "backdrop" \| "navigate-to-start-day", snapshot?: { type, name, activityId, durationMinutes } }` | The dialog closes without a successful submit | `reason: "backdrop"` (create mode only, with unsaved content) → `captureDraft(snapshot)`; any other reason → `clearDraft()`. In all cases, `PopupState` is set to `null`. For `reason: "navigate-to-start-day"`, `DayView.vue` additionally navigates to the block's start day (`shiftIsoDate(date, -1)`) after clearing `PopupState`. |

In read-only details mode (`popupState.readOnly === true`), the edit form and delete actions are not rendered; instead the popup shows a "Starts {block.startTime} on {date} — edit it from that day" notice with a "go to start day" link. `block.startTime`/`block.endTime` are always the block's real local times (confirmed against `TimeBlockResponseAssembler`/`TimeBlockResponse` — never clamped to `00:00`/`24:00` in the API response), so no fallback text is needed. Activating the link emits `closed` with `reason: "navigate-to-start-day"` — the popup itself has no knowledge of the router; `DayView.vue` owns the close-then-navigate sequencing (the day-change watcher then purges any draft, per `data-model.md`'s state transitions).

Internally wraps `reka-ui`'s `DialogRoot`/`DialogPortal`/`DialogOverlay`/`DialogContent`/`DialogTitle`/`DialogClose`, bound via `:open="popupState !== null"` and reacting to Escape (built-in, emits `closed` with `reason: "escape"`) and `@pointer-down-outside` (emits `closed` with `reason: "backdrop"` and, in create mode, a `snapshot` of the current unsaved fields). The multi-fragment delete choice (FR-013) is rendered as in-place content inside the same `DialogContent` — never a second, nested dialog — toggled by local component state.

## `DayView.vue` (integration point — not a new contract, but its responsibilities change)

Owns (unchanged from today): `useDaySchedule`, `horizon`, `dayActivities`, the day-navigation keydown handler.

Owns (new, per this feature): `PopupState` (a local `ref`), `useBlockDraft(dateRef)`, the suspension of day-navigation shortcuts while `PopupState !== null` (FR-019/`/speckit-clarify` Q1) — implemented as an added guard at the top of the existing `handleKeydown`, alongside its current form-control guard — plus opening the details popup with `readOnly: true` when `activate-block` carries a block whose `startsPreviousDay` is true, and handling the `navigate-to-start-day` close reason (clear `PopupState`, then navigate to the block's start day).

Renders `<DayGrid>` in place of `<DayTimeline>`, and `<BlockPopup>` in place of the current inline `<form class="day-view__form">` and `<div class="day-view__confirm">` blocks (both retired).