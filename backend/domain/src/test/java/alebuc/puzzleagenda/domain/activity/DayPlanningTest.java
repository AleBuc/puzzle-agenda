package alebuc.puzzleagenda.domain.activity;

import alebuc.puzzleagenda.domain.timeblock.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DayPlanningTest {

    private static TimeRange range(int startMinute, int durationMinutes) {
        LocalDateTime base = LocalDateTime.of(2026, 8, 29, 0, 0);
        return new TimeRange(base.plusMinutes(startMinute), base.plusMinutes(startMinute + durationMinutes));
    }

    @Test
    void zeroFragmentsIsUnplannedWithFullRemainingTime() {
        DayPlanning planning = DayPlanning.of(300, List.of());

        assertThat(planning.plannedMinutes()).isZero();
        assertThat(planning.remainingMinutes()).isEqualTo(300);
        assertThat(planning.status()).isEqualTo(DayPlanningStatus.UNPLANNED);
    }

    @Test
    void someButNotAllQuotaIsPartiallyPlanned() {
        DayPlanning planning = DayPlanning.of(300, List.of(range(0, 120)));

        assertThat(planning.plannedMinutes()).isEqualTo(120);
        assertThat(planning.remainingMinutes()).isEqualTo(180);
        assertThat(planning.status()).isEqualTo(DayPlanningStatus.PARTIALLY_PLANNED);
    }

    @Test
    void exactlyAtQuotaIsPlannedWithZeroRemaining() {
        DayPlanning planning = DayPlanning.of(300, List.of(range(0, 120), range(200, 180)));

        assertThat(planning.plannedMinutes()).isEqualTo(300);
        assertThat(planning.remainingMinutes()).isZero();
        assertThat(planning.status()).isEqualTo(DayPlanningStatus.PLANNED);
    }

    @Test
    void overQuotaIsPlannedWithRemainingFlooredAtZeroButRawTotalUncapped() {
        DayPlanning planning = DayPlanning.of(45, List.of(range(0, 60)));

        assertThat(planning.plannedMinutes()).isEqualTo(60);
        assertThat(planning.remainingMinutes()).isZero();
        assertThat(planning.status()).isEqualTo(DayPlanningStatus.PLANNED);
    }
}
