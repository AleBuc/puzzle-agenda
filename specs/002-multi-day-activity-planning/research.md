# Phase 0 Research: Multi-Block, Multi-Day Activity Planning

No `NEEDS CLARIFICATION` markers remain in `spec.md` (resolved during
`/speckit-clarify`). This document instead resolves the *design* decisions
needed to extend feature 001's implementation — grounded in the existing
code (`backend/domain`, `backend/application`, `backend/infrastructure`,
`frontend/src`) rather than choices between unrelated technologies, since
this feature introduces no new dependency.

## §1. Dropping the "one fragment per activity" invariant

**Decision**: Remove `Activity.status`/`ActivityStatus` (global
`UNPLANNED`/`PLANNED`) entirely. Drop the `time_block_activity_id_unique`
partial unique index (V1 migration) via a new `V4` migration. Change
`TimeBlockRepository.findByActivityId(UUID): Optional<TimeBlock>` to
`findByActivityId(UUID): List<TimeBlock>` (all fragments, any day) and add
`findByActivityIdAndDay(UUID, LocalDate): List<TimeBlock>` (fragments of
one activity on one specific day — the shape every merge/quota
computation actually needs).

**Rationale**: `CreateTimeBlock.requireUnplannedActivity` and the unique
index both encode "an activity can have at most one live
`PLANNED_ACTIVITY` block," which is the exact invariant FR-001 replaces.
Per FR-009, status is now a *per-day* derived value — there is no single
global status left to store or index against.

**Alternatives considered**: Keeping `ActivityStatus` as a "has at least
one fragment anywhere" boolean was considered, but it answers a question
nothing in the spec asks (US3/FR-009 always want the *per-day* status),
and would invite confusion with the new `DayPlanningStatus` (§3). Rejected
per Principle V.

## §2. Fragment merge algorithm

**Decision**: A new domain service, `FragmentMerger`, pure and stateless
like `MaterializationService`, with a signature along the lines of
`TimeRange merge(TimeRange candidate, List<TimeBlock> sameActivityDayFragments)`
returning the union range, plus the subset of `sameActivityDayFragments`
absorbed (to delete). Algorithm: classic interval-merge — repeatedly scan
the candidate-day, same-activity fragment list for any range that
*touches or overlaps* the current union (a relaxed version of
`OverlapPolicy.overlaps`, using `<=` instead of `<` so a shared boundary
instant also triggers a merge, matching FR-005's "overlap or adjacent");
absorb it (expand the union, remove it from the pending list); repeat
until a full pass absorbs nothing more. This naturally satisfies FR-006's
transitive/chain-merge requirement (edit the middle of three fragments →
all three end up absorbed in one call) without special-casing pair vs.
chain merges.

Call sites (`CreateTimeBlock`, `EditTimeBlock`, `MoveTimeBlock`) run this
**after** the existing hard-conflict check (`OverlapPolicy.checkNoOverlap`
against every *other* activity's/type's blocks on the target day — FR-008,
unchanged) and **before** persisting: fetch
`findByActivityIdAndDay(activityId, targetDay)` (excluding the block's own
current row, relevant for same-day edits/moves), run `FragmentMerger`,
then delete every absorbed fragment and save one block with the union
range — all inside the same transaction, so the DB's `EXCLUDE` constraint
never observes an intermediate overlapping state (FR-007, atomicity).

**Rationale**: reuses the exact "pure function over a small candidate
list, caller supplies a wide-enough window, only one exception-throwing
path (the pre-existing hard-conflict check) untouched" shape already
proven by `MaterializationService`/`OverlapPolicy` — no new architectural
pattern, easy to unit-test with `@ParameterizedTest` per merge shape
(disjoint, adjacent, overlapping, fully-contained, three-way chain,
identical-range idempotent merge).

**Alternatives considered**: Encoding merge as a database-level operation
(e.g., a trigger or a recursive CTE) was considered given `time_block`
already has a GiST-indexed `span` column, but rejected per Principle I
(business rules belong in the framework-free domain, not in
database-specific procedural code) and Principle V (a hand-rolled
in-memory interval merge over at most a handful of rows needs no such
machinery).

## §3. Per-day planning status and remaining time

**Decision**: A new small domain type, `DayPlanningStatus` (`UNPLANNED`,
`PARTIALLY_PLANNED`, `PLANNED`), computed by a pure function —
`DayPlanningStatus.of(int estimatedDurationMinutes, int plannedMinutes)` —
alongside a remaining-time helper that floors at zero for display
(`Math.max(0, estimatedDurationMinutes - plannedMinutes)`, per spec.md's
Assumptions) while the underlying `plannedMinutes` sum itself is never
clamped (so over-allocation, FR-004, is still visible to anyone who wants
the raw numbers). `plannedMinutes` for a given activity/day is simply the
sum of `TimeRange` durations from `findByActivityIdAndDay`.

**Rationale**: matches FR-009's "computed on read, never stored" and
keeps the calculation next to `OverlapPolicy`/`FragmentMerger` as another
small, framework-free domain service.

**Alternatives considered**: Persisting status as a denormalized column
updated on every fragment write was considered (would simplify some
queries) but rejected: it duplicates data the spec explicitly says must
never be stored (FR-009), and re-derivation is cheap at this scale
(Scale/Scope: a handful of fragments per activity per day).

## §4. Midnight confinement for `PLANNED_ACTIVITY`

**Decision**: Enforce "no midnight span" once, in the domain, inside
`TimeBlock`'s existing invariant-checking factory methods (`create` and
`withRangeAndName`) — reject when `type == PLANNED_ACTIVITY &&
range.spansMidnight()`, reusing the already-computed `TimeRange
.spansMidnight()`. A new named exception,
`PlannedActivitySpansMidnightException`, maps to `400 Bad Request` /
`PLANNED_ACTIVITY_SPANS_MIDNIGHT` (parallel to how `InvalidTimeRangeException`
already maps to `400`/`INVALID_TIME_GRANULARITY`).

