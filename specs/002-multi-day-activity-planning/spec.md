# Feature Specification: Multi-Block, Multi-Day Activity Planning

**Feature Branch**: `002-multi-day-activity-planning`

**Created**: 2026-08-29

**Status**: Draft

**Input**: User description: "Multi-block, multi-day activity planning for puzzle-agenda. An activity from the backlog can be planned into several time blocks (\"fragments\") on any day within the planning horizon, and on several different days independently. The estimated duration acts as a per-day quota, indicative only: for each day, remaining time = estimated duration minus the sum of that day's fragment durations; planning more than the estimated duration on a day is allowed and never rejected. Fragments on different days never interact. Fragments of the same activity on the same day that overlap or are adjacent are MERGED into a single block covering their union (creation, edit, and move all trigger merging); overlap with blocks of any other activity or type remains rejected as today. Merging is atomic: absorbed fragments are removed and replaced by the merged block. Activity planning status is derived per day, never stored: for a given day, an activity is UNPLANNED (no fragments that day), PARTIALLY_PLANNED (planned time < estimated), or PLANNED (planned time >= estimated). The day view's activity selector offers every activity with its remaining time for the displayed day; fully-planned ones stay selectable but visually marked. The backlog lists all activities with aggregate planning info across the reachable window (e.g. \"planned on 3 days\", with per-day breakdown available). Deleting a fragment from the timeline: if the activity has a single fragment on that day, direct deletion; if several on that day, a confirmation offers \"delete this fragment only\" or \"delete all fragments of this activity on this day\". Deleting the activity itself (from the backlog) requires confirmation, states the total fragment count across all days, and cascades to all of them. Out of scope: automatic splitting suggestions, ordered sequences of fragments, progress/completion tracking, recurrence rules (multi-day planning is manual placement, not repetition)."

## Clarifications

### Session 2026-08-29

- Q: Should a single planned-activity fragment be allowed to span across midnight into the next day, the way routine-template blocks already do? → A: No — each fragment is confined to one calendar day; longer spans are represented as separate fragments per day.
- Q: When the backlog shows an activity is "planned on N days", does that count include days where only some (not all) of the day's quota was filled? → A: Yes — any day with at least one fragment counts, regardless of whether that day's quota is fully met.
- Q: Can a user move an existing fragment to a different day directly, or does changing days always mean deleting and recreating it? → A: Direct cross-day reassignment is supported; the fragment is evaluated for merge/overlap only against the destination day's blocks.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Plan an activity across multiple days (Priority: P1)

A user has an activity that will take longer than fits comfortably in one
sitting (or that they simply want to spread out), so they place a fragment
of it on one day and another fragment of the same activity on a different
day, without recreating the activity or losing track of how much of it is
already scheduled where.

**Why this priority**: This is the core value of the feature. Every other
behavior (merging, status, deletion) exists to support planning the same
activity across several days; without this capability there is nothing new
to test or use.

**Independent Test**: Can be fully tested by planning a 2-hour fragment of
a 5-hour activity on day D and a 2-hour fragment of the same activity on
day D+2, then confirming both fragments exist independently, each day
shows 3 hours remaining for that activity, and neither day's state affects
the other.

**Acceptance Scenarios**:

1. **Given** an activity "Write report" with a 5-hour estimated duration
   in the backlog, **When** the user plans a 2-hour fragment on day D and
   a 2-hour fragment on day D+2, **Then** both fragments are created and
   each day's remaining time for the activity is 3 hours, independently.
2. **Given** fragments already exist for an activity on day D, **When**
   the user views a different day D+2 with no fragments of that activity,
   **Then** the activity is shown as UNPLANNED for day D+2 with its full
   estimated duration as remaining time.
3. **Given** an activity is fully planned (PLANNED) on day D, **When** the
   user opens day D+2's activity selector, **Then** the activity still
   appears with its full remaining time for D+2, unaffected by its status
   on day D.
4. **Given** an activity already has fragments totaling its full estimated
   duration on day D, **When** the user plans an additional fragment for
   it on day D, **Then** the fragment is accepted and the day's remaining
   time for that activity is not treated as an error condition.
5. **Given** a fragment of an activity exists on day D, **When** the user
   moves it directly to a different day D+1 (with no overlap there),
   **Then** it is reassigned to day D+1 in one action, day D's remaining
   time for that activity returns to reflect its removal, and day D+1's
   remaining time reflects the fragment's addition.

---

### User Story 2 - Split an activity into multiple fragments within one day, with automatic merging (Priority: P2)

