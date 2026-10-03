---
name: "smoke-test-prompt"
description: "Write the browser smoke-test prompt that Claude in Chrome runs against the local dev server, covering what the automated tests cannot prove (real layout, click geometry, focus, stale state, network failure). Use when an implementation or a fix is reported complete and before the PR is merged, or when asked for a smoke test, a re-test after fixes, or a prompt to test in Chrome."
argument-hint: "Feature number, branch or PR to test (default: current branch). Add 're-test' plus the previous report for a targeted re-test."
---

# Smoke-test prompt

Produce one prompt, ready to paste into Claude in Chrome, that validates a
feature or a fix in a real browser. The prompt is the deliverable: this skill
does not run the test and does not change any file.

A smoke test here is not a second pass over the unit tests. Vitest runs under
jsdom, which has no layout engine and no real focus manager, and the backend
tests never see the UI. The prompt targets exactly that gap.

## User input

```text
$ARGUMENTS
```

Take it into account before proceeding. If it names a feature, branch or PR,
test that one. If it contains a previous test report, switch to
[re-test mode](#re-test-mode).

## 1. Gather the facts

Read before writing. Never state an expected result you have not found in the
spec or in the code.

- `specs/<NNN-name>/spec.md`: user stories, functional requirements, the
  Clarifications section, Assumptions, Out of scope.
- `specs/<NNN-name>/quickstart.md`: the "Manual validation scenarios" are the
  starting list of flows.
- `specs/<NNN-name>/research.md` and `tasks.md`: bugs found and fixed during
  implementation are recorded there.
- The completion report, if one was given: what it claims, and what the spec
  requires that it does not mention.
- `frontend/tests/*.spec.js`: what is already covered, so the prompt does not
  repeat it.
- `frontend/src/api/errorMessages.js`: the exact user-facing strings.
- `frontend/src/router/index.js`: the routes (`/days/:date`, `/backlog`,
  `/routine-template`).

For a fix without a spec directory, read the PR description and the diff
instead.

## 2. Choose what goes in

Include a step only if it falls in one of these categories:

1. **Real layout**: proportions, sizes in pixels, scroll position, stacking
   order, overlapping labels.
2. **Real pointer and focus**: where a click lands after scrolling, focus
   trap, focus returning to the trigger, a full keyboard-only path.
3. **Error paths through the real API**: business rejections triggered from
   the UI, stale state, network failure.
4. **Bugs found outside the tests during implementation**: one dedicated step
   each, because the tests did not catch them the first time.
5. **Requirements the completion report is silent about**, typically
   accessible names and keyboard behaviour.
6. **Non-regression** on the views the change was not supposed to touch, and
   a clean JS console.

Leave out pure logic already covered by unit tests, and anything that only
needs `curl` (that belongs in `quickstart.md`).

## 3. Write the prompt

Language: the prompt is written in French. UI strings are quoted verbatim, in
English, as they appear in `errorMessages.js`.

Put the whole prompt in a single fenced code block. Structure:

**Header** — one paragraph starting with `Contexte :` that gives:

- what is tested (feature number or PR, branch name) and a one-sentence
  summary of the change;
- `Ouvre http://localhost:5173.`
- `Ne modifie aucun fichier.`
- the reporting rule: `Note PASS/FAIL par étape avec le texte exact des messages.`
- when geometry or focus matters: `Vrais clics souris (pas element.click() JS).`
  and `Rapporte les mesures exactes.`
- the tooling rule: if a click gets no reaction, retry once before concluding
  FAIL, and separate "FAIL app" from "flakiness outillage" in the report.

**Global rule** (optional) — an invariant that holds for every step, stated
once under `Règle globale :`. The standing one for this project: no raw
backend message (for example `TimeRange[start=...]`) and no Java or JSON dump
may ever appear in the UI; if one does, it is a FAIL and must be quoted in
full.

**Sections** — `## A. <theme>`, `## B. <theme>`, and so on. Steps are numbered
continuously across sections (1, 2, 3 … not restarting per section) so the
report can refer to "étape 13".

**Steps** — each step is an action with concrete values, then `→` and the
expected result:

- Give real values: times on the 5-minute grid, block names, the day to open.
- State the expectation precisely, including what must NOT happen
  (`14:15 attendu, pas 14:30`).
- When the outcome could be ambiguous, list the FAIL conditions explicitly.
- When two different behaviours can both be correct, do not ask for
  PASS/FAIL: ask to `Décris précisément la séquence observée.`
- When the spec does not fix the exact wording, ask to `Cite le texte exact.`
  rather than inventing one.
- For geometry, ask for numbers: element height in px, `scrollTop` before and
  after, the pre-filled time.
- For focus, ask to check `document.activeElement`, not the visual ring.
- A step that needs the human (stopping a process) says so and allows `SKIP`.

**Closing section** — always last: one quick flow on each untouched view
(`/backlog`, `/routine-template`), no error or warning in the JS console over
the whole session (Vite HMR logs excluded, Vue warnings reported), and
deletion of the test data created.

**Final report** — a closing paragraph starting with `Rapport final :` asking
for a PASS/FAIL/SKIP table per step, the exact text of every message and
accessible label noted, and any abnormal behaviour the steps did not cover.

Size: 15 to 25 steps for a feature, about 10 for a fix.

## 4. Techniques that work on this project

Reuse these rather than reinventing them.

- **Stale state with two tabs.** Open the same view in a second tab, delete
  the resource there, then act on it from the first tab without reloading.
  This produces a real 404 (`TIME_BLOCK_NOT_FOUND`, `ACTIVITY_NOT_FOUND`,
  `ROUTINE_TEMPLATE_ENTRY_NOT_FOUND`) with no devtools and no backend change.
  Expected: the mapped "no longer exists" message is visible and the view
  refreshes to the real state. Applies to day blocks, backlog activities and
  routine template entries.
- **Network failure.** The backend must be stopped (Ctrl+C or `kill`), not
  paused: a paused process keeps its socket listening, so the request hangs
  instead of failing. Expected: `Something went wrong. Please try again.`,
  never a silent failure.
- **Business rejections from the UI.** Create a block overlapping an existing
  one → `This time slot overlaps an existing block.` shown where the action
  was taken, with the form or popup still open.
- **Multi-fragment activity.** Plan the same activity twice on the same day on
  non-adjacent slots (`08:00–08:30` and `20:00–20:30`): adjacent or
  overlapping fragments merge, which would hide the case.
- **Midnight spillover.** Use a routine entry crossing midnight (for example
  `22:30–07:00`) and check both days.
- **Click after scroll.** Scroll the container first, then click with the real
  mouse, and compare `scrollTop` just before `mousedown` and at `click`.
- **Accessibility tree.** Inspect accessible names of focusable elements and
  ask for two exact examples.

## 5. Around the prompt

Outside the code block, keep it short.

- Before: one line of preconditions — the branch to check out, backend and
  frontend running (commands are in the root `README.md`; `mvn install
  -DskipTests` must be re-run from `backend/` after any backend change, since
  `spring-boot:run` on `bootstrap` resolves the other modules from `~/.m2`).
- After: name the two or three steps that carry most of the risk and say why.

## Re-test mode

When the input contains a report with FAIL steps, or the fixes that followed
it:

- Test only what failed or was fixed, and say so in the header
  (`tout le reste est déjà validé`).
- Replay each failed scenario identically, with the same values.
- Add the aggravating path, if the analysis found one (the sequence that
  re-arms the bug).
- Add one check that the fix did not over-correct the neighbouring behaviour.
- End with the same closing net: clean console, test data deleted.
- Four to six numbered checks, each under its own `## N. <title>` heading.
