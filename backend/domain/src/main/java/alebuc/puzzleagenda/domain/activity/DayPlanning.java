package alebuc.puzzleagenda.domain.activity;

import alebuc.puzzleagenda.domain.timeblock.TimeRange;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * An {@link Activity}'s derived planning snapshot for one specific day
 * (data-model.md DayPlanningStatus, FR-003, FR-009). {@code plannedMinutes}
 * is the raw, never-capped sum of that day's fragment durations (FR-004:
 * over-allocation is always visible, never rejected); {@code remainingMinutes}
 * floors at zero for display once the quota is met or exceeded (spec.md
 * Assumptions).
 */
public record DayPlanning(int plannedMinutes, int remainingMinutes, DayPlanningStatus status) {

    public static DayPlanning of(int estimatedDurationMinutes, List<TimeRange> fragmentRanges) {
        Objects.requireNonNull(fragmentRanges, "fragmentRanges must not be null");
        int plannedMinutes = fragmentRanges.stream()
                .mapToInt(range -> (int) Duration.between(range.start(), range.end()).toMinutes())
                .sum();
        int remainingMinutes = Math.max(0, estimatedDurationMinutes - plannedMinutes);
        DayPlanningStatus status = DayPlanningStatus.of(estimatedDurationMinutes, plannedMinutes);
        return new DayPlanning(plannedMinutes, remainingMinutes, status);
    }
}
