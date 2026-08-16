package alebuc.puzzleagenda.domain.routine;

import alebuc.puzzleagenda.domain.timeblock.TimeRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutineTemplateEntryTest {

    private static final LocalDate REFERENCE_DAY = LocalDate.of(2026, 8, 16);

    private static RoutineTemplateEntry entry(String name, LocalTime start, LocalTime end) {
        return RoutineTemplateEntry.create(UUID.randomUUID(), name, start, end);
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> RoutineTemplateEntry.create(UUID.randomUUID(), "  ", LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonFiveMinuteGranularity() {
        assertThatThrownBy(() -> RoutineTemplateEntry.create(UUID.randomUUID(), "Lunch", LocalTime.of(12, 3), LocalTime.of(13, 0)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void withDetailsPreservesId() {
        UUID id = UUID.randomUUID();
        RoutineTemplateEntry entry = RoutineTemplateEntry.create(id, "Lunch", LocalTime.of(12, 30), LocalTime.of(13, 15));

        RoutineTemplateEntry edited = entry.withDetails("Lunch break", LocalTime.of(12, 0), LocalTime.of(13, 0));

        assertThat(edited.id()).isEqualTo(id);
        assertThat(edited.name()).isEqualTo("Lunch break");
    }

    @Test
    void projectOntoANonMidnightSpanningEntryStaysOnTheSameDay() {
        RoutineTemplateEntry lunch = entry("Lunch", LocalTime.of(12, 30), LocalTime.of(13, 15));

        TimeRange projected = lunch.projectOnto(REFERENCE_DAY);

        assertThat(projected.start()).isEqualTo(LocalDateTime.of(2026, 8, 16, 12, 30));
        assertThat(projected.end()).isEqualTo(LocalDateTime.of(2026, 8, 16, 13, 15));
        assertThat(lunch.spansMidnight()).isFalse();
    }

    @Test
    void projectOntoAMidnightSpanningEntrySpillsIntoTheNextDay() {
        RoutineTemplateEntry sleep = entry("Sleep", LocalTime.of(23, 0), LocalTime.of(7, 0));

        TimeRange projected = sleep.projectOnto(REFERENCE_DAY);

        assertThat(projected.start()).isEqualTo(LocalDateTime.of(2026, 8, 16, 23, 0));
        assertThat(projected.end()).isEqualTo(LocalDateTime.of(2026, 8, 17, 7, 0));
        assertThat(sleep.spansMidnight()).isTrue();
    }

    // --- Two-day projection overlap rule (FR-016) ---------------------------

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("conflictingEntryPairs")
    void conflictingEntriesReportAConflict(String label, RoutineTemplateEntry a, RoutineTemplateEntry b) {
        assertThat(a.conflictsWith(b)).isTrue();
        assertThat(b.conflictsWith(a)).isTrue(); // symmetric
    }

    static Stream<Arguments> conflictingEntryPairs() {
        return Stream.of(
                Arguments.of(
                        "spec FR-016 worked example: sleep 23:00-07:00 conflicts with an entry from 06:30-07:00",
                        entry("Sleep", LocalTime.of(23, 0), LocalTime.of(7, 0)),
                        entry("Early jog", LocalTime.of(6, 30), LocalTime.of(7, 0))),
                Arguments.of(
                        "two same-day entries partially overlapping",
                        entry("A", LocalTime.of(9, 0), LocalTime.of(10, 0)),
                        entry("B", LocalTime.of(9, 30), LocalTime.of(10, 30))),
                Arguments.of(
                        "two midnight-spanning entries overlapping in their after-midnight portions",
                        entry("Sleep", LocalTime.of(23, 0), LocalTime.of(7, 0)),
                        entry("Late sleep", LocalTime.of(23, 30), LocalTime.of(6, 0))));
    }

    @Test
    void anEntryStartingExactlyWhenAMidnightSpanningEntryEndsDoesNotConflict() {
        // spec FR-016: "conflicts with an entry from 06:30-07:00, but not with one from 07:00-07:30"
        RoutineTemplateEntry sleep = entry("Sleep", LocalTime.of(23, 0), LocalTime.of(7, 0));
        RoutineTemplateEntry jog = entry("Jog", LocalTime.of(7, 0), LocalTime.of(7, 30));

        assertThat(sleep.conflictsWith(jog)).isFalse();
        assertThat(jog.conflictsWith(sleep)).isFalse();
    }

    @Test
    void nonOverlappingSameDayEntriesDoNotConflict() {
        RoutineTemplateEntry lunch = entry("Lunch", LocalTime.of(12, 30), LocalTime.of(13, 15));
        RoutineTemplateEntry gym = entry("Gym", LocalTime.of(18, 0), LocalTime.of(19, 0));

        assertThat(lunch.conflictsWith(gym)).isFalse();
    }

    @Test
    void anEntryDoesNotConflictWithItself() {
        RoutineTemplateEntry sleep = entry("Sleep", LocalTime.of(23, 0), LocalTime.of(7, 0));

        // Same instance would trivially "conflict" with itself under the day-offset-0 check;
        // this is only meaningful for two *distinct* entries, which callers are responsible for
        // excluding (e.g. an edit comparing against every entry except itself).
        assertThat(sleep.conflictsWith(sleep)).isTrue();
    }
}