A user places more than one fragment of the same activity on the same day
(for example, a morning slot and an evening slot), and later adds or edits
a fragment that ends up touching or overlapping another fragment of that
same activity on that day — expecting the system to consolidate them into
one block rather than leaving redundant, overlapping entries.

**Why this priority**: Multi-fragment-per-day is part of the same core
capability as User Story 1, but its correctness depends on a merge rule
that has no equivalent in the existing single-block model. It must work
correctly for the per-day quota math (User Story 3) and deletion behavior
(User Story 4) to make sense.

**Independent Test**: Can be fully tested by creating two non-touching
fragments of the same activity on the same day, confirming both remain
distinct, then creating or editing a third fragment so it touches or
overlaps one or both, and confirming the system merges them into a single
block covering their union while leaving unrelated blocks untouched.

**Acceptance Scenarios**:

1. **Given** an activity with two non-adjacent, non-overlapping fragments
   on day D (e.g. 08:00–08:20 and 18:00–18:25), **When** the user views
   day D, **Then** both fragments appear as separate blocks.
2. **Given** the fragments above, **When** the user creates a new fragment
   of the same activity from 08:20 to 08:35 (adjacent to the first),
   **Then** the system merges the first and new fragments into a single
   08:00–08:35 block, and the 18:00–18:25 fragment is left unchanged.
3. **Given** two non-touching fragments of the same activity on day D,
   **When** the user edits one fragment's end time so it now overlaps or
   touches the other, **Then** the two are merged into a single block
   covering their union, and the absorbed fragment no longer exists as a
   separate block.
4. **Given** two fragments of the same activity, **When** the user moves
   one fragment so it overlaps or touches a block belonging to a
   different activity, or to a routine/constrained block, **Then** the
   move is rejected exactly as ordinary overlap rejection today.
5. **Given** three fragments of the same activity positioned so that
   editing the middle one makes it touch both neighbors at once, **When**
   the edit is submitted, **Then** all three fragments are merged in one
   operation into a single block covering the full union.

---

### User Story 3 - See per-day and aggregate planning progress (Priority: P3)

A user wants to glance at a day and see how much of an activity's
estimated time is still open for that specific day, and wants to check the
backlog to see, across the reachable planning window, on which days and
for how long each activity has already been placed.

**Why this priority**: Without visible status, the fragments created in
User Stories 1 and 2 are not actionable — the user can create them but
cannot reason about progress, over-allocation, or what is left to plan.
This story is the feedback layer built on top of the fragment model.

**Independent Test**: With fragments distributed across days and within a
day as in User Stories 1–2, open a partially-planned day's activity
selector and confirm the remaining time and status are correct; open a
fully-planned day and confirm the activity is marked but still selectable;
open the backlog and confirm the aggregate summary lists the correct days
and durations for an activity.

**Acceptance Scenarios**:

1. **Given** an activity with no fragments on day D, **When** the user
   opens day D's activity selector, **Then** the activity is listed as
   UNPLANNED with remaining time equal to its full estimated duration.
2. **Given** an activity with fragments totaling less than its estimated
   duration on day D, **When** the user opens day D's activity selector,
   **Then** the activity shows as PARTIALLY_PLANNED with the correct
   remaining time.
3. **Given** an activity with fragments totaling at least its estimated
   duration on day D, **When** the user opens day D's activity selector,
   **Then** the activity is visually marked as fully planned but remains
   selectable for further planning that day.
4. **Given** an activity planned across 3 different days within the
   reachable window, **When** the user opens the backlog, **Then** that
   activity's entry indicates it is planned on 3 days, with a per-day
   breakdown of planned time available on request.

---

### User Story 4 - Delete fragments, and cascade-delete a multi-fragment activity (Priority: P4)

A user wants to remove a single fragment they no longer need, remove all
of a day's fragments for an activity at once, or delete an activity
outright and have every one of its fragments across every day cleaned up —
without ambiguity about what exactly will be removed.

**Why this priority**: These are destructive operations that only become
meaningful once an activity can have several fragments (User Stories 1–2).
They protect the user from accidental data loss now that "delete" is no
longer a single, unambiguous action.

**Independent Test**: With an activity having a single fragment on day A
and two fragments on day B, delete the day-A fragment directly and confirm
no prompt appears and the activity returns to UNPLANNED for day A; delete
one of day-B's two fragments and confirm the two-option prompt appears;
then delete the activity from the backlog and confirm the confirmation
states the total remaining fragment count and removes all of them.

**Acceptance Scenarios**:

1. **Given** an activity has exactly one fragment on day D, **When** the
   user deletes that fragment from the timeline, **Then** it is removed
   immediately without a confirmation prompt and the activity becomes
   UNPLANNED for day D.
