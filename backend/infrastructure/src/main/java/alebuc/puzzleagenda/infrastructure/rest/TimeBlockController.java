package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.application.timeblock.CreateTimeBlock;
import alebuc.puzzleagenda.application.timeblock.DeleteTimeBlock;
import alebuc.puzzleagenda.application.timeblock.EditTimeBlock;
import alebuc.puzzleagenda.application.timeblock.MoveTimeBlock;
import alebuc.puzzleagenda.domain.timeblock.BlockType;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/** Time block endpoints (contracts/api.md "Time Blocks" section). */
@RestController
@RequestMapping("/api")
public class TimeBlockController {

    private final CreateTimeBlock createTimeBlock;
    private final EditTimeBlock editTimeBlock;
    private final DeleteTimeBlock deleteTimeBlock;
    private final MoveTimeBlock moveTimeBlock;
    private final TimeBlockResponseAssembler responseAssembler;

    public TimeBlockController(
            CreateTimeBlock createTimeBlock,
            EditTimeBlock editTimeBlock,
            DeleteTimeBlock deleteTimeBlock,
            MoveTimeBlock moveTimeBlock,
            TimeBlockResponseAssembler responseAssembler) {
        this.createTimeBlock = createTimeBlock;
        this.editTimeBlock = editTimeBlock;
        this.deleteTimeBlock = deleteTimeBlock;
        this.moveTimeBlock = moveTimeBlock;
        this.responseAssembler = responseAssembler;
    }

    @PostMapping("/days/{date}/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public TimeBlockResponse createBlock(@PathVariable LocalDate date, @RequestBody CreateTimeBlockRequest request) {
        LocalTime start = LocalTime.parse(request.startTime());
        LocalTime end = LocalTime.parse(request.endTime());
        LocalDateTime startAt = date.atTime(start);
        LocalDateTime endAt = end.compareTo(start) <= 0 ? date.plusDays(1).atTime(end) : date.atTime(end);

        TimeBlock block = createTimeBlock.execute(
                new CreateTimeBlock.Command(request.type(), startAt, endAt, request.name(), request.activityId()));
        return responseAssembler.toResponse(block);
    }

    @PutMapping("/blocks/{id}")
    public TimeBlockResponse editBlock(@PathVariable UUID id, @RequestBody EditTimeBlockRequest request) {
        LocalTime start = LocalTime.parse(request.startTime());
        LocalTime end = LocalTime.parse(request.endTime());

        TimeBlock updated = editTimeBlock.execute(id, start, end, request.name());
        return responseAssembler.toResponse(updated);
    }

    @PatchMapping("/blocks/{id}/move")
    public TimeBlockResponse moveBlock(@PathVariable UUID id, @RequestBody MoveTimeBlockRequest request) {
        TimeBlock moved = moveTimeBlock.execute(
                id, request.day(), LocalTime.parse(request.startTime()), LocalTime.parse(request.endTime()));
        return responseAssembler.toResponse(moved);
    }

    @DeleteMapping("/blocks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBlock(@PathVariable UUID id, @RequestParam(defaultValue = "self") String scope) {
        deleteTimeBlock.execute(id, parseScope(scope));
    }

    private static DeleteTimeBlock.Scope parseScope(String scope) {
        return switch (scope) {
            case "self" -> DeleteTimeBlock.Scope.SELF;
            case "activityDay" -> DeleteTimeBlock.Scope.ACTIVITY_DAY;
            default -> throw new IllegalArgumentException("scope must be 'self' or 'activityDay', was: " + scope);
        };
    }

    public record CreateTimeBlockRequest(BlockType type, String startTime, String endTime, String name, UUID activityId) {
    }

    public record EditTimeBlockRequest(String startTime, String endTime, String name) {
    }

    public record MoveTimeBlockRequest(LocalDate day, String startTime, String endTime) {
    }
}
