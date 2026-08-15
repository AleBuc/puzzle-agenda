package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.application.routine.CreateRoutineEntry;
import alebuc.puzzleagenda.application.routine.DeleteRoutineEntry;
import alebuc.puzzleagenda.application.routine.EditRoutineEntry;
import alebuc.puzzleagenda.domain.port.RoutineTemplateRepository;
import alebuc.puzzleagenda.domain.routine.RoutineTemplateEntry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Routine template endpoints (contracts/api.md "Routine Template" section). */
@RestController
@RequestMapping("/api/routine-template/entries")
public class RoutineTemplateController {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final RoutineTemplateRepository routineTemplateRepository;
    private final CreateRoutineEntry createRoutineEntry;
    private final EditRoutineEntry editRoutineEntry;
    private final DeleteRoutineEntry deleteRoutineEntry;

    public RoutineTemplateController(
            RoutineTemplateRepository routineTemplateRepository,
            CreateRoutineEntry createRoutineEntry,
            EditRoutineEntry editRoutineEntry,
            DeleteRoutineEntry deleteRoutineEntry) {
        this.routineTemplateRepository = routineTemplateRepository;
        this.createRoutineEntry = createRoutineEntry;
        this.editRoutineEntry = editRoutineEntry;
        this.deleteRoutineEntry = deleteRoutineEntry;
    }

    @GetMapping
    public List<RoutineTemplateEntryResponse> getEntries() {
        return routineTemplateRepository.findAll().stream().map(RoutineTemplateEntryResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoutineTemplateEntryResponse createEntry(@RequestBody RoutineTemplateEntryRequest request) {
        RoutineTemplateEntry entry = createRoutineEntry.execute(new CreateRoutineEntry.Command(
                request.name(), LocalTime.parse(request.startTime()), LocalTime.parse(request.endTime())));
        return RoutineTemplateEntryResponse.from(entry);
    }

    @PutMapping("/{id}")
    public RoutineTemplateEntryResponse editEntry(@PathVariable UUID id, @RequestBody RoutineTemplateEntryRequest request) {
        RoutineTemplateEntry entry = editRoutineEntry.execute(id, new EditRoutineEntry.Command(
                request.name(), LocalTime.parse(request.startTime()), LocalTime.parse(request.endTime())));
        return RoutineTemplateEntryResponse.from(entry);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@PathVariable UUID id) {
        deleteRoutineEntry.execute(id);
    }

    public record RoutineTemplateEntryRequest(String name, String startTime, String endTime) {
    }

    public record RoutineTemplateEntryResponse(UUID id, String name, String startTime, String endTime) {
        static RoutineTemplateEntryResponse from(RoutineTemplateEntry entry) {
            return new RoutineTemplateEntryResponse(
                    entry.id(), entry.name(), entry.startTime().format(HH_MM), entry.endTime().format(HH_MM));
        }
    }
}