2. **Given** an activity has two or more fragments on day D, **When** the
   user attempts to delete one of them from the timeline, **Then** the
   system prompts with two options: delete only that fragment, or delete
   all of that activity's fragments on day D.
3. **Given** the prompt in scenario 2, **When** the user chooses "delete
   this fragment only", **Then** only the selected fragment is removed and
   the other fragment(s) on day D remain.
4. **Given** the prompt in scenario 2, **When** the user chooses "delete
   all fragments of this activity on this day", **Then** every fragment of
   that activity on day D is removed, while fragments of that activity on
   other days remain untouched.
5. **Given** an activity has fragments spread across multiple days,
   **When** the user deletes the activity from the backlog, **Then** the
   confirmation states the total number of fragments that will be removed
   across all days, and confirming removes the activity and every one of
   its fragments.

### Edge Cases

- Creating a fragment with a time range identical to an existing fragment
  of the same activity on the same day is treated as a merge (the union
  equals the existing range) — an idempotent no-op rather than an error.
- A single edit can trigger a chain merge across three or more touching or
  overlapping fragments of the same activity in one atomic operation, not
  just the two fragments directly involved in the edit.
- If the union produced by a same-activity merge would also overlap a
  block belonging to a different activity or a routine/constrained block,
  the operation is rejected as an ordinary overlap conflict — same-activity
  merging never bypasses conflict rules with other blocks.
- An activity with zero fragments on any day has no per-day breakdown
  entries in the backlog's aggregate view; it is simply unplanned overall.
- Deleting the last remaining fragment of an activity on one day has no
  effect on that activity's fragments on any other day.
- The backlog's aggregate/per-day breakdown only reflects days within the
  currently reachable planning horizon, consistent with how days become
  navigable/materialized in the base scheduling feature.
- Planning or moving a fragment to a day at or beyond the forward horizon
  boundary remains governed by the existing planning-horizon rejection,
  unchanged by this feature.
- Moving a fragment to a different day removes it from the origin day's
  totals and status immediately; it is then evaluated for merge/overlap
  only against the destination day's blocks. The origin and destination
  days do not interact beyond this single transfer.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow a backlog activity to have multiple
  planned-activity fragments simultaneously, on the same day and/or across
  several different days within the planning horizon.
- **FR-002**: System MUST treat fragments of the same activity on
  different days as fully independent: creating, editing, deleting, or
  merging a fragment on one day MUST NOT affect fragments of that activity
  on any other day.
- **FR-003**: For each day and each activity, system MUST compute
  remaining time as the activity's estimated duration minus the sum of
  that activity's fragment durations on that day; this value MUST be
  computed on demand and MUST NOT be persisted.
- **FR-004**: System MUST allow the sum of an activity's fragment
  durations on a single day to exceed its estimated duration, and MUST
  NOT reject or error on such over-allocation.
- **FR-005**: When a fragment is created, edited, or moved such that it
  overlaps or is adjacent (end-exclusive, matching existing block-adjacency
  rules) to another fragment of the SAME activity on the SAME day, system
  MUST merge them into a single fragment covering the union of their time
  ranges.
- **FR-006**: Merging MUST be transitive within a single operation: if the
  resulting union touches or overlaps additional fragments of the same
  activity on the same day, those MUST also be absorbed into the same
  merged block before the operation completes.
- **FR-007**: Merge operations MUST be atomic: all absorbed fragments are
  removed and replaced by the single merged block as one indivisible
  change, never leaving an intermediate or partially-merged state visible.
- **FR-008**: Overlap between a fragment and any block belonging to a
  different activity, or to a routine/constrained block, MUST continue to
  be rejected exactly as it is today; automatic merging applies only
  among fragments of the same activity on the same day.
- **FR-009**: System MUST derive, per day and per activity, a planning
  status of UNPLANNED (no fragments that day), PARTIALLY_PLANNED (planned
  time on that day is less than the estimated duration), or PLANNED
  (planned time on that day is greater than or equal to the estimated
  duration). This status MUST be computed on read and MUST NOT be stored.
- **FR-010**: The day view's activity selector MUST list every backlog
  activity together with its remaining time for the currently displayed
  day, per FR-003.
- **FR-011**: Activities with PLANNED status for the displayed day MUST
  remain selectable in the day view's activity selector but MUST be
  visually distinguished from UNPLANNED/PARTIALLY_PLANNED activities.
- **FR-012**: The backlog view MUST show, for each activity, aggregate
  planning information across the reachable planning horizon, including
  at minimum the number of days on which it has at least one fragment —
  counting any day with at least one fragment, whether PARTIALLY_PLANNED
  or PLANNED, regardless of whether that day's quota is fully met.
