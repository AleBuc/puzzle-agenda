package alebuc.puzzleagenda.domain.service;

import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Merges same-activity, same-day {@code PLANNED_ACTIVITY} fragments that
 * touch or overlap a candidate range into a single union range (FR-005-
 * FR-007; research.md §2). Pure/stateless, mirroring
 * {@link MaterializationService}: the caller is responsible for fetching a
 * sufficiently narrow candidate list (same {@code activityId}, same day,
 * excluding the block being edited/moved itself).
 */
public final class FragmentMerger {

    private final OverlapPolicy overlapPolicy = new OverlapPolicy();

    /**
     * @param candidate            the range being created/edited/moved into place
     * @param sameActivityDayFragments existing fragments of the same activity, on the same
     *                             target day, excluding the block (if any) being edited/moved
     * @return the merge result: the union range and every fragment absorbed into it
     *         (possibly empty if nothing touched or overlapped the candidate)
     */
    public Result merge(TimeRange candidate, List<TimeBlock> sameActivityDayFragments) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(sameActivityDayFragments, "sameActivityDayFragments must not be null");

        LocalDateTime unionStart = candidate.start();
        LocalDateTime unionEnd = candidate.end();
        List<TimeBlock> absorbed = new ArrayList<>();
        List<TimeBlock> pending = new ArrayList<>(sameActivityDayFragments);

        boolean absorbedSomething;
        do {
            absorbedSomething = false;
            TimeRange union = new TimeRange(unionStart, unionEnd);
            for (var it = pending.iterator(); it.hasNext(); ) {
                TimeBlock fragment = it.next();
                if (overlapPolicy.touchesOrOverlaps(union, fragment.range())) {
                    unionStart = min(unionStart, fragment.range().start());
                    unionEnd = max(unionEnd, fragment.range().end());
                    absorbed.add(fragment);
                    it.remove();
                    absorbedSomething = true;
                }
            }
        } while (absorbedSomething);

        return new Result(new TimeRange(unionStart, unionEnd), absorbed);
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    public record Result(TimeRange mergedRange, List<TimeBlock> absorbedFragments) {

        public boolean mergedAnything() {
            return !absorbedFragments.isEmpty();
        }
    }
}
