package alebuc.puzzleagenda.domain.timeblock;

import alebuc.puzzleagenda.domain.exception.PlannedActivitySpansMidnightException;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An entry on a specific day (spec Key Entities "Time Block"; data-model.md
 * TimeBlock). {@code type} is immutable once created; {@code activityId} is
 * required iff {@code type == PLANNED_ACTIVITY} (domain invariant, FR-007).
 *
 * <p>Treated as an immutable snapshot with identity by {@code id}: edits
 * ({@link #withRangeAndName}) produce a new instance rather than mutating
 * this one, matching how the repository persists it as an upsert.
 */
public final class TimeBlock {

    private final UUID id;
    private final BlockType type;
    private final TimeRange range;
    private final String name;
    private final UUID activityId;

    private TimeBlock(UUID id, BlockType type, TimeRange range, String name, UUID activityId) {
        this.id = id;
        this.type = type;
        this.range = range;
        this.name = name;
        this.activityId = activityId;
    }

    public static TimeBlock create(UUID id, BlockType type, TimeRange range, String name, UUID activityId) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(range, "range must not be null");
        requireActivityIdConsistentWithType(type, activityId);
        requireNoMidnightSpanIfPlannedActivity(type, range);
        return new TimeBlock(id, type, range, name, activityId);
    }

    private static void requireActivityIdConsistentWithType(BlockType type, UUID activityId) {
        boolean isPlannedActivity = type == BlockType.PLANNED_ACTIVITY;
        if (isPlannedActivity && activityId == null) {
            throw new IllegalArgumentException("activityId is required for a PLANNED_ACTIVITY block");
        }
        if (!isPlannedActivity && activityId != null) {
            throw new IllegalArgumentException("activityId must be null for a " + type + " block");
        }
    }

    /**
     * A {@code PLANNED_ACTIVITY} fragment MUST be confined to one calendar
     * day (FR-021) — unlike {@code ROUTINE}/{@code CONSTRAINED} blocks, which
     * may still span midnight.
     */
    private static void requireNoMidnightSpanIfPlannedActivity(BlockType type, TimeRange range) {
        if (type == BlockType.PLANNED_ACTIVITY && range.spansMidnight()) {
            throw new PlannedActivitySpansMidnightException(
                    "A PLANNED_ACTIVITY block must not span midnight: " + range);
        }
    }

    /** Same id/type/activityId, new range and name (FR-010: start/end/name edit only). */
    public TimeBlock withRangeAndName(TimeRange newRange, String newName) {
        Objects.requireNonNull(newRange, "newRange must not be null");
        requireNoMidnightSpanIfPlannedActivity(type, newRange);
        return new TimeBlock(id, type, newRange, newName, activityId);
    }

    public UUID id() {
        return id;
    }

    public BlockType type() {
        return type;
    }

    public TimeRange range() {
        return range;
    }

    /** The block's start day — used for reachability checks and day-scoped queries (FR-009). */
    public LocalDate day() {
        return range.start().toLocalDate();
    }

    public String name() {
        return name;
    }

    public Optional<UUID> activityId() {
        return Optional.ofNullable(activityId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeBlock other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
