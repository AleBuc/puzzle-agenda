package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.domain.activity.Activity;
import alebuc.puzzleagenda.domain.port.ActivityRepository;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

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

    /** Single-block CRUD responses have no "day being viewed" context — {@code startsPreviousDay} is always false. */
    public TimeBlockResponse toResponse(TimeBlock block) {
        return toResponse(block, block.day());
    }

    /** Used when rendering a day's block list, so {@code startsPreviousDay} can be computed against {@code viewedDate}. */
    public TimeBlockResponse toResponse(TimeBlock block, LocalDate viewedDate) {
        String activityName = block.activityId()
                .flatMap(activityRepository::findById)
                .map(Activity::name)
                .orElse(null);
        return TimeBlockResponse.from(block, viewedDate, activityName);
    }
}
