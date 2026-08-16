package alebuc.puzzleagenda.domain.service;

import alebuc.puzzleagenda.domain.routine.RoutineTemplateEntry;
import alebuc.puzzleagenda.domain.timeblock.BlockType;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Materializes a day from the routine template (FR-017; research.md §3): for
 * each template entry, project it onto the target day, subtract every
 * intersecting existing block, and emit one {@code ROUTINE} {@link TimeBlock}
 * per maximal free sub-interval.
 *
 * <p>Pure/stateless — no repository dependency, unlike {@link OverlapPolicy}
 * only in that it also has no exception-throwing "reject" mode: entry vs.
 * entry independence and "never fails as a whole" (FR-017) fall out
 * naturally from processing each entry's own candidate list separately and
 * never throwing.
 *
 * <p><strong>Interpretation note</strong> (documented ambiguity resolution):
 * spec.md's Edge Cases describe two worked examples for sleep 23:00-07:00 —
 * one clipped by a 02:00-03:00 block ("creates two sleep blocks, 23:00-02:00
 * and 03:00-07:00") and one clipped by a 06:00-06:30 jog block ("clipped to
 * 23:00-06:00, stopping where the jog starts"). Taken completely literally,
 * the second example would produce only one block and drop the 06:30-07:00
 * remainder — but that contradicts FR-017's general rule ("If clipping
 * splits an entry's range into more than one free sub-interval, the system
 * MUST create one routine block per maximal free sub-interval") and
 * research.md §3's algorithm description, both of which the first example
 * already demonstrates. This implementation follows the general rule
 * consistently: the second example is read as an incomplete/illustrative
 * description of the leading clip, not a literal complete-output spec —
 * i.e. it also produces a 06:30-07:00 remainder block.
 */
public final class MaterializationService {

    /**
     * @param day             the day being materialized
     * @param templateEntries the current routine template (entries never overlap each other,
     *                        so each is processed independently against {@code candidateBlocks})
     * @param candidateBlocks existing blocks that could possibly intersect any entry projected
     *                        onto {@code day} — the caller is responsible for fetching a
     *                        sufficiently wide window (research.md §3: a block "on the
     *                        materialized day, spilling into it from the previous day, or
     *                        already on the following day" all count)
     * @return newly created {@code ROUTINE} blocks (possibly none per entry, possibly several)
     */
    public List<TimeBlock> materialize(LocalDate day, List<RoutineTemplateEntry> templateEntries, List<TimeBlock> candidateBlocks) {
        Objects.requireNonNull(day, "day must not be null");
        Objects.requireNonNull(templateEntries, "templateEntries must not be null");
        Objects.requireNonNull(candidateBlocks, "candidateBlocks must not be null");

        List<TimeBlock> created = new ArrayList<>();
        for (RoutineTemplateEntry entry : templateEntries) {
            TimeRange projected = entry.projectOnto(day);
            for (TimeRange freeSubInterval : freeSubIntervals(projected, candidateBlocks)) {
                created.add(TimeBlock.create(UUID.randomUUID(), BlockType.ROUTINE, freeSubInterval, entry.name(), null));
            }
        }
        return created;
    }

    private List<TimeRange> freeSubIntervals(TimeRange projected, List<TimeBlock> candidateBlocks) {
        List<TimeRange> free = new ArrayList<>(List.of(projected));
        for (TimeBlock block : candidateBlocks) {
            List<TimeRange> next = new ArrayList<>();
            for (TimeRange fragment : free) {
                next.addAll(subtract(fragment, block.range()));
            }
            free = next;
        }
        return free;
    }

    /** Subtracts {@code occupied} from {@code range}, returning 0, 1, or 2 resulting fragments. */
    private List<TimeRange> subtract(TimeRange range, TimeRange occupied) {
        if (!overlaps(range, occupied)) {
            return List.of(range);
        }

        List<TimeRange> result = new ArrayList<>(2);
        if (occupied.start().isAfter(range.start())) {
            result.add(new TimeRange(range.start(), occupied.start()));
        }
        if (occupied.end().isBefore(range.end())) {
            result.add(new TimeRange(occupied.end(), range.end()));
        }
        return result;
    }

    private boolean overlaps(TimeRange a, TimeRange b) {
        return a.start().isBefore(b.end()) && b.start().isBefore(a.end());
    }
}
