# Implementation Plan: Calendar-Style Day Grid View

**Branch**: `003-calendar-day-view` | **Date**: 2026-08-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-calendar-day-view/spec.md`

## Summary

Replace `DayView.vue`'s flat, list-based day timeline (`DayTimeline.vue` + `TimeBlockCard.vue` + a bottom add/edit form) with a vertical, time-proportional 24-hour grid that positions and sizes blocks by actual time, distinguishes the three block types visually, clamps and marks midnight-spanning blocks, shows a current-time indicator on today, and moves creation/edit/delete into an accessible popup dialog (Reka UI's `Dialog` primitive) opened either by activating an empty grid slot or an existing block. All business rules, error mapping, and the existing REST API are reused unchanged — this is a frontend-only, presentation-and-interaction-model change.

## Technical Context

**Language/Version**: JavaScript (ES2022+), Vue 3.5 (Composition API, `<script setup>`, no TypeScript — matches existing codebase)

**Primary Dependencies**: Vue 3 (`^3.5.40`, existing), Vue Router 4 (`^4.5.0`, existing, unchanged), **`reka-ui` `^2.10.4`** (new — headless, MIT-licensed accessible primitives; peer dependency `vue >= 3.4.0` is satisfied). Scoped to its `Dialog` primitive only (`DialogRoot`, `DialogTrigger`, `DialogPortal`, `DialogOverlay`, `DialogContent`, `DialogTitle`, `DialogClose`) — no other Reka component is introduced.

**Storage**: N/A — no persistence change. The existing PostgreSQL schema and REST endpoints are consumed as-is.

**Testing**: Vitest `^3.2.6` + `@vue/test-utils` `^2.4.6` (existing, jsdom environment) — matches Constitution Principle III for frontend tests.

**Target Platform**: Desktop web browser (existing SPA), served by the same Vite dev/build pipeline.

**Project Type**: Web application (monorepo `backend/` + `frontend/`) — this feature touches `frontend/` only.

**Performance Goals**: No new formal targets. The grid renders a single day's blocks (personal-scheduler scale — at most a few dozen per day per Constitution's bounded-domain framing); no virtualization or special rendering optimization is warranted.

**Constraints**: No state-management library (Constitution Principle V) — the popup draft (see data-model.md) is a plain `ref` inside a composable, not a store. Zero backend changes (spec Assumptions + explicit scope guard). `reka-ui` footprint limited to the `Dialog` primitive (explicit YAGNI guard from the feature owner).

**Scale/Scope**: Single-user, one day viewed at a time, two-week reachable horizon (existing, unchanged) — this feature does not change scale characteristics.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Hexagonal Architecture with DDD | N/A — PASS | No backend module is touched; the domain/application/infrastructure boundary is unaffected. |
| II. Data Integrity First | N/A — PASS | No schema or persistence change; overlap prevention continues to be enforced by the existing DB constraints and domain rules, consumed as-is. |
| III. Test-Backed Development | PASS | Frontend tests remain Vitest + Vue Test Utils. `DayTimeline.spec.js`'s list-model tests are rewritten (not patched) against the new grid model, per the feature owner's explicit testing-strategy input; new components/composables ship with tests before merge. |
| IV. API Contract Clarity | PASS | Zero REST contract changes (see `contracts/api-usage.md`). All existing endpoints, status codes, and error-code mappings are reused unchanged. |
| V. Simplicity Over Speculation (YAGNI) | PASS, with one justified exception | See Complexity Tracking below for the `reka-ui` addition. The draft cache is deliberately a plain composable `ref`, not a state-management library, per the feature owner's explicit instruction — no exception needed there. |

**Overall**: PASS (one documented, pre-justified exception; see Complexity Tracking).

**Post-Phase-1 re-check**: Design artifacts (`research.md`, `data-model.md`, `contracts/`) introduce no new frontend files or patterns beyond what this table already covers — `time-grid-utils.js` (pure functions, no new abstraction), `useBlockDraft.js` (a plain `ref`, explicitly not a store), `DayGrid.vue`/`GridBlock.vue`/`BlockPopup.vue` (ordinary component decomposition, no new architectural layer), and `reka-ui` scoped to its `Dialog` primitive exactly as planned. No additional Constitution exceptions arose during design; the table and its single Complexity Tracking entry stand unchanged post-design.

## Project Structure

### Documentation (this feature)

```text
specs/003-calendar-day-view/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── api-usage.md
│   └── component-contracts.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/                          # UNCHANGED by this feature
└── ...

