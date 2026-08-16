package alebuc.puzzleagenda.domain.exception;

import java.util.UUID;

/**
 * Thrown when deleting a currently-planned activity without {@code confirm=true}
 * (FR-005). Maps to 409 Conflict / {@code ACTIVITY_CURRENTLY_PLANNED} at the
 * API boundary (contracts/api.md Error Conventions).
 */
public class ActivityCurrentlyPlannedException extends RuntimeException {

    private final UUID id;

    public ActivityCurrentlyPlannedException(UUID id) {
        super("Activity " + id + " is currently planned; deleting it requires confirm=true");
        this.id = id;
    }

    public UUID id() {
        return id;
    }
}
