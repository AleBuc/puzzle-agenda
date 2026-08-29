package alebuc.puzzleagenda.domain.timeblock;

import alebuc.puzzleagenda.domain.exception.PlannedActivitySpansMidnightException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeBlockTest {

    private static final LocalDateTime DAY1_2300 = LocalDateTime.of(2026, 8, 29, 23, 0);
    private static final LocalDateTime DAY2_0700 = LocalDateTime.of(2026, 8, 30, 7, 0);

    @ParameterizedTest
    @EnumSource(value = BlockType.class, names = "PLANNED_ACTIVITY", mode = EnumSource.Mode.EXCLUDE)
    void routineAndConstrainedBlocksMayStillSpanMidnight(BlockType type) {
        TimeBlock block = TimeBlock.create(UUID.randomUUID(), type, new TimeRange(DAY1_2300, DAY2_0700), "Sommeil", null);

        assertThat(block.range().spansMidnight()).isTrue();
    }

    @Test
    void plannedActivityBlockRejectsCreationSpanningMidnight() {
        UUID activityId = UUID.randomUUID();
        assertThatThrownBy(() -> TimeBlock.create(
                UUID.randomUUID(), BlockType.PLANNED_ACTIVITY, new TimeRange(DAY1_2300, DAY2_0700), null, activityId))
                .isInstanceOf(PlannedActivitySpansMidnightException.class);
    }

    @Test
    void plannedActivityBlockRejectsEditSpanningMidnight() {
        UUID activityId = UUID.randomUUID();
        TimeBlock block = TimeBlock.create(
                UUID.randomUUID(), BlockType.PLANNED_ACTIVITY,
                new TimeRange(LocalDateTime.of(2026, 8, 29, 20, 0), LocalDateTime.of(2026, 8, 29, 21, 0)),
                null, activityId);

        assertThatThrownBy(() -> block.withRangeAndName(new TimeRange(DAY1_2300, DAY2_0700), null))
                .isInstanceOf(PlannedActivitySpansMidnightException.class);
    }

    @Test
    void activityIdRequiredIffPlannedActivity() {
        TimeRange range = new TimeRange(LocalDateTime.of(2026, 8, 29, 9, 0), LocalDateTime.of(2026, 8, 29, 10, 0));

        assertThatThrownBy(() -> TimeBlock.create(UUID.randomUUID(), BlockType.PLANNED_ACTIVITY, range, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TimeBlock.create(UUID.randomUUID(), BlockType.ROUTINE, range, "Sleep", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
