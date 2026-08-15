package alebuc.puzzleagenda.domain.routine;

import alebuc.puzzleagenda.domain.exception.InvalidTimeRangeException;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A reusable, day-agnostic routine definition (spec FR-015, FR-016;
 * data-model.md RoutineTemplateEntry). {@code endTime <= startTime} denotes
 * a midnight-spanning entry (e.g. sleep 23:00-07:00), exactly as for a
 * {@code TimeBlock} (FR-014).
 *
 * <p>{@link #conflictsWith} implements FR-016's "two-day projection rule".
 * Naively projecting two entries onto the *same* reference day and
 * comparing is not enough: FR-016 explicitly requires that sleep
 * 23:00-07:00 conflicts with an entry from 06:30-07:00, but projected onto
 * the same day, sleep's tail lands on day D+1 while a same-day 06:30-07:00
 * entry lands on day D's morning — they'd never overlap under that scheme.
 * The correct model is that every entry recurs daily, so a midnight-spanning
 * entry's tail (materialized from day D) can collide with another entry's
 * own placement on day D+1's independent materialization. Checking day
 * offsets {@code {-1, 0, +1}} between the two entries' projections covers
 * every such case, since no single entry's projected span exceeds 24h.
 */
public final class RoutineTemplateEntry {

    private final UUID id;
    private final String name;
    private final LocalTime startTime;
    private final LocalTime endTime;

    private RoutineTemplateEntry(UUID id, String name, LocalTime startTime, LocalTime endTime) {
        this.id = id;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static RoutineTemplateEntry create(UUID id, String name, LocalTime startTime, LocalTime endTime) {
        Objects.requireNonNull(id, "id must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        requireFiveMinuteGranularity(startTime, "startTime");
        requireFiveMinuteGranularity(endTime, "endTime");
        return new RoutineTemplateEntry(id, name.trim(), startTime, endTime);
    }

    private static void requireFiveMinuteGranularity(LocalTime time, String label) {
        Objects.requireNonNull(time, label + " must not be null");
        if (time.getMinute() % 5 != 0 || time.getSecond() != 0 || time.getNano() != 0) {
            throw new InvalidTimeRangeException(label + " (" + time + ") must fall on a 5-minute increment");
        }
    }

    /** Same id, new name/startTime/endTime (FR-016). */
    public RoutineTemplateEntry withDetails(String newName, LocalTime newStartTime, LocalTime newEndTime) {
        return create(id, newName, newStartTime, newEndTime);
    }

    /**
     * Projects this entry onto a concrete day as a continuous {@code [start, end)}
     * interval, using the same midnight-spanning rule as a {@code TimeBlock}
     * (FR-014) — {@code endTime <= startTime} means the interval ends on
     * {@code referenceDay + 1}.
     */
    public TimeRange projectOnto(LocalDate referenceDay) {
        Objects.requireNonNull(referenceDay, "referenceDay must not be null");
        LocalDateTime start = referenceDay.atTime(startTime);
        LocalDateTime end = endTime.compareTo(startTime) <= 0
                ? referenceDay.plusDays(1).atTime(endTime)
                : referenceDay.atTime(endTime);
        return new TimeRange(start, end);
    }

    public boolean spansMidnight() {
        return endTime.compareTo(startTime) <= 0;
    }

    /** True if this entry and {@code other} would ever produce colliding materialized blocks (FR-016). */
    public boolean conflictsWith(RoutineTemplateEntry other) {
        Objects.requireNonNull(other, "other must not be null");
        LocalDate referenceDay = LocalDate.EPOCH;
        TimeRange thisRange = projectOnto(referenceDay);
        for (int dayOffset = -1; dayOffset <= 1; dayOffset++) {
            TimeRange otherRange = other.projectOnto(referenceDay.plusDays(dayOffset));
            if (thisRange.start().isBefore(otherRange.end()) && otherRange.start().isBefore(thisRange.end())) {
                return true;
            }
        }
        return false;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public LocalTime startTime() {
        return startTime;
    }

    public LocalTime endTime() {
        return endTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoutineTemplateEntry other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
