package alebuc.puzzleagenda.domain.activity;

import java.util.Objects;
import java.util.UUID;

/**
 * A backlog item (spec FR-001..FR-004; data-model.md Activity). Planning
 * status is no longer a property of the Activity itself: since a single
 * activity may now have independent fragments on several days (feature 002),
 * status is derived per day ({@link DayPlanningStatus}) or aggregated across
 * days ({@code ActivityPlanningSummary}, application layer), never stored
 * here.
 */
public final class Activity {

    private final UUID id;
    private final String name;
    private final int estimatedDurationMinutes;
    private final Priority priority;
    private final String category;

    private Activity(UUID id, String name, int estimatedDurationMinutes, Priority priority, String category) {
        this.id = id;
        this.name = name;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.priority = priority;
        this.category = category;
    }

    public static Activity create(UUID id, String name, int estimatedDurationMinutes, Priority priority, String category) {
        Objects.requireNonNull(id, "id must not be null");
        validate(name, estimatedDurationMinutes, priority);
        return new Activity(id, name.trim(), estimatedDurationMinutes, priority, category);
    }

    /** Rehydrates from persistence. */
    public static Activity reconstitute(
            UUID id, String name, int estimatedDurationMinutes, Priority priority, String category) {
        Objects.requireNonNull(id, "id must not be null");
        validate(name, estimatedDurationMinutes, priority);
        return new Activity(id, name, estimatedDurationMinutes, priority, category);
    }

    private static void validate(String name, int estimatedDurationMinutes, Priority priority) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (estimatedDurationMinutes <= 0) {
            throw new IllegalArgumentException("estimatedDurationMinutes must be > 0");
        }
        Objects.requireNonNull(priority, "priority must not be null");
    }

    /** Same id, new name/duration/priority/category (FR-003). */
    public Activity withDetails(String newName, int newEstimatedDurationMinutes, Priority newPriority, String newCategory) {
        validate(newName, newEstimatedDurationMinutes, newPriority);
        return new Activity(id, newName.trim(), newEstimatedDurationMinutes, newPriority, newCategory);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int estimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public Priority priority() {
        return priority;
    }

    public String category() {
        return category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Activity other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
