package alebuc.puzzleagenda.application.routine;

import alebuc.puzzleagenda.domain.exception.RoutineTemplateEntryNotFoundException;
import alebuc.puzzleagenda.domain.port.RoutineTemplateRepository;

import java.util.Objects;
import java.util.UUID;

/**
 * Deletes a routine template entry. No cascading effect on already-materialized
 * days (FR-019) — those days' `ROUTINE` blocks have no link back to the
 * template (FR-018), so there is nothing to cascade to.
 */
public final class DeleteRoutineEntry {

    private final RoutineTemplateRepository routineTemplateRepository;

    public DeleteRoutineEntry(RoutineTemplateRepository routineTemplateRepository) {
        this.routineTemplateRepository = Objects.requireNonNull(routineTemplateRepository);
    }

    public void execute(UUID id) {
        routineTemplateRepository.findById(id)
                .orElseThrow(() -> new RoutineTemplateEntryNotFoundException(id));
        routineTemplateRepository.deleteById(id);
    }
}
