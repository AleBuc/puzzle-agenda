-- Materialization idempotency marker (research.md §4; data-model.md MaterializedDay).
-- A row's presence means materialization already ran for that date, regardless
-- of how many ROUTINE blocks it produced (possibly zero).
CREATE TABLE materialized_day (
    day             DATE PRIMARY KEY,
    materialized_at TIMESTAMP NOT NULL
);

-- Singleton table holding the fixed backward bound of the reachable range
-- (research.md §5; data-model.md HorizonState). The forward bound is never
-- persisted; it is computed at request time from the server's current date.
CREATE TABLE horizon_state (
    day1 DATE
);

-- Defense-in-depth (Constitution Principle II): an expression index on the
-- constant TRUE allows at most one row in this singleton table.
CREATE UNIQUE INDEX horizon_state_singleton_idx ON horizon_state ((true));
