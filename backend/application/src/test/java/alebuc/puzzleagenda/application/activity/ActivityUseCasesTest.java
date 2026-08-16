package alebuc.puzzleagenda.application.activity;

import alebuc.puzzleagenda.domain.activity.Activity;
import alebuc.puzzleagenda.domain.activity.ActivityStatus;
import alebuc.puzzleagenda.domain.activity.Priority;
import alebuc.puzzleagenda.domain.exception.ActivityCurrentlyPlannedException;
import alebuc.puzzleagenda.domain.exception.ActivityNotFoundException;
import alebuc.puzzleagenda.domain.port.ActivityRepository;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.timeblock.BlockType;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityUseCasesTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private TimeBlockRepository timeBlockRepository;

    // --- CreateActivity ----------------------------------------------------

    @Test
    void createsAnUnplannedActivity() {
        CreateActivity createActivity = new CreateActivity(activityRepository);

        Activity created = createActivity.execute(
                new CreateActivity.Command("Grocery run", 30, Priority.MEDIUM, "errands"));

        assertThat(created.name()).isEqualTo("Grocery run");
        assertThat(created.status()).isEqualTo(ActivityStatus.UNPLANNED);
        verify(activityRepository).save(created);
    }

    @Test
    void rejectsABlankName() {
        CreateActivity createActivity = new CreateActivity(activityRepository);

        assertThatThrownBy(() -> createActivity.execute(new CreateActivity.Command("  ", 30, Priority.LOW, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(activityRepository, never()).save(any());
    }

    @Test
    void rejectsANonPositiveDuration() {
        CreateActivity createActivity = new CreateActivity(activityRepository);

        assertThatThrownBy(() -> createActivity.execute(new CreateActivity.Command("Nap", 0, Priority.LOW, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- EditActivity --------------------------------------------------------

    @Test
    void editsNameDurationPriorityAndCategoryButNotStatus() {
        UUID id = UUID.randomUUID();
        Activity existing = Activity.reconstitute(id, "Old", 15, Priority.LOW, null, ActivityStatus.PLANNED);
        when(activityRepository.findById(id)).thenReturn(Optional.of(existing));
        EditActivity editActivity = new EditActivity(activityRepository);

        Activity updated = editActivity.execute(id, new EditActivity.Command("New", 45, Priority.HIGH, "leisure"));

        assertThat(updated.name()).isEqualTo("New");
        assertThat(updated.estimatedDurationMinutes()).isEqualTo(45);
        assertThat(updated.priority()).isEqualTo(Priority.HIGH);
        assertThat(updated.category()).isEqualTo("leisure");
        assertThat(updated.status()).isEqualTo(ActivityStatus.PLANNED);
        verify(activityRepository).save(updated);
    }

    @Test
    void editThrowsWhenTheActivityDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(activityRepository.findById(id)).thenReturn(Optional.empty());
        EditActivity editActivity = new EditActivity(activityRepository);

        assertThatThrownBy(() -> editActivity.execute(id, new EditActivity.Command("New", 10, Priority.LOW, null)))
                .isInstanceOf(ActivityNotFoundException.class);
    }

    // --- DeleteActivity ------------------------------------------------------

    @Test
    void deletesAnUnplannedActivityDirectly() {
        UUID id = UUID.randomUUID();
        Activity existing = Activity.reconstitute(id, "Errand", 20, Priority.LOW, null, ActivityStatus.UNPLANNED);
        when(activityRepository.findById(id)).thenReturn(Optional.of(existing));
        DeleteActivity deleteActivity = new DeleteActivity(activityRepository, timeBlockRepository);

        deleteActivity.execute(id, false);

        verify(activityRepository).deleteById(id);
        verify(timeBlockRepository, never()).deleteById(any());
    }

    @Test
    void rejectsDeletingAPlannedActivityWithoutConfirmation() {
        UUID id = UUID.randomUUID();
        Activity existing = Activity.reconstitute(id, "Errand", 20, Priority.LOW, null, ActivityStatus.PLANNED);
        when(activityRepository.findById(id)).thenReturn(Optional.of(existing));
        DeleteActivity deleteActivity = new DeleteActivity(activityRepository, timeBlockRepository);

        assertThatThrownBy(() -> deleteActivity.execute(id, false))
                .isInstanceOf(ActivityCurrentlyPlannedException.class);
        verify(activityRepository, never()).deleteById(any());
    }

    @Test
    void confirmedDeleteOfAPlannedActivityAlsoDeletesItsScheduledBlock() {
        UUID activityId = UUID.randomUUID();
        Activity existing = Activity.reconstitute(activityId, "Errand", 20, Priority.LOW, null, ActivityStatus.PLANNED);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(existing));

        UUID blockId = UUID.randomUUID();
        TimeBlock block = TimeBlock.create(
                blockId, BlockType.PLANNED_ACTIVITY,
                new TimeRange(LocalDateTime.of(2026, 8, 16, 9, 0), LocalDateTime.of(2026, 8, 16, 9, 30)),
                null, activityId);
        when(timeBlockRepository.findByActivityId(activityId)).thenReturn(Optional.of(block));

        DeleteActivity deleteActivity = new DeleteActivity(activityRepository, timeBlockRepository);
        deleteActivity.execute(activityId, true);

        ArgumentCaptor<UUID> deletedBlockId = ArgumentCaptor.forClass(UUID.class);
        verify(timeBlockRepository).deleteById(deletedBlockId.capture());
        assertThat(deletedBlockId.getValue()).isEqualTo(blockId);
        verify(activityRepository).deleteById(activityId);
    }

    @Test
    void deleteThrowsWhenTheActivityDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(activityRepository.findById(id)).thenReturn(Optional.empty());
        DeleteActivity deleteActivity = new DeleteActivity(activityRepository, timeBlockRepository);

        assertThatThrownBy(() -> deleteActivity.execute(id, false))
                .isInstanceOf(ActivityNotFoundException.class);
    }

    // --- ListActivities --------------------------------------------------------

    @Test
    void listsAllActivitiesWhenNoFilterIsGiven() {
        Activity unplanned = Activity.reconstitute(UUID.randomUUID(), "A", 10, Priority.LOW, null, ActivityStatus.UNPLANNED);
        Activity planned = Activity.reconstitute(UUID.randomUUID(), "B", 10, Priority.LOW, null, ActivityStatus.PLANNED);
        when(activityRepository.findAll()).thenReturn(List.of(unplanned, planned));
        ListActivities listActivities = new ListActivities(activityRepository);

        assertThat(listActivities.execute(null)).containsExactly(unplanned, planned);
    }

    @Test
    void filtersActivitiesByStatus() {
        Activity unplanned = Activity.reconstitute(UUID.randomUUID(), "A", 10, Priority.LOW, null, ActivityStatus.UNPLANNED);
        Activity planned = Activity.reconstitute(UUID.randomUUID(), "B", 10, Priority.LOW, null, ActivityStatus.PLANNED);
        when(activityRepository.findAll()).thenReturn(List.of(unplanned, planned));
        ListActivities listActivities = new ListActivities(activityRepository);

        assertThat(listActivities.execute(ActivityStatus.PLANNED)).containsExactly(planned);
    }
}
