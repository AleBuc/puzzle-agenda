package alebuc.puzzleagenda.domain.exception;

import java.util.UUID;

/** Thrown when a referenced time block id doesn't exist. Maps to 404 Not Found. */
public class TimeBlockNotFoundException extends RuntimeException {

    private final UUID id;

    public TimeBlockNotFoundException(UUID id) {
        super("Time block " + id + " does not exist");
        this.id = id;
    }

    public UUID id() {
        return id;
    }
}
