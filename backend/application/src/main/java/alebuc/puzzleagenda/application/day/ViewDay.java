package alebuc.puzzleagenda.application.day;

import alebuc.puzzleagenda.domain.horizon.HorizonState;
import alebuc.puzzleagenda.domain.port.HorizonStateRepository;
import alebuc.puzzleagenda.domain.port.MaterializedDayRepository;
import alebuc.puzzleagenda.domain.port.RoutineTemplateRepository;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.routine.RoutineTemplateEntry;
import alebuc.puzzleagenda.domain.service.MaterializationService;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Returns a day's blocks in chronological order (FR-020), enforcing
 * reachability (FR-009, FR-023). Materializes the day from the routine
 * template on first-ever access to a today-or-future day (FR-017;
 * research.md §4) — idempotent via the {@code MaterializedDay} marker, and
 * never applied to a past day.
 */
public final class ViewDay {

    private final TimeBlockRepository timeBlockRepository;
    private final HorizonStateRepository horizonStateRepository;
    private final RoutineTemplateRepository routineTemplateRepository;
    private final MaterializedDayRepository materializedDayRepository;
    private final MaterializationService materializationService;
    private final Clock clock;

    public ViewDay(
            TimeBlockRepository timeBlockRepository,
            HorizonStateRepository horizonStateRepository,
            RoutineTemplateRepository routineTemplateRepository,
            MaterializedDayRepository materializedDayRepository,
            MaterializationService materializationService,
            Clock clock) {
        this.timeBlockRepository = Objects.requireNonNull(timeBlockRepository);
        this.horizonStateRepository = Objects.requireNonNull(horizonStateRepository);
        this.routineTemplateRepository = Objects.requireNonNull(routineTemplateRepository);
        this.materializedDayRepository = Objects.requireNonNull(materializedDayRepository);
        this.materializationService = Objects.requireNonNull(materializationService);
        this.clock = Objects.requireNonNull(clock);
    }

    public DayView execute(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");

        LocalDate today = LocalDate.now(clock);
        HorizonState horizonState = horizonStateRepository.load();
        horizonState.checkReachable(date, today);

        List<TimeBlock> blocks = new ArrayList<>(timeBlockRepository.findByDay(date));

        boolean materialized = materializedDayRepository.isMaterialized(date);
        if (!materialized && !date.isBefore(today)) {
            blocks.addAll(materialize(date));
            materializedDayRepository.markMaterialized(date, LocalDateTime.now(clock));
            materialized = true;
        }

        blocks.sort((a, b) -> a.range().start().compareTo(b.range().start()));
        return new DayView(date, materialized, blocks);
    }

    private List<TimeBlock> materialize(LocalDate date) {
        List<RoutineTemplateEntry> template = routineTemplateRepository.findAll();
        if (template.isEmpty()) {
            return List.of();
        }

        // Wide enough to catch a block spilling into `date` from the previous day, a block
        // already on `date`, and a block already on the following day (research.md §3) — no
        // single template entry's projected span exceeds the following day.
        TimeRange candidateWindow = new TimeRange(date.minusDays(1).atStartOfDay(), date.plusDays(2).atStartOfDay());
        List<TimeBlock> candidates = timeBlockRepository.findIntersecting(candidateWindow);

        List<TimeBlock> newBlocks = materializationService.materialize(date, template, candidates);
        newBlocks.forEach(timeBlockRepository::save);
        return newBlocks;
    }

    public record DayView(LocalDate date, boolean materialized, List<TimeBlock> blocks) {
    }
}
