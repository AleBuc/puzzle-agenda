package alebuc.puzzleagenda.domain.exception;

import alebuc.puzzleagenda.domain.timeblock.TimeRange;

/**
 * Thrown when a candidate {@link TimeRange} intersects an existing time
 * block or template entry on the same day (FR-008, FR-016). Maps to 409
 * Conflict / {@code TIME_BLOCK_OVERLAP} (or {@code TEMPLATE_ENTRY_OVERLAP}
 * for routine template entries) at the API boundary (contracts/api.md
 * Error Conventions).
 */
public class TimeBlockOverlapException extends RuntimeException {

    private final TimeRange requested;
    private final TimeRange conflicting;

    public TimeBlockOverlapException(TimeRange requested, TimeRange conflicting) {
        super("Requested range " + requested + " overlaps existing range " + conflicting);
        this.requested = requested;
        this.conflicting = conflicting;
    }

    public TimeRange requested() {
        return requested;
    }

    public TimeRange conflicting() {
        return conflicting;
    }
}
