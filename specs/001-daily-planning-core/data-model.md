# Phase 1 Data Model: Daily Schedule Planning

Entities and value objects derived from `spec.md`'s Key Entities section,
made concrete per the decisions in `research.md`. All entities live in the
`domain` module (framework-free); the shapes below are conceptual, not
JPA/table definitions, though each maps to one Flyway-managed table (noted
per entity).

## Activity

Backlog item; see spec FR-001–FR-005.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | generated, immutable |
| `name` | string | required, non-blank |
| `estimatedDurationMinutes` | integer | required, > 0 |
| `priority` | `Priority` enum (`LOW`, `MEDIUM`, `HIGH`) | required |
| `category` | string | optional, free text (no fixed list — spec Assumptions) |

**Status** (`UNPLANNED` / `PLANNED`) is derived, not stored: an activity
is `PLANNED` if a non-deleted `TimeBlock` of type `PLANNED_ACTIVITY`
references it, `UNPLANNED` otherwise. At most one such `TimeBlock` may
reference a given activity at a time (enforced by the
`CreateTimeBlock`/`MoveTimeBlock` use cases, backed by a partial unique
index on `time_block.activity_id`).

**State transitions**:
`(created)` → `UNPLANNED` → `PLANNED` (a `PLANNED_ACTIVITY` block is
created referencing it) → `UNPLANNED` (that block is deleted, FR-012) →
… (cycle repeats) → `deleted` (direct delete from `UNPLANNED`, FR-004; or
confirmed delete from `PLANNED`, which cascades deletion of its block,
FR-005).

**Table**: `activity`.

## TimeBlock

An entry on a specific day; see spec FR-006–FR-014, FR-017–FR-019.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | generated, immutable |
| `type` | `BlockType` enum (`ROUTINE`, `CONSTRAINED`, `PLANNED_ACTIVITY`) | required, immutable after creation |
| `startAt` | timestamp | required; minute is a multiple of 5, zero seconds/nanos (FR-006) |
| `endAt` | timestamp | required; strictly after `startAt`; may fall on the following calendar date (midnight-spanning, FR-014) |
| `day` | date (derived) | `startAt`'s calendar date — the block's "start day," used for reachability checks (FR-009) and day-scoped queries |
| `name` | string | optional; meaningful for `ROUTINE` and `CONSTRAINED` blocks (user-facing label); unused for `PLANNED_ACTIVITY`, whose display label is the linked Activity's name — a data-model-level default, not stated explicitly in the spec |
| `activityId` | UUID (FK → Activity) | required and non-null iff `type = PLANNED_ACTIVITY`; null otherwise (domain invariant) |

**Validation rules**:
- `[startAt, endAt)` MUST NOT intersect any other `TimeBlock`'s
  `[startAt, endAt)` (FR-008; half-open interval, so a block ending when
  another starts does not conflict).
- `day` (i.e., `startAt`'s date) MUST be within the reachable range —
  between `HorizonState.day1` and `today + 13 days`, inclusive — at
  creation or edit time (FR-009). `endAt` is not bound by this rule on its
  own (FR-014): a block starting on `today + 13` may end after midnight on
  `today + 14`.
- Creating a `PLANNED_ACTIVITY` block requires an `activityId` currently
  `UNPLANNED` (FR-007).

**State transitions**: created (direct creation, FR-006, or as a
materialization output, FR-017) → edited (start/end/name on the same day,
FR-010) → moved (`PLANNED_ACTIVITY` only, to a new day/slot within the
reachable range, FR-011) → deleted (`PLANNED_ACTIVITY`: the referenced
Activity becomes `UNPLANNED` again, FR-012; `ROUTINE`/`CONSTRAINED`:
removed with no other effect).

**Table**: `time_block`, with a `span tsrange` column derived from
`[startAt, endAt)` and an `EXCLUDE USING GIST (span WITH &&)` constraint
(research.md §2).

## RoutineTemplateEntry

A reusable, day-agnostic definition; see spec FR-015, FR-016.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | generated, immutable |
| `name` | string | required, non-blank |
| `startTime` | local time (HH:mm) | required; multiple of 5 minutes |
| `endTime` | local time (HH:mm) | required; multiple of 5 minutes; `endTime <= startTime` denotes a midnight-spanning entry (e.g., sleep 23:00–07:00) |

**Validation rules**: no two entries may overlap, using the same two-day
projection as `TimeBlock` (FR-016) — an entry conflicts with another if
their projected `[start, end)` intervals (each projected onto an
arbitrary shared reference day) intersect.

Entries carry **no persisted link** to the `TimeBlock`s they produce at
materialization time (FR-018) — copying is by value (name, start, end),
not by reference.

**Table**: `routine_template_entry`.

## MaterializedDay

Idempotency marker recording that a day has been stamped from the
template; see spec FR-017 and research.md §4.

| Field | Type | Rules |
|---|---|---|
| `day` | date | primary key |
| `materializedAt` | timestamp | audit only |

A row's presence means "materialization already ran for this date,"
regardless of how many `ROUTINE` blocks it produced (possibly zero).
Never written for a day earlier than today (FR-017's "today and future
days only" rule).

**Table**: `materialized_day`.

## HorizonState

Singleton holding the fixed backward bound of the reachable range; see
spec Key Entities "Day" / Assumptions and research.md §5.

| Field | Type | Rules |
|---|---|---|
| `day1` | date, nullable | set exactly once, on the first-ever materialization or first-ever `TimeBlock` placement, to *today's date at that moment* — not the day the triggering action targeted |

The forward bound (`today + 13 days`) is never persisted; every
reachability check computes it from the server's current date at request
time.

**Table**: `horizon_state` (single row, no natural key beyond "the one
row").

## Relationships

```text
Activity 1 ──── 0..1 TimeBlock (type = PLANNED_ACTIVITY)
RoutineTemplateEntry ╌╌copied-by-value-into╌╌> TimeBlock (type = ROUTINE, at materialization; no FK)
MaterializedDay 1 ──── 1 calendar date (independent of TimeBlock rows on that date)
HorizonState (singleton) ──── governs reachability of every TimeBlock.day and every MaterializedDay.day
```
