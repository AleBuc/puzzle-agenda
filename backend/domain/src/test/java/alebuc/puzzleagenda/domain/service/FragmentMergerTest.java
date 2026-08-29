package alebuc.puzzleagenda.domain.service;

import alebuc.puzzleagenda.domain.timeblock.BlockType;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FragmentMergerTest {

    private final FragmentMerger merger = new FragmentMerger();
    private final UUID activityId = UUID.randomUUID();

    private static LocalDateTime dt(int hour, int minute) {
        return LocalDateTime.of(2026, 8, 29, hour, minute);
    }

    private TimeBlock fragment(int startHour, int startMinute, int endHour, int endMinute) {
        return TimeBlock.create(
                UUID.randomUUID(), BlockType.PLANNED_ACTIVITY,
                new TimeRange(dt(startHour, startMinute), dt(endHour, endMinute)), null, activityId);
    }

    @Test
    void disjointFragmentsAreNotMerged() {
        TimeBlock evening = fragment(18, 0, 18, 25);
        TimeRange candidate = new TimeRange(dt(7, 0), dt(7, 20));

        FragmentMerger.Result result = merger.merge(candidate, List.of(evening));

        assertThat(result.mergedAnything()).isFalse();
        assertThat(result.mergedRange()).isEqualTo(candidate);
    }

    @Test
    void adjacentFragmentIsMergedIntoTheUnion() {
        TimeBlock morning = fragment(7, 0, 7, 20);
        TimeRange candidate = new TimeRange(dt(7, 20), dt(7, 35));

        FragmentMerger.Result result = merger.merge(candidate, List.of(morning));

        assertThat(result.mergedAnything()).isTrue();
        assertThat(result.absorbedFragments()).containsExactly(morning);
        assertThat(result.mergedRange()).isEqualTo(new TimeRange(dt(7, 0), dt(7, 35)));
    }

    @Test
    void overlappingFragmentIsMergedIntoTheUnion() {
        TimeBlock existing = fragment(9, 0, 10, 0);
        TimeRange candidate = new TimeRange(dt(9, 30), dt(10, 30));

        FragmentMerger.Result result = merger.merge(candidate, List.of(existing));

        assertThat(result.mergedRange()).isEqualTo(new TimeRange(dt(9, 0), dt(10, 30)));
    }

    @Test
    void threeWayTransitiveChainMergesInOneOperation() {
        TimeBlock left = fragment(7, 0, 7, 20);
        TimeBlock right = fragment(7, 40, 8, 0);
        // Editing the middle fragment to touch both neighbors at once (FR-006).
        TimeRange middleEdit = new TimeRange(dt(7, 20), dt(7, 40));

        FragmentMerger.Result result = merger.merge(middleEdit, List.of(left, right));

        assertThat(result.absorbedFragments()).containsExactlyInAnyOrder(left, right);
        assertThat(result.mergedRange()).isEqualTo(new TimeRange(dt(7, 0), dt(8, 0)));
    }

    @Test
    void identicalRangeMergeIsIdempotent() {
        TimeBlock existing = fragment(9, 0, 10, 0);
        TimeRange candidate = new TimeRange(dt(9, 0), dt(10, 0));

        FragmentMerger.Result result = merger.merge(candidate, List.of(existing));

        assertThat(result.mergedRange()).isEqualTo(candidate);
        assertThat(result.absorbedFragments()).containsExactly(existing);
    }
}
