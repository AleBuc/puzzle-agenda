package alebuc.puzzleagenda.domain.exception;

import java.time.LocalDate;

/**
 * Thrown when a requested date is later than {@code forwardBound}
 * ({@code today + 13 days}) — a syntactically valid date the reachable
 * window hasn't grown to yet (FR-009). Maps to 422 Unprocessable Entity /
 * {@code DAY_BEYOND_FORWARD_HORIZON} at the API boundary (contracts/api.md
 * Error Conventions).
 */
public class DayBeyondForwardHorizonException extends RuntimeException {

    private final LocalDate date;
    private final LocalDate forwardBound;

    public DayBeyondForwardHorizonException(LocalDate date, LocalDate forwardBound) {
        super("Day " + date + " is beyond the planning horizon; the forward bound is " + forwardBound);
        this.date = date;
        this.forwardBound = forwardBound;
    }

    public LocalDate date() {
        return date;
    }

    public LocalDate forwardBound() {
        return forwardBound;
    }
}
