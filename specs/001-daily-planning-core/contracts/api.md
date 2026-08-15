# API Contract: Daily Schedule Planning

REST API exposed by `infrastructure/rest` to the frontend, per Constitution
Principle IV (consistent REST conventions, meaningful status codes). All
request/response bodies are JSON. Dates are ISO-8601 (`YYYY-MM-DD`); times
of day are `HH:mm` (5-minute increments only, per FR-006/FR-015).

## Error Conventions

Every business-rule rejection returns a JSON body
`{ "reason": "CODE", "message": "human-readable explanation" }`. There are
six distinct business error cases across this API, each with its own
status code — endpoints below reference this table rather than repeating
it:

| Case | Status | `reason` |
|---|---|---|
| A new/edited time range overlaps an existing block on the same day (FR-008) | 409 Conflict | `TIME_BLOCK_OVERLAP` |
| A new/edited routine template entry overlaps an existing entry, using the two-day projection rule (FR-016) | 409 Conflict | `TEMPLATE_ENTRY_OVERLAP` |
| A block's, move's, or day-view's target date is later than `forwardBound` (`today + 13 days`) — a syntactically valid date the horizon does not yet reach (FR-009) | 422 Unprocessable Entity | `DAY_BEYOND_FORWARD_HORIZON` |
| A target date is earlier than `HorizonState.day1` — the day does not exist for this user (FR-009, spec Edge Cases) | 404 Not Found | `DAY_NOT_REACHABLE` |
| A `startTime`/`endTime` is not a 5-minute increment, or produces a zero-length range (FR-006, FR-015) | 400 Bad Request | `INVALID_TIME_GRANULARITY` |
| A `PLANNED_ACTIVITY` block references an `activityId` that is not currently in the unplanned backlog (already planned elsewhere, or nonexistent) (FR-007) | 409 Conflict | `ACTIVITY_NOT_AVAILABLE` |
| An activity that is currently planned (has a scheduled block) is deleted without `confirm=true` (FR-005) | 409 Conflict | `ACTIVITY_CURRENTLY_PLANNED` |

`DAY_BEYOND_FORWARD_HORIZON` and `DAY_NOT_REACHABLE` are deliberately
distinct: a date beyond the forward bound is a *valid future calendar
date the reachable window hasn't grown to yet* (422 — the request is
well-formed but not currently processable), while a date before Day 1
*does not exist as a concept for this user* (404 — per spec Edge Cases,
"the day is treated as non-existent").

Every endpoint below also names its `reason` for the plainer, non-business
cases that aren't in the table above — a referenced id not existing
(`ACTIVITY_NOT_FOUND`, `TIME_BLOCK_NOT_FOUND`,
`ROUTINE_TEMPLATE_ENTRY_NOT_FOUND`) and generic request-shape validation
failures (`INVALID_REQUEST`) — since the response body always carries a
`reason`, even outside these six named business rules.

## Activities

### `GET /api/activities`

Query param `status` (optional): `unplanned` | `planned`. Omitted returns
all activities.

**200 OK**
```json
[
  {
    "id": "uuid",
    "name": "string",
    "estimatedDurationMinutes": 30,
    "priority": "LOW | MEDIUM | HIGH",
    "category": "string | null",
    "status": "UNPLANNED | PLANNED"
  }
]
```
**400 Bad Request** — `INVALID_REQUEST`: `status` is present but not
`unplanned` or `planned`.

### `POST /api/activities`

**Request**
```json
{
  "name": "string",
  "estimatedDurationMinutes": 30,
  "priority": "LOW | MEDIUM | HIGH",
  "category": "string | null"
}
```

**201 Created** — body: the created Activity (see shape above).
**400 Bad Request** — `INVALID_REQUEST`: validation failure (blank name,
non-positive duration); an invalid `priority` value fails JSON
deserialization before reaching validation and gets a generic 400 from
Spring, not this `{reason, message}` shape.

### `PUT /api/activities/{id}`

