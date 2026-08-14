# Phase 0 Research: Daily Schedule Planning

All technology choices are fixed by the project constitution's Technology
Constraints (Java 25, Spring Boot 4.x, Maven multi-module, PostgreSQL 16+
with Flyway, Vue 3 + Vite, no state-management library), so no
`NEEDS CLARIFICATION` markers remain in the Technical Context. The
research below covers the design decisions needed to turn the spec's
rules into a concrete, constitution-compliant implementation.

## 1. Representing a time block's span

**Decision**: Store each `TimeBlock` (and each `RoutineTemplateEntry`
projection at materialization time) as a continuous `[startAt, endAt)`
timestamp interval, not as a "day + local start time + local end time"
triple. The block's "day" (used for reachability/horizon checks and
day-scoped queries) is a derived value: the calendar date of `startAt`.

**Rationale**: FR-014 requires a midnight-spanning block to occupy time at
the end of one day and the start of the next, with overlap rules applied
correctly across both. If storage were day-scoped (day + local times),
every overlap check and every materialization clip would need explicit
two-day logic (check day D, then check day D+1 for the spillover). A
continuous timestamp interval collapses this: two blocks conflict exactly
when their `[startAt, endAt)` intervals intersect, full stop — midnight
stops being a special case anywhere in the domain logic or the database
constraint. This directly enables Decision 2 below.

**Alternatives considered**: Day-scoped storage with a boolean
"spans midnight" flag and duplicated overlap logic for the spillover day —
rejected as needless complexity (violates Principle V) that both the
domain algorithm and the database constraint would have to reimplement
identically.

## 2. Database-level overlap enforcement (Constitution Principle II)

**Decision**: Add a generated `tsrange` column (or an expression index)
derived from `[startAt, endAt)` and enforce non-overlap with a PostgreSQL
`EXCLUDE ... USING GIST (span WITH &&)` constraint on the `time_block`
table. Combined with Decision 1, a single constraint covers same-day,
adjacent (`)` exclusive upper bound), and midnight-spanning cases — no
per-day partitioning needed.

**Rationale**: Principle II mandates DB-level overlap prevention via
range types + exclusion constraints, as a second, independent line of
defense behind the domain's `OverlapPolicy`. Because storage is a single
continuous interval (Decision 1), one exclusion constraint is sufficient;
this is the simplest schema that satisfies the constitution.

**Alternatives considered**: Enforcing overlap only in application code —
rejected, contradicts Principle II directly. Per-day check constraints —
rejected as redundant once Decision 1 is in place, and would still miss
the midnight-spillover case (a block's tail could overlap a same-day block
on day D+1 without either being "the same row's day").

## 3. Materialization clipping algorithm (FR-017)

**Decision**: When a day D (today ≤ D ≤ today+13) is first accessed, for
each `RoutineTemplateEntry`, project it onto D as a concrete
`[start, end)` timestamp interval (adding a day to `end` when
`end <= start`, i.e., the entry spans midnight). Query all existing
`TimeBlock`s whose interval intersects this projected interval (a single
range query thanks to Decision 1 — no separate "day D" and "day D+1"
lookups). Subtract each intersecting block's interval from the projected
interval; the result is zero, one, or several maximal free sub-intervals.
Create one `ROUTINE` `TimeBlock` per non-empty sub-interval, copying the
entry's name. Record `D` as materialized (see Decision 4) regardless of
how many blocks (including zero) were produced. Repeat independently per
entry; one entry's clipping never affects another entry's placement, and
no single entry's conflict aborts materialization of the others.

**Rationale**: This is a direct implementation of the interval-subtraction
rule requested for FR-017 (materialized entries have lower priority than
pre-existing blocks; splitting produces multiple blocks; full coverage
produces none; materialization never fails as a whole). Doing the
subtraction against a single projected interval, rather than against
"day D's blocks" and "day D+1's blocks" separately, avoids reimplementing
the midnight special-case a second time.

