-- An activity may now have multiple concurrent PLANNED_ACTIVITY fragments
-- (data-model.md Activity/TimeBlock, feature 002) -- the "at most one" invariant
-- from feature 001's V1 migration no longer holds.
DROP INDEX time_block_activity_id_unique;
