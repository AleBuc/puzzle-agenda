package alebuc.puzzleagenda.application.activity;

import alebuc.puzzleagenda.domain.activity.Activity;
import alebuc.puzzleagenda.domain.activity.ActivityStatus;
import alebuc.puzzleagenda.domain.port.ActivityRepository;

import java.util.List;
import java.util.Objects;

/**
 * Lists backlog activities, optionally filtered by status (contracts/api.md
 * {@code GET /api/activities}). Not a tasks.md-named use case, but a natural
 * completion of T041/T043: the controller needs somewhere to put the read
 * side of activity management, and per Constitution Principle I it belongs
 * in the application module, not directly in the REST controller.
 */
public final class ListActivities {

    private final ActivityRepository activityRepository;

    public ListActivities(ActivityRepository activityRepository) {
        this.activityRepository = Objects.requireNonNull(activityRepository);
    }

    public List<Activity> execute(ActivityStatus statusFilter) {
        List<Activity> all = activityRepository.findAll();
        if (statusFilter == null) {
            return all;
        }
        return all.stream().filter(activity -> activity.status() == statusFilter).toList();
    }
}
