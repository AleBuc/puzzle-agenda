package alebuc.puzzleagenda.infrastructure.config;

import alebuc.puzzleagenda.application.day.GetHorizon;
import alebuc.puzzleagenda.application.day.ViewDay;
import alebuc.puzzleagenda.application.timeblock.CreateTimeBlock;
import alebuc.puzzleagenda.application.timeblock.DeleteTimeBlock;
import alebuc.puzzleagenda.application.timeblock.EditTimeBlock;
import alebuc.puzzleagenda.domain.port.HorizonStateRepository;
import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
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
    public GetHorizon getHorizon(HorizonStateRepository horizonStateRepository, Clock clock) {
        return new GetHorizon(horizonStateRepository, clock);
    }

    @Bean
    public ViewDay viewDay(
            TimeBlockRepository timeBlockRepository, HorizonStateRepository horizonStateRepository, Clock clock) {
        return new ViewDay(timeBlockRepository, horizonStateRepository, clock);
    }

    @Bean
    public CreateTimeBlock createTimeBlock(
            TimeBlockRepository timeBlockRepository,
            HorizonStateRepository horizonStateRepository,
            OverlapPolicy overlapPolicy,
            Clock clock) {
        return new CreateTimeBlock(timeBlockRepository, horizonStateRepository, overlapPolicy, clock);
    }

    @Bean
    public EditTimeBlock editTimeBlock(TimeBlockRepository timeBlockRepository, OverlapPolicy overlapPolicy) {
        return new EditTimeBlock(timeBlockRepository, overlapPolicy);
    }

    @Bean
    public DeleteTimeBlock deleteTimeBlock(TimeBlockRepository timeBlockRepository) {
        return new DeleteTimeBlock(timeBlockRepository);
    }
}
