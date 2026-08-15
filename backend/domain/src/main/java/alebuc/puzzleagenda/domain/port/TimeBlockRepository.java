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

    /** Blocks whose start day equals {@code day}, in chronological order (FR-020). */
    List<TimeBlock> findByDay(LocalDate day);

    /** Existing blocks whose stored range intersects {@code range} (used for overlap checks). */
    List<TimeBlock> findIntersecting(TimeRange range);

    /** Insert-or-update by id. */
    void save(TimeBlock block);

    void deleteById(UUID id);
}