Same request/response shape as `POST`. **200 OK** on success. **400 Bad
Request** — `INVALID_REQUEST`: same validation failures as `POST` (blank
name, non-positive duration). **404 Not Found** — `ACTIVITY_NOT_FOUND`:
`id` doesn't exist.

### `DELETE /api/activities/{id}`

Query param `confirm` (optional boolean, default `false`).

- If the activity is `UNPLANNED`: deletes immediately. **204 No Content**.
- If the activity is `PLANNED` and `confirm=false`: **409 Conflict**,
  body `{ "reason": "ACTIVITY_CURRENTLY_PLANNED", "message": "..." }`
  (FR-005 — confirmation required).
- If the activity is `PLANNED` and `confirm=true`: deletes the activity
  **and** its scheduled `TimeBlock`. **204 No Content**.
- **404 Not Found** — `ACTIVITY_NOT_FOUND`: `id` doesn't exist.

## Horizon

### `GET /api/horizon`

**200 OK**
```json
{
  "day1": "YYYY-MM-DD | null",
  "forwardBound": "YYYY-MM-DD"
}
```
`day1` is `null` until the first-ever materialization or block placement
(research.md §5). `forwardBound` is always `today + 13 days`, computed at
request time. The frontend uses this to disable navigation past either
bound (FR-023).

## Days

### `GET /api/days/{date}`

If `date` is between `today` and `forwardBound` inclusive and has not yet
been materialized, this call materializes it first (FR-017, clipped
against any pre-existing blocks per research.md §3), then returns it. A
past day (`date < today`) is returned as-is, never materialized (FR-017).

**Deviation from GET safety**: this is a deliberate, documented deviation
from strict REST "GET must be side-effect-free" semantics — see plan.md's
Complexity Tracking table. The write is idempotent (a second `GET` after
materialization returns the identical state), so callers may still treat
repeated requests as safe to retry.

**200 OK**
```json
{
  "date": "YYYY-MM-DD",
  "materialized": true,
  "blocks": [
    {
      "id": "uuid",
      "type": "ROUTINE | CONSTRAINED | PLANNED_ACTIVITY",
      "startTime": "HH:mm",
      "endTime": "HH:mm",
      "endsNextDay": false,
      "name": "string | null",
      "activityId": "uuid | null",
      "activityName": "string | null"
    }
  ]
}
```
Blocks are returned in chronological order by `startAt`. `endsNextDay`
is `true` when the block's `endAt` falls on the following calendar date
(midnight-spanning, FR-014). `activityName` is populated only for
`PLANNED_ACTIVITY` blocks (`null` otherwise): per data-model.md, a
planned-activity block's display label is its linked Activity's name, not
its own `name` field (which is unused for that type) — this field is what
actually carries that label to the frontend, since nothing else in this
response shape did.

**404 Not Found** — `date` is earlier than `day1` (`DAY_NOT_REACHABLE`,
see Error Conventions) (FR-009, FR-023, edge case: days before Day 1 do
not exist).
**422 Unprocessable Entity** — `date` is later than `forwardBound`
(`DAY_BEYOND_FORWARD_HORIZON`, see Error Conventions) (FR-009, FR-023).

## Time Blocks

### `POST /api/days/{date}/blocks`

**Request**
```json
{
  "type": "ROUTINE | CONSTRAINED | PLANNED_ACTIVITY",
  "startTime": "HH:mm",
  "endTime": "HH:mm",
  "name": "string | null",
  "activityId": "uuid | null"
}
```
`endTime <= startTime` means the block spans into the next calendar day
(FR-014). `activityId` is required iff `type = PLANNED_ACTIVITY` and must
reference a currently `UNPLANNED` activity (FR-007).

