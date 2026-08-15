package alebuc.puzzleagenda.application.timeblock;

import alebuc.puzzleagenda.domain.exception.TimeBlockNotFoundException;
import alebuc.puzzleagenda.domain.horizon.HorizonState;
import alebuc.puzzleagenda.domain.port.HorizonStateRepository;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.service.OverlapPolicy;
import alebuc.puzzleagenda.domain.timeblock.BlockType;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Reschedules a {@code PLANNED_ACTIVITY} block to a (possibly different) day
 * and slot (FR-011; contracts/api.md {@code PATCH /api/blocks/{id}/move}).
 * {@code endTime <= startTime} denotes a midnight-spanning move, exactly as
 * on creation (FR-014). The activity's own identity/type is unchanged — only
 * the range moves; the name (unused for {@code PLANNED_ACTIVITY}, per
 * data-model.md) is preserved as-is.
 */
public final class MoveTimeBlock {

    private final TimeBlockRepository timeBlockRepository;
    private final HorizonStateRepository horizonStateRepository;
    private final OverlapPolicy overlapPolicy;
    private final Clock clock;

    public MoveTimeBlock(
            TimeBlockRepository timeBlockRepository,
            HorizonStateRepository horizonStateRepository,
            OverlapPolicy overlapPolicy,
            Clock clock) {
        this.timeBlockRepository = Objects.requireNonNull(timeBlockRepository);
        this.horizonStateRepository = Objects.requireNonNull(horizonStateRepository);
        this.overlapPolicy = Objects.requireNonNull(overlapPolicy);
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

        var others = timeBlockRepository.findIntersecting(candidate).stream()
                .filter(block -> !block.id().equals(blockId))
                .map(TimeBlock::range)
                .toList();
        overlapPolicy.checkNoOverlap(candidate, others);

        TimeBlock moved = existing.withRangeAndName(candidate, existing.name());
        timeBlockRepository.save(moved);
        return moved;
    }
}
