package alebuc.puzzleagenda.domain.exception;

import java.util.UUID;

/**
 * Thrown when deleting an activity that has one or more planned fragments,
 * across any day, without {@code confirm=true} (FR-016). States the exact
 * total fragment count so the caller can present it before the user
 * confirms. Maps to 409 Conflict / {@code ACTIVITY_HAS_PLANNED_FRAGMENTS} at
 * the API boundary (contracts/api.md Error Conventions).
 */
public class ActivityHasPlannedFragmentsException extends RuntimeException {

    private final UUID id;
    private final int fragmentCount;

    public ActivityHasPlannedFragmentsException(UUID id, int fragmentCount, int dayCount) {
        super("Activity " + id + " has " + fragmentCount + " planned fragment(s) across " + dayCount
                + " day(s); deleting it requires confirm=true");
        this.id = id;
        this.fragmentCount = fragmentCount;
    }

    public UUID id() {
        return id;
    }

    public int fragmentCount() {
        return fragmentCount;
    }
}
