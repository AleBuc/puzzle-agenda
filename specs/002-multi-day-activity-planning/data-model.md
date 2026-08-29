# Phase 1 Data Model: Multi-Block, Multi-Day Activity Planning

Extends `specs/001-daily-planning-core/data-model.md`. Only entities that
change, or are newly introduced, are described in full below; unchanged
entities (`RoutineTemplateEntry`, `MaterializedDay`, `HorizonState`) are
unaffected by this feature and are not repeated here.

## Activity *(changed)*

Backlog item; see spec FR-001–FR-004, FR-021.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | generated, immutable — unchanged |
| `name` | string | required, non-blank — unchanged |
| `estimatedDurationMinutes` | integer | required, > 0 — unchanged; now interpreted as a **per-day quota** (FR-003), not an overall total |
| `priority` | `Priority` enum | required — unchanged |
| `category` | string | optional — unchanged |

**Removed**: the `status` field and the `ActivityStatus` enum
(`UNPLANNED`/`PLANNED`) from `specs/001-daily-planning-core/data-model.md`.
A single global status cannot represent "PLANNED on day D, UNPLANNED on
day D+2" for the same activity (FR-009). Planning status is now exclusively
a per-day derived value — see `DayPlanningStatus` below — and an
aggregate, cross-day summary — see `ActivityPlanningSummary` below.
Callers that need "does this activity have any fragment at all" use
`ActivityPlanningSummary.totalFragmentCount() > 0`.

**State transitions**: `(created)` → edited (name/duration/priority/category,
FR unchanged from 001) → `deleted` (direct delete if `totalFragmentCount ==
0`; confirmed cascade delete otherwise, removing every fragment across
every day, FR-016).

**Table**: `activity` — schema unchanged (the removed `status` was already
derived, never a column).

## TimeBlock *(changed)*

An entry on a specific day; see spec FR-001–FR-002, FR-005–FR-008,
FR-021–FR-022.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | generated, immutable |
| `type` | `BlockType` enum | required, immutable — unchanged |
| `startAt` / `endAt` | timestamp | required, 5-minute granularity — unchanged |
| `day` | date (derived) | `startAt`'s calendar date — unchanged |
| `name` | string | unchanged |
| `activityId` | UUID (FK → Activity) | required and non-null iff `type = PLANNED_ACTIVITY` — unchanged structurally, but **no longer unique**: several `TimeBlock` rows may now reference the same `activityId` simultaneously, on the same day (until merged) or across different days |

**New validation rule** (FR-021): when `type = PLANNED_ACTIVITY`,
`[startAt, endAt)` MUST NOT span midnight (`startAt.toLocalDate() ==
endAt.toLocalDate()`, strictly). `ROUTINE` and `CONSTRAINED` blocks are
unaffected and may still span midnight, as today.

**Changed validation rule** (replaces 001's "activityId currently
`UNPLANNED`", FR-001): creating or moving a `PLANNED_ACTIVITY` block no
longer requires the target activity to have zero existing fragments —
only that `activityId` refers to an existing `Activity` (FR-001).

**New behavior — same-activity/same-day merge** (FR-005–FR-007): on
create, edit, or move, if the candidate range touches or overlaps one or
more existing `PLANNED_ACTIVITY` blocks with the *same* `activityId` on
the *same* target day, those blocks are deleted and replaced, in the same
operation, by a single new block covering the union of all their ranges
plus the candidate. Overlap against any block of a *different* activity,
or of type `ROUTINE`/`CONSTRAINED`, is still rejected exactly as in
feature 001 (FR-008) — merge never bypasses that check.

**State transitions**: created (direct, or absorbed into a merge) →
edited/moved (may itself become a merge, per above) → deleted, either
alone (`scope=self`) or together with every other same-activity fragment
on its day (`scope=activityDay`, FR-014–FR-015) or as part of a full
activity cascade (FR-016).

**Table**: `time_block` — schema unchanged. The
`time_block_activity_id_unique` partial unique index (001's V1 migration)
is **dropped** (see Migration below); the `EXCLUDE USING GIST (span WITH
&&)` constraint is unchanged and remains valid because merges are
resolved (absorbed rows deleted, merged row inserted) inside the same
transaction as the write that triggered them.

## DayPlanningStatus *(new, derived — not a table)*

Per activity, per day; see spec FR-009.

| Value | Condition |
|---|---|
| `UNPLANNED` | Sum of that activity's `PLANNED_ACTIVITY` fragment durations on that day is `0` |
| `PARTIALLY_PLANNED` | Sum is `> 0` and `< estimatedDurationMinutes` |
| `PLANNED` | Sum is `>= estimatedDurationMinutes` |

Computed on every read from `TimeBlockRepository.findByActivityIdAndDay`;
never persisted. The companion **remaining time for display** is
`max(0, estimatedDurationMinutes - plannedMinutes)` (spec.md Assumptions:
floors at zero once fully planned, even though `plannedMinutes` itself is
never capped and over-allocation, FR-004, is always accepted).

## ActivityPlanningSummary *(new, derived — not a table)*

Per activity, across the reachable horizon; backs the backlog's aggregate
view (FR-012–FR-013).

| Field | Type | Rules |
|---|---|---|
| `activityId` | UUID | — |
| `totalFragmentCount` | integer | count of that activity's `PLANNED_ACTIVITY` blocks across every reachable day |
| `plannedDayCount` | integer | count of distinct reachable days with `>= 1` fragment (any `DayPlanningStatus` other than `UNPLANNED` counts — `/speckit-clarify` Q2) |
| `days` | list of `{day, plannedMinutes, status}` | one entry per reachable day that has `>= 1` fragment; sparse (no entry for `UNPLANNED` days) |

Computed on every read from `TimeBlockRepository.findByActivityId`
(all of that activity's fragments) grouped by day; never persisted.

## Relationships

```text
Activity 1 ──── 0..N TimeBlock (type = PLANNED_ACTIVITY)   [changed: was 0..1 in feature 001]
    │
    └── derives, per (Activity, day) ──── DayPlanningStatus            [new]
    └── derives, per Activity, across the horizon ──── ActivityPlanningSummary  [new]
```

All other relationships (`RoutineTemplateEntry`, `MaterializedDay`,
`HorizonState`) are unchanged from feature 001.

## Migration

`V4__drop_time_block_activity_unique.sql`:

```sql
-- An activity may now have multiple concurrent PLANNED_ACTIVITY fragments
-- (data-model.md Activity/TimeBlock, feature 002) — the "at most one" invariant
-- from feature 001's V1 migration no longer holds.
DROP INDEX time_block_activity_id_unique;
```

No other schema change: `time_block`'s columns, `CHECK` constraints, and
the `EXCLUDE USING GIST` constraint are all unchanged — only the
per-activity cardinality assumption they did not encode (the unique index
was a separate object) is removed.
