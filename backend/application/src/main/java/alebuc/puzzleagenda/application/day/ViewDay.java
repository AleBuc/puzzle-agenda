package alebuc.puzzleagenda.application.day;

import alebuc.puzzleagenda.domain.horizon.HorizonState;
import alebuc.puzzleagenda.domain.port.HorizonStateRepository;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Returns a day's blocks in chronological order (FR-020), enforcing
 * reachability (FR-009, FR-023).
 *
 * <p>US1 scope only: this is a pure pass-through, with no materialization
 * side effect. {@code materialized} is unconditionally {@code false} —
 * nothing has ever actually run materialization yet, since
 * {@code MaterializedDayRepository} has no adapter until tasks.md T064.
 * tasks.md T063/US4 wires real materialization (and a real
 * {@code materialized} flag) into this use case.
 */
public final class ViewDay {

    private final TimeBlockRepository timeBlockRepository;
    private final HorizonStateRepository horizonStateRepository;
    private final Clock clock;

    public ViewDay(TimeBlockRepository timeBlockRepository, HorizonStateRepository horizonStateRepository, Clock clock) {
        this.timeBlockRepository = Objects.requireNonNull(timeBlockRepository);
        this.horizonStateRepository = Objects.requireNonNull(horizonStateRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public DayView execute(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");

        LocalDate today = LocalDate.now(clock);
        HorizonState horizonState = horizonStateRepository.load();
        horizonState.checkReachable(date, today);

        List<TimeBlock> blocks = timeBlockRepository.findByDay(date).stream()
                .sorted((a, b) -> a.range().start().compareTo(b.range().start()))
                .toList();

        return new DayView(date, false, blocks);
    }

    public record DayView(LocalDate date, boolean materialized, List<TimeBlock> blocks) {
    }
}
