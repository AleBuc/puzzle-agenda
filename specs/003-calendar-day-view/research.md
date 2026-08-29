# Phase 0 Research: Calendar-Style Day Grid View

All items below were decided either by the feature owner's plan input (recorded as "Decision" with that source noted) or resolved through direct verification against the existing codebase and the `reka-ui` package. No `NEEDS CLARIFICATION` markers remain.

## 1. Accessible dialog implementation

**Decision**: Use `reka-ui`'s `Dialog` primitive (`DialogRoot`, `DialogTrigger`, `DialogPortal`, `DialogOverlay`, `DialogContent`, `DialogTitle`, `DialogClose`), imported directly — no wrapper design-system component, no other Reka UI primitive.

**Verified facts** (npm registry + package docs, checked 2026-08-29):
- Package name: `reka-ui`, current version `2.10.4`, license MIT, peer dependency `vue >= 3.4.0` (project is on `^3.5.40` — compatible).
- `DialogRoot` supports controlled state via `v-model:open` bound to a local `ref(false)`; a scoped slot `v-slot="{ close }"` on `DialogContent`/`DialogRoot` also exposes a `close()` function for explicit close buttons.
- Escape-to-close and the internal focus trap are automatic/built-in and verified working under Vue Test Utils + jsdom (confirmed during implementation: Tab cycling stays inside `DialogContent`).
- **Correction found during implementation**: focus-return-to-trigger is *not* automatic for a fully-controlled `DialogRoot` (bound via a plain `:open` prop, as this feature uses — see data-model.md `PopupState`). The documented "Esc moves focus to `DialogTrigger`" behavior is implemented in `DialogContentModal` as `rootContext.triggerElement.value?.focus()`, and `rootContext.triggerElement` is populated *exclusively* by `<DialogTrigger>`'s own `onMounted` hook (verified against `reka-ui`'s source, `src/Dialog/DialogTrigger.vue` and `DialogContentModal.vue`) — a component this feature does not use, since the actual trigger (a grid slot or block, or the "Add block" control) lives in a sibling component (`DayGrid.vue`), not wrapped in `<DialogTrigger>`. An external `@close-auto-focus` listener on `<DialogContent>` does **not** fire either: `DialogContentModal` declares that emit only to type its own internal listener on `DialogContentImpl`, and never re-emits it upward. **Fix**: `BlockPopup.vue` restores focus itself — a plain `watch` on its own `isOpen` computed captures `document.activeElement` when the popup opens and calls `.focus()` on it (after `nextTick()`, so it runs after reka-ui's own — here, no-op — close handling) when it closes. This satisfies FR-017 without `<DialogTrigger>`.
- `DialogContent` emits an outside-interaction event (`@pointer-down-outside` in the documented example) that fires on a backdrop/outside click, separately from Escape. The handler receives the native event via `event.detail.originalEvent` and `event.preventDefault()` can suppress the default close — this is the hook used to implement FR-024's distinct backdrop-click behavior (let it close, but run the draft-retention logic first).
- Confirmed emit names on `DialogContent` (`src/Dialog/DialogContentImpl.vue.d.ts`): `escapeKeyDown`, `pointerDownOutside`, `focusOutside`, `interactOutside`, `openAutoFocus`, `closeAutoFocus` — bound in templates as `@escape-key-down`, `@pointer-down-outside`, etc. `pointer-down-outside` alone is sufficient to distinguish "closed via outside click" from "closed via Escape" for FR-024, since Escape does not fire it.

**Rationale**: Building a correct focus trap, ARIA `role="dialog"`/`aria-modal`, labelling, and portal rendering by hand is a well-known source of subtle accessibility bugs, and this feature makes an accessible dialog a first-class, independently-tested requirement (User Story 4). Reka UI is headless (zero imposed styling), so it does not conflict with the project's existing hand-rolled CSS approach.

**Alternatives considered**:
- Hand-rolled `<dialog>` element or a custom `role="dialog"` div with manual focus-trap code — rejected per Complexity Tracking in plan.md (accessibility risk, and User Story 4 needs this to be reliably correct).
- A fuller headless UI kit (e.g., importing multiple Reka UI primitives, or a different library bundling many components) — rejected as speculative; only the `Dialog` primitive is needed, so only it is imported (Constitution Principle V).

## 2. Minutes-to-pixels / proportional layout math

**Decision**: Extract the existing pure helpers already used by `DayTimeline.vue` — `toMinutes(hhmm)`, `formatMinutes(minutes)`, `effectiveStart(block)` (which treats a `startsPreviousDay` block as starting at minute 0) — into a new `frontend/src/time-grid-utils.js` module, and add two new pure functions alongside them:
- `minutesToPercent(minutes)` → `(minutes / 1440) * 100`, used for both `top` and `height` CSS (as percentages of the grid container's height, so the grid reflows naturally with its container rather than depending on a fixed pixel-per-hour constant).
- `effectiveEnd(block)` → mirrors `effectiveStart`: `block.endsNextDay ? 1440 : toMinutes(block.endTime)`.

**Rationale**: These functions already exist and are already unit-testable in isolation (they have no Vue dependency); extracting them avoids duplicating midnight-clamp logic between the retired `DayTimeline.vue` and the new `DayGrid.vue`, and makes `time-grid-utils.spec.js` a direct, focused replacement for `DayTimeline.spec.js`'s gap/ordering tests (which no longer apply — the grid has no synthetic "gap" items, per FR-007).

**Alternatives considered**:
- A fixed pixel-per-hour scale (e.g., 60px/hour) — rejected: percentage-based sizing lets the grid's total height be set once (a CSS custom property or a single `min-height` on the grid container) and everything inside scales proportionally, which is simpler to reason about and test (assert percentages, not pixels tied to a specific viewport).

## 3. Keyboard navigation across empty grid slots

**Decision**: A roving-tabindex pattern over discrete 5-minute slot positions (288 per day, per FR-023 and the prior `/speckit-clarify` session), plus a persistent, always-`tabindex="0"` **"Add block" button** (FR-018) that opens the creation popup directly with a default time (see §5) as the fast path — so sequential slot-tabbing is available for precise placement but never the *only* path to create a block.

**Rationale**: A roving tabindex (one focusable slot at a time, arrow keys move focus, Enter/Space activates) is the standard accessible pattern for grid-like widgets (WAI-ARIA Authoring Practices "grid" pattern) and avoids putting 288 elements in the page's normal Tab order, which would make Tab alone unusable for navigating past the grid. Existing blocks (rendered as real, always-tabbable elements) are reached the same way they are today — by Tab — since there are only a handful per day.

**Alternatives considered**:
- Making every 5-minute slot a normal tab-stop — rejected: Tab-ing past 288 stops to reach anything after the grid (or to leave it) is a genuine usability regression, which is exactly why the persistent "Add block" control (`/speckit-clarify` Q4) was added.

## 4. Draft-cache scoping and lifecycle

**Decision** (per feature owner's plan input, refining `/speckit-clarify` Q2): a single draft object, scoped to the currently viewed day, held in a plain `ref` inside a new `useBlockDraft(dateRef)` composable — not a store, not persisted, cleared automatically on day change (via a `watch` on the date, mirroring the existing pattern in `useDaySchedule`/`DayView.vue`). Backdrop-click closes the popup but keeps the draft; the next empty-slot activation on the *same* day reopens the popup pre-filled with the draft's `type`/`name`/`activityId`/duration, but adopts the *newly clicked slot's* start time. Escape, the popup's own close control, a successful create/save, or navigating to a different day all discard the draft.

**Rationale**: Matches the constitution's "no state-management library" constraint literally — this is one `ref` local to one composable instance, owned by `DayView.vue`, with the same lifecycle discipline already used for `day`/`dayActivities`/`horizon`.

**Alternatives considered**:
- Scoping the draft to the specific slot/block it was opened for (as the original `/speckit-clarify` Q2 answer implied) rather than the day — superseded by the feature owner's plan input, which explicitly redefines this as day-scoped with start-time adoption from the newly clicked slot. This spec-vs-plan refinement is intentional (plan inputs may sharpen an already-resolved clarification) and does not reopen the original ambiguity.
- `sessionStorage`/`localStorage` persistence of the draft — rejected as speculative; nothing in the spec asks the draft to survive a page reload or tab close.

## 5. Loading/error presentation (deferred from `/speckit-clarify` Q3)

**Decision** (per feature owner's plan input): replace-in-place. The grid area shows the existing "Loading…" text while `loading` is true and "Could not load this day." on `error`, exactly where the grid would otherwise render — no separate overlay layer.

**Rationale**: Zero new UI surface to build or test beyond what `DayView.vue` already does (`v-if="loading"` / `v-else-if="error"` / `v-else <DayGrid ...>`); consistent with Constitution Principle V.

**Alternatives considered**: An overlay on a skeleton grid — explicitly rejected by the feature owner's plan input as unnecessary extra surface for a personal, single-user scheduler.

## 6. Default time for the persistent "Add block" control

**Decision**: When activated, the "Add block" control opens the creation popup with a default start time computed the same way as FR-021's initial-scroll rule: the current time (rounded down to the nearest 15 minutes) if the viewed day is today, or the start of the day (00:00) otherwise — end time defaults to 1 hour later, exactly as for a slot click (FR-008).

**Rationale**: FR-018 requires only "an adjustable default time" without pinning its value; reusing the FR-021 today-vs-other-day rule keeps the mental model consistent (the same rule already answers "where does the grid start looking" and "what time does a fresh block start at") without introducing a second, different default-time rule.

**Alternatives considered**: Always defaulting to 09:00 or another fixed time — rejected as an arbitrary, inconsistent second rule when a rule for "sensible default time for this day" already exists (FR-021).

## 7. Testing strategy

**Decision** (per feature owner's plan input): `DayTimeline.spec.js` is deleted, not patched — its gap-computation and chronological-ordering assertions test a model (interleaved block/gap list items) that no longer exists once FR-007 removes explicit "Free" items. Its place is taken by:
- `time-grid-utils.spec.js` — pure-function tests for `toMinutes`/`formatMinutes`/`effectiveStart`/`effectiveEnd`/`minutesToPercent`/the two snapping functions (15-minute click snap, 5-minute adjustment/keyboard-step snap).
- `DayGrid.spec.js` — component tests for proportional positioning, per-type visual distinction, midnight-clamp rendering (`startsPreviousDay`/`endsNextDay`) with the continuation indicator, short-block (≤5 min) compact-label/tooltip behavior, current-time indicator positioning (today vs. another day), and the roving-tabindex keyboard path including the persistent "Add block" control.
- `BlockPopup.spec.js` — dialog semantics (focus trap, Escape, focus return — to the extent testable under jsdom/Vue Test Utils without a real browser focus manager), create/edit/delete flows, the multi-fragment in-place confirmation choice, backdrop-click draft retention and restoration, and mapped-error-message display (reusing the existing `errorMessages.js` dictionary, unchanged).
- `DayView.spec.js` — updated for the new popup-based interaction (replacing its current inline-form assertions), plus new coverage for day-navigation being suspended while a popup is open and resuming after close (`/speckit-clarify` Q1).

**Rationale**: Explicit instruction from the feature owner; also the only approach consistent with Constitution Principle III, since patched tests would otherwise keep asserting a list/gap model FR-007 explicitly removes.

## 8. Backend/API surface

**Decision**: No backend changes. See `contracts/api-usage.md` for the explicit list of existing endpoints this feature consumes unchanged.

**Rationale**: Explicit scope guard from the feature owner; also already established in the spec's Assumptions section.
