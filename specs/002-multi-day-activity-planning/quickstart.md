# Quickstart: Validating Multi-Block, Multi-Day Activity Planning

Manual/scripted end-to-end validation that this feature's user stories
(spec.md) work through the real API. See `data-model.md` for entity
shapes and `contracts/api.md` (plus the unchanged parts of
`specs/001-daily-planning-core/contracts/api.md`) for the full endpoint
reference — not duplicated here.

## Prerequisites

Same as `specs/001-daily-planning-core/quickstart.md`'s Setup section
(PostgreSQL via Docker, `mvn install -DskipTests` + `mvn -pl bootstrap
spring-boot:run` for the backend, `npm install && npm run dev` for the
frontend) — this feature adds no new dependency or setup step. `$BASE` =
`http://localhost:8080/api`. `$TODAY` = today's date (`YYYY-MM-DD`).
`$TODAY_PLUS_2` = `$TODAY` + 2 days.

## Walkthrough

### 1. Plan an activity across multiple days (User Story 1)

```bash
curl -s -X POST $BASE/activities -H 'Content-Type: application/json' \
  -d '{"name":"Write report","estimatedDurationMinutes":300,"priority":"HIGH","category":"work"}'
# → 201, note "id" as $ACTIVITY_ID; no "status" field anymore (data-model.md Activity)

curl -s -X POST $BASE/days/$TODAY/blocks -H 'Content-Type: application/json' \
  -d "{\"type\":\"PLANNED_ACTIVITY\",\"startTime\":\"09:00\",\"endTime\":\"11:00\",\"activityId\":\"$ACTIVITY_ID\"}"
# → 201, a 2h fragment on $TODAY

curl -s -X POST $BASE/days/$TODAY_PLUS_2/blocks -H 'Content-Type: application/json' \
  -d "{\"type\":\"PLANNED_ACTIVITY\",\"startTime\":\"09:00\",\"endTime\":\"11:00\",\"activityId\":\"$ACTIVITY_ID\"}"
# → 201, a 2h fragment on $TODAY_PLUS_2 — no ACTIVITY_NOT_AVAILABLE rejection
#   (feature 001 would have rejected this second fragment)

curl -s "$BASE/activities?day=$TODAY" | jq ".[] | select(.id==\"$ACTIVITY_ID\")"
# → remainingMinutesForDay: 180, dayStatus: "PARTIALLY_PLANNED"

curl -s "$BASE/activities?day=$TODAY_PLUS_2" | jq ".[] | select(.id==\"$ACTIVITY_ID\")"
# → remainingMinutesForDay: 180, dayStatus: "PARTIALLY_PLANNED" — independent of $TODAY

curl -s -X POST $BASE/days/$TODAY/blocks -H 'Content-Type: application/json' \
  -d "{\"type\":\"PLANNED_ACTIVITY\",\"startTime\":\"14:00\",\"endTime\":\"17:00\",\"activityId\":\"$ACTIVITY_ID\"}"
# → 201, a 3h fragment; $TODAY's planned total is now 5h == estimate exactly
curl -s "$BASE/activities?day=$TODAY" | jq ".[] | select(.id==\"$ACTIVITY_ID\")"
# → remainingMinutesForDay: 0, dayStatus: "PLANNED"

curl -s -X POST $BASE/days/$TODAY/blocks -H 'Content-Type: application/json' \
  -d "{\"type\":\"PLANNED_ACTIVITY\",\"startTime\":\"20:00\",\"endTime\":\"21:00\",\"activityId\":\"$ACTIVITY_ID\"}"
# → 201, accepted even though $TODAY is already fully PLANNED (FR-004: never rejected)
```

### 2. Same-day fragment merging (User Story 2)

