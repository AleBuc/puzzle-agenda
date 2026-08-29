package alebuc.puzzleagenda.application.timeblock;

import alebuc.puzzleagenda.domain.exception.TimeBlockNotFoundException;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.timeblock.BlockType;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;

import java.util.Objects;
import java.util.UUID;

/**
 * Deletes a time block. With {@link Scope#SELF} (the default): only this
 * block. With {@link Scope#ACTIVITY_DAY} (FR-015; only meaningful for a
 * {@code PLANNED_ACTIVITY} block): every {@code PLANNED_ACTIVITY} fragment
 * sharing this block's {@code activityId} and day is removed in the same
 * call — the frontend decides which scope to request based on how many
 * same-activity fragments already exist that day (research.md §6); this use
 * case does not itself require confirmation for either scope.
 */
public final class DeleteTimeBlock {

    private final TimeBlockRepository timeBlockRepository;

    public DeleteTimeBlock(TimeBlockRepository timeBlockRepository) {
        this.timeBlockRepository = Objects.requireNonNull(timeBlockRepository);
    }

    public void execute(UUID blockId, Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        TimeBlock block = timeBlockRepository.findById(blockId)
                .orElseThrow(() -> new TimeBlockNotFoundException(blockId));

        if (scope == Scope.ACTIVITY_DAY && block.type() == BlockType.PLANNED_ACTIVITY) {
            UUID activityId = block.activityId().orElseThrow();
            timeBlockRepository.findByActivityIdAndDay(activityId, block.day())
                    .forEach(fragment -> timeBlockRepository.deleteById(fragment.id()));
            return;
        }

        timeBlockRepository.deleteById(blockId);
    }

    public enum Scope {
        SELF,
        ACTIVITY_DAY
    }
}
