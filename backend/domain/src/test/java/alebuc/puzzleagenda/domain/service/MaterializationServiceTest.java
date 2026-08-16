package alebuc.puzzleagenda.domain.service;

import alebuc.puzzleagenda.domain.routine.RoutineTemplateEntry;
import alebuc.puzzleagenda.domain.timeblock.BlockType;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MaterializationServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 16);
    private final MaterializationService service = new MaterializationService();

    private static TimeBlock existingBlock(LocalDateTime start, LocalDateTime end) {
        return TimeBlock.create(UUID.randomUUID(), BlockType.CONSTRAINED, new TimeRange(start, end), "Existing", null);
    }

    private static RoutineTemplateEntry entry(String name, LocalTime start, LocalTime end) {
        return RoutineTemplateEntry.create(UUID.randomUUID(), name, start, end);
    }

    @Test
    void anEntryWithNoConflictingBlocksProducesOneUnclippedBlock() {
        RoutineTemplateEntry lunch = entry("Lunch", LocalTime.of(12, 30), LocalTime.of(13, 15));

        List<TimeBlock> created = service.materialize(DAY, List.of(lunch), List.of());

        assertThat(created).extracting(b -> b.range().start(), b -> b.range().end())
                .containsExactly(tuple(LocalDateTime.of(2026, 8, 16, 12, 30), LocalDateTime.of(2026, 8, 16, 13, 15)));
        assertThat(created.get(0).type()).isEqualTo(BlockType.ROUTINE);
        assertThat(created.get(0).name()).isEqualTo("Lunch");
    }

    @Test
    void anEntryFullyCoveredByAnExistingBlockProducesZeroBlocks() {
        // spec Edge Cases: "template entry's full range is already entirely covered ... No
        // routine block is created for that entry."
        RoutineTemplateEntry lunch = entry("Lunch", LocalTime.of(12, 30), LocalTime.of(13, 15));
        TimeBlock covering = existingBlock(
                LocalDateTime.of(2026, 8, 16, 12, 0), LocalDateTime.of(2026, 8, 16, 14, 0));

        List<TimeBlock> created = service.materialize(DAY, List.of(lunch), List.of(covering));

        assertThat(created).isEmpty();
    }

    @Test
    void anEntryPartiallyClippedFromTheStartProducesOneClippedBlock() {
        RoutineTemplateEntry lunch = entry("Lunch", LocalTime.of(12, 0), LocalTime.of(13, 0));
        TimeBlock leading = existingBlock(
                LocalDateTime.of(2026, 8, 16, 11, 30), LocalDateTime.of(2026, 8, 16, 12, 30));

        List<TimeBlock> created = service.materialize(DAY, List.of(lunch), List.of(leading));

        assertThat(created).extracting(b -> b.range().start(), b -> b.range().end())
                .containsExactly(tuple(LocalDateTime.of(2026, 8, 16, 12, 30), LocalDateTime.of(2026, 8, 16, 13, 0)));
    }

    @Test
    void aMidnightSpanningEntryClippedByABlockInTheMiddleSplitsIntoTwoBlocks() {
        // spec Edge Cases worked example: sleep 23:00-07:00, existing block 02:00-03:00
        // -> two sleep blocks, 23:00-02:00 and 03:00-07:00.
        RoutineTemplateEntry sleep = entry("Sleep", LocalTime.of(23, 0), LocalTime.of(7, 0));
        TimeBlock middleBlock = existingBlock(
                LocalDateTime.of(2026, 8, 17, 2, 0), LocalDateTime.of(2026, 8, 17, 3, 0));

        List<TimeBlock> created = service.materialize(DAY, List.of(sleep), List.of(middleBlock));

        assertThat(created).extracting(b -> b.range().start(), b -> b.range().end())
                .containsExactlyInAnyOrder(
                        tuple(LocalDateTime.of(2026, 8, 16, 23, 0), LocalDateTime.of(2026, 8, 17, 2, 0)),
                        tuple(LocalDateTime.of(2026, 8, 17, 3, 0), LocalDateTime.of(2026, 8, 17, 7, 0)));
        assertThat(created).allMatch(b -> b.name().equals("Sleep") && b.type() == BlockType.ROUTINE);
    }

    @Test
    void aMidnightSpanningEntryClippedByABlockOnTheFollowingDayAlsoProducesATrailingRemainder() {
        // spec Edge Cases worked example: sleep 23:00-07:00, existing jog 06:00-06:30 on the
        // following day -> clipped to 23:00-06:00 ("stopping where the jog starts") *and*, per
        // FR-017's general splitting rule, a 06:30-07:00 remainder (see MaterializationService's
        // class-level interpretation note on this specific example).
        RoutineTemplateEntry sleep = entry("Sleep", LocalTime.of(23, 0), LocalTime.of(7, 0));
        TimeBlock jog = existingBlock(
                LocalDateTime.of(2026, 8, 17, 6, 0), LocalDateTime.of(2026, 8, 17, 6, 30));

        List<TimeBlock> created = service.materialize(DAY, List.of(sleep), List.of(jog));

        assertThat(created).extracting(b -> b.range().start(), b -> b.range().end())
                .containsExactlyInAnyOrder(
                        tuple(LocalDateTime.of(2026, 8, 16, 23, 0), LocalDateTime.of(2026, 8, 17, 6, 0)),
                        tuple(LocalDateTime.of(2026, 8, 17, 6, 30), LocalDateTime.of(2026, 8, 17, 7, 0)));
    }

    @Test
    void aBlockSpillingInFromThePreviousDayClipsAnEntryStartingAtMidnight() {
        // spillover from the previous day: a block that started yesterday and spills into DAY.
        RoutineTemplateEntry morningRoutine = entry("Wake up", LocalTime.of(0, 0), LocalTime.of(1, 0));
        TimeBlock spilloverFromYesterday = existingBlock(
                LocalDateTime.of(2026, 8, 15, 22, 0), LocalDateTime.of(2026, 8, 16, 0, 30));

        List<TimeBlock> created = service.materialize(DAY, List.of(morningRoutine), List.of(spilloverFromYesterday));

        assertThat(created).extracting(b -> b.range().start(), b -> b.range().end())
                .containsExactly(tuple(LocalDateTime.of(2026, 8, 16, 0, 30), LocalDateTime.of(2026, 8, 16, 1, 0)));
    }

    @Test
    void unrelatedCandidateBlocksThatDoNotIntersectAreIgnored() {
        RoutineTemplateEntry lunch = entry("Lunch", LocalTime.of(12, 30), LocalTime.of(13, 15));
        TimeBlock unrelated = existingBlock(
                LocalDateTime.of(2026, 8, 16, 18, 0), LocalDateTime.of(2026, 8, 16, 19, 0));

        List<TimeBlock> created = service.materialize(DAY, List.of(lunch), List.of(unrelated));

        assertThat(created).hasSize(1);
        assertThat(created.get(0).range().start()).isEqualTo(LocalDateTime.of(2026, 8, 16, 12, 30));
    }

    @Test
    void materializationNeverFailsAsAWholeWhenOneEntryConflicts() {
        // FR-017: "Materialization of a day MUST always complete for every template entry,
        // never failing as a whole because one entry conflicts with existing blocks."
        RoutineTemplateEntry lunch = entry("Lunch", LocalTime.of(12, 30), LocalTime.of(13, 15));
        RoutineTemplateEntry gym = entry("Gym", LocalTime.of(18, 0), LocalTime.of(19, 0));
        TimeBlock coveringLunch = existingBlock(
                LocalDateTime.of(2026, 8, 16, 12, 0), LocalDateTime.of(2026, 8, 16, 14, 0));

        List<TimeBlock> created = service.materialize(DAY, List.of(lunch, gym), List.of(coveringLunch));

        assertThat(created).hasSize(1);
        assertThat(created.get(0).name()).isEqualTo("Gym");
    }
}
