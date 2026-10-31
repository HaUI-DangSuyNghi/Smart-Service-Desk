package vn.haui.dx.hpdi.intelligence.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClassificationConfiguration {
    public record Settings(String baseUrl, String token, String tokenFile, long timeoutMillis) {
        public Settings(String baseUrl, String token, String tokenFile) { this(baseUrl, token, tokenFile, 4000); }
        public Settings(String baseUrl, String token) { this(baseUrl, token, "", 4000); }
        public Settings {
            if (timeoutMillis < 500 || timeoutMillis > 20000) throw new IllegalArgumentException("Classification deadline must be between 500 and 20000 ms");
        }
        @Override public String toString() { return "Classification settings [redacted]"; }
        public String bearer() {
            return vn.haui.dx.hpdi.common.api.SecretFiles.resolve(token, tokenFile);
        }
    }

    @Bean
    Settings classificationSettings(
            @Value("${intelligence.classification.base-url:}") String baseUrl,
            @Value("${intelligence.classification.token:}") String token,
            @Value("${intelligence.classification.token-file:}") String tokenFile,
            @Value("${intelligence.classification.timeout-ms:4000}") long timeoutMillis) {
        return new Settings(baseUrl, token, tokenFile, timeoutMillis);
    }
}
