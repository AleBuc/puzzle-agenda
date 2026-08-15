package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.application.timeblock.CreateTimeBlock;
import alebuc.puzzleagenda.application.timeblock.DeleteTimeBlock;
import alebuc.puzzleagenda.application.timeblock.EditTimeBlock;
import alebuc.puzzleagenda.domain.timeblock.BlockType;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public TimeBlockController(CreateTimeBlock createTimeBlock, EditTimeBlock editTimeBlock, DeleteTimeBlock deleteTimeBlock) {
        this.createTimeBlock = createTimeBlock;
        this.editTimeBlock = editTimeBlock;
        this.deleteTimeBlock = deleteTimeBlock;
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
        return TimeBlockResponse.from(block);
    }

    @PutMapping("/blocks/{id}")
    public TimeBlockResponse editBlock(@PathVariable UUID id, @RequestBody EditTimeBlockRequest request) {
        LocalTime start = LocalTime.parse(request.startTime());
        LocalTime end = LocalTime.parse(request.endTime());

        TimeBlock updated = editTimeBlock.execute(id, start, end, request.name());
        return TimeBlockResponse.from(updated);
    }

    @DeleteMapping("/blocks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBlock(@PathVariable UUID id) {
        deleteTimeBlock.execute(id);
    }

    public record CreateTimeBlockRequest(BlockType type, String startTime, String endTime, String name, UUID activityId) {
    }

    public record EditTimeBlockRequest(String startTime, String endTime, String name) {
    }
}
