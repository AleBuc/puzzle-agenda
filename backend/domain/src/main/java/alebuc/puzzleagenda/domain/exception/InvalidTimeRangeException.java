package alebuc.puzzleagenda.domain.exception;

/**
 * Thrown when a start/end time pair is not a valid 5-minute-increment,
 * strictly-positive interval (FR-006, FR-015). Maps to 400 Bad Request /
 * {@code INVALID_TIME_GRANULARITY} at the API boundary (contracts/api.md
 * Error Conventions).
 */
public class InvalidTimeRangeException extends RuntimeException {

    public InvalidTimeRangeException(String message) {
        super(message);
    }
}
