package alebuc.puzzleagenda.application.activity;

import alebuc.puzzleagenda.domain.activity.Activity;
import alebuc.puzzleagenda.domain.activity.DayPlanning;
import alebuc.puzzleagenda.domain.activity.DayPlanningStatus;
import alebuc.puzzleagenda.domain.port.ActivityRepository;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Lists backlog activities (contracts/api.md {@code GET /api/activities}).
 * With no day (the backlog/aggregate view, FR-012-FR-013): each activity
 * carries its total fragment count and a sparse per-day breakdown across
 * every reachable day that has at least one fragment. With a {@code day}
 * (the day view's activity selector, FR-010-FR-011): each activity
 * additionally carries its remaining time and status for exactly that day.
 * All of this is computed on read from {@code TimeBlockRepository}, never
 * stored (FR-003, FR-009).
 */
public final class ListActivities {

    private final ActivityRepository activityRepository;
    private final TimeBlockRepository timeBlockRepository;

    public ListActivities(ActivityRepository activityRepository, TimeBlockRepository timeBlockRepository) {
        this.activityRepository = Objects.requireNonNull(activityRepository);
        this.timeBlockRepository = Objects.requireNonNull(timeBlockRepository);
    }

    /** Aggregate (backlog) view — no specific day requested. */
    public List<ActivityView> execute() {
        return execute(null);
    }

    /** Day-scoped view when {@code day} is non-null, aggregate view otherwise. */
    public List<ActivityView> execute(LocalDate day) {
        return activityRepository.findAll().stream()
                .map(activity -> describe(activity, day))
                .toList();
    }

    /** Builds the view for a single, already-loaded activity (e.g. right after create/edit). */
    public ActivityView describe(Activity activity, LocalDate day) {
        List<TimeBlock> allFragments = timeBlockRepository.findByActivityId(activity.id());
        Map<LocalDate, List<TimeRange>> rangesByDay = allFragments.stream()
                .collect(Collectors.groupingBy(TimeBlock::day, Collectors.mapping(TimeBlock::range, Collectors.toList())));

        List<ActivityView.DaySummary> days = rangesByDay.entrySet().stream()
                .map(entry -> {
                    DayPlanning planning = DayPlanning.of(activity.estimatedDurationMinutes(), entry.getValue());
                    return new ActivityView.DaySummary(entry.getKey(), planning.plannedMinutes(), planning.status());
                })
                .sorted(Comparator.comparing(ActivityView.DaySummary::day))
                .toList();

        DayPlanning dayPlanning = day == null
                ? null
                : DayPlanning.of(activity.estimatedDurationMinutes(), rangesByDay.getOrDefault(day, List.of()));

        return new ActivityView(activity, allFragments.size(), days.size(), days, dayPlanning);
    }

    public record ActivityView(
            Activity activity,
            int totalFragmentCount,
            int plannedDayCount,
            List<DaySummary> days,
            DayPlanning dayPlanning) {

        public record DaySummary(LocalDate day, int plannedMinutes, DayPlanningStatus status) {
        }
    }
}
