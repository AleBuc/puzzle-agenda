# Quickstart: Validating Daily Schedule Planning

Manual/scripted end-to-end validation that the feature's user stories
(spec.md) work through the real API. See `data-model.md` for entity
shapes and `contracts/api.md` for the full endpoint reference — not
duplicated here.

## Prerequisites

- JDK 25, Maven, Node 20+/npm
- PostgreSQL 16+ reachable (local instance or Docker), with Flyway
  migrations applied
- `docker` available if running infrastructure tests locally
  (Testcontainers)

## Setup

```bash
# Backend (from backend/)
mvn -pl bootstrap -am spring-boot:run

# Frontend (from frontend/), separate shell
npm install
npm run dev
```

The backend serves the REST API (default `http://localhost:8080`); the
commands below use `curl` against it directly, independent of the
frontend, to validate behavior. `$BASE` = `http://localhost:8080/api`.
`$TODAY` = today's date (`YYYY-MM-DD`).

## Walkthrough

### 1. Backlog basics (User Story 2)

```bash
curl -s -X POST $BASE/activities -H 'Content-Type: application/json' \
  -d '{"name":"Grocery run","estimatedDurationMinutes":30,"priority":"MEDIUM","category":"errands"}'
# → 201, note the returned "id" as $ACTIVITY_ID; status is UNPLANNED

curl -s "$BASE/activities?status=unplanned"
# → includes the activity above
```

### 2. Build today's schedule (User Story 1)

```bash
curl -s -X POST $BASE/days/$TODAY/blocks -H 'Content-Type: application/json' \
  -d '{"type":"CONSTRAINED","startTime":"09:00","endTime":"10:30","name":"Standup + focus block"}'
# → 201

# Overlap rejected:
curl -s -o /dev/null -w '%{http_code}\n' -X POST $BASE/days/$TODAY/blocks \
  -H 'Content-Type: application/json' \
  -d '{"type":"CONSTRAINED","startTime":"09:30","endTime":"09:45"}'
# → 409

# Adjacent block accepted (end-exclusive, FR-008 scenario 2):
curl -s -o /dev/null -w '%{http_code}\n' -X POST $BASE/days/$TODAY/blocks \
  -H 'Content-Type: application/json' \
  -d '{"type":"CONSTRAINED","startTime":"10:30","endTime":"11:00"}'
# → 201

# Beyond the 13-day forward bound rejected (FR-009, SC-003):
FAR_DATE=$(date -d "$TODAY +14 days" +%F)   # macOS: date -j -v+14d -f %F "$TODAY" +%F
curl -s -o /dev/null -w '%{http_code}\n' -X POST $BASE/days/$FAR_DATE/blocks \
  -H 'Content-Type: application/json' \
  -d '{"type":"CONSTRAINED","startTime":"09:00","endTime":"10:00"}'
# → 422 (DAY_BEYOND_FORWARD_HORIZON — contracts/api.md Error Conventions;
#   404 is reserved for dates earlier than Day 1)
```

### 3. Plan the backlog activity (User Story 3)

```bash
curl -s -X POST $BASE/days/$TODAY/blocks -H 'Content-Type: application/json' \
  -d "{\"type\":\"PLANNED_ACTIVITY\",\"startTime\":\"14:00\",\"endTime\":\"15:00\",\"activityId\":\"$ACTIVITY_ID\"}"
# → 201, note "id" as $BLOCK_ID; slot (60 min) differs from the 30-min estimate, accepted (FR-013)

curl -s "$BASE/activities?status=unplanned"
# → no longer includes $ACTIVITY_ID

curl -s -X DELETE $BASE/blocks/$BLOCK_ID -o /dev/null -w '%{http_code}\n'
# → 204

curl -s "$BASE/activities?status=unplanned"
# → $ACTIVITY_ID is back (FR-012)
```

### 4. Routine template and materialization (User Story 4)

```bash
curl -s -X POST $BASE/routine-template/entries -H 'Content-Type: application/json' \
  -d '{"name":"Sleep","startTime":"23:00","endTime":"07:00"}'
# → 201 (midnight-spanning entry, endTime <= startTime)

TOMORROW=$(date -d "$TODAY +1 day" +%F)
curl -s $BASE/days/$TOMORROW
# → 200, "materialized": true, includes a ROUTINE block 23:00 → next day 07:00 (endsNextDay: true)

# Re-view: template edits afterward never touch this day (FR-019)
curl -s -X POST $BASE/routine-template/entries -H 'Content-Type: application/json' \
  -d '{"name":"Lunch","startTime":"12:30","endTime":"13:15"}'
curl -s $BASE/days/$TOMORROW
# → still only the Sleep block; Lunch appears on days materialized from now on
```

### 5. Materialization clipping against pre-existing blocks

```bash
DAY2=$(date -d "$TODAY +2 days" +%F)
DAY3=$(date -d "$TODAY +3 days" +%F)

# Pre-existing jog block on DAY3, before DAY2 is materialized:
curl -s -X POST $BASE/days/$DAY3/blocks -H 'Content-Type: application/json' \
  -d '{"type":"CONSTRAINED","startTime":"06:00","endTime":"06:30","name":"Jog"}'

curl -s $BASE/days/$DAY2
# → Sleep block clipped to 23:00–06:00 (stops where the jog block starts on DAY3),
#   per the worked example in spec.md's Edge Cases
```

### 6. Past-day parity (SC-009)

`GET /horizon` never establishes Day 1 itself — it is a pure read
(research.md §5). Day 1 was already set back in step 2, at the moment of
the *first* block placement or materialization in this walkthrough,
whichever ran first chronologically:

```bash
curl -s $BASE/horizon
# → { "day1": "<date set in step 2>", "forwardBound": "<today+13>" }
```

On a genuinely fresh install — before any block has been placed and
before any day has been materialized — this same call instead returns
`{ "day1": null, "forwardBound": "<today+13>" }` (spec Assumptions): Day 1
only becomes non-null on the first-ever materialization or first-ever
block placement, never merely from viewing the horizon.

```bash
curl -s -X POST $BASE/days/$TODAY/blocks -H 'Content-Type: application/json' \
  -d '{"type":"CONSTRAINED","startTime":"18:00","endTime":"18:30","name":"Errand"}'
# Edit/delete it exactly as any other block — a past day (once today rolls forward)
# behaves identically for create/edit/delete, minus template materialization.
```

## Automated coverage

The scenarios above correspond to the acceptance scenarios and edge cases
in `spec.md`. Automated tests exercising the same behavior live in:

- `backend/domain/src/test/java/...` — `OverlapPolicy`,
  `MaterializationService` clipping (parameterized per FR-008/FR-014/FR-017
  edge cases), `HorizonState` reachability
- `backend/infrastructure/src/test/java/...` — REST contract tests
  (Spring Boot Test) and the `EXCLUDE` constraint under Testcontainers
- `frontend/tests/` — day timeline rendering (gaps, block-type styling)
  with Vitest + Vue Test Utils
