package alebuc.puzzleagenda.application.timeblock;

import alebuc.puzzleagenda.domain.exception.ActivityNotAvailableException;
import alebuc.puzzleagenda.domain.horizon.HorizonState;
import alebuc.puzzleagenda.domain.port.ActivityRepository;
import alebuc.puzzleagenda.domain.port.HorizonStateRepository;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.service.FragmentMerger;
import alebuc.puzzleagenda.domain.service.OverlapPolicy;
import alebuc.puzzleagenda.domain.timeblock.BlockType;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates a time block on a day within the reachable range (FR-006..FR-009).
 * Establishes Day 1 on the first-ever placement, to today's date at that
 * moment — never to the day targeted (research.md §5).
 *
 * <p>For a {@code PLANNED_ACTIVITY} block, {@code activityId} only needs to
 * reference an existing activity (FR-001) — an activity may now have several
 * concurrent fragments, so this no longer requires the activity to be
 * currently unplanned. The structural "activityId required iff
 * PLANNED_ACTIVITY" invariant is enforced separately, by
 * {@link TimeBlock#create}.
 */
public final class CreateTimeBlock {

    private final TimeBlockRepository timeBlockRepository;
    private final HorizonStateRepository horizonStateRepository;
    private final ActivityRepository activityRepository;
    private final OverlapPolicy overlapPolicy;
    private final FragmentMerger fragmentMerger;
    private final Clock clock;

    public CreateTimeBlock(
            TimeBlockRepository timeBlockRepository,
            HorizonStateRepository horizonStateRepository,
            ActivityRepository activityRepository,
            OverlapPolicy overlapPolicy,
            Clock clock) {
        this.timeBlockRepository = Objects.requireNonNull(timeBlockRepository);
        this.horizonStateRepository = Objects.requireNonNull(horizonStateRepository);
        this.activityRepository = Objects.requireNonNull(activityRepository);
        this.overlapPolicy = Objects.requireNonNull(overlapPolicy);
        this.fragmentMerger = new FragmentMerger();
        this.clock = Objects.requireNonNull(clock);
    }

    public TimeBlock execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");

        TimeRange candidate = new TimeRange(command.startAt(), command.endAt());
        LocalDate today = LocalDate.now(clock);
        LocalDate day = candidate.start().toLocalDate();

        HorizonState horizonState = horizonStateRepository.load();
        horizonState.checkReachable(day, today);

        if (command.type() == BlockType.PLANNED_ACTIVITY) {
            requireActivityExists(command.activityId());
        }

        List<TimeBlock> intersecting = timeBlockRepository.findIntersecting(candidate);
        List<TimeRange> foreignRanges = intersecting.stream()
                .filter(block -> !isSameActivityFragment(block, command.type(), command.activityId()))
                .map(TimeBlock::range)
                .toList();
        overlapPolicy.checkNoOverlap(candidate, foreignRanges);

        TimeRange finalRange = candidate;
        if (command.type() == BlockType.PLANNED_ACTIVITY) {
            List<TimeBlock> sameActivityDayFragments = timeBlockRepository.findByActivityIdAndDay(command.activityId(), day);
            FragmentMerger.Result mergeResult = fragmentMerger.merge(candidate, sameActivityDayFragments);
            finalRange = mergeResult.mergedRange();
            mergeResult.absorbedFragments().forEach(fragment -> timeBlockRepository.deleteById(fragment.id()));
        }

        TimeBlock block = TimeBlock.create(
                UUID.randomUUID(), command.type(), finalRange, command.name(), command.activityId());
        timeBlockRepository.save(block);

        if (!horizonState.isEstablished()) {
            horizonStateRepository.save(horizonState.establishIfNeeded(today));
        }

        return block;
    }

    private static boolean isSameActivityFragment(TimeBlock block, BlockType candidateType, UUID candidateActivityId) {
        return candidateType == BlockType.PLANNED_ACTIVITY
                && block.type() == BlockType.PLANNED_ACTIVITY
                && block.activityId().map(id -> id.equals(candidateActivityId)).orElse(false);
    }

    private void requireActivityExists(UUID activityId) {
        if (activityId == null) {
            throw new ActivityNotAvailableException(null, "activityId is required for a PLANNED_ACTIVITY block");
        }
        activityRepository.findById(activityId)
                .orElseThrow(() -> new ActivityNotAvailableException(activityId));
    }

    public record Command(BlockType type, LocalDateTime startAt, LocalDateTime endAt, String name, UUID activityId) {
    }
}
