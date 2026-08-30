# API Usage Contract: Calendar-Style Day Grid View

**No backend changes. No new endpoints. No changed request/response shapes.** This feature is a frontend-only presentation-and-interaction change. This document exists to make that scope guard explicit and traceable — the endpoints below are the complete set this feature consumes, each already implemented and documented in `specs/001-daily-planning-core/contracts/api.md` and `specs/002-multi-day-activity-planning/contracts/api.md`, which remain the source of truth for exact request/response shapes and error codes.

## Endpoints consumed (unchanged)

| Endpoint | Used for | Consumed by (new/changed component) |
|---|---|---|
| `GET /api/horizon` | Bounding day-to-day navigation and the reachable range for slot/popup validation | `DayView.vue` (unchanged call site) |
| `GET /api/days/{date}` | Loading the viewed day's blocks to lay out on the grid | `DayView.vue` via `useDaySchedule` (unchanged call site) — feeds `GridPositionedBlock` computation (`data-model.md`) |
| `GET /api/activities?day={date}` | Populating the creation popup's activity selector with per-day remaining time | `DayView.vue` (unchanged call site) — feeds `BlockPopup.vue`'s activity selector |
| `POST /api/days/{date}/blocks` | Creating a block from the creation popup (empty-slot or "Add block" control) | `useDaySchedule.createBlock` (unchanged), invoked from `BlockPopup.vue`'s submit instead of `DayView.vue`'s inline form |
| `PUT /api/blocks/{id}` | Saving an edit from the details popup | `useDaySchedule.editBlock` (unchanged), invoked from `BlockPopup.vue` |
| `DELETE /api/blocks/{id}?scope={self\|activityDay}` | Deleting a block from the details popup, including the multi-fragment scope choice | `useDaySchedule.deleteBlock` (unchanged), invoked from `BlockPopup.vue`'s in-place confirmation, not a separate page-level confirm block |

## Error handling contract (unchanged)

Every rejection from the endpoints above continues to return `{ reason, message }` per the existing Error Conventions. This feature routes every such error through the existing `frontend/src/api/errorMessages.js` dictionary (`resolveErrorMessage(err.reason)`) exactly as `DayView.vue` already does — `err.message` (the raw backend string) is never shown in a popup, matching FR-015/FR-016. No new error codes are introduced; no existing one changes meaning.

## Explicitly not used by this feature

- `PATCH /api/blocks/{id}/move` — exists (feature 002) for a future drag-and-drop feature; not invoked here (spec Assumptions, out-of-scope list).
- All Backlog (`/api/activities` CRUD beyond the `?day=` read) and Routine Template (`/api/routine-template/entries`) endpoints — those pages are unaffected by this feature.
