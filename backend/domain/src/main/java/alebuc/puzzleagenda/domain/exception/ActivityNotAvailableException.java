package alebuc.puzzleagenda.domain.exception;

import java.util.UUID;

/**
 * Thrown when a {@code PLANNED_ACTIVITY} block references an {@code activityId}
 * that is not currently in the unplanned backlog — already planned elsewhere,
 * or nonexistent (FR-007). Maps to 409 Conflict / {@code ACTIVITY_NOT_AVAILABLE}
 * at the API boundary (contracts/api.md Error Conventions).
 */
public class ActivityNotAvailableException extends RuntimeException {

    private final UUID activityId;

    public ActivityNotAvailableException(UUID activityId) {
        super("Activity " + activityId + " is not available to plan (not found or already planned)");
        this.activityId = activityId;
    }

    public UUID activityId() {
        return activityId;
    }
}
