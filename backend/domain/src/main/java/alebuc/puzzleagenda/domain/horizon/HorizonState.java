package alebuc.puzzleagenda.domain.horizon;

import alebuc.puzzleagenda.domain.exception.DayBeyondForwardHorizonException;
import alebuc.puzzleagenda.domain.exception.DayNotReachableException;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * The fixed backward bound (Day 1) of the reachable range, plus the
 * reachability rules derived from it (research.md §5; data-model.md
 * HorizonState; spec Key Entities "Day").
 *
 * <p>{@code day1} is set exactly once — on the first-ever materialization or
 * first-ever {@code TimeBlock} placement — to <em>today's date at that
 * moment</em>, never to the day the triggering action targeted. Before it is
 * established, the reachable range is simply {@code [today, today + 13]}.
 */
public final class HorizonState {

    private static final int FORWARD_HORIZON_DAYS = 13;

    private final LocalDate day1;

    private HorizonState(LocalDate day1) {
        this.day1 = day1;
    }

    public static HorizonState notYetEstablished() {
        return new HorizonState(null);
    }

    public static HorizonState withDay1(LocalDate day1) {
        Objects.requireNonNull(day1, "day1 must not be null");
        return new HorizonState(day1);
    }

    public Optional<LocalDate> day1() {
        return Optional.ofNullable(day1);
    }

    public boolean isEstablished() {
        return day1 != null;
    }

    /**
     * Sets Day 1 to {@code today} if it has not been established yet;
     * otherwise returns this instance unchanged (Day 1, once set, never
     * moves).
     */
    public HorizonState establishIfNeeded(LocalDate today) {
        Objects.requireNonNull(today, "today must not be null");
        return isEstablished() ? this : withDay1(today);
    }

    /** {@code today + 13 days}; never persisted, always computed at request time. */
    public LocalDate forwardBound(LocalDate today) {
        Objects.requireNonNull(today, "today must not be null");
        return today.plusDays(FORWARD_HORIZON_DAYS);
    }

    /**
     * @throws DayNotReachableException     if {@code date} is earlier than the lower bound
     *                                       (Day 1 if established, otherwise {@code today})
     * @throws DayBeyondForwardHorizonException if {@code date} is later than {@code today + 13}
     */
    public void checkReachable(LocalDate date, LocalDate today) {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(today, "today must not be null");

        LocalDate lowerBound = day1 != null ? day1 : today;
        if (date.isBefore(lowerBound)) {
            throw new DayNotReachableException(date);
        }

        LocalDate forwardBound = forwardBound(today);
        if (date.isAfter(forwardBound)) {
            throw new DayBeyondForwardHorizonException(date, forwardBound);
        }
    }
}
