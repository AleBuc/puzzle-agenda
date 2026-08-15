package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.domain.timeblock.TimeBlock;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Shared block shape for {@code GET /api/days/{date}} and the time-block
 * endpoints (contracts/api.md).
 *
 * <p>{@code activityName} is a contract extension made during tasks.md
 * T055/US3: data-model.md's TimeBlock section says a {@code PLANNED_ACTIVITY}
 * block's display label is its linked Activity's name ("a data-model-level
 * default, not stated explicitly in the spec"), but contracts/api.md's
 * response shape never actually carried that information — {@code name} is
 * explicitly "unused for PLANNED_ACTIVITY" and there was no field to fall
 * back to. Without this, the frontend would have no way to render a
 * meaningful label for a planned-activity block.
 */
public record TimeBlockResponse(
        UUID id, String type, String startTime, String endTime, boolean endsNextDay,
        String name, UUID activityId, String activityName) {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public static TimeBlockResponse from(TimeBlock block, String activityName) {
        return new TimeBlockResponse(
                block.id(),
                block.type().name(),
                block.range().start().toLocalTime().format(HH_MM),
                block.range().end().toLocalTime().format(HH_MM),
                block.range().spansMidnight(),
                block.name(),
                block.activityId().orElse(null),
                activityName);
    }

    public static TimeBlockResponse from(TimeBlock block) {
        return from(block, null);
    }
}
