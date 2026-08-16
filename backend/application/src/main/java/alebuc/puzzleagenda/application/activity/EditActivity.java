package alebuc.puzzleagenda.application.activity;

import alebuc.puzzleagenda.domain.activity.Activity;
import alebuc.puzzleagenda.domain.activity.Priority;
import alebuc.puzzleagenda.domain.exception.ActivityNotFoundException;
import alebuc.puzzleagenda.domain.port.ActivityRepository;

import java.util.Objects;
import java.util.UUID;

/** Edits an activity's name/duration/priority/category; status is untouched (FR-003). */
public final class EditActivity {

    private final ActivityRepository activityRepository;

    public EditActivity(ActivityRepository activityRepository) {
        this.activityRepository = Objects.requireNonNull(activityRepository);
    }

    public Activity execute(UUID id, Command command) {
        Objects.requireNonNull(command, "command must not be null");
        Activity existing = activityRepository.findById(id)
                .orElseThrow(() -> new ActivityNotFoundException(id));

        Activity updated = existing.withDetails(
                command.name(), command.estimatedDurationMinutes(), command.priority(), command.category());
        activityRepository.save(updated);
        return updated;
    }

    public record Command(String name, int estimatedDurationMinutes, Priority priority, String category) {
    }
}
