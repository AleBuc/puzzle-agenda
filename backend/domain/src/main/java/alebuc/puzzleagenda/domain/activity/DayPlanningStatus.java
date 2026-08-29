package alebuc.puzzleagenda.domain.activity;

/**
 * An {@link Activity}'s derived planning status for one specific day
 * (data-model.md DayPlanningStatus, FR-009). Computed on read from that
 * day's {@code PLANNED_ACTIVITY} fragments; never stored.
 */
public enum DayPlanningStatus {
    UNPLANNED,
    PARTIALLY_PLANNED,
    PLANNED;

    public static DayPlanningStatus of(int estimatedDurationMinutes, int plannedMinutes) {
        if (plannedMinutes <= 0) {
            return UNPLANNED;
        }
        if (plannedMinutes < estimatedDurationMinutes) {
            return PARTIALLY_PLANNED;
        }
        return PLANNED;
    }
}
