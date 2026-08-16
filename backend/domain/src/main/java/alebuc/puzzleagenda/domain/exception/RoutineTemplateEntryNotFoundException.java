package alebuc.puzzleagenda.domain.exception;

import java.util.UUID;

/** Thrown when a referenced routine template entry id doesn't exist. Maps to 404 Not Found. */
public class RoutineTemplateEntryNotFoundException extends RuntimeException {

    private final UUID id;

    public RoutineTemplateEntryNotFoundException(UUID id) {
        super("Routine template entry " + id + " does not exist");
        this.id = id;
    }

    public UUID id() {
        return id;
    }
}
