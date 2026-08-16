-- Daily routine template entries (spec FR-015, FR-016; data-model.md RoutineTemplateEntry).
-- Entries are day-agnostic (time-of-day only), so the two-day-projection overlap
-- rule (FR-016) is enforced in the domain/application layer, not as a static
-- database range constraint here (research.md's EXCLUDE constraint applies only
-- to time_block, which has concrete dates).
CREATE TABLE routine_template_entry (
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL CHECK (btrim(name) <> ''),
    start_time TIME NOT NULL,
    end_time   TIME NOT NULL,

    CONSTRAINT routine_template_entry_start_five_minute_granularity CHECK (
        EXTRACT(MINUTE FROM start_time)::integer % 5 = 0 AND EXTRACT(SECOND FROM start_time) = 0
    ),
    CONSTRAINT routine_template_entry_end_five_minute_granularity CHECK (
        EXTRACT(MINUTE FROM end_time)::integer % 5 = 0 AND EXTRACT(SECOND FROM end_time) = 0
    )
);
