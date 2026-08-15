package alebuc.puzzleagenda.domain.activity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityTest {

    @Test
    void createStartsUnplanned() {
        Activity activity = Activity.create(UUID.randomUUID(), "Grocery run", 30, Priority.MEDIUM, "errands");

        assertThat(activity.status()).isEqualTo(ActivityStatus.UNPLANNED);
        assertThat(activity.isPlanned()).isFalse();
    }

    @Test
    void reconstituteCanRehydrateAPlannedActivity() {
        Activity activity = Activity.reconstitute(
                UUID.randomUUID(), "Grocery run", 30, Priority.MEDIUM, "errands", ActivityStatus.PLANNED);

        assertThat(activity.status()).isEqualTo(ActivityStatus.PLANNED);
        assertThat(activity.isPlanned()).isTrue();
    }

    @Test
    void withDetailsPreservesIdAndStatus() {
        UUID id = UUID.randomUUID();
        Activity planned = Activity.reconstitute(id, "Old", 15, Priority.LOW, null, ActivityStatus.PLANNED);

        Activity edited = planned.withDetails("New", 45, Priority.HIGH, "leisure");

        assertThat(edited.id()).isEqualTo(id);
        assertThat(edited.status()).isEqualTo(ActivityStatus.PLANNED);
        assertThat(edited.name()).isEqualTo("New");
        assertThat(edited.estimatedDurationMinutes()).isEqualTo(45);
        assertThat(edited.priority()).isEqualTo(Priority.HIGH);
        assertThat(edited.category()).isEqualTo("leisure");
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> Activity.create(UUID.randomUUID(), "   ", 30, Priority.LOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonPositiveDuration() {
        assertThatThrownBy(() -> Activity.create(UUID.randomUUID(), "Nap", 0, Priority.LOW, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Activity.create(UUID.randomUUID(), "Nap", -5, Priority.LOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void categoryIsOptional() {
        Activity activity = Activity.create(UUID.randomUUID(), "Read", 60, Priority.LOW, null);

        assertThat(activity.category()).isNull();
    }
}
