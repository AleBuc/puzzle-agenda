# Feature Specification: Daily Schedule Planning

**Feature Branch**: `001-daily-planning-core`

**Created**: 2026-08-14

**Status**: Draft

**Input**: User description: "Core planning feature for puzzle-agenda, a personal day and free-time planner. Users manage a catalog of activities. An activity has a name, an estimated duration, a priority (e.g., low / medium / high), and an optional category (e.g., sport, errands, leisure, chores). Activities start as \"unplanned\" (a backlog of things to do). Users build their schedule by placing time blocks on a day. A time block has a start time, an end time, and a type: \"routine\" (sleep, meals, hygiene/getting dressed — everyday self-care time), \"constrained time\" (work, appointments, commitments), or a \"planned activity\" (an activity from the backlog assigned to a specific slot). Routine and constrained blocks both make time unavailable for free-time activities; they are distinguished so users can see at a glance what is routine vs. commitments. Daily routine template: users define a routine template once — a set of named routine blocks with start and end times. The template is a pre-fill mechanism only: when a day within the planning horizon is materialized, it is stamped with the template's routine blocks; from that point on these are ordinary time blocks with no link back to the template. Users can freely edit, move, or delete any routine block on any specific day individually. Editing the template only affects days that have not been pre-filled yet. Sleep may span midnight and is handled as time unavailable at the end of one day and the beginning of the next. Two time blocks on the same day must never overlap; adjacent blocks are allowed (end time is exclusive). Planning horizon is 2 weeks: users can place blocks from today up to 13 days ahead; placing a block outside this window is rejected with a clear message. When an activity is placed on a slot it leaves the backlog; if the block is deleted the activity returns to the backlog; an activity can be rescheduled. A placed activity's slot duration may differ from its estimated duration. Users can view a single day as a timeline showing all its time blocks in chronological order, with visible gaps between blocks, with routine/constrained/activity blocks visually distinct, and can navigate day to day within the horizon. Users can create, edit, and delete activities, time blocks, and routine template entries. Deleting an activity that is currently planned also removes its scheduled block, with confirmation. Out of scope: automatic free-slot suggestion, weekly view, general recurrence rules, multi-user support and authentication."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Build a day's schedule with time blocks (Priority: P1)

A user wants to block out the time in their day that isn't free — everyday
routine (sleep, meals, getting ready) and firm commitments (work,
appointments) — so they can see, at a glance, how much of the day is
actually theirs. They add, move, and remove these blocks on a chosen day,
and the system keeps the day free of double-booked time.

**Why this priority**: This is the foundation of the whole feature: a
day's schedule is a set of non-overlapping time blocks. Without it, there
is nothing to view, navigate, or later assign activities into. It delivers
value standalone — a user can already see their day's committed vs. free
time.

**Independent Test**: Can be fully tested by opening a day, adding a
routine block and a constrained-time block that don't overlap, confirming
both appear on the day's timeline, then attempting to add a third block
that overlaps one of them and confirming it is rejected.

**Acceptance Scenarios**:

1. **Given** an empty day within the planning horizon, **When** the user
   adds a constrained-time block from 09:00 to 10:30, **Then** the block
   appears on that day and the time 09:00–10:30 is no longer free.
2. **Given** a day with a block ending at 15:00, **When** the user adds
   another block starting at 15:00, **Then** both blocks are accepted
   because they only share a boundary instant, not overlapping time.
3. **Given** a day with a block from 09:00 to 10:00, **When** the user
   tries to add a block from 09:30 to 09:45, **Then** the system rejects
   the new block because it overlaps the existing one.
4. **Given** a day with a block, **When** the user edits its start or end
   time to a range that would overlap another block on the same day,
   **Then** the edit is rejected.
5. **Given** a day with a block, **When** the user deletes it, **Then**
   the block no longer appears and that time becomes free again.
6. **Given** the user tries to add a block on a day beyond 13 days from
   today, **When** they submit it, **Then** the system rejects the block
   and explains that it falls outside the 2-week planning horizon.

---

### User Story 2 - Manage the activity backlog (Priority: P2)

A user wants to keep a running list of things they'd like to do —
errands, chores, leisure activities — each with a name, a rough duration,
a priority, and an optional category, without committing them to a time
yet.

