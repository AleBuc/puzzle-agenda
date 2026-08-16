package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.domain.timeblock.TimeBlock;

import java.time.LocalDate;
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
 *
 * <p>{@code startsPreviousDay} is a second contract extension: a block that
 * spans midnight is only ever returned by {@code GET /api/days/{date}} for
 * its *start* day ({@link TimeBlock#day()}) — but its after-midnight
 * portion genuinely occupies time on the following day too. Without a way
 * to say so, that following day's timeline had no idea that time wasn't
 * free (a bug reported live: "0h to 7h shown as free on day J+1" for a
 * 23:00-07:00 block). True only when {@code GET /api/days/{date}} includes
 * this block for a `date` later than the block's own start day; always
 * `false` for the single-block CRUD endpoints, which have no "day being
 * viewed" context.
 *
 * <p>{@code endsNextDay} is likewise relative to {@code viewedDate}, not a
 * fixed property of the block: it means "does this block's occupied time
 * continue past the end of the viewed day", i.e. {@code end > viewedDate}.
 * For a spillover block viewed on the day it spills *into* (spillover day
 * == its end day), the block ends within that day and does not spill
 * further, so this must read {@code false} there even though the same
 * block reads {@code true} when viewed on its start day.
 */
public record TimeBlockResponse(
        UUID id, String type, String startTime, String endTime, boolean endsNextDay, boolean startsPreviousDay,
        String name, UUID activityId, String activityName) {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public static TimeBlockResponse from(TimeBlock block, LocalDate viewedDate, String activityName) {
        return new TimeBlockResponse(
                block.id(),
                block.type().name(),
                block.range().start().toLocalTime().format(HH_MM),
                block.range().end().toLocalTime().format(HH_MM),
                block.range().end().toLocalDate().isAfter(viewedDate),
                block.day().isBefore(viewedDate),
                block.name(),
                block.activityId().orElse(null),
                activityName);
    }

    public static TimeBlockResponse from(TimeBlock block, String activityName) {
        return from(block, block.day(), activityName);
    }

    public static TimeBlockResponse from(TimeBlock block) {
        return from(block, block.day(), null);
    }
}
