package alebuc.puzzleagenda.application.routine;

import alebuc.puzzleagenda.domain.exception.TemplateEntryOverlapException;
import alebuc.puzzleagenda.domain.port.RoutineTemplateRepository;
import alebuc.puzzleagenda.domain.routine.RoutineTemplateEntry;

import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/** Creates a routine template entry, rejecting overlap with any existing entry (FR-016). */
public final class CreateRoutineEntry {

    private final RoutineTemplateRepository routineTemplateRepository;

    public CreateRoutineEntry(RoutineTemplateRepository routineTemplateRepository) {
        this.routineTemplateRepository = Objects.requireNonNull(routineTemplateRepository);
    }

    public RoutineTemplateEntry execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        RoutineTemplateEntry candidate = RoutineTemplateEntry.create(
                UUID.randomUUID(), command.name(), command.startTime(), command.endTime());

        for (RoutineTemplateEntry existing : routineTemplateRepository.findAll()) {
            if (candidate.conflictsWith(existing)) {
                throw new TemplateEntryOverlapException(
                        "Entry \"" + candidate.name() + "\" overlaps existing entry \"" + existing.name() + "\"");
            }
        }

        routineTemplateRepository.save(candidate);
        return candidate;
    }

    public record Command(String name, LocalTime startTime, LocalTime endTime) {
    }
}
