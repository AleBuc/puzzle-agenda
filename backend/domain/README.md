# Domain module

Framework-free entities, value objects, and domain services (Constitution
Principle I — no Spring, no JPA, no Jackson in this module's main scope).
This file documents the invariants of the two non-trivial domain
services, `OverlapPolicy` and `MaterializationService`, for anyone
extending or debugging them. See `/specs/001-daily-planning-core/` for
the full spec, research, and data model — this is a code-adjacent
reference, not a duplicate of those.

## `OverlapPolicy`

**Invariant**: two `TimeRange`s conflict iff they share at least one
instant of half-open `[start, end)` time — `a.start() < b.end() &&
b.start() < a.end()`. Nothing else. In particular:

- **Adjacency is not overlap.** A range ending exactly when another
  starts (`a.end() == b.start()`) does not conflict (FR-008 scenario 2).
- **Midnight is not special-cased.** Because both `TimeBlock` and a
  projected `RoutineTemplateEntry` are stored as a single continuous
  timestamp interval (research.md §1), a range spanning into the next
  calendar day is compared exactly like any other range — there is no
  separate "check day D, then check day D+1" branch anywhere in this
  policy or its callers.
- **`overlaps()` is pure and total** — it never throws, works for any
  two valid `TimeRange`s, and is symmetric (`overlaps(a, b) ==
  overlaps(b, a)`).
- **`checkNoOverlap()` throws on the *first* conflict found** — callers
  that need to know about *all* conflicts (there is no such caller today)
  would need to use `overlaps()` directly in a loop instead.

`RoutineTemplateEntry.conflictsWith` deliberately does **not** reuse
`OverlapPolicy.overlaps()` on a single same-day projection — see that
method's own Javadoc for why the two-day-projection rule (FR-016) needs
day offsets `{-1, 0, +1}`, not a single shared reference day.

## `MaterializationService`

**Invariant**: `materialize(day, templateEntries, candidateBlocks)` is a
pure function — no I/O, no repository dependency, no side effects. Given
the same three arguments, it always returns the same list of new
`ROUTINE` blocks. The caller (`ViewDay`) is responsible for fetching
`candidateBlocks` (a sufficiently wide window — see its own Javadoc) and
persisting the result; this service only computes what *should* exist.

**Algorithm**: for each template entry, independently:

1. Project the entry onto `day` as a `TimeRange` (`entry.projectOnto(day)`,
   spilling into `day + 1` if the entry spans midnight).
2. Subtract every candidate block's range from that projected range, one
   at a time, always operating on the *current* set of free fragments
   (start with one fragment — the whole projected range — and each
   subtraction can leave a fragment unchanged, shrink it, split it in
   two, or remove it entirely).
3. Emit one `ROUTINE` `TimeBlock` per surviving non-empty fragment,
   copying the entry's name.

**Properties that follow from this**:

- **Entries never affect each other.** Two entries in the same template
  are already guaranteed non-overlapping (`RoutineTemplateEntry.
  conflictsWith` is checked at creation/edit time), so entry A's
  clipping never needs to consider entry B's own projected range — only
  `candidateBlocks` (pre-existing blocks) matter as obstacles.
- **Zero, one, or many blocks per entry** — full coverage by existing
  blocks legitimately produces zero blocks for that entry; a block
  sitting in the middle of the entry's range legitimately produces two.
  Neither is an error case.
- **Never throws.** There is no scenario in which materializing one
  entry can fail the whole batch (FR-017) — the subtraction algorithm
  has no failure mode, only "the fragment list happens to be empty".
- **Order of `candidateBlocks` does not matter.** Subtracting block X
  then block Y from a fragment produces the same final fragment set as
  Y then X, since each subtraction only ever shrinks/splits/removes
  fragments already produced by the previous step.

**Documented interpretation note**: spec.md's two sleep-23:00–07:00
worked examples read as contradictory if taken fully literally — one
(clipped by a 02:00–03:00 block) explicitly produces two blocks; the
other (clipped by a 06:00–06:30 jog block) only mentions "clipped to
23:00–06:00" and never mentions the 06:30–07:00 remainder. This
implementation follows FR-017's general rule (confirmed by
research.md §3): the jog example is read as an incomplete/illustrative
description of the leading clip, not a literal complete-output spec, so
it *also* produces the 06:30–07:00 remainder block. See
`MaterializationService`'s class-level Javadoc for the full reasoning,
and `quickstart.md` §5 for this exact scenario verified live end-to-end.
