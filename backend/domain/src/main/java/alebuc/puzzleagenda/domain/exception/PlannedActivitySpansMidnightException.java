package alebuc.puzzleagenda.domain.exception;

/**
 * Thrown when a {@code PLANNED_ACTIVITY} block's range would span midnight
 * (FR-021). Unlike {@code ROUTINE}/{@code CONSTRAINED} blocks, a planned-
 * activity fragment must be confined to one calendar day. Maps to 400 Bad
 * Request / {@code PLANNED_ACTIVITY_SPANS_MIDNIGHT} at the API boundary
 * (contracts/api.md Error Conventions).
 */
public class PlannedActivitySpansMidnightException extends RuntimeException {

    public PlannedActivitySpansMidnightException(String message) {
        super(message);
    }
}
