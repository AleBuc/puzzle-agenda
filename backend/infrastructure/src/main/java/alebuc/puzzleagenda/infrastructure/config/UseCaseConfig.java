package alebuc.puzzleagenda.infrastructure.config;

import alebuc.puzzleagenda.application.activity.CreateActivity;
import alebuc.puzzleagenda.application.activity.DeleteActivity;
import alebuc.puzzleagenda.application.activity.EditActivity;
import alebuc.puzzleagenda.application.activity.ListActivities;
import alebuc.puzzleagenda.application.day.GetHorizon;
import alebuc.puzzleagenda.application.day.ViewDay;
import alebuc.puzzleagenda.application.routine.CreateRoutineEntry;
import alebuc.puzzleagenda.application.routine.DeleteRoutineEntry;
import alebuc.puzzleagenda.application.routine.EditRoutineEntry;
import alebuc.puzzleagenda.application.timeblock.CreateTimeBlock;
import alebuc.puzzleagenda.application.timeblock.DeleteTimeBlock;
import alebuc.puzzleagenda.application.timeblock.EditTimeBlock;
import alebuc.puzzleagenda.application.timeblock.MoveTimeBlock;
import alebuc.puzzleagenda.domain.port.ActivityRepository;
import alebuc.puzzleagenda.domain.port.HorizonStateRepository;
import alebuc.puzzleagenda.domain.port.MaterializedDayRepository;
import alebuc.puzzleagenda.domain.port.RoutineTemplateRepository;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.service.MaterializationService;
import alebuc.puzzleagenda.domain.service.OverlapPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires application-module use cases as Spring beans. Use cases themselves
 * carry no framework annotations (application depends only on domain, per
 * Constitution Principle I), so this configuration class — living in
 * infrastructure — is where they meet their port implementations.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public OverlapPolicy overlapPolicy() {
        return new OverlapPolicy();
    }

    @Bean
    public MaterializationService materializationService() {
        return new MaterializationService();
    }

    @Bean
    public GetHorizon getHorizon(HorizonStateRepository horizonStateRepository, Clock clock) {
        return new GetHorizon(horizonStateRepository, clock);
    }

    @Bean
    public ViewDay viewDay(
            TimeBlockRepository timeBlockRepository,
            HorizonStateRepository horizonStateRepository,
            RoutineTemplateRepository routineTemplateRepository,
            MaterializedDayRepository materializedDayRepository,
            MaterializationService materializationService,
            Clock clock) {
        return new ViewDay(
                timeBlockRepository, horizonStateRepository, routineTemplateRepository,
                materializedDayRepository, materializationService, clock);
    }

    @Bean
    public CreateTimeBlock createTimeBlock(
            TimeBlockRepository timeBlockRepository,
            HorizonStateRepository horizonStateRepository,
            ActivityRepository activityRepository,
            OverlapPolicy overlapPolicy,
            Clock clock) {
        return new CreateTimeBlock(timeBlockRepository, horizonStateRepository, activityRepository, overlapPolicy, clock);
    }

    @Bean
    public EditTimeBlock editTimeBlock(TimeBlockRepository timeBlockRepository, OverlapPolicy overlapPolicy) {
        return new EditTimeBlock(timeBlockRepository, overlapPolicy);
    }

    @Bean
    public DeleteTimeBlock deleteTimeBlock(TimeBlockRepository timeBlockRepository) {
        return new DeleteTimeBlock(timeBlockRepository);
    }

    @Bean
    public MoveTimeBlock moveTimeBlock(
            TimeBlockRepository timeBlockRepository, HorizonStateRepository horizonStateRepository, OverlapPolicy overlapPolicy, Clock clock) {
        return new MoveTimeBlock(timeBlockRepository, horizonStateRepository, overlapPolicy, clock);
    }

    @Bean
    public ListActivities listActivities(ActivityRepository activityRepository, TimeBlockRepository timeBlockRepository) {
        return new ListActivities(activityRepository, timeBlockRepository);
    }

    @Bean
    public CreateActivity createActivity(ActivityRepository activityRepository) {
        return new CreateActivity(activityRepository);
    }

    @Bean
    public EditActivity editActivity(ActivityRepository activityRepository) {
        return new EditActivity(activityRepository);
    }

    @Bean
    public DeleteActivity deleteActivity(ActivityRepository activityRepository, TimeBlockRepository timeBlockRepository) {
        return new DeleteActivity(activityRepository, timeBlockRepository);
    }

    @Bean
    public CreateRoutineEntry createRoutineEntry(RoutineTemplateRepository routineTemplateRepository) {
        return new CreateRoutineEntry(routineTemplateRepository);
    }

    @Bean
    public EditRoutineEntry editRoutineEntry(RoutineTemplateRepository routineTemplateRepository) {
        return new EditRoutineEntry(routineTemplateRepository);
    }

    @Bean
    public DeleteRoutineEntry deleteRoutineEntry(RoutineTemplateRepository routineTemplateRepository) {
        return new DeleteRoutineEntry(routineTemplateRepository);
    }
}
