package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.domain.exception.DayBeyondForwardHorizonException;
import alebuc.puzzleagenda.domain.exception.DayNotReachableException;
import alebuc.puzzleagenda.domain.exception.InvalidTimeRangeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain/application exceptions to the {@code { "reason", "message" }}
 * bodies and status codes in contracts/api.md's Error Conventions table.
 *
 * <p>Only the three exception types that exist as of the Foundational phase
 * are handled here (INVALID_TIME_GRANULARITY, DAY_NOT_REACHABLE,
 * DAY_BEYOND_FORWARD_HORIZON). TIME_BLOCK_OVERLAP, TEMPLATE_ENTRY_OVERLAP,
 * ACTIVITY_NOT_AVAILABLE, and ACTIVITY_CURRENTLY_PLANNED get their handlers
 * added alongside the exceptions that introduce them in later user-story
 * tasks (tasks.md T025 OverlapPolicy, T050 CreateTimeBlock, T053 DeleteActivity).
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

    public record ErrorBody(String reason, String message) {
    }
}
