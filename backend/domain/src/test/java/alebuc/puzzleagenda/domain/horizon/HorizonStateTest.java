package alebuc.puzzleagenda.domain.horizon;

import alebuc.puzzleagenda.domain.exception.DayBeyondForwardHorizonException;
import alebuc.puzzleagenda.domain.exception.DayNotReachableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class HorizonStateTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);

    @Test
    void notYetEstablishedHasNoDay1() {
        HorizonState state = HorizonState.notYetEstablished();

        assertThat(state.isEstablished()).isFalse();
        assertThat(state.day1()).isEmpty();
    }

    @Test
    void establishIfNeededSetsDay1ToTodayOnlyOnce() {
        HorizonState notEstablished = HorizonState.notYetEstablished();

        HorizonState established = notEstablished.establishIfNeeded(TODAY);
        assertThat(established.day1()).contains(TODAY);

        // A later action targeting a different day must never move Day 1
        // once it is set (spec Key Entities "Day 1").
        HorizonState reEstablished = established.establishIfNeeded(TODAY.plusDays(5));
        assertThat(reEstablished.day1()).contains(TODAY);
    }

    @Test
    void forwardBoundIsAlwaysTodayPlus13Days() {
        HorizonState state = HorizonState.withDay1(TODAY.minusDays(30));

        assertThat(state.forwardBound(TODAY)).isEqualTo(TODAY.plusDays(13));
    }

    @ParameterizedTest(name = "[{index}] day1={0} today={1} date={2} -> reachable")
    @MethodSource("reachableDates")
    void checkReachableAcceptsDatesWithinBothBounds(LocalDate day1, LocalDate today, LocalDate date) {
        HorizonState state = day1 == null ? HorizonState.notYetEstablished() : HorizonState.withDay1(day1);

        assertThatCode(() -> state.checkReachable(date, today)).doesNotThrowAnyException();
    }

    static Stream<Arguments> reachableDates() {
        return Stream.of(
                // Day 1 not yet established: lower bound is today itself.
                Arguments.of(null, TODAY, TODAY),
                Arguments.of(null, TODAY, TODAY.plusDays(13)),
                // Day 1 established in the past: today and the established Day 1 are both reachable.
                Arguments.of(TODAY.minusDays(10), TODAY, TODAY.minusDays(10)),
                Arguments.of(TODAY.minusDays(10), TODAY, TODAY),
                Arguments.of(TODAY.minusDays(10), TODAY, TODAY.plusDays(13))
        );
    }

    @ParameterizedTest(name = "[{index}] day1={0} today={1} date={2} -> DayNotReachableException")
    @MethodSource("datesBeforeLowerBound")
    void checkReachableRejectsDatesBeforeTheLowerBound(LocalDate day1, LocalDate today, LocalDate date) {
        HorizonState state = day1 == null ? HorizonState.notYetEstablished() : HorizonState.withDay1(day1);

        assertThatThrownBy(() -> state.checkReachable(date, today))
                .isInstanceOf(DayNotReachableException.class);
    }

    static Stream<Arguments> datesBeforeLowerBound() {
        return Stream.of(
                // Before Day 1 is established, yesterday does not exist yet either.
                Arguments.of(null, TODAY, TODAY.minusDays(1)),
                // Once Day 1 is established, a day earlier than it does not exist.
                Arguments.of(TODAY.minusDays(10), TODAY, TODAY.minusDays(11))
        );
    }

    @ParameterizedTest(name = "[{index}] day1={0} today={1} date={2} -> DayBeyondForwardHorizonException")
    @MethodSource("datesBeyondForwardBound")
    void checkReachableRejectsDatesBeyondTheForwardBound(LocalDate day1, LocalDate today, LocalDate date) {
        HorizonState state = day1 == null ? HorizonState.notYetEstablished() : HorizonState.withDay1(day1);

        assertThatThrownBy(() -> state.checkReachable(date, today))
                .isInstanceOf(DayBeyondForwardHorizonException.class);
    }

    static Stream<Arguments> datesBeyondForwardBound() {
        return Stream.of(
                // spec Edge Cases: day 13 ahead is accepted, day 14 ahead is rejected.
                Arguments.of(null, TODAY, TODAY.plusDays(14)),
                Arguments.of(TODAY.minusDays(10), TODAY, TODAY.plusDays(14))
        );
    }
}
