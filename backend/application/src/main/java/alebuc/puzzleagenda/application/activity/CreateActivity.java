package alebuc.puzzleagenda.application.activity;

import alebuc.puzzleagenda.domain.activity.Activity;
import alebuc.puzzleagenda.domain.activity.Priority;
import alebuc.puzzleagenda.domain.port.ActivityRepository;

import java.util.Objects;
import java.util.UUID;

/** Creates a new backlog activity, starting UNPLANNED (FR-001, FR-002). */
public final class CreateActivity {

    private final ActivityRepository activityRepository;

    public CreateActivity(ActivityRepository activityRepository) {
        this.activityRepository = Objects.requireNonNull(activityRepository);
    }

    public Activity execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        Activity activity = Activity.create(
                UUID.randomUUID(), command.name(), command.estimatedDurationMinutes(), command.priority(), command.category());
        activityRepository.save(activity);
        return activity;
    }

    public record Command(String name, int estimatedDurationMinutes, Priority priority, String category) {
    }
}