frontend/
├── src/
│   ├── views/
│   │   └── DayView.vue                    # MODIFIED — hosts DayGrid + BlockPopup, drops the
│   │                                       # inline add/edit form and the keydown suspend logic
│   ├── components/
│   │   ├── DayGrid.vue                    # NEW — 24h grid, hour gridlines/labels, current-time
│   │   │                                  # indicator, empty-slot activation, persistent
│   │   │                                  # "Add block" control, roving-tabindex keyboard nav
│   │   ├── GridBlock.vue                  # NEW — one positioned block (type styling, midnight
│   │   │                                  # clamp + continuation indicator, short-block label/tooltip)
│   │   ├── BlockPopup.vue                 # NEW — Reka UI Dialog wrapper; create/details/edit
│   │   │                                  # modes and the in-place multi-fragment delete choice
│   │   ├── DayTimeline.vue                # REMOVED — superseded by DayGrid.vue
│   │   └── TimeBlockCard.vue              # REMOVED — superseded by GridBlock.vue
│   ├── composables/
│   │   ├── useDaySchedule.js              # UNCHANGED — createBlock/editBlock/deleteBlock/load reused as-is
│   │   └── useBlockDraft.js               # NEW — single per-viewed-day draft cache (plain ref)
│   ├── time-grid-utils.js                 # NEW — pure functions: toMinutes/formatMinutes/
│   │                                       # effectiveStart (extracted from DayTimeline.vue),
│   │                                       # minutesToPercent, snapToQuarterHour, snapToFiveMinutes
│   └── date-utils.js                      # UNCHANGED — todayIsoDate() reused for "is today" checks
└── tests/
    ├── time-grid-utils.spec.js            # NEW — replaces DayTimeline.spec.js's gap/ordering tests
    ├── DayGrid.spec.js                    # NEW — proportional layout, type styling, midnight clamp,
    │                                       # short-block labeling, current-time indicator, keyboard nav
    ├── BlockPopup.spec.js                 # NEW — dialog semantics, create/edit/delete flows, draft
    │                                       # retention on backdrop click, error mapping display
    ├── DayView.spec.js                    # MODIFIED — popup-based interaction, suspended day-nav
    │                                       # while a popup is open, keyboard "Add block" fast path
    └── DayTimeline.spec.js                # REMOVED — superseded by time-grid-utils.spec.js + DayGrid.spec.js
```

**Structure Decision**: Existing `frontend/src/{views,components,composables}` layout is kept as-is (Constitution Principle V — no new top-level structure). `DayTimeline.vue`/`TimeBlockCard.vue` are retired in favor of `DayGrid.vue`/`GridBlock.vue` because the layout model changes fundamentally (absolute/proportional positioning vs. a flex list with synthetic "gap" rows) — patching the list components in place would leave dead code paths (the gap-item logic has no place in a grid where free time is simply unrendered space, per FR-007). `BlockPopup.vue` is a single component handling both creation and details/edit/delete (with the multi-fragment choice as in-place content, not a nested dialog) rather than two separate popup components, since they share the same Reka `Dialog` shell and FR-020 requires only one to ever be open at a time.

## Complexity Tracking

> Principle V (Simplicity Over Speculation) flags any new dependency or architectural abstraction. One addition needs justification here.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| New runtime dependency: `reka-ui` (Dialog primitive only) | FR-017 requires a fully accessible dialog: focus trapped while open, Escape-to-close with focus returned to the trigger, plus FR-024's distinct backdrop-click-vs-Escape behavior. This is exactly what Radix-style dialog primitives exist to get right (focus trap edge cases, ARIA roles/labelling, portal rendering, distinguishing outside-pointer interaction from Escape). | Hand-rolling a focus trap + ARIA dialog was rejected: it is a well-known source of subtle accessibility bugs (focus escaping on dynamic content, incorrect ARIA attributes, inconsistent Escape/outside-click semantics), and User Story 4 (P3) makes correct keyboard/dialog behavior a first-class, independently-tested requirement rather than an afterthought. The feature owner explicitly scoped this to the `Dialog` primitive only (no other Reka UI component), keeping the addition minimal and auditable rather than adopting a full component/design-system library. |

No other Constitution deviations are introduced by this feature.
