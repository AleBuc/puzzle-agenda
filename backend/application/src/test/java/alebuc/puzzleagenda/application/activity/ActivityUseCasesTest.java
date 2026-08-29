package alebuc.puzzleagenda.application.activity;

import alebuc.puzzleagenda.domain.activity.Activity;
import alebuc.puzzleagenda.domain.activity.DayPlanningStatus;
import alebuc.puzzleagenda.domain.activity.Priority;
import alebuc.puzzleagenda.domain.exception.ActivityHasPlannedFragmentsException;
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

import java.time.LocalDate;
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
    void createsAnActivity() {
        CreateActivity createActivity = new CreateActivity(activityRepository);

        Activity created = createActivity.execute(
                new CreateActivity.Command("Grocery run", 30, Priority.MEDIUM, "errands"));

        assertThat(created.name()).isEqualTo("Grocery run");
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
    void editsNameDurationPriorityAndCategory() {
        UUID id = UUID.randomUUID();
        Activity existing = Activity.reconstitute(id, "Old", 15, Priority.LOW, null);
        when(activityRepository.findById(id)).thenReturn(Optional.of(existing));
        EditActivity editActivity = new EditActivity(activityRepository);

        Activity updated = editActivity.execute(id, new EditActivity.Command("New", 45, Priority.HIGH, "leisure"));

        assertThat(updated.name()).isEqualTo("New");
        assertThat(updated.estimatedDurationMinutes()).isEqualTo(45);
        assertThat(updated.priority()).isEqualTo(Priority.HIGH);
        assertThat(updated.category()).isEqualTo("leisure");
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
    void deletesAnActivityWithNoFragmentsDirectly() {
        UUID id = UUID.randomUUID();
        Activity existing = Activity.reconstitute(id, "Errand", 20, Priority.LOW, null);
        when(activityRepository.findById(id)).thenReturn(Optional.of(existing));
        when(timeBlockRepository.findByActivityId(id)).thenReturn(List.of());
        DeleteActivity deleteActivity = new DeleteActivity(activityRepository, timeBlockRepository);

        deleteActivity.execute(id, false);

        verify(activityRepository).deleteById(id);
        verify(timeBlockRepository, never()).deleteById(any());
    }

    @Test
    void rejectsDeletingAnActivityWithFragmentsWithoutConfirmation() {
        UUID id = UUID.randomUUID();
        Activity existing = Activity.reconstitute(id, "Errand", 20, Priority.LOW, null);
        when(activityRepository.findById(id)).thenReturn(Optional.of(existing));
        when(timeBlockRepository.findByActivityId(id)).thenReturn(List.of(fragment(id, "2026-08-16")));
        DeleteActivity deleteActivity = new DeleteActivity(activityRepository, timeBlockRepository);

        assertThatThrownBy(() -> deleteActivity.execute(id, false))
                .isInstanceOf(ActivityHasPlannedFragmentsException.class);
        verify(activityRepository, never()).deleteById(any());
    }

    @Test
    void confirmedDeleteOfAMultiFragmentActivityCascadesToEveryFragment() {
        UUID activityId = UUID.randomUUID();
        Activity existing = Activity.reconstitute(activityId, "Errand", 20, Priority.LOW, null);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(existing));

        TimeBlock dayOneFragment = fragment(activityId, "2026-08-16");
        TimeBlock dayTwoFragment = fragment(activityId, "2026-08-18");
        when(timeBlockRepository.findByActivityId(activityId)).thenReturn(List.of(dayOneFragment, dayTwoFragment));

        DeleteActivity deleteActivity = new DeleteActivity(activityRepository, timeBlockRepository);
        deleteActivity.execute(activityId, true);

        ArgumentCaptor<UUID> deletedBlockIds = ArgumentCaptor.forClass(UUID.class);
        verify(timeBlockRepository, org.mockito.Mockito.times(2)).deleteById(deletedBlockIds.capture());
        assertThat(deletedBlockIds.getAllValues()).containsExactlyInAnyOrder(dayOneFragment.id(), dayTwoFragment.id());
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
    void listsAllActivitiesInTheAggregateView() {
        Activity first = Activity.reconstitute(UUID.randomUUID(), "A", 10, Priority.LOW, null);
        Activity second = Activity.reconstitute(UUID.randomUUID(), "B", 10, Priority.LOW, null);
        when(activityRepository.findAll()).thenReturn(List.of(first, second));
        when(timeBlockRepository.findByActivityId(first.id())).thenReturn(List.of());
        when(timeBlockRepository.findByActivityId(second.id())).thenReturn(List.of(fragment(second.id(), "2026-08-16")));
        ListActivities listActivities = new ListActivities(activityRepository, timeBlockRepository);

        List<ListActivities.ActivityView> views = listActivities.execute();

        assertThat(views).extracting(v -> v.activity().id()).containsExactly(first.id(), second.id());
        assertThat(views.get(0).totalFragmentCount()).isZero();
        assertThat(views.get(1).totalFragmentCount()).isEqualTo(1);
        assertThat(views.get(1).plannedDayCount()).isEqualTo(1);
    }

    @Test
    void dayScopedViewIncludesRemainingTimeAndStatusForThatDay() {
        Activity activity = Activity.reconstitute(UUID.randomUUID(), "A", 300, Priority.LOW, null);
        when(activityRepository.findAll()).thenReturn(List.of(activity));
        when(timeBlockRepository.findByActivityId(activity.id())).thenReturn(List.of(fragment(activity.id(), "2026-08-16")));
        ListActivities listActivities = new ListActivities(activityRepository, timeBlockRepository);

        List<ListActivities.ActivityView> views = listActivities.execute(LocalDate.parse("2026-08-16"));

        assertThat(views.get(0).dayPlanning()).isNotNull();
        assertThat(views.get(0).dayPlanning().status()).isEqualTo(DayPlanningStatus.PARTIALLY_PLANNED);
        assertThat(views.get(0).dayPlanning().remainingMinutes()).isEqualTo(270);
    }

    private static TimeBlock fragment(UUID activityId, String isoDate) {
        LocalDateTime start = LocalDate.parse(isoDate).atTime(9, 0);
        return TimeBlock.create(
                UUID.randomUUID(), BlockType.PLANNED_ACTIVITY, new TimeRange(start, start.plusMinutes(30)), null, activityId);
    }
}
