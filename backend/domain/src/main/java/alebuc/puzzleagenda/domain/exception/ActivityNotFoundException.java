package alebuc.puzzleagenda.domain.exception;

import java.util.UUID;

/** Thrown when a referenced activity id doesn't exist. Maps to 404 Not Found. */
public class ActivityNotFoundException extends RuntimeException {

    private final UUID id;

    public ActivityNotFoundException(UUID id) {
        super("Activity " + id + " does not exist");
        this.id = id;
    }

    public UUID id() {
        return id;
    }
}