**Why this priority**: The backlog is the pool of things the schedule
ultimately exists to place. It is independently useful (a simple to-do
catalog) and is a prerequisite for planning an activity into the day
(User Story 3).

**Independent Test**: Can be fully tested by creating an activity with a
name, estimated duration, priority, and category, confirming it appears
in the unplanned backlog, then editing and deleting it.

**Acceptance Scenarios**:

1. **Given** the user is viewing the backlog, **When** they create an
   activity with a name, estimated duration, and priority (category
   optional), **Then** the activity appears in the "unplanned" backlog.
2. **Given** an existing backlog activity, **When** the user edits its
   name, duration, priority, or category, **Then** the updated values are
   shown.
3. **Given** an existing backlog activity that has never been planned,
   **When** the user deletes it, **Then** it is removed with no further
   confirmation needed.

---

### User Story 3 - Plan an activity into the schedule (Priority: P3)

A user wants to take something from their backlog and give it a real slot
on a specific day, so it becomes part of their actual plan rather than
just an intention.

**Why this priority**: This connects the backlog (User Story 2) to the
day's schedule (User Story 1) and is the feature's central "planning"
action, but it depends on both existing first.

**Independent Test**: Can be fully tested by placing a backlog activity
onto a free slot on a day, confirming it disappears from the backlog and
appears on the day's timeline as a planned-activity block, then deleting
the block and confirming the activity returns to the backlog.

**Acceptance Scenarios**:

1. **Given** an unplanned activity and a free slot on a day within the
   horizon, **When** the user assigns the activity to that slot, **Then**
   a planned-activity block appears on the day and the activity no longer
   appears in the backlog.
2. **Given** a planned activity's estimated duration is 30 minutes,
   **When** the user assigns it to a 45-minute slot, **Then** the system
   accepts the slot as given, without requiring the slot to match the
   estimate.
3. **Given** a planned-activity block, **When** the user moves it to a
   different non-overlapping slot (same or another day within the
   horizon), **Then** the block is rescheduled to the new slot.
4. **Given** a planned-activity block, **When** the user deletes the
   block, **Then** the block disappears from the day and the activity
   reappears in the unplanned backlog.
5. **Given** an activity that is currently planned (has a block on some
   day), **When** the user deletes the activity from the catalog,
   **Then** the system asks for confirmation and, once confirmed, removes
   both the activity and its scheduled block.

---

### User Story 4 - Define a daily routine template (Priority: P4)

A user wants to describe their typical day once (sleep, meals, getting
ready) so that new days start pre-filled with that routine instead of
requiring them to re-enter the same blocks every day, while still being
free to tweak any single day's routine without disturbing the rest.

**Why this priority**: This is a convenience layer on top of User Story
1's time blocks. It is valuable but not required for the core scheduling
and viewing capability to work, so it is built last.

**Independent Test**: Can be fully tested by defining a template entry
(e.g., sleep 23:00–07:00), viewing a not-yet-visited day within the
horizon and confirming it is pre-filled with that routine block, editing
that day's copy, then confirming a later template change does not alter
that already-materialized day.

**Acceptance Scenarios**:

1. **Given** no routine template exists yet, **When** the user creates a
   template entry with a name, start time, and end time (e.g., "Lunch"
   12:30–13:15), **Then** the entry is saved to the template.
2. **Given** a saved routine template, **When** the user views a day
   within the horizon for the first time, **Then** that day is pre-filled
   with one routine block per template entry, using the template's
   names, start times, and end times.
3. **Given** a day that was already pre-filled from the template,
   **When** the user edits, moves, or deletes one of its routine blocks
   (e.g., a later dinner on that specific day), **Then** only that day's
   block changes; the template and all other days are unaffected.
4. **Given** a day that has already been pre-filled, **When** the user
   edits the routine template afterward, **Then** the already-filled day
   keeps its original blocks unchanged.
5. **Given** a routine entry for sleep from 23:00 to 07:00, **When** a day
   is pre-filled, **Then** that day's time from 23:00 onward and the
   following day's time until 07:00 are both treated as unavailable, and
   no other block may be created that overlaps either portion.

---

### Edge Cases

- What happens when a user tries to create two routine template entries
  whose times overlap each other? The system MUST reject the overlapping
  template entry, mirroring the same-day overlap rule used for time
  blocks.