**201 Created** — body: the created block (shape as in `GET /api/days/{date}`).
**400 Bad Request** — either `INVALID_TIME_GRANULARITY`: non-5-minute
time value or zero-length block; or `INVALID_REQUEST`: `activityId` is
present for a `type` other than `PLANNED_ACTIVITY` (must be `null`).
**404 Not Found** — `DAY_NOT_REACHABLE`: `date` is earlier than `day1`
(FR-009).
**422 Unprocessable Entity** — `DAY_BEYOND_FORWARD_HORIZON`: `date` is
later than `forwardBound` (FR-009).
**409 Conflict** — either `TIME_BLOCK_OVERLAP`: the requested
`[startTime, endTime)` overlaps an existing block (FR-008); or
`ACTIVITY_NOT_AVAILABLE`: `type = PLANNED_ACTIVITY` and `activityId` is
missing/`null`, or references an activity that is not currently in the
unplanned backlog (FR-007) — a *missing* `activityId` for a
`PLANNED_ACTIVITY` block is `ACTIVITY_NOT_AVAILABLE`, not a 400: the
activity-lookup runs before the structural null check ever would, so it's
indistinguishable from "that activity doesn't exist" by the time an error
is raised. See Error Conventions for both 409 bodies.

### `PUT /api/blocks/{id}`

Edits `startTime`/`endTime`/`name` in place, same day (FR-010).

**Request**
```json
{ "startTime": "HH:mm", "endTime": "HH:mm", "name": "string | null" }
```

**200 OK** — updated block. **400 Bad Request** — `INVALID_TIME_GRANULARITY`.
**404 Not Found** — `TIME_BLOCK_NOT_FOUND`: `id` doesn't exist. **409
Conflict** — `TIME_BLOCK_OVERLAP`: resulting range overlaps another block
on the same day (FR-008).

### `PATCH /api/blocks/{id}/move`

Reschedules a `PLANNED_ACTIVITY` block to a (possibly different) day and
slot (FR-011).

**Request**
```json
{ "day": "YYYY-MM-DD", "startTime": "HH:mm", "endTime": "HH:mm" }
```

**200 OK** — updated block.
**400 Bad Request** — either `INVALID_REQUEST`: the target block is not
of type `PLANNED_ACTIVITY`; or `INVALID_TIME_GRANULARITY`: non-5-minute
time value or zero-length range.
**404 Not Found** — `TIME_BLOCK_NOT_FOUND`: `id` doesn't exist; or
`DAY_NOT_REACHABLE`: `day` is earlier than `day1` (FR-009).
**422 Unprocessable Entity** — `DAY_BEYOND_FORWARD_HORIZON`: `day` is
later than `forwardBound` (FR-009).
**409 Conflict** — `TIME_BLOCK_OVERLAP`: overlap at the destination
(FR-008).

### `DELETE /api/blocks/{id}`

**204 No Content**. If the block is `PLANNED_ACTIVITY`, its Activity
becomes `UNPLANNED` again (FR-012); no confirmation is required for any
block type (spec Assumptions — confirmation only applies to deleting a
planned Activity from `/api/activities`, not to deleting the block
directly). **404 Not Found** — `TIME_BLOCK_NOT_FOUND`: `id` doesn't exist.

## Routine Template

### `GET /api/routine-template/entries`

**200 OK**
```json
[
  { "id": "uuid", "name": "string", "startTime": "HH:mm", "endTime": "HH:mm" }
]
```

### `POST /api/routine-template/entries`

**Request**: `{ "name": "string", "startTime": "HH:mm", "endTime": "HH:mm" }`
(`endTime <= startTime` denotes a midnight-spanning entry).

**201 Created** — the created entry. **400 Bad Request** — either
`INVALID_REQUEST`: blank name; or `INVALID_TIME_GRANULARITY`:
non-5-minute value or zero-length range. **409 Conflict** —
`TEMPLATE_ENTRY_OVERLAP`: overlaps an existing entry, using the two-day
projection rule (FR-016).

### `PUT /api/routine-template/entries/{id}`

Same request/response shape as `POST`. **200 OK**. **400 Bad Request**,
**409 Conflict** (same rules as creation). **404 Not Found** —
`ROUTINE_TEMPLATE_ENTRY_NOT_FOUND`: `id` doesn't exist.

### `DELETE /api/routine-template/entries/{id}`

**204 No Content**. Only affects days materialized after this point
(FR-019) — no cascading effect on already-materialized days.
**404 Not Found** — `ROUTINE_TEMPLATE_ENTRY_NOT_FOUND`: `id` doesn't exist.