**Alternatives considered**: Rejecting materialization of an entry
entirely if any conflict exists (all-or-nothing per entry) — rejected,
contradicts the explicit clipping requirement and the two worked examples
in the spec (sleep clipped to 23:00–06:00; sleep split into two blocks
around an existing 02:00–03:00 block).

## 4. Tracking "has this day been materialized?"

**Decision**: Persist a `materialized_day` marker (`day DATE PRIMARY KEY`,
`materialized_at TIMESTAMP`) written once per date, independent of the
`time_block` table. `ViewDay` checks this marker before deciding whether
to run materialization for today-or-future days.

**Rationale**: A day can materialize to zero routine blocks (every
template entry fully covered by pre-existing blocks). If "materialized"
were inferred from "at least one `ROUTINE` block exists on this day," such
a day would look unmaterialized and would be re-processed on the next
view — re-running clipping against whatever blocks exist *then* could
produce a different (nondeterministic-looking) result and would violate
the "first time" semantics of FR-017. An explicit marker makes
materialization idempotent and auditable.

**Alternatives considered**: Inferring materialization from block
existence — rejected for the reason above.

## 5. Day 1 (earliest reachable day) storage

**Decision**: Persist a single-row `horizon_state` table with a nullable
`day1 DATE` column. On the first-ever materialization or first-ever
`TimeBlock` placement (whichever happens first), if `day1` is still null,
set it to *today's date at that moment* — never to the day the triggering
action targeted. The forward bound (`today + 13 days`) is never stored;
it is computed on every request from the server's current date.

**Rationale**: Directly implements the Key Entities "Day 1" definition:
fixed once, set to "today" at the moment of first use regardless of which
day the first action targeted, guaranteeing today is always reachable
afterward. Keeping it as one nullable row (rather than inferring it from
`MIN(day)` across blocks/materializations) makes the "not yet established"
state explicit and matches the Assumptions note that a brand-new
installation has no reachable past day until first use.

**Alternatives considered**: Deriving Day 1 from
`MIN(day)` over `time_block` ∪ `materialized_day` — rejected, because that
would make Day 1 equal to the *earliest targeted day*, not "today" at
first use, which is exactly the bug the spec's clarification called out
(a first action targeting a future day must not make today unreachable).

## 6. 5-minute granularity enforcement

**Decision**: Validate `minute % 5 == 0` (and zero seconds/nanos) in the
domain layer, in the value object(s) that wrap a time-of-day/timestamp
(constructor-time validation, rejecting construction otherwise). Add a
matching database `CHECK` constraint as defense-in-depth, consistent with
Principle II treating the database as a last line of defense.

**Rationale**: Principle I requires business rules to live in the
framework-free domain; the domain must be the source of truth for what a
valid time value is. The DB check is cheap insurance, not a replacement.

**Alternatives considered**: Enforcing granularity only via frontend input
step="5" — rejected as insufficient; the API contract must reject
invalid values regardless of client behavior (Principle IV requires
meaningful status codes for invalid requests).

## 7. Frontend state approach

**Decision**: No global state-management library. Each view composes
small composables (`useDaySchedule(date)`, `useBacklog()`,
`useRoutineTemplate()`) that own their own reactive state and call the
REST API directly; day-timeline free-time gaps are computed client-side
by sorting a day's blocks and diffing consecutive `endAt`/`startAt`
values, not fetched as separate entities.

**Rationale**: Principle V explicitly forbids introducing a
state-management library before a concrete need is demonstrated; the
feature's view set (backlog, single day, routine template) has no
cross-view shared state that composables-per-view can't handle.

**Alternatives considered**: Pinia store — rejected per Principle V absent
a demonstrated need.

## 8. API contract style

**Decision**: Resource-oriented REST, matching Principle IV — `/activities`,
`/days/{date}`, `/days/{date}/blocks`, `/blocks/{id}`,
`/routine-template/entries`, `/horizon`. See `contracts/api.md`.

**Rationale**: Consistent, predictable REST conventions are mandated
directly by Principle IV.
