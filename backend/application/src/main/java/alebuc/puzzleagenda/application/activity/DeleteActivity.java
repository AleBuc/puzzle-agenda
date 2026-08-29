package alebuc.puzzleagenda.application.activity;

import alebuc.puzzleagenda.domain.activity.Activity;
import alebuc.puzzleagenda.domain.exception.ActivityHasPlannedFragmentsException;
import alebuc.puzzleagenda.domain.exception.ActivityNotFoundException;
import alebuc.puzzleagenda.domain.port.ActivityRepository;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Deletes a backlog activity. If it has one or more planned fragments,
 * across any day, deletion requires {@code confirm=true} and cascades to
 * every one of them (FR-016) — an activity may now have several
 * concurrent fragments (feature 002), not just one.
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
        Objects.requireNonNull(activity, "activity must not be null");

        List<TimeBlock> fragments = timeBlockRepository.findByActivityId(id);
        if (!fragments.isEmpty()) {
            if (!confirm) {
                Set<LocalDate> days = fragments.stream().map(TimeBlock::day).collect(Collectors.toSet());
                throw new ActivityHasPlannedFragmentsException(id, fragments.size(), days.size());
            }
            fragments.forEach(fragment -> timeBlockRepository.deleteById(fragment.id()));
        }

        activityRepository.deleteById(id);
    }
}
