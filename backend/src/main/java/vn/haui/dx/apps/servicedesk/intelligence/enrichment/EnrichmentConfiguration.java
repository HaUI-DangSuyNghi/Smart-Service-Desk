package vn.haui.dx.apps.servicedesk.intelligence.enrichment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class EnrichmentConfiguration {
    public record Settings(boolean enabled, boolean timersEnabled) {}

    @Bean
    Settings enrichmentSettings(
            @Value("${serviceDesk.enrichment.enabled:false}") boolean enabled,
            @Value("${serviceDesk.enrichment.timers-enabled:false}") boolean timers) {
        return new Settings(enabled, timers);
    }

    @Bean
    Clock enrichmentClock() {
        return Clock.systemUTC();
    }
}
