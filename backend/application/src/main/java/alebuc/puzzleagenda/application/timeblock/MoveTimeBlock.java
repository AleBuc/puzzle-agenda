package alebuc.puzzleagenda.application.timeblock;

import alebuc.puzzleagenda.domain.exception.TimeBlockNotFoundException;
import alebuc.puzzleagenda.domain.horizon.HorizonState;
import alebuc.puzzleagenda.domain.port.HorizonStateRepository;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.service.FragmentMerger;
import alebuc.puzzleagenda.domain.service.OverlapPolicy;
import alebuc.puzzleagenda.domain.timeblock.BlockType;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reschedules a {@code PLANNED_ACTIVITY} block to a (possibly different) day
 * and slot (FR-011, FR-022; contracts/api.md {@code PATCH /api/blocks/{id}/move}).
 * {@code endTime <= startTime} denotes a midnight-spanning move, exactly as
 * on creation (FR-014) — though {@link TimeBlock#withRangeAndName} rejects
 * this for a {@code PLANNED_ACTIVITY} block (FR-021). The activity's own
 * identity/type is unchanged — only the range moves; the name (unused for
 * {@code PLANNED_ACTIVITY}, per data-model.md) is preserved as-is.
 *
 * <p>If the destination range touches or overlaps another
 * {@code PLANNED_ACTIVITY} fragment of the same activity on the destination
 * day, the two are merged into one block covering their union (FR-005-
 * FR-007, FR-022) — evaluated only against the destination day's fragments,
 * excluding this block's own current row (relevant when the destination day
 * equals the origin day).
 */
public final class MoveTimeBlock {

    private final TimeBlockRepository timeBlockRepository;
    private final HorizonStateRepository horizonStateRepository;
    private final OverlapPolicy overlapPolicy;
    private final FragmentMerger fragmentMerger;
    private final Clock clock;

    public MoveTimeBlock(
            TimeBlockRepository timeBlockRepository,
            HorizonStateRepository horizonStateRepository,
            OverlapPolicy overlapPolicy,
            Clock clock) {
        this.timeBlockRepository = Objects.requireNonNull(timeBlockRepository);
        this.horizonStateRepository = Objects.requireNonNull(horizonStateRepository);
        this.overlapPolicy = Objects.requireNonNull(overlapPolicy);
        this.fragmentMerger = new FragmentMerger();
        this.clock = Objects.requireNonNull(clock);
    }

    public TimeBlock execute(UUID blockId, LocalDate newDay, LocalTime newStartTime, LocalTime newEndTime) {
        TimeBlock existing = timeBlockRepository.findById(blockId)
                .orElseThrow(() -> new TimeBlockNotFoundException(blockId));

        if (existing.type() != BlockType.PLANNED_ACTIVITY) {
            throw new IllegalArgumentException(
                    "Only PLANNED_ACTIVITY blocks can be moved, but " + blockId + " is " + existing.type());
        }

        LocalDate today = LocalDate.now(clock);
        HorizonState horizonState = horizonStateRepository.load();
        horizonState.checkReachable(newDay, today);

        LocalDateTime newStart = newDay.atTime(newStartTime);
        LocalDateTime newEnd = newEndTime.compareTo(newStartTime) <= 0
                ? newDay.plusDays(1).atTime(newEndTime)
                : newDay.atTime(newEndTime);
        TimeRange candidate = new TimeRange(newStart, newEnd);

        UUID activityId = existing.activityId().orElseThrow();
        List<TimeBlock> intersecting = timeBlockRepository.findIntersecting(candidate).stream()
                .filter(block -> !block.id().equals(blockId))
                .toList();
        List<TimeRange> foreignRanges = intersecting.stream()
                .filter(block -> !isSameActivityFragment(block, activityId))
                .map(TimeBlock::range)
                .toList();
        overlapPolicy.checkNoOverlap(candidate, foreignRanges);

        List<TimeBlock> sameActivityDayFragments = timeBlockRepository.findByActivityIdAndDay(activityId, newDay).stream()
                .filter(block -> !block.id().equals(blockId))
                .toList();
        FragmentMerger.Result mergeResult = fragmentMerger.merge(candidate, sameActivityDayFragments);
        mergeResult.absorbedFragments().forEach(fragment -> timeBlockRepository.deleteById(fragment.id()));

        TimeBlock moved = existing.withRangeAndName(mergeResult.mergedRange(), existing.name());
        timeBlockRepository.save(moved);
        return moved;
    }

    private static boolean isSameActivityFragment(TimeBlock block, UUID activityId) {
        return block.type() == BlockType.PLANNED_ACTIVITY && block.activityId().map(activityId::equals).orElse(false);
    }
}
