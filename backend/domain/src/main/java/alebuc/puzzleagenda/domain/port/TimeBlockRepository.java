package alebuc.puzzleagenda.domain.port;

import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for {@link TimeBlock} persistence (data-model.md TimeBlock). Defined
 * here, alongside the entity, per the deferral documented in
 * {@code package-info.java} (tasks.md T024/US1).
 */
public interface TimeBlockRepository {

    Optional<TimeBlock> findById(UUID id);

    /**
     * Every {@code PLANNED_ACTIVITY} block referencing {@code activityId}, across every day —
     * an activity may now have several concurrent fragments (data-model.md Activity/TimeBlock,
     * feature 002). Used by {@code DeleteActivity} to cascade-delete every fragment, and to
     * compute cross-day aggregate planning info.
     */
    List<TimeBlock> findByActivityId(UUID activityId);

    /**
     * {@code PLANNED_ACTIVITY} blocks referencing {@code activityId} whose start day equals
     * {@code day} — the candidate list for same-activity/same-day merge (FR-005-FR-007) and for
     * that day's remaining-time/status computation (FR-003, FR-009).
     */
    List<TimeBlock> findByActivityIdAndDay(UUID activityId, LocalDate day);

    /** Blocks whose start day equals {@code day}, in chronological order (FR-020). */
    List<TimeBlock> findByDay(LocalDate day);

    /** Existing blocks whose stored range intersects {@code range} (used for overlap checks). */
    List<TimeBlock> findIntersecting(TimeRange range);

    /** Insert-or-update by id. */
    void save(TimeBlock block);

    void deleteById(UUID id);
}
