package alebuc.puzzleagenda.application.timeblock;

import alebuc.puzzleagenda.domain.exception.DayBeyondForwardHorizonException;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeBlockUseCasesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 16);
    private static final Clock CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @Mock
    private TimeBlockRepository timeBlockRepository;

    @Mock
    private HorizonStateRepository horizonStateRepository;

    @Mock
    private ActivityRepository activityRepository;

    private final OverlapPolicy overlapPolicy = new OverlapPolicy();

    private CreateTimeBlock createTimeBlock;
    private EditTimeBlock editTimeBlock;
    private DeleteTimeBlock deleteTimeBlock;

    @BeforeEach
    void setUp() {
        createTimeBlock = new CreateTimeBlock(
                timeBlockRepository, horizonStateRepository, activityRepository, overlapPolicy, CLOCK);
        editTimeBlock = new EditTimeBlock(timeBlockRepository, overlapPolicy);
        deleteTimeBlock = new DeleteTimeBlock(timeBlockRepository);
    }

    // --- CreateTimeBlock -----------------------------------------------

    @Test
    void createsABlockAndEstablishesDay1OnFirstEverPlacement() {
        when(horizonStateRepository.load()).thenReturn(HorizonState.notYetEstablished());
        when(timeBlockRepository.findIntersecting(any())).thenReturn(List.of());

        CreateTimeBlock.Command command = new CreateTimeBlock.Command(
                BlockType.CONSTRAINED,
                LocalDateTime.of(2026, 8, 16, 9, 0),
                LocalDateTime.of(2026, 8, 16, 10, 30),
                "Standup",
                null);

        TimeBlock created = createTimeBlock.execute(command);

        assertThat(created.type()).isEqualTo(BlockType.CONSTRAINED);
        assertThat(created.name()).isEqualTo("Standup");
        verify(timeBlockRepository).save(created);

        ArgumentCaptor<HorizonState> savedHorizon = ArgumentCaptor.forClass(HorizonState.class);
        verify(horizonStateRepository).save(savedHorizon.capture());
        assertThat(savedHorizon.getValue().day1()).contains(TODAY);
    }

    @Test
    void doesNotRewriteHorizonStateOnceDay1IsEstablished() {
        when(horizonStateRepository.load()).thenReturn(HorizonState.withDay1(TODAY.minusDays(5)));
        when(timeBlockRepository.findIntersecting(any())).thenReturn(List.of());

        CreateTimeBlock.Command command = new CreateTimeBlock.Command(
                BlockType.CONSTRAINED,
                LocalDateTime.of(2026, 8, 16, 9, 0),
                LocalDateTime.of(2026, 8, 16, 10, 0),
                null,
                null);

        createTimeBlock.execute(command);

        verify(horizonStateRepository, never()).save(any());
    }

    @Test
    void rejectsABlockOverlappingAnExistingOne() {
        when(horizonStateRepository.load()).thenReturn(HorizonState.withDay1(TODAY));
        TimeBlock existing = TimeBlock.create(
                UUID.randomUUID(), BlockType.CONSTRAINED,
                new TimeRange(LocalDateTime.of(2026, 8, 16, 9, 0), LocalDateTime.of(2026, 8, 16, 10, 0)),
                "Existing", null);
        when(timeBlockRepository.findIntersecting(any())).thenReturn(List.of(existing));

        CreateTimeBlock.Command command = new CreateTimeBlock.Command(
                BlockType.CONSTRAINED,
                LocalDateTime.of(2026, 8, 16, 9, 30),
                LocalDateTime.of(2026, 8, 16, 9, 45),
                null,
                null);

        assertThatThrownBy(() -> createTimeBlock.execute(command))
                .isInstanceOf(TimeBlockOverlapException.class);
        verify(timeBlockRepository, never()).save(any());
    }

    @Test
    void rejectsABlockBeyondTheForwardHorizon() {
        when(horizonStateRepository.load()).thenReturn(HorizonState.withDay1(TODAY));

        LocalDateTime farStart = LocalDateTime.of(TODAY.plusDays(14).getYear(),
                TODAY.plusDays(14).getMonthValue(), TODAY.plusDays(14).getDayOfMonth(), 9, 0);
        CreateTimeBlock.Command command = new CreateTimeBlock.Command(
                BlockType.CONSTRAINED, farStart, farStart.plusHours(1), null, null);

        assertThatThrownBy(() -> createTimeBlock.execute(command))
                .isInstanceOf(DayBeyondForwardHorizonException.class);
    }

    @Test
    void rejectsABlockBeforeDay1() {
        when(horizonStateRepository.load()).thenReturn(HorizonState.withDay1(TODAY));

        LocalDate before = TODAY.minusDays(1);
        LocalDateTime start = before.atTime(9, 0);
        CreateTimeBlock.Command command = new CreateTimeBlock.Command(
                BlockType.CONSTRAINED, start, start.plusHours(1), null, null);

        assertThatThrownBy(() -> createTimeBlock.execute(command))
                .isInstanceOf(DayNotReachableException.class);
    }

    // --- EditTimeBlock ---------------------------------------------------

    @Test
    void editsABlocksTimeAndNameOnTheSameDay() {
        UUID id = UUID.randomUUID();
        TimeBlock existing = TimeBlock.create(
                id, BlockType.CONSTRAINED,
                new TimeRange(LocalDateTime.of(2026, 8, 16, 9, 0), LocalDateTime.of(2026, 8, 16, 10, 0)),
                "Old name", null);
        when(timeBlockRepository.findById(id)).thenReturn(Optional.of(existing));
        when(timeBlockRepository.findIntersecting(any())).thenReturn(List.of(existing));

        TimeBlock updated = editTimeBlock.execute(id, LocalTime.of(9, 30), LocalTime.of(10, 30), "New name");

        assertThat(updated.name()).isEqualTo("New name");
        assertThat(updated.range().start()).isEqualTo(LocalDateTime.of(2026, 8, 16, 9, 30));
        assertThat(updated.day()).isEqualTo(LocalDate.of(2026, 8, 16));
        verify(timeBlockRepository).save(updated);
    }

    @Test
    void editingDoesNotConflictWithItself() {
        UUID id = UUID.randomUUID();
        TimeBlock existing = TimeBlock.create(
                id, BlockType.CONSTRAINED,
                new TimeRange(LocalDateTime.of(2026, 8, 16, 9, 0), LocalDateTime.of(2026, 8, 16, 10, 0)),
                null, null);
        when(timeBlockRepository.findById(id)).thenReturn(Optional.of(existing));
        // findIntersecting legitimately returns the block being edited itself.
        when(timeBlockRepository.findIntersecting(any())).thenReturn(List.of(existing));

        TimeBlock updated = editTimeBlock.execute(id, LocalTime.of(9, 0), LocalTime.of(10, 30), null);

        assertThat(updated.range().end()).isEqualTo(LocalDateTime.of(2026, 8, 16, 10, 30));
    }

    @Test
    void editRejectsAResultingOverlapWithAnotherBlock() {
        UUID id = UUID.randomUUID();
        TimeBlock existing = TimeBlock.create(
                id, BlockType.CONSTRAINED,
                new TimeRange(LocalDateTime.of(2026, 8, 16, 9, 0), LocalDateTime.of(2026, 8, 16, 10, 0)),
                null, null);
        TimeBlock other = TimeBlock.create(
                UUID.randomUUID(), BlockType.CONSTRAINED,
                new TimeRange(LocalDateTime.of(2026, 8, 16, 11, 0), LocalDateTime.of(2026, 8, 16, 12, 0)),
                null, null);
        when(timeBlockRepository.findById(id)).thenReturn(Optional.of(existing));
        when(timeBlockRepository.findIntersecting(any())).thenReturn(List.of(existing, other));

        assertThatThrownBy(() -> editTimeBlock.execute(id, LocalTime.of(9, 0), LocalTime.of(11, 30), null))
                .isInstanceOf(TimeBlockOverlapException.class);
    }

    @Test
    void editThrowsWhenTheBlockDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(timeBlockRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> editTimeBlock.execute(id, LocalTime.of(9, 0), LocalTime.of(10, 0), null))
                .isInstanceOf(TimeBlockNotFoundException.class);
    }

    // --- DeleteTimeBlock -------------------------------------------------

    @Test
    void deletesAnExistingBlock() {
        UUID id = UUID.randomUUID();
        TimeBlock existing = TimeBlock.create(
                id, BlockType.ROUTINE,
                new TimeRange(LocalDateTime.of(2026, 8, 16, 23, 0), LocalDateTime.of(2026, 8, 17, 7, 0)),
                "Sleep", null);
        when(timeBlockRepository.findById(id)).thenReturn(Optional.of(existing));

        deleteTimeBlock.execute(id);

        verify(timeBlockRepository).deleteById(id);
    }

    @Test
    void deleteThrowsWhenTheBlockDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(timeBlockRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteTimeBlock.execute(id))
                .isInstanceOf(TimeBlockNotFoundException.class);
        verify(timeBlockRepository, never()).deleteById(any());
    }
}