- **FR-013**: The backlog view MUST let the user view a per-day breakdown
  of an activity's planned time within the reachable planning horizon.
- **FR-014**: Deleting a fragment from the timeline, when it is the only
  fragment of that activity on that day, MUST remove it immediately
  without a confirmation prompt.
- **FR-015**: Deleting a fragment from the timeline, when the activity has
  more than one fragment on that day, MUST prompt the user to choose
  between deleting only that fragment or deleting all fragments of that
  activity on that day, and MUST act only on the chosen scope.
- **FR-016**: Deleting an activity from the backlog MUST require
  confirmation, MUST state the total number of fragments that will be
  removed across all days, and, once confirmed, MUST remove the activity
  and cascade-delete every one of its fragments on every day.
- **FR-017**: System MUST NOT offer automatic suggestions for how to split
  an activity's estimated duration into fragments (out of scope).
- **FR-018**: System MUST NOT enforce or expose any ordering or sequence
  relationship between an activity's fragments (out of scope).
- **FR-019**: System MUST NOT track completion or progress status of
  fragments or activities; the planning status in FR-009 describes
  scheduling coverage only, not completion (out of scope).
- **FR-020**: System MUST NOT introduce recurrence rules for activity
  fragments; each fragment remains a manual, one-off placement (out of
  scope).
- **FR-021**: System MUST NOT allow a single fragment to span across
  midnight; each fragment MUST be confined to one calendar day. A span
  that would otherwise cross midnight MUST be represented as separate
  fragments on each affected day.
- **FR-022**: System MUST allow a fragment to be reassigned directly from
  one day to a different day in a single move action, without requiring a
  separate delete-then-create sequence. Upon reassignment, the fragment
  MUST be evaluated for overlap/merge exclusively against blocks on the
  destination day, and the origin day's remaining time and planning
  status MUST immediately reflect the fragment's removal.

### Key Entities

- **Activity Fragment**: A planned-activity time block tied to exactly one
  backlog activity and exactly one day, with a start and end time. An
  activity may have zero, one, or several fragments on a given day, and
  fragments on different days are unrelated to one another.
- **Activity** *(extended)*: The existing backlog item (name, estimated
  duration, priority, optional category) now may be associated with
  fragments spread across zero or more days, instead of a single planned
  slot.
- **Day Planning Status** *(derived)*: For a given activity and day, one
  of UNPLANNED, PARTIALLY_PLANNED, or PLANNED, computed from that day's
  fragments and never stored.
- **Backlog Aggregate View** *(derived)*: For a given activity, the set of
  days within the reachable horizon that have at least one fragment, and
  the total planned time per such day; computed on demand, not stored.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can plan a single activity's time across at least 5
  different days within the planning horizon without recreating the
  activity, each additional day's fragment placed in under 30 seconds.
- **SC-002**: When a fragment is created, edited, or moved so that it
  touches or overlaps an existing fragment of the same activity on the
  same day, the two are shown as one consolidated block immediately, with
  zero duplicate or overlapping fragments left visible.
- **SC-003**: For any day, a user can tell whether an activity is
  unplanned, partially planned, or fully planned for that specific day at
  a glance, without leaving the day or backlog view.
- **SC-004**: Every attempt to plan more time than an activity's estimated
  duration on a single day succeeds rather than being blocked, consistent
  with the duration being an indicative, non-blocking quota.
- **SC-005**: Deleting a fragment that is the only one for its activity on
  that day requires no extra confirmation step; deleting a fragment among
  several on the same day requires exactly one clarifying choice before
  anything is removed.
- **SC-006**: Deleting an activity that has fragments spread across
  multiple days removes all of them in a single confirmed action, and the
  confirmation accurately states the total fragment count beforehand.

## Assumptions

- "Reachable planning horizon" for the backlog's aggregate and per-day
  breakdown means the same forward-looking window already used for day
  navigation (today up to the forward horizon bound); fragments are only
  ever placed within that window, so no past-day accumulation is expected.
- When a day's fragments for an activity already meet or exceed its
  estimated duration, the day view's remaining-time indicator displays as
  fully used (e.g. "0m left") rather than a negative number, even though
  additional fragments may still be planned that day.
- The two-option delete prompt and the cascade-delete confirmation reuse
  the existing non-blocking, custom confirmation UI pattern already used
  for planned-activity deletions, not a native browser dialog.
- Fragment merge and overlap-rejection logic apply identically whether a
  fragment is created via a form, edited in place, or moved (including a
  move to a different day, per FR-022) — "move" is not a distinct
  validation path from "edit".