- What happens when a user tries to place a "planned activity" block
  using an activity that is already planned elsewhere? This cannot occur
  through normal use, since an activity leaves the backlog as soon as it
  is placed and only backlog activities can be selected for placement.
- How does the system handle a midnight-spanning block (e.g., sleep
  23:00–07:00) when checking for overlaps? The portion after midnight is
  treated as occupying the start of the next calendar day, and overlap
  rules are applied to both affected days independently.
- What happens if a user tries to start a block on the day exactly 13
  days ahead of today (the last day of the future horizon) versus 14 days
  ahead? A block whose start day is 13 days ahead is accepted — including
  one that spans midnight and therefore ends after midnight on day 14, a
  day that could never itself be a block's start day. A block whose start
  day is 14 days ahead or later is rejected with an explanatory message.
- Does materializing day 13 (the last day of the future horizon) work
  when the routine template includes a midnight-spanning entry, e.g.,
  sleep 23:00–07:00? Yes — day 13 is materialized normally, producing a
  sleep block that starts on day 13 and ends at 07:00 on day 14; this
  succeeds even though day 14 itself is beyond the future horizon and
  could not independently host a new block's start.
- What happens when materializing a day whose template includes sleep
  23:00–07:00 and the following day already holds a jog block from
  06:00–06:30? The system creates the sleep block clipped to
  23:00–06:00, stopping where the existing jog block starts, instead of
  failing or overlapping it.
- What happens when materializing a day whose template includes sleep
  23:00–07:00 and an existing block already occupies 02:00–03:00 within
  that spanned range? The system creates two sleep blocks, 23:00–02:00
  and 03:00–07:00, instead of one continuous block.
- What happens when a template entry's full range is already entirely
  covered by existing blocks on the day being materialized (and, for a
  midnight-spanning entry, on the following day)? No routine block is
  created for that entry; materialization still proceeds normally for
  the template's other entries.
- What happens when a user deletes a routine or constrained-time block
  (not a planned-activity block)? It is removed immediately; no
  confirmation is required and no backlog activity is affected.
- What happens to a day's already-materialized blocks as the forward
  bound of the horizon advances (today moves forward)? Blocks already
  placed on a day are never removed by the passage of time; only the
  ability to place *new* blocks beyond 13 days ahead is restricted.
- What happens when a user tries to navigate, view, or create a block on
  a day earlier than Day 1? The day is treated as non-existent: it cannot
  be navigated to or viewed, and no block can be created on it.
- Can a user place a backlog activity onto a slot on a day in the past
  (Day 1 through yesterday)? Yes — placement works the same as on a
  today-or-future day: the activity leaves the backlog and a
  planned-activity block appears on that past day.
- Does a past day ever get pre-filled from the routine template? No —
  materialization only ever applies to today or a future day (through 13
  days ahead); a past day shows only the blocks it already has and can
  only be edited manually.

## Requirements *(mandatory)*

### Functional Requirements

**Activity backlog**

- **FR-001**: Users MUST be able to create an activity with a name, an
  estimated duration, and a priority (low, medium, or high); category is
  optional.
- **FR-002**: A newly created activity MUST start in the "unplanned"
  backlog.
- **FR-003**: Users MUST be able to edit an activity's name, estimated
  duration, priority, and category at any time.
- **FR-004**: Users MUST be able to delete an unplanned activity directly.
- **FR-005**: Deleting an activity that is currently planned (has an
  associated time block) MUST require user confirmation and, once
  confirmed, MUST remove both the activity and its scheduled time block.

**Time blocks**

- **FR-006**: Users MUST be able to create a time block on a specific day
  by specifying a start time, an end time, and a type: routine,
  constrained time, or planned activity. Start and end times MUST be
  given in 5-minute increments (multiples of 5 minutes past the hour,
  e.g., 09:05, 09:10); any other value MUST be rejected.
- **FR-007**: Creating a "planned activity" block MUST require selecting
  an activity currently in the backlog; upon successful placement, that
  activity MUST no longer appear in the backlog.
- **FR-008**: The system MUST reject creating or editing a time block
  that overlaps another time block on the same day. Two blocks where one
  ends exactly when the other starts MUST NOT be treated as overlapping.
