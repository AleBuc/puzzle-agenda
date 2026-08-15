-- Activity backlog (spec FR-001..FR-005; data-model.md Activity)
CREATE TABLE activity (
    id                          UUID PRIMARY KEY,
    name                        VARCHAR(255) NOT NULL CHECK (btrim(name) <> ''),
    estimated_duration_minutes  INTEGER NOT NULL CHECK (estimated_duration_minutes > 0),
    priority                    VARCHAR(10) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    category                    VARCHAR(255)
);

-- Time blocks, stored as a continuous [start_at, end_at) interval regardless of
-- whether they span midnight (research.md §1). "day" is a derived value
-- (start_at's calendar date), never stored as its own column.
CREATE TABLE time_block (
    id          UUID PRIMARY KEY,
    type        VARCHAR(20) NOT NULL CHECK (type IN ('ROUTINE', 'CONSTRAINED', 'PLANNED_ACTIVITY')),
    start_at    TIMESTAMP NOT NULL,
    end_at      TIMESTAMP NOT NULL,
    name        VARCHAR(255),
    activity_id UUID REFERENCES activity (id),
    span        tsrange GENERATED ALWAYS AS (tsrange(start_at, end_at, '[)')) STORED,

    CONSTRAINT time_block_end_after_start CHECK (end_at > start_at),
    CONSTRAINT time_block_start_five_minute_granularity CHECK (
        EXTRACT(MINUTE FROM start_at)::integer % 5 = 0 AND EXTRACT(SECOND FROM start_at) = 0
    ),
    CONSTRAINT time_block_end_five_minute_granularity CHECK (
        EXTRACT(MINUTE FROM end_at)::integer % 5 = 0 AND EXTRACT(SECOND FROM end_at) = 0
    ),
    -- activityId required iff type = PLANNED_ACTIVITY (data-model.md TimeBlock)
    CONSTRAINT time_block_activity_required_iff_planned CHECK (
        (type = 'PLANNED_ACTIVITY' AND activity_id IS NOT NULL)
        OR (type <> 'PLANNED_ACTIVITY' AND activity_id IS NULL)
    ),
    -- Database-level overlap enforcement (Constitution Principle II; research.md §2):
    -- a single EXCLUDE constraint covers same-day, adjacent (half-open upper bound),
    -- and midnight-spanning cases without per-day partitioning.
    EXCLUDE USING GIST (span WITH &&)
);

-- At most one non-deleted PLANNED_ACTIVITY block may reference a given activity
-- at a time (data-model.md Activity).
CREATE UNIQUE INDEX time_block_activity_id_unique ON time_block (activity_id)
    WHERE activity_id IS NOT NULL;

CREATE INDEX time_block_start_at_idx ON time_block (start_at);
