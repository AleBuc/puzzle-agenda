package alebuc.puzzleagenda.application.timeblock;

import alebuc.puzzleagenda.domain.exception.TimeBlockNotFoundException;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;

import java.util.Objects;
import java.util.UUID;

/**
 * Deletes a {@code ROUTINE}/{@code CONSTRAINED} block: plain removal, no
 * other side effect (FR-012). Extended in tasks.md T052/US3 to also return
 * a linked {@code Activity} to {@code UNPLANNED} when deleting a
 * {@code PLANNED_ACTIVITY} block.
 */
public final class DeleteTimeBlock {

    private final TimeBlockRepository timeBlockRepository;

    public DeleteTimeBlock(TimeBlockRepository timeBlockRepository) {
        this.timeBlockRepository = Objects.requireNonNull(timeBlockRepository);
    }

    public void execute(UUID blockId) {
        timeBlockRepository.findById(blockId)
                .orElseThrow(() -> new TimeBlockNotFoundException(blockId));
        timeBlockRepository.deleteById(blockId);
    }
}
