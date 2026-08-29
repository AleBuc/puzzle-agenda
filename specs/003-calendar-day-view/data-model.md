# Phase 1 Data Model: Calendar-Style Day Grid View

This feature introduces **no backend/persistence entities**. It consumes the existing `Time Block` and `Activity` REST shapes unchanged (see `contracts/api-usage.md`) and introduces only **frontend view-model / interaction-state shapes**, none of which are persisted beyond the current page session.

## Reused entities (unchanged)

### Time Block (from `GET /api/days/{date}`)

```text
{
  id: uuid
  type: "ROUTINE" | "CONSTRAINED" | "PLANNED_ACTIVITY"
  startTime: "HH:mm"
  endTime: "HH:mm"
  endsNextDay: boolean       // relative to the requested date, not a fixed block property
  startsPreviousDay: boolean // relative to the requested date, not a fixed block property
  name: string | null        // unused (null) for PLANNED_ACTIVITY
  activityId: uuid | null
  activityName: string | null // populated only for PLANNED_ACTIVITY
}
```

**Design decision (changed from the list view)**: a block whose `startsPreviousDay`
is `true` (the viewed day is the spillover day, not the block's start day) IS
clickable, but opens the details popup in **read-only mode**: no edit or delete
actions, and a notice "Starts on {date} — edit it from that day" with a link that
closes the popup and navigates to the block's start day. Confirmed against
`TimeBlockResponseAssembler`/`TimeBlockResponse` (backend/infrastructure): `startTime`
and `endTime` are always the block's real local times regardless of which day is
being viewed — they are never clamped to `00:00`/`24:00` in the API response (only
`startsPreviousDay`/`endsNextDay` are viewed-day-relative). So the read-only popup
always shows the block's real start time (e.g. "Starts 22:30 on {date}"); no backend
change needed, and no "Continues from" fallback is required. Rationale: in a
proportional grid an inert multi-hour block reads as a bug; read-only details with
a redirect is the minimal interactive treatment that stays honest about where the
block lives. Full edit-from-spillover remains a possible future enhancement.

### Activity (from `GET /api/activities?day={date}`)

```text
{
  id: uuid
  name: string
  estimatedDurationMinutes: number
  priority: "LOW" | "MEDIUM" | "HIGH"
  category: string | null
  totalFragmentCount: number
  plannedDayCount: number
  days: [...]                        // unused by this feature
  remainingMinutesForDay: number     // floors at 0 once fully planned; computed for the viewed day
  dayStatus: "UNPLANNED" | "PARTIALLY_PLANNED" | "PLANNED"
}
```

No changes. Reused exactly as `DayView.vue` already consumes it for its activity selector (`activityOptionLabel`).

## New frontend view-models

### GridPositionedBlock (derived, non-persisted)

Computed once per render from `day.blocks`, replacing `DayTimeline.vue`'s `timeline` computed (which interleaved `block`/`gap` items for a flex list). One entry per block — there is no `gap` kind, since free time is simply unrendered space (FR-007).

```text
{
  block: TimeBlock             // the source block, untouched
  topPercent: number           // minutesToPercent(effectiveStart(block))
  heightPercent: number        // minutesToPercent(effectiveEnd(block)) - topPercent, floored to a
                                // visible minimum so a 5-minute block never renders at ~0.35%
                                // height with zero visible area (see FR-005/FR-022)
  isVeryShort: boolean         // (effectiveEnd - effectiveStart) <= 5 minutes (FR-022)
  isContinuationOnly: boolean  // opens details popup in read-only mode (see above)
}
```

Produced by a pure function in `time-grid-utils.js` (e.g. `layoutBlocks(blocks)`), unit-tested independently of any component (`time-grid-utils.spec.js`).

### GridSlot (conceptual, non-persisted — spec's Key Entities)

Not a stored object; a slot is simply a point in time derived at the moment of activation:

- **Pointer activation**: the clicked pixel's vertical offset within the grid container, converted to a percentage, then to minutes, then snapped down to the nearest 15-minute mark (FR-008).
- **Keyboard activation**: the roving-tabindex focus index (`0..287`, one per 5-minute increment across the day, FR-023) converted directly to `HH:mm` (`index * 5` minutes).

Either path produces the same shape handed to `BlockPopup.vue` when opening in creation mode: `{ startTime: "HH:mm" }` (end time defaults to `startTime + 1 hour` per FR-008, independent of activation method).

### PopupState (interaction state, held in `DayView.vue`)

Enforces FR-020 (only one popup open at a time) as a single reactive value rather than two independent booleans:

```text
null                                                   // no popup open
| { mode: "create", startTime: "HH:mm" }               // creation popup, pre-filled start
| { mode: "details", block: TimeBlock, readOnly: boolean }
   // readOnly = true when opened from a spillover rendering; no edit/delete,
   // shows a "starts on {date}" notice linking to the start day
```

Setting `PopupState` to a new value while non-null implicitly closes whatever was open before opening the new one (FR-020) — there is no separate "close current, then open next" step to get wrong.

### BlockDraft (per-day draft cache — `useBlockDraft(dateRef)`)

A single plain `ref`, not a store (Constitution Principle V), owned by `DayView.vue` via the new composable:

```text
{
  day: "YYYY-MM-DD"           // the day this draft belongs to
  type: "ROUTINE" | "CONSTRAINED" | "PLANNED_ACTIVITY"
  name: string | null
  activityId: uuid | null
  durationMinutes: number     // preserved across slot changes; end time is recomputed from the
                               // newly-clicked slot's start + this duration
} | null
```

**Lifecycle** (per plan input §2, refining `/speckit-clarify` Q2 — see `research.md` §4):
- Set when the creation popup is dismissed via backdrop click (FR-024), capturing whatever was currently filled in.
- Read (and the popup pre-filled from it) when a new empty slot on the **same day** is activated while a draft exists for that day; the draft's `type`/`name`/`activityId`/`durationMinutes` are reused, but the popup's `startTime` always comes from the newly-activated slot, not the draft.
- Cleared: on Escape, on the popup's own close control, on a successful create/save, or when `dateRef` changes (watched, mirroring `useDaySchedule`'s existing day-change watchers).

This shape only ever holds *creation*-popup content; a details/edit popup dismissed via backdrop click simply closes without a draft (there is nothing to "recreate" for an edit — the block itself is unchanged on the backend, which is the discard behavior FR-016/FR-024 already imply for a no-op dismissal of an edit in progress).

## State transitions

```text
PopupState: null → {mode:"create", ...} → null            (submit success, Escape, or close button)
                                        → null (draft set) (backdrop click)
PopupState: null → {mode:"details", readOnly:false} → null (Escape, close button, or successful
                                                            edit/delete)
                                                    → null (no draft) (backdrop click)
PopupState: null → {mode:"details", readOnly:true} → null  (Escape, close, backdrop — never a
                                                            draft; or the "go to start day" link,
                                                            which closes the popup then changes
                                                            dateRef — the day-change watcher then
                                                            purges any existing draft as usual)
BlockDraft:  null → {...}  (backdrop click while creation popup open)
             {...} → null  (Escape / close button / successful create / day change)
             {...} → {...} (re-opening creation popup on same day: startTime replaced, rest kept)
```

No backend state machine is introduced or changed; `Time Block`/`Activity` lifecycle rules (creation, edit, single-fragment vs. multi-fragment deletion, fragment merging) are entirely the existing ones, invoked exactly as `DayView.vue` already invokes them via `useDaySchedule`.