package vn.haui.dx.hpdi.intelligence.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssistanceConfiguration {

    public record Settings(String baseUrl, String token, String tokenFile) {
        public Settings(String baseUrl, String token) { this(baseUrl, token, ""); }
        public String bearer() { return vn.haui.dx.hpdi.common.api.SecretFiles.resolve(token, tokenFile); }
        @Override public String toString() { return "Assistance settings [redacted]"; }
    }

    @Bean
    Settings assistanceSettings(
            @Value("${intelligence.assistance.base-url:${INTELLIGENCE_ASSISTANCE_BASE_URL:}}") String baseUrl,
            @Value("${intelligence.assistance.token:${INTELLIGENCE_ASSISTANCE_TOKEN:}}") String token,
            @Value("${intelligence.assistance.token-file:${INTELLIGENCE_ASSISTANCE_TOKEN_FILE:}}") String tokenFile) {
        return new Settings(baseUrl, token, tokenFile);
    }
}
