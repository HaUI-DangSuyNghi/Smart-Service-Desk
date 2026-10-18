package vn.haui.dx.apps.servicedesk.triage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TriageConfiguration.Settings.class)
public class TriageConfiguration {
    @ConfigurationProperties("dx.intelligence.triage")
    public record Settings(String baseUrl, String token) {
        @Override public String toString() { return "Intelligence triage settings [redacted]"; }
    }
}
