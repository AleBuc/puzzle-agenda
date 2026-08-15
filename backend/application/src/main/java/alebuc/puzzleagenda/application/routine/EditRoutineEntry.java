package alebuc.puzzleagenda.application.routine;

import alebuc.puzzleagenda.domain.exception.RoutineTemplateEntryNotFoundException;
import alebuc.puzzleagenda.domain.exception.TemplateEntryOverlapException;
import alebuc.puzzleagenda.domain.port.RoutineTemplateRepository;
import alebuc.puzzleagenda.domain.routine.RoutineTemplateEntry;

import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Edits a routine template entry, rejecting overlap with any *other* entry
 * (FR-016). Only affects days materialized after this edit (FR-019) — this
 * use case has no knowledge of, or effect on, already-materialized days.
 */
public final class EditRoutineEntry {

    private final RoutineTemplateRepository routineTemplateRepository;

    public EditRoutineEntry(RoutineTemplateRepository routineTemplateRepository) {
        this.routineTemplateRepository = Objects.requireNonNull(routineTemplateRepository);
    }

    public RoutineTemplateEntry execute(UUID id, Command command) {
        Objects.requireNonNull(command, "command must not be null");
        RoutineTemplateEntry existing = routineTemplateRepository.findById(id)
                .orElseThrow(() -> new RoutineTemplateEntryNotFoundException(id));

        RoutineTemplateEntry updated = existing.withDetails(command.name(), command.startTime(), command.endTime());

        for (RoutineTemplateEntry other : routineTemplateRepository.findAll()) {
            if (other.id().equals(id)) {
                continue;
            }
            if (updated.conflictsWith(other)) {
                throw new TemplateEntryOverlapException(
                        "Entry \"" + updated.name() + "\" overlaps existing entry \"" + other.name() + "\"");
            }
        }

        routineTemplateRepository.save(updated);
        return updated;
    }

    public record Command(String name, LocalTime startTime, LocalTime endTime) {
    }
}
