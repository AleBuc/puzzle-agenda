package alebuc.puzzleagenda.application.activity;

import alebuc.puzzleagenda.domain.activity.Activity;
import alebuc.puzzleagenda.domain.exception.ActivityCurrentlyPlannedException;
import alebuc.puzzleagenda.domain.exception.ActivityNotFoundException;
import alebuc.puzzleagenda.domain.port.ActivityRepository;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;

import java.util.Objects;
import java.util.UUID;

/**
 * Deletes a backlog activity. If it is currently planned (has a scheduled
 * {@code TimeBlock}), deletion requires {@code confirm=true} and also
 * removes that block (FR-004, FR-005).
 *
 * <p>Built to this final, T053-extended shape directly rather than staged
 * behind a simpler T041-only version: US2 and US3 were implemented in the
 * same pass, so a temporarily-incomplete confirm/cascade flow (which would
 * hit the {@code time_block.activity_id} foreign key constraint on delete)
 * would have served no purpose.
 */
public final class DeleteActivity {

    private final ActivityRepository activityRepository;
    private final TimeBlockRepository timeBlockRepository;

    public DeleteActivity(ActivityRepository activityRepository, TimeBlockRepository timeBlockRepository) {
        this.activityRepository = Objects.requireNonNull(activityRepository);
        this.timeBlockRepository = Objects.requireNonNull(timeBlockRepository);
    }

    public void execute(UUID id, boolean confirm) {
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new ActivityNotFoundException(id));

        if (activity.isPlanned()) {
            if (!confirm) {
                throw new ActivityCurrentlyPlannedException(id);
            }
            timeBlockRepository.findByActivityId(id).map(TimeBlock::id).ifPresent(timeBlockRepository::deleteById);
        }

        activityRepository.deleteById(id);
    }
}
