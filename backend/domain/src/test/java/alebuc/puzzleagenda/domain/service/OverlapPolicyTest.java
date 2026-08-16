package alebuc.puzzleagenda.domain.service;

import alebuc.puzzleagenda.domain.exception.TimeBlockOverlapException;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverlapPolicyTest {

    private final OverlapPolicy policy = new OverlapPolicy();

    private static LocalDateTime dt(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 8, day, hour, minute);
    }

    @Test
    void adjacentRangesDoNotOverlap() {
        // spec Acceptance Scenario 2: one block ends exactly when the other starts.
        TimeRange first = new TimeRange(dt(16, 9, 0), dt(16, 10, 30));
        TimeRange second = new TimeRange(dt(16, 10, 30), dt(16, 11, 0));

        assertThat(policy.overlaps(first, second)).isFalse();
        assertThatCode(() -> policy.checkNoOverlap(second, List.of(first))).doesNotThrowAnyException();
    }

    @Test
    void fullyContainedRangeOverlaps() {
        // spec Acceptance Scenario 3.
        TimeRange existing = new TimeRange(dt(16, 9, 0), dt(16, 10, 0));
        TimeRange candidate = new TimeRange(dt(16, 9, 30), dt(16, 9, 45));

        assertThat(policy.overlaps(existing, candidate)).isTrue();
        assertThatThrownBy(() -> policy.checkNoOverlap(candidate, List.of(existing)))
                .isInstanceOf(TimeBlockOverlapException.class);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("overlappingRanges")
    void overlappingRangesAreRejected(String label, TimeRange a, TimeRange b) {
        assertThat(policy.overlaps(a, b)).isTrue();
        assertThatThrownBy(() -> policy.checkNoOverlap(a, List.of(b)))
                .isInstanceOf(TimeBlockOverlapException.class);
    }

    static Stream<Arguments> overlappingRanges() {
        return Stream.of(
                Arguments.of(
                        "partial overlap, same day",
                        new TimeRange(dt(16, 9, 0), dt(16, 10, 0)),
                        new TimeRange(dt(16, 9, 30), dt(16, 10, 30))),
                Arguments.of(
                        "identical ranges",
                        new TimeRange(dt(16, 9, 0), dt(16, 10, 0)),
                        new TimeRange(dt(16, 9, 0), dt(16, 10, 0))),
                Arguments.of(
                        // spec Edge Cases: sleep 23:00-06:00 (day16->17) vs. an existing jog 06:00-06:30 (day17)
                        // does NOT overlap (adjacent), but a jog starting before 06:00 would.
                        "midnight-spanning block overlaps a block on the following day",
                        new TimeRange(dt(16, 23, 0), dt(17, 6, 30)),
                        new TimeRange(dt(17, 6, 0), dt(17, 6, 30))),
                Arguments.of(
                        // spec Edge Cases worked example: sleep 23:00-07:00 vs. an existing 02:00-03:00 block.
                        "midnight-spanning block overlaps a block fully inside its spanned range",
                        new TimeRange(dt(16, 23, 0), dt(17, 7, 0)),
                        new TimeRange(dt(17, 2, 0), dt(17, 3, 0))));
    }

    @Test
    void midnightSpanningBlocksAdjacentAcrossTheBoundaryDoNotOverlap() {
        // spec Edge Cases: sleep 23:00-06:00 clipped to stop exactly where a jog starts.
        TimeRange sleep = new TimeRange(dt(16, 23, 0), dt(17, 6, 0));
        TimeRange jog = new TimeRange(dt(17, 6, 0), dt(17, 6, 30));

        assertThat(policy.overlaps(sleep, jog)).isFalse();
    }

    @Test
    void nonOverlappingRangesOnDifferentDaysDoNotOverlap() {
        TimeRange dayOne = new TimeRange(dt(16, 9, 0), dt(16, 10, 0));
        TimeRange dayTwo = new TimeRange(dt(17, 9, 0), dt(17, 10, 0));

        assertThat(policy.overlaps(dayOne, dayTwo)).isFalse();
    }
}