- **FR-009**: The system MUST reject creating or editing a time block
  whose start day is later than 13 days ahead of today, and MUST show a
  clear explanatory message. This future bound restricts only how far
  ahead a block's start day may be. Within the past, users MAY create,
  edit, and delete blocks on any day from Day 1 (the earliest reachable
  day — see Key Entities) through today. The system MUST reject creating
  or editing a block on any day earlier than Day 1, since such a day does
  not exist for the user.
- **FR-010**: Users MUST be able to edit any time block's start and end
  time, subject to the overlap and horizon rules.
- **FR-011**: Users MUST be able to move ("reschedule") a planned-activity
  block to a different slot, on the same day or any other reachable day
  (Day 1 through 13 days ahead of today, including a past day),
  consistent with FR-009 and SC-009, subject to the overlap and horizon
  rules.
- **FR-012**: Users MUST be able to delete any time block. Deleting a
  planned-activity block MUST return its activity to the unplanned
  backlog. Deleting a routine or constrained-time block MUST simply
  remove it, with no other side effect.
- **FR-013**: A planned-activity block's duration MUST be independent of
  its activity's estimated duration; the system MUST accept a slot
  duration that differs from the estimate without error.
- **FR-014**: The system MUST treat a time block that spans midnight as
  occupying time at the end of its start day and the beginning of the
  following day, and MUST apply the overlap rule to both affected days.
  The future-horizon rule in FR-009 applies only to a block's start day:
  a block whose start day is 13 days ahead of today MAY still spill over
  into day 14, even though day 14 could never itself be a block's start
  day.

**Routine template**

- **FR-015**: Users MUST be able to define a routine template made up of
  named entries, each with a start time and an end time. As with time
  blocks (FR-006), start and end times MUST be given in 5-minute
  increments; any other value MUST be rejected.
- **FR-016**: Users MUST be able to create, edit, and delete entries in
  the routine template, subject to the same no-overlap rule applied among
  template entries. A template entry that spans midnight (e.g., sleep
  23:00–07:00) MUST be validated using the same two-day projection as
  FR-014: it conflicts with another entry that overlaps either its
  before-midnight portion or its after-midnight portion (e.g., it
  conflicts with an entry from 06:30–07:00, but not with one from
  07:00–07:30).
