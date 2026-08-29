package alebuc.puzzleagenda.domain.service;

import alebuc.puzzleagenda.domain.exception.TimeBlockOverlapException;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;

import java.util.List;

/**
 * Same-day overlap detection (FR-008, FR-016). Because both a
 * {@code TimeBlock} and a projected routine template entry are stored as a
 * single continuous {@code [start, end)} interval (research.md §1), a
 * midnight-spanning range needs no special two-day handling here: two
 * ranges conflict exactly when they intersect.
 */
public final class OverlapPolicy {

    /** True when {@code a} and {@code b} share any instant (half-open intervals). */
    public boolean overlaps(TimeRange a, TimeRange b) {
        return a.start().isBefore(b.end()) && b.start().isBefore(a.end());
    }

    /**
     * True when {@code a} and {@code b} overlap OR share a boundary instant
     * (one's end equals the other's start) — the same-activity fragment
     * merge trigger (FR-005), a relaxed version of {@link #overlaps} using
     * {@code <=} instead of {@code <}.
     */
    public boolean touchesOrOverlaps(TimeRange a, TimeRange b) {
        return !a.start().isAfter(b.end()) && !b.start().isAfter(a.end());
    }

    /**
     * @throws TimeBlockOverlapException on the first {@code existingRanges} entry that
     *                                   intersects {@code candidate}
     */
    public void checkNoOverlap(TimeRange candidate, List<TimeRange> existingRanges) {
        for (TimeRange existing : existingRanges) {
            if (overlaps(candidate, existing)) {
                throw new TimeBlockOverlapException(candidate, existing);
            }
        }
    }
}