```bash
curl -s -X POST $BASE/activities -H 'Content-Type: application/json' \
  -d '{"name":"Course a pied","estimatedDurationMinutes":45,"priority":"MEDIUM","category":"sport"}'
# → 201, note "id" as $RUN_ID

curl -s -X POST $BASE/days/$TODAY/blocks -H 'Content-Type: application/json' \
  -d "{\"type\":\"PLANNED_ACTIVITY\",\"startTime\":\"07:00\",\"endTime\":\"07:20\",\"activityId\":\"$RUN_ID\"}"
curl -s -X POST $BASE/days/$TODAY/blocks -H 'Content-Type: application/json' \
  -d "{\"type\":\"PLANNED_ACTIVITY\",\"startTime\":\"18:00\",\"endTime\":\"18:25\",\"activityId\":\"$RUN_ID\"}"
# → two separate fragments, non-adjacent

curl -s -X POST $BASE/days/$TODAY/blocks -H 'Content-Type: application/json' \
  -d "{\"type\":\"PLANNED_ACTIVITY\",\"startTime\":\"07:20\",\"endTime\":\"07:35\",\"activityId\":\"$RUN_ID\"}"
# → 201, but the response block's range is 07:00-07:35 (merged with the first
#   fragment, adjacent at 07:20) — the 18:00-18:25 fragment is untouched

curl -s "$BASE/days/$TODAY" | jq '.blocks[] | select(.activityId=="'"$RUN_ID"'")'
# → exactly two blocks remain: 07:00-07:35 and 18:00-18:25 (not three)

curl -s -X POST $BASE/days/$TODAY/blocks -H 'Content-Type: application/json' \
  -d '{"type":"CONSTRAINED","startTime":"12:00","endTime":"13:00","name":"Lunch meeting"}'
curl -s -X PUT $BASE/blocks/$RUN_ID_SECOND_FRAGMENT -H 'Content-Type: application/json' \
  -d '{"startTime":"12:30","endTime":"13:15"}'
# → 409 TIME_BLOCK_OVERLAP (overlaps the CONSTRAINED block, not a same-activity
#   fragment — merge never bypasses this, FR-008)
```

### 3. Per-day and aggregate planning progress (User Story 3)

```bash
curl -s $BASE/activities | jq ".[] | select(.id==\"$ACTIVITY_ID\")"
# → totalFragmentCount: 3, plannedDayCount: 2,
#   days: [{day: $TODAY, plannedMinutes: 360, status: "PLANNED"},
#          {day: $TODAY_PLUS_2, plannedMinutes: 120, status: "PARTIALLY_PLANNED"}]

curl -s "$BASE/activities?day=$TODAY" | jq ".[] | select(.id==\"$RUN_ID\")"
# → dayStatus: "PLANNED" (45 min estimate, 45 min planned across the two merged blocks),
#   but still present and selectable — visually marked as fully planned in the UI
```

### 4. Delete fragments, and cascade-delete a multi-fragment activity (User Story 4)

```bash
# Single fragment on $TODAY_PLUS_2 → direct delete, no scope needed
curl -s -X DELETE $BASE/blocks/$FRAGMENT_ON_DAY_PLUS_2 -w '%{http_code}'
# → 204

# $TODAY has 3 fragments for $ACTIVITY_ID (07:00-... merged one aside, the
# report activity's own three blocks from step 1) — deleting one needs a scope choice:
curl -s -X DELETE "$BASE/blocks/$ONE_OF_THE_REPORT_FRAGMENTS?scope=self" -w '%{http_code}'
# → 204, only that one fragment removed, the others on $TODAY remain

curl -s -X DELETE "$BASE/blocks/$ANOTHER_REPORT_FRAGMENT?scope=activityDay" -w '%{http_code}'
# → 204, every remaining $ACTIVITY_ID fragment on $TODAY removed in one call

curl -s "$BASE/activities?day=$TODAY" | jq ".[] | select(.id==\"$ACTIVITY_ID\")"
# → dayStatus: "UNPLANNED", remainingMinutesForDay: 300

# Cascade-delete $RUN_ID, which still has 2 fragments on $TODAY
curl -s -X DELETE $BASE/activities/$RUN_ID -w '%{http_code}'
# → 409 ACTIVITY_HAS_PLANNED_FRAGMENTS, message states "2 planned fragments across 1 day(s)"

curl -s -X DELETE "$BASE/activities/$RUN_ID?confirm=true" -w '%{http_code}'
# → 204, activity and both remaining fragments removed
curl -s "$BASE/days/$TODAY" | jq '.blocks[] | select(.activityId=="'"$RUN_ID"'")'
# → empty
```

## Expected outcome

All four walkthroughs complete with the status codes annotated above and
no unexpected `TIME_BLOCK_OVERLAP` / `ACTIVITY_NOT_AVAILABLE` responses
except where explicitly called out — confirming FR-001–FR-022 end-to-end
against the real API, matching spec.md's acceptance scenarios for User
Stories 1–4.
