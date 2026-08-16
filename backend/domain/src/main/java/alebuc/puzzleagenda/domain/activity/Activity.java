package alebuc.puzzleagenda.domain.activity;

import java.util.Objects;
import java.util.UUID;

/**
 * A backlog item (spec FR-001..FR-005; data-model.md Activity). {@code status}
 * is a read-only projection: it is never set by domain logic in this class,
 * only hydrated by the repository (via a join against {@code time_block}) or
 * defaulted to {@code UNPLANNED} on {@link #create}.
 */
public final class Activity {

    private final UUID id;
    private final String name;
    private final int estimatedDurationMinutes;
    private final Priority priority;
    private final String category;
    private final ActivityStatus status;

    private Activity(UUID id, String name, int estimatedDurationMinutes, Priority priority, String category, ActivityStatus status) {
        this.id = id;
        this.name = name;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.priority = priority;
        this.category = category;
        this.status = status;
    }

    /** A newly created activity always starts UNPLANNED (FR-002). */
    public static Activity create(UUID id, String name, int estimatedDurationMinutes, Priority priority, String category) {
        Objects.requireNonNull(id, "id must not be null");
        validate(name, estimatedDurationMinutes, priority);
        return new Activity(id, name.trim(), estimatedDurationMinutes, priority, category, ActivityStatus.UNPLANNED);
    }

    /** Rehydrates from persistence, where {@code status} is already known (repository join). */
    public static Activity reconstitute(
            UUID id, String name, int estimatedDurationMinutes, Priority priority, String category, ActivityStatus status) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        validate(name, estimatedDurationMinutes, priority);
        return new Activity(id, name, estimatedDurationMinutes, priority, category, status);
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

    /** Same id/status, new name/duration/priority/category (FR-003). */
    public Activity withDetails(String newName, int newEstimatedDurationMinutes, Priority newPriority, String newCategory) {
        validate(newName, newEstimatedDurationMinutes, newPriority);
        return new Activity(id, newName.trim(), newEstimatedDurationMinutes, newPriority, newCategory, status);
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

    public ActivityStatus status() {
        return status;
    }

    public boolean isPlanned() {
        return status == ActivityStatus.PLANNED;
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
