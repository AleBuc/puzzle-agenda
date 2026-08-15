package alebuc.puzzleagenda.domain.timeblock;

import alebuc.puzzleagenda.domain.exception.InvalidTimeRangeException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A continuous, half-open {@code [start, end)} timestamp interval (research.md
 * §1) — the storage shape for both a {@link TimeBlock} and a routine template
 * entry projected onto a concrete day. Naive local wall-clock time; DST
 * transitions are out of scope (research.md §1 note).
 */
public record TimeRange(LocalDateTime start, LocalDateTime end) {

    private static final int GRANULARITY_MINUTES = 5;

    public TimeRange {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");
        requireFiveMinuteGranularity(start, "start");
        requireFiveMinuteGranularity(end, "end");
        if (!end.isAfter(start)) {
            throw new InvalidTimeRangeException(
                    "end (" + end + ") must be strictly after start (" + start + ")");
        }
    }

    private static void requireFiveMinuteGranularity(LocalDateTime value, String label) {
        if (value.getMinute() % GRANULARITY_MINUTES != 0
                || value.getSecond() != 0
                || value.getNano() != 0) {
            throw new InvalidTimeRangeException(
                    label + " (" + value + ") must fall on a 5-minute increment");
        }
    }

    /** True when {@code end} falls on a later calendar date than {@code start} (FR-014). */
    public boolean spansMidnight() {
        return !end.toLocalDate().equals(start.toLocalDate());
    }
}