**Rationale**: `TimeBlock.create` already centralizes the
"`activityId` required iff `PLANNED_ACTIVITY`" invariant (`TimeBlock.java`
lines 41-49) — adding the midnight-confinement invariant next to it keeps
a single source of truth regardless of call path (direct creation, edit,
or move), even though the HH:mm-to-`LocalDateTime` midnight-wrap
computation itself happens earlier, in the REST controller layer
(`TimeBlockController`, `EditTimeBlock`, `MoveTimeBlock` all do
`endTime.compareTo(startTime) <= 0 ? nextDay : sameDay` before constructing
the candidate `TimeRange`) — by the time that candidate reaches
`TimeBlock`, a rejected midnight span is indistinguishable in shape from
one a `ROUTINE` block would be allowed to have, so the type-conditioned
check has to live at the point where both `type` and `range` are known
together.

**Alternatives considered**: Rejecting earlier, in each REST controller
method, was considered but rejected as three duplicated checks instead of
one; validating in `CreateTimeBlock`/`EditTimeBlock`/`MoveTimeBlock`
instead of the domain entity was also considered, but `TimeBlock.create`
is exactly where the parallel `activityId`-required invariant already
lives, so co-locating them is more consistent with the existing code.

## §5. Cross-day fragment move

**Decision**: `MoveTimeBlock` already accepts an arbitrary `newDay`
(`MoveTimeBlock.java` line 45) — no structural change needed to support
FR-022's direct reassignment. The only addition: run the §2 merge step
scoped to `newDay` (not the fragment's origin day) after the existing
hard-conflict check, exactly as for `CreateTimeBlock`/`EditTimeBlock`. The
origin day needs no explicit cleanup — the moved block's row is updated
in place (`existing.withRangeAndName(...)`, then `save`), so it simply no
longer appears in `findByDay(originDay)` once its `start_at` changes.

**Rationale**: confirms and narrows spec.md's Edge Cases note ("moving a
fragment... is evaluated for merge/overlap only against the destination
day's blocks") to a one-line change on top of code that already supports
cross-day dates.

**Alternatives considered**: None — this is the smallest change of the
five decisions, since the capability was already present structurally.

## §6. Fragment deletion scope and activity cascade messaging

**Decision**: Add a `scope` query parameter to the existing
`DELETE /api/blocks/{id}` endpoint: `scope=self` (default) deletes only
that block; `scope=activityDay` deletes every `PLANNED_ACTIVITY` block
sharing that block's `activityId` and day. Deciding *which* prompt to show
(FR-014 vs. FR-015) is a pure frontend computation — the day's blocks
(including `activityId`) are already loaded by `useDaySchedule`, so the
frontend can count same-activity fragments client-side without a new
read endpoint. `DeleteActivity`'s existing `confirm=true` gate is kept,
but its trigger condition changes from "has a `PLANNED_ACTIVITY` block at
all" (old 1:1 model) to "has one or more fragments across any day," and
its rejection message states the exact total fragment count (FR-016) —
computed server-side from `findByActivityId` (now a `List`), not carried
as a new field on the existing `{reason, message}` error shape (Principle
V: the frontend already has the same count available from the extended
`GET /api/activities` aggregate, §7, before the user even opens the
confirmation).

**Rationale**: keeps both endpoints' request/response *shapes* stable
(one new query parameter, one changed trigger condition) rather than
introducing new resources, per Principle V; avoids a redundant new
"how many fragments does this day have" read endpoint the frontend does
not need.

**Alternatives considered**: A dedicated
`GET /api/blocks?activityId=&day=` lookup for the frontend to count
same-day fragments before deciding which prompt to show was considered,
but rejected as redundant — that data is already in the loaded `DayView`.

## §7. Backlog aggregate view shape

**Decision**: Extend the existing `GET /api/activities` response (rather
than adding a new endpoint) with, per activity: `plannedDayCount` (count
of reachable-horizon days with ≥1 fragment, any status — per the
`/speckit-clarify` Q2 decision), `totalFragmentCount`, and a `days` array
of `{day, plannedMinutes, status}` for exactly the days that have ≥1
fragment (sparse — no entry for UNPLANNED days, keeping the payload small
at this scale). The optional `?day=YYYY-MM-DD` query parameter (replacing
the removed `?status=` filter, §1) additionally computes, for that one
day, `remainingMinutesForDay` and `dayStatus` for every activity — this is
the shape the day view's activity selector (FR-010/FR-011) consumes.

**Rationale**: one endpoint, two modes (bare vs. `?day=`), mirrors the
existing `?status=` query-parameter pattern already established on this
same endpoint in feature 001; avoids a second endpoint purely for the
day-view selector, and avoids N+1 per-activity detail calls for the
backlog's per-day breakdown (FR-013) by embedding it directly, acceptable
at this feature's single-user, ~14-day-horizon scale.

**Alternatives considered**: A separate `GET /api/activities/{id}/planning`
endpoint for the per-day breakdown (fetched only when a user expands one
activity in the backlog) was considered, to keep the main list payload
smaller; rejected per Principle V — at this scale (a handful of
activities, ≤14 reachable days each) the embedded array costs nothing
worth a second endpoint and an extra frontend round-trip.
