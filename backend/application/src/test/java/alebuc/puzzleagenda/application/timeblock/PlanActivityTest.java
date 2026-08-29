package alebuc.puzzleagenda.application.timeblock;

import alebuc.puzzleagenda.domain.activity.Activity;
import alebuc.puzzleagenda.domain.activity.Priority;
import alebuc.puzzleagenda.domain.exception.ActivityNotAvailableException;
import alebuc.puzzleagenda.domain.exception.DayNotReachableException;
import alebuc.puzzleagenda.domain.exception.TimeBlockNotFoundException;
import alebuc.puzzleagenda.domain.exception.TimeBlockOverlapException;
import alebuc.puzzleagenda.domain.horizon.HorizonState;
import alebuc.puzzleagenda.domain.port.ActivityRepository;
import alebuc.puzzleagenda.domain.port.HorizonStateRepository;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.service.OverlapPolicy;
import alebuc.puzzleagenda.domain.timeblock.BlockType;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * US3-specific scenarios: planning a backlog activity into a slot and
 * moving a planned-activity block. The confirm-required delete flow
 * (FR-005) is covered by {@code ActivityUseCasesTest} (T037), not
 * duplicated here.
 */
@ExtendWith(MockitoExtension.class)
class PlanActivityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 16);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @Mock
    private TimeBlockRepository timeBlockRepository;

    @Mock
    private HorizonStateRepository horizonStateRepository;

    @Mock
    private ActivityRepository activityRepository;

    private final OverlapPolicy overlapPolicy = new OverlapPolicy();

    private CreateTimeBlock createTimeBlock;
    private MoveTimeBlock moveTimeBlock;

    @BeforeEach
    void setUp() {
        createTimeBlock = new CreateTimeBlock(
                timeBlockRepository, horizonStateRepository, activityRepository, overlapPolicy, CLOCK);
        moveTimeBlock = new MoveTimeBlock(timeBlockRepository, horizonStateRepository, overlapPolicy, CLOCK);
    }

    // --- CreateTimeBlock (PLANNED_ACTIVITY) -------------------------------

    @Test
    void plansAnActivityIntoASlot() {
        UUID activityId = UUID.randomUUID();
        Activity activity = Activity.reconstitute(activityId, "Errand", 30, Priority.MEDIUM, null);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(horizonStateRepository.load()).thenReturn(HorizonState.withDay1(TODAY));
        when(timeBlockRepository.findIntersecting(any())).thenReturn(List.of());
        when(timeBlockRepository.findByActivityIdAndDay(any(), any())).thenReturn(List.of());

        CreateTimeBlock.Command command = new CreateTimeBlock.Command(
                BlockType.PLANNED_ACTIVITY,
                LocalDateTime.of(2026, 8, 16, 14, 0),
                LocalDateTime.of(2026, 8, 16, 15, 0),
                null,
                activityId);

        TimeBlock created = createTimeBlock.execute(command);

        assertThat(created.type()).isEqualTo(BlockType.PLANNED_ACTIVITY);
        assertThat(created.activityId()).contains(activityId);
        verify(timeBlockRepository).save(created);
    }

    @Test
    void allowsPlanningASecondFragmentForAnActivityThatAlreadyHasOne() {
        // FR-001 (feature 002): an activity may now have several concurrent fragments —
        // no ACTIVITY_NOT_AVAILABLE rejection just because it already has one elsewhere.
        UUID activityId = UUID.randomUUID();
        Activity activity = Activity.reconstitute(activityId, "Errand", 30, Priority.MEDIUM, null);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(horizonStateRepository.load()).thenReturn(HorizonState.withDay1(TODAY));
        when(timeBlockRepository.findIntersecting(any())).thenReturn(List.of());
        when(timeBlockRepository.findByActivityIdAndDay(any(), any())).thenReturn(List.of());

        CreateTimeBlock.Command command = new CreateTimeBlock.Command(
                BlockType.PLANNED_ACTIVITY,
                LocalDateTime.of(2026, 8, 18, 14, 0),
                LocalDateTime.of(2026, 8, 18, 15, 0),
                null,
                activityId);

        TimeBlock created = createTimeBlock.execute(command);

        assertThat(created.activityId()).contains(activityId);
        verify(timeBlockRepository).save(created);
    }

    @Test
    void mergesWithATouchingFragmentOfTheSameActivityOnTheSameDay() {
        UUID activityId = UUID.randomUUID();
        Activity activity = Activity.reconstitute(activityId, "Course a pied", 45, Priority.MEDIUM, "sport");
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(horizonStateRepository.load()).thenReturn(HorizonState.withDay1(TODAY));
        when(timeBlockRepository.findIntersecting(any())).thenReturn(List.of());

        TimeBlock morning = TimeBlock.create(
                UUID.randomUUID(), BlockType.PLANNED_ACTIVITY,
                new TimeRange(LocalDateTime.of(2026, 8, 16, 7, 0), LocalDateTime.of(2026, 8, 16, 7, 20)),
                null, activityId);
        when(timeBlockRepository.findByActivityIdAndDay(activityId, TODAY)).thenReturn(List.of(morning));

        CreateTimeBlock.Command command = new CreateTimeBlock.Command(
                BlockType.PLANNED_ACTIVITY,
                LocalDateTime.of(2026, 8, 16, 7, 20),
                LocalDateTime.of(2026, 8, 16, 7, 35),
                null,
                activityId);

        TimeBlock created = createTimeBlock.execute(command);

        assertThat(created.range()).isEqualTo(new TimeRange(
                LocalDateTime.of(2026, 8, 16, 7, 0), LocalDateTime.of(2026, 8, 16, 7, 35)));
        verify(timeBlockRepository).deleteById(morning.id());
        verify(timeBlockRepository).save(created);
    }

    @Test
    void rejectsPlanningANonexistentActivity() {
        UUID activityId = UUID.randomUUID();
        when(activityRepository.findById(activityId)).thenReturn(Optional.empty());
        when(horizonStateRepository.load()).thenReturn(HorizonState.withDay1(TODAY));

        CreateTimeBlock.Command command = new CreateTimeBlock.Command(
                BlockType.PLANNED_ACTIVITY,
                LocalDateTime.of(2026, 8, 16, 14, 0),
                LocalDateTime.of(2026, 8, 16, 15, 0),
                null,
                activityId);

        assertThatThrownBy(() -> createTimeBlock.execute(command))
                .isInstanceOf(ActivityNotAvailableException.class);
    }

    @Test
    void rejectsPlanningWithAMissingActivityId() {
        // Found during the T071 contracts/api.md audit: a null activityId must not fall through
        // to a raw NPE or an unrelated 400 — it's still an ACTIVITY_NOT_AVAILABLE case (409),
        // same as a nonexistent one, just with a clearer message than "Activity null...".
        when(horizonStateRepository.load()).thenReturn(HorizonState.withDay1(TODAY));

        CreateTimeBlock.Command command = new CreateTimeBlock.Command(
                BlockType.PLANNED_ACTIVITY,
                LocalDateTime.of(2026, 8, 16, 14, 0),
                LocalDateTime.of(2026, 8, 16, 15, 0),
                null,
                null);

        assertThatThrownBy(() -> createTimeBlock.execute(command))
                .isInstanceOf(ActivityNotAvailableException.class)
                .hasMessageContaining("activityId is required");
        verifyNoInteractions(activityRepository);
    }

    // --- MoveTimeBlock -----------------------------------------------------

    @Test
    void movesAPlannedActivityBlockToANewDayAndSlot() {
        UUID activityId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        TimeBlock existing = TimeBlock.create(
                blockId, BlockType.PLANNED_ACTIVITY,
                new TimeRange(LocalDateTime.of(2026, 8, 16, 14, 0), LocalDateTime.of(2026, 8, 16, 15, 0)),
                null, activityId);
        when(timeBlockRepository.findById(blockId)).thenReturn(Optional.of(existing));
        when(horizonStateRepository.load()).thenReturn(HorizonState.withDay1(TODAY));
        when(timeBlockRepository.findIntersecting(any())).thenReturn(List.of());
        when(timeBlockRepository.findByActivityIdAndDay(any(), any())).thenReturn(List.of());

        TimeBlock moved = moveTimeBlock.execute(blockId, TODAY.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThat(moved.day()).isEqualTo(TODAY.plusDays(1));
        assertThat(moved.activityId()).contains(activityId);
        verify(timeBlockRepository).save(moved);
    }

    @Test
    void movingAFragmentOnlyMergesAgainstTheDestinationDaysFragments() {
        // FR-022 / US2: merge is evaluated only against the destination day, never the origin day.
        UUID activityId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        TimeBlock existing = TimeBlock.create(
                blockId, BlockType.PLANNED_ACTIVITY,
                new TimeRange(LocalDateTime.of(2026, 8, 16, 14, 0), LocalDateTime.of(2026, 8, 16, 15, 0)),
                null, activityId);
        TimeBlock destinationNeighbor = TimeBlock.create(
                UUID.randomUUID(), BlockType.PLANNED_ACTIVITY,
                new TimeRange(LocalDateTime.of(2026, 8, 17, 11, 0), LocalDateTime.of(2026, 8, 17, 12, 0)),
                null, activityId);
        when(timeBlockRepository.findById(blockId)).thenReturn(Optional.of(existing));
        when(horizonStateRepository.load()).thenReturn(HorizonState.withDay1(TODAY));
        when(timeBlockRepository.findIntersecting(any())).thenReturn(List.of());
        when(timeBlockRepository.findByActivityIdAndDay(activityId, TODAY.plusDays(1)))
                .thenReturn(List.of(destinationNeighbor));

        TimeBlock moved = moveTimeBlock.execute(blockId, TODAY.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThat(moved.range()).isEqualTo(new TimeRange(
                LocalDateTime.of(2026, 8, 17, 10, 0), LocalDateTime.of(2026, 8, 17, 12, 0)));
        verify(timeBlockRepository).deleteById(destinationNeighbor.id());
    }

    @Test
    void rejectsMovingABlockThatIsNotPlannedActivity() {
        UUID blockId = UUID.randomUUID();
        TimeBlock existing = TimeBlock.create(
                blockId, BlockType.CONSTRAINED,
                new TimeRange(LocalDateTime.of(2026, 8, 16, 9, 0), LocalDateTime.of(2026, 8, 16, 10, 0)),
                null, null);
        when(timeBlockRepository.findById(blockId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> moveTimeBlock.execute(blockId, TODAY, LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnOverlapAtTheDestination() {
        UUID blockId = UUID.randomUUID();
        TimeBlock existing = TimeBlock.create(
                blockId, BlockType.PLANNED_ACTIVITY,
                new TimeRange(LocalDateTime.of(2026, 8, 16, 14, 0), LocalDateTime.of(2026, 8, 16, 15, 0)),
                null, UUID.randomUUID());
        TimeBlock destinationConflict = TimeBlock.create(
                UUID.randomUUID(), BlockType.CONSTRAINED,
                new TimeRange(LocalDateTime.of(2026, 8, 17, 10, 30), LocalDateTime.of(2026, 8, 17, 11, 30)),
                null, null);
        when(timeBlockRepository.findById(blockId)).thenReturn(Optional.of(existing));
        when(horizonStateRepository.load()).thenReturn(HorizonState.withDay1(TODAY));
        when(timeBlockRepository.findIntersecting(any())).thenReturn(List.of(destinationConflict));

        assertThatThrownBy(() -> moveTimeBlock.execute(blockId, TODAY.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isInstanceOf(TimeBlockOverlapException.class);
    }

    @Test
    void rejectsMovingToADayBeforeDay1() {
        UUID blockId = UUID.randomUUID();
        TimeBlock existing = TimeBlock.create(
                blockId, BlockType.PLANNED_ACTIVITY,
                new TimeRange(LocalDateTime.of(2026, 8, 16, 14, 0), LocalDateTime.of(2026, 8, 16, 15, 0)),
                null, UUID.randomUUID());
        when(timeBlockRepository.findById(blockId)).thenReturn(Optional.of(existing));
        when(horizonStateRepository.load()).thenReturn(HorizonState.withDay1(TODAY));

        assertThatThrownBy(() -> moveTimeBlock.execute(blockId, TODAY.minusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isInstanceOf(DayNotReachableException.class);
    }

    @Test
    void moveThrowsWhenTheBlockDoesNotExist() {
        UUID blockId = UUID.randomUUID();
        when(timeBlockRepository.findById(blockId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moveTimeBlock.execute(blockId, TODAY, LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .isInstanceOf(TimeBlockNotFoundException.class);
    }
}
