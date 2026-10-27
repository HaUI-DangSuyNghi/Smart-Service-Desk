package vn.haui.dx.hpdi.intelligence.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClassificationConfiguration {
    public record Settings(String baseUrl, String token, String tokenFile) {
        public Settings(String baseUrl, String token) { this(baseUrl, token, ""); }
        public String bearer() {
            return vn.haui.dx.hpdi.common.api.SecretFiles.resolve(token, tokenFile);
        }
    }

    @Bean
    Settings classificationSettings(
            @Value("${intelligence.classification.base-url:}") String baseUrl,
            @Value("${intelligence.classification.token:}") String token,
            @Value("${intelligence.classification.token-file:}") String tokenFile) {
        return new Settings(baseUrl, token, tokenFile);
    }
}
