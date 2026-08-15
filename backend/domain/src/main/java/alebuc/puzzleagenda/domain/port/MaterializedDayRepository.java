package alebuc.puzzleagenda.domain.port;

import alebuc.puzzleagenda.domain.horizon.MaterializedDay;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Port for the {@link MaterializedDay} idempotency marker (research.md §4). */
public interface MaterializedDayRepository {

    boolean isMaterialized(LocalDate day);

    void markMaterialized(LocalDate day, LocalDateTime materializedAt);
}
