package alebuc.puzzleagenda.domain.activity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityTest {

    @Test
    void createBuildsAnActivityWithNoStoredPlanningState() {
        Activity activity = Activity.create(UUID.randomUUID(), "Grocery run", 30, Priority.MEDIUM, "errands");

        assertThat(activity.name()).isEqualTo("Grocery run");
        assertThat(activity.estimatedDurationMinutes()).isEqualTo(30);
        assertThat(activity.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(activity.category()).isEqualTo("errands");
    }

    @Test
    void reconstituteRehydratesFromPersistence() {
        UUID id = UUID.randomUUID();
        Activity activity = Activity.reconstitute(id, "Grocery run", 30, Priority.MEDIUM, "errands");

        assertThat(activity.id()).isEqualTo(id);
        assertThat(activity.name()).isEqualTo("Grocery run");
    }

    @Test
    void withDetailsPreservesId() {
        UUID id = UUID.randomUUID();
        Activity original = Activity.reconstitute(id, "Old", 15, Priority.LOW, null);

        Activity edited = original.withDetails("New", 45, Priority.HIGH, "leisure");

        assertThat(edited.id()).isEqualTo(id);
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
