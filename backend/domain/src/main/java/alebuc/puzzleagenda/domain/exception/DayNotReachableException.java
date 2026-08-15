package alebuc.puzzleagenda.domain.exception;

import java.time.LocalDate;

/**
 * Thrown when a requested date is earlier than {@code HorizonState.day1} — the
 * day does not exist for this user (FR-009, spec Edge Cases). Maps to 404 Not
 * Found / {@code DAY_NOT_REACHABLE} at the API boundary (contracts/api.md
 * Error Conventions).
 */
public class DayNotReachableException extends RuntimeException {

    private final LocalDate date;

    public DayNotReachableException(LocalDate date) {
        super("Day " + date + " does not exist: it is earlier than Day 1");
        this.date = date;
    }

    public LocalDate date() {
        return date;
    }
}
