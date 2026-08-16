package alebuc.puzzleagenda.domain.horizon;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Idempotency marker recording that a day has been stamped from the routine
 * template, regardless of how many {@code ROUTINE} blocks it produced
 * (possibly zero) — research.md §4; data-model.md MaterializedDay. Never
 * written for a day earlier than today (FR-017).
 */
public record MaterializedDay(LocalDate day, LocalDateTime materializedAt) {

    public MaterializedDay {
        Objects.requireNonNull(day, "day must not be null");
        Objects.requireNonNull(materializedAt, "materializedAt must not be null");
    }
}
