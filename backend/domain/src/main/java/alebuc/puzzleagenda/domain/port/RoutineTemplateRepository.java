package alebuc.puzzleagenda.domain.port;

import alebuc.puzzleagenda.domain.routine.RoutineTemplateEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for {@link RoutineTemplateEntry} persistence (data-model.md
 * RoutineTemplateEntry). Defined here, alongside the entity, per the
 * deferral documented in {@code package-info.java} (tasks.md T060/US4).
 */
public interface RoutineTemplateRepository {

    Optional<RoutineTemplateEntry> findById(UUID id);

    /** All template entries, in no particular business order. */
    List<RoutineTemplateEntry> findAll();

    /** Insert-or-update by id. */
    void save(RoutineTemplateEntry entry);

    void deleteById(UUID id);
}
