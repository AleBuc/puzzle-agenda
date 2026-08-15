package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.domain.timeblock.TimeBlock;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Shared block shape for {@code GET /api/days/{date}} and the time-block endpoints (contracts/api.md). */
public record TimeBlockResponse(
        UUID id, String type, String startTime, String endTime, boolean endsNextDay, String name, UUID activityId) {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public static TimeBlockResponse from(TimeBlock block) {
        return new TimeBlockResponse(
                block.id(),
                block.type().name(),
                block.range().start().toLocalTime().format(HH_MM),
                block.range().end().toLocalTime().format(HH_MM),
                block.range().spansMidnight(),
                block.name(),
                block.activityId().orElse(null));
    }
}