- **FR-017**: The first time a user views or interacts with a day from
  today through 13 days ahead that has not yet been pre-filled, the
  system MUST "materialize" that day. For each entry in the current
  routine template, the system MUST place a routine-type time block
  (copying the entry's name) over as much of the entry's full range as
  remains free — including the after-midnight portion, for an entry that
  spans midnight — after clipping away any part of that range that
  overlaps an existing time block, whether that existing block sits on
  the materialized day, spills into it from the previous day, or already
  sits on the following day. A materialized template entry therefore has
  lower priority than any pre-existing block on either affected day. If
  clipping splits an entry's range into more than one free sub-interval,
  the system MUST create one routine block per maximal free sub-interval.
  An entry whose entire range is already covered by existing blocks
  produces no routine block for that entry. Materialization of a day MUST
  always complete for every template entry, never failing as a whole
  because one entry conflicts with existing blocks. Materialization
  applies only to today and future days; a day earlier than today is
  never pre-filled from the template — it shows only whatever time blocks
  it already holds, and the user may add, edit, or delete blocks on it
  manually.
- **FR-018**: Once created, a materialized day's routine blocks MUST
  behave like any other time block — independently editable, movable, and
  deletable — with no ongoing link back to the template.
- **FR-019**: Changes to the routine template (adding, editing, or
  deleting entries) MUST NOT alter any day that has already been
  materialized; they MUST only take effect for days materialized
  afterward.

**Day view and navigation**

- **FR-020**: Users MUST be able to view a single day as a chronological
  timeline of all its time blocks.
- **FR-021**: The day timeline MUST visually distinguish routine,
  constrained-time, and planned-activity blocks from one another.
- **FR-022**: The day timeline MUST make free time (gaps between blocks)
  visibly apparent.
- **FR-023**: Users MUST be able to navigate from the currently viewed
  day to the previous or next day. Backward navigation MUST stop at Day 1
  (the earliest reachable day) and MUST NOT go earlier. Forward
  navigation MUST remain capped at 13 days ahead of today.

### Key Entities

- **Activity**: A backlog item with a name, an estimated duration, a
  priority (low/medium/high), and an optional category. Its status
  (unplanned vs. planned) is derived from whether a planned-activity
  block currently references it.
- **Time Block**: An entry on a specific day with a start time, an end
  time, and a type (routine, constrained time, or planned activity). A
  planned-activity block references exactly one activity. A routine block
  carries a name (copied from a template entry at materialization time,
  freely editable afterward). Time blocks on the same day never overlap;
  a block ending at one time and another starting at the same time are
  not considered overlapping.
- **Routine Template Entry**: A named, reusable definition with a start
  time and an end time, used only to pre-fill newly materialized days;
  has no ongoing link to the time blocks it produces.
- **Day**: A specific calendar date that holds an ordered set of time
  blocks and tracks whether it has been materialized from the routine
  template. A day is reachable — it can be navigated to, viewed, and have
  blocks created on it — only if it falls between Day 1 and 13 days ahead
  of today, inclusive. **Day 1** is the earliest reachable day: the
  calendar date that was "today" at the moment of the first-ever
  materialization or the first-ever time block placement, whichever
  happened first — regardless of which day that action targeted. For
  example, if a user's very first action pre-fills or places a block on
  a day several days in the future, Day 1 is still set to today's date
  at that moment, not to the future day; this guarantees today is always
  reachable once Day 1 is established. Days before Day 1 do not exist for
  the user. The forward bound (13 days ahead of today) advances
  automatically as today advances; Day 1, once established, is fixed and
  does not move.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can create a new activity and see it appear in the
  backlog in under 15 seconds.
- **SC-002**: The system never accepts two overlapping time blocks on the
  same day — 100% of overlap attempts (including boundary and
  midnight-spanning cases) are rejected.
- **SC-003**: 100% of attempts to start a time block more than 13 days
  ahead of today are rejected with an explanatory message.
- **SC-004**: A user viewing any day within the horizon can identify,
  without opening any individual block, which time is routine, which is
  committed, and which is still free.
- **SC-005**: A user who has defined a routine template sees every
  newly-visited day within the horizon pre-filled with that routine,
  with no manual re-entry required.
- **SC-006**: Editing the routine template produces zero changes to any
  day that was materialized before the edit was made.
- **SC-007**: A user can move from any day to an adjacent day within the
  horizon in a single navigation action.
- **SC-008**: A user can take an activity from the backlog to fully
  scheduled on a specific day in one continuous flow, with the activity
  removed from the backlog and visible on the day's timeline immediately
  after.
- **SC-009**: A user can open any past day (Day 1 through yesterday) and
  add, edit, or delete blocks on it exactly as they would on a
  today-or-future day within the horizon.

## Assumptions

- A day is "materialized" the first time the user views or interacts
  with it in the app, and only applies to today or a future day (through
  13 days ahead); this is the trigger described in the input for
  pre-filling it from the routine template.
- Time block and routine template entry start and end times are
  constrained to 5-minute increments (multiples of 5 minutes past the
  hour); this is the finest time resolution the system accepts.
- The reachable range of days has two independent bounds: a forward
  bound of 13 days ahead of today, which advances automatically as today
  advances, and a backward bound, Day 1, which is fixed once established
  as the calendar date that was "today" at the moment of the first-ever
  materialization or first-ever block placement (whichever happened
  first) — not necessarily the day that action targeted — and does not
  move afterward. Blocks already placed on a day are unaffected as the
  forward bound advances.
- Before Day 1 is established (e.g., a brand-new installation with no
  materialized day and no block placed yet), there is no reachable past
  day; the reachable range is simply today through 13 days ahead until
  the first materialization or block placement occurs.
- Activity categories (e.g., sport, errands, leisure, chores) are
  illustrative examples, not an exhaustive fixed list; users may use any
  category label.
- Deleting a routine or constrained-time block does not require
  confirmation, since no backlog activity is affected; confirmation is
  only required when deleting an activity that is currently planned, as
  specified in the input.
- This feature covers a single user with no authentication or
  multi-user concerns, consistent with the stated out-of-scope items.
- Automatic suggestion of free slots, a weekly view, and recurrence rules
  beyond the single daily routine template are explicitly out of scope
  for this feature and are left to later iterations.
