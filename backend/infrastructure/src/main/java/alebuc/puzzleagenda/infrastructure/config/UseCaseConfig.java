package alebuc.puzzleagenda.infrastructure.config;

import alebuc.puzzleagenda.application.day.GetHorizon;
import alebuc.puzzleagenda.domain.port.HorizonStateRepository;
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
    public GetHorizon getHorizon(HorizonStateRepository horizonStateRepository, Clock clock) {
        return new GetHorizon(horizonStateRepository, clock);
    }
}
