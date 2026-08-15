package alebuc.puzzleagenda.application.day;

import alebuc.puzzleagenda.domain.horizon.HorizonState;
import alebuc.puzzleagenda.domain.port.HorizonStateRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Returns the current reachable range (contracts/api.md {@code GET /api/horizon}).
 * A pure read: never establishes Day 1 (research.md §5; quickstart.md §6).
 */
public final class GetHorizon {

    private final HorizonStateRepository horizonStateRepository;
    private final Clock clock;

    public GetHorizon(HorizonStateRepository horizonStateRepository, Clock clock) {
        this.horizonStateRepository = Objects.requireNonNull(horizonStateRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public HorizonView execute() {
        LocalDate today = LocalDate.now(clock);
        HorizonState state = horizonStateRepository.load();
        LocalDate forwardBound = state.forwardBound(today);
        return new HorizonView(state.day1().orElse(null), forwardBound);
    }

    public record HorizonView(LocalDate day1, LocalDate forwardBound) {
    }
}
