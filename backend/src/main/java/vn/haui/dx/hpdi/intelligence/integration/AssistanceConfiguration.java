package vn.haui.dx.hpdi.intelligence.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssistanceConfiguration {

    public record Settings(String baseUrl, String token) {}

    @Bean
    Settings assistanceSettings(
            @Value("${intelligence.assistance.base-url:${INTELLIGENCE_ASSISTANCE_BASE_URL:}}") String baseUrl,
            @Value("${intelligence.assistance.token:${INTELLIGENCE_ASSISTANCE_TOKEN:}}") String token) {
        return new Settings(baseUrl, token);
    }
}
