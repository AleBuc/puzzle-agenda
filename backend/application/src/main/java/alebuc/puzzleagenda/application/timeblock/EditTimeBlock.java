package alebuc.puzzleagenda.application.timeblock;

import alebuc.puzzleagenda.domain.exception.TimeBlockNotFoundException;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.service.OverlapPolicy;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Edits a time block's start/end/name in place, on the same day it already
 * belongs to (FR-010; contracts/api.md {@code PUT /api/blocks/{id}} takes
 * no date — only {@code HH:mm} times). {@code endTime <= startTime} still
 * denotes a midnight-spanning edit, exactly as on creation (FR-014).
 *
 * <p>No horizon re-check: the block's day was already validated reachable
 * when created, and the forward bound only ever grows over time, so a day
 * that was reachable stays reachable.
 */
public final class EditTimeBlock {

    private final TimeBlockRepository timeBlockRepository;
    private final OverlapPolicy overlapPolicy;

    public EditTimeBlock(TimeBlockRepository timeBlockRepository, OverlapPolicy overlapPolicy) {
        this.timeBlockRepository = Objects.requireNonNull(timeBlockRepository);
        this.overlapPolicy = Objects.requireNonNull(overlapPolicy);
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

        var others = timeBlockRepository.findIntersecting(candidate).stream()
                .filter(block -> !block.id().equals(blockId))
                .map(TimeBlock::range)
                .toList();
        overlapPolicy.checkNoOverlap(candidate, others);

        TimeBlock updated = existing.withRangeAndName(candidate, newName);
        timeBlockRepository.save(updated);
        return updated;
    }
}
