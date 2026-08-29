# API Contract Changes: Multi-Block, Multi-Day Activity Planning

This document describes only what changes relative to
`specs/001-daily-planning-core/contracts/api.md`, which remains the
baseline for every endpoint, error, and convention not mentioned here
(`GET /api/horizon`, `GET /api/days/{date}`'s general shape, routine
template endpoints, and the `{reason, message}` error body shape are all
unchanged).

## Error Conventions — additions

| Case | Status | `reason` |
|---|---|---|
| A `PLANNED_ACTIVITY` block's range would span midnight (FR-021) | 400 Bad Request | `PLANNED_ACTIVITY_SPANS_MIDNIGHT` |

**Removed** from the 001 table: `ACTIVITY_NOT_AVAILABLE`'s "already
planned elsewhere" case — creating/moving a `PLANNED_ACTIVITY` block no
longer requires the target activity to be `UNPLANNED` (FR-001). The
`ACTIVITY_NOT_AVAILABLE`/409 case for a missing or nonexistent
`activityId` is unchanged.

**Renamed**: `ACTIVITY_CURRENTLY_PLANNED` → `ACTIVITY_HAS_PLANNED_FRAGMENTS`
(same 409 status, same trigger shape — see `DELETE /api/activities/{id}`
below — renamed because "currently planned" no longer describes a single
block).

## Activities

### `GET /api/activities` *(response shape changed, breaking)*

The `status` field and `?status=unplanned|planned` query filter from
feature 001 are **removed** (plan.md Complexity Tracking: a global status
cannot represent per-day planning). Replaced by:

**200 OK** — no `day` query parameter (backlog / aggregate view, FR-012–FR-013):
```json
[
  {
    "id": "uuid",
    "name": "string",
    "estimatedDurationMinutes": 30,
    "priority": "LOW | MEDIUM | HIGH",
    "category": "string | null",
    "totalFragmentCount": 5,
    "plannedDayCount": 3,
    "days": [
      { "day": "YYYY-MM-DD", "plannedMinutes": 40, "status": "PARTIALLY_PLANNED | PLANNED" }
    ]
  }
]
```
`days` is sparse: only reachable-horizon days with at least one fragment
appear (never `UNPLANNED`, data-model.md `ActivityPlanningSummary`).

**200 OK** — with `?day=YYYY-MM-DD` (day view's activity selector, FR-010–FR-011):
same array, each activity additionally carrying:
```json
{
  "remainingMinutesForDay": 20,
  "dayStatus": "UNPLANNED | PARTIALLY_PLANNED | PLANNED"
}
```
`remainingMinutesForDay` floors at `0` once `dayStatus` is `PLANNED`
(spec.md Assumptions), computed only for the requested `day`; `days`/
`totalFragmentCount`/`plannedDayCount` are still present and unchanged
(the aggregate view and the single-day view are not mutually exclusive —
both are always computed).

**400 Bad Request** — `INVALID_REQUEST`: `day` is present but not a valid
`YYYY-MM-DD` date.

### `DELETE /api/activities/{id}` *(trigger condition and message changed)*

Same request shape (`confirm` query param, default `false`) and same
204/404 cases as feature 001.

- If `totalFragmentCount == 0`: deletes immediately. **204 No Content**.
- If `totalFragmentCount > 0` and `confirm=false`: **409 Conflict**, body
  `{ "reason": "ACTIVITY_HAS_PLANNED_FRAGMENTS", "message": "..." }`,
  where `message` states the exact total fragment count across all days
  (FR-016) — e.g. `"Activity has 5 planned fragments across 3 days;
  pass confirm=true to delete all of them."`
- If `totalFragmentCount > 0` and `confirm=true`: deletes the activity
  **and every one of its fragments, across every day**. **204 No Content**.

## Time Blocks

### `POST /api/days/{date}/blocks` *(behavior changed)*

Same request/response shape as feature 001. Changes:

- `activityId` for a `PLANNED_ACTIVITY` block now only needs to reference
  an *existing* activity — it no longer needs to be currently
  `UNPLANNED` (FR-001). The `ACTIVITY_NOT_AVAILABLE` 409 case now fires
  only for a missing/nonexistent `activityId`.
- If `type = PLANNED_ACTIVITY` and the resulting `[startTime, endTime)`
  would span midnight: **400 Bad Request** —
  `PLANNED_ACTIVITY_SPANS_MIDNIGHT`, instead of being created (FR-021).
- If `type = PLANNED_ACTIVITY` and the range touches or overlaps one or
  more existing `PLANNED_ACTIVITY` blocks with the **same** `activityId`
  on the **same** day: instead of `TIME_BLOCK_OVERLAP`, those blocks are
  merged with the new one (FR-005–FR-007). **201 Created** still returns
  a single block — the merged range, not the raw requested range. Overlap
  with a block of a *different* `activityId`, or of type
  `ROUTINE`/`CONSTRAINED`, still returns `TIME_BLOCK_OVERLAP` (FR-008,
  unchanged).

### `PUT /api/blocks/{id}` *(behavior changed)*

Same request/response shape. Same merge behavior as `POST` above applies
when the edited range touches/overlaps another same-activity,
same-day fragment. Same new `PLANNED_ACTIVITY_SPANS_MIDNIGHT` 400 case.

### `PATCH /api/blocks/{id}/move` *(behavior changed)*

Same request/response shape — `day` may already differ from the block's
current day, as in feature 001. Changes:

- Merge behavior (as above) is now evaluated against the **destination**
  day's fragments only, never the origin day's (FR-022, spec.md Edge
  Cases). Moving a fragment away from a day never affects any other
  fragment left behind there.
- Same new `PLANNED_ACTIVITY_SPANS_MIDNIGHT` 400 case as `POST`/`PUT`.

### `DELETE /api/blocks/{id}` *(new query parameter)*

**Request**: query param `scope` (optional, `self` | `activityDay`,
default `self`).

- `scope=self` (default): deletes only this block — same as feature 001's
  unconditional delete. **204 No Content**.
- `scope=activityDay`: deletes this block **and** every other
  `PLANNED_ACTIVITY` block sharing its `activityId` and day, in one
  operation (FR-015). **204 No Content**.
- `scope` present but not `self`/`activityDay`: **400 Bad Request** —
  `INVALID_REQUEST`.
- **404 Not Found** — `TIME_BLOCK_NOT_FOUND`: `id` doesn't exist (unchanged).

Choosing which prompt to show (none, per FR-014, vs. the two-option
choice, per FR-015) is a frontend-only decision based on same-activity,
same-day fragments already present in the loaded day view — this endpoint
does not itself require confirmation for either scope value (unlike
`DELETE /api/activities/{id}` above, which does).
