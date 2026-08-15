package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.domain.activity.Activity;
import alebuc.puzzleagenda.domain.port.ActivityRepository;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@code activityName} enrichment (tasks.md T055/US3) shared by
 * {@link DayController} and {@link TimeBlockController}, so the lookup isn't
 * duplicated across both.
 */
@Component
public class TimeBlockResponseAssembler {

    private final ActivityRepository activityRepository;

    public TimeBlockResponseAssembler(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    public TimeBlockResponse toResponse(TimeBlock block) {
        String activityName = block.activityId()
                .flatMap(activityRepository::findById)
                .map(Activity::name)
                .orElse(null);
        return TimeBlockResponse.from(block, activityName);
    }
}
