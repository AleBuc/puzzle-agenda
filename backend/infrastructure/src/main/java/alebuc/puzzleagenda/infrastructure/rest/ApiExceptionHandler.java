package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.domain.exception.DayBeyondForwardHorizonException;
import alebuc.puzzleagenda.domain.exception.DayNotReachableException;
import alebuc.puzzleagenda.domain.exception.InvalidTimeRangeException;
import alebuc.puzzleagenda.domain.exception.TimeBlockNotFoundException;
import alebuc.puzzleagenda.domain.exception.TimeBlockOverlapException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain/application exceptions to the {@code { "reason", "message" }}
 * bodies and status codes in contracts/api.md's Error Conventions table.
 *
 * <p>{@code TEMPLATE_ENTRY_OVERLAP}, {@code ACTIVITY_NOT_AVAILABLE}, and
 * {@code ACTIVITY_CURRENTLY_PLANNED} get their handlers added alongside the
 * exceptions that introduce them in later user-story tasks (tasks.md
 * T060/US4, T050/US3, T053/US3).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidTimeRangeException.class)
    public ResponseEntity<ErrorBody> handleInvalidTimeRange(InvalidTimeRangeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody("INVALID_TIME_GRANULARITY", ex.getMessage()));
    }

    @ExceptionHandler(DayNotReachableException.class)
    public ResponseEntity<ErrorBody> handleDayNotReachable(DayNotReachableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorBody("DAY_NOT_REACHABLE", ex.getMessage()));
    }

    @ExceptionHandler(DayBeyondForwardHorizonException.class)
    public ResponseEntity<ErrorBody> handleDayBeyondForwardHorizon(DayBeyondForwardHorizonException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorBody("DAY_BEYOND_FORWARD_HORIZON", ex.getMessage()));
    }

    @ExceptionHandler(TimeBlockOverlapException.class)
    public ResponseEntity<ErrorBody> handleTimeBlockOverlap(TimeBlockOverlapException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("TIME_BLOCK_OVERLAP", ex.getMessage()));
    }

    @ExceptionHandler(TimeBlockNotFoundException.class)
    public ResponseEntity<ErrorBody> handleTimeBlockNotFound(TimeBlockNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorBody("TIME_BLOCK_NOT_FOUND", ex.getMessage()));
    }

    /** Catch-all for domain constructor invariants (e.g. a missing activityId on a PLANNED_ACTIVITY block). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody("INVALID_REQUEST", ex.getMessage()));
    }

    public record ErrorBody(String reason, String message) {
    }
}
