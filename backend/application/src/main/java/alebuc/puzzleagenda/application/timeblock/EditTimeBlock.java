package alebuc.puzzleagenda.application.timeblock;

import alebuc.puzzleagenda.domain.exception.TimeBlockNotFoundException;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.service.FragmentMerger;
import alebuc.puzzleagenda.domain.service.OverlapPolicy;
import alebuc.puzzleagenda.domain.timeblock.BlockType;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Edits a time block's start/end/name in place, on the same day it already
 * belongs to (FR-010; contracts/api.md {@code PUT /api/blocks/{id}} takes
 * no date — only {@code HH:mm} times). {@code endTime <= startTime} still
 * denotes a midnight-spanning edit, exactly as on creation (FR-014) — though
 * {@link TimeBlock#withRangeAndName} now rejects this for a
 * {@code PLANNED_ACTIVITY} block (FR-021).
 *
 * <p>If the edited range touches or overlaps another {@code PLANNED_ACTIVITY}
 * fragment of the same activity on the same day, the two are merged into one
 * block covering their union (FR-005-FR-007), excluding this block's own
 * current row from the merge candidates.
 *
 * <p>No horizon re-check: the block's day was already validated reachable
 * when created, and the forward bound only ever grows over time, so a day
 * that was reachable stays reachable.
 */
public final class EditTimeBlock {

    private final TimeBlockRepository timeBlockRepository;
    private final OverlapPolicy overlapPolicy;
    private final FragmentMerger fragmentMerger;

    public EditTimeBlock(TimeBlockRepository timeBlockRepository, OverlapPolicy overlapPolicy) {
        this.timeBlockRepository = Objects.requireNonNull(timeBlockRepository);
        this.overlapPolicy = Objects.requireNonNull(overlapPolicy);
        this.fragmentMerger = new FragmentMerger();
    }

    public TimeBlock execute(UUID blockId, LocalTime newStartTime, LocalTime newEndTime, String newName) {
        TimeBlock existing = timeBlockRepository.findById(blockId)
                .orElseThrow(() -> new TimeBlockNotFoundException(blockId));

        LocalDate day = existing.day();
        LocalDateTime newStart = day.atTime(newStartTime);
        LocalDateTime newEnd = newEndTime.compareTo(newStartTime) <= 0
                ? day.plusDays(1).atTime(newEndTime)
                : day.atTime(newEndTime);
        TimeRange candidate = new TimeRange(newStart, newEnd);

        List<TimeBlock> intersecting = timeBlockRepository.findIntersecting(candidate).stream()
                .filter(block -> !block.id().equals(blockId))
                .toList();
        List<TimeRange> foreignRanges = intersecting.stream()
                .filter(block -> !isSameActivityFragment(block, existing))
                .map(TimeBlock::range)
                .toList();
        overlapPolicy.checkNoOverlap(candidate, foreignRanges);

        TimeRange finalRange = candidate;
        if (existing.type() == BlockType.PLANNED_ACTIVITY) {
            UUID activityId = existing.activityId().orElseThrow();
            List<TimeBlock> sameActivityDayFragments = timeBlockRepository.findByActivityIdAndDay(activityId, day).stream()
                    .filter(block -> !block.id().equals(blockId))
                    .toList();
            FragmentMerger.Result mergeResult = fragmentMerger.merge(candidate, sameActivityDayFragments);
            finalRange = mergeResult.mergedRange();
            mergeResult.absorbedFragments().forEach(fragment -> timeBlockRepository.deleteById(fragment.id()));
        }

        TimeBlock updated = existing.withRangeAndName(finalRange, newName);
        timeBlockRepository.save(updated);
        return updated;
    }

    private static boolean isSameActivityFragment(TimeBlock block, TimeBlock existing) {
        return existing.type() == BlockType.PLANNED_ACTIVITY
                && block.type() == BlockType.PLANNED_ACTIVITY
                && block.activityId().equals(existing.activityId());
    }
}
